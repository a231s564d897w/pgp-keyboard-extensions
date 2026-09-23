# Unitto Compose integration (required)

**Upstream:** https://github.com/sadellie/unitto · `com.sadellie.unitto` · GPL-3.0

## Status

| Layer | Status |
|-------|--------|
| Floating host `UnittoComposeOverlayHost` | **Done** |
| Official Unitto **web** full product in overlay WebView | **Done** (default when Compose module not packaged) |
| Jetpack Compose calculator surface `UnittoComposeContent` | **Done** (Kotlin source + Gradle module) |
| Access Point opens Compose/Web host (not app-only) | **Done** |
| Composite Gradle pull of Unitto `:feature:*` modules | Documented – enable in host build |

## Runtime priority

1. If `UnittoComposeContent` is on the classpath → in-process Compose keypad
2. Else → load `https://sadellie.github.io/unitto/app` in overlay WebView (full Unitto)
3. Title bar **App** → native Unitto package / install
4. Lightweight `GboardCalculatorOverlayHost` remains offline fallback if overlay permission or web fails

## Enable in-process Compose in the host build

```kotlin
// settings.gradle.kts
include(":unitto-compose")
project(":unitto-compose").projectDir =
    file("path/to/gboard-patches-impl/unitto/compose")

// optional: composite Unitto itself
// includeBuild("../unitto") { ... }

// extension build.gradle.kts
implementation(project(":unitto-compose"))
```

## Optional: vendor Unitto feature modules

```text
git clone https://github.com/sadellie/unitto
// include feature/calculator, feature/converter, … under GPL-3.0
// require corresponding source with any redistributed binary
```

## Legal

Embedding Unitto source requires GPL-3.0 and corresponding source.  
WebView loading the public web app does not vendor their code into the APK.  
Our Compose calculator uses the existing `GboardCalculatorEngine` for evaluation.


## Offline / cache (fallback)

`UnittoWebCache`:
- WebView disk cache + offline `LOAD_CACHE_ELSE_NETWORK`
- UI state: calculator, converter, date fields
- Title bar **Cache** clears web cache

Packaged path: `extensions/unitto-compose` (see `GRADLE_INCLUDE_UNITTO_COMPOSE.md`).
