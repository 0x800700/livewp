# HyperJump Live Wallpaper

Android live wallpaper with OpenGL ES 2.0 rendering engine.

## Versions

- **V12 - Energy Burst (Current)**: High-performance GPU-based radial flow. Thousands of fragments (shards, lines, streaks) with layered parallax, pulsing core, and breathing expansion speed.
- **V11 - Light Tunnel Evolved**: Spiral tunnel with 1000+ segments. (Archived/Tagged)
- **V9 - Chaos**: Multi-segment snake trails. (Baseline)
- **V1 - Centrifuge**: Basic radial burst logic.

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
