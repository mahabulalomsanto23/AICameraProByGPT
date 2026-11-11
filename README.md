# AI Camera Pro — Android Studio source project

Kotlin / CameraX Android app targeting Android 8.0+ (API 26), with OnePlus 8 / Snapdragon 865 / Android 13 as the primary device target.

## Implemented

- CameraX live preview and full-quality still capture.
- Four modes: Normal, Vivid, AI, Portrait; AI is selected by default.
- Colorful dark premium-style camera UI with gradient panels and distinct mode colors.
- Front/rear camera switching and rear flash control.
- Gallery import, draggable before/after wipe comparison, original/enhanced toggle, and JPEG save to `Pictures/AI Camera Pro`.
- Tap-to-focus metering, exposure compensation and digital zoom controls.
- Basic EXIF capture metadata preservation (date/time, camera make/model and selected exposure tags; GPS is intentionally not copied).
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

GitHub Actions uses JDK 17 and Gradle 8.7 to verify `assembleDebug` and `assembleRelease`. The workflow uploads the installable debug APK as a 14-day GitHub Actions artifact; it does not publish a public release. Build verification is separate from physical-device testing.

## Remaining work versus the full product specification

This is an actively developed source project, not yet the complete professional camera system in the supplied specification. Remaining items include:

- More detailed scene and multi-subject understanding; semantic sky, vegetation, skin, hair and object segmentation.
- Advanced exposure/white-balance estimation, selective LAB/HSL color, multi-region local tone mapping and semantic region-aware enhancement. A lightweight local-detail contrast pass is implemented.
- Dedicated denoising, edge-aware sharpening, restoration and upscaling models.
- Portrait hair-edge refinement and more advanced edge-aware mask refinement. Four selectable blur-strength presets are implemented.
- Manual ISO/shutter/focus/white-balance controls, resolution and aspect-ratio selection. Tap-to-focus, exposure compensation and zoom are implemented; manual sensor controls depend on device Camera2 support.

- Full EXIF/XMP/GPS preservation and configurable metadata privacy controls.
- MVVM/StateFlow architecture, bounded processing queue, thermal awareness, automated quality tests.
- Physical OnePlus 8 testing and measured image-quality/performance results.

No capability is represented as complete unless it is actually implemented. Physical-device testing must be recorded separately from CI compilation.

## Development environment

- Android Gradle Plugin 8.5.2 / Kotlin 1.9.24
- Gradle 8.7 / JDK 17
- compileSdk 35 / minSdk 26 / targetSdk 35
