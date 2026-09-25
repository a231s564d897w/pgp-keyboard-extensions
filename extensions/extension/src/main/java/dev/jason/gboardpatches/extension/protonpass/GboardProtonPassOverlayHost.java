package dev.jason.gboardpatches.extension.protonpass;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.overlay.OverlayPermission;

/**
 * Floating Proton Pass credentials window (Samsung Pass style).
 *
 * Phase 1: structured UI + open Proton Pass app + insert via InputConnection.
 * Phase 2: fill {@link #setEntries} from Autofill / deep-link when available.
 */
public final class GboardProtonPassOverlayHost {
    private static final String TAG = "PGP";
    private static final String[] PROTON_PASS_PACKAGES = {
            "proton.android.pass",
            "proton.android.pass.fdroid",
    };

    public static final class Entry {
        public final String title;
        public final String username;
        public final String password;

        public Entry(String title, String username, String password) {
            this.title = title != null ? title : "";
            this.username = username != null ? username : "";
            this.password = password != null ? password : "";
        }
    }

    private static WindowManager.LayoutParams sLayoutParams;
    private static View sOverlayView;
    private static boolean sShowing;
    private static List<Entry> sEntries = new ArrayList<>();
    private static volatile InputConnection sInputConnection;

    private GboardProtonPassOverlayHost() {
    }

    public static void setInputConnection(InputConnection ic) {
        sInputConnection = ic;
    }

    /** Replace the visible credential list (called when Autofill data arrives). */
    public static void setEntries(List<Entry> entries) {
        sEntries.clear();
        if (entries != null) {
            sEntries.addAll(entries);
        }
        // If already showing, user must reopen to refresh; full live refresh later
    }

    public static boolean show(Context context) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;

        
                try {
            if (sEntries == null || sEntries.isEmpty()) {
                Context loadCtx = context.getApplicationContext() != null
                        ? context.getApplicationContext() : context;
                java.util.List<Entry> stored = ProtonPassEntryStore.load(loadCtx);
                if (stored != null && !stored.isEmpty()) {
                    sEntries = new java.util.ArrayList<>(stored);
                }
            }
        } catch (Throwable ignored) {
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
            WindowManager.LayoutParams lp = createLayoutParams(app);
            if (!dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper.addView(app, content, lp)) {
                return false;
            }
            sOverlayView = content;
            sLayoutParams = lp;
            sShowing = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show Proton Pass overlay", t);
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
            if (app == null) {
                return;
            }
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper.removeView(app, sOverlayView);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hide Proton Pass overlay", t);
        } finally {
            sOverlayView = null;
            sLayoutParams = null;
            sShowing = false;
        }
    }

    public static boolean isShowing() {
        return sShowing;
    }

    private static WindowManager.LayoutParams createLayoutParams(Context context) {
        return dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                .aboveKeyboardParams(context, 0.40f);
    }

    private static View buildContent(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0121214);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), 0xFF3A3A3C);
        root.setBackground(bg);

        // Title
        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("Proton Pass");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button close = actionButton(context, "✕", 0xFF3A3A3C);
        close.setOnClickListener(v -> hide(context));
        titleBar.addView(close, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 36)));
        root.addView(titleBar);

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        if (sEntries.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(
                    "No credentials loaded yet.\n\n"
                            + "• Open Proton Pass to unlock vaults\n"
                            + "• Autofill will fill this list later\n"
                            + "• Demo entries test Insert into the focused field");
            empty.setTextColor(0xFFAEAEB2);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setPadding(0, dp(context, 16), 0, dp(context, 8));
            list.addView(empty);
            Button demo = actionButton(context, "Load demo entries", 0xFF3A3A3C);
            demo.setOnClickListener(v -> {
                java.util.List<Entry> demoList = new java.util.ArrayList<>();
                demoList.add(new Entry("Example site", "user@example.com", "demo-password"));
                demoList.add(new Entry("Work account", "you@company.com", "work-secret"));
                setEntries(demoList);
                try {
                    ProtonPassEntryStore.save(context, demoList);
                } catch (Throwable ignored) {
                }
                hide(context);
                show(context);
            });
            list.addView(demo);
        } else {
            for (Entry entry : sEntries) {
                list.addView(buildEntryRow(context, entry));
            }
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button openApp = actionButton(context, "Open Proton Pass", 0xFF6D4AFF);
        openApp.setOnClickListener(v -> openProtonPass(context));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 42));
        openLp.topMargin = dp(context, 8);
        root.addView(openApp, openLp);

        if (!sEntries.isEmpty()) {
            Button clear = actionButton(context, "Clear saved entries", 0xFF3A3A3C);
            clear.setOnClickListener(v -> {
                setEntries(new java.util.ArrayList<>());
                try {
                    ProtonPassEntryStore.clear(context);
                } catch (Throwable ignored) {
                }
                hide(context);
                show(context);
            });
            LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 42));
            clearLp.topMargin = dp(context, 6);
            root.addView(clear, clearLp);
        }

        return root;
    }

    private static View buildEntryRow(Context context, Entry entry) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(0xFF2C2C2E);
        rowBg.setCornerRadius(dp(context, 10));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(context, 8);
        row.setLayoutParams(rowLp);

        TextView title = new TextView(context);
        title.setText(entry.title.isEmpty() ? "(untitled)" : entry.title);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(title);

        if (!entry.username.isEmpty()) {
            Button userBtn = actionButton(context, "User: " + entry.username, 0xFF3A3A3C);
            userBtn.setOnClickListener(v -> insertText(context, entry.username));
            row.addView(userBtn);
        }
        if (!entry.password.isEmpty()) {
            Button passBtn = actionButton(context, "Password ••••••", 0xFF3A3A3C);
            passBtn.setOnClickListener(v -> insertText(context, entry.password));
            row.addView(passBtn);
        }
        return row;
    }

    private static void insertText(Context context, String text) {
        InputConnection ic = sInputConnection;
        if (ic != null) {
            try {
                ic.commitText(text, 1);
                Toast.makeText(context, "Inserted", Toast.LENGTH_SHORT).show();
                return;
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(context, "No focused field", Toast.LENGTH_SHORT).show();
    }

    private static void openProtonPass(Context context) {
        if (context == null) {
            return;
        }
        String[] packages = {
                "proton.android.pass",
                "proton.android.pass.fdroid",
        };
        for (String pkg : packages) {
            try {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        // Real path when Pass is installed but locked: open system Autofill settings
        try {
            Intent autofill = new Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
            autofill.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            autofill.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(autofill);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
        } catch (Throwable ignored) {
        }
    }

    private static Button actionButton(Context context, String label, int color) {
        Button b = new Button(context);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
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
