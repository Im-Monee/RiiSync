/**
 * Riivolution Mod Linking Manager.
 * This file contains high-level logic for linking local Git repository folders to Dolphin's
 * Riivolution directories using either symlinks or incremental sync via Shizuku.
 */
package com.riisync.app.shizuku

import java.io.File

/**
 * Logic dedicated to linking GitHub repo mod folders (e.g. ".../Github/riivolution/SMSDWii") 
 * to the internal folder Dolphin expects.
 */
object RiivolutionLinker {

    private val DOLPHIN_PACKAGES = listOf(
        "org.dolphinemu.dolphinemu", // Official
        "org.dolphinemu.mmjr"        // MMJR2 (Medard22 VBI)
    )

    private var currentTargetPackage = "org.dolphinemu.dolphinemu"

    /**
     * Updates the target Dolphin package used for paths.
     */
    fun setTargetPackage(pkg: String) {
        currentTargetPackage = pkg
    }

    /**
     * Determines the target Riivolution path for a specific Dolphin package.
     */
    fun dolphinRiivolutionPath(pkg: String = currentTargetPackage): String {
        return if (pkg == "org.dolphinemu.mmjr") {
            "/storage/emulated/0/mmjr2-vbi/Load/Riivolution"
        } else {
            "/storage/emulated/0/Android/data/$pkg/files/Load/Riivolution"
        }
    }

    /**
     * Clears a specific Dolphin's cache directories.
     */
    suspend fun clearDolphinCache(pkg: String = currentTargetPackage): String {
        val service = ShizukuHelper.fileService ?: return "Shizuku not connected."
        val base = "/storage/emulated/0/Android/data/$pkg/cache"
        return try {
            service.remove(base)
            val parent = base.substringBeforeLast("/")
            service.list(parent).forEach { 
                if (it.contains("cache", ignoreCase = true)) service.remove("$parent/$it")
            }
            ""
        } catch (e: Exception) { e.message ?: "Clear failed" }
    }

    /**
     * Links a mod folder to a specific package.
     */
    suspend fun linkModFolder(
        sourceModDir: String, 
        modFolderName: String, 
        pkg: String = currentTargetPackage,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): String {
        val service = ShizukuHelper.fileService ?: return "Shizuku not connected."
        val linkPath = "${dolphinRiivolutionPath(pkg)}/$modFolderName"
        
        val err = service.createSymlink(sourceModDir, linkPath)
        if (err.isEmpty()) return ""
        
        val callback = object : IFileProgressCallback.Stub() {
            override fun onProgress(current: Int, total: Int, currentFile: String) {
                onProgress(current, total, currentFile)
            }
        }
        return service.syncIncremental(sourceModDir, linkPath, callback)
    }

    /**
     * Links a Riivolution XML configuration file.
     */
    suspend fun linkXmlConfig(
        sourceXmlDirOrFile: String, 
        targetName: String,
        pkg: String = currentTargetPackage,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): String {
        val service = ShizukuHelper.fileService ?: return "Shizuku not connected."
        val xmlDirPath = "${dolphinRiivolutionPath(pkg)}/riivolution"
        val linkPath = "$xmlDirPath/$targetName"

        val err = service.createSymlink(sourceXmlDirOrFile, linkPath)
        if (err.isEmpty()) return ""
        
        val callback = object : IFileProgressCallback.Stub() {
            override fun onProgress(current: Int, total: Int, currentFile: String) {
                onProgress(current, total, currentFile)
            }
        }
        return service.syncIncremental(sourceXmlDirOrFile, linkPath, callback)
    }

    /**
     * Removes a previously created mod link or directory.
     */
    suspend fun unlink(modFolderName: String): String {
        val service = ShizukuHelper.fileService ?: return "Shizuku not connected."
        val linkPath = "${dolphinRiivolutionPath()}/$modFolderName"
        return service.remove(linkPath)
    }

    /**
     * Advanced extraction of Game ID from Riivolution XML content.
     * Supports standard value/id attributes and multi-region game/type blocks.
     */
    fun extractGameId(content: String): String? {
        // 1. Check for standard <id value="XXXX"> or <game id="XXXX">
        val standardMatch = "(?:<id[^>]+value\\s*=\\s*|<game[^>]+id\\s*=\\s*)['\"]([^'\"]+)['\"]"
            .toRegex(RegexOption.IGNORE_CASE)
            .find(content)
        
        if (standardMatch != null) {
            return standardMatch.groupValues[1].trim().uppercase()
        }

        // 2. Check for multi-region format <id game="SMN"> <region type="P"/> ...
        val multiRegionMatch = "<id[^>]+game\\s*=\\s*['\"]([^'\"]+)['\"]".toRegex(RegexOption.IGNORE_CASE).find(content)
        if (multiRegionMatch != null) {
            val gamePart = multiRegionMatch.groupValues[1].trim().uppercase()
            val regionMatch = "<region[^>]+type\\s*=\\s*['\"]([^'\"]+)['\"]".toRegex(RegexOption.IGNORE_CASE).find(content)
            if (regionMatch != null) {
                val regionPart = regionMatch.groupValues[1].trim().uppercase()
                return gamePart + regionPart
            }
            return gamePart
        }
        return null
    }

    /**
     * Sealed class representing the result of a mod folder validation.
     */
    sealed class ValidationResult {
        data class Success(val mod: ValidatedMod) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    data class ValidatedMod(
        val riivolutionFolder: File,
        val modFolder: File,
        val xmlFile: File,
        val message: String,
        val gameId: String? = null
    )

    /**
     * Performs strict validation of a folder's structure.
     */
    fun validateFolderStrict(path: String): ValidationResult {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) {
            return ValidationResult.Error("Selected path is not a valid directory.")
        }

        // 1. Find the 'riivolution' folder (case-insensitive)
        val riiv = root.listFiles { f -> f.isDirectory && f.name.equals("riivolution", ignoreCase = true) }?.firstOrNull()
            ?: return ValidationResult.Error("Required 'riivolution' folder not found inside selected directory.")

        // 2. Find XML files inside 'riivolution'
        val xmlFiles = riiv.listFiles { file -> file.name.lowercase().endsWith(".xml") } ?: emptyArray()
        if (xmlFiles.isEmpty()) {
            return ValidationResult.Error("No .xml configuration files found inside the 'riivolution' folder.")
        }

        // 3. Parse the XML to find the 'root' attribute
        val xml = xmlFiles[0]
        val content = try { xml.readText() } catch (e: Exception) { 
            return ValidationResult.Error("Could not read XML file: ${e.message}")
        }
        
        if (content.isBlank()) {
            return ValidationResult.Error("The selected XML configuration file is empty.")
        }

        val rootMatch = "root\\s*=\\s*['\"]/?([^'\"]+?)['\"]".toRegex(RegexOption.IGNORE_CASE).find(content)
        val rawRootName = rootMatch?.groupValues?.get(1) 
            ?: return ValidationResult.Error("XML error: The 'root' attribute is missing from the patch configuration.")
        
        val rootName = rawRootName.trim().removeSuffix("/")

        // 4. Verify the data folder exists next to the 'riivolution' folder
        val modFolder = root.listFiles { f -> 
            f.isDirectory && f.name.trim().equals(rootName, ignoreCase = true) 
        }?.firstOrNull() ?: return ValidationResult.Error("Patch error: The data folder '$rootName' (defined in XML) was not found.")

        // Extraction of Game ID
        val gameId = extractGameId(content)

        return ValidationResult.Success(
            ValidatedMod(riiv, modFolder, xml, "Structure verified! Mod: ${modFolder.name}", gameId)
        )
    }
}
