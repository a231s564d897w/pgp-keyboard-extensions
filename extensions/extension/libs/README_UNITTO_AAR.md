# unitto-engine.aar (Path B)

Place the prebuilt Unitto **library** AAR here:

- `unitto-engine.aar` — evaluatto / math (required for Path B)
- `unitto-calc-ui.aar` — optional calculator Compose UI

Build outside this repo from sadellie/unitto with `com.android.library` only.
Then:

```kotlin
// extensions/extension/build.gradle.kts
dependencies {
    implementation(files("libs/unitto-engine.aar"))
}
```

Do not put an application module or nested Gradle project under `extensions/`.
