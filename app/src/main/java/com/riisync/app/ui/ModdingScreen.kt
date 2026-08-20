/**
 * Mod Management Center.
 * This file provides tools for scanning local mod folders
 * and linking Riivolution mods to Dolphin Emulator using Shizuku.
 */
package com.riisync.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.riisync.app.R
import com.riisync.app.shizuku.RiivolutionLinker
import com.riisync.app.shizuku.ShizukuHelper
import com.riisync.app.utils.ApkDownloader
import com.riisync.app.utils.PathUtils
import com.riisync.app.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import java.io.File
import java.util.UUID

/**
 * Main modding screen providing mod scanning and connection tools.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ModdingScreen(taskViewModel: GlobalTaskViewModel, settingsManager: SettingsManager, onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    
    var modToManage by remember { mutableStateOf<SettingsManager.ManagedMod?>(null) }

    // Folder Launcher for RELOCATING an existing mod
    val relocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            val mod = modToManage
            if (path != null && mod != null) {
                taskViewModel.runTask(
                    title = "Relocating ${mod.name}",
                    type = TaskType.MOD
                ) { monitor ->
                    val result = RiivolutionLinker.validateFolderStrict(path)
                    when (result) {
                        is RiivolutionLinker.ValidationResult.Success -> {
                            val validated = result.mod
                            
                            // 1. Delete OLD linked destination files
                            RiivolutionLinker.unlink(mod.modFilesFolderName)
                            
                            // 2. Sync NEW source files
                            val resXml = RiivolutionLinker.linkXmlConfig(validated.xmlFile.absolutePath, validated.xmlFile.name)
                            val resMod = RiivolutionLinker.linkModFolder(validated.modFolder.absolutePath, validated.modFolder.name)
                            
                            if (resXml.contains("failed") || resMod.contains("failed")) {
                                taskViewModel.notify("Relocation failed during linking phase.", true)
                            } else {
                                // 3. Update the entry in settings
                                val updated = settingsManager.getInstalledMods().toMutableList()
                                val index = updated.indexOfFirst { it.name == mod.name }
                                if (index != -1) {
                                    updated[index] = updated[index].copy(
                                        sourcePath = path,
                                        riivolutionFolderName = validated.riivolutionFolder.name,
                                        xmlFileName = validated.xmlFile.name,
                                        modFilesFolderName = validated.modFolder.name,
                                        gameId = validated.gameId
                                    )
                                    settingsManager.saveInstalledMods(updated)
                                    taskViewModel.notify("Mod relocated successfully!", false)
                                }
                            }
                        }
                        is RiivolutionLinker.ValidationResult.Error -> {
                            taskViewModel.notify(result.message, true)
                        }
                    }
                }
            }
            modToManage = null // Clear state regardless of outcome
        }
    }

    // Check for Dolphin installation
    var isDolphinInstalled by remember { mutableStateOf(false) }
    
    val refreshDolphinStatus = {
        val pm = context.packageManager
        val official = try { pm.getPackageInfo("org.dolphinemu.dolphinemu", 0); true } catch (e: Exception) { false }
        val mmjr = try { pm.getPackageInfo("org.dolphinemu.mmjr", 0); true } catch (e: Exception) { false }
        isDolphinInstalled = official || mmjr
    }

    LaunchedEffect(Unit) {
        while(true) {
            refreshDolphinStatus()
            delay(2000) // 2s polling
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshDolphinStatus()
    }

    // Mod Selection State bound to ViewModel
    val pendingMod by remember { derivedStateOf { taskViewModel.pendingMod } }
    val pendingModName by remember { derivedStateOf { taskViewModel.pendingModName } }
    
    // Local UI State
    val installedMods by settingsManager.installedMods
    val externalFolders = taskViewModel.externalFolders
    var modToDelete by remember { mutableStateOf<SettingsManager.ManagedMod?>(null) }
    var unknownFolderToDelete by remember { mutableStateOf<String?>(null) }

    var showModScanWarning by remember { mutableStateOf(false) }
    var dontShowAgainWarning by remember { mutableStateOf(false) }

    // GAME ID AUTO-DISCOVERY MIGRATION
    LaunchedEffect(installedMods) {
        val modsToUpdate = installedMods.filter { it.gameId == null }
        if (modsToUpdate.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val updatedList = installedMods.toMutableList()
                var changed = false
                modsToUpdate.forEach { mod ->
                    val xmlFile = File(mod.sourcePath, "${mod.riivolutionFolderName}/${mod.xmlFileName}")
                    if (xmlFile.exists()) {
                        val content = try { xmlFile.readText() } catch (e: Exception) { "" }
                        val gId = RiivolutionLinker.extractGameId(content)
                        if (gId != null) {
                            val index = updatedList.indexOfFirst { it.name == mod.name }
                            if (index != -1) {
                                updatedList[index] = updatedList[index].copy(gameId = gId)
                                changed = true
                            }
                        }
                    }
                }
                if (changed) {
                    withContext(Dispatchers.Main) {
                        settingsManager.saveInstalledMods(updatedList)
                    }
                }
            }
        }
    }

    // Shizuku State Monitoring
    val shizukuAvailable by ShizukuHelper.isAvailable
    val shizukuGranted by ShizukuHelper.hasPermission
    var isShizukuInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isShizukuInstalled = try { context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (e: Exception) { false }
    }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    var showInstallPermissionDialog by remember { mutableStateOf(false) }
    var pendingInstallationType by remember { mutableStateOf("new") }

    // Install flow UI states for Shizuku/Dolphin downloads
    var showShizukuInstallChoice by remember { mutableStateOf(false) }
    var shizukuFilenameHint by remember { mutableStateOf("shizuku.apk") }
    var showInstallResultDialog by remember { mutableStateOf(false) }
    var installResultTitle by remember { mutableStateOf("") }
    var installResultMessage by remember { mutableStateOf("") }

    val installerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Prefer checking package presence for a definitive success when known
        val expectedPkg = if (pendingInstallationType == "new") "moe.shizuku.privileged.api" else "moe.shizuku.privileged.api"
        val pm = context.packageManager
        val installed = try { pm.getPackageInfo(expectedPkg, 0); true } catch (e: Exception) { false }
        if (installed) {
            installResultTitle = "Installed"
            installResultMessage = "Shizuku installed successfully."
        } else {
            when (result.resultCode) {
                android.app.Activity.RESULT_OK -> {
                    installResultTitle = "Installed"
                    installResultMessage = "Shizuku installed successfully."
                }
                android.app.Activity.RESULT_CANCELED -> {
                    installResultTitle = "Aborted"
                    installResultMessage = "Installation was cancelled by the user."
                }
                else -> {
                    installResultTitle = "Failed"
                    installResultMessage = "Installation failed."
                }
            }
        }
        showInstallResultDialog = true
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            if (path != null) {
                taskViewModel.runTask(
                    title = context.getString(R.string.mod_validating_folder),
                    type = TaskType.MOD
                ) { monitor ->
                    val result = RiivolutionLinker.validateFolderStrict(path)
                    when (result) {
                        is RiivolutionLinker.ValidationResult.Success -> {
                            val validated = result.mod
                            withContext(Dispatchers.Main) {
                                taskViewModel.pendingMod = PendingMod(
                                    riivolutionFolder = validated.riivolutionFolder.absoluteFile,
                                    modFolder = validated.modFolder.absoluteFile,
                                    xmlFile = validated.xmlFile.absoluteFile,
                                    validationMessage = validated.message,
                                    modRootPath = File(path).absolutePath,
                                    gameId = validated.gameId
                                )
                                taskViewModel.pendingModName = ""
                                taskViewModel.setTabAttention(1, true)
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) 
                            }
                        }
                        is RiivolutionLinker.ValidationResult.Error -> {
                            taskViewModel.notify(result.message, true)
                        }
                    }
                }
            }
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            ModdingListPane(
                taskViewModel = taskViewModel,
                settingsManager = settingsManager,
                shizukuGranted = shizukuGranted,
                shizukuAvailable = shizukuAvailable,
                isShizukuInstalled = isShizukuInstalled,
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                installedMods = installedMods,
                externalFolders = externalFolders,
                isAnyDolphinInstalled = isDolphinInstalled,
                onScanLocal = { 
                    if (settingsManager.showModScanWarning.value) {
                        showModScanWarning = true
                    } else {
                        folderLauncher.launch(null)
                    }
                },
                onDeleteMod = { modToDelete = it },
                onDeleteExternal = { unknownFolderToDelete = it },
                onShizukuAction = { 
                    if (!isShizukuInstalled) {
                        // Ask user whether to open browser or download/install in-app
                        shizukuFilenameHint = "shizuku.apk"
                        pendingInstallationType = "new"
                        showShizukuInstallChoice = true
                    } else if (shizukuAvailable && !shizukuGranted) {
                        ShizukuHelper.requestPermission()
                    }
                },
                onSyncAll = { taskViewModel.syncAllMods(settingsManager) },
                onNavigateToSettings = onNavigateToSettings,
                onManageMod = { modToManage = it }
            )
        },
        detailPane = {
            val mod = pendingMod // LOCAL CAPTURE FOR SMART CAST
            if (mod != null) {
                ModdingDetailPane(
                    shizukuGranted = shizukuGranted,
                    taskViewModel = taskViewModel,
                    settingsManager = settingsManager,
                    modName = pendingModName,
                    onModNameChange = { taskViewModel.pendingModName = it },
                    validationMessage = mod.validationMessage,
                    riivolutionFolder = mod.riivolutionFolder,
                    modFolder = mod.modFolder,
                    xmlFile = mod.xmlFile,
                    modRootPath = mod.modRootPath,
                    onLink = {
                        val mName = taskViewModel.pendingModName
                        if (mName.isBlank()) { taskViewModel.notify(context.getString(R.string.enter_mod_name), true); return@ModdingDetailPane }
                        if (!shizukuGranted) { ShizukuHelper.requestPermission(); return@ModdingDetailPane }
                        if (!taskViewModel.wifiConnected) { taskViewModel.notify(context.getString(R.string.shizuku_needs_wifi), true); return@ModdingDetailPane }
                        
                        taskViewModel.runSimpleTask(context.getString(R.string.syncing_mod), mod.modFolder.absolutePath, type = TaskType.MOD) { task ->
                            val riiv = mod.riivolutionFolder.absoluteFile
                            val mData = mod.modFolder.absoluteFile
                            val xml = mod.xmlFile
                            
                            val dolphinVersions = listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr")
                            var overallSuccess = true
                            var errorLogs = ""

                            dolphinVersions.forEach { pkg ->
                                try {
                                    val label = if (pkg.contains("mmjr")) "MMJR2" else "Official"
                                    task.currentSubTask.value = "Linking with $label..."
                                    val resXml = RiivolutionLinker.linkXmlConfig(xml.absolutePath, xml.name, pkg = pkg)
                                    val resMod = RiivolutionLinker.linkModFolder(mData.absolutePath, mData.name, pkg = pkg)
                                    if (resXml.contains("failed") || resMod.contains("failed")) {
                                        overallSuccess = false
                                        errorLogs += "[$label] $resXml $resMod\n"
                                    }
                                } catch (e: Exception) {
                                    overallSuccess = false
                                    errorLogs += "[${pkg}] Error: ${e.message}\n"
                                }
                            }
                            
                            if (overallSuccess) {
                                task.progress.value = 1f
                                task.currentSubTask.value = "Success!"
                                delay(800)
                                
                                val existing = settingsManager.getInstalledMods().find { it.name == mName }
                                settingsManager.addInstalledMod(SettingsManager.ManagedMod(
                                    name = mName, sourcePath = mod.modRootPath, riivolutionFolderName = riiv.name, 
                                    xmlFileName = xml.name, modFilesFolderName = mData.name,
                                    syncCount = (existing?.syncCount ?: 0) + 1, lastSyncTimestamp = System.currentTimeMillis(),
                                    gameId = mod.gameId, gameTitle = mod.gameTitle
                                ))
                                
                                withContext(Dispatchers.Main) {
                                    taskViewModel.pendingMod = null
                                    taskViewModel.setTabAttention(1, false)
                                    scope.launch { navigator.navigateBack() }
                                }
                            } else {
                                taskViewModel.notify("Linking failed:\n$errorLogs", true)
                            }
                        }
                    },
                    onCancel = {
                        taskViewModel.pendingMod = null
                        taskViewModel.setTabAttention(1, false)
                        scope.launch { navigator.navigateBack() }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Text("Select a mod to configure", color = Color.Gray)
                    }
                }
            }
        }
    )

    if (showUpdateConfirm) { AlertDialog(onDismissRequest = { showUpdateConfirm = false }, title = { Text("Shizuku Update Available") }, text = { Text("A newer version of Shizuku is available. Update now?") }, confirmButton = { Button(onClick = { 
                showUpdateConfirm = false
                // Use the same choice dialog but hint to download the update variant
                shizukuFilenameHint = "shizuku_update.apk"
                pendingInstallationType = "update"
                showShizukuInstallChoice = true
            }) { Text("Update") } }, dismissButton = { TextButton(onClick = { showUpdateConfirm = false }) { Text("Later") } }) }
    if (showInstallPermissionDialog) { AlertDialog(onDismissRequest = { showInstallPermissionDialog = false }, title = { Text("Grant Install Permission") }, text = { Text(stringResource(R.string.install_permission_required)) }, confirmButton = { Button(onClick = { showInstallPermissionDialog = false; ApkDownloader.requestInstallPermission(context) }) { Text("Go to Settings") } }, dismissButton = { TextButton(onClick = { showInstallPermissionDialog = false }) { Text("Cancel") } }) }

    // Choice dialog for Shizuku download
    if (showShizukuInstallChoice) {
        AlertDialog(
            onDismissRequest = { showShizukuInstallChoice = false },
            modifier = Modifier.widthIn(max = 420.dp),
            title = { Text("Download Manager") },
            text = {
                Text(
                    "Do you want to download Shizuku?\n\nYour default browser will be open.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showShizukuInstallChoice = false
                    scope.launch {
                        val url = ApkDownloader.getLatestReleaseAssetUrl("RikkaApps", "Shizuku", shizukuFilenameHint, settingsManager.token.value.ifBlank { null })
                        if (url != null) ApkDownloader.openUrlInBrowser(context, url) else ApkDownloader.openUrlInBrowser(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showShizukuInstallChoice = false }) { Text("Cancel") } }
        )
    }

    if (showInstallResultDialog) {
        AlertDialog(onDismissRequest = { showInstallResultDialog = false }, title = { Text(installResultTitle) }, text = { Text(installResultMessage) }, confirmButton = { Button(onClick = { showInstallResultDialog = false }) { Text("OK") } })
    }

    if (showModScanWarning) {
        AlertDialog(
            onDismissRequest = { showModScanWarning = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFF9A825))
                    Text("Hold up!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("To make this working, make sure you select a folder that contains the following structure:", style = MaterialTheme.typography.bodyMedium)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("• \"riivolution\" folder, containing XML patch.", style = MaterialTheme.typography.bodySmall)
                            Text("• \"mod\" folder, named as it is in the patch file.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgainWarning = !dontShowAgainWarning }
                    ) {
                        Checkbox(checked = dontShowAgainWarning, onCheckedChange = { dontShowAgainWarning = it })
                        Text("Don't show again", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (dontShowAgainWarning) settingsManager.setShowModScanWarning(false)
                    showModScanWarning = false
                    folderLauncher.launch(null)
                }) { Text("I understand") }
            },
            dismissButton = {
                TextButton(onClick = { showModScanWarning = false }) { Text("Cancel") }
            }
        )
    }

    modToDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { modToDelete = null },
            title = { Text("Delete Managed Mod") },
            text = { Text("Are you sure you want to remove '${mod.name}'? This will NOT delete the local files, only the configuration inside RiiSync and Dolphin links.") },
            confirmButton = { Button(onClick = { settingsManager.removeInstalledMod(mod.name); modToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { modToDelete = null }) { Text("Cancel") } }
        )
    }

    modToManage?.let { mod ->
        AlertDialog(
            onDismissRequest = { modToManage = null },
            title = { Text("Choose your destination") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Do you want to edit your mod's entry or re-locate the source files?", style = MaterialTheme.typography.bodyMedium)
                    Text("Mod: ${mod.name}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // EDIT Button
                    Button(
                        onClick = {
                            taskViewModel.pendingMod = PendingMod(
                                riivolutionFolder = File(mod.sourcePath, mod.riivolutionFolderName),
                                modFolder = File(mod.sourcePath, mod.modFilesFolderName),
                                xmlFile = File(mod.sourcePath, "${mod.riivolutionFolderName}/${mod.xmlFileName}"),
                                validationMessage = "Editing existing mod",
                                modRootPath = mod.sourcePath,
                                gameId = mod.gameId,
                                gameTitle = mod.gameTitle
                            )
                            taskViewModel.pendingModName = mod.name
                            modToManage = null
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                        }
                    ) { Text("Edit") }

                    // RELOCATE Button
                    Button(
                        onClick = { 
                            relocationLauncher.launch(null)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("Relocate") }
                }
            },
            dismissButton = {
                TextButton(onClick = { modToManage = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * List pane for [ModdingScreen], managing mod scanning and Shizuku status.
 */
@Composable
fun ModdingListPane(
    taskViewModel: GlobalTaskViewModel,
    settingsManager: SettingsManager,
    shizukuGranted: Boolean,
    shizukuAvailable: Boolean,
    isShizukuInstalled: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    installedMods: List<SettingsManager.ManagedMod>,
    externalFolders: List<String>,
    isAnyDolphinInstalled: Boolean,
    onScanLocal: () -> Unit,
    onDeleteMod: (SettingsManager.ManagedMod) -> Unit,
    onDeleteExternal: (String) -> Unit,
    onShizukuAction: () -> Unit,
    onSyncAll: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onManageMod: (SettingsManager.ManagedMod) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val modHealth by produceState(initialValue = emptyMap<String, Boolean>(), installedMods, shizukuGranted) {
        if (shizukuGranted) {
            val service = ShizukuHelper.fileService
            if (service != null) {
                val dolphinBase = RiivolutionLinker.dolphinRiivolutionPath()
                val health = mutableMapOf<String, Boolean>()
                installedMods.forEach { mod ->
                    val sourceExists = File(mod.sourcePath).exists()
                    val xmlExists = try { service.exists(File(dolphinBase, "riivolution/${mod.xmlFileName}").absolutePath) } catch (e: Exception) { false }
                    val folderExists = try { service.exists(File(dolphinBase, mod.modFilesFolderName).absolutePath) } catch (e: Exception) { false }
                    health[mod.name] = sourceExists && xmlExists && folderExists
                }
                value = health
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.modding_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.modding_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        val shizukuActiveColor = if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
        val statusColor = if (shizukuGranted) shizukuActiveColor else if (shizukuAvailable) Color(0xFFE6A700) else MaterialTheme.colorScheme.error
        val statusText = if (!isShizukuInstalled) stringResource(R.string.shizuku_status_not_installed) else if (shizukuGranted) stringResource(R.string.shizuku_status_active) else if (shizukuAvailable) stringResource(R.string.shizuku_status_unauthorized) else stringResource(R.string.shizuku_status_inactive)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)), border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (shizukuGranted) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = "Status Icon", tint = statusColor)
                    Text(stringResource(R.string.shizuku_status) + ": " + statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = statusColor)
                }
                if (!isShizukuInstalled) {
                    Button(onClick = onShizukuAction, enabled = !isDownloading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, "Download"); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.download_shizuku)) }
                } else if (!shizukuAvailable) {
                    OutlinedButton(onClick = { context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { context.startActivity(it) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open Shizuku") }
                } else if (!shizukuGranted) {
                    Button(onClick = onShizukuAction, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.grant_permission)) }
                } else {
                    Text(if (taskViewModel.wifiConnected) stringResource(R.string.shizuku_running) else "Service is active, but:", style = MaterialTheme.typography.bodySmall)
                    if (!taskViewModel.wifiConnected) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            border = BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.wifi_warning_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFF9A825),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.wifi_warning_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.DarkGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
                if (isDownloading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(R.string.downloading_shizuku),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        if (!isAnyDolphinInstalled) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SportsEsports, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Text("No supported emulator installed!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Button(onClick = onNavigateToSettings, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Download Now"); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                }
            }
        } else if (shizukuGranted) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.FlashOn, null, tint = MaterialTheme.colorScheme.primary); Text("Quick Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { /* Clear cache */ }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.DeleteSweep, null, tint = Color.White); Text("Clean Cache", color = Color.White, fontSize = 10.sp) }
                        Button(
                            onClick = onSyncAll, 
                            enabled = taskViewModel.wifiConnected,
                            modifier = Modifier.weight(1f), 
                            shape = RoundedCornerShape(12.dp), 
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2), disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) { 
                            Icon(
                                Icons.Default.Sync, 
                                null, 
                                tint = if (taskViewModel.wifiConnected) Color.White else Color.Gray.copy(alpha = 0.5f)
                            )
                            Text("Sync All", color = if (taskViewModel.wifiConnected) Color.White else Color.Gray, fontSize = 10.sp) 
                        }
                    }
                }
            }

            Text(stringResource(R.string.managed_mods), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            if (installedMods.isEmpty() && externalFolders.isEmpty()) Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_results), color = Color.Gray) }
            else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    installedMods.forEach { mod ->
                        val isHealthy = modHealth[mod.name] ?: true
                        val isDbActive = taskViewModel.activeTasks.any { it.type == TaskType.SYSTEM && it.title.contains("Icon") }
                        
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // 1. Leading Icon (Local-First with Live Refresh)
                                val baseRiisyncDir = File("/storage/emulated/0", "RiiSync")
                                val animatedIconFile = File(baseRiisyncDir, "database/${mod.gameId?.take(4)}/icon_animated.png")
                                val hasLocalIcon = animatedIconFile.exists()
                                
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                                ) {
                                    when {
                                        hasLocalIcon -> {
                                            AsyncImage(
                                                model = animatedIconFile, 
                                                contentDescription = "Mod Icon", 
                                                modifier = Modifier.fillMaxSize().padding(2.dp), 
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                        isDbActive -> {
                                            Box(contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            }
                                        }
                                        else -> {
                                            // Show download arrow button as requested
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable(enabled = taskViewModel.wifiConnected) { 
                                                        taskViewModel.buildTargetedIconDatabase(settingsManager, listOf(mod))
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Download, 
                                                    contentDescription = "Download Icon", 
                                                    tint = if (taskViewModel.wifiConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.width(16.dp))

                                // 2. Content Info
                                Column(Modifier.weight(1f)) {
                                    Text(mod.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    if (mod.gameId != null) {
                                        Text(
                                            mod.gameId, 
                                            style = MaterialTheme.typography.labelSmall, 
                                            color = MaterialTheme.colorScheme.primary, 
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
                                        )
                                    }
                                    Text(stringResource(R.string.sync_stats) + ": ${mod.syncCount}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }

                                // 3. Trailing Status & Actions
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Health Indicator (Moved here)
                                    Icon(
                                        if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning, 
                                        contentDescription = "Status", 
                                        tint = if (isHealthy) Color(0xFF1976D2) else Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    
                                    Spacer(Modifier.width(4.dp))
                                    
                                    IconButton(onClick = { onManageMod(mod) }) { 
                                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)) 
                                    }
                                    
                                    IconButton(onClick = { onDeleteMod(mod) }) { 
                                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.8f)) 
                                    }
                                }
                            }
                        }
                    }
                    externalFolders.forEach { folderName ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QuestionMark, null, tint = Color.Gray)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text("External Folder", fontWeight = FontWeight.Bold); Text("Path: .../$folderName", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                                IconButton(onClick = { onDeleteExternal(folderName) }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                Button(
                    onClick = onScanLocal, 
                    modifier = Modifier.fillMaxWidth(), 
                    enabled = taskViewModel.wifiConnected,
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_local)) 
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ModdingDetailPane(
    shizukuGranted: Boolean,
    taskViewModel: GlobalTaskViewModel,
    settingsManager: SettingsManager,
    modName: String,
    onModNameChange: (String) -> Unit,
    validationMessage: String,
    riivolutionFolder: File?,
    modFolder: File?,
    xmlFile: File?,
    modRootPath: String,
    onLink: () -> Unit,
    onCancel: () -> Unit
) {
    val isLinking = modFolder?.let { taskViewModel.isPathActive(it.absolutePath) } ?: false
    var showXmlEditor by remember { mutableStateOf(false) }

    // REAL-TIME INTEGRITY WATCHER
    LaunchedEffect(modFolder, xmlFile, riivolutionFolder) {
        while (true) {
            delay(2000)
            if (modFolder == null || xmlFile == null || riivolutionFolder == null) break
            
            val modFolderExists = modFolder.exists()
            val xmlFileExists = xmlFile.exists()
            
            if (!modFolderExists) {
                taskViewModel.notify("Your mod game files got corrupted or deleted! Reverting...", true)
                delay(1500)
                onCancel()
                break
            }
            
            if (!xmlFileExists) {
                taskViewModel.notify("Your XML patch file got corrupted or deleted! Reverting...", true)
                delay(1500)
                onCancel()
                break
            }

            // Check if root tag is still present in XML
            val content = try { xmlFile.readText() } catch (e: Exception) { "" }
            if (!content.contains("root=['\"]/?([^'\"]+)['\"]".toRegex())) {
                taskViewModel.notify("Your XML patch file is missing the root attribute! Reverting...", true)
                delay(1500)
                onCancel()
                break
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") }; Text("Mod Configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        
        val gameId = (taskViewModel.pendingMod as? PendingMod)?.gameId
        if (gameId != null) {
            val cleanId = gameId.substring(0, minOf(gameId.length, 6))
            val baseRiisyncDir = File("/storage/emulated/0", "RiiSync")
            val localCoverFile = File(baseRiisyncDir, "database/GameTDBcovers/${cleanId.take(4)}/cover.png")
            val isDbActive = taskViewModel.activeTasks.any { it.type == TaskType.SYSTEM && it.title.contains("Icon") }
            
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 1. Main Cover (Local-First with Sync Controls)
                    key(isDbActive, localCoverFile.exists()) {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 84.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(
                                    if (!localCoverFile.exists() && !isDbActive) {
                                        Modifier.clickable(enabled = taskViewModel.wifiConnected) { 
                                            taskViewModel.buildIconForPendingMod(settingsManager, taskViewModel.pendingMod!!) 
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = if (localCoverFile.exists()) localCoverFile else "https://art.gametdb.com/wii/cover/EN/$cleanId.png", 
                                contentDescription = "Game Cover", 
                                modifier = Modifier.fillMaxSize(), 
                                contentScale = ContentScale.Crop
                            )
                            
                            when {
                                isDbActive && !localCoverFile.exists() -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                !localCoverFile.exists() -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Download, 
                                                contentDescription = "Sync Assets", 
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { 
                        Text("Target Game:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            taskViewModel.getGameTitle(gameId), 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.ExtraBold
                        ) 
                    }
                    
                    // Channel Icon (With Sync Controls)
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val animatedIconFile = File(baseRiisyncDir, "database/${cleanId.take(4)}/icon_animated.png")
                        
                        when {
                            animatedIconFile.exists() -> {
                                AsyncImage(
                                    model = animatedIconFile, 
                                    contentDescription = "Wii Icon", 
                                    modifier = Modifier.fillMaxSize().padding(4.dp), 
                                    contentScale = ContentScale.Fit
                                )
                            }
                            isDbActive -> {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                            else -> {
                                // Show download button inside the configuration screen too
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = taskViewModel.wifiConnected) { 
                                            taskViewModel.buildIconForPendingMod(settingsManager, taskViewModel.pendingMod!!) 
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Download, 
                                        contentDescription = "Download", 
                                        tint = if (taskViewModel.wifiConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        val verifiedBg = if (isDarkTheme) Color(0xFF1B2E1B) else Color(0xFFE8F5E9)
        val verifiedContent = if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
        Card(colors = CardDefaults.cardColors(containerColor = verifiedBg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = verifiedContent)
                Spacer(Modifier.width(8.dp))
                Text(validationMessage, style = MaterialTheme.typography.bodySmall, color = verifiedContent)
            }
        }

        if (xmlFile != null) OutlinedButton(onClick = { showXmlEditor = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.EditNote, null); Text("Edit XML Patch") }

        OutlinedTextField(value = modName, onValueChange = onModNameChange, label = { Text(stringResource(R.string.mod_name)) }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) })
        
        Button(
            onClick = onLink, 
            modifier = Modifier.fillMaxWidth(), 
            enabled = modName.isNotBlank() && !isLinking,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2), disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLinking) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.connect_sync_with_dolphin)) }
        }
    }
}

@Composable
fun XmlEditorDialog(file: File, onDismiss: () -> Unit, onSave: () -> Unit) {
    var content by remember { mutableStateOf(try { file.readText() } catch (e: Exception) { "" }) }
    val rootRegex = "root=['\"]/?([^'\"]+)['\"]".toRegex()
    val hasRoot = content.contains(rootRegex)
    
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    
    // Auto-calculate line numbers
    val lineCount = content.lines().size

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("XML Patch Editor", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 500.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF1E1E1E), // Professional dark code background
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                ) {
                    Row(Modifier.fillMaxSize()) {
                        // Line Numbers Column
                        Column(
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF252525))
                                .verticalScroll(verticalScrollState)
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = i.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.Gray.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.height(18.dp) // Match line height
                                )
                            }
                        }

                        // Editor Column
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(verticalScrollState)
                                .horizontalScroll(horizontalScrollState)
                                .padding(12.dp)
                        ) {
                            BasicTextField(
                                value = content,
                                onValueChange = { content = it },
                                textStyle = TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = XmlSyntaxTransformation(),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.width(IntrinsicSize.Max)) {
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }
                
                if (!hasRoot) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Warning: Riivolution 'root' attribute is missing!", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { 
            Button(
                enabled = hasRoot,
                onClick = { 
                    try { 
                        file.writeText(content)
                        onSave() 
                    } catch (e: Exception) {} 
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save Changes") } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Discard") } 
        }
    )
}

class XmlSyntaxTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        "<[^>]+>".toRegex().findAll(text.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFF0055AA), fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1) }
        "\\b[a-zA-Z0-9_-]+=".toRegex().findAll(text.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFF660099)), it.range.first, it.range.last + 1) }
        "\"([^\"]*)\"".toRegex().findAll(text.text).forEach { builder.addStyle(SpanStyle(color = Color(0xFF1976D2)), it.range.first, it.range.last + 1) }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

