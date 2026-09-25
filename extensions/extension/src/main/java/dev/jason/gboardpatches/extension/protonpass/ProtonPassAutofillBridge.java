package dev.jason.gboardpatches.extension.protonpass;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between Autofill / password-manager data and the floating Proton Pass overlay.
 *
 * Phase 1: accept credential lists from any caller (settings demo, future AutofillService,
 * deep links) and push them into {@link GboardProtonPassOverlayHost}.
 *
 * Phase 2: hook a real {@code AutofillService} or Proton Pass SDK callbacks when available.
 * This class does not implement AutofillService itself (that belongs in a dedicated
 * component with its own manifest entry).
 */
public final class ProtonPassAutofillBridge {
    private static final String TAG = "PGP";

    private static volatile String lastPackage;
    private static volatile long lastUpdatedAtMs;

    private ProtonPassAutofillBridge() {
    }

    /** Replace overlay entries and remember the source package. */
    public static void publishEntries(Context context, String sourcePackage,
            List<GboardProtonPassOverlayHost.Entry> entries) {
        lastPackage = sourcePackage;
        lastUpdatedAtMs = System.currentTimeMillis();
        List<GboardProtonPassOverlayHost.Entry> list =
                entries != null ? entries : new ArrayList<>();
        GboardProtonPassOverlayHost.setEntries(list);
        if (context != null) {
            ProtonPassEntryStore.save(context, list);
        }
        try {
            dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                    "ProtonPass",
                    "publishEntries pkg=" + sourcePackage
                            + " count=" + (entries != null ? entries.size() : 0));
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "ProtonPass entries published: "
                + (entries != null ? entries.size() : 0)
                + " for " + sourcePackage);
    }

    /**
     * Best-effort hint from the current editor (web domain / package).
     * Used later to filter vault items; for now only logged.
     */
    public static void onEditorInfo(EditorInfo info) {
        if (info == null) {
            return;
        }
        String pkg = info.packageName;
        String hint = info.hintText != null ? info.hintText.toString() : null;
        lastPackage = pkg;
        try {
            dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                    "ProtonPass", "editor pkg=" + pkg + " hint=" + hint);
        } catch (Throwable ignored) {
        }
    }

    public static String lastSourcePackage() {
        return lastPackage;
    }

    public static long lastUpdatedAtMs() {
        return lastUpdatedAtMs;
    }

    /** Clear cached credentials (e.g. on lock / input window hidden). */
    public static void clear() {
        GboardProtonPassOverlayHost.setEntries(new ArrayList<>());
        // clear() may not have context; host clear handles store when possible
        lastPackage = null;
        lastUpdatedAtMs = 0L;
    }
}
