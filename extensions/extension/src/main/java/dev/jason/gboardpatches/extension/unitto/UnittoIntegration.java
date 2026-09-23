package dev.jason.gboardpatches.extension.unitto;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

/**
 * Integration surface for the original Unitto app (sadellie/unitto).
 *
 * Upstream is more actively maintained than the NumberHub fork.
 * Package: {@code com.sadellie.unitto} · GPL-3.0-only
 * Repo: https://github.com/sadellie/unitto
 * Web: https://sadellie.github.io/unitto/app
 *
 * Phase 1: detect install, launch full app, open install page, feature tokens
 * for a future Compose embed. Phase 2: ComposeView host of Unitto feature modules.
 */
public final class UnittoIntegration {
    private static final String TAG = "PGP";

    public static final String PACKAGE_NAME = "com.sadellie.unitto";
    public static final String REPO_URL = "https://github.com/sadellie/unitto";
    public static final String WEB_APP_URL = "https://sadellie.github.io/unitto/app";
    public static final String FDROID_URL = "https://f-droid.org/packages/com.sadellie.unitto/";
    public static final String PLAY_URL =
            "https://play.google.com/store/apps/details?id=com.sadellie.unitto";
    public static final String LICENSE = "GPL-3.0-only";

    public enum Feature {
        CALCULATOR("calculator", "Calculator"),
        CONVERTER("converter", "Unit converter"),
        DATE("date", "Date calculator"),
        TIMEZONE("timezone", "Time zones"),
        BODY_MASS("bodymass", "Body mass"),
        SETTINGS("settings", "Unitto settings");

        public final String moduleId;
        public final String label;

        Feature(String moduleId, String label) {
            this.moduleId = moduleId;
            this.label = label;
        }
    }

    private UnittoIntegration() {
    }

    public static boolean isInstalled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Launch Unitto main UI (full product experience). */
    public static boolean openApp(Context context) {
        if (context == null) {
            return false;
        }
        try {
            if (!isInstalled(context)) {
                return openInstallPage(context);
            }
            Intent launch = context.getPackageManager()
                    .getLaunchIntentForPackage(PACKAGE_NAME);
            if (launch == null) {
                return openInstallPage(context);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(launch);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Unitto openApp failed", t);
            return openInstallPage(context);
        }
    }

    /**
     * Open Unitto for a logical feature. Upstream may not expose stable deep links
     * for every tool; we launch the app and leave navigation to Unitto for now.
     * Feature tokens are kept so a later Compose embed can route precisely.
     */
    public static boolean openFeature(Context context, Feature feature) {
        if (context == null) {
            return false;
        }
        // Prefer full app until deep-link routes are confirmed in upstream
        return openApp(context);
    }

    public static boolean openInstallPage(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent fdroid = new Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_URL));
            fdroid.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fdroid);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Intent market = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + PACKAGE_NAME));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(market);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(web);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Unitto install page failed", t);
            return false;
        }
    }

    public static boolean openWebApp(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_APP_URL));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(web);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean openSourceRepo(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(web);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
