package dev.jason.gboardpatches.extension.protonpass;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import dev.jason.gboardpatches.extension.overlay.FloatingHeightHelper;

/**
 * Opens the real Proton Pass application in a freeform / bounded window above the
 * keyboard when the device supports it, and always shows a small circular control
 * (top-right) to open Pass full-screen.
 *
 * Android cannot re-parent Pass's views into Gboard. Freeform launch bounds are the
 * supported way to show another app as a window. Devices without freeform still get
 * Pass launched normally + the bubble control.
 */
public final class ProtonPassWindowedLauncher {
    private static final String TAG = "PGP";
    private static final String[] PACKAGES = {
            "proton.android.pass",
            "proton.android.pass.fdroid",
    };

    private static View sBubble;
    private static boolean sBubbleShowing;

    private ProtonPassWindowedLauncher() {
    }

    public static String findInstalledPackage(Context context) {
        if (context == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        for (String pkg : PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean openWindowedAboveKeyboard(Context context) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        String pkg = findInstalledPackage(app);
        if (pkg == null) {
            Log.i(TAG, "Proton Pass not installed");
            return false;
        }
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            return false;
        }
        boolean started = FloatingHeightHelper.startFreeformBottomSheet(app, launch);
        if (started) {
            showFullAppBubble(app, pkg);
        }
        return started;
    }

    /** Small circle top-right: open Pass full-screen (no bounds). */
    public static void showFullAppBubble(Context app, String pkg) {
        if (app == null || sBubbleShowing) {
            return;
        }
        try {
            FrameLayout bubble = new FrameLayout(app);
            bubble.setBackgroundColor(0xFF6D4AFF);
            TextView label = new TextView(app);
            label.setText("P");
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(16f);
            label.setGravity(Gravity.CENTER);
            bubble.addView(label, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            final String packageName = pkg;
            bubble.setOnClickListener(v -> {
                try {
                    Intent full = app.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (full != null) {
                        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        app.startActivity(full);
                    }
                } catch (Throwable ignored) {
                }
                hideBubble(app);
            });

            int size = dp(app, 48);
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            // Prefer IME panel if token known
            WindowManager.LayoutParams lp;
            try {
                lp = dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                        .panelParams(app, size, size, Gravity.TOP | Gravity.END);
                lp.x = dp(app, 12);
                lp.y = dp(app, 12);
            } catch (Throwable t) {
                lp = new WindowManager.LayoutParams(
                        size, size, type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.x = dp(app, 12);
                lp.y = dp(app, 12);
            }
            if (!dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                    .addView(app, bubble, lp)) {
                WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    wm.addView(bubble, lp);
                } else {
                    return;
                }
            }
            sBubble = bubble;
            sBubbleShowing = true;
        } catch (Throwable t) {
            Log.w(TAG, "Bubble failed", t);
        }
    }

    public static void hideBubble(Context context) {
        if (!sBubbleShowing || sBubble == null) {
            return;
        }
        try {
            Context app = context != null && context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                    .removeView(app, sBubble);
        } catch (Throwable ignored) {
            try {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    wm.removeView(sBubble);
                }
            } catch (Throwable ignored2) {
            }
        }
        sBubble = null;
        sBubbleShowing = false;
    }

    private static int estimateKeyboardHeight(Context context) {
        // Typical phone keyboard ~0.35 of screen; used only for freeform top edge.
        try {
            return (int) (context.getResources().getDisplayMetrics().heightPixels * 0.35f);
        } catch (Throwable t) {
            return 400;
        }
    }

    private static int dp(Context context, int value) {
        float d = context.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
