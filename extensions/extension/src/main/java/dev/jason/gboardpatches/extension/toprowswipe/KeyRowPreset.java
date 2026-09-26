package dev.jason.gboardpatches.extension.toprowswipe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A named collection of custom keys that can target one or more keyboard rows.
 *
 * - id          unique, stable
 * - name        user-visible title
 * - enabledRows which physical rows this preset occupies
 * - slots       ordered KeySlots (each knows its RowType + columnIndex)
 * - enabled     whether the preset is active / shown in Access Points
 *
 * The original 10-slot Top Row becomes the default preset with only Q_TO_P enabled.
 */
public final class KeyRowPreset {
    public final String id;
    public final String name;
    public final Set<RowType> enabledRows;
    public final List<KeySlot> slots;
    public final boolean enabled;
    public final long updatedAtMs;

    public KeyRowPreset(String id, String name, Set<RowType> enabledRows,
            List<KeySlot> slots, boolean enabled, long updatedAtMs) {
        this.id = id != null && !id.isEmpty() ? id : UUID.randomUUID().toString();
        this.name = name != null && !name.isEmpty() ? name : "Untitled";
        this.enabledRows = enabledRows != null && !enabledRows.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(enabledRows))
                : Collections.unmodifiableSet(EnumSet.of(RowType.Q_TO_P));
        this.slots = slots != null
                ? Collections.unmodifiableList(new ArrayList<>(slots))
                : Collections.emptyList();
        this.enabled = enabled;
        this.updatedAtMs = updatedAtMs > 0 ? updatedAtMs : System.currentTimeMillis();
    }

    /** Build the default preset from the existing 10-slot Top Row data. */
    public static KeyRowPreset fromLegacySlots(List<GboardTopRowSwipeSettings.SlotText> legacy) {
        List<KeySlot> slots = new ArrayList<>();
        if (legacy != null) {
            for (int i = 0; i < legacy.size(); i++) {
                slots.add(KeySlot.fromLegacy(legacy.get(i), i));
            }
        }
        // Pad to 10 if needed
        while (slots.size() < RowType.Q_TO_P.defaultSlotCount) {
            slots.add(new KeySlot("", "", false, "", 1000, RowType.Q_TO_P, slots.size()));
        }
        return new KeyRowPreset(
                "default-q-to-p",
                "Q–P (default)",
                EnumSet.of(RowType.Q_TO_P),
                slots,
                true,
                System.currentTimeMillis());
    }

    public List<KeySlot> slotsForRow(RowType row) {
        List<KeySlot> result = new ArrayList<>();
        for (KeySlot s : slots) {
            if (s.rowType == row) {
                result.add(s);
            }
        }
        // Sort by columnIndex so the visual editor stays ordered
        result.sort((a, b) -> Integer.compare(a.columnIndex, b.columnIndex));
        return result;
    }

    public KeyRowPreset withName(String newName) {
        return new KeyRowPreset(id, newName, enabledRows, slots, enabled, System.currentTimeMillis());
    }

    public KeyRowPreset withEnabled(boolean newEnabled) {
        return new KeyRowPreset(id, name, enabledRows, slots, newEnabled, System.currentTimeMillis());
    }

    public KeyRowPreset withSlots(List<KeySlot> newSlots) {
        return new KeyRowPreset(id, name, enabledRows, newSlots, enabled, System.currentTimeMillis());
    }

    public KeyRowPreset withEnabledRows(Set<RowType> rows) {
        return new KeyRowPreset(id, name, rows, slots, enabled, System.currentTimeMillis());
    }

    // ---- JSON (for Backup / Restore and SharedPreferences) ----

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("enabled", enabled);
        o.put("updatedAtMs", updatedAtMs);
        JSONArray rows = new JSONArray();
        for (RowType r : enabledRows) {
            rows.put(r.name());
        }
        o.put("enabledRows", rows);
        JSONArray slotArr = new JSONArray();
        for (KeySlot s : slots) {
            JSONObject so = new JSONObject();
            so.put("displayText", s.displayText);
            so.put("commitText", s.commitText);
            so.put("isJavaScript", s.isJavaScript);
            so.put("scriptText", s.scriptText);
            so.put("timeoutMs", s.timeoutMs);
            so.put("rowType", s.rowType.name());
            so.put("columnIndex", s.columnIndex);
            slotArr.put(so);
        }
        o.put("slots", slotArr);
        return o;
    }

    public static KeyRowPreset fromJson(JSONObject o) throws Exception {
        String id = o.optString("id", UUID.randomUUID().toString());
        String name = o.optString("name", "Untitled");
        boolean enabled = o.optBoolean("enabled", true);
        long updated = o.optLong("updatedAtMs", System.currentTimeMillis());

        Set<RowType> rows = EnumSet.noneOf(RowType.class);
        JSONArray rowArr = o.optJSONArray("enabledRows");
        if (rowArr != null) {
            for (int i = 0; i < rowArr.length(); i++) {
                try {
                    rows.add(RowType.valueOf(rowArr.getString(i)));
                } catch (Exception ignored) {
                }
            }
        }
        if (rows.isEmpty()) {
            rows.add(RowType.Q_TO_P);
        }

        List<KeySlot> slots = new ArrayList<>();
        JSONArray slotArr = o.optJSONArray("slots");
        if (slotArr != null) {
            for (int i = 0; i < slotArr.length(); i++) {
                JSONObject so = slotArr.getJSONObject(i);
                RowType rt = RowType.Q_TO_P;
                try {
                    rt = RowType.valueOf(so.optString("rowType", "Q_TO_P"));
                } catch (Exception ignored) {
                }
                slots.add(new KeySlot(
                        so.optString("displayText", ""),
                        so.optString("commitText", ""),
                        so.optBoolean("isJavaScript", false),
                        so.optString("scriptText", ""),
                        so.optInt("timeoutMs", 1000),
                        rt,
                        so.optInt("columnIndex", i)));
            }
        }
        return new KeyRowPreset(id, name, rows, slots, enabled, updated);
    }
}
