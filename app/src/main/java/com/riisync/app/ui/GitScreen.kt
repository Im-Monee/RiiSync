/**
 * GitHub Management Hub.
 * This file contains the primary Git interface for the app, including repository searching,
 * cloning, branch management, and detailed repository information displays.
 */
package com.riisync.app.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.riisync.app.R
import com.riisync.app.git.GitHubService
import com.riisync.app.git.GitManager
import com.riisync.app.git.TokenManager
import com.riisync.app.utils.PathUtils
import com.riisync.app.utils.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main Git screen providing an adaptive list-detail layout.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GitScreen(taskViewModel: GlobalTaskViewModel, settingsManager: SettingsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gitManager = remember { GitManager() }
    val githubService = remember { GitHubService() }

    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()

    // Use ViewModel state for persistent navigation
    val activeRepoForDetails = taskViewModel.activeRepoForDetails
    val viewingUserProfile = taskViewModel.viewingUserProfile
    val isViewingChanges = taskViewModel.isViewingChanges

    // Ensure navigator matches ViewModel state on return
    LaunchedEffect(activeRepoForDetails, viewingUserProfile, isViewingChanges) {
        if (activeRepoForDetails != null || viewingUserProfile != null || isViewingChanges) {
            // Check if we are currently in list mode and should be in detail
            if (navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == androidx.compose.material3.adaptive.layout.PaneAdaptedValue.Hidden) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            }
        }
    }

    var showCloneDialogForRepo by remember { mutableStateOf<GitHubService.RepoInfo?>(null) }
    var showDeleteRepoConfirm by remember { mutableStateOf<String?>(null) }
    
    val onCloneRequest: (GitHubService.RepoInfo) -> Unit = { repo ->
        showCloneDialogForRepo = repo
    }

    BackHandler(navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            GitListPane(
                taskViewModel = taskViewModel,
                githubService = githubService,
                settingsManager = settingsManager,
                onRepoClick = { repo ->
                    taskViewModel.activeRepoForDetails = repo
                    taskViewModel.viewingUserProfile = null
                    taskViewModel.isViewingChanges = false
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                },
                onUserClick = { user ->
                    scope.launch {
                        val profile = githubService.getUserProfile(user.login, settingsManager.token.value.ifBlank { null })
                        if (profile != null) {
                            taskViewModel.viewingUserProfile = profile
                            taskViewModel.activeRepoForDetails = null
                            taskViewModel.isViewingChanges = false
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                        } else {
                            taskViewModel.notify(context.getString(R.string.user_not_found), true)
                        }
                    }
                },
                onViewChanges = {
                    taskViewModel.isViewingChanges = true
                    taskViewModel.activeRepoForDetails = null
                    taskViewModel.viewingUserProfile = null
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                },
                onClone = onCloneRequest,
                onDelete = { path -> showDeleteRepoConfirm = path }
            )
        },
        detailPane = {
            val token = settingsManager.token.value
            val username = settingsManager.username.value
            val localPath = settingsManager.getLastGitPath()

            when {
                isViewingChanges && localPath.isNotBlank() -> {
                    IntegratedChangesView(
                        localPath = localPath,
                        taskViewModel = taskViewModel,
                        gitManager = gitManager,
                        username = username,
                        token = token,
                        onBack = { 
                            taskViewModel.isViewingChanges = false
                            if (navigator.canNavigateBack()) {
                                scope.launch { navigator.navigateBack() }
                            }
                        }
                    )
                }
                viewingUserProfile != null -> {
                    UserProfileView(
                        user = viewingUserProfile!!,
                        token = token,
                        githubService = githubService,
                        taskViewModel = taskViewModel,
                        settingsManager = settingsManager,
                        onBack = { 
                            taskViewModel.viewingUserProfile = null
                            // Only navigate back if we don't have a repo to show underneath
                            if (taskViewModel.activeRepoForDetails == null && navigator.canNavigateBack()) {
                                scope.launch { navigator.navigateBack() }
                            }
                        },
                        onRepoDetails = { repo ->
                            taskViewModel.activeRepoForDetails = repo
                            taskViewModel.viewingUserProfile = null
                        },
                        onClone = { showCloneDialogForRepo = it }
                    )
                }
                activeRepoForDetails != null -> {
                    RepoDetailsView(
                        repo = activeRepoForDetails!!,
                        token = token,
                        githubService = githubService,
                        gitManager = gitManager,
                        settingsManager = settingsManager,
                        taskViewModel = taskViewModel,
                        onBack = { 
                            taskViewModel.activeRepoForDetails = null
                            if (navigator.canNavigateBack()) {
                                scope.launch { navigator.navigateBack() }
                            }
                        },
                        onUserClick = { user ->
                            scope.launch {
                                val profile = githubService.getUserProfile(user.login, settingsManager.token.value.ifBlank { null })
                                if (profile != null) {
                                    taskViewModel.viewingUserProfile = profile
                                    taskViewModel.isViewingChanges = false
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                                } else {
                                    taskViewModel.notify(context.getString(R.string.user_not_found), true)
                                }
                            }
                        }
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.select_repo_from_git), color = Color.Gray)
                        }
                    }
                }
            }
        }
    )

    showCloneDialogForRepo?.let { repo ->
        CloneDialog(
            repo = repo,
            settingsManager = settingsManager,
            onDismiss = { showCloneDialogForRepo = null },
            onConfirm = { path ->
                showCloneDialogForRepo = null
                val targetDir = File(path)
                taskViewModel.runTask(context.getString(R.string.cloning_repo_format, repo.name), targetDir.absolutePath, isNewClone = true, type = TaskType.GIT) { monitor ->
                    val res = gitManager.clone(repo.cloneUrl, targetDir, settingsManager.username.value, settingsManager.token.value, null, monitor)
                    if (res is GitManager.Result.Success) {
                        settingsManager.setLastGitPath(targetDir.absolutePath)
                    }
                    taskViewModel.notify(toLogString(res, context), res is GitManager.Result.Error)
                }
            }
        )
    }

    showDeleteRepoConfirm?.let { path ->
        val repoFolder = File(path)
        val rootFolder = settingsManager.rootCloneFolder.value
        
        AlertDialog(
            onDismissRequest = { showDeleteRepoConfirm = null },
            title = { Text("Delete Repository") },
            text = { Text("Are you sure you want to delete \u0027${repoFolder.name}\u0027?\n\nThis will remove the local files and any associated Dolphin links.") },
            confirmButton = {
                Button(onClick = {
                    taskViewModel.runSimpleTask(context.getString(R.string.deleting)) {
                        val normalizedPath = File(path).canonicalPath
                        val normalizedRoot = File(rootFolder).canonicalPath

                        // CRITICAL: Safety check to prevent deleting the root Github folder
                        if (normalizedPath == normalizedRoot || path.isEmpty() || !repoFolder.exists()) {
                            taskViewModel.notify(context.getString(R.string.safety_check_failed), true)
                            showDeleteRepoConfirm = null
                            return@runSimpleTask
                        }

                        // Remove associated Dolphin links (Managed Mods)
                        val installedMods = settingsManager.getInstalledMods()
                        val modsToRemove = installedMods.filter { it.sourcePath.startsWith(path) }
                        
                        modsToRemove.forEach { mod ->
                            settingsManager.removeInstalledMod(mod.name)
                        }

                        // Delete physical files
                        repoFolder.deleteRecursively()
                        
                        // Remove from bookmarks (this will auto-select the next repo if needed)
                        settingsManager.removeBookmark(path)
                        
                        val nextRepo = settingsManager.getLastGitPath()
                        val nextMsg = if (nextRepo.isNotEmpty()) "\nSwitching to: ${File(nextRepo).name}" else ""

                        if (modsToRemove.isNotEmpty()) {
                            taskViewModel.notify(context.getString(R.string.repo_mods_deleted_format, modsToRemove.size, nextMsg), false)
                        } else {
                            taskViewModel.notify(context.getString(R.string.repo_deleted_success_format, nextMsg), false)
                        }
                        
                        showDeleteRepoConfirm = null
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteRepoConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/**
 * The list pane for [GitScreen], managing searching and account status.
 */
@Composable
fun GitListPane(
    taskViewModel: GlobalTaskViewModel,
    githubService: GitHubService,
    settingsManager: SettingsManager,
    onRepoClick: (GitHubService.RepoInfo) -> Unit,
    onUserClick: (GitHubService.UserProfile) -> Unit,
    onViewChanges: () -> Unit,
    onClone: (GitHubService.RepoInfo) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gitManager = remember { GitManager() }

    var hasFullStorageAccess by remember {
        mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) hasFullStorageAccess = android.os.Environment.isExternalStorageManager()
    }

    var isFetchingRepos by remember { mutableStateOf(false) }

    val activeUsername by settingsManager.username
    val activeToken by settingsManager.token
    
    val localPath by settingsManager.lastGitPath
    
    // State hoisted to ViewModel
    var searchQuery by taskViewModel::searchQuery
    var localSearchQuery by taskViewModel::localSearchQuery
    var searchModeAll by taskViewModel::searchModeAll
    var searchCategory by taskViewModel::searchCategory
    var repos by taskViewModel::searchRepos
    var userRepos by taskViewModel::userRepos
    var users by taskViewModel::searchUsers
    var currentSearchPage by taskViewModel::currentSearchPage
    var canLoadMore by taskViewModel::canLoadMoreSearch
    
    // Bookmark Sync Watcher
    LaunchedEffect(Unit) {
        while(true) {
            settingsManager.syncBookmarksWithFilesystem { repoName ->
                taskViewModel.notify(context.getString(R.string.repo_deleted_from_storage, repoName), true)
            }
            delay(3000)
        }
    }

    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var incomingCommits by remember { mutableStateOf<List<GitManager.CommitInfo>>(emptyList()) }
    
    var filterExpanded by remember { mutableStateOf(false) }
    
    var showAddLocalRepoDialog by remember { mutableStateOf<File?>(null) }
    var showCreateRemoteRepoDialog by remember { mutableStateOf<Pair<File, String>?>(null) }

    val localRepoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            if (path != null) {
                val folder = File(path)
                if (File(folder, ".git").exists()) {
                    showAddLocalRepoDialog = folder
                } else {
                    taskViewModel.notify(context.getString(R.string.not_a_git_repo), true)
                }
            }
        }
    }

    // Reset search when category or query changes
    LaunchedEffect(searchQuery, searchCategory, searchModeAll) {
        if (searchQuery.isBlank() || searchModeAll) {
            repos = emptyList()
            users = emptyList()
            currentSearchPage = 1
            canLoadMore = true
            if (searchQuery.isBlank()) isFetchingRepos = false
        }
    }

    // Real-time Search Logic for Global Mode
    LaunchedEffect(searchQuery, searchCategory, searchModeAll, currentSearchPage) {
        if (searchModeAll && searchQuery.isNotBlank()) {
            if (currentSearchPage == 1) delay(300) 
            
            try {
                isFetchingRepos = true
                if (searchCategory == "Users") {
                    val newUsers = githubService.searchUsers(searchQuery, activeToken.ifBlank { null }, page = currentSearchPage)
                    if (newUsers.isEmpty()) canLoadMore = false
                    users = if (currentSearchPage == 1) newUsers else (users + newUsers).distinctBy { it.login }
                } else {
                    val newRepos = githubService.searchRepositories(searchQuery, activeToken.ifBlank { null }, page = currentSearchPage)
                    if (newRepos.isEmpty()) canLoadMore = false
                    repos = if (currentSearchPage == 1) newRepos else (repos + newRepos).distinctBy { it.fullName }
                }
            } finally {
                isFetchingRepos = false
            }
        }
    }

    // Fetch personal repositories separately
    LaunchedEffect(activeUsername, activeToken, taskViewModel.isTokenValid) {
        if (activeUsername.isNotBlank() && taskViewModel.isTokenValid == true) {
            try {
                isFetchingRepos = true
                userRepos = githubService.getUserRepos(activeUsername, activeToken.ifBlank { null }, isOwnProfile = true)
            } finally {
                isFetchingRepos = false
            }
        } else {
            userRepos = emptyList()
        }
    }

    LaunchedEffect(localPath) {
        if (localPath.isNotBlank() && File(localPath).exists()) {
            branches = gitManager.getBranches(File(localPath))
        } else {
            branches = emptyList(); incomingCommits = emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Git Manager",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!hasFullStorageAccess && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.limited_file_access), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.android_11_permission_msg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }, modifier = Modifier.padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.grant_access)) }
                    }
                }
            }
        }
        item {
            val isLoggedIn = taskViewModel.isTokenValid == true
            val hasToken = activeToken.isNotBlank()
            val hasInternet = taskViewModel.internetConnected
            val isDark by settingsManager.isDarkTheme
            
            // Re-verify if needed on tab switch
            LaunchedEffect(activeToken, hasInternet) {
                if (hasToken && taskViewModel.isTokenValid == null && hasInternet) {
                    taskViewModel.checkGitHubConnection(settingsManager)
                }
            }

            val cardBg = when {
                !hasInternet -> Color(0xFFFBC02D).copy(alpha = 0.8f) // Darker yellow for better contrast
                isLoggedIn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                hasToken && taskViewModel.isTokenValid == false -> Color(0xFFFBC02D).copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            }
            
            val cardBorder = when {
                !hasInternet -> Color(0xFFFBC02D)
                isLoggedIn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                hasToken && taskViewModel.isTokenValid == false -> Color(0xFFFBC02D)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }

            val textColor = if (isDark) Color.White else Color.Gray

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, cardBorder),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) { 
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
                    if (!hasInternet) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = textColor, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isLoggedIn) Icons.Default.AccountCircle else Icons.Default.NoAccounts, 
                            contentDescription = null, 
                            tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else if (hasToken) Color(0xFFF9A825) else Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) { 
                        Text(stringResource(R.string.github_account), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                        Text(
                            text = when {
                                !hasInternet -> stringResource(R.string.waiting_for_connection)
                                isLoggedIn -> stringResource(R.string.logged_in_as, activeUsername)
                                hasToken && taskViewModel.isTokenValid == false -> "You\u0027re not logged in."
                                else -> stringResource(R.string.login_with_github)
                            }, 
                            style = MaterialTheme.typography.bodySmall, 
                            color = textColor.copy(alpha = 0.8f)
                        ) 
                    } 
                } 
            }
        }

        // Offline Displayer
        if (!taskViewModel.internetConnected) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4).copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFE6A700), modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.connect_to_internet), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE6A700))
                        }
                        Text(stringResource(R.string.offline_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (taskViewModel.internetConnected && taskViewModel.isTokenValid == true) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Global Search Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "GitHub Explorer", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = searchQuery, 
                                onValueChange = { searchQuery = it; searchModeAll = true }, 
                                modifier = Modifier.fillMaxWidth(), 
                                placeholder = { Text("Search on github.com...") }, 
                                leadingIcon = { Icon(painterResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(20.dp)) }, 
                                trailingIcon = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (searchQuery.isNotEmpty()) { 
                                            IconButton(onClick = { searchQuery = ""; if (searchModeAll) searchModeAll = false }) { 
                                                Icon(Icons.Default.Close, contentDescription = null) 
                                            } 
                                        }
                                        IconButton(onClick = { filterExpanded = true }) { 
                                            Icon(getFilterIcon(searchCategory), contentDescription = "Filter") 
                                        }
                                    }
                                }, 
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) { 
                                listOf("Repositories", "Users").forEach { f -> 
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(getFilterIcon(f), contentDescription = null, modifier = Modifier.size(18.dp)) }, 
                                        text = { Text(f) }, 
                                        onClick = { searchCategory = f; filterExpanded = false }
                                    ) 
                                } 
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Personal Search Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Your Library", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = localSearchQuery, 
                                onValueChange = { 
                                    localSearchQuery = it
                                    searchModeAll = false
                                }, 
                                modifier = Modifier.fillMaxWidth(), 
                                placeholder = { Text("Search in your repositories...") }, 
                                leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) }, 
                                trailingIcon = { 
                                    Row {
                                        if (localSearchQuery.isNotEmpty()) { 
                                            IconButton(onClick = { localSearchQuery = "" }) { 
                                                Icon(Icons.Default.Close, contentDescription = null) 
                                            } 
                                        }
                                        IconButton(onClick = { localRepoPicker.launch(null) }) { 
                                            Icon(
                                                Icons.Default.AddCircleOutline, 
                                                contentDescription = "Add Local", 
                                                tint = MaterialTheme.colorScheme.primary
                                            ) 
                                        }
                                    }
                                }, 
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // --- SYNC & STATUS (Moved Up) ---
            if (taskViewModel.isTokenValid == true) {
                item { 
                    Text(stringResource(R.string.pull_fetch_status), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) 
                }
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (localPath.isBlank()) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    Spacer(Modifier.height(16.dp))
                                    Text(stringResource(R.string.select_repo_from_git), style = MaterialTheme.typography.bodyLarge, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            } else if (!File(localPath).exists()) {
                                Text("Selected path no longer exists.", color = MaterialTheme.colorScheme.error)
                            } else {
                                val bookmarks by settingsManager.bookmarks
                                if (bookmarks.isNotEmpty()) { 
                                    var expanded by remember { mutableStateOf(false) }
                                    Box(Modifier.fillMaxWidth()) { 
                                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { 
                                            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(File(localPath).name) 
                                        }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { 
                                            bookmarks.forEach { path -> 
                                                DropdownMenuItem(
                                                    text = { 
                                                        Column(Modifier.fillMaxWidth()) { 
                                                            Text(File(path).name, fontWeight = FontWeight.Bold)
                                                            Text(path, style = MaterialTheme.typography.labelSmall) 
                                                        }
                                                    }, 
                                                    onClick = { 
                                                        settingsManager.setLastGitPath(path)
                                                        expanded = false 
                                                    }
                                                ) 
                                            } 
                                        } 
                                    } 
                                }
                                
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = onViewChanges,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Details")
                                        }
                                        Button(
                                            onClick = { onDelete(localPath) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFD32F2F)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Delete", color = Color.White)
                                        }
                                    }
                                    
                                    if (branches.isNotEmpty()) { 
                                        var expanded by remember { mutableStateOf(false) }
                                        Box(Modifier.fillMaxWidth()) { 
                                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { 
                                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Branch: ${branches.firstOrNull() ?: "main"}") 
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { 
                                                branches.forEach { branch -> 
                                                    DropdownMenuItem(text = { Text(branch) }, onClick = { expanded = false; scope.launch { gitManager.checkoutBranch(File(localPath), branch) } }) 
                                                } 
                                            } 
                                        } 
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isProcessing = taskViewModel.isPathActive(localPath)
                                    
                                    Button(
                                        onClick = { taskViewModel.runTask(context.getString(R.string.fetching_updates), localPath, isNewClone = false, type = TaskType.GIT) { monitor -> val res = gitManager.fetch(File(localPath), activeUsername, activeToken, null, monitor); if (res is GitManager.Result.Success) incomingCommits = gitManager.getIncomingCommits(File(localPath)); taskViewModel.notify(toLogString(res, context), res is GitManager.Result.Error) } }, 
                                        modifier = Modifier.weight(1f),
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(12.dp)
                                    ) { 
                                        if (isProcessing) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.fetch)) 
                                    }
                                    Button(
                                        enabled = incomingCommits.isNotEmpty() && !isProcessing, 
                                        onClick = { 
                                            taskViewModel.runTask(context.getString(R.string.pulling_updates), localPath, isNewClone = false, type = TaskType.GIT) { monitor -> 
                                                val res = gitManager.pull(File(localPath), activeUsername, activeToken, null, monitor)
                                                if (res is GitManager.Result.Success) {
                                                    incomingCommits = emptyList()
                                                }
                                                taskViewModel.notify(toLogString(res, context), res is GitManager.Result.Error) 
                                            } 
                                        }, 
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { 
                                        if (isProcessing) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.pull)) 
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- RESULTS SECTION (Merged for tighter spacing) ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. Global Results
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Global Results", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (isFetchingRepos && currentSearchPage > 1) {
                                Spacer(Modifier.width(12.dp))
                                Text("Loading...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            }
                            
                            val itemsPerPage = 3
                            val totalItems = if (searchCategory == "Users") users.size else repos.size
                            if (totalItems > itemsPerPage) {
                                Spacer(Modifier.weight(1f))
                                Text("Swipe for more →", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }

                        if (searchQuery.isBlank()) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("Type something in the search bar to discover.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        } else if (isFetchingRepos && currentSearchPage == 1) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        } else {
                            val itemsPerPage = 3
                            val totalItems = if (searchCategory == "Users") users.size else repos.size
                            
                            if (totalItems == 0) {
                                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            } else {
                                val availablePages = (totalItems + itemsPerPage - 1) / itemsPerPage
                                val pagerState = rememberPagerState(pageCount = { if (canLoadMore) availablePages + 1 else availablePages })
                                
                                LaunchedEffect(pagerState.currentPage) {
                                    if (canLoadMore && pagerState.currentPage >= availablePages - 1 && !isFetchingRepos) {
                                        currentSearchPage++
                                    }
                                }

                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth().height(280.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    pageSpacing = 16.dp,
                                    verticalAlignment = Alignment.Top
                                ) { pageIndex ->
                                    if (pageIndex >= availablePages) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    } else {
                                        val startIndex = pageIndex * itemsPerPage
                                        val currentItems = if (searchCategory == "Users") users.drop(startIndex).take(itemsPerPage) else repos.drop(startIndex).take(itemsPerPage)

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            currentItems.forEach { item ->
                                                if (item is GitHubService.UserProfile) {
                                                    UserListItem(item) { onUserClick(item) }
                                                } else if (item is GitHubService.RepoInfo) {
                                                    RepoListItem(
                                                        repo = item, 
                                                        onDetails = { onRepoClick(item) }, 
                                                        onClone = { onClone(item) }, 
                                                        hasToken = true,
                                                        isQueueing = taskViewModel.isPathActive("${settingsManager.rootCloneFolder.value}/${item.name}"),
                                                        currentUser = activeUsername,
                                                        taskViewModel = taskViewModel,
                                                        githubService = githubService,
                                                        settingsManager = settingsManager
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 2. Personal Repositories
                    val filteredRepos = if (localSearchQuery.isNotEmpty()) {
                        userRepos.filter { it.name.contains(localSearchQuery, ignoreCase = true) }
                    } else {
                        userRepos 
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your Library", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (filteredRepos.size > 3) {
                                Spacer(Modifier.weight(1f))
                                Text("Swipe for more →", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                        
                        if (filteredRepos.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { 
                                Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                            }
                        } else {
                            val itemsPerPage = 3
                            val pagerState = rememberPagerState(pageCount = { (filteredRepos.size + itemsPerPage - 1) / itemsPerPage })
                            
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().height(280.dp),
                                contentPadding = PaddingValues(0.dp),
                                pageSpacing = 16.dp,
                                verticalAlignment = Alignment.Top
                            ) { pageIndex ->
                                val startIndex = pageIndex * itemsPerPage
                                val currentItems = filteredRepos.drop(startIndex).take(itemsPerPage)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    currentItems.forEach { repo ->
                                        RepoListItem(
                                            repo = repo, 
                                            onDetails = { onRepoClick(repo) }, 
                                            onClone = { onClone(repo) }, 
                                            hasToken = true,
                                            isQueueing = taskViewModel.isPathActive("${settingsManager.rootCloneFolder.value}/${repo.name}"),
                                            currentUser = activeUsername,
                                            taskViewModel = taskViewModel,
                                            githubService = githubService,
                                            settingsManager = settingsManager
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (incomingCommits.isNotEmpty()) {
                item { Text(stringResource(R.string.incoming_commits), style = MaterialTheme.typography.titleSmall, color = Color(0xFFE6A700)) }
                items(incomingCommits) { commit -> Card(modifier = Modifier.fillMaxWidth().clickable { /* showIncomingFilesForSha = commit.fullHash */ }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column { Text("#${commit.hash}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE6A700), fontWeight = FontWeight.Bold); Text(commit.message, style = MaterialTheme.typography.bodyMedium, color = Color.Black); Text(stringResource(R.string.by_author, commit.author), style = MaterialTheme.typography.labelSmall, color = Color.Gray) } } } }
            }
        }
    }

    // Add Local Repo Dialogs
    showAddLocalRepoDialog?.let { folder ->
        AlertDialog(
            onDismissRequest = { showAddLocalRepoDialog = null },
            title = { Text("Add Local Repository") },
            text = { Text("Repository identified at: ${folder.absolutePath}\n\nRiiSync will now check if this repository exists on your GitHub profile.") },
            confirmButton = {
                Button(onClick = {
                    showAddLocalRepoDialog = null
                    scope.launch {
                        isFetchingRepos = true
                        val remoteUrl = gitManager.getRemoteUrl(folder)
                        if (remoteUrl != null) {
                            val fullName = remoteUrl.substringAfter("github.com/").substringBefore(".git").removeSuffix("/")
                            val repoInfo = githubService.getRepository(fullName, activeToken.ifBlank { null })
                            if (repoInfo != null) {
                                settingsManager.setLastGitPath(folder.absolutePath)
                                taskViewModel.notify(context.getString(R.string.repo_matched_github, repoInfo.fullName), false)
                            } else {
                                showCreateRemoteRepoDialog = folder to fullName.substringAfter("/")
                            }
                        } else {
                            showCreateRemoteRepoDialog = folder to folder.name
                        }
                        isFetchingRepos = false
                    }
                }) { Text("Identify") }
            },
            dismissButton = { TextButton(onClick = { showAddLocalRepoDialog = null }) { Text("Cancel") } }
        )
    }

    showCreateRemoteRepoDialog?.let { (folder, repoName) ->
        var newName by remember { mutableStateOf(repoName) }
        var isPrivate by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreateRemoteRepoDialog = null },
            title = { Text("Publish to GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This repository was not found on GitHub. Would you like to create it now?")
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Repository Name") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                        Text("Private Repository", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(enabled = activeToken.isNotBlank(), onClick = {
                    showCreateRemoteRepoDialog = null
                    taskViewModel.runSimpleTask(context.getString(R.string.creating_repo), folder.absolutePath, type = TaskType.GIT) { _ ->
                        val newRepo = githubService.createRepository(newName, "Created via RiiSync", isPrivate, activeToken)
                        if (newRepo != null) {
                            gitManager.addRemote(folder, newRepo.cloneUrl)
                            settingsManager.setLastGitPath(folder.absolutePath)
                            taskViewModel.notify(context.getString(R.string.repo_created_linked, newRepo.fullName), false)
                        } else {
                            taskViewModel.notify(context.getString(R.string.repo_create_failed), true)
                        }
                    }
                }) { Text("Create \u0026 Publish") }
            },
            dismissButton = { TextButton(onClick = { showCreateRemoteRepoDialog = null }) { Text("Cancel") } }
        )
    }
}

/**
 * Visual list item representing a repository.
 */
@Composable
fun RepoListItem(
    repo: GitHubService.RepoInfo, 
    onDetails: () -> Unit, 
    onClone: () -> Unit, 
    hasToken: Boolean, 
    isQueueing: Boolean = false, 
    currentUser: String = "", 
    taskViewModel: GlobalTaskViewModel? = null, 
    githubService: GitHubService? = null,
    settingsManager: SettingsManager
) {
    // We only care about collaborator status if I'm not the owner and have a token
    val isNotOwner = currentUser.isNotBlank() && !repo.ownerLogin.equals(currentUser, ignoreCase = true)
    
    // Read from cache if available
    var isCollaborator by remember(repo.fullName) { 
        mutableStateOf(taskViewModel?.collaboratorCache?.get(repo.fullName) ?: false) 
    }

    // Explicit check via collaborators list if not in cache
    if (isNotOwner && !isCollaborator && hasToken && githubService != null && taskViewModel != null && !taskViewModel.collaboratorCache.containsKey(repo.fullName)) {
        LaunchedEffect(repo.fullName) {
            val token = settingsManager.token.value.ifBlank { null }
            val collaborators = githubService.getCollaborators(repo.fullName, token)
            val matched = collaborators.any { it.login.equals(currentUser, ignoreCase = true) }
            taskViewModel.collaboratorCache[repo.fullName] = matched
            isCollaborator = matched
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onDetails() }, 
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { 
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(repo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    
                    if (repo.isPrivate) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                stringResource(R.string.private_repo), 
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isCollaborator) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                              ) {
                                Icon(
                                    Icons.Default.Group, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    stringResource(R.string.you_are_collaborator), 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }
                Text(repo.description ?: stringResource(R.string.no_description), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) 
            }
            if (!hasToken) {
                Text(
                    "You have to log-in first.", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            IconButton(onClick = onClone, enabled = hasToken && !isQueueing) { 
                if (isQueueing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(
                        if (hasToken) Icons.Default.Build else Icons.Default.VpnKey, 
                        contentDescription = "Clone", 
                        tint = if (hasToken) MaterialTheme.colorScheme.primary else Color(0xFFF9A825)
                    ) 
                }
            }
        }
    }
}

/**
 * Visual list item representing a GitHub user.
 */
@Composable
fun UserListItem(user: GitHubService.UserProfile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp), 
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { 
            AsyncImage(model = user.avatarUrl, contentDescription = user.login, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Text(user.login, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View Profile", tint = MaterialTheme.colorScheme.primary) 
        }
    }
}

/**
 * Detailed view for a specific remote repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailsView(
    repo: GitHubService.RepoInfo, 
    token: String, 
    githubService: GitHubService, 
    gitManager: GitManager, 
    settingsManager: SettingsManager,
    taskViewModel: GlobalTaskViewModel,
    onBack: () -> Unit,
    onUserClick: (GitHubService.UserProfile) -> Unit
) {
    var selectedTab by taskViewModel::activeRepoDetailsTab
    val tabs = listOf("Main", "Commits", "Files", "Collaborators")
    
    var collaborators by remember { mutableStateOf<List<GitHubService.UserProfile>>(emptyList()) }
    LaunchedEffect(repo.fullName) {
        collaborators = githubService.getCollaborators(repo.fullName, token.ifBlank { null })
    }

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to List") }
                    Column {
                        Text(repo.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(repo.fullName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }

                if (collaborators.isNotEmpty()) {
                    Row(
                        Modifier.padding(start = 48.dp, top = 4.dp), 
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Collaborators:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            collaborators.take(10).forEach { user ->
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = user.login,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (collaborators.size > 10) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+${collaborators.size - 10}", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        Box(Modifier.weight(1f).padding(16.dp)) {
            val isDark by settingsManager.isDarkTheme
            when (selectedTab) {
                0 -> ReadmeTab(repo, token, githubService, isDark)
                1 -> CommitsTab(repo, token, githubService)
                2 -> FilesTab(repo, token, githubService)
                3 -> CollaboratorsTab(settingsManager, collaborators, onUserClick)
            }
        }
    }
}

@Composable
fun ReadmeTab(repo: GitHubService.RepoInfo, token: String, githubService: GitHubService, isDark: Boolean) {
    var readmeHtml by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(repo) {
        readmeHtml = githubService.getReadmeHtml(repo.fullName, token.ifBlank { null })
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            if (readmeHtml != null) {
                HtmlReadmeViewer(html = readmeHtml!!, isDark = isDark, repo = repo)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No README found for this repository.", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun HtmlReadmeViewer(html: String, isDark: Boolean, repo: GitHubService.RepoInfo) {
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()

    // Inject CSS to match the app theme and support full GitHub syntax
    val styledHtml = remember(html, isDark) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                :root {
                    --bg-color: ${if (isDark) "#121212" else "#ffffff"};
                    --text-color: ${if (isDark) "#e0e0e0" else "#24292f"};
                    --border-color: ${if (isDark) "#30363d" else "#d0d7de"};
                    --code-bg: ${if (isDark) "#161b22" else "#f6f8fa"};
                    --accent-color: ${String.format("#%06X", (0xFFFFFF and accentColor))};
                }
                body {
                    font-family: -apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans",Helvetica,Arial,sans-serif,"Apple Color Emoji","Segoe UI Emoji";
                    font-size: 14px;
                    line-height: 1.5;
                    color: var(--text-color);
                    background-color: transparent;
                    margin: 16px;
                    word-wrap: break-word;
                }
                h1, h2, h3, h4, h5, h6 {
                    margin-top: 24px;
                    margin-bottom: 16px;
                    font-weight: 600;
                    line-height: 1.25;
                }
                h1 { font-size: 2em; padding-bottom: 0.3em; border-bottom: 1px solid var(--border-color); }
                h2 { font-size: 1.5em; padding-bottom: 0.3em; border-bottom: 1px solid var(--border-color); }
                h3 { font-size: 1.25em; }
                h4 { font-size: 1em; }
                h5 { font-size: 0.875em; }
                h6 { font-size: 0.85em; color: #6e7781; }
                a { color: var(--accent-color); text-decoration: none; }
                a:hover { text-decoration: underline; }
                table {
                    display: block;
                    width: 100%;
                    width: max-content;
                    max-width: 100%;
                    overflow: auto;
                    border-spacing: 0;
                    border-collapse: collapse;
                    margin-top: 0;
                    margin-bottom: 16px;
                }
                table th, table td {
                    padding: 6px 13px;
                    border: 1px solid var(--border-color);
                }
                table tr { background-color: transparent; border-top: 1px solid var(--border-color); }
                table tr:nth-child(2n) { background-color: var(--code-bg); }
                blockquote {
                    padding: 0 1em;
                    color: #6e7781;
                    border-left: 0.25em solid var(--border-color);
                    margin: 0 0 16px 0;
                }
                code {
                    padding: 0.2em 0.4em;
                    margin: 0;
                    font-size: 85%;
                    background-color: var(--code-bg);
                    border-radius: 6px;
                    font-family: ui-monospace,SFMono-Regular,SF Mono,Menlo,Consolas,Liberation Mono,monospace;
                }
                pre {
                    padding: 16px;
                    overflow: auto;
                    font-size: 85%;
                    line-height: 1.45;
                    background-color: var(--code-bg);
                    border-radius: 6px;
                    margin-bottom: 16px;
                }
                pre code { background-color: transparent; padding: 0; margin: 0; font-size: 100%; }
                img { max-width: 100%; box-sizing: border-box; }
                hr { height: 0.25em; padding: 0; margin: 24px 0; background-color: var(--border-color); border: 0; }
                ul, ol { padding-left: 2em; margin-top: 0; margin-bottom: 16px; }
                li + li { margin-top: 0.25em; }
                kbd {
                    display: inline-block;
                    padding: 3px 5px;
                    font: 11px ui-monospace,SFMono-Regular,SF Mono,Menlo,Consolas,Liberation Mono,monospace;
                    line-height: 10px;
                    color: var(--text-color);
                    vertical-align: middle;
                    background-color: var(--code-bg);
                    border: solid 1px var(--border-color);
                    border-bottom-color: var(--border-color);
                    border-radius: 6px;
                    box-shadow: inset 0 -1px 0 var(--border-color);
                }
                .markdown-alert {
                    padding: 8px 16px;
                    margin-bottom: 16px;
                    border-left: 0.25em solid;
                    border-radius: 0 6px 6px 0;
                }
                .markdown-alert-note { border-left-color: #0969da; background-color: ${if (isDark) "#121d2f" else "#f0f7ff"}; }
                .markdown-alert-tip { border-left-color: #1a7f37; background-color: ${if (isDark) "#12211a" else "#f0fff4"}; }
                .markdown-alert-important { border-left-color: #8250df; background-color: ${if (isDark) "#1e1634" else "#fbefff"}; }
                .markdown-alert-warning { border-left-color: #9a6700; background-color: ${if (isDark) "#221d11" else "#fff8c5"}; }
                .markdown-alert-caution { border-left-color: #cf222e; background-color: ${if (isDark) "#25171c" else "#fff1f0"}; }
                
                details { display: block; }
                summary { display: list-item; cursor: pointer; font-weight: 600; padding: 4px; }
                details[open] summary { margin-bottom: 16px; }

                /* Hide GitHub-specific UI elements that don't belong in mobile README view */
                .anchor, .octicon, .clipboard-copy, .heading-link, .Link--external, .octicon-link {
                    display: none !important;
                }
                button, .BtnGroup, .Header, .AppHeader {
                    display: none !important;
                }
                .sr-only, .screen-reader-main-content {
                    display: none !important;
                }
                /* Ensure images don't explode and handle SVGs */
                img { 
                    max-width: 100%; 
                    height: auto;
                    box-sizing: border-box; 
                }
                svg { 
                    max-width: 100%; 
                    height: auto;
                }
                /* The "eye" is often a GitHub UI element for "Hidden" or "View" icons that are unscaled */
                .hidden-text-expander, .Details-content--hidden {
                    display: none !important;
                }
            </style>
        </head>
        <body>
            $html
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0) // Transparent
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Open external links in browser
                        val url = request?.url.toString()
                        if (url.startsWith("http")) {
                            val intent = Intent(Intent.ACTION_VIEW, request?.url)
                            context.startActivity(intent)
                            return true
                        }
                        return false
                    }
                }
                settings.apply {
                    javaScriptEnabled = false // Disabled for security and to prevent UI scripts from running
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    blockNetworkImage = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }
        },
        update = { webView ->
            // Use the specific repository as the base URL to resolve relative images correctly
            val baseUrl = "https://github.com/${repo.fullName}/raw/${repo.defaultBranch}/"
            webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun CommitsTab(repo: GitHubService.RepoInfo, token: String, githubService: GitHubService) {
    var commits by remember { mutableStateOf<List<GitManager.CommitInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(1) }
    var canLoadMore by remember { mutableStateOf(true) }

    LaunchedEffect(page) {
        val newCommits = githubService.getRepoCommits(repo.fullName, token.ifBlank { null }, page)
        if (newCommits.isEmpty()) canLoadMore = false else commits = commits + newCommits
        isLoading = false
    }

    val itemsPerPage = 5
    val availablePages = (commits.size + itemsPerPage - 1) / itemsPerPage
    val pagerState = rememberPagerState(pageCount = { if (canLoadMore) availablePages + 1 else availablePages })

    LaunchedEffect(pagerState.currentPage) {
        if (canLoadMore && pagerState.currentPage >= availablePages - 1 && !isLoading) {
            isLoading = true
            page++
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Commit History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (isLoading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            if (pageIndex >= availablePages) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val startIndex = pageIndex * itemsPerPage
                val currentItems = commits.drop(startIndex).take(itemsPerPage)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(currentItems) { commit ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("#${commit.hash}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(commit.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text("by ${commit.author} on ${commit.date}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                    if (canLoadMore) {
                        item {
                            Text("Swipe for more history →", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(repo: GitHubService.RepoInfo, token: String, githubService: GitHubService) {
    var files by remember { mutableStateOf<List<GitHubService.GitHubFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(repo) {
        files = githubService.getRepositoryTree(repo.fullName, branch = repo.defaultBranch, token = token.ifBlank { null })
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        // Group files by directory to build a flat list representing the hierarchy
        val displayItems = remember(files, expandedFolders) {
            val items = mutableListOf<FileExplorerItem>()
            
            fun addEntries(pathPrefix: String, level: Int) {
                val entries = files.filter { 
                    val parentPath = it.path.substringBeforeLast("/", "")
                    val isRoot = !it.path.contains("/") && pathPrefix == ""
                    val isDirectChild = parentPath == pathPrefix && it.path != pathPrefix
                    isRoot || isDirectChild
                }.sortedWith(compareBy<GitHubService.GitHubFile> { it.type != "tree" }.thenBy { it.path })
                
                entries.forEach { file ->
                    val isFolder = file.type == "tree"
                    items.add(FileExplorerItem(file, level, isFolder, expandedFolders.contains(file.path)))
                    if (isFolder && expandedFolders.contains(file.path)) {
                        addEntries(file.path, level + 1)
                    }
                }
            }
            
            addEntries("", 0)
            items.distinctBy { it.file.path }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(displayItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (item.level * 16).dp)
                        .clickable(enabled = item.isFolder) {
                            expandedFolders = if (expandedFolders.contains(item.file.path)) {
                                expandedFolders - item.file.path
                            } else {
                                expandedFolders + item.file.path
                            }
                        },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isFolder) MaterialTheme.colorScheme.primary.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.isFolder) {
                            Icon(
                                if (item.isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Spacer(Modifier.width(20.dp))
                        }
                        
                        Icon(
                            if (item.isFolder) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (item.isFolder) Color(0xFFFFA000) else Color.Gray
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.file.path.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (item.isFolder) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

data class FileExplorerItem(
    val file: GitHubService.GitHubFile,
    val level: Int,
    val isFolder: Boolean,
    val isExpanded: Boolean
)

@Composable
fun CollaboratorsTab(settingsManager: SettingsManager, collaborators: List<GitHubService.UserProfile>, onUserClick: (GitHubService.UserProfile) -> Unit) {
    val currentUser = settingsManager.username.value

    if (collaborators.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No public collaborators found.", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(collaborators) { user ->
                val isMe = user.login.equals(currentUser, ignoreCase = true)
                Card(
                    modifier = Modifier.fillMaxWidth().then(if (isMe) Modifier else Modifier.clickable { onUserClick(user) }), 
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { 
                        AsyncImage(model = user.avatarUrl, contentDescription = user.login, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.login, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            if (isMe) {
                                Text("You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!isMe) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View Profile", tint = MaterialTheme.colorScheme.primary) 
                        }
                    }
                }
            }
        }
    }
}

/**
 * Profile view for a GitHub user.
 */
@Composable
fun UserProfileView(
    user: GitHubService.UserProfile, 
    token: String, 
    githubService: GitHubService, 
    taskViewModel: GlobalTaskViewModel,
    settingsManager: SettingsManager,
    onBack: () -> Unit, 
    onRepoDetails: (GitHubService.RepoInfo) -> Unit, 
    onClone: (GitHubService.RepoInfo) -> Unit
) {
    var repos by remember { mutableStateOf<List<GitHubService.RepoInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUser = settingsManager.username.value
    
    LaunchedEffect(user) { repos = githubService.getUserRepos(user.login, token.ifBlank { null }); isLoading = false }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }; Text(stringResource(R.string.user_profile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = user.avatarUrl, contentDescription = user.login, modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop); Spacer(Modifier.width(16.dp)); Column { Text(user.name ?: user.login, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("@${user.login}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray); user.bio?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { StatItem(stringResource(R.string.followers_count, user.followers), "Followers"); StatItem(stringResource(R.string.following_count, user.following), "Following"); StatItem(stringResource(R.string.public_repos_count, user.publicRepos), "Repos") }
            HorizontalDivider(); Text("Repositories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)) else { repos.forEach { repo -> RepoListItem(repo = repo, onDetails = { onRepoDetails(repo) }, onClone = { onClone(repo) }, hasToken = taskViewModel.isTokenValid == true, currentUser = currentUser, taskViewModel = taskViewModel, githubService = githubService, settingsManager = settingsManager) } }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * Detailed view for managing local changes within the [GitScreen].
 */
@Composable
fun IntegratedChangesView(
    localPath: String, 
    taskViewModel: GlobalTaskViewModel, 
    gitManager: GitManager, 
    username: String, 
    token: String, 
    onBack: () -> Unit
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var changes by taskViewModel::localChanges; var isLoading by remember { mutableStateOf(false) }; var selectedPaths by taskViewModel::selectedPaths; var commitTitle by taskViewModel::commitTitle; var commitDescription by taskViewModel::commitDescription; var selectedDiffFile by remember { mutableStateOf<String?>(null) }; var diffLines by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val githubService = remember { GitHubService() }
    var collaborators by taskViewModel::activeCollaborators
    var repoStats by taskViewModel::activeRepoStats

    val refreshChanges = { 
        if (localPath.isNotBlank() && File(localPath).exists()) { 
            scope.launch { 
                isLoading = true
                changes = gitManager.getLocalChanges(File(localPath))
                repoStats = gitManager.getRepositoryStats(File(localPath))
                
                // Fetch collaborators
                val remoteUrl = gitManager.getRemoteUrl(File(localPath))
                if (remoteUrl != null && remoteUrl.contains("github.com")) {
                    val fullName = remoteUrl.substringAfter("github.com/").substringBefore(".git").removeSuffix("/")
                    collaborators = githubService.getCollaborators(fullName, token.ifBlank { null })
                }
                
                isLoading = false 
            } 
        } 
    }
    LaunchedEffect(localPath) { refreshChanges() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text(File(localPath).name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) 
                }
                
                if (collaborators.isNotEmpty()) {
                    Row(
                        Modifier.padding(start = 48.dp), 
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Collaborators:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            collaborators.take(10).forEach { user ->
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = user.login,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (collaborators.size > 10) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+${collaborators.size - 10}", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
                
                if (repoStats.isNotEmpty()) {
                    Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Size: ${repoStats["disk_usage"]}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("Last Sync: ${repoStats["last_sync"]}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }

        if (selectedPaths.isNotEmpty()) Text(stringResource(R.string.items_selected, selectedPaths.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedPaths.isEmpty()) { 
                Button(onClick = { refreshChanges() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.refresh)) }
                Button(enabled = changes.isNotEmpty() && !taskViewModel.isOperating, onClick = { taskViewModel.runSimpleTask(context.getString(R.string.stashing_changes)) { val res = gitManager.stashCreate(File(localPath)); taskViewModel.notify(toLogString(res, context), res is GitManager.Result.Error); refreshChanges() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.stash)) }
                OutlinedButton(enabled = changes.isNotEmpty() && !taskViewModel.isOperating, onClick = { taskViewModel.runSimpleTask(context.getString(R.string.discarding_all_changes)) { val res = gitManager.discardChanges(File(localPath), null); taskViewModel.notify(context.getString(R.string.all_changes_discarded), res is GitManager.Result.Error); refreshChanges() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.discard_all)) }
            } else { 
                Button(onClick = { taskViewModel.runSimpleTask(context.getString(R.string.discarding_selected)) { selectedPaths.forEach { gitManager.discardChanges(File(localPath), it) }; taskViewModel.notify(context.getString(R.string.selected_changes_discarded), false); selectedPaths = emptySet(); refreshChanges() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.discard_selected)) }
                Button(onClick = { selectedPaths = emptySet() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.cancel)) } 
            }
        }
        
        if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)) 
        else if (changes.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_changes_detected), color = Color.Gray) } 
        else { 
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { 
                items(changes) { change -> 
                    val isSelected = selectedPaths.contains(change.path)
                    Card(
                        modifier = Modifier.fillMaxWidth().pointerInput(change.path) { detectTapGestures(onLongPress = { selectedPaths = if (isSelected) selectedPaths - change.path else selectedPaths + change.path }, onTap = { if (selectedPaths.isNotEmpty()) { selectedPaths = if (isSelected) selectedPaths - change.path else selectedPaths + change.path } else { scope.launch { diffLines = gitManager.getFileDiff(File(localPath), change.path); selectedDiffFile = change.path } } }) }, 
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) { 
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { 
                            StatusIcon(change.status); Spacer(Modifier.width(12.dp)); Text(change.path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); if (change.status != "Untracked" && selectedPaths.isEmpty()) { IconButton(onClick = { taskViewModel.runSimpleTask(context.getString(R.string.discarding_file_format, change.path)) { gitManager.discardChanges(File(localPath), change.path); refreshChanges() } }) { Icon(Icons.Default.Refresh, contentDescription = "Discard", modifier = Modifier.size(18.dp), tint = Color(0xFFD32F2F)) } } 
                        } 
                    } 
                } 
            } 
        }
        
        if (changes.isNotEmpty() && selectedPaths.isEmpty()) { 
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            ) { 
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                    Text(stringResource(R.string.new_commit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Textures", "XML Fix", "Version Bump").forEach { template ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    commitTitle = when(template) {
                                        "Textures" -> "feat(textures): Update mod assets"
                                        "XML Fix" -> "fix(xml): Adjust Riivolution config"
                                        "Version Bump" -> "chore(version): Bump mod version"
                                        else -> commitTitle
                                    }
                                },
                                label = { Text(template) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    
                    OutlinedTextField(value = commitTitle, onValueChange = { if (it.length <= 50) commitTitle = it }, label = { Text(stringResource(R.string.subject_max_50)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), supportingText = { Text("${commitTitle.length}/50") }); 
                    OutlinedTextField(value = commitDescription, onValueChange = { commitDescription = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(12.dp)); 
                    Button(enabled = commitTitle.isNotBlank(), onClick = { taskViewModel.runSimpleTask(context.getString(R.string.publishing_changes), localPath, type = TaskType.GIT) { _ -> val fullMsg = if (commitDescription.isBlank()) commitTitle else "$commitTitle\n\n$commitDescription"; val res = gitManager.commitAndPush(File(localPath), fullMsg, authorName = username, authorEmail = "$username@users.noreply.github.com", username = username, token = token); taskViewModel.notify(if (res is GitManager.Result.Success) context.getString(R.string.commit_push_completed) else toLogString(res, context), res is GitManager.Result.Error); if (res is GitManager.Result.Success) { commitTitle = ""; commitDescription = ""; refreshChanges() } } }, modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.commit_push)) } 
                } 
            } 
        }
    }
    selectedDiffFile?.let { DiffDialog(fileName = it, diffLines = diffLines, onDismiss = { selectedDiffFile = null }) }
}

/**
 * Dialog for browsing the remote file tree of a GitHub repository.
 */
@Composable
fun RepositoryTreeDialog(fullName: String, token: String, githubService: GitHubService, onDismiss: () -> Unit) {
    var files by remember { mutableStateOf<List<GitHubService.GitHubFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(fullName) { files = githubService.getRepositoryTree(fullName, token = token.ifBlank { null }); isLoading = false }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repository Explorer") },
        text = {
            Box(Modifier.heightIn(max = 400.dp).fillMaxWidth()) {
                if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else {
                    LazyColumn {
                        items(files) { file ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (file.type == "tree") Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (file.type == "tree") Color(0xFFFFA000) else Color.Gray
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(file.path, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * Dialog for viewing the README content of a repository.
 */
@Composable
fun ReadmeDialog(html: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("README") },
        text = {
            Box(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                // Simple HTML rendering (not perfect but works for basic markdown)
                Text(html.replace(Regex("<[^>]*>"), ""), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * Dialog for viewing files changed in a specific commit.
 */
@Composable
fun CommitFilesDialog(fullName: String, sha: String, token: String, githubService: GitHubService, onDismiss: () -> Unit) {
    var files by remember { mutableStateOf<List<GitHubService.CommitFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(sha) { files = githubService.getCommitFiles(fullName, sha, token.ifBlank { null }); isLoading = false }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.modified_files_format, sha.take(7))) }, text = { Box(Modifier.heightIn(max = 300.dp).fillMaxWidth()) { if (isLoading) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(files) { file -> Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(file.filename, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text(file.status, style = MaterialTheme.typography.labelSmall, color = when(file.status) { "added" -> Color(0xFF2E7D32); "removed" -> Color(0xFFD32F2F); else -> Color(0xFF1976D2) }) } } } } }, confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.ok)) } })
}

/**
 * Dialog for configuring the local destination for a repository clone operation.
 */
@Composable
fun CloneDialog(repo: GitHubService.RepoInfo, settingsManager: SettingsManager, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var path by remember { mutableStateOf("${settingsManager.rootCloneFolder.value}/${repo.name}") }
    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { path = (PathUtils.getAbsolutePath(context, it) ?: "") + "/${repo.name}" } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.clone_title_format, repo.name)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.select_dest_folder)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(path, { path = it }, modifier = Modifier.weight(1f)); IconButton(onClick = { dirLauncher.launch(null) }) { Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.browse)) } } } }, confirmButton = { Button(onClick = { onConfirm(path) }) { Text(stringResource(R.string.clone)) } }, dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel)) } })
}

/**
 * Dialog for viewing incoming files from a remote commit before pulling.
 */
@Composable
fun IncomingFilesDialog(localPath: String, sha: String, gitManager: GitManager, onDismiss: () -> Unit) {
    var files by remember { mutableStateOf<List<GitManager.LocalChange>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(sha) { files = gitManager.getFilesForCommitLocal(File(localPath), sha); isLoading = false }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.incoming_files_format, sha.take(7))) }, text = { Box(Modifier.heightIn(max = 300.dp).fillMaxWidth()) { if (isLoading) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(files) { file -> Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(file.path, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text(file.status, style = MaterialTheme.typography.labelSmall, color = when(file.status) { "Added", "ADD" -> Color(0xFF2E7D32); "Deleted", "DELETE" -> Color(0xFFD32F2F); else -> Color(0xFF1976D2) }) } } } } }, confirmButton = { Button(onClick = { onDismiss() }) { Text(stringResource(R.string.ok)) } })
}

/**
 * Component for displaying a single numerical statistic and its label.
 */
@Composable
fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

/**
 * Converts [GitManager.Result] into a localized log message for [GitScreen].
 */
private fun toLogString(result: GitManager.Result, context: android.content.Context): String = when (result) {
    is GitManager.Result.Success -> context.getString(R.string.ok_format, result.message)
    is GitManager.Result.Error -> context.getString(R.string.error_format, result.message)
}

/**
 * Returns an appropriate [ImageVector] for a given search filter category.
 */
fun getFilterIcon(filter: String): ImageVector = when (filter) {
    "Repositories" -> Icons.AutoMirrored.Filled.List
    "Users" -> Icons.Default.Person
    "Discussions" -> Icons.Default.MailOutline // Placeholder
    else -> Icons.AutoMirrored.Filled.List
}
