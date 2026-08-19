/**
 * Generic APK Downloader for RiiSync.
 * This file handles downloading APKs from GitHub or direct URLs and triggering the Android package installer.
 */
package com.riisync.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.riisync.app.git.GitHubService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility object for downloading and installing APKs.
 */
object ApkDownloader {

    /**
     * Specifically resolves the latest Official Dolphin build URL using their update API.
     */
    suspend fun resolveDolphinOfficialUrl(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://dolphin-emu.org/update/latest/master/")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                if (response.contains("{")) {
                    val json = org.json.JSONObject(response)
                    val name = json.optString("name", "") // e.g. "2412-10" or "5.0-21444"
                    val hash = json.optString("hash", "") // e.g. "a83b..."
                    
                    if (name.isNotBlank() && hash.length >= 4) {
                        val shard1 = hash.substring(0, 2)
                        val shard2 = hash.substring(2, 4)
                        return@withContext "https://dl.dolphin-emu.org/builds/$shard1/$shard2/dolphin-master-$name.apk"
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        // Absolute fallback if API fails
        "https://dl.dolphin-emu.org/builds/dolphin-master-5.0-21460.apk"
    }

    /**
     * Downloads an APK from a GitHub repository's latest release, choosing the correct architecture.
     */
    suspend fun downloadFromGitHub(
        context: Context,
        owner: String,
        repo: String,
        fileName: String,
        token: String? = null,
        onProgress: (Float) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val githubService = GitHubService()
            val release = githubService.getLatestRelease(owner, repo, token) ?: throw Exception("Could not fetch latest release for $owner/$repo")
            val assets = release.getJSONArray("assets")
            var downloadUrl: String? = null
            
            // 1. Identify device architecture
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "universal"
            android.util.Log.d("ApkDownloader", "Device ABI: $abi")

            val assetList = mutableListOf<JSONObject>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                    assetList.add(asset)
                }
            }

            // 2. Filter for best match
            // Preference: Specific ABI > Universal > First APK found
            downloadUrl = assetList.find { it.getString("name").contains(abi, ignoreCase = true) }?.getString("browser_download_url")
                ?: assetList.find { it.getString("name").contains("universal", ignoreCase = true) }?.getString("browser_download_url")
                ?: assetList.firstOrNull()?.getString("browser_download_url")

            if (downloadUrl == null) throw Exception("No compatible APK found in $owner/$repo release")
            downloadDirect(context, downloadUrl, fileName, onProgress, onComplete, onError)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e.message ?: "GitHub download failed") }
        }
    }

    /**
     * Downloads an APK directly from a URL.
     */
    suspend fun downloadDirect(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            var currentUrl = downloadUrl
            var conn: HttpURLConnection
            var redirects = 0
            val maxRedirects = 5

            // Manual redirect loop
            while (true) {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false // We handle it manually
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode in 300..307 && redirects < maxRedirects) {
                    val location = conn.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    redirects++
                    continue
                }
                break
            }

            if (conn.responseCode !in 200..299) {
                throw Exception("Server returned code ${conn.responseCode}")
            }

            val fileLength = conn.contentLength
            val destination = File(context.cacheDir, fileName)
            if (destination.exists()) destination.delete()

            conn.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16384) // Larger buffer
                    var total = 0
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            withContext(Dispatchers.Main) {
                                onProgress(total.toFloat() / fileLength)
                            }
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(destination)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Download failed")
            }
        }
    }

    /**
     * Checks if the app has permission to install packages.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system settings to request install permission.
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    /**
     * Launches the system package installer.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
