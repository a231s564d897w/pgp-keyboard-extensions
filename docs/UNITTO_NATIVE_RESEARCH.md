# Research: Real Unitto built into the PGP patch (no WebView primary)

Date: 2026-09-26  
Upstream: https://github.com/sadellie/unitto (GPL-3.0)

## What “real Unitto” means

Unitto is **not** one APK-drop file. It is a KMP graph:

```
androidApp (application)
  └── sharedApp (navigation + DI)
        ├── feature:calculator  →  core:evaluatto (math)
        ├── feature:converter   →  core:evaluatto + data
        ├── feature:datecalculator
        ├── feature:timezone
        ├── feature:bodymass
        └── core:designsystem / datastore / database (Compose UI)
```

The **math heart** is `:core:evaluatto` (`io.github.sadellie.evaluatto.Expression`).  
The **UI** is Jetpack Compose Multiplatform in feature modules.

## Why past attempts failed

| Approach | Result |
|----------|--------|
| Separate `extensions/unitto-compose` module with `android.application` | Morphe discovers every `extensions/*` module → library/application plugin clash |
| Nested `build.gradle.kts` under Java sources | Gradle still configures it → Compose plugin missing |
| WebView → unitto PWA | Real product UI, but network/cache; user wants **built-in** |
| Launch installed Unitto freeform | Real app, not “inside” the patch |

## Ways that actually work inside Morphe

### A. In-process evaluatto engine (shipped now) — **primary built-in path**

Port Unitto’s evaluatto algorithm into pure Java inside `extensions/extension`:

- Tokenizer + recursive-descent Expression (same operators/functions as Unitto)
- `BigDecimal` math (sin/cos/tan/ln/log/sqrt/pow/factorial/%)
- Native scientific keypad overlay (already hosted by WindowManager / IME panel)

**Pros:** Offline, no WebView, no second APK, compiles with existing Java toolchain.  
**License:** GPL-3.0 attribution to Elshan Agaev / Unitto evaluatto.  
**Cons:** UI is View-based scientific layout, not pixel-identical Compose Unitto chrome.

### B. Compose UI **inside** `extensions/extension` only (next hard step)

1. Enable Compose on the **existing** library module only (`buildFeatures { compose = true }`).
2. Add Compose BOM deps matching upstream AGP/Kotlin.
3. Put Kotlin sources under `src/main/java/.../unitto/compose/` (no nested Gradle module).
4. Host with `ComposeView` in the floating window.

**Pros:** Closest to full Unitto look.  
**Cons:** Needs CI Kotlin Compose plugin versions aligned with Morphe; classloader size in patched Gboard.

### C. Prebuilt AAR of Unitto feature UI

Build `:feature:calculator` + `:core:evaluatto` offline as `android.library` AAR, place under `extensions/extension/libs/`, `implementation(files("libs/unitto-calc.aar"))`.

**Pros:** Real Compose UI binary without Morphe discovering a second module.  
**Cons:** Must rebuild AAR when Unitto updates; packaging/ProGuard care.

### D. Installed app freeform (kept as secondary)

`app://com.sadellie.unitto/...` + One UI freeform bounds — real full app, not in-process.

## Recommendation

1. **Now:** Path A — native evaluatto-grade engine + scientific overlay as **default** calculator AP (built into the patch).  
2. **Next CI-safe step:** Path B gradle flags when Actions Kotlin/Compose versions are confirmed.  
3. **Optional:** Path D when Unitto is installed for full converter/date/BMI.  
4. WebView stays **last-resort offline cache only**, not primary.

## Author note

Unitto README discourages hard forks of the **app**. We vendor the **evaluatto algorithm** under GPL-3.0 with clear attribution, and do not rebrand the full app chrome as “Unitto”.
