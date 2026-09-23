package dev.jason.gboardpatches.extension.debug;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.jason.gboardpatches.extension.overlay.OverlayPermission;

/**
 * In-app debugging panel (floating window).
 * Collects ring-buffered log lines from features and shows them without ADB.
 * No external wording; plain "Debug" title.
 */
public final class GboardDebugPanel {
    private static final String TAG = "PGP";
    private static final int MAX_LINES = 200;

    private static final List<String> BUFFER = new ArrayList<>();
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View sOverlayView;
    private static TextView sLogView;
    private static boolean sShowing;

    private GboardDebugPanel() {
    }

    public static void log(String source, String message) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                + " [" + (source != null ? source : "?") + "] "
                + (message != null ? message : "");
        synchronized (LOCK) {
            BUFFER.add(line);
            while (BUFFER.size() > MAX_LINES) {
                BUFFER.remove(0);
            }
        }
        Log.d(TAG, line);
        MAIN.post(() -> {
            if (sLogView != null) {
                sLogView.setText(snapshot());
            }
        });
    }

    public static String snapshot() {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            for (String line : BUFFER) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            BUFFER.clear();
        }
        MAIN.post(() -> {
            if (sLogView != null) {
                sLogView.setText("");
            }
        });
    }

    public static boolean show(Context context) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (!OverlayPermission.ensureCanDrawOverlays(app)) {
            log("Debug", "Overlay permission missing");
            return false;
        }
        if (sShowing) {
            return true;
        }
        try {
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return false;
            }
            View content = build(app);
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(app, 320),
                    dp(app, 400),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.y = dp(app, 48);
            wm.addView(content, lp);
            sOverlayView = content;
            sShowing = true;
            log("Debug", "Panel opened");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Debug panel failed", t);
            return false;
        }
    }

    public static void runSelfTest() {
        try {
            GboardPatchesSelfTest.runAll();
        } catch (Throwable t) {
            log("SelfTest", "crash: " + t.getMessage());
        }
    }

    public static void hide(Context context) {
        if (!sShowing || sOverlayView == null) {
            return;
        }
        try {
            Context app = context != null && context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            if (app == null) {
                return;
            }
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.removeView(sOverlayView);
            }
        } catch (Throwable ignored) {
        } finally {
            sOverlayView = null;
            sLogView = null;
            sShowing = false;
        }
    }

    public static boolean isShowing() {
        return sShowing;
    }

    private static View build(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0000000);
        bg.setCornerRadius(dp(context, 12));
        bg.setStroke(dp(context, 1), 0xFF3A3A3C);
        root.setBackground(bg);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(context);
        title.setText("PGP Debug");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = smallBtn(context, "Clear");
        clear.setOnClickListener(v -> clear());
        bar.addView(clear);

        Button selfTest = smallBtn(context, "Test");
        selfTest.setOnClickListener(v -> runSelfTest());
        bar.addView(selfTest);

        Button close = smallBtn(context, "Close");
        close.setOnClickListener(v -> hide(context));
        bar.addView(close);
        root.addView(bar);

        ScrollView scroll = new ScrollView(context);
        sLogView = new TextView(context);
        sLogView.setTypeface(Typeface.MONOSPACE);
        sLogView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sLogView.setTextColor(0xFFD1D1D6);
        sLogView.setText(snapshot());
        scroll.addView(sLogView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private static Button smallBtn(Context context, String label) {
        Button b = new Button(context);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        return b;
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
