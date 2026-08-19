/**
 * Global Task Management ViewModel.
 * This file contains the ViewModel that coordinates long-running operations across the app,
 * such as Git clones, pulls, and filesystem synchronization, including network monitoring
 * and background service integration.
 */
package com.riisync.app.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riisync.app.GitService
import com.riisync.app.R
import com.riisync.app.git.GitHubService
import com.riisync.app.shizuku.IFileService
import com.riisync.app.shizuku.RiivolutionLinker
import com.riisync.app.shizuku.ShizukuHelper
import com.riisync.app.utils.SettingsManager
import com.riisync.app.grabber.GameDatabaseBuilder
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import java.net.URL
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.util.UUID

/**
 * Task Type to distinguish between Git and other operations.
 */
enum class TaskType { GIT, MOD, SYSTEM }

/**
 * Information about a specific task.
 */
data class TaskInfo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: TaskType,
    val cleanupPath: String? = null,
    val isNewClone: Boolean = false,
    val progress: MutableState<Float> = mutableStateOf(0f),
    val currentSubTask: MutableState<String> = mutableStateOf(""),
    val task: (suspend (org.eclipse.jgit.lib.ProgressMonitor) -> Unit)? = null,
    val simpleTask: (suspend (TaskInfo) -> Unit)? = null,
    val isSimple: Boolean = false,
    var job: Job? = null
)

/**
 * Information about a mod that has been validated but not yet synced.
 */
data class PendingMod(
    val riivolutionFolder: File,
    val modFolder: File,
    val xmlFile: File,
    val validationMessage: String,
    val modRootPath: String,
    val gameId: String? = null,
    val gameTitle: String? = null
)

/**
 * ViewModel that provides shared state for background operations and notifications.
 */
class GlobalTaskViewModel(application: Application) : AndroidViewModel(application) {

    /** Pending mod configuration that survives tab switching. */
    var pendingMod by mutableStateOf<PendingMod?>(null)
    var pendingModName by mutableStateOf("")

    /** All currently running tasks. */
    val activeTasks = mutableStateListOf<TaskInfo>()
    /** Tasks waiting in the queue. */
    val queuedTasks = mutableStateListOf<TaskInfo>()

    /** Computed state for backward compatibility and high-level UI. */
    val isOperating get() = activeTasks.isNotEmpty()
    val progressPercent get() = activeTasks.firstOrNull()?.progress?.value ?: 0f
    val progressTask get() = activeTasks.firstOrNull()?.title ?: ""
    val waitingTasksCount get() = queuedTasks.size

    /** Tabs that require user attention (e.g., Modding tab waiting for a name). */
    val tabsRequiringAttention = mutableStateListOf<Int>()

    fun setTabAttention(tabIndex: Int, needsAttention: Boolean) {
        if (needsAttention) {
            if (!tabsRequiringAttention.contains(tabIndex)) tabsRequiringAttention.add(tabIndex)
        } else {
            tabsRequiringAttention.remove(tabIndex)
        }
    }

    /** Global notification message to be displayed in a banner. */
    var notificationMessage by mutableStateOf<String?>(null)
    /** Indicates if the current notification is an error. */
    var isErrorNotification by mutableStateOf(false)

    /** GitHub Login Status */
    var isTokenValid by mutableStateOf<Boolean?>(null)

    /** PERSISTENT GIT TAB STATE - survives tab switching */
    var activeRepoForDetails by mutableStateOf<GitHubService.RepoInfo?>(null)
    var viewingUserProfile by mutableStateOf<GitHubService.UserProfile?>(null)
    var isViewingChanges by mutableStateOf(false)
    
    // Git Search State
    var searchQuery by mutableStateOf("")
    var localSearchQuery by mutableStateOf("")
    var searchModeAll by mutableStateOf(false)
    var searchCategory by mutableStateOf("Repositories")
    var searchRepos by mutableStateOf<List<GitHubService.RepoInfo>>(emptyList())
    var userRepos by mutableStateOf<List<GitHubService.RepoInfo>>(emptyList())
    var searchUsers by mutableStateOf<List<GitHubService.UserProfile>>(emptyList())
    var currentSearchPage by mutableIntStateOf(1)
    var canLoadMoreSearch by mutableStateOf(true)

    // Git Changes State
    var selectedPaths by mutableStateOf(setOf<String>())
    var commitTitle by mutableStateOf("")
    var commitDescription by mutableStateOf("")
    var localChanges by mutableStateOf<List<com.riisync.app.git.GitManager.LocalChange>>(emptyList())
    var activeRepoStats by mutableStateOf<Map<String, String>>(emptyMap())
    var activeCollaborators by mutableStateOf<List<com.riisync.app.git.GitHubService.UserProfile>>(emptyList())
    var activeRepoDetailsTab by mutableIntStateOf(0)

    // Collaborator Cache: fullName -> isCollaborator
    val collaboratorCache = mutableStateMapOf<String, Boolean>()

    /** Cache for human-readable game titles fetched from GitHub. */
    private var gameTitlesMap = mutableStateMapOf<String, String>()

    /**
     * Refreshes the game titles mapping by fetching gameid_names.txt from GitHub.
     */
    fun refreshGameTitles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/Im-Monee/RiiSync-DB/main/database/gameid_names.txt")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val newMap = mutableMapOf<String, String>()
                    content.lines().forEach { line ->
                        if (line.contains(":")) {
                            val parts = line.split(":", limit = 2)
                            val id = parts[0].trim().take(4).uppercase()
                            val name = parts[1].trim()
                            if (id.isNotEmpty() && name.isNotEmpty()) {
                                newMap[id] = name
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        gameTitlesMap.clear()
                        gameTitlesMap.putAll(newMap)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RiiSync", "Failed to refresh game titles from GitHub", e)
            }
        }
    }

    /**
     * Attempts to retrieve a human-readable game title for a given Game ID.
     * Prioritizes the live GitHub mapping over the local JSON database.
     */
    fun getGameTitle(gameId: String?): String {
        if (gameId == null) return "Unknown Game"
        val cleanId = gameId.take(4).uppercase()
        
        // 1. Try Live Cache (from gameid_names.txt)
        gameTitlesMap[cleanId]?.let { return it }
        
        // 2. Fallback to Local JSON (WiiSave-Database.json)
        val baseRiisyncDir = File(android.os.Environment.getExternalStorageDirectory(), "RiiSync")
        val gamesJson = File(baseRiisyncDir, "database/WiiSave-Database.json")
        
        if (gamesJson.exists()) {
            try {
                val json = JSONObject(gamesJson.readText())
                if (json.has(cleanId)) {
                    val title = json.getJSONObject(cleanId).optString("title", "")
                    if (title.isNotBlank()) return title
                }
            } catch (e: Exception) {
                android.util.Log.e("RiiSync", "Failed to parse WiiSave-Database.json", e)
            }
        }
        return gameId // Ultimate Fallback to raw ID
    }

    /** PERSISTENT MODDING TAB STATE */
    val externalFolders = mutableStateListOf<String>()

    /** PERSISTENT ABOUT TAB STATE */
    var latestAppVersion by mutableStateOf<String?>(null)
    var appUpdateAvailable by mutableStateOf(false)
    var isCheckingForUpdates by mutableStateOf(false)

    /**
     * Checks for application updates from the GitHub repository.
     */
    fun checkForAppUpdates(settingsManager: SettingsManager, quiet: Boolean = false) {
        if (isCheckingForUpdates) return
        isCheckingForUpdates = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val service = GitHubService()
                val latest = service.getLatestReleaseVersion(settingsManager.token.value.ifBlank { null })
                val current = com.riisync.app.BuildConfig.VERSION_NAME
                
                withContext(Dispatchers.Main) {
                    latestAppVersion = latest
                    if (latest != null && latest != current && latest.isNotEmpty()) {
                        appUpdateAvailable = true
                        if (!quiet) notify("New version $latest is available!", false)
                        setTabAttention(2, true) // Highlight Settings tab
                    } else if (!quiet) {
                        notify("RiiSync is up to date.", false)
                    }
                    isCheckingForUpdates = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!quiet) notify("Failed to check for updates: ${e.message}", true)
                    isCheckingForUpdates = false
                }
            }
        }
    }

    /**
     * Downloads and installs the latest application update.
     */
    fun performAppUpdate(settingsManager: SettingsManager) {
        val context = getApplication<Application>()
        
        runSimpleTask("Updating RiiSync", type = TaskType.SYSTEM) { task ->
            com.riisync.app.utils.ApkDownloader.downloadFromGitHub(
                context = context,
                owner = "Im-Monee",
                repo = "RiiSync",
                fileName = "riisync_update.apk",
                token = settingsManager.token.value.ifBlank { null },
                onProgress = { task.progress.value = it },
                onComplete = { file ->
                    task.currentSubTask.value = "Starting installer..."
                    com.riisync.app.utils.ApkDownloader.installApk(context, file)
                },
                onError = { error ->
                    notify("Update failed: $error", true)
                }
            )
        }
    }

    /** Countdown timer for network reconnection attempts. */
    var connectionLostTimer by mutableIntStateOf(0)
    /** Indicates if the network connection is currently lost during an operation. */
    var isConnectionLost by mutableStateOf(false)

    /** Live WiFi connection status. */
    var wifiConnected by mutableStateOf(true)
        private set

    /** Live Internet connection status (WiFi or Mobile). */
    var internetConnected by mutableStateOf(true)
        private set

    /** Track mod directory states for the watcher. */
    private val modLastModified = mutableMapOf<String, Long>()

    private val activePaths = mutableStateListOf<String>()

    init {
        // Advanced Queue Processor
        // - GIT and SYSTEM (Downloads) are sequential with each other to avoid connection issues.
        // - MOD tasks are parallel and can start immediately.
        viewModelScope.launch {
            while (true) {
                if (queuedTasks.isNotEmpty()) {
                    // We need a snapshot because queuedTasks is a mutable state list
                    val snapshot = queuedTasks.toList()
                    
                    for (task in snapshot) {
                        val isHeavy = task.type == TaskType.GIT || 
                                     (task.type == TaskType.SYSTEM && task.title.contains("Dolphin", ignoreCase = true))
                        
                        val canStart = if (isHeavy) {
                            // Heavy tasks wait for other heavy tasks to finish
                            !activeTasks.any { 
                                it.type == TaskType.GIT || 
                                (it.type == TaskType.SYSTEM && it.title.contains("Dolphin", ignoreCase = true)) 
                            }
                        } else {
                            // Other tasks (MOD, SYSTEM utilities) can start immediately
                            true
                        }

                        if (canStart) {
                            queuedTasks.remove(task)
                            startTaskExecution(task)
                            break // Process next in next tick
                        }
                    }
                }
                delay(1000)
            }
        }

        // Live Network Watcher for connection status
        viewModelScope.launch {
            var lastInternetState = true
            var lastWifiState = true
            var internetDisconnectCount = 0
            var wifiDisconnectCount = 0
            
            while(true) {
                val currentWifi = isWifiConnectedInternal()
                val currentInternet = isInternetConnectedInternal()
                
                // 1. Internet Loss Trigger (WiFi + Mobile) with Debounce
                if (lastInternetState && !currentInternet) {
                    internetDisconnectCount++
                    if (internetDisconnectCount >= 4) { // Persistent loss (approx 12-15s)
                        if (activeTasks.any { it.type == TaskType.GIT }) {
                            notify(getApplication<Application>().getString(R.string.connection_lost_alert), true)
                        }
                        
                        if (internetDisconnectCount >= 6) { 
                            activeRepoForDetails = null
                            viewingUserProfile = null
                            isViewingChanges = false
                            lastInternetState = false
                        }
                    }
                } else if (currentInternet) {
                    internetDisconnectCount = 0
                    lastInternetState = true
                }

                // 2. WiFi Loss Trigger (for Mod Linking) with Debounce
                if (lastWifiState && !currentWifi) {
                    wifiDisconnectCount++
                    // We only notify and cleanup if the disconnection is persistent
                    if (wifiDisconnectCount >= 3) { // approx 9-10s
                        pendingMod?.let { mod ->
                            // ONLY clear if an active MOD task is running. 
                            // Otherwise, let the user stay on the config screen; the button will handle the state.
                            if (activeTasks.any { it.type == TaskType.MOD }) {
                                notify(getApplication<Application>().getString(R.string.shizuku_needs_wifi), true)
                                cleanupPendingModLinks(mod)
                                pendingMod = null
                                pendingModName = ""
                                setTabAttention(1, false)
                            }
                        }
                        lastWifiState = false
                    }
                } else if (currentWifi) {
                    wifiDisconnectCount = 0
                    lastWifiState = true
                }
                
                wifiConnected = currentWifi
                internetConnected = currentInternet
                delay(3000)
            }
        }
    }

    /**
     * Cleans up partially created Dolphin links if a session is interrupted.
     */
    private fun cleanupPendingModLinks(mod: PendingMod) {
        viewModelScope.launch(Dispatchers.IO) {
            val dolphinVersions = listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr")
            dolphinVersions.forEach { pkg ->
                try {
                    val dolphinBase = RiivolutionLinker.dolphinRiivolutionPath(pkg)
                    val service = ShizukuHelper.fileService
                    if (service != null) {
                        // Remove XML from riivolution subfolder
                        service.remove(File(dolphinBase, "riivolution/${mod.xmlFile.name}").absolutePath)
                        // Remove Mod Data Folder
                        service.remove(File(dolphinBase, mod.modFolder.name).absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Starts the live mod directory watcher.
     */
    fun startModWatcher(settingsManager: SettingsManager) {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val mods = settingsManager.installedMods.value
                mods.forEach { mod ->
                    val root = File(mod.sourcePath)
                    if (root.exists()) {
                        val currentStamp = getRecursiveLastModified(root)
                        val lastStamp = modLastModified[mod.id()] ?: 0L
                        
                        if (lastStamp != 0L && currentStamp > lastStamp) {
                            // Change detected!
                            withContext(Dispatchers.Main) {
                                triggerModSync(mod, settingsManager)
                            }
                        }
                        modLastModified[mod.id()] = currentStamp
                    }
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    private fun getRecursiveLastModified(file: File): Long {
        var last = file.lastModified()
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                val sub = getRecursiveLastModified(it)
                if (sub > last) last = sub
            }
        }
        return last
    }

    private fun SettingsManager.ManagedMod.id() = "$name|$sourcePath"

    /**
     * Triggers a sync for a specific mod across all installed Dolphin versions.
     */
    fun triggerModSync(mod: SettingsManager.ManagedMod, settingsManager: SettingsManager) {
        if (isPathActive(mod.sourcePath)) return // Already busy

        val context = getApplication<Application>()
        runSimpleTask(context.getString(R.string.mod_auto_sync_title, mod.name), mod.sourcePath, type = TaskType.MOD) { task ->
            val dolphinVersions = listOf(
                "org.dolphinemu.dolphinemu",
                "org.dolphinemu.mmjr"
            )

            dolphinVersions.forEach { pkg ->
                try {
                    val label = if (pkg.contains("mmjr")) "MMJR2" else "Official"
                    task.currentSubTask.value = context.getString(R.string.mod_syncing_with, label)
                    
                    val xmlPath = File(mod.sourcePath, "${mod.riivolutionFolderName}/${mod.xmlFileName}").absolutePath
                    val dataPath = File(mod.sourcePath, mod.modFilesFolderName).absolutePath
                    
                    RiivolutionLinker.linkXmlConfig(xmlPath, mod.xmlFileName, pkg = pkg)
                    RiivolutionLinker.linkModFolder(dataPath, mod.modFilesFolderName, pkg = pkg)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            task.progress.value = 1f
            task.currentSubTask.value = context.getString(R.string.mod_sync_complete)
            delay(1000)
            
            // Update sync count in settings
            val updated = settingsManager.getInstalledMods().toMutableList()
            val index = updated.indexOfFirst { it.name == mod.name }
            if (index != -1) {
                val m = updated[index]
                updated[index] = m.copy(syncCount = m.syncCount + 1, lastSyncTimestamp = System.currentTimeMillis())
                settingsManager.saveInstalledMods(updated)
            }
        }
    }

    /**
     * Synchronizes all managed mods with Dolphin.
     */
    fun syncAllMods(settingsManager: SettingsManager) {
        val mods = settingsManager.installedMods.value
        if (mods.isEmpty()) return
        
        val context = getApplication<Application>()
        runSimpleTask(context.getString(R.string.managed_mods), type = TaskType.MOD) { task ->
            mods.forEachIndexed { index, mod ->
                task.currentSubTask.value = "Syncing ${mod.name} (${index + 1}/${mods.size})"
                task.progress.value = index.toFloat() / mods.size

                val dolphinVersions = listOf(
                    "org.dolphinemu.dolphinemu",
                    "org.dolphinemu.mmjr"
                )

                dolphinVersions.forEach { pkg ->
                    try {
                        val xmlPath = File(mod.sourcePath, "${mod.riivolutionFolderName}/${mod.xmlFileName}").absolutePath
                        val dataPath = File(mod.sourcePath, mod.modFilesFolderName).absolutePath
                        
                        RiivolutionLinker.linkXmlConfig(xmlPath, mod.xmlFileName, pkg = pkg)
                        RiivolutionLinker.linkModFolder(dataPath, mod.modFilesFolderName, pkg = pkg)
                    } catch (e: Exception) {
                        Log.e("GlobalTaskViewModel", "Sync failed for ${mod.name} on $pkg", e)
                    }
                }
            }
            task.progress.value = 1f
            task.currentSubTask.value = "All mods synchronized!"
            delay(1500)
        }
    }

    /**
     * Triggers a global notification.
     */
    fun notify(message: String, isError: Boolean = false) {
        notificationMessage = message
        isErrorNotification = isError
    }

    /**
     * Clears the current global notification.
     */
    fun clearNotification() {
        notificationMessage = null
    }

    /**
     * Checks if the device is connected to WiFi (Internal helper).
     * Now considers "Connected without internet" as disconnected for sync purposes.
     */
    private fun isWifiConnectedInternal(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            isWifi && isValidated
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the network is available and GitHub is reachable.
     */
    private suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@withContext false
            
            val hasCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            if (!hasCapability || !isValidated) return@withContext false

            val address = InetAddress.getByName("github.com")
            !address.hostAddress.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Cancels a specific task by ID.
     */
    fun cancelTask(id: String) {
        val task = activeTasks.find { it.id == id } ?: queuedTasks.find { it.id == id }
        if (task != null) {
            task.job?.cancel()
            activeTasks.remove(task)
            queuedTasks.remove(task)
            task.cleanupPath?.let { activePaths.remove(it) }
            
            if (activeTasks.isEmpty()) GitService.stop(getApplication())
            notify("Task '${task.title}' cancelled.", false)
        }
    }

    /**
     * Move a task to the front of its queue (Priority change).
     */
    fun promoteTask(id: String) {
        val index = queuedTasks.indexOfFirst { it.id == id }
        if (index > 0) {
            val task = queuedTasks.removeAt(index)
            queuedTasks.add(0, task)
        }
    }

    private fun startTaskExecution(taskInfo: TaskInfo) {
        activeTasks.add(taskInfo)
        
        val job = if (taskInfo.isSimple) {
            executeSimpleTask(taskInfo)
        } else {
            executeTask(taskInfo)
        }
        taskInfo.job = job
    }

    private fun executeTask(taskInfo: TaskInfo): Job {
        val monitor = object : org.eclipse.jgit.lib.ProgressMonitor {
            private var totalWork = 0
            private var completedWork = 0
            override fun start(totalTasks: Int) {}
            override fun beginTask(title: String?, totalWork: Int) {
                this.totalWork = totalWork
                this.completedWork = 0
                if (title != null) taskInfo.currentSubTask.value = title
                taskInfo.progress.value = if (totalWork > 0) 0f else -1f
                GitService.update(getApplication(), taskInfo.title, taskInfo.progress.value)
            }
            override fun update(completed: Int) {
                completedWork += completed
                if (totalWork > 0) {
                    taskInfo.progress.value = completedWork.toFloat() / totalWork
                    GitService.update(getApplication(), taskInfo.title, taskInfo.progress.value)
                }
            }
            override fun endTask() { taskInfo.progress.value = 1f }
            override fun isCancelled(): Boolean = false
            override fun showDuration(enabled: Boolean) {}
        }

        return viewModelScope.launch(Dispatchers.IO) {
            try {
                GitService.start(getApplication(), taskInfo.title)
                taskInfo.task?.invoke(monitor)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isNetworkAvailable()) {
                        handleConnectionTimeout(taskInfo)
                    } else {
                        notify(getApplication<Application>().getString(R.string.task_error_format, taskInfo.title, e.message ?: ""), true)
                    }
                }
            } finally {
                activeTasks.remove(taskInfo)
                taskInfo.cleanupPath?.let { activePaths.remove(it) }
                
                // NEW: Trigger attention dot on completion if it's a major operation
                withContext(Dispatchers.Main) {
                    val tabIndex = when(taskInfo.type) {
                        TaskType.GIT -> 0
                        TaskType.MOD -> 1
                        TaskType.SYSTEM -> 2
                    }
                    setTabAttention(tabIndex, true)
                }
                
                if (activeTasks.isEmpty()) GitService.stop(getApplication())
            }
        }
    }

    private fun executeSimpleTask(taskInfo: TaskInfo): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                GitService.start(getApplication(), taskInfo.title)
                
                // Track progress changes to update system notification
                val progressJob = launch {
                    snapshotFlow { taskInfo.progress.value }.collect { p ->
                        GitService.update(getApplication(), taskInfo.title, p)
                    }
                }
                
                taskInfo.simpleTask?.invoke(taskInfo)
                progressJob.cancel()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    notify(getApplication<Application>().getString(R.string.system_error_format, taskInfo.title, e.message ?: ""), true)
                }
            } finally {
                activeTasks.remove(taskInfo)
                taskInfo.cleanupPath?.let { activePaths.remove(it) }
                
                // NEW: Trigger attention dot for simple tasks too
                withContext(Dispatchers.Main) {
                    val tabIndex = when(taskInfo.type) {
                        TaskType.GIT -> 0
                        TaskType.MOD -> 1
                        TaskType.SYSTEM -> 2
                    }
                    setTabAttention(tabIndex, true)
                }
                
                if (activeTasks.isEmpty()) GitService.stop(getApplication())
            }
        }
    }

    /**
     * Handles connection loss for a specific task.
     */
    private suspend fun handleConnectionTimeout(taskInfo: TaskInfo) {
        val context = getApplication<Application>()
        isConnectionLost = true
        connectionLostTimer = 10
        
        while (connectionLostTimer > 0) {
            delay(1000)
            if (isNetworkAvailable()) {
                isConnectionLost = false
                return
            }
            connectionLostTimer--
        }
        
        taskInfo.cleanupPath?.let { 
            val file = File(it)
            if (file.exists()) file.deleteRecursively()
        }
        notify(context.getString(R.string.operation_failed_timeout), true)
        isConnectionLost = false
    }

    /**
     * Aborts all active and queued tasks, performing necessary cleanup.
     * Triggered when critical system permissions are revoked.
     */
    fun abortAllTasks() {
        val snapshot = activeTasks.toList()
        activeTasks.clear()
        queuedTasks.clear()
        
        snapshot.forEach { task ->
            task.job?.cancel()
            
            // Perform cleanup based on task type
            viewModelScope.launch(Dispatchers.IO) {
                when(task.type) {
                    TaskType.GIT -> {
                        // Delete leftover repository files if it was a new clone
                        if (task.isNewClone && task.cleanupPath != null) {
                            val dir = File(task.cleanupPath)
                            if (dir.exists()) dir.deleteRecursively()
                        }
                    }
                    TaskType.MOD -> {
                        // Delete partial Dolphin link files
                        if (task.cleanupPath != null) {
                            val dolphinVersions = listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr")
                            dolphinVersions.forEach { pkg ->
                                try {
                                    val dolphinBase = RiivolutionLinker.dolphinRiivolutionPath(pkg)
                                    val service = ShizukuHelper.fileService
                                    if (service != null) {
                                        // Try to remove potential link paths
                                        val modName = File(task.cleanupPath).name
                                        service.remove(File(dolphinBase, "riivolution/$modName.xml").absolutePath)
                                        service.remove(File(dolphinBase, modName).absolutePath)
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }
                    else -> {}
                }
                
                withContext(Dispatchers.Main) {
                    task.cleanupPath?.let { activePaths.remove(it) }
                }
            }
        }
        
        // Reset major state
        pendingMod = null
        pendingModName = ""
        tabsRequiringAttention.clear()
        
        GitService.stop(getApplication())
    }

    private fun normalizePath(path: String): String {
        return try { File(path).canonicalPath } catch (e: Exception) { path }
    }

    fun isPathActive(path: String): Boolean {
        val normalized = normalizePath(path)
        val inActive = activeTasks.any { it.cleanupPath?.let { p -> normalizePath(p) == normalized } ?: false }
        val inQueue = queuedTasks.any { it.cleanupPath?.let { p -> normalizePath(p) == normalized } ?: false }
        return inActive || inQueue
    }

    /**
     * Manually triggers a network state refresh.
     */
    fun refreshNetworkState() {
        wifiConnected = isWifiConnectedInternal()
        internetConnected = isInternetConnectedInternal()
    }

    /**
     * Runs a complex task with progress monitoring.
     */
    fun runTask(
        title: String, 
        cleanupPath: String? = null, 
        isNewClone: Boolean = false,
        type: TaskType = TaskType.GIT,
        task: suspend (org.eclipse.jgit.lib.ProgressMonitor) -> Unit
    ) {
        val context = getApplication<Application>()
        if (cleanupPath != null && isPathActive(cleanupPath)) {
            notify(context.getString(R.string.task_already_active, title), false)
            return
        }

        val taskInfo = TaskInfo(title = title, type = type, cleanupPath = cleanupPath, isNewClone = isNewClone, task = task)
        cleanupPath?.let { activePaths.add(it) }

        if (type == TaskType.GIT) {
            queuedTasks.add(taskInfo)
            if (activeTasks.any { it.type == TaskType.GIT }) {
                notify(context.getString(R.string.task_queued, title), false)
            }
        } else {
            startTaskExecution(taskInfo)
        }
    }
    
    /**
     * Runs a simple task without detailed progress updates.
     */
    fun runSimpleTask(title: String, path: String? = null, type: TaskType = TaskType.MOD, task: suspend (TaskInfo) -> Unit) {
        val context = getApplication<Application>()
        if (path != null && isPathActive(path)) {
            notify(context.getString(R.string.task_already_active, title), false)
            return
        }

        val taskInfo = TaskInfo(title = title, type = type, cleanupPath = path, simpleTask = task, isSimple = true)
        path?.let { activePaths.add(it) }

        if (type == TaskType.GIT) {
            queuedTasks.add(taskInfo)
        } else {
            startTaskExecution(taskInfo)
        }
    }

    /**
     * Checks if the device has any internet connection (WiFi or Mobile).
     */
    private fun isInternetConnectedInternal(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts fresh Game IDs from mod XMLs and builds the targeted icon database.
     */
    fun buildTargetedIconDatabase(settingsManager: SettingsManager, mods: List<SettingsManager.ManagedMod>) {
        viewModelScope.launch(Dispatchers.IO) {
            val freshIds = mods.mapNotNull { mod ->
                var detectedId: String? = null
                try {
                    val xmlFile = File(mod.sourcePath, "${mod.riivolutionFolderName}/${mod.xmlFileName}")
                    if (xmlFile.exists()) {
                        val content = xmlFile.readText()
                        detectedId = com.riisync.app.shizuku.RiivolutionLinker.extractGameId(content)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RiiSync", "Failed to read XML for ${mod.name}", e)
                }
                detectedId ?: mod.gameId
            }.distinct()

            withContext(Dispatchers.Main) {
                if (freshIds.isNotEmpty()) {
                    buildGameDatabase(settingsManager, freshIds, mode = "LINKED")
                }
            }
        }
    }

    /**
     * Specialized sync for a single pending mod (unlinked yet).
     */
    fun buildIconForPendingMod(settingsManager: SettingsManager, mod: PendingMod) {
        val gameId = mod.gameId ?: return
        buildGameDatabase(settingsManager, listOf(gameId), mode = "LINKED")
    }

    /**
     * Rebuilds or updates the Wii Save Icon Database.
     * @param filterIds If provided, only downloads/updates icons for these Game IDs.
     * @param mode "EVERYTHING" or "LINKED"
     */
    fun buildGameDatabase(settingsManager: SettingsManager, filterIds: List<String>? = null, mode: String = "EVERYTHING") {
        val context = getApplication<Application>()
        val builder = GameDatabaseBuilder(context)
        
        val title = if (mode == "LINKED") "Syncing Mod Icons" else "Building Save Icon DB"
        
        runSimpleTask(title, type = TaskType.SYSTEM) { task ->
            if (mode == "EVERYTHING") {
                builder.downloadEverything(task, settingsManager)
            } else if (filterIds != null) {
                builder.downloadLinkedOnly(task, filterIds, settingsManager)
            }
        }
    }

    /**
     * Clears the local Wii Save Icon Database.
     */
    fun clearGameDatabase(settingsManager: SettingsManager) {
        val context = getApplication<Application>()
        val builder = GameDatabaseBuilder(context)
        builder.clearDatabase(settingsManager)
    }

    /**
     * Periodically check token validity if one is set.
     */
    fun checkGitHubConnection(settingsManager: SettingsManager) {
        val token = settingsManager.token.value.trim()
        if (token.isBlank()) {
            isTokenValid = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val githubService = GitHubService()
            // We fetch the profile to verify the token AND get the username
            val profile = githubService.getUserProfile("", token) 
            withContext(Dispatchers.Main) {
                isTokenValid = profile != null
                if (profile != null) {
                    settingsManager.setGitHubCredentials(profile.login, token)
                }
            }
        }
    }
}
