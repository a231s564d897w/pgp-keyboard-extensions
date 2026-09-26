package dev.jason.gboardpatches.extension.protonpass;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import dev.jason.gboardpatches.extension.overlay.FloatingHeightHelper;

/**
 * Launches the real Proton Pass app in freeform (~40% height) above the keyboard.
 * No mock entry lists — only the installed Proton Pass package.
 *
 * Packages: proton.android.pass (Play) · proton.android.pass.fdroid (F-Droid)
 */
public final class ProtonPassWindowedLauncher {
    private static final String TAG = "PGP";

    public static final String[] PACKAGES = {
            "proton.android.pass",
            "proton.android.pass.fdroid",
            "me.proton.pass.android",
    };

    private ProtonPassWindowedLauncher() {
    }

    public static String installedPackage(Context context) {
        if (context == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        for (String pkg : PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean isInstalled(Context context) {
        return installedPackage(context) != null;
    }

    /**
     * Open Proton Pass as a freeform window at FloatingHeightHelper height.
     * Returns false only when Pass is not installed.
     */
    public static boolean openWindowedAboveKeyboard(Context context) {
        return launch(context);
    }

    public static boolean launch(Context context) {
        if (context == null) {
            return false;
        }
        String pkg = installedPackage(context);
        if (pkg == null) {
            Log.i(TAG, "Proton Pass not installed");
            try {
                Toast.makeText(context, "Install Proton Pass first", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
            return false;
        }
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setPackage(pkg);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            boolean ok = FloatingHeightHelper.startFreeformBottomSheet(context, launch);
            Log.i(TAG, "Proton Pass freeform launch pkg=" + pkg + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "Proton Pass launch failed", t);
            return false;
        }
    }
}
