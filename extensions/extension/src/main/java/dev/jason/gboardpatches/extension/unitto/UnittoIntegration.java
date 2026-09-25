package dev.jason.gboardpatches.extension.unitto;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import dev.jason.gboardpatches.extension.overlay.FloatingHeightHelper;

/**
 * Optional bridge to the installed Unitto app (https://github.com/sadellie/unitto).
 *
 * The calculator Access Point must NOT call this for its primary path.
 * Use {@link #openAppIfInstalled} only as an explicit secondary action.
 */
public final class UnittoIntegration {
    private static final String TAG = "PGP";

    public static final String PACKAGE_NAME = "com.sadellie.unitto";
    public static final String PACKAGE_DEBUG = "com.sadellie.unitto.debug";
    public static final String REPO_URL = "https://github.com/sadellie/unitto";
    public static final String WEB_APP_URL = "https://sadellie.github.io/unitto/app";

    private static final String[] CANDIDATE_PACKAGES = {
            PACKAGE_NAME,
            PACKAGE_DEBUG,
    };

    private UnittoIntegration() {
    }

    public static String installedPackage(Context context) {
        if (context == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        for (String pkg : CANDIDATE_PACKAGES) {
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

    /** Launch Unitto only if already installed. Returns false otherwise (no store redirect). */
    public static boolean openAppIfInstalled(Context context) {
        if (context == null) {
            return false;
        }
        String pkg = installedPackage(context);
        if (pkg == null) {
            Log.i(TAG, "Unitto not installed");
            return false;
        }
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                return false;
            }
            // Same partial height as Floating Web Search (One UI freeform + CCT extras).
            FloatingHeightHelper.putCustomTabHeightExtras(launch, context);
            return FloatingHeightHelper.startFreeformBottomSheet(context, launch);
        } catch (Throwable t) {
            Log.w(TAG, "Unitto launch failed", t);
            return false;
        }
    }

    /** @deprecated Prefer {@link #openAppIfInstalled}; do not open stores from keyboard AP. */
    @Deprecated
    public static boolean openApp(Context context) {
        return openAppIfInstalled(context);
    }

    public static boolean openWebApp(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_APP_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
