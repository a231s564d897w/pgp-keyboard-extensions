package dev.jason.gboardpatches.extension.unitto;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import dev.jason.gboardpatches.extension.overlay.FloatingHeightHelper;

/**
 * Real Unitto integration (https://github.com/sadellie/unitto).
 *
 * Paths:
 * 1. Installed app via deep link app://com.sadellie.unitto/... at freeform height.
 * 2. Official Unitto web product embedded by UnittoComposeOverlayHost.
 * 3. Launcher intent fallback.
 *
 * Manifest (upstream): scheme "app", host "com.sadellie.unitto".
 */
public final class UnittoIntegration {
    private static final String TAG = "PGP";

    public static final String PACKAGE_NAME = "com.sadellie.unitto";
    public static final String PACKAGE_DEBUG = "com.sadellie.unitto.debug";
    public static final String REPO_URL = "https://github.com/sadellie/unitto";
    public static final String WEB_APP_URL = "https://sadellie.github.io/unitto/app";
    public static final String DEEP_LINK_SCHEME = "app";
    public static final String DEEP_LINK_HOST = "com.sadellie.unitto";

    private static final String[] CANDIDATE_PACKAGES = {
            PACKAGE_NAME,
            PACKAGE_DEBUG,
    };

    public enum Feature {
        CALCULATOR("calculator", "/calculator", "#/calculator"),
        CONVERTER("converter", "/converter", "#/converter"),
        DATE("date", "/date", "#/date"),
        TIMEZONE("timezone", "/timezone", "#/timezone"),
        BODY_MASS("bodymass", "/bodymass", "#/bodymass"),
        HOME("home", "", "");

        public final String id;
        public final String deepPath;
        public final String webHash;

        Feature(String id, String deepPath, String webHash) {
            this.id = id;
            this.deepPath = deepPath;
            this.webHash = webHash;
        }

        public static Feature fromName(String name) {
            if (name == null || name.isEmpty()) {
                return CALCULATOR;
            }
            for (Feature f : values()) {
                if (f.id.equalsIgnoreCase(name) || f.name().equalsIgnoreCase(name)) {
                    return f;
                }
            }
            return CALCULATOR;
        }
    }

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

    public static Uri deepLinkUri(Feature feature) {
        Feature f = feature != null ? feature : Feature.HOME;
        String path = f.deepPath != null ? f.deepPath : "";
        if (path.isEmpty()) {
            return Uri.parse(DEEP_LINK_SCHEME + "://" + DEEP_LINK_HOST);
        }
        return Uri.parse(DEEP_LINK_SCHEME + "://" + DEEP_LINK_HOST + path);
    }

    public static String webUrlFor(Feature feature) {
        Feature f = feature != null ? feature : Feature.HOME;
        if (f.webHash == null || f.webHash.isEmpty()) {
            return WEB_APP_URL;
        }
        return WEB_APP_URL + f.webHash;
    }

    public static boolean openFeature(Context context, Feature feature) {
        if (context == null) {
            return false;
        }
        Feature f = feature != null ? feature : Feature.CALCULATOR;
        String pkg = installedPackage(context);
        if (pkg == null) {
            Log.i(TAG, "Unitto not installed; feature=" + f.id);
            return false;
        }

        try {
            Intent deep = new Intent(Intent.ACTION_VIEW, deepLinkUri(f));
            deep.setPackage(pkg);
            deep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            FloatingHeightHelper.putCustomTabHeightExtras(deep, context);
            if (FloatingHeightHelper.startFreeformBottomSheet(context, deep)) {
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "deep link failed feature=" + f.id, t);
        }

        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                FloatingHeightHelper.putCustomTabHeightExtras(launch, context);
                return FloatingHeightHelper.startFreeformBottomSheet(context, launch);
            }
        } catch (Throwable t) {
            Log.w(TAG, "launcher failed", t);
        }
        return false;
    }

    public static boolean openCalculator(Context context) {
        return openFeature(context, Feature.CALCULATOR);
    }

    public static boolean openConverter(Context context) {
        return openFeature(context, Feature.CONVERTER);
    }

    public static boolean openDate(Context context) {
        return openFeature(context, Feature.DATE);
    }

    public static boolean openAppIfInstalled(Context context) {
        return openFeature(context, Feature.HOME);
    }

    @Deprecated
    public static boolean openApp(Context context) {
        return openAppIfInstalled(context);
    }

    public static boolean openWebApp(Context context) {
        return openWebApp(context, Feature.HOME);
    }

    public static boolean openWebApp(Context context, Feature feature) {
        if (context == null) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrlFor(feature)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
