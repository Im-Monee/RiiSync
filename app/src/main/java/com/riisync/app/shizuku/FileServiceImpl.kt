/**
 * Shizuku Privileged File Service Implementation.
 * This file provides the implementation of the privileged file service that runs with
 * shell/ADB permissions via Shizuku, allowing operations in restricted directories.
 */
package com.riisync.app.shizuku

import android.system.ErrnoException
import android.system.Os
import com.riisync.app.shizuku.IFileProgressCallback
import java.io.File

/**
 * Real implementation of the service, executed by Shizuku with shell/ADB permissions (uid 2000).
 * This allows writing/creating symlinks in areas like Android/data normally blocked by scoped storage.
 */
class FileServiceImpl : IFileService.Stub() {

    /**
     * Creates a symbolic link from target to link path.
     */
    override fun createSymlink(targetPath: String, linkPath: String): String {
        return try {
            val linkFile = File(linkPath)
            linkFile.parentFile?.mkdirs()
            if (linkFile.exists() || isSymlinkInternal(linkFile)) {
                linkFile.delete()
            }
            Os.symlink(targetPath, linkPath)
            "" // success
        } catch (e: ErrnoException) {
            "Symlink failed (${e.errno}): ${e.message}"
        } catch (e: Exception) {
            "Symlink error: ${e.message}"
        }
    }

    /**
     * Removes a file or directory recursively.
     */
    override fun remove(path: String): String {
        return try {
            val f = File(path)
            if (f.isDirectory && !isSymlinkInternal(f)) {
                f.deleteRecursively()
            } else {
                f.delete()
            }
            ""
        } catch (e: Exception) {
            "Removal failed: ${e.message}"
        }
    }

    /**
     * Copies a directory or file recursively with progress callbacks.
     */
    override fun copyRecursive(targetPath: String, destPath: String, callback: IFileProgressCallback?): String {
        return try {
            val src = File(targetPath)
            val dst = File(destPath)
            
            val totalFiles = countFiles(src)
            var copiedFiles = 0
            
            copyRecursiveInternal(src, dst) { currentFile ->
                copiedFiles++
                callback?.onProgress(copiedFiles, totalFiles, currentFile)
            }
            ""
        } catch (e: Exception) {
            "Copy failed: ${e.message}"
        }
    }

    /**
     * Helper to count the total number of files in a directory tree.
     */
    private fun countFiles(file: File): Int {
        if (file.isFile) return 1
        return 1 + (file.listFiles()?.sumOf { countFiles(it) } ?: 0)
    }

    /**
     * Internal implementation of recursive copy.
     */
    private fun copyRecursiveInternal(src: File, dst: File, onFileCopied: (String) -> Unit) {
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            onFileCopied(src.name)
            src.listFiles()?.forEach { 
                copyRecursiveInternal(it, File(dst, it.name), onFileCopied)
            }
        } else {
            src.copyTo(dst, overwrite = true)
            onFileCopied(src.name)
        }
    }

    /**
     * Checks if the specified path is a symbolic link.
     */
    override fun isSymlink(path: String): Boolean = isSymlinkInternal(File(path))

    /**
     * Lists the files in a directory.
     */
    override fun list(path: String): Array<String> {
        return File(path).list() ?: emptyArray()
    }

    /**
     * Checks if the specified path exists.
     */
    override fun exists(path: String): Boolean = File(path).exists() || isSymlinkInternal(File(path))

    /**
     * Synchronizes two directories incrementally based on modification time and size.
     */
    override fun syncIncremental(targetPath: String, destPath: String, callback: IFileProgressCallback?): String {
        return try {
            val src = File(targetPath)
            val dst = File(destPath)
            
            val totalFiles = countFiles(src)
            var processedFiles = 0
            var updatedFiles = 0
            
            syncRecursiveInternal(src, dst) { currentFile, wasUpdated ->
                processedFiles++
                if (wasUpdated) updatedFiles++
                callback?.onProgress(processedFiles, totalFiles, currentFile)
            }
            if (updatedFiles == 0) "UP_TO_DATE" else "Updated $updatedFiles / $totalFiles files"
        } catch (e: Exception) {
            "Sync failed: ${e.message}"
        }
    }

    /**
     * Internal implementation of incremental synchronization.
     */
    private fun syncRecursiveInternal(src: File, dst: File, onProgress: (String, Boolean) -> Unit) {
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            onProgress(src.name, false)
            src.listFiles()?.forEach { 
                syncRecursiveInternal(it, File(dst, it.name), onProgress)
            }
        } else {
            val needsUpdate = !dst.exists() || src.lastModified() > dst.lastModified() || src.length() != dst.length()
            if (needsUpdate) {
                src.copyTo(dst, overwrite = true)
            }
            onProgress(src.name, needsUpdate)
        }
    }

    /**
     * Deletes the content of a directory without removing the directory itself.
     */
    override fun deleteDirectoryContent(path: String): String {
        return try {
            val f = File(path)
            if (f.isDirectory) {
                f.listFiles()?.forEach { it.deleteRecursively() }
            }
            ""
        } catch (e: Exception) {
            "Cleanup failed: ${e.message}"
        }
    }

    /**
     * Terminates the service.
     */
    override fun destroy() {
        System.exit(0)
    }

    /**
     * Detects if a file is a symbolic link using canonical and absolute paths.
     */
    private fun isSymlinkInternal(file: File): Boolean {
        return try {
            val canon = if (file.parent == null) file
            else File(file.parentFile!!.canonicalFile, file.name)
            canon.canonicalFile != canon.absoluteFile
        } catch (e: Exception) {
            false
        }
    }
}
