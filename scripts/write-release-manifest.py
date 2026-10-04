#!/usr/bin/env python3
"""Write a release manifest after a production build/test pass.

Does not claim production-complete. Records measured gates only.
Evidence always corresponds to the exact HEAD commit that generated it.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


def read_prop(props: dict[str, str], key: str) -> str:
    if key not in props:
        raise SystemExit(f"missing gradle.properties key: {key}")
    return props[key]


def int_const(text: str, name: str) -> int:
    m = re.search(rf"\b{re.escape(name)}\s*=\s*(\d+)\b", text)
    if not m:
        raise SystemExit(f"missing constant {name}")
    return int(m.group(1))


def str_const(text: str, name: str) -> str:
    m = re.search(rf'\b{re.escape(name)}\s*=\s*"([^"]+)"', text)
    if not m:
        raise SystemExit(f"missing string constant {name}")
    return m.group(1)


def git_commit(root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return "unknown"


def git_dirty(root: Path) -> bool:
    try:
        out = subprocess.check_output(
            ["git", "status", "--porcelain"], cwd=root, text=True, stderr=subprocess.DEVNULL
        )
        return bool(out.strip())
    except Exception:
        return True


def sha256_file(path: Path) -> str | None:
    if not path.is_file():
        return None
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def java_version() -> str:
    try:
        out = subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True)
        return out.splitlines()[0].strip()
    except Exception:
        return "unknown"


def gradle_version(root: Path) -> str:
    wrapper = root / "gradlew"
    try:
        if wrapper.exists():
            out = subprocess.check_output([str(wrapper), "--version"], cwd=root, text=True, stderr=subprocess.STDOUT)
        else:
            out = subprocess.check_output(["gradle", "--version"], cwd=root, text=True, stderr=subprocess.STDOUT)
        for line in out.splitlines():
            if line.lower().startswith("gradle "):
                return line.strip()
        return "unknown"
    except Exception:
        return "unknown"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--core-suite", required=True, choices=("pass", "fail", "skipped"))
    parser.add_argument("--linked-build", required=True, choices=("pass", "fail", "skipped"))
    parser.add_argument("--runtime-smoke", default="unverified", choices=("pass", "fail", "unverified", "skipped"))
    parser.add_argument("--gametests", default="unverified", choices=("pass", "fail", "unverified", "skipped"))
    parser.add_argument("--jar", default="")
    parser.add_argument("--out", default="RELEASE_MANIFEST.json")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    props: dict[str, str] = {}
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()

    codec = (root / "src/main/java/dev/livingrealms/sim/persistence/SimulationStateCodec.java").read_text(encoding="utf-8")
    snap = (root / "src/main/java/dev/livingrealms/sim/ui/RealmDashboardSnapshot.java").read_text(encoding="utf-8")
    net = (root / "src/main/java/dev/livingrealms/minecraft/network/LivingRealmsNetwork.java").read_text(encoding="utf-8")
    saved = (root / "src/main/java/dev/livingrealms/minecraft/LivingRealmsSavedData.java").read_text(encoding="utf-8")

    jar_path = Path(args.jar) if args.jar else None
    jar_ok = bool(jar_path and jar_path.is_file() and jar_path.stat().st_size > 1024)
    jar_sha = sha256_file(jar_path) if jar_ok else None
    commit = git_commit(root)
    dirty = git_dirty(root)

    manifest = {
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "gitCommit": commit,
        "gitWorktreeClean": not dirty,
        "modVersion": read_prop(props, "mod_version"),
        "minecraftVersion": read_prop(props, "minecraft_version"),
        "neoforgeVersion": read_prop(props, "neo_version"),
        "createVersion": read_prop(props, "create_version"),
        "javaVersion": java_version(),
        "gradleVersion": gradle_version(root),
        "saveSchema": int_const(codec, "SCHEMA_VERSION"),
        "minSupportedSchema": int_const(codec, "MIN_SUPPORTED_SCHEMA"),
        "dashboardProtocol": int_const(snap, "PROTOCOL_VERSION"),
        "networkProtocol": str_const(net, "NETWORK_VERSION"),
        "contentRevision": int_const(saved, "CONTENT_REVISION"),
        "gates": {
            "coreSuite": args.core_suite,
            "linkedBuild": args.linked_build,
            "gameTests": args.gametests,
            "runtimeSmoke": args.runtime_smoke,
        },
        "artifact": {
            "jar": str(jar_path) if jar_path else None,
            "jarPresent": jar_ok,
            "jarBytes": jar_path.stat().st_size if jar_ok else 0,
            "jarSha256": jar_sha,
        },
        "releaseCompleteClaimAllowed": False,
        "notes": [
            "SOURCE WINS OVER DOCS.",
            "Manifest gitCommit must equal the exact commit being certified.",
            "Do not claim production-ready while runtimeSmoke is unverified/fail/skipped.",
        ],
    }

    out = root / args.out
    out.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    # Sidecar checksum of the manifest itself (not a recursive tree hash dump).
    manifest_sha = sha256_file(out)
    sha_path = root / (out.name + ".sha256")
    sha_path.write_text(f"{manifest_sha}  {out.name}\n", encoding="utf-8")

    print(f"Wrote {out.relative_to(root)}")
    print(f"Wrote {sha_path.relative_to(root)} ({manifest_sha})")
    if dirty:
        print("WARNING: worktree dirty — release evidence is for a dirty tree, not a clean commit.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
