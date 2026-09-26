package dev.jason.gboardpatches.extension.overlay;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Shared partial-height launch for Web Search, Unitto, and Proton Pass.
 *
 * Height always follows Floating Web Search setting
 * {@code pref_floating_web_search_initial_height_percent} (50–100).
 */
public final class FloatingHeightHelper {
    private static final String TAG = "PGP";

    public static final String EXTRA_INITIAL_ACTIVITY_HEIGHT_PX =
            "androidx.browser.customtabs.extra.INITIAL_ACTIVITY_HEIGHT_PX";
    public static final String EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR =
            "androidx.browser.customtabs.extra.ACTIVITY_HEIGHT_RESIZE_BEHAVIOR";

    public static final String PREF_KEY_INITIAL_HEIGHT_PERCENT =
            "pref_floating_web_search_initial_height_percent";

    public static final int WINDOWING_MODE_FREEFORM = 5;

    private FloatingHeightHelper() {
    }

    /** Same allowed set as GboardFloatingWebSearchSettings. */
    public static int clampPercent(int percent) {
        int[] allowed = {100, 90, 80, 70, 60, 50};
        int best = 50;
        int bestDiff = Integer.MAX_VALUE;
        for (int a : allowed) {
            int d = Math.abs(a - percent);
            if (d < bestDiff) {
                bestDiff = d;
                best = a;
            }
        }
        return best;
    }

    /**
     * Read the same initial height % as Floating Web Search.
     */
    public static int initialHeightPercent(Context context) {
        if (context == null) {
            return 50;
        }
        try {
            SharedPreferences prefs = resolvePrefs(context);
            if (prefs != null) {
                Object raw = prefs.getAll().get(PREF_KEY_INITIAL_HEIGHT_PERCENT);
                int value = 50;
                if (raw instanceof Number) {
                    value = ((Number) raw).intValue();
                } else if (raw instanceof String) {
                    try {
                        value = Integer.parseInt((String) raw);
                    } catch (NumberFormatException ignored) {
                    }
                } else if (prefs.contains(PREF_KEY_INITIAL_HEIGHT_PERCENT)) {
                    value = prefs.getInt(PREF_KEY_INITIAL_HEIGHT_PERCENT, 50);
                }
                return clampPercent(value);
            }
        } catch (Throwable t) {
            Log.i(TAG, "height pref read: " + t.getMessage());
        }
        return 50;
    }

    private static SharedPreferences resolvePrefs(Context context) {
        try {
            Class<?> cls = Class.forName(
                    "dev.jason.gboardpatches.extension.settings.GboardPatchesSettings");
            Object prefs = cls.getMethod("preferences", Context.class).invoke(null, context);
            if (prefs instanceof SharedPreferences) {
                return (SharedPreferences) prefs;
            }
        } catch (Throwable ignored) {
        }
        try {
            return context.getSharedPreferences("gboard_patches_prefs", Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static int heightPx(Context context) {
        DisplayMetrics dm = metrics(context);
        int percent = initialHeightPercent(context);
        return Math.max(1, Math.round(dm.heightPixels * (percent / 100.0f)));
    }

    public static Rect bottomSheetBounds(Context context, int heightPx) {
        DisplayMetrics dm = metrics(context);
        int h = Math.min(heightPx, dm.heightPixels);
        int top = Math.max(0, dm.heightPixels - h);
        int margin = Math.max(8, dm.widthPixels / 80);
        return new Rect(margin, top, dm.widthPixels - margin, dm.heightPixels);
    }

    public static void putCustomTabHeightExtras(Intent intent, Context context) {
        if (intent == null || context == null) {
            return;
        }
        int px = heightPx(context);
        intent.putExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX, px);
        intent.putExtra(EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR, 0);
    }

    public static boolean startFreeformBottomSheet(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        Intent i = new Intent(intent);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);

        int px = heightPx(context);
        Rect bounds = bottomSheetBounds(context, px);
        putCustomTabHeightExtras(i, context);

        if (startWithLaunchWindowingMode(context, i, bounds)) {
            return true;
        }
        if (startWithBoundsOnly(context, i, bounds)) {
            return true;
        }
        if (startWithBundleExtras(context, i, bounds)) {
            return true;
        }
        try {
            context.startActivity(i);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "startActivity failed", t);
            return false;
        }
    }

    private static boolean startWithLaunchWindowingMode(Context context, Intent i, Rect bounds) {
        try {
            ActivityOptions opts = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 24) {
                opts.setLaunchBounds(bounds);
            }
            Method m = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
            m.invoke(opts, WINDOWING_MODE_FREEFORM);
            Bundle bundle = opts.toBundle();
            if (bundle != null) {
                bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM);
            }
            context.startActivity(i, bundle);
            Log.i(TAG, "freeform mode5 percent=" + initialHeightPercent(context)
                    + " bounds=" + bounds);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean startWithBoundsOnly(Context context, Intent i, Rect bounds) {
        try {
            if (Build.VERSION.SDK_INT < 24) {
                return false;
            }
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchBounds(bounds);
            context.startActivity(i, opts.toBundle());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean startWithBundleExtras(Context context, Intent i, Rect bounds) {
        try {
            ActivityOptions opts = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 24) {
                opts.setLaunchBounds(bounds);
            }
            Bundle bundle = opts.toBundle();
            if (bundle == null) {
                bundle = new Bundle();
            }
            bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM);
            context.startActivity(i, bundle);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static DisplayMetrics metrics(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm != null ? dm : new DisplayMetrics();
    }
}
