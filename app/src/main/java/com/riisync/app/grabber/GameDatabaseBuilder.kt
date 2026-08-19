package com.riisync.app.grabber

import android.content.Context
import com.riisync.app.git.GitManager
import com.riisync.app.git.GitHubService
import com.riisync.app.ui.TaskInfo
import com.riisync.app.utils.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orchestrates the creation and synchronization of the Wii Game Icon Database.
 * Uses the RiiSync-DB GitHub repository as the primary source.
 */
class GameDatabaseBuilder(private val context: Context) {

    private val baseRiisyncDir = File(android.os.Environment.getExternalStorageDirectory(), "RiiSync")
    private val databaseDir = File(baseRiisyncDir, "database").apply { mkdirs() }
    private val coversDir = File(databaseDir, "GameTDBcovers").apply { mkdirs() }
    
    // Repositories Configuration
    private val DATABASE_REPO_URL = "https://github.com/Im-Monee/RiiSync-DB.git"
    private val REPO_OWNER = "Im-Monee"
    private val REPO_NAME = "RiiSync-DB"
    
    private val gitManager = GitManager()
    private val githubService = GitHubService()

    init {
        // Ensure .nomedia exists to prevent gallery flooding
        try { File(databaseDir, ".nomedia").createNewFile() } catch (e: Exception) {}
    }

    /**
     * Downloads the entire database folder from the GitHub repository.
     */
    suspend fun downloadEverything(task: TaskInfo, settingsManager: SettingsManager) = coroutineScope {
        task.currentSubTask.value = "Cloning entire database..."
        task.progress.value = -1f
        
        val tempRepoDir = File(context.cacheDir, "temp_db_repo")
        if (tempRepoDir.exists()) tempRepoDir.deleteRecursively()

        val cloneResult = gitManager.clone(
            repoUrl = DATABASE_REPO_URL,
            localDir = tempRepoDir,
            progressMonitor = object : org.eclipse.jgit.lib.ProgressMonitor {
                override fun start(totalTasks: Int) {}
                override fun beginTask(title: String?, totalWork: Int) {
                    task.currentSubTask.value = title ?: "Downloading..."
                }
                override fun update(completed: Int) {}
                override fun endTask() {}
                override fun isCancelled(): Boolean = task.job?.isCancelled == true
                override fun showDuration(enabled: Boolean) {}
            }
        )

        if (cloneResult is GitManager.Result.Success) {
            task.currentSubTask.value = "Extracting icons..."
            val repoDbFolder = File(tempRepoDir, "database")
            if (repoDbFolder.exists()) {
                repoDbFolder.copyRecursively(databaseDir, overwrite = true)
                task.currentSubTask.value = "Database successfully downloaded!"
            } else {
                task.currentSubTask.value = "Error: Repository structure invalid."
            }
            tempRepoDir.deleteRecursively()
            
            // NEW: Grab every cover from GameTDB
            task.currentSubTask.value = "Fetching covers from GameTDB..."
            val allIds = databaseDir.listFiles { f -> f.isDirectory && f.name.length == 4 }?.map { it.name } ?: emptyList()
            if (allIds.isNotEmpty()) {
                // If we only have 4-char IDs, we attempt with suffix 01
                val guessedFullIds = allIds.map { if (it.length == 4) "${it}01" else it }
                downloadCovers(task, guessedFullIds)
            }

            // Update metadata
            updateSyncMetadata(settingsManager)
            
            task.progress.value = 1f
        } else {
            task.currentSubTask.value = "Clone failed: ${(cloneResult as GitManager.Result.Error).message}"
            task.progress.value = 1f
        }
    }

    /**
     * Downloads only the folders matching the provided Game IDs.
     */
    suspend fun downloadLinkedOnly(task: TaskInfo, filterIds: List<String>, settingsManager: SettingsManager) = coroutineScope {
        if (filterIds.isEmpty()) {
            task.currentSubTask.value = "No mods to download icons for."
            task.progress.value = 1f
            return@coroutineScope
        }

        task.currentSubTask.value = "Fetching repository file list..."
        task.progress.value = -1f

        // NEW: Always download the database JSON if it doesn't exist or to keep it updated
        launch(Dispatchers.IO) {
            val dbJsonUrl = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/database/WiiSave-Database.json"
            downloadFile(dbJsonUrl, File(databaseDir, "WiiSave-Database.json"))
        }

        // 1. Get the repository tree to find folders
        val tree = githubService.getRepositoryTree("$REPO_OWNER/$REPO_NAME", "main")
        val dbFolders = tree.filter { it.type == "tree" && it.path.startsWith("database/") }
        
        // 2. Identify folders that match our IDs (4-char match)
        val targetIds = filterIds.map { it.take(4).uppercase() }.toSet()
        val matchingFolders = dbFolders.filter { folder ->
            val folderId = folder.path.substringAfterLast("/").uppercase()
            targetIds.contains(folderId)
        }

        if (matchingFolders.isEmpty()) {
            task.currentSubTask.value = "No icons found in repo for your synced mods."
            task.progress.value = 1f
            return@coroutineScope
        }

        task.currentSubTask.value = "Downloading ${matchingFolders.size} icon sets..."
        val total = matchingFolders.size
        val processed = AtomicInteger(0)

        // 3. Download icon_animated.png for each matching folder
        val jobs = matchingFolders.map { folder ->
            launch(Dispatchers.IO) {
                if (task.job?.isCancelled == true) return@launch
                
                try {
                    val folderId = folder.path.substringAfterLast("/")
                    val localFolder = File(databaseDir, folderId).apply { mkdirs() }
                    val remoteUrl = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/${folder.path}/icon_animated.png"
                    
                    val destFile = File(localFolder, "icon_animated.png")
                    downloadFile(remoteUrl, destFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    val current = processed.incrementAndGet()
                    task.progress.value = current.toFloat() / total
                    task.currentSubTask.value = "Downloaded $current/$total icons"
                }
            }
        }

        jobs.joinAll()
        
        // NEW: Grab targeted covers from GameTDB using FULL IDs
        task.currentSubTask.value = "Fetching covers from GameTDB..."
        downloadCovers(task, filterIds)

        // Update metadata
        updateSyncMetadata(settingsManager)
        
        task.currentSubTask.value = "Targeted icon sync complete!"
        task.progress.value = 1f
    }

    /**
     * Downloads frontal box art from GameTDB for the provided IDs.
     * GameTDB requires 6-character IDs.
     */
    private suspend fun downloadCovers(task: TaskInfo, ids: List<String>) = coroutineScope {
        val total = ids.size
        val processed = AtomicInteger(0)
        val downloadSemaphore = Semaphore(3)
        // Expanded language support
        val languages = listOf("EN", "US", "IT", "DE", "FR", "ES", "NL", "PT", "JA", "KO", "ZH")

        val jobs = ids.map { fullId ->
            launch(Dispatchers.IO) {
                if (task.job?.isCancelled == true) return@launch
                var success = false
                try {
                    val id6 = fullId.uppercase().trim()
                    // Local folder name is still first 4 characters for organization
                    val folder4 = id6.take(4)
                    val localFolder = File(coversDir, folder4).apply { mkdirs() }
                    val destFile = File(localFolder, "cover.png")
                    
                    if (destFile.exists()) {
                        success = true
                    } else if (id6.length >= 4) {
                        downloadSemaphore.withPermit {
                            // Try multiple languages, replacing only the 4th character for regional variants
                            val prefix = id6.take(3)
                            val suffix = if (id6.length > 4) id6.substring(4) else "01"
                            
                            for (lang in languages) {
                                // Match the region character to the language if possible, 
                                // otherwise iterate standard regional indicators
                                val regionsToTry = when(lang) {
                                    "EN", "US" -> listOf("E", "P")
                                    "JA" -> listOf("J")
                                    "KO" -> listOf("K")
                                    "ZH" -> listOf("W")
                                    else -> listOf("P", "E")
                                }
                                
                                for (region in regionsToTry) {
                                    val targetId6 = "$prefix$region$suffix"
                                    val url = "https://art.gametdb.com/wii/cover/$lang/$targetId6.png"
                                    if (downloadFile(url, destFile)) {
                                        success = true
                                        break
                                    }
                                }
                                if (success) break
                            }
                        }
                    }
                    
                    // Cleanup: If folder is empty (no cover found), remove it
                    if (!success && localFolder.exists() && (localFolder.list()?.isEmpty() == true)) {
                        localFolder.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    val current = processed.incrementAndGet()
                    task.progress.value = current.toFloat() / total
                    task.currentSubTask.value = "Downloaded $current/$total covers"
                }
            }
        }
        jobs.joinAll()
    }

    private fun downloadFile(url: String, destination: File): Boolean {
        return try {
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun updateSyncMetadata(settingsManager: SettingsManager) {
        val iconCount = databaseDir.listFiles { f -> 
            f.isDirectory && f.name.length == 4 && f.name.all { it.isLetterOrDigit() }
        }?.size ?: 0
        
        val coverCount = coversDir.listFiles { f ->
            f.isDirectory && f.name.length == 4 && File(f, "cover.png").exists()
        }?.size ?: 0
        
        android.util.Log.d("RiiSync-DB", "Sync complete. Icons: $iconCount, Covers: $coverCount")
        
        // Ensure state update happens on Main thread for UI consistency
        CoroutineScope(Dispatchers.Main).launch {
            settingsManager.setDbMetadata(System.currentTimeMillis(), iconCount, coverCount)
        }
    }

    /**
     * Updates the existing database by pulling changes or adding missing icons.
     */
    suspend fun updateDatabase(task: TaskInfo, syncedIds: List<String>?, mode: String, settingsManager: SettingsManager) = coroutineScope {
        if (mode == "EVERYTHING") {
            downloadEverything(task, settingsManager)
        } else if (syncedIds != null) {
            downloadLinkedOnly(task, syncedIds, settingsManager)
        }
    }

    fun clearDatabase(settingsManager: SettingsManager) {
        if (databaseDir.exists()) {
            databaseDir.deleteRecursively()
            databaseDir.mkdirs()
            coversDir.mkdirs()
            try { File(databaseDir, ".nomedia").createNewFile() } catch (e: Exception) {}
            // Reset metadata
            settingsManager.setDbMetadata(0L, 0, 0)
        }
    }
}
