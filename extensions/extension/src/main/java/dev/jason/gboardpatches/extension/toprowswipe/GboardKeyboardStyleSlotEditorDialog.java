package dev.jason.gboardpatches.extension.toprowswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.KeyListener;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keyboard-style visual slot editor.
 *
 * Shows only the rows enabled on the current {@link KeyRowPreset} as horizontal
 * key rows (Number on top, then Q–P, A–Ñ/A–L, Z–M). Tapping a key opens the
 * property sheet for that slot. No long scrolling list of every slot.
 */
final class GboardKeyboardStyleSlotEditorDialog {

    private static final String TAG = "PGP";

    /** Preferred vertical order of rows in the visual editor. */
    private static final RowType[] ROW_ORDER = {
            RowType.NUMBER, RowType.Q_TO_P, RowType.A_TO_L, RowType.Z_TO_M
    };

    interface PresetSaveCallback {
        void onSave(KeyRowPreset preset);
    }

    /** Legacy callback kept so the old SettingsFeature path still compiles. */
    interface SaveCallback {
        void onSave(List<GboardTopRowSwipeSettings.SlotText> slots);
    }

    private GboardKeyboardStyleSlotEditorDialog() {
    }

    /**
     * Preferred entry: edit a full preset (multi-row aware).
     */
    static boolean show(Activity activity, KeyRowPreset preset,
            PresetSaveCallback saveCallback, Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || preset == null) {
            return false;
        }

        GboardTopRowSwipeStrings strings = GboardTopRowSwipeStrings.from(activity);
        boolean[] shown = {false};

        runUiActionSafely(activity, "show keyboard-style slot editor", () -> {
            Controller controller = new Controller(activity, preset, strings);
            View root = controller.buildRootView();

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(preset.name)
                    .setView(root)
                    .setPositiveButton(strings.saveButton, null)
                    .setNegativeButton(strings.cancelButton, null)
                    .create();

            dialog.setOnShowListener(ignored -> runUiActionSafely(activity,
                    "configure keyboard-style editor", () -> {
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
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(v -> runUiActionSafely(activity,
                                        "save keyboard-style editor", () -> {
                                            KeyRowPreset edited = controller.editedPreset();
                                            if (edited == null) {
                                                return;
                                            }
                                            if (saveCallback != null) {
                                                saveCallback.onSave(edited);
                                            }
                                            dialog.dismiss();
                                        }));
                    }));

            dialog.setOnDismissListener(ignored -> {
                if (onDismiss != null) {
                    onDismiss.run();
                }
            });
            dialog.show();
            shown[0] = true;
        });
        return shown[0];
    }

    /**
     * Legacy entry used by the old SettingsFeature path (Q–P only).
     * Converts the 10 SlotText list into a temporary preset, then back on save.
     */
    static boolean show(Activity activity,
            List<GboardTopRowSwipeSettings.SlotText> currentSlots,
            SaveCallback saveCallback,
            Runnable onDismiss) {
        KeyRowPreset temp = KeyRowPreset.fromLegacySlots(currentSlots);
        return show(activity, temp, preset -> {
            if (saveCallback != null) {
                List<KeySlot> qRow = preset.slotsForRow(RowType.Q_TO_P);
                List<GboardTopRowSwipeSettings.SlotText> legacy = new ArrayList<>();
                for (KeySlot s : qRow) {
                    legacy.add(s.toLegacy());
                }
                // Pad/truncate to SLOT_COUNT for the old write path
                while (legacy.size() < GboardTopRowSwipeSettings.SLOT_COUNT) {
                    legacy.add(new GboardTopRowSwipeSettings.SlotText("", ""));
                }
                if (legacy.size() > GboardTopRowSwipeSettings.SLOT_COUNT) {
                    legacy = legacy.subList(0, GboardTopRowSwipeSettings.SLOT_COUNT);
                }
                saveCallback.onSave(legacy);
            }
        }, onDismiss);
    }

    // -------------------------------------------------------------------------
    // Controller
    // -------------------------------------------------------------------------

    private static final class Controller {
        private final Activity activity;
        private final KeyRowPreset originalPreset;
        private final List<KeySlot> workingSlots;
        private final Set<RowType> enabledRows;
        private final GboardTopRowSwipeStrings strings;

        /** Maps a visual key view to its index in workingSlots. */
        private final Map<TextView, Integer> keyToIndex = new HashMap<>();
        private final List<TextView> allKeyViews = new ArrayList<>();

        private LinearLayout propertySheet;
        private TextView propertyTitle;
        private EditText displayInput;
        private EditText commitInput;
        private CheckBox useJavaScriptInput;
        private EditText scriptInput;
        private EditText timeoutInput;
        private EditText testInput;
        private Button testButton;
        private TextView testResult;
        private TextView scriptLockButton;
        private boolean scriptLocked = true;
        private KeyListener scriptEditableKeyListener;
        private int scriptEditableInputType;
        private int selectedIndex = -1;
        private boolean commitManuallyEdited;
        private boolean updatingCommitFromDisplay;

        Controller(Activity activity, KeyRowPreset preset, GboardTopRowSwipeStrings strings) {
            this.activity = activity;
            this.originalPreset = preset;
            this.enabledRows = EnumSet.copyOf(preset.enabledRows);
            this.workingSlots = new ArrayList<>();
            for (KeySlot s : preset.slots) {
                this.workingSlots.add(s);
            }
            // Ensure every enabled row has the expected number of slots
            for (RowType row : enabledRows) {
                ensureRowSlots(row);
            }
            this.strings = strings;
        }

        private void ensureRowSlots(RowType row) {
            List<KeySlot> existing = new ArrayList<>();
            for (KeySlot s : workingSlots) {
                if (s.rowType == row) {
                    existing.add(s);
                }
            }
            int needed = row.defaultSlotCount;
            for (int i = existing.size(); i < needed; i++) {
                workingSlots.add(new KeySlot("", "", false, "", 1000, row, i));
            }
        }

        View buildRootView() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(12), dp(8), dp(12), dp(12));

            TextView hint = new TextView(activity);
            hint.setText("Tap a key to edit. Only enabled rows are shown.");
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            hint.setPadding(0, 0, 0, dp(8));
            root.addView(hint);

            // Render rows in natural keyboard order, only if enabled
            for (RowType row : ROW_ORDER) {
                if (!enabledRows.contains(row)) {
                    continue;
                }
                root.addView(buildRowLabel(row.displayName));
                root.addView(buildHorizontalKeyRow(row));
            }

            propertySheet = buildPropertySheet();
            propertySheet.setVisibility(View.GONE);
            root.addView(propertySheet);

            return root;
        }

        private TextView buildRowLabel(String label) {
            TextView tv = new TextView(activity);
            tv.setText(label);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setPadding(dp(4), dp(10), dp(4), dp(4));
            return tv;
        }

        private View buildHorizontalKeyRow(RowType row) {
            HorizontalScrollView hsv = new HorizontalScrollView(activity);
            hsv.setHorizontalScrollBarEnabled(false);

            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);
            rowLayout.setPadding(dp(2), dp(4), dp(2), dp(4));

            // Collect indices of slots belonging to this row, sorted by columnIndex
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
                        0, dp(48), 1f);
                lp.setMargins(dp(3), dp(2), dp(3), dp(2));
                rowLayout.addView(key, lp);
            }

            hsv.addView(rowLayout, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return hsv;
        }

        private TextView buildKeyView(int slotIndex) {
            TextView key = new TextView(activity);
            key.setGravity(Gravity.CENTER);
            key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            key.setTypeface(Typeface.DEFAULT_BOLD);
            key.setMaxLines(1);
            key.setEllipsize(android.text.TextUtils.TruncateAt.END);
            key.setPadding(dp(4), dp(8), dp(4), dp(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF2C2C2E);
            bg.setCornerRadius(dp(8));
            bg.setStroke(dp(1), 0xFF3A3A3C);
            key.setBackground(bg);
            key.setTextColor(Color.WHITE);

            updateKeyLabel(key, slotIndex);
            key.setOnClickListener(v -> selectSlot(slotIndex));
            return key;
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
                label = "JS:" + (label.length() > 4 ? label.substring(0, 4) : label);
            }
            key.setText(label);
        }

        private void selectSlot(int index) {
            selectedIndex = index;
            for (TextView key : allKeyViews) {
                Integer idx = keyToIndex.get(key);
                GradientDrawable bg = new GradientDrawable();
                if (idx != null && idx == index) {
                    bg.setColor(0xFF0A84FF);
                    bg.setStroke(dp(2), 0xFF64B5F6);
                } else {
                    bg.setColor(0xFF2C2C2E);
                    bg.setStroke(dp(1), 0xFF3A3A3C);
                }
                bg.setCornerRadius(dp(8));
                key.setBackground(bg);
            }

            KeySlot slot = workingSlots.get(index);
            propertyTitle.setText(slot.rowType.displayName + "  ·  col " + (slot.columnIndex + 1));
            displayInput.setText(slot.displayText);
            commitInput.setText(slot.commitText);
            useJavaScriptInput.setChecked(slot.isJavaScript);
            scriptInput.setText(slot.scriptText);
            timeoutInput.setText(Integer.toString(slot.timeoutMs));
            testInput.setText("");
            testResult.setText("");
            commitManuallyEdited = true; // display never drives commit text
            updateModeVisibility();
            propertySheet.setVisibility(View.VISIBLE);
        }

        private LinearLayout buildPropertySheet() {
            LinearLayout sheet = new LinearLayout(activity);
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setPadding(dp(8), dp(12), dp(8), dp(4));

            GradientDrawable sheetBg = new GradientDrawable();
            sheetBg.setColor(0xFF1C1C1E);
            sheetBg.setCornerRadius(dp(12));
            sheet.setBackground(sheetBg);
            sheet.setPadding(dp(16), dp(12), dp(16), dp(12));

            propertyTitle = new TextView(activity);
            propertyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            propertyTitle.setTypeface(Typeface.DEFAULT_BOLD);
            propertyTitle.setTextColor(Color.WHITE);
            propertyTitle.setPadding(0, 0, 0, dp(10));
            sheet.addView(propertyTitle);

            sheet.addView(buildLabel(strings.editorDisplayLabel + "  (1 emoji + 1 letter max)"));
            displayInput = buildInput(strings.editorDisplayHint, "");
            displayInput.setFilters(new android.text.InputFilter[] {
                    new android.text.InputFilter.LengthFilter(8)
            });
            sheet.addView(rowWithClear(displayInput, () -> {
                displayInput.setText("");
                displayInput.requestFocus();
            }));

            sheet.addView(buildLabel(strings.editorCommitLabel + "  (sent on key press)"));
            commitInput = buildInput(strings.editorCommitHint, "");
            sheet.addView(rowWithClear(commitInput, () -> {
                commitInput.setText("");
                commitInput.requestFocus();
            }));

            useJavaScriptInput = new CheckBox(activity);
            useJavaScriptInput.setText(strings.editorUseJavaScriptLabel);
            useJavaScriptInput.setTextColor(Color.WHITE);
            sheet.addView(useJavaScriptInput);

            LinearLayout scriptHeader = new LinearLayout(activity);
            scriptHeader.setOrientation(LinearLayout.HORIZONTAL);
            TextView scriptLabel = buildLabel(strings.editorScriptLabel);
            scriptLockButton = new TextView(activity);
            scriptLockButton.setText(strings.editorLockedState);
            scriptLockButton.setTextColor(0xFF64B5F6);
            scriptLockButton.setPadding(dp(12), 0, 0, 0);
            scriptHeader.addView(scriptLabel);
            scriptHeader.addView(scriptLockButton);
            sheet.addView(scriptHeader);

            scriptInput = buildScriptInput("");
            sheet.addView(scriptInput);

            sheet.addView(buildLabel(strings.editorTimeoutLabel));
            timeoutInput = buildTimeoutInput("1000");
            sheet.addView(timeoutInput);

            sheet.addView(buildLabel("Test"));
            testInput = buildInput(strings.editorTestInputHint, "");
            sheet.addView(testInput);
            testButton = new Button(activity);
            testButton.setText(strings.editorTestButton);
            sheet.addView(testButton);
            testResult = new TextView(activity);
            testResult.setTextColor(0xFFAEAEB2);
            testResult.setPadding(0, dp(6), 0, 0);
            sheet.addView(testResult);

            // Display and commit are independent — editing one never rewrites the other.
            displayInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override
                public void afterTextChanged(Editable value) {
                    if (selectedIndex < 0 || updatingCommitFromDisplay) return;
                    String limited = limitDisplayText(value != null ? value.toString() : "");
                    if (value != null && !limited.equals(value.toString())) {
                        updatingCommitFromDisplay = true;
                        value.replace(0, value.length(), limited);
                        updatingCommitFromDisplay = false;
                    }
                    applyCurrentPropertySheetToWorkingSlot();
                    refreshSelectedKeyLabel();
                }
            });
            commitInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override
                public void afterTextChanged(Editable value) {
                    if (selectedIndex < 0) return;
                    applyCurrentPropertySheetToWorkingSlot();
                }
            });
            useJavaScriptInput.setOnCheckedChangeListener((btn, checked) -> {
                updateModeVisibility();
                applyCurrentPropertySheetToWorkingSlot();
                refreshSelectedKeyLabel();
            });
            scriptLockButton.setOnClickListener(v -> {
                scriptLocked = !scriptLocked;
                applyScriptLockState();
            });
            scriptInput.addTextChangedListener(simpleWatcher(this::applyCurrentPropertySheetToWorkingSlot));
            timeoutInput.addTextChangedListener(simpleWatcher(this::applyCurrentPropertySheetToWorkingSlot));
            testButton.setOnClickListener(v -> {
                testResult.setText("(JS test will run against the real QuickJs bridge)");
            });

            return sheet;
        }

        private void refreshSelectedKeyLabel() {
            if (selectedIndex < 0) return;
            for (TextView key : allKeyViews) {
                Integer idx = keyToIndex.get(key);
                if (idx != null && idx == selectedIndex) {
                    updateKeyLabel(key, selectedIndex);
                    break;
                }
            }
        }

        private void applyCurrentPropertySheetToWorkingSlot() {
            if (selectedIndex < 0 || selectedIndex >= workingSlots.size()) {
                return;
            }
            KeySlot old = workingSlots.get(selectedIndex);
            String display = safeTrim(displayInput.getText() != null
                    ? displayInput.getText().toString() : "");
            String commit = safeTrim(commitInput.getText() != null
                    ? commitInput.getText().toString() : "");
            boolean isJs = useJavaScriptInput.isChecked();
            String script = scriptInput.getText() != null
                    ? scriptInput.getText().toString() : "";
            int timeout = parseTimeout(timeoutInput.getText() != null
                    ? timeoutInput.getText().toString() : "1000");

            workingSlots.set(selectedIndex, new KeySlot(
                    display, commit, isJs, script, timeout,
                    old.rowType, old.columnIndex));
        }

        private void updateModeVisibility() {
            boolean js = useJavaScriptInput.isChecked();
            scriptInput.setVisibility(js ? View.VISIBLE : View.GONE);
            scriptLockButton.setVisibility(js ? View.VISIBLE : View.GONE);
            timeoutInput.setVisibility(js ? View.VISIBLE : View.GONE);
            testInput.setVisibility(js ? View.VISIBLE : View.GONE);
            testButton.setVisibility(js ? View.VISIBLE : View.GONE);
            testResult.setVisibility(js ? View.VISIBLE : View.GONE);
            applyScriptLockState();
        }

        private void applyScriptLockState() {
            if (scriptLocked) {
                scriptInput.setKeyListener(null);
                scriptInput.setInputType(InputType.TYPE_NULL);
                scriptLockButton.setText(strings.editorLockedState);
            } else {
                if (scriptEditableKeyListener != null) {
                    scriptInput.setKeyListener(scriptEditableKeyListener);
                }
                scriptInput.setInputType(scriptEditableInputType);
                scriptLockButton.setText(strings.editorUnlockedState);
            }
        }

        KeyRowPreset editedPreset() {
            applyCurrentPropertySheetToWorkingSlot();
            return originalPreset.withSlots(new ArrayList<>(workingSlots));
        }

        // ---- helpers ----

        private TextView buildLabel(String text) {
            TextView tv = new TextView(activity);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(0xFFAEAEB2);
            tv.setPadding(0, dp(8), 0, dp(2));
            return tv;
        }

        private EditText buildInput(String hint, String value) {
            EditText et = new EditText(activity);
            et.setHint(hint);
            et.setText(value);
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
            et.setPadding(dp(10), dp(10), dp(10), dp(10));
            et.setMinHeight(dp(44));
            return et;
        }

        private View rowWithClear(EditText field, Runnable onClear) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(field, fieldLp);
            Button clear = new Button(activity);
            clear.setText("Clear");
            clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            clear.setOnClickListener(v -> {
                if (onClear != null) {
                    onClear.run();
                }
            });
            row.addView(clear, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }

        /**
         * Display label on the key: at most one emoji (or symbol) plus one letter.
         * Commit text is unlimited and independent.
         */
        private static String limitDisplayText(String raw) {
            if (raw == null || raw.isEmpty()) {
                return "";
            }
            java.text.BreakIterator it = java.text.BreakIterator.getCharacterInstance();
            it.setText(raw);
            String emoji = null;
            String letter = null;
            int start = it.first();
            for (int end = it.next(); end != java.text.BreakIterator.DONE;
                    start = end, end = it.next()) {
                String g = raw.substring(start, end);
                if (g.isBlank()) {
                    continue;
                }
                boolean isLetter = g.codePointCount(0, g.length()) == 1
                        && Character.isLetter(g.codePointAt(0));
                if (isLetter) {
                    if (letter == null) {
                        letter = g;
                    }
                } else if (emoji == null) {
                    emoji = g;
                }
                if (emoji != null && letter != null) {
                    break;
                }
            }
            StringBuilder out = new StringBuilder();
            if (emoji != null) {
                out.append(emoji);
            }
            if (letter != null) {
                out.append(letter);
            }
            return out.toString();
        }

        private EditText buildScriptInput(String value) {
            EditText et = new EditText(activity);
            et.setHint(strings.editorScriptHint);
            et.setText(value);
            et.setMinLines(3);
            et.setMaxLines(8);
            et.setGravity(Gravity.TOP | Gravity.START);
            et.setTextColor(Color.WHITE);
            et.setHintTextColor(0xFF636366);
            et.setBackgroundColor(0xFF2C2C2E);
            et.setPadding(dp(10), dp(8), dp(10), dp(8));
            et.setTypeface(Typeface.MONOSPACE);
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            scriptEditableKeyListener = et.getKeyListener();
            scriptEditableInputType = et.getInputType();
            return et;
        }

        private EditText buildTimeoutInput(String value) {
            EditText et = buildInput(strings.editorTimeoutHint, value);
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
            return et;
        }

        private static TextWatcher simpleWatcher(Runnable after) {
            return new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) { after.run(); }
            };
        }

        private int dp(int value) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, value,
                    activity.getResources().getDisplayMetrics()));
        }

        private static String safeTrim(String s) {
            return s == null ? "" : s.trim();
        }

        private static int parseTimeout(String raw) {
            try {
                int v = Integer.parseInt(raw.trim());
                if (v < 1) return 1;
                if (v > 30000) return 30000;
                return v;
            } catch (NumberFormatException e) {
                return 1000;
            }
        }
    }

    private static void runUiActionSafely(Activity activity, String action, Runnable r) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            r.run();
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to " + action, t);
        }
    }
}
