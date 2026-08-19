/**
 * Filesystem Path Utilities.
 * This file contains utility methods for interacting with the Android Storage Access Framework (SAF)
 * and converting content Uris into absolute filesystem paths.
 */
package com.riisync.app.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Utility object for path conversions.
 */
object PathUtils {
    /**
     * Tenta di convertire un Uri SAF in un percorso assoluto.
     * Funziona principalmente per lo storage primario (/storage/emulated/0).
     */
    fun getAbsolutePath(context: Context, uri: Uri): String? {
        if (DocumentsContract.isTreeUri(uri)) {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val split = documentId.split(":")
            val type = split[0]
            val path = split[1]

            return if ("primary".equals(type, ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory()}/$path"
            } else {
                "/storage/$type/$path"
            }
        }
        // Fallback minimo se non è un tree URI (es. selezione file singolo)
        return null
    }
}
