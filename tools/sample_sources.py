"""
Shared helpers to fetch real, openly-licensed instrument samples from GitHub
mirrors and decode them.

Why GitHub blobs API instead of raw.githubusercontent.com?
The build/dev environment used to assemble the Core Pack only has outbound
access to api.github.com (the raw CDN and the original FreePats/Karoryfer hosts
are blocked). The git blobs API returns file contents as base64 for any size,
so it is the reliable transport here.

All sources used are CC0-1.0 or CC-BY-4.0 (see CREDITS.md / pack manifest).
"""
import base64
import io
import json
import os
import time
import urllib.request

import numpy as np
import soundfile as sf

CACHE_DIR = os.path.expanduser("~/sample_cache")
os.makedirs(CACHE_DIR, exist_ok=True)


def _api(url: str):
    last = None
    for attempt in range(5):
        try:
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "backingtrack-pack-builder",
                    "Accept": "application/vnd.github+json",
                },
            )
            tok = os.environ.get("GITHUB_PAT") or os.environ.get("GITHUB_TOKEN")
            if tok:
                req.add_header("Authorization", f"Bearer {tok}")
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"GitHub API failed for {url}: {last}")


def repo_tree(repo: str, branch: str) -> dict:
    """Return {path: (sha, size)} for all blobs in a repo at branch."""
    data = _api(
        f"https://api.github.com/repos/{repo}/git/trees/{branch}?recursive=1"
    )
    return {
        e["path"]: (e["sha"], e.get("size", 0))
        for e in data["tree"]
        if e["type"] == "blob"
    }


def fetch_blob(repo: str, sha: str) -> bytes:
    safe = sha.replace("/", "_")
    cache = os.path.join(CACHE_DIR, f"{repo.replace('/', '_')}_{safe}.bin")
    if os.path.exists(cache):
        with open(cache, "rb") as f:
            return f.read()
    d = _api(f"https://api.github.com/repos/{repo}/git/blobs/{sha}")
    raw = base64.b64decode(d["content"])
    with open(cache, "wb") as f:
        f.write(raw)
    return raw


def decode_audio(raw: bytes):
    """Decode FLAC/WAV bytes -> (float32 mono array, samplerate)."""
    data, sr = sf.read(io.BytesIO(raw), dtype="float32", always_2d=True)
    mono = data.mean(axis=1)
    return mono, sr


def resample_to(mono: np.ndarray, sr: int, target: int = 44100) -> np.ndarray:
    if sr == target:
        return mono
    n = int(round(len(mono) * target / sr))
    if n <= 1:
        return mono
    x_old = np.linspace(0.0, 1.0, num=len(mono), endpoint=False)
    x_new = np.linspace(0.0, 1.0, num=n, endpoint=False)
    return np.interp(x_new, x_old, mono).astype(np.float32)


def trim_and_fade(
    mono: np.ndarray,
    sr: int,
    max_seconds: float | None = None,
    fade_ms: float = 8.0,
    thresh: float = 3e-4,
) -> np.ndarray:
    """Trim leading silence and (optionally) cap length with a short fade-out."""
    nz = np.where(np.abs(mono) > thresh)[0]
    if len(nz):
        mono = mono[nz[0]:]
    if max_seconds is not None:
        cap = int(max_seconds * sr)
        if len(mono) > cap:
            mono = mono[:cap].copy()
    f = int(sr * fade_ms / 1000.0)
    if f > 0 and len(mono) > f:
        mono[-f:] *= np.linspace(1.0, 0.0, f, dtype=np.float32)
    return mono


def write_wav(path: str, mono: np.ndarray, sr: int = 44100):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = np.clip(mono, -1.0, 1.0)
    sf.write(path, pcm, sr, subtype="PCM_16")
