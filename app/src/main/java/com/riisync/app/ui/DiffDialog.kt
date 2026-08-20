/**
 * File Difference Dialog.
 * This file contains a dialog composable used to display code diffs between two versions of a file,
 * highlighting added lines in green and removed lines in red.
 */
package com.riisync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A dialog that displays a list of diff lines with appropriate syntax highlighting.
 * @param fileName The name of the file being viewed.
 * @param diffLines The list of strings representing the Git diff.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun DiffDialog(
    fileName: String,
    diffLines: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        title = { Text(fileName, style = MaterialTheme.typography.titleMedium) },
        text = {
            Surface(
                modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                LazyColumn(Modifier.padding(8.dp)) {
                    items(diffLines) { line ->
                        val bgColor = when {
                            line.startsWith("+") -> Color(0xFFE8F5E9)
                            line.startsWith("-") -> Color(0xFFFDECEA)
                            line.startsWith("@@") -> Color(0xFFE3F2FD)
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            line.startsWith("+") -> Color(0xFF1976D2)
                            line.startsWith("-") -> Color(0xFFC62828)
                            line.startsWith("@@") -> Color(0xFF1976D2)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            color = textColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

