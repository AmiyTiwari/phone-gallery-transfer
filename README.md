# Phone Gallery Transfer

Transfer phone photos to laptop over USB. No Wi-Fi needed.

## Quick Start

### 1. Laptop server

```bash
cd desktop-server
./start.sh          # installs deps, starts daemon + server
```

Server runs on `localhost:8080`. Photos saved to `~/Desktop/phone_photos/`.

### 2. Phone app

Open `android-app/` in Android Studio → Run on device.

> **Prerequisite:** Enable USB Debugging on phone (Settings → Developer Options).

### 3. Use it

1. Plug phone into laptop via USB
2. Daemon auto-detects phone, runs `adb reverse tcp:8080 tcp:8080`
3. Open app → tap **Laptop** tab
4. Tab shows "Connected" when server is reachable
5. Only unsynced photos shown (selective sync via manifest)
6. Select photos (or "Select All") → Upload
7. Per-photo progress bars while uploading
8. Files appear in `~/Desktop/phone_photos/`

---

## Architecture

```
[Android App] ──USB──► [ADB Tunnel] ──► [Python FastAPI Server]
     ↑                      ↑                    ↓
 MediaStore           USB daemon          ~/Desktop/phone_photos/
 SharedPrefs        (auto-setup)          .manifest.json
```

## Manual ADB tunnel (if daemon not running)

```bash
adb reverse tcp:8080 tcp:8080
```

## Cross-platform

`desktop-server/` works on macOS, Windows, Linux — Python + pathlib handles paths.
