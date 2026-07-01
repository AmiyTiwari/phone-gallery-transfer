"""
USB daemon: polls `adb devices` every 2s, runs `adb reverse` when phone appears.
Cross-platform — works on macOS, Windows, Linux (needs adb in PATH).
"""

import os
import subprocess
import sys
import time

PORT = 8080
POLL_INTERVAL = 2  # seconds

_ADB_FALLBACKS = [
    os.path.expanduser("~/platform-tools/adb"),                       # downloaded by start.sh
    os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),  # Android Studio macOS
    os.path.expanduser("~/Android/Sdk/platform-tools/adb"),          # Android Studio Linux
    "/usr/local/bin/adb",
    "/usr/bin/adb",
    "/opt/homebrew/bin/adb",                                          # Homebrew macOS ARM
]

def _find_adb() -> str:
    if "ADB" in os.environ:
        return os.environ["ADB"]
    for path in _ADB_FALLBACKS:
        if os.path.isfile(path):
            return path
    return "adb"  # hope it's in PATH

ADB_BIN = _find_adb()

_phone_connected = False


def adb(*args) -> subprocess.CompletedProcess:
    return subprocess.run([ADB_BIN, *args], capture_output=True, text=True)


def restart_adb_server():
    subprocess.run([ADB_BIN, "kill-server"], capture_output=True)
    subprocess.run([ADB_BIN, "start-server"], capture_output=True)
    print("[daemon] adb server restarted")


def get_connected_devices() -> list[str]:
    result = adb("devices")
    # If adb server died, restart and retry once
    if result.returncode != 0 or "error" in result.stdout.lower():
        restart_adb_server()
        result = adb("devices")
    lines = result.stdout.strip().splitlines()
    devices = []
    for line in lines[1:]:  # skip "List of devices attached" header
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def setup_tunnel(device_serial: str):
    result = adb("-s", device_serial, "reverse", f"tcp:{PORT}", f"tcp:{PORT}")
    if result.returncode == 0:
        print(f"[daemon] tunnel ready: device {device_serial} → localhost:{PORT}")
    else:
        print(f"[daemon] adb reverse failed: {result.stderr.strip()}", file=sys.stderr)


def teardown_tunnel(device_serial: str):
    adb("-s", device_serial, "reverse", "--remove", f"tcp:{PORT}")
    print(f"[daemon] tunnel removed: {device_serial}")


def check_adb_available() -> bool:
    result = subprocess.run([ADB_BIN, "version"], capture_output=True)
    return result.returncode == 0


def main():
    if not check_adb_available():
        print("[daemon] ERROR: `adb` not found in PATH. Install Android SDK platform-tools.", file=sys.stderr)
        sys.exit(1)

    print(f"[daemon] watching for USB devices (poll every {POLL_INTERVAL}s)...")
    active_devices: set[str] = set()
    consecutive_failures = 0

    while True:
        try:
            current = set(get_connected_devices())
            consecutive_failures = 0
        except Exception as e:
            consecutive_failures += 1
            if consecutive_failures >= 3:
                print(f"[daemon] adb unresponsive, restarting server...", file=sys.stderr)
                restart_adb_server()
                consecutive_failures = 0
            time.sleep(POLL_INTERVAL)
            continue

        # newly connected
        for serial in current - active_devices:
            print(f"[daemon] phone connected: {serial}")
            setup_tunnel(serial)

        # disconnected
        for serial in active_devices - current:
            print(f"[daemon] phone disconnected: {serial}")

        active_devices = current
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[daemon] stopped.")
