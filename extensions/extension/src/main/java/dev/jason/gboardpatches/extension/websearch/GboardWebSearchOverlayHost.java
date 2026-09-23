package dev.jason.gboardpatches.extension.websearch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper;

/**
 * In-Gboard floating WebView for search (instead of launching a separate browser app).
 */
public final class GboardWebSearchOverlayHost {
    private static final String TAG = "PGP";
    private static final String DEFAULT_URL = "https://www.google.com/";

    private static View sOverlayView;
    private static boolean sShowing;
    private static WebView sWebView;

    private GboardWebSearchOverlayHost() {
    }

    public static boolean show(Context context) {
        return show(context, DEFAULT_URL);
    }

    public static boolean show(Context context, String url) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (sShowing) {
            return true;
        }
        try {
            String start = (url == null || url.isEmpty()) ? DEFAULT_URL : url;
            View content = buildContent(app, start);
            int w = app.getResources().getDisplayMetrics().widthPixels;
            int h = (int) (app.getResources().getDisplayMetrics().heightPixels * 0.55f);
            WindowManager.LayoutParams lp = OverlayWindowHelper.panelParams(
                    app, w - dp(app, 16), h, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.y = dp(app, 48);
            if (!OverlayWindowHelper.addView(app, content, lp)) {
                Log.i(TAG, "Web search overlay: could not add window");
                return false;
            }
            sOverlayView = content;
            sShowing = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Web search overlay failed", t);
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
            if (sWebView != null) {
                try {
                    sWebView.stopLoading();
                    sWebView.destroy();
                } catch (Throwable ignored) {
                }
                sWebView = null;
            }
            OverlayWindowHelper.removeView(context, sOverlayView);
        } finally {
            sOverlayView = null;
            sShowing = false;
        }
    }

    public static boolean isShowing() {
        return sShowing;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static View buildContent(Context context, String url) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0121214);
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), 0xFF3A3A3C);
        root.setBackground(bg);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("Web Search");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button back = new Button(context);
        back.setText("◀");
        back.setOnClickListener(v -> {
            if (sWebView != null && sWebView.canGoBack()) {
                sWebView.goBack();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 36)));

        Button close = new Button(context);
        close.setText("✕");
        close.setOnClickListener(v -> hide(context));
        bar.addView(close, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 36)));
        root.addView(bar);

        WebView web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl(url);
        sWebView = web;
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
