package com.riisync.app.grabber

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Wii Save files ('data.bin').
 * Extracts the Game ID and the banner structure.
 */
class WiiSaveParser {

    data class SaveMetadata(
        val gameId: String,
        val bannerOffset: Int,
        val bannerSize: Int
    )

    /**
     * Parses a data.bin file to extract metadata.
     */
    fun parse(file: File): SaveMetadata? {
        val bytes = file.readBytes()
        if (bytes.size < 0x100) return null

        // Scan for 'Bk' header (standard backup)
        // Offset 0x00: 0x00010000
        // Offset 0x08: 'Bk'
        val isBackup = bytes[8].toInt().toChar() == 'B' && bytes[9].toInt().toChar() == 'k'
        
        val gameId = if (isBackup) {
            // Title ID is at 0x04 or 0x00 in some formats. 
            // For standard Bk, Title ID is at 0x00 (low 4 bytes)
            String(bytes.sliceArray(0 until 4))
        } else {
            // Search for game ID pattern or magic
            // Many data.bin have the ID at 0x00
            String(bytes.sliceArray(0 until 4))
        }

        // Scan for 'WIBN' magic
        val wibnOffset = findMagic(bytes, "WIBN") ?: return null

        return SaveMetadata(
            gameId = gameId.filter { it.isLetterOrDigit() },
            bannerOffset = wibnOffset,
            bannerSize = 0x60 + 0x6000 // Approximate, will parse properly in BannerParser
        )
    }

    private fun findMagic(bytes: ByteArray, magic: String): Int? {
        val magicBytes = magic.toByteArray()
        for (i in 0 until bytes.size - magicBytes.size) {
            var found = true
            for (j in magicBytes.indices) {
                if (bytes[i + j] != magicBytes[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return null
    }
}
