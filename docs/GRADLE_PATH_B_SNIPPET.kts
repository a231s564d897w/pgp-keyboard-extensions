// Paste into extensions/extension/build.gradle.kts when unitto-engine.aar is present.
// PGP Path B — do not apply com.android.application here.

dependencies {
    implementation(files("libs/unitto-engine.aar"))
    // Uncomment when unitto-calc-ui.aar ships:
    // implementation(files("libs/unitto-calc-ui.aar"))
    // implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    // implementation("androidx.compose.ui:ui")
    // implementation("androidx.compose.ui:ui-tooling-preview")
    // implementation("androidx.compose.material3:material3")
}

// android {
//     buildFeatures { compose = true }
// }
