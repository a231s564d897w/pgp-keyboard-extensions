package dev.jason.gboardpatches.patches.gboard.features.overlay

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestUsesPermission
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document

/**
 * Injects the SYSTEM_ALERT_WINDOW permission required by any feature that
 * inflates a floating window above other apps (scientific calculator overlay,
 * Proton Pass floating credentials window, etc.).
 *
 * This is the single place that adds the permission to Gboard's AndroidManifest.
 * Individual features only depend on this patch and then use OverlayPermission
 * at runtime to check / request the user grant.
 */
internal val gboardOverlayManifestPatch = resourcePatch(
    description = "Inject SYSTEM_ALERT_WINDOW permission for floating overlays (calculator, Proton Pass, etc.).",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(gboardPatchesSettingsPatch)

    finalize {
        applyOverlayManifest()
    }
}

context(context: ResourcePatchContext)
private fun applyOverlayManifest() = with(context) {
    document("AndroidManifest.xml").use(::applyGboardOverlayManifest)
}

internal fun applyGboardOverlayManifest(document: Document) {
    val manifest = document.documentElement
    OVERLAY_PERMISSIONS.forEach { permissionName ->
        ensureManifestUsesPermission(document, manifest, permissionName)
    }
}

private val OVERLAY_PERMISSIONS = listOf(
    "android.permission.SYSTEM_ALERT_WINDOW",
)
