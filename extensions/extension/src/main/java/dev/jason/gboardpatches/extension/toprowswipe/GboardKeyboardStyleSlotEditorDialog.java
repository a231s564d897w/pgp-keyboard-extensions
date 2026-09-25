package dev.jason.gboardpatches.extension.toprowswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keyboard-style slot editor (reworked UI).
 *
 * Layout:
 * ┌─────────────────────────────┐
 * │  Number   [1][2]…[0]        │  ← only rows enabled on this preset
 * │  Q–P      [q][w]…[p]        │
 * │  A–Ñ      [a]…[ñ]           │
 * │  Z–M      [z]…[m]           │
 * ├─────────────────────────────┤
 * │  Selected: Q–P · col 3      │
 * │  Label on key   [  😀a  ] ✕ │
 * │  Text to type   [ hello ] ✕ │
 * │  [Copy label→text] [Clear]  │
 * │  ▸ Advanced (JS)            │
 * └─────────────────────────────┘
 *
 * Display and commit are independent. Display limited to ~1 emoji + 1 letter.
 */
final class GboardKeyboardStyleSlotEditorDialog {

    private static final RowType[] ROW_ORDER = {
            RowType.NUMBER, RowType.Q_TO_P, RowType.A_TO_L, RowType.Z_TO_M
    };

    interface PresetSaveListener {
        void onSave(KeyRowPreset preset);
    }

    interface LegacySaveListener {
        void onSave(List<GboardTopRowSwipeSettings.SlotText> slots);
    }

    static boolean show(Activity activity, KeyRowPreset preset, PresetSaveListener listener) {
        if (activity == null || activity.isFinishing() || preset == null || listener == null) {
            return false;
        }
        GboardTopRowSwipeStrings strings = GboardTopRowSwipeStrings.from(activity);
        runUiActionSafely(activity, "show slot editor", () -> {
            Controller controller = new Controller(activity, preset, strings);
            View content = controller.buildRoot();
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(preset.name + " — edit keys")
                    .setView(content)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        try {
                            listener.onSave(controller.editedPreset());
                        } catch (Throwable ignored) {
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(d -> {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    dialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                    dialog.getWindow().setAttributes(lp);
                }
            });
            dialog.show();
        });
        return true;
    }

    static boolean showLegacy(Activity activity,
            List<GboardTopRowSwipeSettings.SlotText> currentSlots,
            LegacySaveListener listener) {
        KeyRowPreset temp = KeyRowPreset.fromLegacySlots(currentSlots);
        return show(activity, temp, preset -> {
            List<KeySlot> qRow = preset.slotsForRow(RowType.Q_TO_P);
            List<GboardTopRowSwipeSettings.SlotText> legacy = new ArrayList<>();
            for (KeySlot s : qRow) {
                legacy.add(s.toLegacy());
            }
            while (legacy.size() < GboardTopRowSwipeSettings.SLOT_COUNT) {
                legacy.add(new GboardTopRowSwipeSettings.SlotText("", ""));
            }
            if (legacy.size() > GboardTopRowSwipeSettings.SLOT_COUNT) {
                legacy = legacy.subList(0, GboardTopRowSwipeSettings.SLOT_COUNT);
            }
            listener.onSave(legacy);
        });
    }

    private static void runUiActionSafely(Activity activity, String label, Runnable action) {
        try {
            if (activity.isFinishing()) {
                return;
            }
            action.run();
        } catch (Throwable t) {
            try {
                Toast.makeText(activity, "Editor: " + label, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class Controller {
        private final Activity activity;
        private final KeyRowPreset originalPreset;
        private final List<KeySlot> workingSlots = new ArrayList<>();
        private final Set<RowType> enabledRows;
        private final GboardTopRowSwipeStrings strings;
        private final List<TextView> allKeyViews = new ArrayList<>();
        private final Map<TextView, Integer> keyToIndex = new HashMap<>();

        private int selectedIndex = -1;
        private boolean suppressWatchers;

        private TextView propertyTitle;
        private EditText displayInput;
        private EditText commitInput;
        private CheckBox useJavaScriptInput;
        private EditText scriptInput;
        private EditText timeoutInput;
        private LinearLayout advancedBox;
        private TextView advancedToggle;
        private boolean advancedOpen;

        Controller(Activity activity, KeyRowPreset preset, GboardTopRowSwipeStrings strings) {
            this.activity = activity;
            this.originalPreset = preset;
            this.strings = strings;
            this.enabledRows = EnumSet.copyOf(preset.enabledRows);
            for (KeySlot s : preset.slots) {
                workingSlots.add(s);
            }
            for (RowType row : enabledRows) {
                ensureRowSlots(row);
            }
        }

        private void ensureRowSlots(RowType row) {
            int count = 0;
            for (KeySlot s : workingSlots) {
                if (s.rowType == row) {
                    count++;
                }
            }
            for (int i = count; i < row.defaultSlotCount; i++) {
                workingSlots.add(new KeySlot("", "", false, "", 1000, row, i));
            }
        }

        View buildRoot() {
            ScrollView scroll = new ScrollView(activity);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(12), dp(8), dp(12), dp(12));
            root.setBackgroundColor(0xFF000000);

            TextView hint = new TextView(activity);
            hint.setText("Tap a key → edit label & text below. Rows shown = this preset only.");
            hint.setTextColor(0xFF8E8E93);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setPadding(0, 0, 0, dp(10));
            root.addView(hint);

            for (RowType row : ROW_ORDER) {
                if (!enabledRows.contains(row)) {
                    continue;
                }
                root.addView(buildRowLabel(row.displayName));
                root.addView(buildHorizontalKeyRow(row));
            }

            root.addView(buildPropertySheet());
            scroll.addView(root);
            return scroll;
        }

        private TextView buildRowLabel(String label) {
            TextView tv = new TextView(activity);
            tv.setText(label);
            tv.setTextColor(0xFFAEAEB2);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setPadding(dp(4), dp(10), 0, dp(4));
            return tv;
        }

        private View buildHorizontalKeyRow(RowType row) {
            HorizontalScrollView hsv = new HorizontalScrollView(activity);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setPadding(dp(2), dp(2), dp(2), dp(2));

            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < workingSlots.size(); i++) {
                if (workingSlots.get(i).rowType == row) {
                    indices.add(i);
                }
            }
            indices.sort((a, b) -> Integer.compare(
                    workingSlots.get(a).columnIndex, workingSlots.get(b).columnIndex));

            for (int slotIndex : indices) {
                TextView key = buildKeyView(slotIndex);
                allKeyViews.add(key);
                keyToIndex.put(key, slotIndex);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(40), dp(48));
                lp.setMargins(dp(3), 0, dp(3), 0);
                rowLayout.addView(key, lp);
            }
            hsv.addView(rowLayout, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return hsv;
        }

        private TextView buildKeyView(int slotIndex) {
            TextView key = new TextView(activity);
            key.setGravity(Gravity.CENTER);
            key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            key.setTypeface(Typeface.DEFAULT_BOLD);
            key.setTextColor(Color.WHITE);
            key.setMinWidth(dp(40));
            key.setMinHeight(dp(48));
            paintKey(key, slotIndex, false);
            key.setOnClickListener(v -> selectSlot(slotIndex));
            return key;
        }

        private void paintKey(TextView key, int slotIndex, boolean selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            if (selected) {
                bg.setColor(0xFF0A84FF);
                bg.setStroke(dp(2), 0xFF64B5F6);
            } else {
                bg.setColor(0xFF2C2C2E);
                bg.setStroke(dp(1), 0xFF48484A);
            }
            key.setBackground(bg);
            updateKeyLabel(key, slotIndex);
        }

        private void updateKeyLabel(TextView key, int slotIndex) {
            if (slotIndex < 0 || slotIndex >= workingSlots.size()) {
                key.setText("·");
                return;
            }
            KeySlot slot = workingSlots.get(slotIndex);
            String label = slot.displayText != null && !slot.displayText.isEmpty()
                    ? slot.displayText : "·";
            if (slot.isJavaScript) {
                label = "JS";
            }
            key.setText(label);
        }

        private void selectSlot(int index) {
            selectedIndex = index;
            for (TextView key : allKeyViews) {
                Integer idx = keyToIndex.get(key);
                paintKey(key, idx != null ? idx : -1, idx != null && idx == index);
            }
            KeySlot slot = workingSlots.get(index);
            propertyTitle.setText(slot.rowType.displayName + "  ·  key " + (slot.columnIndex + 1));
            suppressWatchers = true;
            displayInput.setText(slot.displayText != null ? slot.displayText : "");
            commitInput.setText(slot.commitText != null ? slot.commitText : "");
            useJavaScriptInput.setChecked(slot.isJavaScript);
            scriptInput.setText(slot.scriptText != null ? slot.scriptText : "");
            timeoutInput.setText(Integer.toString(Math.max(0, slot.timeoutMs)));
            suppressWatchers = false;
            updateAdvancedVisibility();
        }

        private LinearLayout buildPropertySheet() {
            LinearLayout sheet = new LinearLayout(activity);
            sheet.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable sheetBg = new GradientDrawable();
            sheetBg.setColor(0xFF1C1C1E);
            sheetBg.setCornerRadius(dp(14));
            sheet.setBackground(sheetBg);
            sheet.setPadding(dp(14), dp(12), dp(14), dp(14));
            LinearLayout.LayoutParams sheetLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sheetLp.topMargin = dp(14);
            sheet.setLayoutParams(sheetLp);

            propertyTitle = new TextView(activity);
            propertyTitle.setText("Tap a key above to edit");
            propertyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            propertyTitle.setTypeface(Typeface.DEFAULT_BOLD);
            propertyTitle.setTextColor(Color.WHITE);
            propertyTitle.setPadding(0, 0, 0, dp(10));
            sheet.addView(propertyTitle);

            sheet.addView(buildLabel("Label on key  (emoji + letter)"));
            displayInput = buildInput("e.g. 😀 or A");
            displayInput.setFilters(new InputFilter[] {
                    (source, start, end, dest, dstart, dend) -> {
                        String next = dest.subSequence(0, dstart).toString()
                                + source.subSequence(start, end)
                                + dest.subSequence(dend, dest.length());
                        String limited = limitDisplayText(next);
                        if (limited.equals(next)) {
                            return null;
                        }
                        // reject excess
                        return "";
                    }
            });
            sheet.addView(fieldWithClear(displayInput));

            sheet.addView(buildLabel("Text typed when pressed"));
            commitInput = buildInput("what goes into the text field");
            sheet.addView(fieldWithClear(commitInput));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(8), 0, dp(4));
            Button copyBtn = smallButton("Label → text");
            copyBtn.setOnClickListener(v -> {
                if (selectedIndex < 0) {
                    return;
                }
                String d = displayInput.getText() != null
                        ? displayInput.getText().toString() : "";
                suppressWatchers = true;
                commitInput.setText(d);
                suppressWatchers = false;
                pushToSlot();
            });
            Button wipeBtn = smallButton("Clear key");
            wipeBtn.setOnClickListener(v -> {
                if (selectedIndex < 0) {
                    return;
                }
                suppressWatchers = true;
                displayInput.setText("");
                commitInput.setText("");
                useJavaScriptInput.setChecked(false);
                scriptInput.setText("");
                suppressWatchers = false;
                pushToSlot();
                refreshSelectedKey();
            });
            actions.addView(copyBtn);
            actions.addView(wipeBtn);
            sheet.addView(actions);

            advancedToggle = new TextView(activity);
            advancedToggle.setText("▸  Advanced (script)");
            advancedToggle.setTextColor(0xFF64B5F6);
            advancedToggle.setPadding(0, dp(12), 0, dp(6));
            advancedToggle.setOnClickListener(v -> {
                advancedOpen = !advancedOpen;
                updateAdvancedVisibility();
            });
            sheet.addView(advancedToggle);

            advancedBox = new LinearLayout(activity);
            advancedBox.setOrientation(LinearLayout.VERTICAL);
            advancedBox.setVisibility(View.GONE);

            useJavaScriptInput = new CheckBox(activity);
            useJavaScriptInput.setText("Run script instead of plain text");
            useJavaScriptInput.setTextColor(Color.WHITE);
            advancedBox.addView(useJavaScriptInput);

            advancedBox.addView(buildLabel("Script"));
            scriptInput = buildInput("");
            scriptInput.setSingleLine(false);
            scriptInput.setMinLines(3);
            scriptInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            advancedBox.addView(scriptInput);

            advancedBox.addView(buildLabel("Timeout ms"));
            timeoutInput = buildInput("1000");
            timeoutInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            advancedBox.addView(timeoutInput);
            sheet.addView(advancedBox);

            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override
                public void afterTextChanged(Editable value) {
                    if (suppressWatchers || selectedIndex < 0) {
                        return;
                    }
                    pushToSlot();
                    refreshSelectedKey();
                }
            };
            displayInput.addTextChangedListener(watcher);
            commitInput.addTextChangedListener(watcher);
            scriptInput.addTextChangedListener(watcher);
            timeoutInput.addTextChangedListener(watcher);
            useJavaScriptInput.setOnCheckedChangeListener((b, c) -> {
                if (suppressWatchers || selectedIndex < 0) {
                    return;
                }
                pushToSlot();
                refreshSelectedKey();
            });

            return sheet;
        }

        private void updateAdvancedVisibility() {
            advancedBox.setVisibility(advancedOpen ? View.VISIBLE : View.GONE);
            advancedToggle.setText(advancedOpen
                    ? "▾  Advanced (script)"
                    : "▸  Advanced (script)");
        }

        private void pushToSlot() {
            if (selectedIndex < 0 || selectedIndex >= workingSlots.size()) {
                return;
            }
            KeySlot old = workingSlots.get(selectedIndex);
            String display = limitDisplayText(safe(displayInput));
            String commit = safe(commitInput);
            boolean isJs = useJavaScriptInput.isChecked();
            String script = safe(scriptInput);
            int timeout = 1000;
            try {
                timeout = Integer.parseInt(safe(timeoutInput));
            } catch (Throwable ignored) {
            }
            workingSlots.set(selectedIndex, new KeySlot(
                    display, commit, isJs, script, timeout,
                    old.rowType, old.columnIndex));
        }

        private void refreshSelectedKey() {
            if (selectedIndex < 0) {
                return;
            }
            for (TextView key : allKeyViews) {
                Integer idx = keyToIndex.get(key);
                if (idx != null && idx == selectedIndex) {
                    updateKeyLabel(key, selectedIndex);
                    break;
                }
            }
        }

        KeyRowPreset editedPreset() {
            pushToSlot();
            return originalPreset.withSlots(new ArrayList<>(workingSlots));
        }

        private TextView buildLabel(String text) {
            TextView tv = new TextView(activity);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(0xFFAEAEB2);
            tv.setPadding(0, dp(8), 0, dp(4));
            return tv;
        }

        private EditText buildInput(String hint) {
            EditText et = new EditText(activity);
            et.setHint(hint);
            et.setSingleLine(true);
            et.setFocusable(true);
            et.setFocusableInTouchMode(true);
            et.setCursorVisible(true);
            et.setLongClickable(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            et.setTextColor(Color.WHITE);
            et.setHintTextColor(0xFF636366);
            et.setBackgroundColor(0xFF2C2C2E);
            et.setPadding(dp(12), dp(12), dp(12), dp(12));
            et.setMinHeight(dp(48));
            return et;
        }

        private View fieldWithClear(EditText field) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(field, fieldLp);
            Button clear = smallButton("✕");
            clear.setOnClickListener(v -> {
                field.setText("");
                field.requestFocus();
            });
            row.addView(clear);
            return row;
        }

        private Button smallButton(String text) {
            Button b = new Button(activity);
            b.setText(text);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            b.setLayoutParams(lp);
            return b;
        }

        private int dp(int value) {
            float d = activity.getResources().getDisplayMetrics().density;
            return Math.round(value * d);
        }

        private static String safe(EditText et) {
            if (et == null || et.getText() == null) {
                return "";
            }
            return et.getText().toString().trim();
        }

        /**
         * Keep at most one emoji (or symbol) + one letter/digit.
         * Uses code-point aware trimming so emoji stay intact.
         */
        private static String limitDisplayText(String raw) {
            if (raw == null || raw.isEmpty()) {
                return "";
            }
            String s = raw.trim();
            int[] cps = s.codePoints().toArray();
            if (cps.length == 0) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            boolean hasEmoji = false;
            boolean hasLetter = false;
            for (int cp : cps) {
                boolean isLetter = Character.isLetterOrDigit(cp);
                boolean isEmoji = !isLetter && !Character.isWhitespace(cp);
                if (isEmoji && !hasEmoji) {
                    out.appendCodePoint(cp);
                    hasEmoji = true;
                } else if (isLetter && !hasLetter) {
                    out.appendCodePoint(cp);
                    hasLetter = true;
                }
                if (hasEmoji && hasLetter) {
                    break;
                }
                // allow single emoji alone or single letter alone
                if (!hasEmoji && !hasLetter && isEmoji) {
                    out.appendCodePoint(cp);
                    hasEmoji = true;
                }
            }
            if (out.length() == 0 && cps.length > 0) {
                out.appendCodePoint(cps[0]);
            }
            return out.toString();
        }
    }
}
