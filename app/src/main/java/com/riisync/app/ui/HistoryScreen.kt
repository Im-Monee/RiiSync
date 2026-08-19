/**
 * Commit History Screen.
 * This file displays a scrollable list of local commits for a given repository,
 * providing the message, author, and date for each entry.
 */
package com.riisync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.riisync.app.git.GitManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen displaying the Git commit log for a local repository path.
 */
@Composable
fun HistoryScreen(localPath: String) {
    val gitManager = remember { GitManager() }
    var commits by remember { mutableStateOf<List<GitManager.CommitInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(localPath) {
        if (localPath.isNotBlank()) {
            isLoading = true
            commits = gitManager.getCommitHistory(File(localPath))
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Cronologia Commit", style = MaterialTheme.typography.headlineSmall)
        Text(localPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
        } else if (commits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nessun commit trovato o cartella non valida.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(commits) { commit ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    commit.hash,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    dateFormat.format(commit.date),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(commit.message, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Autore: ${commit.author}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
