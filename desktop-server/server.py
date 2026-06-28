"""
FastAPI server: receives photos from Android app over USB (ADB tunnel).
Endpoints: GET /ping, GET /manifest, POST /upload
"""

import json
import shutil
from datetime import datetime
from pathlib import Path

import uvicorn
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

app = FastAPI()

PHOTOS_DIR = Path.home() / "Desktop" / "phone_photos"
MANIFEST_FILE = PHOTOS_DIR / ".manifest.json"


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


@app.on_event("startup")
def startup():
    ensure_dirs()
    print(f"[server] saving photos to: {PHOTOS_DIR}")


@app.get("/ping")
def ping():
    return {"status": "ok", "photos_dir": str(PHOTOS_DIR)}


@app.get("/manifest")
def get_manifest():
    """Returns list of already-synced files with original_name included."""
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
    uvicorn.run(app, host="0.0.0.0", port=8080, log_level="info")
