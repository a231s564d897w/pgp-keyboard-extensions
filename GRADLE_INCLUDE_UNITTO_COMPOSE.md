# Apply Unitto Compose module to main Gboard-patches repo

## 1. Copy module
```bash
cp -R artifacts/gboard-patches-impl/extensions/unitto-compose \
      <repo>/extensions/unitto-compose
```

Also copy Java hosts into the main extension sources:
```bash
# into extensions/extension/src/main/java/.../unitto/
UnittoComposeOverlayHost.java
UnittoIntegration.java
UnittoWebCache.java
```

## 2. settings.gradle.kts
Append:
```kotlin
include(":extensions:unitto-compose")
// If the project uses flat names instead:
// include(":unitto-compose")
// project(":unitto-compose").projectDir = file("extensions/unitto-compose")
```

## 3. extensions/extension/build.gradle.kts
Inside `dependencies { }`:
```kotlin
implementation(project(":extensions:unitto-compose"))
```

Ensure the extension (or app) applies Kotlin + Compose if not already:
```kotlin
// root or extension plugins as required by Morphe Android setup
```

## 4. Verify
- Build patched APK
- Open Calculator Access Point → floating window
- Footer should show **Compose module** when the class loads
- Without the module, footer shows **Unitto web (cached fallback)**
