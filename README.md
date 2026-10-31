# FrameDimmer

Global, best-effort screen-brightness protection for Android.

## Important platform limitation
A normal Android application cannot inspect and alter a frame before SurfaceFlinger presents that same frame. MediaProjection captures frames asynchronously after they enter the capture pipeline. Therefore strict "dim before the bright frame is ever visible" behavior cannot be guaranteed by a third-party app. This project targets the lowest practical latency, not zero-frame exposure.

## Design
- Global capture through Android MediaProjection (user must grant screen-capture consent).
- Analyze downscaled frames at a bounded sampling rate; use luminance statistics and hysteresis.
- Lower system brightness directly (no overlay window).
- Restore the exact brightness mode/value captured before intervention.
- Foreground service with persistent notification while monitoring.
- Screen-off / projection-stop / service-stop paths restore brightness where possible.

## Requirements
- Android 10+ recommended.
- MediaProjection consent from the user.
- Modify System Settings permission for direct brightness control, or an explicitly configured Shizuku/shell bridge on supported devices.
- Some apps and protected surfaces may appear black in captures; DRM/secure content cannot be analyzed.

## Status
Initial repository scaffold. Frame capture, analysis, and device-specific Shizuku integration remain to be implemented and tested on the target device.