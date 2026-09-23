# Merge guide – `gboard-patches-impl` → main Gboard-patches tree

Copy paths under `artifacts/gboard-patches-impl/` into the matching packages of the
Morphe extension modules (typically `extensions/extension/src/main/java/...`).

## 1. Custom Key Row (`toprowswipe/`)
Copy all new/changed files. Keep existing QuickJs / settings classes.
Ensure `GboardTopRowSwipeSettingsFeature` and `GboardTopRowSwipeRuntime` from this
tree replace the older versions (or merge carefully).

## 2. Access Points (`accesspoint/`)
Merge `GboardAccessPointContributions1803Runtime` registrations for:
- Calculator
- Proton Pass
- Preset (`custom_key_row_preset` + per-id tokens)

## 3. Overlay permission (`patches/` + `overlay/`)
- Apply `GboardOverlayManifestPatch.kt` (SYSTEM_ALERT_WINDOW)
- Ship `OverlayPermission`, `InputConnectionBridge`, `ImeInputHooks`, `OverlayFeatureSettings`

## 4. Calculator (`calculator/`)
- Overlay host + engine + Access Point + **LifecycleRuntime** (must keep ABI used by
  existing `GboardCalculatorLifecyclePatch`)

## 5. Proton Pass (`protonpass/`)
- Overlay host, Access Point, Autofill bridge/service, `res/xml/gboard_patches_autofill_service.xml`
- Merge `AndroidManifest.xml` service entry into the packaged extension manifest

## 6. Unitto Compose (required)

```kotlin
// settings.gradle.kts (root)
include(":unitto-compose")
project(":unitto-compose").projectDir =
    file("extensions/unitto-compose")  // copy unitto/compose here

// extensions/extension/build.gradle.kts
dependencies {
    implementation(project(":unitto-compose"))
}
```

Copy:
- `unitto/UnittoComposeOverlayHost.java` → extension java tree
- `unitto/UnittoIntegration.java` → extension java tree
- `unitto/compose/` → `extensions/unitto-compose/` (library module)

Without the library on the classpath, the host still embeds the **official Unitto web app**
in a WebView so the full product remains available.


## 7. Debug (`debug/`)
- `GboardDebugPanel`

## 8. Verify
- Access Points menu shows Calculator, Proton Pass, Key Row (+ named presets)
- Settings → Custom Key Row → visual editor + Manage Presets
- Overlay permission prompt when opening calculator / Proton Pass first time
- Lifecycle: typing in a field then Insert from calculator still works

## License notes
- Unitto is GPL-3.0; launching the app is fine. Embedding Compose modules later
  requires GPL-3.0 compliance for the distributed patch.
