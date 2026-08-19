/**
 * Dolphin Emulator Integration Screen.
 * This file provides a interface for linking Riivolution mods to various Dolphin emulator
 * packages installed on the device using Shizuku for privileged filesystem access.
 */
package com.riisync.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.riisync.app.shizuku.RiivolutionLinker
import com.riisync.app.utils.PathUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Screen for managing the connection between local mods and the Dolphin emulator.
 */
@Composable
fun DolphinScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootPath by remember { mutableStateOf("") }
    var riivolutionFolder by remember { mutableStateOf<File?>(null) }
    var modFolder by remember { mutableStateOf<File?>(null) }
    var validationMessage by remember { mutableStateOf("Seleziona una cartella per iniziare.") }
    var isValid by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = PathUtils.getAbsolutePath(context, it)
            if (path != null) {
                rootPath = path
                val result = RiivolutionLinker.validateFolderStrict(path)
                when (result) {
                    is RiivolutionLinker.ValidationResult.Success -> {
                        isValid = true
                        validationMessage = result.mod.message
                        riivolutionFolder = result.mod.riivolutionFolder
                        modFolder = result.mod.modFolder
                    }
                    is RiivolutionLinker.ValidationResult.Error -> {
                        isValid = false
                        validationMessage = result.message
                        riivolutionFolder = null
                        modFolder = null
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Collegamento mod Riivolution → Dolphin", style = MaterialTheme.typography.titleMedium)
        Text("Percorso Dolphin rilevato: ${RiivolutionLinker.dolphinRiivolutionPath()}",
            style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()

        Text("Seleziona la cartella radice della mod (es. la cartella del repo)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = rootPath,
                onValueChange = { 
                    rootPath = it
                    val res = RiivolutionLinker.validateFolderStrict(it)
                    if (res is RiivolutionLinker.ValidationResult.Success) {
                        isValid = true
                        validationMessage = res.mod.message
                        riivolutionFolder = res.mod.riivolutionFolder
                        modFolder = res.mod.modFolder
                    } else if (res is RiivolutionLinker.ValidationResult.Error) {
                        isValid = false
                        validationMessage = res.message
                        riivolutionFolder = null
                        modFolder = null
                    }
                },
                label = { Text("Percorso cartella") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { dirLauncher.launch(null) }, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text("Sfoglia")
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isValid) Color(0xFFE8F5E9) else Color(0xFFFDECEA)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isValid) "✅ $validationMessage" else "❌ $validationMessage",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isValid) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = isValid,
                onClick = {
                    scope.launch {
                        val riiv = riivolutionFolder ?: return@launch
                        val mod = modFolder ?: return@launch
                        
                        val resXml = RiivolutionLinker.linkXmlConfig(riiv.absolutePath, "riivolution")
                        val resMod = RiivolutionLinker.linkModFolder(mod.absolutePath, mod.name)
                        
                        result = if (resXml.isEmpty() && resMod.isEmpty()) {
                            "Mod collegata correttamente!"
                        } else {
                            "XML: ${resXml.ifEmpty { "OK" }}\nMod: ${resMod.ifEmpty { "OK" }}"
                        }
                    }
                }
            ) { Text("Collega Mod") }

            OutlinedButton(
                enabled = modFolder != null,
                onClick = {
                    scope.launch {
                        val mod = modFolder ?: return@launch
                        result = RiivolutionLinker.unlink(mod.name)
                            .ifEmpty { "Link rimosso." }
                    }
                }
            ) { Text("Rimuovi link") }
        }

        HorizontalDivider()
        Text("Risultato:", style = MaterialTheme.typography.labelLarge)
        Text(result)
    }
}
