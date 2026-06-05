#!/usr/bin/env python3
"""
Core Pack builder (local version) – processes previously-downloaded CC0/CC-BY
sample repos (extracted tarballs) and emits curated WAVs + manifest into the
Android app assets.

Usage:
  # Download tarballs first (see build_core_pack.py for GitHub API version):
  cd /tmp
  curl -fL -o bass.tar.gz   https://codeload.github.com/freepats/electric-bass-YR/tar.gz/refs/heads/main
  curl -fL -o guitar.tar.gz https://codeload.github.com/freepats/e-guitar-FSBS-clean/tar.gz/refs/heads/main
  curl -fL -o drums.tar.gz  https://codeload.github.com/freepats/muldjordkit/tar.gz/refs/heads/main
  # Extract
  mkdir extracted && for f in *.tar.gz; do tar xzf $f -C extracted/; done
  # Build pack
  python3 tools/build_core_pack_local.py /tmp/extracted
"""
import glob
import json
import os
import re
import sys

import numpy as np
import soundfile as sf

EXTRACTED = sys.argv[1] if len(sys.argv) > 1 else "/tmp/extracted"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "packs", "core")


def _ensure(d):
    os.makedirs(d, exist_ok=True)


def read_flac(path):
    data, sr = sf.read(path, dtype="float32", always_2d=True)
    return data.mean(axis=1), sr


def resample(mono, sr, target=44100):
    if sr == target:
        return mono
    n = int(round(len(mono) * target / sr))
    x_old = np.linspace(0, 1, len(mono), endpoint=False)
    x_new = np.linspace(0, 1, n, endpoint=False)
    return np.interp(x_new, x_old, mono).astype(np.float32)


def trim_fade(mono, sr, max_sec=None, fade_ms=12.0, thresh=3e-4):
    nz = np.where(np.abs(mono) > thresh)[0]
    if len(nz):
        mono = mono[nz[0]:]
    if max_sec:
        cap = int(max_sec * sr)
        if len(mono) > cap:
            mono = mono[:cap].copy()
    f = int(sr * fade_ms / 1000)
    if f > 0 and len(mono) > f:
        mono[-f:] *= np.linspace(1, 0, f, dtype=np.float32)
    return mono


def write_wav(path, mono, sr=44100):
    _ensure(os.path.dirname(path))
    sf.write(path, np.clip(mono, -1, 1), sr, subtype="PCM_16")


# ========== BASS ==========

def build_bass():
    print("=== BASS ===")
    base = glob.glob(os.path.join(EXTRACTED, "electric-bass*"))[0]
    sfz_path = glob.glob(os.path.join(base, "*inger*.sfz"))[0]
    with open(sfz_path) as f:
        sfz = f.read()

    regions = []
    cur = {}
    for line in sfz.splitlines():
        line = line.strip()
        if line == "<region>":
            if cur.get("sample"):
                regions.append(cur)
            cur = {}
        m = re.match(r"(\w+)=(.+)", line)
        if m:
            cur[m.group(1)] = m.group(2).strip()
    if cur.get("sample"):
        regions.append(cur)

    bass_dir = os.path.join(OUT, "bass")
    manifest = []
    for reg in regions:
        sample_rel = reg["sample"]
        midi = int(reg.get("key") or reg.get("pitch_keycenter", "0"))
        if not midi:
            continue
        src = os.path.join(base, sample_rel)
        if not os.path.exists(src):
            print(f"  SKIP {sample_rel} (not found)")
            continue
        mono, sr = read_flac(src)
        mono = resample(mono, sr)
        mono = trim_fade(mono, 44100, max_sec=4.5)
        fname = f"bass_{midi}.wav"
        write_wav(os.path.join(bass_dir, fname), mono)
        manifest.append({"file": f"bass/{fname}", "rootMidi": midi, "loVel": 0, "hiVel": 127})
        print(f"  {fname} midi={midi} dur={len(mono)/44100:.2f}s")

    return {
        "instrument": "bass_finger",
        "license": "CC0-1.0",
        "source": "FreePats Electric Bass YR",
        "url": "https://freepats.zenvoid.org/ElectricGuitar/clean-electric-bass.html",
        "samples": manifest,
    }


# ========== GUITAR ==========

def build_guitar():
    print("=== GUITAR ===")
    base = glob.glob(os.path.join(EXTRACTED, "e-guitar*"))[0]
    sfz_path = glob.glob(os.path.join(base, "*.sfz"))[0]
    with open(sfz_path) as f:
        sfz = f.read()

    groups = []
    cur_g = {}
    cur_r = None
    for line in sfz.splitlines():
        line = line.strip()
        if line == "<group>":
            if cur_r and cur_r.get("sample"):
                cur_g.setdefault("regions", []).append(cur_r)
            if cur_g.get("regions"):
                groups.append(cur_g)
            cur_g = {}
            cur_r = None
            continue
        if line == "<region>":
            if cur_r and cur_r.get("sample"):
                cur_g.setdefault("regions", []).append(cur_r)
            cur_r = {}
            continue
        m = re.match(r"(\w+)=(.+)", line)
        if m:
            target = cur_r if cur_r is not None else cur_g
            target[m.group(1)] = m.group(2).strip()
    if cur_r and cur_r.get("sample"):
        cur_g.setdefault("regions", []).append(cur_r)
    if cur_g.get("regions"):
        groups.append(cur_g)

    guitar_dir = os.path.join(OUT, "guitar")
    manifest = []
    done = set()
    for g in groups:
        pkc = int(g.get("pitch_keycenter", 0))
        if not pkc:
            continue
        is_soft = "hivel" in g and int(g.get("hivel", 127)) < 127
        vel_tag = "soft" if is_soft else "hard"
        lo_vel = 0 if is_soft else int(g.get("lovel", 93))
        hi_vel = int(g.get("hivel", 127)) if is_soft else 127
        regs = g.get("regions", [])
        picks = [regs[0], regs[2]] if len(regs) >= 3 else regs[:1]

        for seq_i, reg in enumerate(picks, 1):
            sample_rel = reg.get("sample", "")
            if not sample_rel or sample_rel in done:
                continue
            done.add(sample_rel)
            src = os.path.join(base, sample_rel)
            if not os.path.exists(src):
                print(f"  SKIP {sample_rel}")
                continue
            mono, sr = read_flac(src)
            mono = resample(mono, sr)
            mono = trim_fade(mono, 44100, max_sec=2.5)
            fname = f"gtr_{pkc}_{vel_tag}_{seq_i}.wav"
            write_wav(os.path.join(guitar_dir, fname), mono)
            manifest.append({
                "file": f"guitar/{fname}",
                "rootMidi": pkc,
                "loVel": lo_vel,
                "hiVel": hi_vel,
                "seq": seq_i,
            })
            print(f"  {fname} midi={pkc} vel={vel_tag} seq={seq_i}")

    return {
        "instrument": "guitar_clean",
        "license": "CC0-1.0",
        "source": "FreePats FSBS Clean Electric Guitar",
        "url": "https://freepats.zenvoid.org/ElectricGuitar/clean-electric-guitar.html",
        "samples": manifest,
    }


# ========== DRUMS ==========

DRUM_MAP = {
    "kick":      "KdrumL",
    "snare":     "Snare1",
    "hh_closed": "HihatClosed",
    "hh_open":   "HihatOpen",
    "ride":      "RideR",
    "ride_bell":  "RideRBell",
    "crash":     "CrashR",
    "tom_hi":    "Tom1",
    "tom_mid":   "Tom2",
    "tom_lo":    "Tom3",
    "tom_floor": "Tom4",
}
VEL_TIERS = {"soft": 0.20, "mid": 0.55, "hard": 0.90}
RR_COUNT = 2


def build_drums():
    print("=== DRUMS ===")
    base = glob.glob(os.path.join(EXTRACTED, "muldjord*"))[0]
    drum_dir = os.path.join(OUT, "drums")
    manifest = []

    for hit_name, folder_name in DRUM_MAP.items():
        folder = os.path.join(base, "samples", folder_name)
        files = sorted(
            glob.glob(os.path.join(folder, "*.flac")),
            key=lambda p: int(re.search(r"(\d+)-", os.path.basename(p)).group(1))
            if re.search(r"(\d+)-", os.path.basename(p)) else 0,
        )
        n = len(files)
        if n == 0:
            print(f"  WARN: no files for {hit_name}")
            continue

        for vel_name, vel_pct in VEL_TIERS.items():
            center = int(vel_pct * (n - 1))
            for rr in range(RR_COUNT):
                idx = min(center + rr, n - 1)
                src = files[idx]
                mono, sr = read_flac(src)
                mono = resample(mono, sr)
                mono = trim_fade(mono, 44100, max_sec=3.0)
                fname = f"{hit_name}_{vel_name}_{rr + 1}.wav"
                write_wav(os.path.join(drum_dir, fname), mono)
                manifest.append({
                    "file": f"drums/{fname}",
                    "hit": hit_name,
                    "vel": vel_name,
                    "seq": rr + 1,
                })
                print(f"  {fname} [{os.path.basename(src)}]")

    return {
        "instrument": "drums_acoustic",
        "license": "CC-BY-4.0",
        "source": "MuldjordKit (DrumGizmo / FreePats)",
        "url": "https://freepats.zenvoid.org/Percussion/acoustic-drum-kit.html",
        "attribution": "MuldjordKit by Muldjord, licensed CC-BY-4.0",
        "samples": manifest,
    }


# ========== PIANO ==========

def build_piano():
    print("=== PIANO ===")
    base = glob.glob(os.path.join(EXTRACTED, "upright-piano*"))[0]
    sfz_path = glob.glob(os.path.join(base, "*.sfz"))[0]
    with open(sfz_path) as f:
        sfz = f.read()

    # Parse <group> (carries hivel) then <region> (carries pitch_keycenter+sample).
    regions = []
    cur_group = {}
    cur = None
    for line in sfz.splitlines():
        line = line.strip()
        if line.startswith("<group>"):
            cur_group = {}
            cur = None
            continue
        if line.startswith("<region>"):
            if cur and cur.get("sample"):
                regions.append({**cur_group, **cur})
            cur = {}
            continue
        m = re.match(r"(\w+)=(.+)", line)
        if m:
            target = cur if cur is not None else cur_group
            target[m.group(1)] = m.group(2).strip()
    if cur and cur.get("sample"):
        regions.append({**cur_group, **cur})

    piano_dir = os.path.join(OUT, "piano")
    manifest = []
    # Subsample keycenters to keep the pack small: keep every region whose
    # keycenter is a multiple of 3 semitones (plus both velocity layers).
    for reg in regions:
        pkc = int(reg.get("pitch_keycenter", 0))
        if not pkc or pkc % 3 != 0:
            continue
        if pkc < 28 or pkc > 91:  # C1..G6 useful comping range
            continue
        sample_rel = reg.get("sample", "")
        src = os.path.join(base, sample_rel)
        if not os.path.exists(src):
            continue
        hivel = int(reg.get("hivel", 127))
        is_soft = hivel <= 90
        vel_tag = "soft" if is_soft else "hard"
        lo_vel = 0 if is_soft else 81
        hi_vel = 80 if is_soft else 127
        mono, sr = read_flac(src)
        mono = resample(mono, sr)
        mono = trim_fade(mono, 44100, max_sec=4.0, fade_ms=60.0)
        fname = f"piano_{pkc}_{vel_tag}.wav"
        write_wav(os.path.join(piano_dir, fname), mono)
        manifest.append({
            "file": f"piano/{fname}",
            "rootMidi": pkc,
            "loVel": lo_vel,
            "hiVel": hi_vel,
            "seq": 1,
        })
        print(f"  {fname} midi={pkc} vel={vel_tag}")

    return {
        "instrument": "piano_upright",
        "license": "CC0-1.0",
        "source": "Upright Piano KW (FreePats)",
        "url": "https://freepats.zenvoid.org/Piano/acoustic-grand-piano.html",
        "samples": manifest,
    }


# ========== MAIN ==========

def main():
    manifests = {}
    manifests["bass"] = build_bass()
    manifests["guitar"] = build_guitar()
    manifests["drums"] = build_drums()
    manifests["piano"] = build_piano()

    mf_path = os.path.join(OUT, "manifest.json")
    with open(mf_path, "w") as f:
        json.dump(manifests, f, indent=2)
    print(f"\nManifest: {mf_path}")

    cr_path = os.path.join(OUT, "CREDITS.md")
    with open(cr_path, "w") as f:
        f.write("# Core Pack – Sample Credits & Licenses\n\n")
        for name, m in manifests.items():
            f.write(f"## {name.title()}: {m['source']}\n")
            f.write(f"- License: {m['license']}\n")
            f.write(f"- URL: {m['url']}\n")
            if "attribution" in m:
                f.write(f"- Attribution: {m['attribution']}\n")
            f.write(f"- Samples: {len(m['samples'])}\n\n")
    print(f"Credits: {cr_path}")

    total = sum(len(m["samples"]) for m in manifests.values())
    print(f"Total samples: {total}")

    import subprocess
    r = subprocess.run(["du", "-sh", OUT], capture_output=True, text=True)
    print(f"Pack size: {r.stdout.strip()}")


if __name__ == "__main__":
    main()
