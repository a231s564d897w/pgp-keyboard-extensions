package dev.jason.gboardpatches.extension.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Simple on/off toggles for the Calculator and Proton Pass Access Points
 * and their floating windows.
 */
public final class OverlayFeatureSettings {
    private static final String PREFS = "gboard_patches_overlay_features";
    private static final String KEY_CALCULATOR_ENABLED = "calculator_enabled";
    private static final String KEY_PROTON_PASS_ENABLED = "proton_pass_enabled";

    private OverlayFeatureSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isCalculatorEnabled(Context context) {
        if (context == null) return false;
        return prefs(context).getBoolean(KEY_CALCULATOR_ENABLED, true);
    }

    public static void setCalculatorEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_CALCULATOR_ENABLED, enabled).apply();
    }

    public static boolean isProtonPassEnabled(Context context) {
        if (context == null) return false;
        return prefs(context).getBoolean(KEY_PROTON_PASS_ENABLED, true);
    }

    public static void setProtonPassEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_PROTON_PASS_ENABLED, enabled).apply();
    }
}
