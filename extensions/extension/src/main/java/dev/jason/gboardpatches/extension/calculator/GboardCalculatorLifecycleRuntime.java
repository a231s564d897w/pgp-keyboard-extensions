package dev.jason.gboardpatches.extension.calculator;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.view.View;

import dev.jason.gboardpatches.extension.overlay.ImeInputHooks;
import dev.jason.gboardpatches.extension.overlay.InputConnectionBridge;

/**
 * Patched-APK adapter between lifecycle delegates, preferences, and runtime.
 *
 * Also keeps {@link InputConnectionBridge} fresh for floating overlays
 * (Calculator Access Point, Proton Pass, Unitto hub Insert).
 */
public final class GboardCalculatorLifecycleRuntime {
    private static final String PREF_FILE = "gboard_patches_settings";
    private static final String PREF_KEY_ENABLED = "pref_simple_calculator_enabled";
    private static final boolean DEFAULT_ENABLED = false;

    private GboardCalculatorLifecycleRuntime() {
    }

    public static void onInputViewStarted(Object receiver) {
        boolean enabled = booleanValue(() -> readEnabled(receiver), false);
        run(() -> {
            // Existing suggestion-calculator path (if GboardCalculatorRuntime is on classpath)
            try {
                Class<?> runtime = Class.forName(
                        "dev.jason.gboardpatches.extension.calculator.GboardCalculatorRuntime");
                runtime.getMethod("onInputViewStarted", Object.class, boolean.class)
                        .invoke(null, receiver, enabled);
            } catch (Throwable ignored) {
                // Overlay-only builds may not ship the suggestion runtime
            }
            // Always refresh InputConnection for floating overlays
            if (receiver instanceof InputMethodService service) {
                ImeInputHooks.onStartInputView(service, null, false);
                try {
                    dev.jason.gboardpatches.extension.protonpass.ProtonPassAutofillBridge
                            .onEditorInfo(service.getCurrentInputEditorInfo());
                } catch (Throwable ignored) {
                }
            } else if (receiver instanceof Context context) {
                InputConnectionBridge.refreshFrom(context);
            }
            try {
                dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                        "IME", "onInputViewStarted enabled=" + enabled);
            } catch (Throwable ignored) {
            }
        });
    }

    public static void onSelectionUpdated(Object receiver, View inputView,
            int selectionStart, int selectionEnd) {
        run(() -> {
            try {
                Class<?> runtime = Class.forName(
                        "dev.jason.gboardpatches.extension.calculator.GboardCalculatorRuntime");
                runtime.getMethod("onSelectionUpdated", Object.class, View.class, int.class,
                        int.class).invoke(null, receiver, inputView, selectionStart, selectionEnd);
            } catch (Throwable ignored) {
            }
            if (receiver instanceof InputMethodService service) {
                ImeInputHooks.onUpdateSelection(service);
            }
        });
    }

    public static void onInputWindowHidden() {
        run(() -> {
            try {
                Class<?> runtime = Class.forName(
                        "dev.jason.gboardpatches.extension.calculator.GboardCalculatorRuntime");
                runtime.getMethod("onInputWindowHidden").invoke(null);
            } catch (Throwable ignored) {
            }
            ImeInputHooks.onFinishInput(null);
            InputConnectionBridge.clear();
            // Overlays intentionally stay until the user closes them or toggles Access Point.
            // InputConnection is cleared above so Insert becomes a no-op.
            try {
                dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                        "IME", "onInputWindowHidden");
            } catch (Throwable ignored) {
            }
        });
    }

    private static boolean readEnabled(Object receiver) {
        if (!(receiver instanceof Context context)) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        SharedPreferences preferences = lookupContext.getSharedPreferences(
                PREF_FILE, Context.MODE_PRIVATE);
        Object raw = preferences.getAll().get(PREF_KEY_ENABLED);
        if (raw instanceof Boolean value) {
            return value.booleanValue();
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return DEFAULT_ENABLED;
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (Throwable ignored) {
        }
    }

    private static boolean booleanValue(BooleanSupplier supplier, boolean fallback) {
        try {
            return supplier.getAsBoolean();
        } catch (Throwable t) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
