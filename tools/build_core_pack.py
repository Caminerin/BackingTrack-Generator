#!/usr/bin/env python3
"""
Core Pack builder – downloads curated REAL instrument samples from CC0/CC-BY
GitHub mirrors, converts FLAC→WAV 44.1 kHz mono PCM-16, and writes them into
the Android app assets under  app/src/main/assets/packs/core/.

Sources:
  Drums  – MuldjordKit (CC-BY-4.0) via freepats/muldjordkit
  Bass   – FreePats Electric Bass YR finger (CC0-1.0) via freepats/electric-bass-YR
  Guitar – FreePats FSBS Clean Electric (CC0-1.0) via freepats/e-guitar-FSBS-clean

Run:  python3 tools/build_core_pack.py
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from sample_sources import (
    decode_audio,
    fetch_blob,
    repo_tree,
    resample_to,
    trim_and_fade,
    write_wav,
)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "packs", "core")
os.makedirs(OUT, exist_ok=True)


# ---------- helpers ----------

def _download_and_write(repo, tree, src_path, dst_path, max_sec=None, target_sr=44100):
    sha = tree[src_path][0]
    raw = fetch_blob(repo, sha)
    mono, sr = decode_audio(raw)
    mono = resample_to(mono, sr, target_sr)
    mono = trim_and_fade(mono, target_sr, max_seconds=max_sec, fade_ms=12.0)
    write_wav(dst_path, mono, target_sr)
    return len(mono) / target_sr


# ========== BASS ==========

def build_bass():
    print("=== BASS (FreePats Electric Bass YR, finger, CC0) ===")
    repo = "freepats/electric-bass-YR"
    tree = repo_tree(repo, "main")

    sfz_path = [p for p in tree if "finger" in p.lower() and p.endswith(".sfz")][0]
    sfz = fetch_blob(repo, tree[sfz_path][0]).decode("utf-8", "replace")

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
        sample = reg["sample"]
        midi = int(reg.get("key") or reg.get("pitch_keycenter", "0"))
        if not midi:
            continue
        fname = f"bass_{midi}.wav"
        dst = os.path.join(bass_dir, fname)
        dur = _download_and_write(repo, tree, sample, dst, max_sec=4.5)
        manifest.append({
            "file": f"bass/{fname}",
            "rootMidi": midi,
            "loVel": 0,
            "hiVel": 127,
        })
        print(f"  {fname} midi={midi} dur={dur:.2f}s")

    return {
        "instrument": "bass_finger",
        "license": "CC0-1.0",
        "source": "FreePats Electric Bass YR",
        "url": "https://freepats.zenvoid.org/ElectricGuitar/clean-electric-bass.html",
        "samples": manifest,
    }


# ========== GUITAR ==========

def build_guitar():
    print("=== GUITAR (FreePats FSBS Clean Electric, CC0) ===")
    repo = "freepats/e-guitar-FSBS-clean"
    tree = repo_tree(repo, "main")

    sfz_path = [p for p in tree if p.endswith(".sfz")][0]
    sfz = fetch_blob(repo, tree[sfz_path][0]).decode("utf-8", "replace")

    # Parse groups: each <group> has lokey/hikey, hivel or lovel, pitch_keycenter
    # each <region> inside has lorand/hirand and sample
    groups = []
    cur_group = {}
    cur_region = None
    for line in sfz.splitlines():
        line = line.strip()
        if line == "<group>":
            if cur_region and cur_region.get("sample"):
                cur_group.setdefault("regions", []).append(cur_region)
            if cur_group.get("regions"):
                groups.append(cur_group)
            cur_group = {}
            cur_region = None
            continue
        if line == "<region>":
            if cur_region and cur_region.get("sample"):
                cur_group.setdefault("regions", []).append(cur_region)
            cur_region = {}
            continue
        m = re.match(r"(\w+)=(.+)", line)
        if m:
            target = cur_region if cur_region is not None else cur_group
            target[m.group(1)] = m.group(2).strip()
    if cur_region and cur_region.get("sample"):
        cur_group.setdefault("regions", []).append(cur_region)
    if cur_group.get("regions"):
        groups.append(cur_group)

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
        # Pick 2 round-robins (first and third, or first two if <3)
        picks = []
        if len(regs) >= 3:
            picks = [regs[0], regs[2]]
        elif len(regs) >= 1:
            picks = [regs[0]]

        for seq_i, reg in enumerate(picks, 1):
            sample = reg.get("sample", "")
            if not sample or sample in done:
                continue
            done.add(sample)
            fname = f"gtr_{pkc}_{vel_tag}_{seq_i}.wav"
            dst = os.path.join(guitar_dir, fname)
            dur = _download_and_write(repo, tree, sample, dst, max_sec=2.5)
            manifest.append({
                "file": f"guitar/{fname}",
                "rootMidi": pkc,
                "loVel": lo_vel,
                "hiVel": hi_vel,
                "seq": seq_i,
            })
            print(f"  {fname} midi={pkc} vel={vel_tag} seq={seq_i} dur={dur:.2f}s")

    return {
        "instrument": "guitar_clean",
        "license": "CC0-1.0",
        "source": "FreePats FSBS Clean Electric Guitar",
        "url": "https://freepats.zenvoid.org/ElectricGuitar/clean-electric-guitar.html",
        "samples": manifest,
    }


# ========== DRUMS ==========

DRUM_MAP = {
    "kick":       {"folder": "KdrumL",     "count": 25},
    "snare":      {"folder": "Snare1",     "count": 56},
    "hh_closed":  {"folder": "HihatClosed","count": None},
    "hh_open":    {"folder": "HihatOpen",  "count": None},
    "ride":       {"folder": "RideR",      "count": None},
    "ride_bell":  {"folder": "RideRBell",  "count": None},
    "crash":      {"folder": "CrashR",     "count": None},
    "tom_hi":     {"folder": "Tom1",       "count": None},
    "tom_mid":    {"folder": "Tom2",       "count": None},
    "tom_lo":     {"folder": "Tom3",       "count": None},
    "tom_floor":  {"folder": "Tom4",       "count": None},
}

VEL_TIERS = {
    "soft": 0.20,
    "mid":  0.55,
    "hard": 0.90,
}
RR_COUNT = 2


def build_drums():
    print("=== DRUMS (MuldjordKit, CC-BY-4.0) ===")
    repo = "freepats/muldjordkit"
    tree = repo_tree(repo, "main")

    drum_dir = os.path.join(OUT, "drums")
    manifest = []

    for hit_name, info in DRUM_MAP.items():
        folder = f"samples/{info['folder']}"
        files = sorted(
            [p for p in tree if p.startswith(folder + "/") and p.endswith(".flac")],
            key=lambda p: int(re.search(r"(\d+)-", p.split("/")[-1]).group(1))
            if re.search(r"(\d+)-", p.split("/")[-1])
            else 0,
        )
        n = len(files)
        if n == 0:
            print(f"  WARN: no files for {hit_name} in {folder}")
            continue

        for vel_name, vel_pct in VEL_TIERS.items():
            center = int(vel_pct * (n - 1))
            for rr in range(RR_COUNT):
                idx = min(center + rr, n - 1)
                src = files[idx]
                fname = f"{hit_name}_{vel_name}_{rr + 1}.wav"
                dst = os.path.join(drum_dir, fname)
                dur = _download_and_write(repo, tree, src, dst, max_sec=3.0)
                manifest.append({
                    "file": f"drums/{fname}",
                    "hit": hit_name,
                    "vel": vel_name,
                    "seq": rr + 1,
                })
                print(f"  {fname} [{src.split('/')[-1]}] dur={dur:.2f}s")

    return {
        "instrument": "drums_acoustic",
        "license": "CC-BY-4.0",
        "source": "MuldjordKit (DrumGizmo / FreePats)",
        "url": "https://freepats.zenvoid.org/Percussion/acoustic-drum-kit.html",
        "attribution": "MuldjordKit by Muldjord, licensed CC-BY-4.0",
        "samples": manifest,
    }


# ========== MAIN ==========

def main():
    manifests = {}

    manifests["bass"] = build_bass()
    manifests["guitar"] = build_guitar()
    manifests["drums"] = build_drums()

    # Write combined manifest
    mf_path = os.path.join(OUT, "manifest.json")
    with open(mf_path, "w") as f:
        json.dump(manifests, f, indent=2)
    print(f"\nManifest written to {mf_path}")

    # Write CREDITS.md
    cr = os.path.join(OUT, "CREDITS.md")
    with open(cr, "w") as f:
        f.write("# Core Pack – Sample Credits & Licenses\n\n")
        for name, m in manifests.items():
            f.write(f"## {name.title()}: {m['source']}\n")
            f.write(f"- License: {m['license']}\n")
            f.write(f"- URL: {m['url']}\n")
            if "attribution" in m:
                f.write(f"- Attribution: {m['attribution']}\n")
            f.write(f"- Samples: {len(m['samples'])}\n\n")
    print(f"Credits written to {cr}")

    # Summary
    total = sum(len(m["samples"]) for m in manifests.values())
    print(f"\nTotal samples: {total}")
    # Size on disk
    import subprocess
    r = subprocess.run(["du", "-sh", OUT], capture_output=True, text=True)
    print(f"Pack size: {r.stdout.strip()}")


if __name__ == "__main__":
    main()
