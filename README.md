# Phone Gallery Transfer

Transfer Android phone photos to a laptop over Wi-Fi or USB.

The project has two parts:

- `desktop-server/`: a Python FastAPI server that receives photos and tracks synced files.
- `android-app/`: a native Android app that discovers the laptop, shows phone photos, and uploads selected items.

## Quick Start

### 1. Laptop server

Wi-Fi mode is the default. The phone and laptop must be on the same network.

```bash
cd desktop-server
./start.sh
```

Server runs on `localhost:8080`. Photos saved to `~/Desktop/phone_photos/`.

For USB cable mode, enable USB Debugging on the phone and start the server with:

```bash
cd desktop-server
./start.sh --usb
```

USB mode starts the ADB daemon helper and runs:

```bash
adb reverse tcp:8080 tcp:8080
```

### 2. Phone app

Open `android-app/` in Android Studio, then run the app on a device.

For direct testing without Android Studio, install the debug APK from:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

Using ADB:

```bash
adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
```

> USB install and USB transfer both require USB Debugging on the phone (Settings -> Developer Options).

### 3. Use it

1. Start the laptop server.
2. Open the Android app and tap the **Laptop** tab.
3. Select one or more discovered servers.
4. Continue to the photo grid.
5. Select photos, or tap **Select All**.
6. Tap **Upload**.
7. Watch per-photo upload status.
8. Files appear in `~/Desktop/phone_photos/`.

The app hides photos that are already synced by comparing against the server manifest.

---

## Architecture

```
[Android App] ──Wi-Fi/mDNS or USB/ADB──► [Python FastAPI Server]
     ↑                                           ↓
 MediaStore                              ~/Desktop/phone_photos/
 selected uploads                         .manifest.json
```

## Build Debug APK

If the debug APK is missing or stale, rebuild it:

```bash
cd android-app
./gradlew assembleDebug
```

The generated app is written to:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

## Manual USB Tunnel

```bash
adb reverse tcp:8080 tcp:8080
```

## Cross-platform

`desktop-server/` is written with Python and `pathlib`, so it can run on macOS, Windows, and Linux. The bundled `start.sh` is for Unix-like shells; Windows users can run `python server.py` after installing `desktop-server/requirements.txt`.
