#!/data/data/com.termux/files/usr/bin/bash
# PGP Path B — build unitto-engine.aar from upstream Unitto (library-only).
# Run on a machine with Android SDK + JDK 17 (or Termux with enough disk).
set -euo pipefail
OUT_DIR="${1:-$HOME/storage/downloads}"
WORKDIR="${TMPDIR:-/tmp}/pgp-unitto-engine-build"
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR" "$OUT_DIR"
cd "$WORKDIR"
echo "Cloning sadellie/unitto…"
git clone --depth 1 https://github.com/sadellie/unitto.git
cd unitto
# Prefer evaluatto module; full KMP build needs network + SDK.
# Minimal approach: document that developer builds :core:evaluatto as AAR
# after applying android.library packaging in a local fork.
echo ""
echo "Manual steps (Android Studio / full SDK):"
echo "  1. Open this clone in Android Studio"
echo "  2. Ensure :core:evaluatto is a library (KMP androidLibrary)"
echo "  3. Run: ./gradlew :core:evaluatto:assembleRelease"
echo "  4. Copy the AAR to PGP:"
echo "     extensions/extension/libs/unitto-engine.aar"
echo ""
echo "Clone is at: $WORKDIR/unitto"
# Try gradle if present
if [ -f ./gradlew ]; then
  chmod +x ./gradlew
  ./gradlew :core:evaluatto:assembleRelease --no-daemon 2>&1 | tail -40 || true
  find . -name '*.aar' 2>/dev/null | head -20
fi
