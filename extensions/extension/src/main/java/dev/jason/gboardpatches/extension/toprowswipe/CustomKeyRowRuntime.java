package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime bridge between the active {@link KeyRowPreset} and the SoftKey layer.
 *
 * Phase 1 responsibilities:
 * - Resolve the active preset (with legacy fallback)
 * - Supply ordered KeySlots for a given {@link RowType}
 * - Report which rows are currently customizable
 * - Provide a stable snapshot that the existing Top Row Swipe runtime can
 *   consume for the Q–P row without further changes
 *
 * Later phases will use the same snapshot to rebind SoftKeys on Number / A–L / Z–M.
 */
public final class CustomKeyRowRuntime {
    private static final String TAG = "PGP";

    private static final Object LOCK = new Object();
    private static volatile Snapshot cachedSnapshot;
    private static volatile long cachedAtElapsedMs;

    private CustomKeyRowRuntime() {
    }

    // ---- Public API ----

    public static Snapshot current(Context context) {
        if (context == null) {
            return Snapshot.empty();
        }
        long now = android.os.SystemClock.elapsedRealtime();
        Snapshot local = cachedSnapshot;
        if (local != null && (now - cachedAtElapsedMs) < 2_000L) {
            return local;
        }
        synchronized (LOCK) {
            local = cachedSnapshot;
            if (local != null && (now - cachedAtElapsedMs) < 2_000L) {
                return local;
            }
            local = loadSnapshot(context);
            cachedSnapshot = local;
            cachedAtElapsedMs = now;
            return local;
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            cachedSnapshot = null;
            cachedAtElapsedMs = 0L;
        }
        // Also clear the legacy Top Row snapshot so both stay in sync
        try {
            GboardTopRowSwipeRuntime.clearSettingsSnapshotCache();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Ordered slots for a row, padded/truncated to the row's defaultSlotCount.
     * Empty strings for missing slots so SoftKey rebinding always has a value.
     */
    public static List<KeySlot> slotsForRow(Context context, RowType row) {
        Snapshot snap = current(context);
        if (!snap.enabledRows.contains(row)) {
            return Collections.emptyList();
        }
        List<KeySlot> fromPreset = new ArrayList<>();
        for (KeySlot s : snap.slots) {
            if (s.rowType == row) {
                fromPreset.add(s);
            }
        }
        fromPreset.sort((a, b) -> Integer.compare(a.columnIndex, b.columnIndex));

        List<KeySlot> result = new ArrayList<>(row.defaultSlotCount);
        for (int i = 0; i < row.defaultSlotCount; i++) {
            KeySlot found = null;
            for (KeySlot s : fromPreset) {
                if (s.columnIndex == i) {
                    found = s;
                    break;
                }
            }
            if (found != null) {
                result.add(found);
            } else if (i < fromPreset.size()) {
                // Fallback: sequential fill if columnIndex was never set
                result.add(fromPreset.get(i));
            } else {
                result.add(new KeySlot("", "", false, "", 1000, row, i));
            }
        }
        return result;
    }

    public static boolean isRowEnabled(Context context, RowType row) {
        return current(context).enabledRows.contains(row);
    }

    /**
     * Q–P row as legacy SlotText list (for the existing Top Row Swipe runtime).
     */
    public static List<GboardTopRowSwipeSettings.SlotText> legacyQtoPSlots(Context context) {
        List<KeySlot> slots = slotsForRow(context, RowType.Q_TO_P);
        List<GboardTopRowSwipeSettings.SlotText> legacy = new ArrayList<>(slots.size());
        for (KeySlot s : slots) {
            legacy.add(s.toLegacy());
        }
        return legacy;
    }

    // ---- Snapshot ----

    public static final class Snapshot {
        public final boolean available;
        public final String presetId;
        public final String presetName;
        public final Set<RowType> enabledRows;
        public final List<KeySlot> slots;

        Snapshot(boolean available, String presetId, String presetName,
                Set<RowType> enabledRows, List<KeySlot> slots) {
            this.available = available;
            this.presetId = presetId;
            this.presetName = presetName;
            this.enabledRows = enabledRows != null
                    ? Collections.unmodifiableSet(EnumSet.copyOf(enabledRows))
                    : Collections.unmodifiableSet(EnumSet.noneOf(RowType.class));
            this.slots = slots != null
                    ? Collections.unmodifiableList(new ArrayList<>(slots))
                    : Collections.emptyList();
        }

        static Snapshot empty() {
            return new Snapshot(false, null, null, EnumSet.noneOf(RowType.class),
                    Collections.emptyList());
        }
    }

    private static Snapshot loadSnapshot(Context context) {
        try {
            KeyRowPreset active = KeyRowPresetStore.readActivePreset(context);
            if (active == null) {
                // Fall back to pure legacy slots
                List<GboardTopRowSwipeSettings.SlotText> legacy =
                        GboardTopRowSwipeSettings.readSlots(context);
                KeyRowPreset migrated = KeyRowPreset.fromLegacySlots(legacy);
                return new Snapshot(true, migrated.id, migrated.name,
                        migrated.enabledRows, migrated.slots);
            }
            return new Snapshot(true, active.id, active.name,
                    active.enabledRows, active.slots);
        } catch (Throwable t) {
            Log.w(TAG, "CustomKeyRowRuntime snapshot failed", t);
            return Snapshot.empty();
        }
    }
}
