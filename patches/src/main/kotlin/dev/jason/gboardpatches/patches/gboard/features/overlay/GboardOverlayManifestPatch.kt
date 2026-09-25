package dev.jason.gboardpatches.patches.gboard.features.overlay

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestUsesPermission
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document

/**
 * Injects SYSTEM_ALERT_WINDOW into Gboard's AndroidManifest so the system
 * "Appear on top" / "Display over other apps" page can list the keyboard app.
 *
 * Without this permission in the *final patched APK*, Android will not show
 * Gboard in that settings list at all.
 *
 * Must be registered via dependsOn() from an admitted public patch
 * (Custom Top Row / Floating Web Search / Simple Calculator) — see
 * GboardPatchRegistry hooks in REGISTRY_OVERLAY_HOOK.md.
 */
internal val gboardOverlayManifestPatch = resourcePatch(
    description = "Inject SYSTEM_ALERT_WINDOW so Gboard can appear in Display over other apps.",
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
