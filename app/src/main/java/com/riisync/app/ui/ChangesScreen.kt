/**
 * Changes Management Screen.
 * This file provides a UI for viewing uncommitted local Git changes, stashing them,
 * or committing and pushing them to the remote repository.
 */
package com.riisync.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riisync.app.R
import com.riisync.app.git.GitManager
import com.riisync.app.git.TokenManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * Composable screen that displays local Git changes for a specific path.
 */
@Composable
fun ChangesScreen(localPath: String, taskViewModel: GlobalTaskViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gitManager = remember { GitManager() }
    val tokenManager = remember { TokenManager(context) }
    
    var changes by remember { mutableStateOf<List<GitManager.LocalChange>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var commitTitle by remember { mutableStateOf("") }
    var commitDescription by remember { mutableStateOf("") }
    
    var selectedDiffFile by remember { mutableStateOf<String?>(null) }
    var diffLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoStats by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val refreshChanges = {
        if (localPath.isNotBlank()) {
            scope.launch {
                isLoading = true
                changes = gitManager.getLocalChanges(File(localPath))
                repoStats = gitManager.getRepositoryStats(File(localPath))
                isLoading = false
            }
        }
    }

    LaunchedEffect(localPath) {
        refreshChanges()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val repoName = if (localPath.isNotBlank()) File(localPath).name else stringResource(R.string.no_repo_selected)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(repoName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(localPath.ifBlank { stringResource(R.string.select_repo_from_git) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (repoStats.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repoStats["disk_usage"]?.let { Text("Size: $it", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                        repoStats["last_sync"]?.let { Text("Last Sync: $it", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                    }
                }
            }
        }
        
        if (selectedPaths.isNotEmpty()) {
            Text(stringResource(R.string.items_selected, selectedPaths.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        }

        if (localPath.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedPaths.isEmpty()) {
                    Button(onClick = { refreshChanges() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.refresh))
                    }
                    Button(
                        enabled = changes.isNotEmpty() && !taskViewModel.isOperating,
                        onClick = {
                            taskViewModel.runSimpleTask(context.getString(R.string.stashing_changes)) {
                                val res = gitManager.stashCreate(File(localPath))
                                taskViewModel.notify(res.toLogString(context), res is GitManager.Result.Error)
                                refreshChanges()
                            }
                        }, 
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.stash))
                    }
                    OutlinedButton(
                        enabled = changes.isNotEmpty() && !taskViewModel.isOperating,
                        onClick = {
                            taskViewModel.runSimpleTask(context.getString(R.string.discarding_all_changes)) {
                                val res = gitManager.discardChanges(File(localPath), null)
                                taskViewModel.notify(context.getString(R.string.all_changes_discarded), res is GitManager.Result.Error)
                                refreshChanges()
                            }
                        }, 
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.discard_all))
                    }
                } else {
                    Button(onClick = {
                        taskViewModel.runSimpleTask(context.getString(R.string.discarding_selected)) {
                            selectedPaths.forEach { path -> gitManager.discardChanges(File(localPath), path) }
                            taskViewModel.notify(context.getString(R.string.selected_changes_discarded), false)
                            selectedPaths = emptySet()
                            refreshChanges()
                        }
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.discard_selected))
                    }
                    Button(onClick = { selectedPaths = emptySet() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (changes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_changes_detected), color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(changes) { change ->
                    val isSelected = selectedPaths.contains(change.path)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(change.path) {
                                detectTapGestures(
                                    onLongPress = { selectedPaths = if (isSelected) selectedPaths - change.path else selectedPaths + change.path },
                                    onTap = {
                                        if (selectedPaths.isNotEmpty()) {
                                            selectedPaths = if (isSelected) selectedPaths - change.path else selectedPaths + change.path
                                        } else {
                                            scope.launch {
                                                diffLines = gitManager.getFileDiff(File(localPath), change.path)
                                                selectedDiffFile = change.path
                                            }
                                        }
                                    }
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusIcon(change.status)
                            Spacer(Modifier.width(12.dp))
                            Text(change.path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (change.status != "Untracked" && selectedPaths.isEmpty()) {
                                IconButton(onClick = {
                                    taskViewModel.runSimpleTask(context.getString(R.string.discarding_file_format, change.path)) {
                                        gitManager.discardChanges(File(localPath), change.path)
                                        refreshChanges()
                                    }
                                }) { Icon(Icons.Default.Refresh, contentDescription = "Discard", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                            }
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
                    
                    OutlinedTextField(
                        value = commitTitle, onValueChange = { if (it.length <= 50) commitTitle = it },
                        label = { Text(stringResource(R.string.subject_max_50)) }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("${commitTitle.length}/50") }
                    )
                    OutlinedTextField(value = commitDescription, onValueChange = { commitDescription = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(12.dp))
                    Button(
                        enabled = commitTitle.isNotBlank() && !taskViewModel.isOperating,
                        onClick = {
                            taskViewModel.runSimpleTask(context.getString(R.string.publishing_changes)) {
                                val username = tokenManager.getUsername() ?: "RiiSync"
                                val token = tokenManager.getToken() ?: ""
                                val fullMessage = if (commitDescription.isBlank()) commitTitle else "$commitTitle\n\n$commitDescription"
                                val res = gitManager.commitAndPush(File(localPath), fullMessage, authorName = username, authorEmail = "$username@users.noreply.github.com", username = username, token = token)
                                taskViewModel.notify(if (res is GitManager.Result.Success) context.getString(R.string.commit_push_completed) else res.toLogString(context), res is GitManager.Result.Error)
                                if (res is GitManager.Result.Success) {
                                    commitTitle = ""; commitDescription = ""; refreshChanges()
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.commit_push)) }
                }
            }
        }
    }

    selectedDiffFile?.let { fileName -> DiffDialog(fileName = fileName, diffLines = diffLines, onDismiss = { selectedDiffFile = null }) }
}

/**
 * Visual indicator for Git change status (Added, Deleted, Modified, etc.).
 */
@Composable
fun StatusIcon(status: String) {
    when (status) {
        "Added", "Untracked" -> Box(Modifier.size(20.dp).background(Color(0xFF1976D2), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        "Deleted" -> Box(Modifier.size(20.dp).background(Color(0xFFC62828), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp, 2.dp).background(Color.White)) }
        "Modified", "Changed" -> Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { Box(Modifier.fillMaxSize().background(Color(0xFFFFB300), CircleShape)); Box(Modifier.size(6.dp).background(Color.White, CircleShape)) }
        else -> Box(Modifier.size(20.dp).background(Color.Gray, RoundedCornerShape(4.dp)))
    }
}

/**
 * Extension to convert [GitManager.Result] into a localized log message.
 */
private fun GitManager.Result.toLogString(context: android.content.Context): String = when (this) {
    is GitManager.Result.Success -> context.getString(R.string.ok_format, message)
    is GitManager.Result.Error -> context.getString(R.string.error_format, message)
}

