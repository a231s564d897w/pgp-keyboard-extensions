package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies custom key labels/commits from the active preset onto SoftKey views
 * for rows beyond the original Top Row Swipe target (Number / A–L / Z–M).
 *
 * Row detection prefers stock label fingerprints over pure Y-coordinate banding.
 */
public final class CustomKeyRowSoftKeyBinder {
    private static final String TAG = "PGP";

    private static final long APPLY_THROTTLE_MS = 80L;
    private static volatile long lastApplyElapsedMs;

    private static final Set<String> NUMBER_LABELS = setOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "!", "@", "#", "$", "%", "^", "&", "*", "(", ")");
    private static final Set<String> A_TO_L_LABELS = setOf(
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",
            "ñ", "Ñ", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");
    private static final Set<String> Z_TO_M_LABELS = setOf(
            "z", "x", "c", "v", "b", "n", "m",
            "Z", "X", "C", "V", "B", "N", "M", ",", ".");

    private CustomKeyRowSoftKeyBinder() {
    }

    /**
     * Public entry for a keyboard layout/bind hook.
     * Throttled; safe to call from afterSoftKeyBound.
     */
    public static void applyEnabledRowsThrottled(Context context, View anyKeyboardView) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastApplyElapsedMs < APPLY_THROTTLE_MS) {
            return;
        }
        lastApplyElapsedMs = now;
        try {
            dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper
                    .rememberImeView(anyKeyboardView);
        } catch (Throwable ignored) {
        }
        View root = findKeyboardRoot(anyKeyboardView);
        applyEnabledRows(context, root != null ? root : anyKeyboardView);
    }

    public static void applyEnabledRows(Context context, View keyboardRoot) {
        if (context == null || keyboardRoot == null) {
            return;
        }
        try {
            CustomKeyRowRuntime.Snapshot snap = CustomKeyRowRuntime.current(context);
            if (!snap.available) {
                return;
            }
            for (RowType row : snap.enabledRows) {
                if (row == RowType.Q_TO_P) {
                    continue;
                }
                applyRow(context, keyboardRoot, row);
            }
        } catch (Throwable t) {
            Log.w(TAG, "applyEnabledRows failed", t);
        }
    }

    public static void applyRow(Context context, View keyboardRoot, RowType row) {
        if (context == null || keyboardRoot == null || row == null) {
            return;
        }
        if (!CustomKeyRowRuntime.isRowEnabled(context, row)) {
            return;
        }
        try {
            List<KeySlot> slots = new ArrayList<>(
                    CustomKeyRowRuntime.slotsForRow(context, row));
            if (row == RowType.A_TO_L) {
                LanguageRowListener.applyToAtoLSlots(context, slots);
            }
            if (slots.isEmpty()) {
                return;
            }

            List<View> softKeyViews = collectSoftKeyViews(keyboardRoot);
            if (softKeyViews.isEmpty()) {
                return;
            }

            ClassLoader cl = keyboardRoot.getClass().getClassLoader();
            if (cl == null) {
                return;
            }
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(cl);

            List<View> band = pickBandForRow(handles, softKeyViews, row, slots.size());
            if (band.size() < Math.min(3, slots.size())) {
                return;
            }

            int count = Math.min(slots.size(), band.size());
            int rebound = 0;
            for (int i = 0; i < count; i++) {
                KeySlot slot = slots.get(i);
                if (slot.displayText == null || slot.displayText.isEmpty()) {
                    continue;
                }
                rebindOne(handles, band.get(i), slot);
                rebound++;
            }
            try {
                dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                        "SoftKey", "Applied " + row + " slots=" + rebound);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.w(TAG, "applyRow failed for " + row, t);
        }
    }

    private static View findKeyboardRoot(View start) {
        if (start == null) {
            return null;
        }
        View v = start;
        View best = start;
        int depth = 0;
        while (v.getParent() instanceof View && depth < 12) {
            v = (View) v.getParent();
            best = v;
            depth++;
        }
        return best;
    }

    private static List<View> collectSoftKeyViews(View root) {
        List<View> out = new ArrayList<>();
        collectRecursive(root, out);
        return out;
    }

    private static void collectRecursive(View view, List<View> out) {
        if (view == null) {
            return;
        }
        String name = view.getClass().getName();
        if (name != null && (name.contains("SoftKeyView")
                || name.contains("SoftKey")
                || name.endsWith("KeyView"))) {
            out.add(view);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collectRecursive(group.getChildAt(i), out);
            }
        }
    }

    private static List<View> pickBandForRow(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            List<View> all,
            RowType row,
            int needed) {
        if (all.isEmpty() || needed <= 0) {
            return java.util.Collections.emptyList();
        }

        List<View> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> {
            int dy = Integer.compare(a.getTop(), b.getTop());
            if (dy != 0) {
                return dy;
            }
            return Integer.compare(a.getLeft(), b.getLeft());
        });

        List<List<View>> bands = groupIntoBands(sorted);
        if (bands.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        Set<String> fingerprint = fingerprintFor(row);
        int bestIndex = -1;
        int bestScore = -1;
        for (int bi = 0; bi < bands.size(); bi++) {
            List<View> band = bands.get(bi);
            int score = 0;
            for (View v : band) {
                String label = readLabel(handles, v);
                if (label != null && fingerprint.contains(label)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestIndex = bi;
            }
        }

        if (bestScore < 1) {
            switch (row) {
                case NUMBER:
                    bestIndex = 0;
                    break;
                case A_TO_L:
                    bestIndex = Math.min(2, bands.size() - 1);
                    break;
                case Z_TO_M:
                    bestIndex = Math.min(3, bands.size() - 1);
                    break;
                default:
                    bestIndex = Math.min(1, bands.size() - 1);
                    break;
            }
        }

        List<View> band = new ArrayList<>(
                bands.get(Math.max(0, Math.min(bestIndex, bands.size() - 1))));
        band.sort((a, b) -> Integer.compare(a.getLeft(), b.getLeft()));

        if (band.size() > needed) {
            if (row == RowType.A_TO_L || row == RowType.Z_TO_M) {
                int bestStart = 0;
                int bestWindowScore = -1;
                for (int s = 0; s + needed <= band.size(); s++) {
                    int windowScore = 0;
                    for (int j = 0; j < needed; j++) {
                        String label = readLabel(handles, band.get(s + j));
                        if (label != null && fingerprint.contains(label)) {
                            windowScore++;
                        }
                    }
                    if (windowScore > bestWindowScore) {
                        bestWindowScore = windowScore;
                        bestStart = s;
                    }
                }
                return band.subList(bestStart, bestStart + needed);
            }
            int start = Math.max(0, (band.size() - needed) / 2);
            return band.subList(start, start + needed);
        }
        return band;
    }

    private static List<List<View>> groupIntoBands(List<View> sortedByY) {
        List<List<View>> bands = new ArrayList<>();
        List<View> current = new ArrayList<>();
        int lastTop = Integer.MIN_VALUE;
        final int threshold = 28;
        for (View v : sortedByY) {
            if (current.isEmpty() || Math.abs(v.getTop() - lastTop) <= threshold) {
                current.add(v);
                if (current.size() == 1) {
                    lastTop = v.getTop();
                }
            } else {
                bands.add(current);
                current = new ArrayList<>();
                current.add(v);
                lastTop = v.getTop();
            }
        }
        if (!current.isEmpty()) {
            bands.add(current);
        }
        return bands;
    }

    private static Set<String> fingerprintFor(RowType row) {
        switch (row) {
            case NUMBER:
                return NUMBER_LABELS;
            case A_TO_L:
                return A_TO_L_LABELS;
            case Z_TO_M:
                return Z_TO_M_LABELS;
            default:
                return java.util.Collections.emptySet();
        }
    }

    private static String readLabel(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles, View softKeyView) {
        try {
            Object metadata = handles.softKeyMetadataField.get(softKeyView);
            String label = handles.extractPrimaryLabel(metadata);
            if (label == null || label.isBlank()) {
                return null;
            }
            return label.trim().toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Mirrors Top Row Swipe's patchIncomingSoftKeyMetadata:
     * copy original metadata → set primary label → replace PRESS action → rebind.
     */
    private static void rebindOne(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            View softKeyView,
            KeySlot slot) {
        try {
            Object originalMetadata = handles.softKeyMetadataField.get(softKeyView);
            Object builder = handles.keyMetadataBuilderConstructor.newInstance();
            if (originalMetadata != null) {
                try {
                    handles.copyKeyMetadataMethod.invoke(builder, originalMetadata);
                } catch (Throwable ignored) {
                    // Fresh builder is acceptable if copy fails
                }
                try {
                    GboardTopRowSwipeRuntimeSupport.LabelSet labelSet =
                            GboardTopRowSwipeRuntimeSupport.appendOrReplaceTextLabelIds(
                                    handles.extractKeyLabelIds(originalMetadata),
                                    handles.extractKeyLabelTexts(originalMetadata),
                                    GboardTopRowSwipeRuntimeSupport.PRIMARY_LABEL_VIEW_ID,
                                    slot.displayText);
                    handles.setKeyLabelTextsMethod.invoke(
                            builder, labelSet.ids, labelSet.texts);
                } catch (Throwable ignored) {
                    // Label paint is best-effort
                }
            }
            String commit = slot.commitText != null && !slot.commitText.isEmpty()
                    ? slot.commitText : slot.displayText;
            Object pressAction = handles.buildPlainTextAction(
                    handles.pressActionType, commit, slot.displayText);
            if (pressAction != null) {
                handles.replaceActionOnKeyMetadataBuilder(
                        builder, handles.pressActionType, pressAction);
            }
            Object metadata = handles.buildKeyMetadataMethod.invoke(builder);
            if (metadata != null) {
                handles.rebindSoftKeyView(softKeyView, metadata);
            }
        } catch (Throwable t) {
            Log.d(TAG, "rebindOne failed col=" + slot.columnIndex, t);
        }
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
