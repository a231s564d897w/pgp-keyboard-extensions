package dev.jason.gboardpatches.extension.overlay;

import android.content.Context;

/**
 * Describes settings rows for Calculator / Proton Pass toggles.
 * Wired into the host settings screen by the feature orchestrator when available.
 */
public final class OverlaySettingsRows {
    private OverlaySettingsRows() {
    }

    public static boolean isCalculatorEnabled(Context context) {
        return OverlayFeatureSettings.isCalculatorEnabled(context);
    }

    public static void setCalculatorEnabled(Context context, boolean enabled) {
        OverlayFeatureSettings.setCalculatorEnabled(context, enabled);
        if (!enabled) {
            try {
                dev.jason.gboardpatches.extension.calculator.GboardCalculatorOverlayHost
                        .hide(context);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isProtonPassEnabled(Context context) {
        return OverlayFeatureSettings.isProtonPassEnabled(context);
    }

    public static void setProtonPassEnabled(Context context, boolean enabled) {
        OverlayFeatureSettings.setProtonPassEnabled(context, enabled);
        if (!enabled) {
            try {
                dev.jason.gboardpatches.extension.protonpass.GboardProtonPassOverlayHost
                        .hide(context);
            } catch (Throwable ignored) {
            }
        }
    }
}
