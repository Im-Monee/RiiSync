/**
 * Main Activity for RiiSync.
 * This file contains the primary entry point for the application, handling high-level UI structure,
 * navigation, and theme initialization.
 */
package com.riisync.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.riisync.app.shizuku.ShizukuHelper
import com.riisync.app.ui.*
import com.riisync.app.utils.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The main activity that hosts the application's Compose UI.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private val taskViewModel: GlobalTaskViewModel by viewModels()

    /**
     * Called when the activity is starting. Initializes Shizuku, settings, and the root UI.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuHelper.init(this)
        settingsManager = SettingsManager(this)
        
        taskViewModel.startModWatcher(settingsManager)

        setContent {
            val isDark by settingsManager.isDarkTheme
            val isOnboardingComplete by settingsManager.onboardingComplete
            
            // Live permission check for startup gate
            var hasPermission by remember { 
                mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true)
            }
            
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                // 1. Refresh Network State immediately on return
                taskViewModel.refreshNetworkState()
                
                // 2. Refresh Game Titles from GitHub
                taskViewModel.refreshGameTitles()
                
                // 3. Refresh Shizuku status
                ShizukuHelper.checkStatus(this@MainActivity)

                // 4. Check for App Updates (Quietly)
                taskViewModel.checkForAppUpdates(settingsManager, quiet = true)

                // 5. Handle Storage Permission Changes
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val currentPermission = android.os.Environment.isExternalStorageManager()
                    if (hasPermission && !currentPermission) {
                        // Permission was just revoked while app was running
                        taskViewModel.abortAllTasks()
                    }
                    hasPermission = currentPermission
                }
            }

            val msg = taskViewModel.notificationMessage
            val isError = taskViewModel.isErrorNotification

            // Light Blue Theme (Matches Favicon Gradient)
            val lightBlue = Color(0xFF03A9F4)
            
            val colorScheme = if (isDark) {
                darkColorScheme(
                    primary = lightBlue,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFF004D6A),
                    onPrimaryContainer = Color(0xFFC2E8FF),
                    secondary = Color(0xFFB0CCC5),
                    tertiary = Color(0xFFA6CBE2)
                )
            } else {
                lightColorScheme(
                    primary = lightBlue,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE1F5FE),
                    onPrimaryContainer = Color(0xFF001F2A),
                    secondary = Color(0xFF4F635D),
                    tertiary = Color(0xFF426277)
                )
            }

            // Optimized Typography for S25 (compact but legible)
            val typography = MaterialTheme.typography.copy(
                headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                labelMedium = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp)
            )

            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        when {
                            !isOnboardingComplete -> {
                                OnboardingScreen(settingsManager, taskViewModel) {
                                    taskViewModel.clearNotification()
                                }
                            }
                            !hasPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R -> {
                                PermissionRevokedScreen()
                            }
                            else -> {
                                AppRoot(settingsManager, taskViewModel)
                            }
                        }
                        
                        // Modern Floating Toast-style Notifications (Top-Right)
                        if (isOnboardingComplete && !taskViewModel.isOperating) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, end = 12.dp) // Move higher to avoid title overlap
                                    .zIndex(100f),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                NotificationBanner(msg, isError) {
                                    taskViewModel.clearNotification()
                                }
                            }
                        }

                        // Changelog Screen
                        if (taskViewModel.showChangelog) {
                            ChangelogScreen(
                                version = com.riisync.app.BuildConfig.VERSION_NAME,
                                notes = taskViewModel.changelogText,
                                isDark = isDark,
                                onDismiss = { taskViewModel.showChangelog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the activity is being destroyed. Ensures Shizuku is properly unbound.
     */
    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.unbind()
    }
}

/**
 * Screen displayed when the "All Files Access" permission is revoked.
 */
@Composable
fun PermissionRevokedScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.GppBad,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            stringResource(R.string.permissions_revoked_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        
        Text(
            stringResource(R.string.permissions_revoked_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.restore_permissions), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The root composable for the application, managing navigation and global progress indicators.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(settingsManager: SettingsManager, taskViewModel: GlobalTaskViewModel) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    var showQueueDetails by remember { mutableStateOf(false) }

    val navItems = listOf(
        NavItem(stringResource(R.string.tab_git), stringResource(R.string.sub_git), R.drawable.ic_github, 0),
        NavItem(stringResource(R.string.tab_modding), stringResource(R.string.sub_modding), R.drawable.ic_dolphin, 1),
        NavItem(stringResource(R.string.tab_settings), stringResource(R.string.sub_settings), Icons.Default.Settings, 2),
        NavItem(stringResource(R.string.tab_about), stringResource(R.string.sub_about), Icons.Default.Info, 3)
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { navItem ->
                val needsAttention = taskViewModel.tabsRequiringAttention.contains(navItem.index)
                
                item(
                    selected = pagerState.currentPage == navItem.index,
                    onClick = { 
                        taskViewModel.setTabAttention(navItem.index, false)
                        scope.launch { pagerState.animateScrollToPage(navItem.index) }
                    },
                    icon = { 
                        BadgedBox(
                            badge = {
                                if (needsAttention && pagerState.currentPage != navItem.index) {
                                    Badge(
                                        containerColor = Color(0xFFFFD600), // Yellow dot
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        ) {
                            when (val icon = navItem.icon) {
                                is ImageVector -> Icon(icon, contentDescription = navItem.label)
                                is Int -> Icon(painterResource(icon), contentDescription = navItem.label, modifier = Modifier.size(24.dp))
                            }
                        }
                    },
                    label = { Text(navItem.label) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.favicon),
                                    contentDescription = "RiiSync Logo",
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                if (taskViewModel.isOperating) {
                                    Spacer(Modifier.width(12.dp))
                                    if (taskViewModel.waitingTasksCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${taskViewModel.waitingTasksCount + taskViewModel.activeTasks.size}", color = Color.White)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                            Text(navItems[pagerState.currentPage].subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                // Multi-Task Progress Area
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (taskViewModel.queuedTasks.isNotEmpty() || taskViewModel.activeTasks.size > 1) showQueueDetails = true }
                        .animateContentSize()
                ) {
                    taskViewModel.activeTasks.forEach { task ->
                        TaskProgressItem(task, onCancel = { taskViewModel.cancelTask(task.id, settingsManager) })
                    }
                    if (taskViewModel.queuedTasks.isNotEmpty()) {
                        Text(
                            stringResource(R.string.task_next_indicator, taskViewModel.queuedTasks.first().title, taskViewModel.queuedTasks.size - 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    userScrollEnabled = false
                ) { page ->
                    when (page) {
                        0 -> GitScreen(taskViewModel, settingsManager)
                        1 -> ModdingScreen(taskViewModel, settingsManager, onNavigateToSettings = { 
                            scope.launch { pagerState.animateScrollToPage(2) }
                        })
                        2 -> SettingsScreen(settingsManager, taskViewModel)
                        3 -> AboutScreen(taskViewModel, settingsManager)
                    }
                }
            }
        }
    }

    if (showQueueDetails) {
        TaskQueueDialog(taskViewModel) { showQueueDetails = false }
    }
}

@Composable
fun TaskProgressItem(task: TaskInfo, onCancel: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        if (task.progress.value >= 0) {
            LinearProgressIndicator(
                progress = { task.progress.value },
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp).clip(CircleShape)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp).clip(CircleShape)
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when(task.type) {
                    TaskType.GIT -> Icons.Default.CloudSync
                    TaskType.MOD -> Icons.Default.Extension
                    TaskType.SYSTEM -> Icons.Default.Dns
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (task.currentSubTask.value.isNotEmpty()) {
                        task.currentSubTask.value
                    } else if (task.progress.value >= 0) {
                        "${(task.progress.value * 100).toInt()}%"
                    } else {
                        "Processing..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
            if (!(task.type == TaskType.SYSTEM && (task.title.contains("Icon") || task.title.contains("DB")))) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Cancel", 
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun TaskQueueDialog(viewModel: GlobalTaskViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_queue_title)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp)) {
                Text(stringResource(R.string.active_tasks), style = MaterialTheme.typography.titleSmall)
                viewModel.activeTasks.forEach { task ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (task.currentSubTask.value.isNotEmpty()) {
                                    task.currentSubTask.value
                                } else if (task.progress.value >= 0) {
                                    "${(task.progress.value * 100).toInt()}%"
                                } else {
                                    "Processing..."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                if (viewModel.queuedTasks.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.waiting_tasks), style = MaterialTheme.typography.titleSmall)
                    LazyColumn {
                        items(viewModel.queuedTasks.toList()) { task ->
                            Card(
                                onClick = { viewModel.promoteTask(task.id) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Prioritize", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(task.title, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/**
 * A modern, premium notification banner that appears at the top-right.
 */
@Composable
fun NotificationBanner(message: String?, isError: Boolean, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    
    // HOLD the last known non-null values for the exit animation
    var displayMessage by remember { mutableStateOf("") }
    var displayIsError by remember { mutableStateOf(false) }
    
    LaunchedEffect(message) {
        if (message != null) {
            displayMessage = message
            displayIsError = isError
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = tween(600)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(600))
    ) {
        // High-Contrast Floating Pill Design
        Surface(
            modifier = Modifier
                .height(30.dp) // Slightly slimmer to be less intrusive
                .widthIn(min = 100.dp, max = 220.dp) // Reduced max width to protect title
                .clip(CircleShape)
                .clickable { onDismiss() },
            color = if (displayIsError) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (displayIsError) Icons.Default.PriorityHigh else Icons.Default.Done,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(Modifier.width(10.dp))
                
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(scrollState)
                ) {
                    Text(
                        displayMessage ?: "",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 0.4.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                blurRadius = 2f
                            )
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                
                if (scrollState.maxValue > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp).padding(start = 4.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        LaunchedEffect(message) {
            if (message != null) {
                scrollState.scrollTo(0)
                delay(1200)
                if (scrollState.maxValue > 0) {
                    val scrollDuration = (scrollState.maxValue.toFloat() / 40f * 1000f).toInt().coerceIn(2000, 12000)
                    scrollState.animateScrollTo(
                        value = scrollState.maxValue,
                        animationSpec = tween(durationMillis = scrollDuration, easing = LinearEasing)
                    )
                    delay(2500)
                } else {
                    delay(3500)
                }
                onDismiss()
            }
        }
    }
}

/**
 * A professional full-screen page that displays the latest changelog/release notes.
 */
@Composable
fun ChangelogScreen(version: String, notes: String, isDark: Boolean, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize().zIndex(200f),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Back Arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "What's New",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Scrollable Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HtmlReadmeViewer(html = notes, isDark = isDark)
            }
            
            // Bottom Action Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue to RiiSync", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Data class representing an item in the navigation suite.
 */
data class NavItem(val label: String, val subtitle: String, val icon: Any, val index: Int)
