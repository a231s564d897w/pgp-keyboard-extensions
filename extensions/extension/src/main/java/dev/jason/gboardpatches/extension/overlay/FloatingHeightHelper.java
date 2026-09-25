package dev.jason.gboardpatches.extension.overlay;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * Shared partial-height launch for Floating Web Search, Unitto, and Proton Pass.
 *
 * Matches upstream Floating Web Search behavior:
 * - Custom Tabs: {@code androidx.browser.customtabs.extra.INITIAL_ACTIVITY_HEIGHT_PX}
 *   (clamped 50%–100% of screen by Chrome-family browsers)
 * - Freeform / One UI: {@link ActivityOptions#setLaunchBounds(Rect)} + windowing mode 5
 *
 * One UI 7 (Android 14) path uses launch bounds + FREEFORM windowing mode extra.
 */
public final class FloatingHeightHelper {
    private static final String TAG = "PGP";

    /** Same extra key Floating Web Search already uses. */
    public static final String EXTRA_INITIAL_ACTIVITY_HEIGHT_PX =
            "androidx.browser.customtabs.extra.INITIAL_ACTIVITY_HEIGHT_PX";
    public static final String EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR =
            "androidx.browser.customtabs.extra.ACTIVITY_HEIGHT_RESIZE_BEHAVIOR";

    /** AOSP WindowConfiguration.WINDOWING_MODE_FREEFORM */
    public static final int WINDOWING_MODE_FREEFORM = 5;

    private FloatingHeightHelper() {
    }

    /** Default height fraction when settings are unavailable (0.55 ≈ mid of CCT 50–100%). */
    public static float defaultFraction() {
        return 0.55f;
    }

    public static int heightPx(Context context) {
        return heightPx(context, defaultFraction());
    }

    public static int heightPx(Context context, float fraction) {
        DisplayMetrics dm = metrics(context);
        float f = fraction;
        if (f < 0.50f) {
            f = 0.50f; // CCT minimum
        }
        if (f > 1.0f) {
            f = 1.0f;
        }
        return Math.max(1, (int) (dm.heightPixels * f));
    }

    /**
     * Bottom-sheet style rect: full width, height from bottom of screen upward.
     * Suited for “above keyboard” freeform windows on One UI.
     */
    public static Rect bottomSheetBounds(Context context, int heightPx) {
        DisplayMetrics dm = metrics(context);
        int h = Math.min(heightPx, dm.heightPixels);
        int top = Math.max(0, dm.heightPixels - h);
        return new Rect(0, top, dm.widthPixels, dm.heightPixels);
    }

    /** Put CCT partial-height extras on an intent (Chrome + any browser that honors them). */
    public static void putCustomTabHeightExtras(Intent intent, Context context) {
        if (intent == null || context == null) {
            return;
        }
        int px = heightPx(context);
        intent.putExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX, px);
        intent.putExtra(EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR, 0);
    }

    /**
     * Start an activity with One UI / freeform bounds matching Floating Web Search height.
     * Tries: launch bounds + FREEFORM windowing mode, then plain start.
     */
    public static boolean startFreeformBottomSheet(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        Intent launch = new Intent(intent);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);

        int px = heightPx(context);
        Rect bounds = bottomSheetBounds(context, px);

        // 1) ActivityOptions.setLaunchBounds (API 24+) — One UI freeform / desktop windowing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchBounds(bounds);
                Bundle bundle = opts.toBundle();
                // AOSP / One UI windowing mode freeform
                bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM);
                // Samsung multi-window request (best-effort; ignored if unsupported)
                bundle.putInt("androidx.activity.windowingMode", WINDOWING_MODE_FREEFORM);
                context.startActivity(launch, bundle);
                return true;
            } catch (Throwable t) {
                Log.w(TAG, "freeform bounds launch", t);
            }
        }

        // 2) Plain start with adjacent flag (split / multi-window entry on some OEMs)
        try {
            context.startActivity(launch);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startActivity failed", t);
            return false;
        }
    }

    private static DisplayMetrics metrics(Context context) {
        try {
            return context.getResources().getDisplayMetrics();
        } catch (Throwable t) {
            DisplayMetrics dm = new DisplayMetrics();
            dm.widthPixels = 1080;
            dm.heightPixels = 2400;
            dm.density = 3f;
            return dm;
        }
    }
}
