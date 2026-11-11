package com.aicamerapro.processing

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

enum class EnhanceMode { NORMAL, VIVID, AI, PORTRAIT }
data class EnhanceSettings(val mode: EnhanceMode = EnhanceMode.AI, val intensity: Float = 0.78f, val warmth: Float = 0.08f, val contrast: Float = 1.12f)

/** Deterministic offline color processing foundation; this is not a trained AI model. */
object VividProcessor {
    fun enhance(source: Bitmap, settings: EnhanceSettings): Bitmap {
        if (settings.mode == EnhanceMode.NORMAL) return source.copy(Bitmap.Config.ARGB_8888, false)
        val w = source.width
        val h = source.height
        val input = IntArray(w * h)
        source.getPixels(input, 0, w, 0, 0, w, h)
        val output = IntArray(input.size)
        val luminance = FloatArray(input.size)
        for (i in input.indices) {
            val c = input[i]
            val r = (c shr 16 and 255) / 255f
            val g = (c shr 8 and 255) / 255f
            val b = (c and 255) / 255f
            luminance[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
        var lumSum = 0.0
        var satSum = 0.0
        val step = max(1, input.size / 12000)
        var samples = 0
        for (i in input.indices step step) {
            val c = input[i]
            val r = (c shr 16 and 255) / 255f
            val g = (c shr 8 and 255) / 255f
            val b = (c and 255) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            lumSum += 0.2126 * r + 0.7152 * g + 0.0722 * b
            satSum += if (mx == 0f) 0.0 else ((mx - mn) / mx).toDouble()
            samples++
        }
        val avgLum = (lumSum / max(1, samples)).toFloat()
        val avgSat = (satSum / max(1, samples)).toFloat()
        val autoBoost = if (settings.mode == EnhanceMode.AI) {
            (1.16f + 0.48f * (0.48f - avgSat).coerceIn(0f, 0.48f) +
                0.18f * (0.48f - avgLum).coerceIn(0f, 0.48f)).coerceIn(1.12f, 1.42f)
        } else 1f
        val vividBoost = if (settings.mode == EnhanceMode.VIVID) 1.18f else 1f
        val vibrance = (1f + settings.intensity * 0.72f) * autoBoost * vividBoost
        val contrast = settings.contrast + if (settings.mode == EnhanceMode.AI) 0.04f else 0f
        val warm = settings.warmth * settings.intensity
        for (i in input.indices) {
            val c = input[i]
            var r = (c shr 16 and 255) / 255f
            var g = (c shr 8 and 255) / 255f
            var b = (c and 255) / 255f
            val y = luminance[i]
            val x = i % w
            val py = i / w
            val left = luminance[py * w + max(0, x - 1)]
            val right = luminance[py * w + min(w - 1, x + 1)]
            val up = luminance[max(0, py - 1) * w + x]
            val down = luminance[min(h - 1, py + 1) * w + x]
            val localAverage = (left + right + up + down) * 0.25f
            val localDetail = (y - localAverage) * 0.16f * settings.intensity
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx <= 0f) 0f else (mx - mn) / mx
            val factor = 1f + (1f - sat) * (vibrance - 1f)
            val mid = 0.46f
            r = (mid + (r - mid) * contrast) * factor + warm * 0.075f
            g = (mid + (g - mid) * contrast) * factor
            b = (mid + (b - mid) * contrast) * factor - warm * 0.060f
            val shadow = (0.34f - y).coerceAtLeast(0f) * 0.045f * settings.intensity
            r += shadow + localDetail
            g += shadow + localDetail
            b += shadow + localDetail
            fun roll(v: Float): Float {
                val x = v.coerceAtLeast(0f)
                return if (x > 0.88f) 0.88f + (x - 0.88f) * 0.55f else x
            }
            r = roll(r).coerceIn(0f, 1f)
            g = roll(g).coerceIn(0f, 1f)
            b = roll(b).coerceIn(0f, 1f)
            output[i] = (c and -0x1000000) or
                ((r * 255f).toInt().coerceIn(0, 255) shl 16) or
                ((g * 255f).toInt().coerceIn(0, 255) shl 8) or
                (b * 255f).toInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }
}
