/**
 * Persistent Settings Manager for RiiSync.
 * This file handles the storage and retrieval of user preferences, account information,
 * repository bookmarks, and managed mod metadata using SharedPreferences.
 */
package com.riisync.app.utils

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import com.riisync.app.git.TokenManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manager class for application-wide settings and state persistence.
 */
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val tokenManager = TokenManager(context)

    private val _isDarkTheme = mutableStateOf(prefs.getBoolean("dark_theme", false))
    val isDarkTheme: State<Boolean> = _isDarkTheme

    private val _autoFetch = mutableStateOf(prefs.getBoolean("auto_fetch", false))
    val autoFetch: State<Boolean> = _autoFetch

    private val _autoClearCache = mutableStateOf(prefs.getBoolean("auto_clear_cache", false))
    val autoClearCache: State<Boolean> = _autoClearCache

    private val _authorName = mutableStateOf(prefs.getString("author_name", "") ?: "")
    val authorName: State<String> = _authorName

    private val _authorEmail = mutableStateOf(prefs.getString("author_email", "") ?: "")
    val authorEmail: State<String> = _authorEmail

    private val _onboardingComplete = mutableStateOf(prefs.getBoolean("onboarding_complete", false))
    val onboardingComplete: State<Boolean> = _onboardingComplete

    private val _username = mutableStateOf(tokenManager.getUsername() ?: "")
    val username: State<String> = _username

    private val _token = mutableStateOf(tokenManager.getToken() ?: "")
    val token: State<String> = _token

    private val _rootCloneFolder = mutableStateOf(prefs.getString("root_clone_folder", "/storage/emulated/0/Github") ?: "/storage/emulated/0/Github")
    val rootCloneFolder: State<String> = _rootCloneFolder

    private val _sshPrivateKey = mutableStateOf(prefs.getString("ssh_private_key", "") ?: "")
    val sshPrivateKey: State<String> = _sshPrivateKey

    private val _sshPublicKey = mutableStateOf(prefs.getString("ssh_public_key", "") ?: "")
    val sshPublicKey: State<String> = _sshPublicKey

    private val _lastGitPath = mutableStateOf(prefs.getString("last_git_path", "") ?: "")
    val lastGitPath: State<String> = _lastGitPath

    private val _autoSyncOnPull = mutableStateOf(prefs.getBoolean("auto_sync_on_pull", false))
    val autoSyncOnPull: State<Boolean> = _autoSyncOnPull

    private val _targetDolphinPackage = mutableStateOf(prefs.getString("target_dolphin_pkg", "org.dolphinemu.dolphinemu") ?: "org.dolphinemu.dolphinemu")
    val targetDolphinPackage: State<String> = _targetDolphinPackage

    private val _lastDbSync = mutableStateOf(prefs.getLong("last_db_sync", 0L))
    val lastDbSync: State<Long> = _lastDbSync

    private val _dbIconCount = mutableIntStateOf(prefs.getInt("db_icon_count", 0))
    val dbIconCount: State<Int> = _dbIconCount

    private val _dbCoverCount = mutableIntStateOf(prefs.getInt("db_cover_count", 0))
    val dbCoverCount: State<Int> = _dbCoverCount

    private val _bookmarks = mutableStateOf(loadBookmarks())
    val bookmarks: State<List<String>> = _bookmarks

    private val _installedMods = mutableStateOf(loadInstalledMods())
    val installedMods: State<List<ManagedMod>> = _installedMods

    private val _showModScanWarning = mutableStateOf(prefs.getBoolean("show_mod_scan_warning", true))
    val showModScanWarning: State<Boolean> = _showModScanWarning

    private val _lastKnownVersion = mutableStateOf(prefs.getString("last_known_version", "") ?: "")
    val lastKnownVersion: State<String> = _lastKnownVersion

    /**
     * Metadata representing a mod linked and managed by the app.
     */
    data class ManagedMod(
        val name: String,
        val sourcePath: String,
        val riivolutionFolderName: String,
        val xmlFileName: String,
        val modFilesFolderName: String,
        val timestamp: Long = System.currentTimeMillis(),
        val syncCount: Int = 0,
        val lastSyncTimestamp: Long = 0,
        val gameId: String? = null,
        val gameTitle: String? = null
    )

    /**
     * Sets the user's dark theme preference.
     */
    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit().putBoolean("dark_theme", enabled).apply()
    }

    /**
     * Enables or disables automatic fetching of remote updates.
     */
    fun setAutoFetch(enabled: Boolean) {
        _autoFetch.value = enabled
        prefs.edit().putBoolean("auto_fetch", enabled).apply()
    }

    /**
     * Enables or disables automatic clearing of the application cache.
     */
    fun setAutoClearCache(enabled: Boolean) {
        _autoClearCache.value = enabled
        prefs.edit().putBoolean("auto_clear_cache", enabled).apply()
    }

    /**
     * Persists the Git author information used for commits.
     */
    fun setAuthorInfo(name: String, email: String) {
        _authorName.value = name
        _authorEmail.value = email
        prefs.edit().putString("author_name", name).putString("author_email", email).apply()
    }

    /**
     * Persists the GitHub credentials for API access using secure storage.
     */
    fun setGitHubCredentials(username: String, token: String) {
        _username.value = username
        _token.value = token
        tokenManager.saveUsername(username)
        tokenManager.saveToken(token)
    }

    /**
     * Sets the root folder where repositories will be cloned by default.
     */
    fun setRootCloneFolder(path: String) {
        _rootCloneFolder.value = path
        prefs.edit().putString("root_clone_folder", path).apply()
    }

    /**
     * Sets the SSH keys used for secure Git operations.
     */
    fun setSshKeys(private: String, public: String) {
        _sshPrivateKey.value = private
        _sshPublicKey.value = public
        prefs.edit()
            .putString("ssh_private_key", private)
            .putString("ssh_public_key", public)
            .apply()
    }

    /**
     * Updates the onboarding completion status.
     */
    fun setOnboardingComplete(complete: Boolean) {
        _onboardingComplete.value = complete
        prefs.edit().putBoolean("onboarding_complete", complete).apply()
    }

    /**
     * Enables or disables automatic mod synchronization after a Git pull.
     */
    fun setAutoSyncOnPull(enabled: Boolean) {
        _autoSyncOnPull.value = enabled
        prefs.edit().putBoolean("auto_sync_on_pull", enabled).apply()
    }

    /**
     * Sets the target Dolphin package for mod linking.
     */
    fun setTargetDolphinPackage(pkg: String) {
        _targetDolphinPackage.value = pkg
        prefs.edit().putString("target_dolphin_pkg", pkg).apply()
    }

    /**
     * Saves the path of the last opened repository and adds it to bookmarks.
     */
    fun setLastGitPath(path: String) {
        if (path.isEmpty()) {
            _lastGitPath.value = ""
            prefs.edit().putString("last_git_path", "").apply()
            return
        }
        _lastGitPath.value = path
        prefs.edit().putString("last_git_path", path).apply()
        addBookmark(path)
    }

    /**
     * Retrieves the path of the last opened repository.
     */
    fun getLastGitPath(): String {
        return _lastGitPath.value
    }

    private fun loadBookmarks(): List<String> {
        val json = prefs.getString("bookmarks", "[]") ?: "[]"
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }

    /**
     * Retrieves the list of bookmarked repository paths.
     */
    fun getBookmarks(): List<String> {
        return _bookmarks.value
    }

    /**
     * Adds a repository path to the bookmarks list.
     */
    fun addBookmark(path: String) {
        if (path.isBlank()) return
        val current = _bookmarks.value.toMutableList()
        if (!current.contains(path)) {
            current.add(0, path)
            if (current.size > 20) current.removeAt(current.size - 1)
            _bookmarks.value = current
            prefs.edit().putString("bookmarks", JSONArray(current).toString()).apply()
        }
    }
    
    /**
     * Removes a repository path from the bookmarks list.
     */
    fun removeBookmark(path: String) {
        val current = _bookmarks.value.toMutableList()
        // Use normalized path for comparison
        val targetFile = File(path)
        val iterator = current.iterator()
        var removed = false
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (p == path || (File(p).exists() && targetFile.exists() && File(p).canonicalPath == targetFile.canonicalPath)) {
                iterator.remove()
                removed = true
            }
        }

        if (removed) {
            _bookmarks.value = current
            prefs.edit().putString("bookmarks", JSONArray(current).toString()).apply()
            
            // If we deleted the active repo, switch to the first available bookmark
            if (getLastGitPath() == path || (getLastGitPath().isNotEmpty() && targetFile.exists() && File(getLastGitPath()).canonicalPath == targetFile.canonicalPath)) {
                val nextPath = current.firstOrNull() ?: ""
                _lastGitPath.value = nextPath
                prefs.edit().putString("last_git_path", nextPath).apply()
            }
        }
    }

    /**
     * Synchronizes bookmarks with the actual filesystem, removing entries for non-existent paths.
     */
    fun syncBookmarksWithFilesystem(onRemoved: (String) -> Unit) {
        val current = _bookmarks.value.toMutableList()
        val iterator = current.iterator()
        var changed = false
        val activePath = getLastGitPath()
        
        while (iterator.hasNext()) {
            val path = iterator.next()
            if (!File(path).exists()) {
                val name = File(path).name
                iterator.remove()
                onRemoved(name)
                changed = true
                
                // If the active repo was physically deleted, switch to another one
                if (activePath == path) {
                    changed = true // Ensure prefs are updated
                }
            }
        }
        if (changed) {
            _bookmarks.value = current
            prefs.edit().putString("bookmarks", JSONArray(current).toString()).apply()
            
            // Update active path if it was removed
            if (!current.contains(activePath)) {
                val nextPath = current.firstOrNull() ?: ""
                _lastGitPath.value = nextPath
                prefs.edit().putString("last_git_path", nextPath).apply()
            }
        }
    }

    /**
     * Retrieves the list of mods currently installed and managed by the app.
     */
    fun getInstalledMods(): List<ManagedMod> {
        return _installedMods.value
    }

    private fun loadInstalledMods(): List<ManagedMod> {
        val json = prefs.getString("installed_mods", "[]") ?: "[]"
        val array = JSONArray(json)
        val mods = mutableListOf<ManagedMod>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            mods.add(ManagedMod(
                obj.getString("name"),
                obj.getString("sourcePath"),
                obj.getString("riivolutionFolderName"),
                obj.optString("xmlFileName", ""),
                obj.getString("modFilesFolderName"),
                obj.getLong("timestamp"),
                obj.optInt("syncCount", 0),
                obj.optLong("lastSyncTimestamp", 0),
                obj.optString("gameId").takeIf { it.isNotBlank() },
                obj.optString("gameTitle").takeIf { it.isNotBlank() }
            ))
        }
        return mods
    }

    /**
     * Adds a new mod to the managed mods list.
     */
    fun addInstalledMod(mod: ManagedMod) {
        val mods = _installedMods.value.toMutableList()
        mods.removeAll { it.name == mod.name }
        mods.add(0, mod)
        saveInstalledMods(mods)
    }

    /**
     * Removes a mod from the managed mods list by name.
     */
    fun removeInstalledMod(name: String) {
        val mods = _installedMods.value.toMutableList()
        if (mods.removeIf { it.name == name }) {
            saveInstalledMods(mods)
        }
    }

    /**
     * Internal helper to save the managed mods list.
     */
    fun saveInstalledMods(mods: List<ManagedMod>) {
        _installedMods.value = mods
        val array = JSONArray()
        mods.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("sourcePath", it.sourcePath)
            obj.put("riivolutionFolderName", it.riivolutionFolderName)
            obj.put("xmlFileName", it.xmlFileName)
            obj.put("modFilesFolderName", it.modFilesFolderName)
            obj.put("timestamp", it.timestamp)
            obj.put("syncCount", it.syncCount)
            obj.put("lastSyncTimestamp", it.lastSyncTimestamp)
            obj.put("gameId", it.gameId)
            obj.put("gameTitle", it.gameTitle)
            array.put(obj)
        }
        prefs.edit().putString("installed_mods", array.toString()).apply()
    }

    /**
     * Updates the database synchronization metadata.
     */
    fun setDbMetadata(timestamp: Long, iconCount: Int, coverCount: Int) {
        _lastDbSync.value = timestamp
        _dbIconCount.intValue = iconCount
        _dbCoverCount.intValue = coverCount
        prefs.edit()
            .putLong("last_db_sync", timestamp)
            .putInt("db_icon_count", iconCount)
            .putInt("db_cover_count", coverCount)
            .apply()
    }

    /**
     * Sets whether to show the mod scanning warning dialog.
     */
    fun setShowModScanWarning(show: Boolean) {
        _showModScanWarning.value = show
        prefs.edit().putBoolean("show_mod_scan_warning", show).apply()
    }

    /**
     * Resets all "Don't show again" preferences across the app.
     */
    fun resetWarnings() {
        setShowModScanWarning(true)
        // Add more warnings here as they are implemented
    }

    /**
     * Updates the last known application version.
     */
    fun setLastKnownVersion(version: String) {
        _lastKnownVersion.value = version
        prefs.edit().putString("last_known_version", version).apply()
    }

    /**
     * Wipes all application data from SharedPreferences and secure storage.
     */
    fun wipeAllData() {
        prefs.edit().clear().apply()
        _onboardingComplete.value = false
        _username.value = ""
        _token.value = ""
        tokenManager.saveUsername("")
        tokenManager.saveToken("")
    }
}
