package dev.jason.gboardpatches.extension.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Shared window params for floating panels from inside Gboard.
 *
 * Prefer attaching to the IME window token (TYPE_APPLICATION_PANEL) so the
 * panel works without a separate "Appear on top" grant. Fall back to
 * SYSTEM_ALERT_WINDOW when no token is available.
 */
public final class OverlayWindowHelper {
    private static final String TAG = "PGP";

    private static volatile IBinder sImeToken;
    private static volatile View sAnchorView;

    private OverlayWindowHelper() {
    }

    /** Call from SoftKey / input-view hooks so overlays can attach to the IME. */
    public static void rememberImeView(View keyboardView) {
        if (keyboardView == null) {
            return;
        }
        try {
            IBinder token = keyboardView.getWindowToken();
            if (token != null) {
                sImeToken = token;
                sAnchorView = keyboardView;
            }
        } catch (Throwable ignored) {
        }
    }

    public static IBinder imeToken() {
        return sImeToken;
    }

    public static View anchorView() {
        return sAnchorView;
    }

    /**
     * Build layout params for a floating panel.
     * width/height in px; gravity typically CENTER or TOP|CENTER_HORIZONTAL.
     */
    public static WindowManager.LayoutParams panelParams(
            Context context, int widthPx, int heightPx, int gravity) {
        WindowManager.LayoutParams lp;
        IBinder token = sImeToken;
        if (token != null) {
            lp = new WindowManager.LayoutParams(
                    widthPx,
                    heightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            lp.token = token;
        } else {
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            lp = new WindowManager.LayoutParams(
                    widthPx,
                    heightPx,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
        }
        lp.gravity = gravity != 0 ? gravity : Gravity.CENTER;
        return lp;
    }

    /**
     * Add a view. Tries IME-attached panel first, then system overlay.
     * Returns false if both fail (and may open overlay settings).
     */
    public static boolean addView(Context context, View content, WindowManager.LayoutParams lp) {
        if (context == null || content == null || lp == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return false;
        }
        try {
            wm.addView(content, lp);
            return true;
        } catch (Throwable first) {
            Log.w(TAG, "addView primary failed type=" + lp.type, first);
        }
        // Retry with system overlay if we had used panel, or vice versa
        try {
            if (lp.type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL) {
                if (!OverlayPermission.canDrawOverlays(app)) {
                    Toast.makeText(app,
                            "Enable Appear on top for Gboard (or open keyboard once first)",
                            Toast.LENGTH_LONG).show();
                    OverlayPermission.openOverlaySettings(app);
                    return false;
                }
                int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
                lp.type = type;
                lp.token = null;
                wm.addView(content, lp);
                return true;
            }
        } catch (Throwable second) {
            Log.e(TAG, "addView fallback failed", second);
            Toast.makeText(app, "Could not open floating window", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    public static void removeView(Context context, View content) {
        if (context == null || content == null) {
            return;
        }
        try {
            Context app = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.removeView(content);
            }
        } catch (Throwable t) {
            Log.w(TAG, "removeView failed", t);
        }
    }

    /**
     * Panel fixed just above the IME, full width, {@code heightFraction} of screen
     * (e.g. 0.40f = 40%). Used for calculator / Pass / web floating panels so they
     * sit flush on the keyboard with no gap.
     */
    public static WindowManager.LayoutParams aboveKeyboardParams(
            Context context, float heightFraction) {
        if (context == null) {
            context = null;
        }
        int screenH;
        int screenW;
        try {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            screenH = dm.heightPixels;
            screenW = dm.widthPixels;
        } catch (Throwable t) {
            screenH = 1200;
            screenW = 1080;
        }
        float f = heightFraction;
        if (f < 0.20f) f = 0.20f;
        if (f > 0.70f) f = 0.70f;
        int heightPx = Math.max(200, (int) (screenH * f));
        WindowManager.LayoutParams lp = panelParams(
                context, screenW, heightPx, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.y = 0; // flush with top of IME when TYPE_APPLICATION_PANEL + IME token
        lp.x = 0;
        return lp;
    }

}
