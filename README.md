# FrameDimmer

Android global high-luminance / flash protection prototype.

## What it does

1. Requests the user's MediaProjection consent for screen capture.
2. Mirrors the display into a quarter-resolution RGBA ImageReader.
3. Samples at most about 8 frames per second and checks the proportion of sampled pixels whose luma is at least 230/255.
4. When at least 6% of sampled pixels are very bright, switches system brightness to manual and writes level 1.
5. After five consecutive sampled frames fall below the trigger, restores the exact brightness value and mode saved before intervention.
6. Restores brightness on screen-off, projection stop, and normal service teardown.

Direct system-brightness control is used instead of a full-screen overlay to avoid an extra compositing layer.

## Critical Android limitation

This is best-effort, not a pre-display safety barrier. MediaProjection delivers frames asynchronously after they enter Android's capture pipeline. A third-party app cannot inspect and alter a frame before SurfaceFlinger presents that same frame, so a bright first frame may already have appeared before this app reacts. DRM and secure surfaces may be black or unavailable in the capture.

If Android kills the process abruptly, no app-level callback can guarantee restoration. Normal stop paths restore brightness, but this prototype is not a safety-certified screen-flash mitigation tool.

## Build

- Android Studio / Gradle
- JDK 17
- Android SDK 35
- Minimum Android 10 (API 29)

Open the project in Android Studio and build the app debug variant.

## First run

1. Grant Modify system settings.
2. Tap Enable global monitoring and approve Android's screen-capture prompt.
3. Keep the persistent foreground-service notification while monitoring.
4. Tap Stop monitoring and restore brightness to stop.

## Initial detector parameters

- Capture dimensions: one quarter of physical display width and height.
- Sampling interval: 120 ms (maximum ~8.3 sampled frames/second).
- Bright-pixel luma threshold: 230/255.
- Trigger ratio: 6% of sampled pixels.
- Restore hysteresis: 5 consecutive clear samples.
- Dim level: 1/255.

These are initial defaults, not calibrated thresholds. Different panels, OEM display pipelines, HDR content, and adaptive-brightness behavior require device testing.

## Known limitations / next work

- No Shizuku backend yet; direct WRITE_SETTINGS permission is used.
- No persistent recovery after abrupt process kill or reboot.
- The broad-area threshold may miss small flashes and may react to bright static interfaces.
- Add configurable thresholds and diagnostic telemetry after testing on the target device.
