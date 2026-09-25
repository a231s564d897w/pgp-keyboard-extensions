package dev.jason.gboardpatches.extension.overlay;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Shared helper for features that inflate a floating window above other apps
 * (scientific calculator overlay, Proton Pass floating credentials window, etc.).
 *
 * Requires the {@code android.permission.SYSTEM_ALERT_WINDOW} permission that is
 * injected into Gboard's manifest by the corresponding ManifestPatch.
 *
 * On Android 6+ the user must also grant "Display over other apps" at runtime.
 * This class checks that grant and can open the system settings page so the
 * user can enable it.
 */
public final class OverlayPermission {
    private static final String TAG = "PGP";

    /** The permission name that must appear in AndroidManifest.xml. */
    public static final String PERMISSION_SYSTEM_ALERT_WINDOW =
            "android.permission.SYSTEM_ALERT_WINDOW";

    private OverlayPermission() {
    }

    /**
     * Returns true when the current process is allowed to draw overlays.
     * On pre-M devices this is always true once the permission is in the manifest.
     */
    public static boolean canDrawOverlays(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            return Settings.canDrawOverlays(context);
        } catch (Throwable t) {
            Log.w(TAG, "Settings.canDrawOverlays failed", t);
            return false;
        }
    }

    /**
     * Opens the system "Display over other apps" settings page for this package.
     * Returns true if the settings activity was started.
     */
    public static boolean openOverlaySettings(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Unable to open overlay permission settings", t);
            // Fallback: open the general app details page
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + context.getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /**
     * Convenience: if the permission is already granted, returns true.
     * Otherwise opens the settings page and returns false.
     */
    public static boolean ensureCanDrawOverlays(Context context) {
        if (canDrawOverlays(context)) {
            return true;
        }
        openOverlaySettings(context);
        return false;
    }
}
