package dev.jason.gboardpatches.extension.calculator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import dev.jason.gboardpatches.extension.overlay.FloatingHeightHelper;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import dev.jason.gboardpatches.extension.unitto.PgpUnittoAssets;

import dev.jason.gboardpatches.extension.overlay.OverlayPermission;

/**
 * Floating calculator hub overlay.
 *
 * - Built-in offline keypad via {@link GboardCalculatorEngine}
 * - Full Unitto experience via {@link dev.jason.gboardpatches.extension.unitto.UnittoIntegration}
 *   (install / launch / feature deep-links). Compose embed is phase 2; see
 *   unitto/UNITTO_COMPOSE_PLAN.md.
 */
public final class GboardCalculatorOverlayHost {
    private static final String TAG = "PGP";

    private static WindowManager.LayoutParams sLayoutParams;
    private static View sOverlayView;
    private static boolean sShowing;
    private static String sExpression = "";
    private static TextView sDisplay;
    private static TextView sResult;

    /** Optional: set by the IME so Insert can write into the focused field. */
    private static volatile InputConnection sInputConnection;

    private GboardCalculatorOverlayHost() {
    }

    public static void setInputConnection(InputConnection ic) {
        sInputConnection = ic;
    }

    public static boolean show(Context context) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;

        if (sShowing) {
            return true;
        }
        try {
            View content = buildContent(app);
            WindowManager.LayoutParams lp = createLayoutParams(app);
            if (!dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                    .addView(app, content, lp)) {
                Log.i(TAG, "Calculator overlay: could not add window");
                return false;
            }
            sOverlayView = content;
            sLayoutParams = lp;
            sShowing = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show calculator overlay", t);
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
            dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                    .removeView(app, sOverlayView);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hide calculator overlay", t);
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
                .aboveKeyboardParams(context, FloatingHeightHelper.initialHeightPercent(context) / 100.0f);
    }

    private static View buildContent(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0121214);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), 0xFF3A3A3C);
        root.setBackground(bg);

        // Title bar
        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("PGP Calculator");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button close = keyButton(context, "✕", 0xFF3A3A3C);
        close.setOnClickListener(v -> hide(context));
        titleBar.addView(close, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 36)));
        root.addView(titleBar);

        // Display
        sDisplay = new TextView(context);
        sDisplay.setText(sExpression.isEmpty() ? "0" : sExpression);
        sDisplay.setTextColor(Color.WHITE);
        sDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        sDisplay.setGravity(Gravity.END);
        sDisplay.setPadding(dp(context, 8), dp(context, 12), dp(context, 8), dp(context, 4));
        sDisplay.setMaxLines(2);
        root.addView(sDisplay);

        sResult = new TextView(context);
        sResult.setText("");
        sResult.setTextColor(0xFF8E8E93);
        sResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sResult.setGravity(Gravity.END);
        sResult.setPadding(dp(context, 8), 0, dp(context, 8), dp(context, 8));
        root.addView(sResult);

        // Scientific keypad (Unitto-grade in-process engine)
        String[][] keys = {
                {"sin", "cos", "tan", "ln"},
                {"asin", "acos", "atan", "log"},
                {"(", ")", "^", "√"},
                {"7", "8", "9", "÷"},
                {"4", "5", "6", "×"},
                {"1", "2", "3", "−"},
                {"0", ".", "%", "+"},
                {"C", "⌫", "!", "="},
        };
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(4);
        for (String[] row : keys) {
            for (String label : row) {
                Button b = keyButton(context, label,
                        isOp(label) ? 0xFF0A84FF : 0xFF2C2C2E);
                b.setOnClickListener(v -> onKey(context, label));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = dp(context, 44);
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
                grid.addView(b, lp);
            }
        }
        root.addView(grid);

        // Actions: Insert / Copy
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(context, 8), 0, 0);

        Button insert = keyButton(context, "Insert", 0xFF30D158);
        insert.setOnClickListener(v -> insertResult(context));
        actions.addView(insert, new LinearLayout.LayoutParams(0, dp(context, 40), 1f));

        Button copy = keyButton(context, "Copy", 0xFF3A3A3C);
        copy.setOnClickListener(v -> copyResult(context));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(context, 40), 1f);
        copyLp.leftMargin = dp(context, 6);
        actions.addView(copy, copyLp);
        root.addView(actions);

        // Unitto hub – full product surface (GPL-3.0 upstream)
        TextView nhTitle = new TextView(context);
        nhTitle.setText("Open Unitto app");
        nhTitle.setTextColor(Color.WHITE);
        nhTitle.setTypeface(Typeface.DEFAULT_BOLD);
        nhTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nhTitle.setPadding(0, dp(context, 10), 0, dp(context, 4));
        root.addView(nhTitle);

        LinearLayout nhRow1 = new LinearLayout(context);
        nhRow1.setOrientation(LinearLayout.HORIZONTAL);
        nhRow1.addView(nhButton(context, "Open Unitto", true),
                new LinearLayout.LayoutParams(0, dp(context, 40), 1f));
        root.addView(nhRow1);

        LinearLayout nhRow2 = new LinearLayout(context);
        nhRow2.setOrientation(LinearLayout.HORIZONTAL);
        String[][] feats = {
                {"Converter", "CONVERTER"},
                {"Date", "DATE"},
                {"Zones", "TIMEZONE"},
        };
        for (int i = 0; i < feats.length; i++) {
            Button fb = nhButton(context, feats[i][0], false);
            final String featName = feats[i][1];
            fb.setOnClickListener(v -> openUnittoFeature(context, featName));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    0, dp(context, 36), 1f);
            if (i > 0) flp.leftMargin = dp(context, 4);
            nhRow2.addView(fb, flp);
        }
        root.addView(nhRow2);

        // Path C offline length convert (bundled assets)
        try {
            LinearLayout conv = new LinearLayout(context);
            conv.setOrientation(LinearLayout.HORIZONTAL);
            conv.setPadding(8, 8, 8, 4);
            EditText convIn = new EditText(context);
            convIn.setHint("len");
            convIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            convIn.setTextColor(0xFFFFFFFF);
            convIn.setHintTextColor(0xFF8E8E93);
            convIn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            String[] units = new String[]{"m", "km", "cm", "mm", "mi", "yd", "ft", "in"};
            Spinner fromSp = new Spinner(context);
            Spinner toSp = new Spinner(context);
            ArrayAdapter<String> ad = new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_dropdown_item, units);
            fromSp.setAdapter(ad);
            toSp.setAdapter(ad);
            toSp.setSelection(1);
            Button go = new Button(context);
            go.setText("Conv");
            go.setOnClickListener(v -> {
                try {
                    double val = Double.parseDouble(convIn.getText().toString());
                    String from = units[fromSp.getSelectedItemPosition()];
                    String to = units[toSp.getSelectedItemPosition()];
                    Double out = PgpUnittoAssets.convertLength(context, val, from, to);
                    if (out != null) {
                        sExpression = String.valueOf(out);
                        if (sDisplay != null) sDisplay.setText(sExpression);
                    }
                } catch (Throwable ignored) {
                }
            });
            conv.addView(convIn);
            conv.addView(fromSp);
            conv.addView(toSp);
            conv.addView(go);
            root.addView(conv);
        } catch (Throwable ignored) {
        }


        TextView note = new TextView(context);
        boolean installed = false;
        try {
            installed = dev.jason.gboardpatches.extension.unitto.UnittoIntegration
                    .isInstalled(context);
        } catch (Throwable ignored) {
        }
        note.setText(installed
                ? "Unitto installed · GPL-3.0 · offline keypad above"
                : "Unitto not installed · tap Open to get full math/units/dates");
        note.setTextColor(0xFF636366);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        note.setPadding(0, dp(context, 6), 0, 0);
        root.addView(note);

        return root;
    }

    private static Button nhButton(Context context, String label, boolean primary) {
        Button b = keyButton(context, label, primary ? 0xFF6D4AFF : 0xFF3A3A3C);
        if (primary) {
            b.setOnClickListener(v -> {
                try {
                    dev.jason.gboardpatches.extension.unitto.UnittoIntegration
                            .openApp(context);
                } catch (Throwable ignored) {
                }
            });
        }
        return b;
    }

    private static void openUnittoFeature(Context context, String featName) {
        try {
            dev.jason.gboardpatches.extension.unitto.UnittoIntegration.Feature f =
                    dev.jason.gboardpatches.extension.unitto.UnittoIntegration.Feature
                            .fromName(featName);
            // 1) Installed Unitto app, feature deep-link, freeform height
            if (dev.jason.gboardpatches.extension.unitto.UnittoIntegration
                    .openFeature(context, f)) {
                return;
            }
            // 2) Official Unitto PWA inside Gboard overlay
            
            // 3) External web as last resort
            dev.jason.gboardpatches.extension.unitto.UnittoIntegration
                    .openFeature(context, f);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isOp(String label) {
        return "÷×−+=^%!".contains(label) || "C".equals(label) || "⌫".equals(label)
                || "sin".equals(label) || "cos".equals(label) || "tan".equals(label)
                || "asin".equals(label) || "acos".equals(label) || "atan".equals(label)
                || "ln".equals(label) || "log".equals(label) || "exp".equals(label)
                || "ln".equals(label) || "log".equals(label) || "√".equals(label)
                || "DEG".equals(label);
    }

    private static void onKey(Context context, String label) {
        if (sExpressionView == null || sResult == null) {
            return;
        }
        switch (label) {
            case "C":
                sExpression = "";
                sResult.setText("");
                break;
            case "⌫":
                if (!sExpression.isEmpty()) {
                    sExpression = sExpression.substring(0, sExpression.length() - 1);
                }
                break;
            case "=":
                String value = GboardCalculatorEngine.evaluate(sExpression);
                sResult.setText(value);
                if (!"Error".equals(value) && !value.isEmpty()) {
                    sExpression = value;
                }
                break;
            case "÷":
                sExpression += "/";
                break;
            case "×":
                sExpression += "*";
                break;
            case "−":
                sExpression += "-";
                break;
            case "asin":
                sExpression += "asin(";
                break;
            case "acos":
                sExpression += "acos(";
                break;
            case "atan":
                sExpression += "atan(";
                break;
            case "exp":
                sExpression += "exp(";
                break;
            case "sin":
                sExpression += "sin(";
                break;
            case "cos":
                sExpression += "cos(";
                break;
            case "tan":
                sExpression += "tan(";
                break;
            case "ln":
                sExpression += "ln(";
                break;
            case "log":
                sExpression += "log(";
                break;
            case "√":
                sExpression += "sqrt(";
                break;
            case "π":
                sExpression += "pi";
                break;
            case "e":
                sExpression += "e";
                break;
            case "!":
                sExpression += "!";
                break;
            case "%":
                sExpression += "%";
                break;
            case "^":
                sExpression += "^";
                break;
            case "DEG":
                // toggle display only; engine defaults radians — mark with suffix
                sResult.setText(sResult.getText().length() == 0
                        ? "mode: DEG (use asin etc carefully)"
                        : sResult.getText());
                break;
            default:
                sExpression += label;
                break;
        }
        sExpressionView.setText(sExpression);
        if (!sExpression.isEmpty() && !"=".equals(label) && !"C".equals(label)) {
            try {
                String preview = GboardCalculatorEngine.evaluate(sExpression);
                if (!"Error".equals(preview)) {
                    sResult.setText(preview);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void insertResult(Context context) {
        String value = sResult.getText() != null ? sResult.getText().toString() : "";
        if (value.startsWith("= ")) {
            value = value.substring(2);
        }
        if (value.isEmpty() || "Error".equals(value)) {
            value = sExpression;
        }
        if (value.isEmpty()) {
            return;
        }
        InputConnection ic = sInputConnection;
        if (ic != null) {
            try {
                ic.commitText(value, 1);
                Toast.makeText(context, "Inserted", Toast.LENGTH_SHORT).show();
                return;
            } catch (Throwable ignored) {
            }
        }
        // Fallback: clipboard
        copyToClipboard(context, value);
        Toast.makeText(context, "Copied (no input field)", Toast.LENGTH_SHORT).show();
    }

    private static void copyResult(Context context) {
        String value = sResult.getText() != null ? sResult.getText().toString() : "";
        if (value.startsWith("= ")) {
            value = value.substring(2);
        }
        if (value.isEmpty() || "Error".equals(value)) {
            value = sExpression;
        }
        if (value.isEmpty()) {
            return;
        }
        copyToClipboard(context, value);
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show();
    }

    private static void copyToClipboard(Context context, String text) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("calculator", text));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Button keyButton(Context context, String label, int color) {
        Button b = new Button(context);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 1 ? 13 : 16);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
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
