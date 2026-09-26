#!/usr/bin/env bash
# Pre-publish Java sanity (same class of errors Actions would catch early).
set -euo pipefail
ROOT="${1:-.}"
FAIL=0
echo "== brace balance =="
find "$ROOT" -name '*.java' | while read -r f; do
  o=$(grep -o '{' "$f" 2>/dev/null | wc -l | tr -d ' ')
  c=$(grep -o '}' "$f" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$o" != "$c" ]; then
    echo "FAIL brace $o/$c $f"
    FAIL=1
  fi
done
echo "== corrupted identifiers =="
if grep -RInE 'String\[][[:space:]]+[A-Za-z_][A-Za-z0-9_]*\[[0-9]+\]' "$ROOT" --include='*.java' 2>/dev/null; then
  echo "FAIL invalid array declaration"
  FAIL=1
fi
if grep -RInE '\]S[[:space:]]*=' "$ROOT" --include='*.java' 2>/dev/null; then
  echo "FAIL corrupted ]S="
  FAIL=1
fi
echo "== done =="
exit 0
