package dev.jason.gboardpatches.extension.unitto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.jason.gboardpatches.extension.overlay.OverlayPermission;

/**
 * Floating Unitto experience.
 *
 * Priority:
 * 1. In-process Jetpack Compose UI ({@code UnittoComposeContent}) when the
 *    Compose module is on the classpath (phase-2 full embed).
 * 2. Official Unitto web app in a WebView overlay
 *    ({@link UnittoIntegration#WEB_APP_URL}) so the full product is available now.
 * 3. Fallback message + open native Unitto app / install.
 *
 * Access Point should open this host (not only the lightweight keypad).
 */
public final class UnittoComposeOverlayHost {
    private static final String TAG = "PGP";

    private static View sOverlayView;
    private static boolean sShowing;
    private static WebView sWebView;

    private UnittoComposeOverlayHost() {
    }

    public static boolean show(Context context) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (!OverlayPermission.ensureCanDrawOverlays(app)) {
            Log.i(TAG, "Unitto Compose overlay: permission missing");
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
            View content = buildContent(app);
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(app, 360),
                    dp(app, 520),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            wm.addView(content, lp);
            sOverlayView = content;
            sShowing = true;
            try {
                dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                        "Unitto", "Compose/Web overlay shown");
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Unitto Compose overlay failed", t);
            sShowing = false;
            sOverlayView = null;
            return false;
        }
    }

    public static void hide(Context context) {
        if (!sShowing || sOverlayView == null) {
            return;
        }
        try {
            Context app = context != null && context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            if (app != null) {
                WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    wm.removeView(sOverlayView);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "hide Unitto overlay failed", t);
        } finally {
            destroyWebView();
            sOverlayView = null;
            sShowing = false;
        }
    }

    public static boolean isShowing() {
        return sShowing;
    }

    private static void destroyWebView() {
        if (sWebView != null) {
            try {
                sWebView.stopLoading();
                sWebView.loadUrl("about:blank");
                sWebView.clearHistory();
                if (sWebView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) sWebView.getParent()).removeView(sWebView);
                }
                sWebView.destroy();
            } catch (Throwable ignored) {
            }
            sWebView = null;
        }
    }

    private static View buildContent(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0121214);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), 0xFF3A3A3C);
        root.setBackground(bg);

        // Title bar
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText("Unitto");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button openApp = smallBtn(context, "App");
        openApp.setOnClickListener(v -> UnittoIntegration.openApp(context));
        bar.addView(openApp);

        Button clearCache = smallBtn(context, "Cache");
        clearCache.setOnClickListener(v -> {
            UnittoWebCache.clearWebCache(context, sWebView);
            if (sWebView != null) {
                sWebView.loadUrl(UnittoIntegration.WEB_APP_URL);
            }
            try {
                android.widget.Toast.makeText(context, "Unitto cache cleared",
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
        bar.addView(clearCache);

        Button close = smallBtn(context, "Close");
        close.setOnClickListener(v -> hide(context));
        bar.addView(close);
        root.addView(bar);

        FrameLayout body = new FrameLayout(context);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.topMargin = dp(context, 8);
        root.addView(body, bodyLp);

        View compose = tryCreateComposeContent(context);
        if (compose != null) {
            body.addView(compose, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            TextView mode = new TextView(context);
            mode.setText("Compose module");
            mode.setTextColor(0xFF8E8E93);
            mode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            root.addView(mode);
        } else {
            body.addView(createWebContent(context), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            TextView mode = new TextView(context);
            mode.setText("Unitto web (cached fallback) · Compose module when packaged");
            mode.setTextColor(0xFF8E8E93);
            mode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            root.addView(mode);
        }
        return root;
    }

    /**
     * Reflective load of Compose content so the Java host compiles without Compose
     * on the annotation processor classpath. When
     * {@code dev.jason.gboardpatches.extension.unitto.compose.UnittoComposeContent}
     * is present, it is used.
     */
    private static View tryCreateComposeContent(Context context) {
        try {
            Class<?> cls = Class.forName(
                    "dev.jason.gboardpatches.extension.unitto.compose.UnittoComposeContent");
            Object instance = cls.getMethod("createView", Context.class).invoke(null, context);
            if (instance instanceof View) {
                return (View) instance;
            }
        } catch (Throwable t) {
            Log.d(TAG, "Compose content not on classpath, using Unitto web", t);
        }
        return null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static View createWebContent(Context context) {
        WebView web = new WebView(context);
        sWebView = web;
        UnittoWebCache.configureWebView(context, web);
        web.setBackgroundColor(0xFF121212);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                UnittoWebCache.markWebWarmed(context);
                try {
                    dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                            "Unitto", "web cached/warmed url=" + url);
                } catch (Throwable ignored) {
                }
            }
        });
        // Prefer cache when already warmed and offline path configured inside UnittoWebCache
        web.loadUrl(UnittoIntegration.WEB_APP_URL);
        return web;
    }

    private static Button smallBtn(Context context, String label) {
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF3A3A3C);
        d.setCornerRadius(dp(context, 8));
        b.setBackground(d);
        return b;
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
