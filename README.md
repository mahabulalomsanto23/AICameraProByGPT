# AI Camera Pro — Android Studio source project

Kotlin / CameraX Android app targeting Android 8.0+ (API 26), with OnePlus 8 / Snapdragon 865 / Android 13 as the primary device target.

## Implemented

- CameraX live preview and full-quality still capture.
- Four modes: Normal, Vivid, AI, Portrait; AI is selected by default.
- Colorful dark premium-style camera UI with gradient panels and distinct mode colors.
- Front/rear camera switching and rear flash control.
- Gallery import, original/enhanced preview toggle, and JPEG save to `Pictures/AI Camera Pro`.
- DataStore-backed saving preference: Original + Enhanced (default) or Enhanced Only.
- Vivid and AI image-wide computational color enhancement.
- **AI mode:** bundled on-device ML Kit Image Labeling model classifies broad image content (people, outdoor/nature, food, general) and selects conservative scene-tuning parameters.
- **Portrait mode:** bundled on-device ML Kit Selfie Segmentation model creates a foreground confidence mask; background blur is composited behind the detected person. If inference fails, the app falls back to safe global enhancement.
- No network permission is declared. Model inference and photo processing run locally; no cloud, account, or photo upload is used.

## Offline models

The app bundles:
- `com.google.mlkit:image-labeling:17.0.9` for on-device image labels.
- `com.google.mlkit:segmentation-selfie:16.0.0-beta6` for human foreground segmentation.

These bundled artifacts include their model assets in the app at build time. Image labeling is a broad 400+ label classifier, not a bespoke photographic scene model. Selfie segmentation detects people; it is not a general-purpose object/depth segmentation model. The scene-specific tuning is deliberately conservative and is not generative image restoration.

## Build verification

GitHub Actions uses JDK 17 and Gradle 8.7 to verify `assembleDebug` and `assembleRelease`. The workflow does not upload or publish APK artifacts. Build verification is separate from physical-device testing.

## Remaining work versus the full product specification

This is an actively developed source project, not yet the complete professional camera system in the supplied specification. Remaining items include:

- More detailed scene and multi-subject understanding; semantic sky, vegetation, skin, hair and object segmentation.
- Exposure/white-balance estimation, selective LAB/HSL color, local tone mapping and region-aware enhancement.
- Dedicated denoising, edge-aware sharpening, restoration and upscaling models.
- Portrait hair-edge refinement and selectable Natural/Medium/Strong/Maximum blur presets.
- Manual exposure/ISO/shutter/focus/white-balance controls, resolution and aspect-ratio selection.
- Draggable before/after wipe comparison.
- Original + Enhanced / Enhanced Only save preference, EXIF preservation and DataStore-backed settings.
- MVVM/StateFlow architecture, bounded processing queue, thermal awareness, automated quality tests.
- Physical OnePlus 8 testing and measured image-quality/performance results.

No capability is represented as complete unless it is actually implemented. Physical-device testing must be recorded separately from CI compilation.

## Development environment

- Android Gradle Plugin 8.5.2 / Kotlin 1.9.24
- Gradle 8.7 / JDK 17
- compileSdk 35 / minSdk 26 / targetSdk 35
