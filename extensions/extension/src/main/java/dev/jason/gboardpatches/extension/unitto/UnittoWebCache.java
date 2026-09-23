package dev.jason.gboardpatches.extension.unitto;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;

/**
 * Offline-friendly cache for the Unitto web fallback and lightweight UI state.
 *
 * WebView:
 * - Enables DOM storage, database, wide viewport
 * - Uses CACHE_MODE so revisited Unitto web loads from disk when offline
 * - Keeps cache under app cache dir (clearable via {@link #clearWebCache})
 *
 * UI state:
 * - Last calculator expression / converter fields survive overlay reopen
 */
public final class UnittoWebCache {
    private static final String TAG = "PGP";
    private static final String PREFS = "gboard_patches_unitto_cache";
    private static final String KEY_LAST_EXPRESSION = "last_expression";
    private static final String KEY_LAST_RESULT = "last_result";
    private static final String KEY_CONV_AMOUNT = "conv_amount";
    private static final String KEY_CONV_FROM = "conv_from";
    private static final String KEY_CONV_TO = "conv_to";
    private static final String KEY_DATE_DAYS = "date_days";
    private static final String KEY_LAST_WEB_LOAD_MS = "last_web_load_ms";
    private static final String KEY_WEB_WARM = "web_warmed";

    private UnittoWebCache() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- WebView cache ----

    /**
     * Apply cache-friendly settings and point WebView at the app cache directory.
     * Call before {@code loadUrl}.
     */
    public static void configureWebView(Context context, WebView web) {
        if (context == null || web == null) {
            return;
        }
        try {
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setAllowFileAccess(true);
            // Prefer cache when present; still revalidate when online
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            // Offline: force cache-only so a previously visited Unitto web still opens
            if (!isNetworkLikelyAvailable(context)) {
                settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                Log.i(TAG, "Unitto WebView: offline → LOAD_CACHE_ELSE_NETWORK");
            }
        } catch (Throwable t) {
            Log.w(TAG, "configureWebView failed", t);
        }
    }

    /** After a successful page load, mark web cache as warmed. */
    public static void markWebWarmed(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit()
                .putBoolean(KEY_WEB_WARM, true)
                .putLong(KEY_LAST_WEB_LOAD_MS, System.currentTimeMillis())
                .apply();
    }

    public static boolean isWebWarmed(Context context) {
        return context != null && prefs(context).getBoolean(KEY_WEB_WARM, false);
    }

    public static void clearWebCache(Context context, WebView web) {
        try {
            if (web != null) {
                web.clearCache(true);
                web.clearHistory();
            }
        } catch (Throwable ignored) {
        }
        if (context != null) {
            prefs(context).edit().putBoolean(KEY_WEB_WARM, false).apply();
            File cacheDir = new File(context.getCacheDir(), "unitto_web");
            deleteRecursive(cacheDir);
        }
    }

    private static boolean isNetworkLikelyAvailable(Context context) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network net = cm.getActiveNetwork();
                if (net == null) {
                    return false;
                }
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            }
            //noinspection deprecation
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            //noinspection deprecation
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursive(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ---- UI state cache (Compose / offline keypad) ----

    public static void saveCalculatorState(Context context, String expression, String result) {
        if (context == null) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_LAST_EXPRESSION, expression != null ? expression : "")
                .putString(KEY_LAST_RESULT, result != null ? result : "")
                .apply();
    }

    public static String lastExpression(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_LAST_EXPRESSION, "");
    }

    public static String lastResult(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_LAST_RESULT, "");
    }

    public static void saveConverterState(Context context, String amount, String from, String to) {
        if (context == null) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_CONV_AMOUNT, amount != null ? amount : "1")
                .putString(KEY_CONV_FROM, from != null ? from : "km")
                .putString(KEY_CONV_TO, to != null ? to : "mi")
                .apply();
    }

    public static String convAmount(Context context) {
        return context == null ? "1" : prefs(context).getString(KEY_CONV_AMOUNT, "1");
    }

    public static String convFrom(Context context) {
        return context == null ? "km" : prefs(context).getString(KEY_CONV_FROM, "km");
    }

    public static String convTo(Context context) {
        return context == null ? "mi" : prefs(context).getString(KEY_CONV_TO, "mi");
    }

    public static void saveDateDays(Context context, String days) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putString(KEY_DATE_DAYS, days != null ? days : "7").apply();
    }

    public static String dateDays(Context context) {
        return context == null ? "7" : prefs(context).getString(KEY_DATE_DAYS, "7");
    }
}
