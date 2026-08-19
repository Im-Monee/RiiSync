/**
 * Manual Symbolic Link Screen.
 * This file provides a utility interface for manually creating symbolic links
 * between any two paths on the filesystem using Shizuku's privileged access.
 */
package com.riisync.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.riisync.app.shizuku.ShizukuHelper
import com.riisync.app.utils.PathUtils
import kotlinx.coroutines.launch

/**
 * Screen providing manual controls for Shizuku-based symbolic link creation.
 */
@Composable
fun SymlinkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetPath by remember { mutableStateOf("/storage/emulated/0/Github/riivolution/SMSDWii") }
    var linkPath by remember { mutableStateOf("/storage/emulated/0/Android/data/org.dolphinemu.dolphinemu/files/Load/Riivolution/SMSDWii") }
    var result by remember { mutableStateOf("") }

    val targetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            if (path != null) targetPath = path
        }
    }

    val linkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            if (path != null) linkPath = path
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Stato Shizuku", style = MaterialTheme.typography.titleMedium)

        val available by ShizukuHelper.isAvailable
        val granted by ShizukuHelper.hasPermission

        Text(if (available) "Shizuku rilevato" else "Shizuku NON attivo — installalo e avvialo prima")
        Text(if (granted) "Permesso concesso" else "Permesso non ancora concesso")

        if (available && !granted) {
            Button(onClick = { ShizukuHelper.requestPermission() }) {
                Text("Richiedi permesso Shizuku")
            }
        }

        HorizontalDivider()

        Text("Crea symlink manuale", style = MaterialTheme.typography.titleMedium)
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Sorgente (reale)") }, modifier = Modifier.weight(1f))
            Button(onClick = { targetLauncher.launch(null) }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)) {
                Text("Sfoglia")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(linkPath, { linkPath = it }, label = { Text("Link da creare") }, modifier = Modifier.weight(1f))
            Button(onClick = { linkLauncher.launch(null) }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)) {
                Text("Sfoglia")
            }
        }

        Button(onClick = {
            scope.launch {
                val service = ShizukuHelper.fileService
                result = if (service == null) {
                    "Servizio non collegato. Assicurati che Shizuku sia attivo e il permesso concesso."
                } else {
                    val err = service.createSymlink(targetPath, linkPath)
                    if (err.isEmpty()) "Symlink creato con successo." else err
                }
            }
        }) { Text("Crea symlink") }

        Text(result)
    }
}
