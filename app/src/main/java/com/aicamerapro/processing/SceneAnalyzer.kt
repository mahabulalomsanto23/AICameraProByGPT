package com.aicamerapro.processing

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import java.util.concurrent.Executors

/** Bundled on-device image-labeling model used for lightweight scene-aware tuning. */
object SceneAnalyzer {
    enum class Profile { PEOPLE, OUTDOOR, FOOD, GENERAL }

    private val executor = Executors.newSingleThreadExecutor()
    private val labelerLazy = lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val labeler: ImageLabeler by labelerLazy

    fun analyze(bitmap: Bitmap, onResult: (Profile) -> Unit, onFailure: () -> Unit) {
        executor.execute {
            try {
                val maxSide = 512
                val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
                val sample = if (scale < 1f) Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true
                ) else bitmap
                labeler.process(InputImage.fromBitmap(sample, 0))
                    .addOnSuccessListener(executor) { labels ->
                        if (sample !== bitmap) sample.recycle()
                        val names = labels.filter { it.confidence >= 0.45f }
                            .map { it.text.lowercase(Locale.US) }
                        val profile = when {
                            names.any { it in setOf("person", "people", "face", "portrait", "human") } -> Profile.PEOPLE
                            names.any { it in setOf("sky", "landscape", "nature", "plant", "tree", "mountain", "water", "grass", "flower") } -> Profile.OUTDOOR
                            names.any { it in setOf("food", "dish", "cuisine", "fruit", "meal") } -> Profile.FOOD
                            else -> Profile.GENERAL
                        }
                        onResult(profile)
                    }
                    .addOnFailureListener(executor) {
                        if (sample !== bitmap) sample.recycle()
                        onFailure()
                    }
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    fun tune(settings: EnhanceSettings, profile: Profile): EnhanceSettings = when (profile) {
        Profile.PEOPLE -> settings.copy(intensity = settings.intensity * 0.82f, warmth = settings.warmth * 0.55f)
        Profile.OUTDOOR -> settings.copy(intensity = (settings.intensity * 1.08f).coerceAtMost(1f), contrast = settings.contrast + 0.025f)
        Profile.FOOD -> settings.copy(intensity = (settings.intensity * 1.02f).coerceAtMost(1f), warmth = settings.warmth + 0.055f)
        Profile.GENERAL -> settings
    }

    fun close() {
        if (labelerLazy.isInitialized()) labeler.close()
        executor.shutdown()
    }
}
