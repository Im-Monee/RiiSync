package com.riisync.app.grabber

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for the 'WIBN' banner structure.
 * Extracts title and animated icon frames.
 */
class WiiBannerParser(private val decoder: WiiIconDecoder) {

    data class BannerInfo(
        val title: String,
        val frames: List<Bitmap>,
        val animSpeed: Int
    )

    fun parse(data: ByteArray): BannerInfo? {
        if (data.size < 0x60) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Magic check
        val magic = ByteArray(4)
        buffer.get(magic)
        if (String(magic) != "WIBN") return null

        // Extract title (offset 0x40, UTF-16BE, null-terminated)
        buffer.position(0x40)
        val titleBytes = ByteArray(64)
        buffer.get(titleBytes)
        val title = String(titleBytes, Charsets.UTF_16BE).substringBefore("\u0000")

        // Icon count and speed
        buffer.position(0x48)
        val iconCount = buffer.short.toInt() and 0xFFFF
        val animSpeed = buffer.short.toInt() and 0xFFFF

        // Icons start at 0x60 (if no banner) or 0x60 + banner size
        // Standard icons are 48x48. RGB5A3 48x48 = 4608 bytes.
        val frames = mutableListOf<Bitmap>()
        var iconOffset = 0x60 // Simple heuristic, most saves don't have banners in WIBN

        // Check if there's a banner (optional)
        // Usually WIBN headers are followed by many bytes. 
        // We'll jump to the first icon.
        
        for (i in 0 until iconCount) {
            val frameData = data.sliceArray(iconOffset until iconOffset + 4608)
            frames.add(decoder.decodeRGB5A3(frameData))
            iconOffset += 4608
            if (iconOffset + 4608 > data.size) break
        }

        return BannerInfo(title, frames, animSpeed)
    }
}
