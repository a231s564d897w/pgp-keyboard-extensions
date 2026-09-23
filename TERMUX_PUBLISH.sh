#!/data/data/com.termux/files/usr/bin/bash
# Publish PGP to a NEW GitHub repo (not upstream Gboard-patches).
set -euo pipefail

REPO_NAME="${REPO_NAME:-pgp-keyboard-extensions}"
REPO_VISIBILITY="${REPO_VISIBILITY:-public}"
WORK="$HOME/pgp-publish"
SOURCE="${SOURCE:-$HOME/gboard-patches-impl}"

echo "==> Preparing $WORK"
rm -rf "$WORK"
mkdir -p "$WORK"

if [ ! -d "$SOURCE" ]; then
  echo "SOURCE not found: $SOURCE"
  echo "Copy the project to the phone first."
  exit 1
fi

cp -a "$SOURCE"/. "$WORK/"
cd "$WORK"

printf '%s\n' "build/" ".gradle/" "*.iml" ".idea/" "local.properties" "*.apk" ".DS_Store" > .gitignore

if [ ! -f README.md ]; then
  printf '%s\n' "# PGP" "PGP Keyboard Extensions — independent Morphe-compatible work (not upstream Gboard-patches)." > README.md
fi

git init
git branch -M main
git add .
git -c user.email="${GIT_EMAIL:-pgp@users.noreply.github.com}" -c user.name="${GIT_NAME:-PGP}" \
  commit -m "PGP: Key Row, Unitto overlay, Pass floating window"

if command -v gh >/dev/null 2>&1; then
  gh auth status
  gh repo create "$REPO_NAME" --"$REPO_VISIBILITY" \
    --description "PGP Keyboard Extensions (Morphe-compatible; not upstream Gboard-patches)" \
    --source=. --remote=origin --push
  echo "==> Published"
  gh repo view --web
else
  echo "Install gh or add remote manually:"
  echo "  git remote add origin https://github.com/YOUR_USER/$REPO_NAME.git"
  echo "  git push -u origin main"
fi
