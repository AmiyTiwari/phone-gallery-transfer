"""
FastAPI server: receives photos from Android app over USB (ADB tunnel) or Wi-Fi.
Endpoints: GET /ping, GET /manifest, POST /upload

Env vars:
  MDNS_ENABLED=1  — announce this server via mDNS for Wi-Fi discovery (set by start.sh)
"""

import json
import os
import shutil
import socket
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path

import uvicorn
from fastapi import FastAPI, File, Form, UploadFile

PHOTOS_DIR = Path.home() / "Desktop" / "phone_photos"
MANIFEST_FILE = PHOTOS_DIR / ".manifest.json"
PORT = 8080

MDNS_SERVICE_TYPE = "_photosync._tcp.local."
MDNS_SERVICE_NAME = "PhotoSync._photosync._tcp.local."

_app_state: dict = {}


def ensure_dirs():
    PHOTOS_DIR.mkdir(parents=True, exist_ok=True)


def load_manifest() -> dict:
    if MANIFEST_FILE.exists():
        with open(MANIFEST_FILE) as f:
            return json.load(f)
    return {}


def save_manifest(manifest: dict):
    with open(MANIFEST_FILE, "w") as f:
        json.dump(manifest, f, indent=2)


def _get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


@asynccontextmanager
async def lifespan(app: FastAPI):
    ensure_dirs()
    local_ip = _get_local_ip()
    hostname = socket.gethostname()
    print(f"[server] saving photos to: {PHOTOS_DIR}")
    print(f"[server] local IP: {local_ip}:{PORT}  hostname: {hostname}")

    _app_state["hostname"] = hostname
    _app_state["local_ip"] = local_ip
    _zc = None
    _info = None

    if os.environ.get("MDNS_ENABLED", "0") == "1":
        try:
            from zeroconf import ServiceInfo, Zeroconf
            _zc = Zeroconf()
            _info = ServiceInfo(
                MDNS_SERVICE_TYPE,
                MDNS_SERVICE_NAME,
                addresses=[socket.inet_aton(local_ip)],
                port=PORT,
                properties={"name": hostname},
            )
            _zc.register_service(_info)
            print(f"[server] mDNS announced: {hostname} @ {local_ip}:{PORT}")
        except Exception as e:
            print(f"[server] mDNS registration failed: {e}")
            _zc = None

    yield

    if _zc and _info:
        _zc.unregister_service(_info)
        _zc.close()
        print("[server] mDNS unregistered")


app = FastAPI(lifespan=lifespan)


@app.get("/ping")
def ping():
    return {
        "status": "ok",
        "host": _app_state.get("hostname", socket.gethostname()),
        "photos_dir": str(PHOTOS_DIR),
    }


@app.get("/manifest")
def get_manifest():
    manifest = load_manifest()
    entries = [{"original_name": orig, **data} for orig, data in manifest.items()]
    return {"files": entries}


@app.post("/upload")
async def upload_photo(
    file: UploadFile = File(...),
    original_name: str = Form(...),
    modified_ts: str = Form(...),
):
    ensure_dirs()

    timestamp = datetime.now().strftime("%Y-%m-%d_%H%M%S")
    safe_name = Path(original_name).name
    dest_filename = f"{timestamp}_{safe_name}"
    dest_path = PHOTOS_DIR / dest_filename

    with open(dest_path, "wb") as out:
        shutil.copyfileobj(file.file, out)

    manifest = load_manifest()
    manifest[original_name] = {
        "filename": dest_filename,
        "size": dest_path.stat().st_size,
        "modified_ts": modified_ts,
        "synced_at": datetime.now().isoformat(),
    }
    save_manifest(manifest)

    print(f"[server] saved: {dest_filename} ({dest_path.stat().st_size} bytes)")
    return {"status": "ok", "saved_as": dest_filename}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info")
