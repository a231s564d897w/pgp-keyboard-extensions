# PGP — Unitto in Gboard (Path B + Path C)

**PGP** = **Private / Personal** Gboard patches (not upstream “Gboard Patches”, not AI branding).

Goal: real Unitto **libraries + resources** live **inside** the patched Gboard APK via Morphe
extension DEX — not a second installed Unitto app as the primary calculator.

## Path B — Prebuilt library AAR → extension

### Offline (developer machine / CI job, not under extensions/)

1. Clone https://github.com/sadellie/unitto (GPL-3.0).
2. Add a **library** product (do **not** ship `androidApp`):
   - `:core:evaluatto` (math) — required
   - optional slim `:feature:calculator` UI as `com.android.library`
3. Assemble `unitto-engine.aar` (and optional `unitto-calc-ui.aar`).
4. Copy into PGP tree:

```
extensions/extension/libs/unitto-engine.aar
```

5. In `extensions/extension/build.gradle.kts` only (same module Morphe already merges):

```kotlin
dependencies {
    implementation(files("libs/unitto-engine.aar"))
    // Compose only if UI AAR needs it — versions must match Morphe/AGP
    // implementation(platform("androidx.compose:compose-bom:…"))
    // implementation("androidx.compose.ui:ui")
    // implementation("androidx.compose.material3:material3")
}
android {
    buildFeatures { compose = true } // only when UI AAR is present
}
```

6. Kotlin/Java bridge in extension calls engine / hosts `ComposeView` in
   `GboardCalculatorOverlayHost` / floating panel.

7. Build `.mpe` as today → Morphe `extendWith` merges classes into Gboard.

### Rules

- No `com.android.application` under `extensions/`.
- No nested `build.gradle.kts` under Java source trees.
- Package/R ids stay on Gboard / extension namespace after merge.
- Attribute evaluatto / Unitto under GPL-3.0 in NOTICE.

## Path C — Resources + DEX pack (install-time “game pack”)

1. From the same library build, export:
   - classes.dex (or keep inside AAR)
   - `res/` drawables, values, raw unit tables
2. Morphe **ResourcePatch** / **RawResourcePatch**:
   - inject assets under e.g. `assets/pgp-unitto/`
   - merge non-conflicting drawables as `res/drawable/pgp_unitto_*`
3. Runtime: extension opens assets via `context.getAssets()` or Resources.
4. Optional: first-run extract to `context.getFilesDir()/pgp-unitto/` if large
   tables need random access (same idea as game OBB, but **bundled at patch time**,
   not downloaded from Play).

## Primary UX (PGP)

1. Calculator Access Point → **in-Gboard** Unitto-grade UI (B/C).
2. Optional: freeform real Unitto app if installed (power user).
3. Height = Floating Web Search `initial_height_percent`.

## Status

| Piece | Status |
|--------|--------|
| Path A native evaluatto Java engine | Shipped |
| Path B AAR slot `libs/` | Scaffolded |
| Path B Compose-in-extension flags | Documented; enable when AAR present |
| Path C asset layout | Documented |
| Full Unitto converter/history in Gboard | Future after B calculator UI |

## Branding

User-facing strings: **PGP**, **Private/Personal Gboard patches**.  
Logs: `PGP`. Avoid “AI” wording.
