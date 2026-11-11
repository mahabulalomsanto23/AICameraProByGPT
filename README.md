# AI Camera Pro — Android source project

A Kotlin / CameraX Android Studio project targeting Android 8.0+ (API 26), with OnePlus 8 / Snapdragon 865 / Android 13 as the primary device target.

## Current implementation in this repository

- CameraX rear-camera preview and still capture.
- Four selectable modes: Normal, Vivid, AI, Portrait; AI is the initial selection.
- Normal returns the decoded source bitmap unchanged.
- Vivid applies a stronger vibrance curve than AI.
- AI currently uses image-wide average luminance and saturation heuristics. It is not a trained scene-understanding model.
- Portrait currently does not perform person segmentation or depth-aware blur; it falls back to the same global color processor.
- Adjustable enhancement strength.
- Import a photo through Android's system photo picker.
- Before/after toggle (not yet a draggable wipe slider).
- Save enhanced JPEG to Pictures/AI Camera Pro.
- No network permission is declared; photos are processed locally.

## Build verification

GitHub Actions runs Gradle 8.7 with JDK 17 and verifies both assembleDebug and assembleRelease. The workflow does not publish or attach APK artifacts, in keeping with the source-project-only delivery requirement.

## Requirements from the supplied product specification that remain incomplete

The current repository is an initial camera foundation, not yet the complete project requested in the product specification. Missing or incomplete items include:

- Scene and multi-subject understanding across the requested categories.
- Genuine on-device AI model integration, model assets, model licensing and CPU/GPU/NNAPI delegate selection.
- Adaptive exposure, white balance, LAB/HSL selective color, local tone mapping and subject-aware processing.
- Real denoising, edge-aware sharpening, detail restoration and AI upscaling.
- Face/skin/hair/sky/vegetation segmentation and region-aware enhancement.
- Portrait segmentation, hair-edge refinement and Natural/Medium/Strong/Maximum computational blur controls.
- Front/rear camera switch, flash control, camera capability/resolution selection, aspect ratio and manual camera controls.
- Interactive before/after wipe slider.
- Original + Enhanced / Enhanced Only saving preferences, metadata preservation and DataStore-backed settings.
- MVVM/StateFlow architecture, bounded processing queue, thermal awareness and quality validation test suite.
- Physical OnePlus 8 testing and measured performance/image-quality results.

No neural model or AI capability is claimed unless it is actually integrated and exercised by the application. Physical-device testing must be recorded separately from CI compilation.

## Development environment

- Android Gradle Plugin 8.5.2
- Kotlin Android plugin 1.9.24
- Gradle 8.7
- JDK 17
- compileSdk 35 / minSdk 26 / targetSdk 35
