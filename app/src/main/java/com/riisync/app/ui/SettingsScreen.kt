/**
 * Application Settings Screen.
 * This file provides a comprehensive interface for configuring app appearance,
 * storage permissions, automation rules, and Git identity.
 */
package com.riisync.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.riisync.app.R
import com.riisync.app.git.GitHubService
import com.riisync.app.utils.PathUtils
import com.riisync.app.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Composable representing the Settings screen.
 */
@Composable
fun SettingsScreen(settingsManager: SettingsManager, taskViewModel: GlobalTaskViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var hasFullStorageAccess by remember { mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) hasFullStorageAccess = android.os.Environment.isExternalStorageManager() }

    var token by remember { mutableStateOf(settingsManager.token.value) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by taskViewModel::isTokenValid
    
    val githubService = remember { GitHubService() }
    val username by settingsManager.username
    
    var authorName by remember { mutableStateOf(settingsManager.authorName.value) }
    var authorEmail by remember { mutableStateOf(settingsManager.authorEmail.value) }
    var rootFolder by remember { mutableStateOf(settingsManager.rootCloneFolder.value) }

    var showConfirmWipe by remember { mutableStateOf(false) }
    var showConfirmClearDb by remember { mutableStateOf(false) }
    var showInstallDolphinConfirm by remember { mutableStateOf<String?>(null) }
    var showIconGrabberDialog by remember { mutableStateOf(false) }
    var showUpdateGrabberDialog by remember { mutableStateOf(false) }

    // Automatic verification logic when token changes - only if edited
    LaunchedEffect(token) {
        val trimmedToken = token.trim()
        if (trimmedToken.isNotBlank()) {
            // Re-check only if token actually changed OR we don't have a result yet
            if (trimmedToken != settingsManager.token.value.trim() || checkResult == null) {
                delay(800) // Debounce
                isChecking = true
                val profile = githubService.getUserProfile("", trimmedToken)
                
                withContext(Dispatchers.Main) {
                    checkResult = profile != null
                    if (checkResult == true && profile != null) {
                        settingsManager.setGitHubCredentials(profile.login, trimmedToken)
                    }
                    isChecking = false
                }
            }
        } else {
            checkResult = null
        }
    }

    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            if (path != null) {
                rootFolder = path
                settingsManager.setRootCloneFolder(path)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // --- APPEARANCE ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = "Appearance Icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.appearance_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.dark_theme), style = MaterialTheme.typography.bodyLarge); Text(stringResource(R.string.dark_theme_desc), style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = settingsManager.isDarkTheme.value, onCheckedChange = { settingsManager.setDarkTheme(it) })
                }
            }
        }

        // --- AUTOMATION ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Automation Icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.automation), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.automation_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.auto_fetch), style = MaterialTheme.typography.bodyLarge); Text(stringResource(R.string.auto_fetch_desc), style = MaterialTheme.typography.bodySmall) }; Switch(checked = settingsManager.autoFetch.value, onCheckedChange = { settingsManager.setAutoFetch(it) }) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.auto_clear_cache), style = MaterialTheme.typography.bodyLarge); Text(stringResource(R.string.auto_clear_cache_desc), style = MaterialTheme.typography.bodySmall) }; Switch(checked = settingsManager.autoClearCache.value, onCheckedChange = { settingsManager.setAutoClearCache(it) }) }
            }
        }

        // --- DOLPHIN CONFIGURATION ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.dolphin_instances_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.dolphin_instances_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val dolphinVersions = mapOf(
                    "Official" to "org.dolphinemu.dolphinemu",
                    "MMJR2" to "org.dolphinemu.mmjr"
                )

                // Reactive package monitoring
                var installedPackages by remember { mutableStateOf(emptySet<String>()) }
                
                val refreshPackages = {
                    installedPackages = dolphinVersions.values.filter { pkg ->
                        try {
                            context.packageManager.getPackageInfo(pkg, 0)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }.toSet()
                }

                // Initial and periodic refresh
                LaunchedEffect(Unit) {
                    while(true) {
                        refreshPackages()
                        delay(2000) // Snappier 2s polling
                    }
                }

                // Immediate refresh when returning to app (e.g., after uninstall dialog)
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    refreshPackages()
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dolphinVersions.forEach { (label, pkg) ->
                        val isInstalled = installedPackages.contains(pkg)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (isInstalled) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_dolphin),
                                        contentDescription = null,
                                        tint = if (isInstalled) Color(0xFF2E7D32) else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text(if (isInstalled) stringResource(R.string.dolphin_installed) else stringResource(R.string.dolphin_not_installed), style = MaterialTheme.typography.labelSmall, color = if (isInstalled) Color(0xFF2E7D32) else Color.Gray)
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isInstalled) {
                                        IconButton(onClick = {
                                            showInstallDolphinConfirm = label
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Get", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        // Update Button
                                        IconButton(onClick = {
                                            if (label == "Official") {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                                                context.startActivity(intent)
                                            } else {
                                                showInstallDolphinConfirm = label
                                            }
                                        }) {
                                            Icon(if (label == "Official") Icons.Default.Update else Icons.Default.Refresh, contentDescription = "Update", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        // Delete/Uninstall Button
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    taskViewModel.notify(context.getString(R.string.error_format, "Could not launch uninstaller"), true)
                                                }
                                            },
                                            modifier = Modifier.size(48.dp) // Match IconButton default/larger size
                                        ) {
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = "Uninstall", 
                                                tint = Color(0xFFD32F2F),
                                                modifier = Modifier.size(24.dp) // Increased size
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- GITHUB & GIT IDENTITY ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.git_config), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    stringResource(R.string.git_identity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (checkResult == true && username.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pat_guide_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                    color = when(checkResult) {
                        true -> Color(0xFF2E7D32)
                        false -> if (token.isNotBlank()) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isChecking) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Verifying...", color = Color.White)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when(checkResult) {
                                    true -> Icons.Default.CheckCircle
                                    false -> if (token.isNotBlank()) Icons.Default.Error else Icons.Default.CloudSync
                                    else -> Icons.Default.CloudSync
                                }
                                Icon(icon, contentDescription = null, tint = if (checkResult == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = when(checkResult) {
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
                Text(stringResource(R.string.default_clone_folder), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = rootFolder,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { IconButton(onClick = { dirLauncher.launch(null) }) { Icon(Icons.Default.FolderOpen, contentDescription = "Browse") } }
                )
            }
        }

        // --- MAINTENANCE & RESET ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            @Suppress("DEPRECATION")
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.maintenance_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.maintenance_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Column {
                    Text(stringResource(R.string.manage_shizuku), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.manage_shizuku_desc), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (intent != null) context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(stringResource(R.string.open_shizuku_manager), color = Color.White) }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                
                Column {
                    Text(stringResource(R.string.clean_app_cache), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.clean_app_cache_desc), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        taskViewModel.runSimpleTask(context.getString(R.string.clean_app_cache), type = TaskType.SYSTEM) { _ ->
                            context.cacheDir.deleteRecursively()
                            context.externalCacheDir?.deleteRecursively()
                            taskViewModel.notify(context.getString(R.string.cache_cleared), false)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(stringResource(R.string.clean_app_cache), color = Color.White) }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                Column {
                    Text("Wii Save Icon Grabber", style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.clear_db_desc), style = MaterialTheme.typography.bodySmall)
                    
                    // --- DB STATUS INDICATOR ---
                    val lastSync = settingsManager.lastDbSync.value
                    val iconCount = settingsManager.dbIconCount.value
                    val coverCount = settingsManager.dbCoverCount.value
                    
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "$iconCount ${if (iconCount == 1) "Icon" else "Icons"}", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "$coverCount ${if (coverCount == 1) "Cover" else "Covers"}", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        val totalCount = iconCount + coverCount
                        if (totalCount > 0) {
                            Text(
                                "• $totalCount Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (lastSync > 0) {
                        val sdf = remember { java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()) }
                        Text(
                            "Last sync: ${sdf.format(java.util.Date(lastSync))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text("Never synchronized", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeIconTask = taskViewModel.activeTasks.find { it.type == TaskType.SYSTEM && it.title.contains("Icon") }
                    
                    if (activeIconTask != null) {
                        Button(
                            onClick = { taskViewModel.cancelTask(activeIconTask.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Process", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { showIconGrabberDialog = true },
                            enabled = !taskViewModel.isOperating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Build DB", color = Color.White)
                        }
                        
                        Button(
                            onClick = { showUpdateGrabberDialog = true },
                            enabled = !taskViewModel.isOperating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Update", color = Color.White)
                        }
                    }
                }
                
                Button(
                    onClick = { showConfirmClearDb = true },
                    enabled = !taskViewModel.isOperating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_db), color = Color.White)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                Column {
                    Text(stringResource(R.string.restart_setup), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.restart_setup_desc), style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            taskViewModel.clearNotification()
                            settingsManager.setOnboardingComplete(false) 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(stringResource(R.string.start_over_config), color = Color.White) }
                    
                    OutlinedButton(
                        onClick = { 
                            settingsManager.resetWarnings()
                            taskViewModel.notify("Preference prompts reset.", false)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) { 
                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Prompts") 
                    }
                }

                HorizontalDivider(color = Color(0xFFD32F2F).copy(alpha = 0.2f))
                Button(
                    onClick = { showConfirmWipe = true },
                    enabled = !taskViewModel.isOperating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.delete_all_data), color = Color.White) }
            }
        }
        
        Spacer(Modifier.height(40.dp))
    }

    if (showIconGrabberDialog) {
        val installedMods = settingsManager.installedMods.value
        val hasMods = installedMods.isNotEmpty()
        
        AlertDialog(
            onDismissRequest = { showIconGrabberDialog = false },
            title = { Text("Choose what to download") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Here you can choose if you want to download the entire save icons database or only the detected save icons of your current synced mods with Dolphin.", style = MaterialTheme.typography.bodyMedium)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                taskViewModel.buildGameDatabase(settingsManager, mode = "EVERYTHING")
                                showIconGrabberDialog = false 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download Everything")
                        }

                        Column {
                            Button(
                                onClick = { 
                                    taskViewModel.buildTargetedIconDatabase(settingsManager, installedMods)
                                    showIconGrabberDialog = false 
                                },
                                enabled = hasMods,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("Download Only Synced Mods Icons")
                            }
                            if (!hasMods) {
                                Text(
                                    "You can't select that option: first, you have to sync a mod with Dolphin.",
                                    color = Color(0xFFF9A825),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showIconGrabberDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showUpdateGrabberDialog) {
        val installedMods = settingsManager.installedMods.value
        val hasMods = installedMods.isNotEmpty()
        
        AlertDialog(
            onDismissRequest = { showUpdateGrabberDialog = false },
            title = { Text("Update Icon Database") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose how you want to update your save icons.", style = MaterialTheme.typography.bodyMedium)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                taskViewModel.buildGameDatabase(settingsManager, mode = "EVERYTHING")
                                showUpdateGrabberDialog = false 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Update Everything")
                        }

                        Column {
                            Button(
                                onClick = { 
                                    taskViewModel.buildTargetedIconDatabase(settingsManager, installedMods)
                                    showUpdateGrabberDialog = false 
                                },
                                enabled = hasMods,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("Update/Add Linked Mod Icons")
                            }
                            if (!hasMods) {
                                Text(
                                    "You can't select that option: first, you have to sync a mod with Dolphin.",
                                    color = Color(0xFFF9A825),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showUpdateGrabberDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showConfirmClearDb) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDb = false },
            title = { Text(stringResource(R.string.confirm_clear_db_title)) },
            text = { Text(stringResource(R.string.confirm_clear_db_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        taskViewModel.clearGameDatabase(settingsManager)
                        showConfirmClearDb = false
                        taskViewModel.notify(context.getString(R.string.database_cleared), false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text(stringResource(R.string.clear_db), color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showConfirmClearDb = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showConfirmWipe) {
        AlertDialog(
            onDismissRequest = { showConfirmWipe = false },
            title = { Text(stringResource(R.string.confirm_reset_title)) },
            text = { Text(stringResource(R.string.confirm_reset_msg)) },
            confirmButton = {
                Button(onClick = {
                    taskViewModel.runSimpleTask(context.getString(R.string.wiping_data), type = TaskType.SYSTEM) { _ ->
                        settingsManager.wipeAllData()
                        withContext(Dispatchers.Main) {
                            showConfirmWipe = false
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text(stringResource(R.string.wipe_everything), color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showConfirmWipe = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    showInstallDolphinConfirm?.let { label ->
        AlertDialog(
            onDismissRequest = { showInstallDolphinConfirm = null },
            title = { Text(stringResource(R.string.dolphin_download_confirm_title)) },
            text = { 
                Text(
                    if (label == "Official") stringResource(R.string.dolphin_official_download_msg)
                    else stringResource(R.string.dolphin_mmjr2_download_msg)
                ) 
            },
            confirmButton = {
                Button(onClick = {
                    val githubRepo = when(label) {
                        "MMJR2" -> "Medard22" to "Dolphin-MMJR2-VBI"
                        else -> null
                    }

                    showInstallDolphinConfirm = null
                    
                    if (label == "Official") {
                        val pkg = "org.dolphinemu.dolphinemu"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                        context.startActivity(intent)
                        return@Button
                    }
                    
                    if (com.riisync.app.utils.ApkDownloader.canInstallPackages(context)) {
                        taskViewModel.runTask(context.getString(R.string.maintenance_title), type = TaskType.SYSTEM) { monitor ->
                            monitor.beginTask(context.getString(R.string.onboarding_intro_desc), 100) // Placeholder
                            
                            val resolvedUrl = if (githubRepo == null) {
                                com.riisync.app.utils.ApkDownloader.resolveDolphinOfficialUrl()
                            } else {
                                null
                            }

                            if (githubRepo != null) {
                                com.riisync.app.utils.ApkDownloader.downloadFromGitHub(
                                    context,
                                    githubRepo.first,
                                    githubRepo.second,
                                    "dolphin_$label.apk",
                                    settingsManager.token.value.ifBlank { null },
                                    onProgress = { progress ->
                                        monitor.update((progress * 100).toInt())
                                    },
                                    onComplete = { file ->
                                        monitor.endTask()
                                        taskViewModel.notify(context.getString(R.string.dolphin_download_complete), false)
                                        com.riisync.app.utils.ApkDownloader.installApk(context, file)
                                    },
                                    onError = { error ->
                                        taskViewModel.notify(context.getString(R.string.dolphin_download_failed, error), true)
                                    }
                                )
                            } else if (resolvedUrl != null) {
                                com.riisync.app.utils.ApkDownloader.downloadDirect(
                                    context,
                                    resolvedUrl,
                                    "dolphin_$label.apk",
                                    onProgress = { progress ->
                                        monitor.update((progress * 100).toInt())
                                    },
                                    onComplete = { file ->
                                        monitor.endTask()
                                        taskViewModel.notify(context.getString(R.string.dolphin_download_complete), false)
                                        com.riisync.app.utils.ApkDownloader.installApk(context, file)
                                    },
                                    onError = { error ->
                                        taskViewModel.notify(context.getString(R.string.dolphin_download_failed, error), true)
                                    }
                                )
                            }
                        }
                    } else {
                        com.riisync.app.utils.ApkDownloader.requestInstallPermission(context)
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showInstallDolphinConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
