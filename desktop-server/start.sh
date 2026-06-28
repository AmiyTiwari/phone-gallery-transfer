#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check Python
if ! command -v python3 &>/dev/null; then
  echo "ERROR: python3 not found"
  exit 1
fi

# Install deps if needed
if ! python3 -c "import fastapi" 2>/dev/null; then
  echo "[start] installing dependencies..."
  pip3 install -r requirements.txt
fi

echo "[start] starting daemon + server..."

# Start daemon in background
python3 daemon.py &
DAEMON_PID=$!

# Trap so daemon dies when script exits
trap "kill $DAEMON_PID 2>/dev/null" EXIT INT TERM

# Start server (foreground)
python3 server.py
