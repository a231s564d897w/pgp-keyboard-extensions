/**
 * Optional standalone RVE-style packaging for Unitto Compose surfaces.
 * Prefer including :unitto-compose as a library dependency of extensions/extension.
 */
extension {
    name = "extensions/gboard-unitto-compose.rve"
}

android {
    namespace = "dev.jason.gboardpatches.unitto.compose"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
}
