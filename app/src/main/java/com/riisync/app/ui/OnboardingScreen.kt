/**
 * Premium Application Onboarding Experience.
 * This file contains a multi-stage interactive walkthrough that helps users configure
 * their environment, verify GitHub connectivity, and personalize their experience.
 */
package com.riisync.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riisync.app.R
import com.riisync.app.git.GitHubService
import com.riisync.app.shizuku.ShizukuHelper
import com.riisync.app.utils.PathUtils
import com.riisync.app.utils.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main interactive onboarding screen.
 */
@Composable
fun OnboardingScreen(settingsManager: SettingsManager, taskViewModel: GlobalTaskViewModel, onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDark by settingsManager.isDarkTheme

    // Permission state for gating
    var hasPermission by remember { 
        mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true)
    }
    
    // Live check when returning from system settings
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            hasPermission = android.os.Environment.isExternalStorageManager()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Gradient Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            // Header Progress
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(6) { index ->
                    val progress by animateFloatAsState(
                        targetValue = if (index <= pagerState.currentPage) 1f else 0f,
                        animationSpec = tween(500)
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> IntroStage()
                    1 -> HealthCheckStage(taskViewModel)
                    2 -> GitHubLinkStage(settingsManager, taskViewModel)
                    3 -> PermissionStage(hasPermission, isDark)
                    4 -> DatabaseSetupStage(settingsManager, taskViewModel)
                    5 -> AppearanceStage(settingsManager)
                }
            }

            // Navigation Bottom Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    val backColor = if (isDark) Color.White else Color.Black
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(56.dp).padding(horizontal = 8.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = backColor)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = backColor)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.previous), color = backColor, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }

                val isPermissionPage = pagerState.currentPage == 3
                val canContinue = !isPermissionPage || hasPermission || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R
                
                Button(
                    enabled = canContinue,
                    onClick = {
                        if (pagerState.currentPage < 5) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            settingsManager.setOnboardingComplete(true)
                            onFinish()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp).weight(1f).padding(start = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pagerState.currentPage == 5) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    val isGetStarted = pagerState.currentPage == 5
                    val contentColor = if (isGetStarted) Color.White else if (isDark) Color.White else Color.White
                     
                    Text(
                        if (isGetStarted) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_next_step),
                        color = if (canContinue) contentColor else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (isGetStarted) Icons.Default.RocketLaunch else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (canContinue) contentColor else Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * Stage 1: Brand Introduction
 */
@Composable
fun IntroStage() {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
    )

    val subtitles = listOf(
        "All of your modding needings, in one place.",
        "Deep search on Github and manage your stuff with ease.",
        "Tune your mods with Dolphin, always, and everywhere.",
        "Manage your XML patch files.",
        "Designed with love for the community."
    )
    
    var subtitleIndex by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            visible = false
            delay(500)
            subtitleIndex = (subtitleIndex + 1) % subtitles.size
            visible = true
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.favicon),
            contentDescription = "Logo",
            modifier = Modifier
                .size(180.dp)
                .scale(logoScale)
                .clip(RoundedCornerShape(32.dp))
        )
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            "Welcome to Riisync!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.height(60.dp) // Fixed height to prevent layout jump
        ) {
            Text(
                subtitles[subtitleIndex],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.onboarding_intro_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Stage 2: System Health Check
 */
@Composable
fun HealthCheckStage(taskViewModel: GlobalTaskViewModel) {
    val context = LocalContext.current
    var isDolphinInstalled by remember { mutableStateOf(false) }
    var isMMJR2Installed by remember { mutableStateOf(false) }
    
    val shizukuActive by ShizukuHelper.isAvailable
    val shizukuPermission by ShizukuHelper.hasPermission

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        isDolphinInstalled = try { pm.getPackageInfo("org.dolphinemu.dolphinemu", 0); true } catch (e: Exception) { false }
        isMMJR2Installed = try { pm.getPackageInfo("org.dolphinemu.mmjr", 0); true } catch (e: Exception) { false }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.onboarding_health_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.onboarding_health_desc), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(Modifier.height(32.dp))

        val shizukuHealthy = shizukuActive && shizukuPermission
        val shizukuLimited = shizukuHealthy && !taskViewModel.wifiConnected
        
        HealthItem(
            title = "Shizuku Service",
            status = if (shizukuLimited) "Limited" 
                     else if (shizukuHealthy) "Ready" 
                     else stringResource(R.string.onboarding_status_action),
            isHealthy = shizukuHealthy,
            isWarning = shizukuLimited,
            icon = Icons.Default.Shield,
            description = if (shizukuLimited) stringResource(R.string.wifi_warning_desc)
                          else if (shizukuActive) "Permission required to access game data." 
                          else "Shizuku app must be running."
        )

        Spacer(Modifier.height(16.dp))

        HealthItem(
            title = "Dolphin Emulator",
            status = if (isDolphinInstalled || isMMJR2Installed) stringResource(R.string.onboarding_status_installed) else stringResource(R.string.onboarding_status_missing),
            isHealthy = isDolphinInstalled || isMMJR2Installed,
            icon = painterResource(R.drawable.ic_dolphin),
            description = "Either Official or MMJR2 Dolphin version is required to manage your mod syncs."
        )
        
        Spacer(Modifier.height(16.dp))

        HealthItem(
            title = stringResource(R.string.internet_connection),
            status = if (taskViewModel.internetConnected) "Ready" else stringResource(R.string.onboarding_status_limited),
            isHealthy = taskViewModel.internetConnected,
            icon = Icons.Default.Public,
            description = if (taskViewModel.internetConnected) "Device is connected to the internet." else "Internet connection is required for accessing Github features and more."
        )
    }
}

@Composable
fun HealthItem(title: String, status: String, isHealthy: Boolean, icon: Any, description: String, isWarning: Boolean = false) {
    val statusColor = when {
        isWarning -> Color(0xFFE6A700)
        isHealthy -> Color(0xFF1976D2)
        else -> Color(0xFFD32F2F)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(if (isHealthy && !isWarning) Color(0xFFE8F5E9) else if (isWarning) Color(0xFFFFF9C4) else Color(0xFFFFEBEE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (icon) {
                    is ImageVector -> Icon(icon, contentDescription = null, tint = statusColor)
                    is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = statusColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(status, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = if (isWarning) Color.Black else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(description, style = MaterialTheme.typography.labelSmall, color = if (isWarning) statusColor else Color.Gray)
            }
        }
    }
}

/**
 * Stage 3: GitHub Link
 */
@Composable
fun GitHubLinkStage(settingsManager: SettingsManager, taskViewModel: GlobalTaskViewModel) {
    var token by remember { mutableStateOf(settingsManager.token.value) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by taskViewModel::isTokenValid
    
    val githubService = remember { GitHubService() }
    val username by settingsManager.username
    val context = LocalContext.current

    // Automatic verification logic when token changes - Only if the token was actually edited
    LaunchedEffect(token) {
        val trimmedToken = token.trim()
        if (trimmedToken.isNotBlank()) {
            if (trimmedToken != settingsManager.token.value.trim() || checkResult == null) {
                delay(800) // Debounce
                isChecking = true
                val profile = githubService.getUserProfile("", trimmedToken)
                checkResult = profile != null
                if (checkResult == true && profile != null) {
                    settingsManager.setGitHubCredentials(profile.login, trimmedToken)
                }
                isChecking = false
            }
        } else {
            checkResult = null
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.onboarding_github_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.onboarding_github_desc), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(Modifier.height(32.dp))

        // --- GITHUB & GIT IDENTITY (shared with Settings) ---
        var authorName by remember { mutableStateOf(settingsManager.authorName.value) }
        var authorEmail by remember { mutableStateOf(settingsManager.authorEmail.value) }
        var rootFolder by remember { mutableStateOf(settingsManager.rootCloneFolder.value) }

        val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                        val path = PathUtils.getAbsolutePath(context, it)
                if (path != null) {
                    rootFolder = path
                    settingsManager.setRootCloneFolder(path)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.1f
                )
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {}

                if (checkResult == true && username.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.logged_as, username),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        if (it != settingsManager.token.value) {
                            checkResult = null
                        }
                    },
                    label = { Text(stringResource(R.string.pat)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (token.isNotEmpty()) {
                            IconButton(onClick = {
                                token = ""
                                checkResult = null
                                settingsManager.setGitHubCredentials("", "")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )

                // Dynamic PAT Guide: Only show when failed or waiting for input
                if (checkResult != true && !isChecking) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.Help,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.pat_guide_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            stringResource(R.string.pat_guide_steps),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Status Viewer (Automatic)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = when (checkResult) {
                        true -> Color(0xFF1976D2)
                        false -> if (token.isNotBlank()) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isChecking) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Verifying...", color = Color.White)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (checkResult) {
                                    true -> Icons.Default.CheckCircle
                                    false -> if (token.isNotBlank()) Icons.Default.Error else Icons.Default.CloudSync
                                    else -> Icons.Default.CloudSync
                                }
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (checkResult == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = when (checkResult) {
                                        true -> stringResource(R.string.onboarding_verified)
                                        false -> if (token.isNotBlank()) stringResource(R.string.onboarding_failed) else "Waiting for input..."
                                        else -> "Waiting for input..."
                                    },
                                    color = if (checkResult == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.default_clone_folder),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = rootFolder,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { dirLauncher.launch(null) }) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "Browse"
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Stage 4: Mandatory Storage Permission
 */
@Composable
fun PermissionStage(hasPermission: Boolean, isDark: Boolean) {
    val context = LocalContext.current
    
    // Notification permission state (API 33+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    val successBg = if (isDark) Color(0xFF1B2E1B) else Color(0xFFE8F5E9)
    val successContent = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (hasPermission) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = if (hasPermission) successContent else MaterialTheme.colorScheme.primary
        )
        
        // ... (title remains same)
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(32.dp))
        
        // --- STORAGE PERMISSION (Mandatory) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasPermission) successBg else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, if (hasPermission) successContent.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (hasPermission) successContent else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.all_files_access),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    stringResource(R.string.onboarding_permissions_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                if (!hasPermission) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_grant_storage), fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stringResource(R.string.onboarding_storage_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- NOTIFICATION PERMISSION (Optional) ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasNotificationPermission) successBg else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, if (hasNotificationPermission) successContent.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasNotificationPermission) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = if (hasNotificationPermission) successContent else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Push Notifications",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        stringResource(R.string.onboarding_notifications_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    if (!hasNotificationPermission) {
                        OutlinedButton(
                            onClick = { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_grant_notifications), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stage 5: Database Setup
 */
@Composable
fun DatabaseSetupStage(settingsManager: SettingsManager, taskViewModel: GlobalTaskViewModel) {
    val iconCount by settingsManager.dbIconCount
    val coverCount by settingsManager.dbCoverCount
    val isDbActive = taskViewModel.activeTasks.any { it.type == TaskType.SYSTEM && it.title.contains("Icon") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Wii Save Icon Database", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("RiiSync can download official animated icons and high-resolution covers for your mods.", 
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)

        Spacer(Modifier.height(48.dp))

        if (iconCount > 0 || coverCount > 0) {
            // Use same green as Finish Setup (0xFF2E7D32)
            val successColor = Color(0xFF2E7D32)
            Surface(
                color = successColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = successColor)
                        Spacer(Modifier.width(12.dp))
                        Text("Database Ready", color = successColor, fontWeight = FontWeight.Bold)
                    }
                    val total = iconCount + coverCount
                    Text(
                        "$iconCount ${if (iconCount == 1) "Icon" else "Icons"} • $coverCount ${if (coverCount == 1) "Cover" else "Covers"} ($total Total)", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = successColor.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = isDbActive,
                    enter = fadeIn(animationSpec = tween(600)),
                    exit = fadeOut()
                ) {
                    Text(
                        "You can continue with the onboarding.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = { taskViewModel.buildGameDatabase(settingsManager, mode = "EVERYTHING") },
                    enabled = !isDbActive,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isDbActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Downloading...", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Download Full Database", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Stage 6: Appearance stage
 */
@Composable
fun AppearanceStage(settingsManager: SettingsManager) {
    val isDark by settingsManager.isDarkTheme

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.onboarding_appearance_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.onboarding_appearance_desc), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(Modifier.height(48.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeCard(
                title = stringResource(R.string.light_theme),
                isSelected = !isDark,
                icon = Icons.Default.LightMode,
                onClick = { settingsManager.setDarkTheme(false) },
                modifier = Modifier.weight(1f)
            )
            ThemeCard(
                title = stringResource(R.string.dark_theme),
                isSelected = isDark,
                icon = Icons.Default.DarkMode,
                onClick = { settingsManager.setDarkTheme(true) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(Modifier.height(48.dp))
        
        Image(
            painter = painterResource(id = R.drawable.banner),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
fun ThemeCard(title: String, isSelected: Boolean, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray)
        }
    }
}

