package com.aicamerapro.processing

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Offline portrait pipeline backed by ML Kit's bundled selfie-segmentation model. */
object PortraitProcessor {
    private val executor = Executors.newSingleThreadExecutor()
    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    fun enhance(
        source: Bitmap,
        settings: EnhanceSettings,
        blurStrength: Float,
        onComplete: (Bitmap) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        executor.execute {
            try {
                val enhanced = VividProcessor.enhance(source, settings.copy(mode = EnhanceMode.AI))
                segmenter.process(InputImage.fromBitmap(source, 0))
                    .addOnSuccessListener(executor) { mask ->
                        try {
                            val width = enhanced.width
                            val height = enhanced.height
                            val maskWidth = mask.width
                            val maskHeight = mask.height
                            val maskBuffer = mask.buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
                            val confidence = FloatArray(maskWidth * maskHeight)
                            for (i in confidence.indices) confidence[i] = maskBuffer.getFloat().coerceIn(0f, 1f)

                            val divisor = (10f + blurStrength.coerceIn(0f, 1f) * 18f).toInt().coerceAtLeast(8)
                            val small = Bitmap.createScaledBitmap(enhanced, max(1, width / divisor), max(1, height / divisor), true)
                            val blurred = Bitmap.createScaledBitmap(small, width, height, true)
                            if (small !== blurred && small !== enhanced) small.recycle()

                            val foreground = IntArray(width * height)
                            val background = IntArray(width * height)
                            enhanced.getPixels(foreground, 0, width, 0, 0, width, height)
                            blurred.getPixels(background, 0, width, 0, 0, width, height)
                            val output = IntArray(foreground.size)
                            for (y in 0 until height) {
                                val my = min(maskHeight - 1, (y.toLong() * maskHeight / height).toInt())
                                for (x in 0 until width) {
                                    val mx = min(maskWidth - 1, (x.toLong() * maskWidth / width).toInt())
                                    val raw = confidence[my * maskWidth + mx]
                                    val alpha = ((raw - 0.18f) / 0.64f).coerceIn(0f, 1f)
                                    val index = y * width + x
                                    val fg = foreground[index]
                                    val bg = background[index]
                                    fun channel(shift: Int): Int {
                                        val f = (fg shr shift) and 255
                                        val b = (bg shr shift) and 255
                                        return (b + (f - b) * alpha).toInt().coerceIn(0, 255)
                                    }
                                    output[index] = (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                                }
                            }
                            blurred.recycle()
                            onComplete(Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888))
                        } catch (e: Exception) {
                            onFailure(e)
                        }
                    }
                    .addOnFailureListener(executor, onFailure)
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    fun close() {
        if (this::class.java.desiredAssertionStatus()) return
        executor.shutdown()
    }
}
