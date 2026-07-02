#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ── Parse flags ───────────────────────────────────────────────────────────
USB_MODE=0
for arg in "$@"; do
    [ "$arg" = "--usb" ] && USB_MODE=1
done

# ── 1. Find or install ADB (needed for USB mode) ──────────────────────────
find_adb() {
    for candidate in \
        "$HOME/platform-tools/adb" \
        "$HOME/Library/Android/sdk/platform-tools/adb" \
        "$HOME/Android/Sdk/platform-tools/adb" \
        "/usr/local/bin/adb" \
        "/opt/homebrew/bin/adb" \
        "/usr/bin/adb"
    do
        if [ -x "$candidate" ]; then
            echo "$candidate"
            return 0
        fi
    done
    if command -v adb &>/dev/null; then
        command -v adb
        return 0
    fi
    return 1
}

# ── 2. Check Python ────────────────────────────────────────────────────────
if ! command -v python3 &>/dev/null; then
    echo "[start] ERROR: python3 not found"
    exit 1
fi

# ── 3. Install Python deps if needed ──────────────────────────────────────
if ! python3 -c "import fastapi" 2>/dev/null; then
    echo "[start] installing dependencies..."
    pip3 install -r requirements.txt
elif ! python3 -c "import zeroconf" 2>/dev/null; then
    echo "[start] installing zeroconf..."
    pip3 install zeroconf==0.132.2
fi

# ── 4. Launch ──────────────────────────────────────────────────────────────
if [ "$USB_MODE" = "1" ]; then
    echo "[start] USB mode — ADB daemon + server (no mDNS)"

    ADB_BIN=$(find_adb || true)
    if [ -z "$ADB_BIN" ]; then
        echo "[start] adb not found — downloading platform-tools..."
        OS="$(uname -s)"
        case "$OS" in
            Darwin) PT_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip" ;;
            Linux)  PT_URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip" ;;
            *) echo "[start] ERROR: unsupported OS $OS — install adb manually"; exit 1 ;;
        esac
        curl -L -o "$HOME/platform-tools.zip" "$PT_URL"
        unzip -q "$HOME/platform-tools.zip" -d "$HOME/"
        rm "$HOME/platform-tools.zip"
        chmod +x "$HOME/platform-tools/adb"
        ADB_BIN="$HOME/platform-tools/adb"
        echo "[start] adb installed at $ADB_BIN"
    fi

    export ADB="$ADB_BIN"
    echo "[start] using adb: $ADB_BIN"

    python3 -u daemon.py &
    DAEMON_PID=$!
    trap "kill $DAEMON_PID 2>/dev/null; echo '[start] stopped.'" EXIT INT TERM
    python3 server.py

else
    echo "[start] Wi-Fi mode (default) — server with mDNS discovery"
    echo "[start] Tip: use 'bash start.sh --usb' for USB cable mode"
    trap "echo '[start] stopped.'" EXIT INT TERM
    MDNS_ENABLED=1 python3 server.py
fi
