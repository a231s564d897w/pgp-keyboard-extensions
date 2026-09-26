package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies a {@link KeyRowPreset} fully: which rows are custom + their slots.
 *
 * Example presets:
 * - Preset "Numbers"  → enabledRows = { NUMBER }
 * - Preset "Bottom"   → enabledRows = { Z_TO_M }
 * - Preset "Full"     → enabledRows = { NUMBER, Q_TO_P, A_TO_L, Z_TO_M }
 * - Preset "Classic"  → enabledRows = { Q_TO_P }
 *
 * Cycling / swiping between presets switches this entire configuration, not only Q–P text.
 */
public final class PresetApplyHelper {
    private PresetApplyHelper() {
    }

    /**
     * Cycle to the next enabled preset and apply it.
     * @return the preset that was applied, or null
     */
    public static KeyRowPreset cycleAndApply(Context context) {
        if (context == null) {
            return null;
        }
        List<KeyRowPreset> all = KeyRowPresetStore.readAll(context);
        if (all == null || all.isEmpty()) {
            return null;
        }
        List<KeyRowPreset> usable = new ArrayList<>();
        for (KeyRowPreset p : all) {
            if (p != null && p.enabled) {
                usable.add(p);
            }
        }
        if (usable.isEmpty()) {
            usable.addAll(all);
        }
        String currentId = KeyRowPresetStore.readActivePresetId(context);
        int idx = 0;
        for (int i = 0; i < usable.size(); i++) {
            if (usable.get(i).id != null && usable.get(i).id.equals(currentId)) {
                idx = (i + 1) % usable.size();
                break;
            }
        }
        KeyRowPreset next = usable.get(idx);
        KeyRowPresetStore.writeActivePresetId(context, next.id);
        apply(context, next, true);
        return next;
    }

    /** Apply a specific preset by id. */
    public static KeyRowPreset applyById(Context context, String presetId) {
        if (context == null || presetId == null) {
            return null;
        }
        KeyRowPresetStore.writeActivePresetId(context, presetId);
        KeyRowPreset p = KeyRowPresetStore.readActivePreset(context);
        if (p != null) {
            apply(context, p, true);
        }
        return p;
    }

    /**
     * Push this preset into runtime:
     * 1. Custom top-row mode on if Q_TO_P is in enabledRows
     * 2. Legacy top-row slots written from Q_TO_P slots (when enabled)
     * 3. Runtime snapshot invalidated so SoftKeyBinder paints Number / A–Ñ / Z–M
     * 4. Immediate SoftKey rebind when a keyboard view is known
     */
    public static void apply(Context context, KeyRowPreset preset, boolean toast) {
        if (context == null || preset == null) {
            return;
        }
        Set<RowType> rows = preset.enabledRows;

        // Top-row swipe feature on when this preset includes Q–P
        boolean wantsQp = rows.contains(RowType.Q_TO_P);
        GboardTopRowSwipeSettings.writeEnabled(context, wantsQp);
        GboardTopRowSwipeSettings.writeEnglishQwertyEnabled(context, true);

        if (wantsQp) {
            List<KeySlot> qRow = preset.slotsForRow(RowType.Q_TO_P);
            for (int i = 0; i < GboardTopRowSwipeSettings.SLOT_COUNT; i++) {
                GboardTopRowSwipeSettings.SlotText legacy =
                        i < qRow.size()
                                ? qRow.get(i).toLegacy()
                                : new GboardTopRowSwipeSettings.SlotText("", "");
                GboardTopRowSwipeSettings.writeSlot(context, i, legacy);
            }
        }

        CustomKeyRowRuntime.invalidate();

        try {
            View anchor = dev.jason.gboardpatches.extension.overlay.OverlayWindowHelper.anchorView();
            if (anchor != null) {
                CustomKeyRowSoftKeyBinder.applyEnabledRows(context, anchor);
            }
        } catch (Throwable ignored) {
        }

        if (toast) {
            StringBuilder rowNames = new StringBuilder();
            for (RowType r : RowType.values()) {
                if (rows.contains(r)) {
                    if (rowNames.length() > 0) {
                        rowNames.append(" · ");
                    }
                    rowNames.append(r.displayName);
                }
            }
            if (rowNames.length() == 0) {
                rowNames.append("none");
            }
            int filled = 0;
            for (KeySlot s : preset.slots) {
                if (s != null && s.displayText != null && !s.displayText.isEmpty()) {
                    filled++;
                }
            }
            Toast.makeText(context,
                    preset.name + "\n" + rowNames + " · " + filled + " keys",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
