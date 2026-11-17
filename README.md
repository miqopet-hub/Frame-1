# FRAME

A native Android app (Kotlin, Jetpack Compose, Material 3) that adds borders **around** a photograph.
The photo is never cropped, stretched, re-proportioned or overwritten: FRAME enlarges the canvas and
places the original, untouched, inside it. Fully offline (the manifest has no INTERNET permission).

Min SDK 26 · target/compile SDK 35 · designed for the Unihertz Titan 2 Elite's compact square display,
and adaptive to any screen size/density.

## Build

### Option A: Android Studio (recommended)
1. Install Android Studio (any recent release; it bundles JDK 17+).
2. **File ▸ Open…** and choose this `FRAME` folder. Let Gradle sync (needs internet once, to fetch dependencies).
3. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
   The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
4. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to the phone and open it
   (allow "install unknown apps" for your file manager).

### Option B: command line
Requires JDK 17+ and the Android SDK (platform 35, build-tools). Set `ANDROID_HOME`, then:
```
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
```

### Option C: no local setup
Push this folder to a GitHub repo. `.github/workflows/build-apk.yml` runs the tests and builds the APK on
GitHub's runners; download the **FRAME-debug-apk** artifact from the Actions tab.

## Using it
Open a photo, then use the tabs along the bottom (tap the active tab again to collapse the panel and see
more of the photo):

- **Style**: Even · Weighted · Asymmetric · Square · Double. The preview updates instantly.
- **Width**: one proportional slider (with a **Link sides** switch). Unlink to get four independent
  sliders (this is the Asymmetric style). Weighted adds a bottom-weight slider; Double adds line
  thickness, gap, position and colour. Widths are a % of the photo's shorter side, with the pixel
  value shown. The slider response is non-linear, fine near zero, up to 100% of the shorter side.
- **Color**: Eyedropper (tap/drag on the photo), HEX entry (validated live), and 10 swatches extracted
  from *this* photo (recomputed on every import).
- **Save**: JPEG (quality 95) or PNG → `Pictures/FRAME/FRAME_yyyyMMdd_HHmmss.jpg`.
  Export is also in the top bar.

## Architecture

```
app/src/main/java/app/frame/
  domain/    Pure Kotlin, no Android imports, unit-tested on the JVM
    BorderModels.kt   styles, settings, limits
    SettingsOps.kt    style switching / linking / proportional-scaling rules
    FrameLayout.kt    LayoutCalculator: THE single source of geometry + colour helpers
  imaging/   Android bitmap work, no UI
    ImageLoader.kt    bounds → EXIF → sampled preview (full-res is never held while editing)
    Orientation.kt    EXIF rotate/flip matrices (90° steps only → lossless)
    FrameRenderer.kt  draws a FrameLayout into a Bitmap (used by export)
    FrameExporter.kt  full-res decode, render, save via MediaStore (API 29+) / file (API 26-28)
    PaletteExtractor.kt  deterministic median-cut, pure Kotlin
  ui/        Compose
    FrameViewModel.kt state + actions;  FrameScreen.kt layout & panels
    components/FramePreview.kt  draws the same FrameLayout, scaled by one factor
```

**Why the preview matches the export:** both call `LayoutCalculator.compute(photoW, photoH, settings)`.
It returns the canvas size, the photo rectangle and the line rectangles in *original-photo pixels*.
The preview multiplies every number by one uniform factor; the exporter uses them as-is. There is no
second layout code path to drift out of sync. The photo is only ever *placed* in its rectangle.

**Eyedropper accuracy:** a tap is converted canvas px → original-photo px → normalised (u,v) inside the
photo rectangle, then looked up in the (EXIF-upright) preview bitmap. The UI reports the equivalent
pixel coordinates in the original.

## Verified here vs. not verified

The build environment used to write this had no Android SDK and no network, so **the APK was not built
and the app was not run on a device or emulator.** What *was* verified:

- ✅ `domain/` and `PaletteExtractor` compiled with the Kotlin 2.0.21 compiler and **23 JVM unit tests pass**:
  photo rectangle is never resized in any style / aspect ratio; canvas = photo + borders exactly;
  Even/Weighted/Asymmetric/Square/Double geometry; Square is square and centred; double lines never touch
  the photo; proportional scaling preserves ratios; link/unlink rules; scaling-down keeps aspect ratio;
  HEX parsing; palette returns 10 deterministic, photo-specific, opaque colours (incl. flat images).
- ⚠️ **Not compiled or run:** everything that touches Android or Compose (loader, renderer, exporter,
  ViewModel, all UI). It was written carefully against the public APIs but a first build may surface a
  compile error or two. Please run Option A or C and send me any error output; I'll fix it.
- ⚠️ **Not exercised on a device:** EXIF orientations 2–8 (mapping reasoned out, not seen on real files),
  MediaStore save, the photo picker, eyedropper touch handling, layout on the Titan's 1:1 display.

## Known limitations / design decisions

- **Very large canvases:** the exported canvas is capped at 100 MP. Above that (or if the device runs out
  of memory) the *whole composition* is scaled down uniformly, which means the photo is resampled. This is
  the only case where pixels are not 1:1, and the app says so in the Save tab and after export.
  Below the cap the photo is copied pixel-for-pixel (scale exactly 1.0, no filtering).
- Full-resolution pixels are re-read from the original file at export time, so the image must still be
  accessible then. A picked photo isn't remembered if Android kills the app's process.
- Eyedropper samples the preview bitmap (≤2048 px on its long side), not the full-resolution file.
  Sampling is the exact pixel at the mapped position of that downscaled copy.
- Unlinking sides from Even/Weighted/Square/Double switches to **Asymmetric**, seeded with the current
  widths so the preview doesn't change (independent sides are what Asymmetric means).
- Flat/single-tone photos have fewer than 10 distinct colours; the remaining swatches are lighter/darker
  variants of that photo's own colours (never random or fixed).
- JPEG has no alpha and the canvas is always opaque. PNG export is lossless and files are large.
- Wide-gamut photos keep their colour space in the output bitmap; embedded ICC profiles of the original
  JPEG aren't copied verbatim.
- The release build type is signed with the debug key so it's installable; use your own keystore to publish.
- Dependency versions (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01) are pinned and known-compatible;
  newer ones are fine to bump.
