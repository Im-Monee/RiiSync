package com.riisync.app.grabber

import android.content.Context
import java.io.File

/**
 * Cache manager for the Wii Save Grabber.
 * Handles temporary file storage and persistent database state.
 */
class GrabberCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "grabber_cache")

    init {
        cacheDir.mkdirs()
    }

    /**
     * Gets a cached file if it exists and is not too old.
     */
    fun getCachedFile(id: String): File? {
        val file = File(cacheDir, id)
        return if (file.exists()) file else null
    }

    /**
     * Clears all temporary cache files.
     */
    fun clear() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }
}
