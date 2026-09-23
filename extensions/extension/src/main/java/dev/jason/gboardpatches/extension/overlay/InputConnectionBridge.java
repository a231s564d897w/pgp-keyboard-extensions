package dev.jason.gboardpatches.extension.overlay;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;

/**
 * Holds the IME's current {@link InputConnection} so floating overlays
 * (Calculator, Proton Pass) can insert text without a hard reference to the service.
 *
 * Preferred update paths:
 * <ul>
 *   <li>{@link #refreshFrom(Context)} – unwraps {@link InputMethodService} from a view/context</li>
 *   <li>{@link #updateFromService(InputMethodService)} – when a service hook is available</li>
 *   <li>{@link #update(InputConnection)} – direct</li>
 * </ul>
 */
public final class InputConnectionBridge {
    private static final Object LOCK = new Object();
    private static volatile WeakReference<InputConnection> current =
            new WeakReference<>(null);
    private static volatile WeakReference<InputMethodService> serviceRef =
            new WeakReference<>(null);

    private InputConnectionBridge() {
    }

    public static void update(InputConnection connection) {
        synchronized (LOCK) {
            current = new WeakReference<>(connection);
        }
        pushToOverlays(connection);
    }

    public static void clear() {
        update(null);
    }

    public static void updateFromService(InputMethodService service) {
        if (service == null) {
            return;
        }
        synchronized (LOCK) {
            serviceRef = new WeakReference<>(service);
        }
        try {
            update(service.getCurrentInputConnection());
        } catch (Throwable t) {
            // keep previous connection
        }
    }

    /**
     * Walk ContextWrappers to find InputMethodService, then refresh the connection.
     * Safe to call from SoftKeyView context or Access Point actions.
     */
    public static InputConnection refreshFrom(Context context) {
        InputMethodService service = unwrapInputMethodService(context);
        if (service != null) {
            updateFromService(service);
            return get();
        }
        // Fall back: try last known service
        InputMethodService cached = serviceRef.get();
        if (cached != null) {
            try {
                update(cached.getCurrentInputConnection());
            } catch (Throwable ignored) {
            }
        }
        return get();
    }

    public static InputConnection get() {
        return current.get();
    }

    public static InputMethodService getService() {
        return serviceRef.get();
    }

    public static boolean commitText(CharSequence text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        InputConnection ic = get();
        if (ic == null) {
            return false;
        }
        try {
            return ic.commitText(text, 1);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void pushToOverlays(InputConnection connection) {
        try {
            dev.jason.gboardpatches.extension.calculator.GboardCalculatorOverlayHost
                    .setInputConnection(connection);
        } catch (Throwable ignored) {
        }
        try {
            dev.jason.gboardpatches.extension.protonpass.GboardProtonPassOverlayHost
                    .setInputConnection(connection);
        } catch (Throwable ignored) {
        }
    }

    static InputMethodService unwrapInputMethodService(Context context) {
        Context currentCtx = context;
        for (int depth = 0; currentCtx != null && depth < 12; depth++) {
            if (currentCtx instanceof InputMethodService) {
                return (InputMethodService) currentCtx;
            }
            if (!(currentCtx instanceof ContextWrapper)) {
                return null;
            }
            Context base = ((ContextWrapper) currentCtx).getBaseContext();
            if (base == currentCtx) {
                return null;
            }
            currentCtx = base;
        }
        return null;
    }
}
