package com.riisync.app.grabber

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for Wii graphics formats (TPL/WIBN textures).
 * Specifically handles the RGB5A3 and CI8 formats used in save icons.
 */
class WiiIconDecoder {

    /**
     * Decodes a 48x48 RGB5A3 texture.
     */
    fun decodeRGB5A3(data: ByteArray): Bitmap {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // RGB5A3 is stored in 4x4 blocks
        for (ty in 0 until 48 step 4) {
            for (tx in 0 until 48 step 4) {
                for (y in 0 until 4) {
                    for (x in 0 until 4) {
                        if (buffer.remaining() < 2) continue
                        val pixel = buffer.short.toInt() and 0xFFFF
                        val color = decodeRGB5A3Pixel(pixel)
                        bitmap.setPixel(tx + x, ty + y, color)
                    }
                }
            }
        }
        return bitmap
    }

    private fun decodeRGB5A3Pixel(pixel: Int): Int {
        return if ((pixel and 0x8000) != 0) {
            // RGB555 (opaque)
            val r = ((pixel shr 10) and 0x1F) shl 3
            val g = ((pixel shr 5) and 0x1F) shl 3
            val b = (pixel and 0x1F) shl 3
            Color.argb(255, r or (r shr 5), g or (g shr 5), b or (b shr 5))
        } else {
            // ARGB3444 (translucent)
            val a = ((pixel shr 12) and 0x07) shl 5
            val r = ((pixel shr 8) and 0x0F) shl 4
            val g = ((pixel shr 4) and 0x0F) shl 4
            val b = (pixel and 0x0F) shl 4
            Color.argb(a or (a shr 3), r or (r shr 4), g or (g shr 4), b or (b shr 4))
        }
    }

    /**
     * Decodes a CI8 (8-bit color indexed) texture.
     */
    fun decodeCI8(data: ByteArray, palette: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.wrap(data)

        // CI8 is stored in 8x4 blocks
        for (ty in 0 until 48 step 4) {
            for (tx in 0 until 48 step 8) {
                for (y in 0 until 4) {
                    for (x in 0 until 8) {
                        if (buffer.remaining() < 1) continue
                        val index = buffer.get().toInt() and 0xFF
                        bitmap.setPixel(tx + x, ty + y, palette[index % palette.size])
                    }
                }
            }
        }
        return bitmap
    }
}
