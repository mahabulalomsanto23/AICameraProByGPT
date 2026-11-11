# ✦ AI Camera Pro — Professional AI Photography Engine

A fresh Android Studio project built from scratch for vivid, colorful mobile photography.

## Visual direction
Midnight surfaces with electric violet, cyan and hot-pink accents. Image processing prioritizes rich, lively color while restraining highlight clipping and excessive warmth.

## Current features
- CameraX live preview and full-resolution capture.
- AI Auto, Vivid+, Portrait (currently uses the same vivid color engine), and Original modes.
- Offline vibrance, adaptive saturation, contrast, gentle shadow lift and highlight roll-off.
- Adjustable enhancement intensity.
- Import a photo using Android's photo picker.
- Before/after toggle.
- Save enhanced JPEGs to Pictures/AI Camera Pro.
- No network permission: photos stay on-device.

## Build
Open the root folder in Android Studio (JDK 17, Gradle 8.7, Android Gradle Plugin 8.5.2). GitHub Actions also builds debug and release APK variants. The release APK is unsigned unless a private signing key is configured.

## Honest implementation scope
This is the first clean foundation, not a claim that every advanced computational-photography feature is complete. Portrait depth segmentation, trained scene recognition, neural denoising/upscaling, lens switching, manual exposure controls and a draggable comparison slider are not yet implemented. AI Auto adapts color enhancement from image-wide luminance and saturation statistics. Portrait currently applies the vivid processor only.

Target baseline: Android 8.0+ (API 26); device focus: OnePlus 8 / Android 13.
