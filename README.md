# HyperJump Live Wallpaper

Android live wallpaper with OpenGL ES 2.0 rendering engine.

## Versions

### V1 - Centrifuge (Current)
- Multi-segment snake trails (12 segments per star)
- Sinusoidal spiral warping
- Bloom effect with dual-pass rendering
- Configurable FPS, star count, and color themes

## Building

```bash
./gradlew assembleDebug
```

## Version Management

### To restore V1:
```bash
git checkout v1-centrifuge
```

### To reference a specific version:
Simply mention "V1" or "v1-centrifuge tag" in conversation.

## Technical Stack
- Kotlin
- OpenGL ES 2.0
- Android Canvas API (legacy versions)
