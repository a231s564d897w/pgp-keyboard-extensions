# PGP build note

`extensions/unitto-compose` was removed from this tree for CI.

Morphe auto-applies extension plugins to every module under `extensions/`.
A `com.android.library` module there fails with:

  'com.android.library' and 'com.android.application' plugins cannot be applied in the same project.

Unitto still works via:
- UnittoComposeOverlayHost → official Unitto **web** (cached) in the floating window
- **App** button → native Unitto package
- Offline keypad fallback

In-process Compose can be added later as a non-`extensions/` library module if needed.
