package dev.jason.gboardpatches.extension.overlay;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * Entry points intended for Morphe / runtime hooks on the IME service.
 *
 * Wire these from patches such as:
 * - after onStartInput / onStartInputView
 * - after onFinishInput
 * - after updateInputView
 *
 * Example:
 * <pre>
 *   public static void afterStartInput(InputMethodService service,
 *           EditorInfo info, boolean restarting) {
 *       ImeInputHooks.onStartInput(service, info, restarting);
 *   }
 * </pre>
 */
public final class ImeInputHooks {
    private ImeInputHooks() {
    }

    public static void onStartInput(InputMethodService service,
            EditorInfo info, boolean restarting) {
        if (service == null) {
            return;
        }
        try {
            InputConnectionBridge.updateFromService(service);
            try {
                dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                        "IME",
                        "onStartInput restarting=" + restarting
                                + (info != null ? " package=" + info.packageName : ""));
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onStartInputView(InputMethodService service,
            EditorInfo info, boolean restarting) {
        onStartInput(service, info, restarting);
    }

    public static void onFinishInput(InputMethodService service) {
        try {
            InputConnectionBridge.clear();
        } catch (Throwable ignored) {
        }
    }

    public static void onUpdateSelection(InputMethodService service) {
        if (service == null) {
            return;
        }
        try {
            InputConnectionBridge.updateFromService(service);
        } catch (Throwable ignored) {
        }
    }

    /** Manual refresh from any runtime that has a service reference. */
    public static void refresh(InputMethodService service) {
        InputConnectionBridge.updateFromService(service);
    }

    public static InputConnection currentConnection() {
        return InputConnectionBridge.get();
    }
}
