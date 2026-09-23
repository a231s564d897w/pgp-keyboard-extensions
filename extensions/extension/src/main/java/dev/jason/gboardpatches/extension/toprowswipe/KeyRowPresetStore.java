package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistence for Custom Key Row presets.
 *
 * - Stores the full preset list as a JSON array in SharedPreferences.
 * - On first run, migrates the existing 10-slot Top Row into a default preset
 *   so no user data is lost.
 * - Tracks which preset is currently active.
 */
public final class KeyRowPresetStore {
    private static final String TAG = "PGP";

    private static final String PREFS_NAME = "gboard_patches_key_row_presets";
    private static final String KEY_PRESETS_JSON = "presets_json";
    private static final String KEY_ACTIVE_PRESET_ID = "active_preset_id";
    private static final String KEY_MIGRATED = "legacy_migrated_v1";

    private KeyRowPresetStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---- Public API ----

    public static List<KeyRowPreset> readAll(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        ensureMigrated(context);
        try {
            String raw = prefs(context).getString(KEY_PRESETS_JSON, "[]");
            JSONArray arr = new JSONArray(raw);
            List<KeyRowPreset> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(KeyRowPreset.fromJson(arr.getJSONObject(i)));
            }
            return list;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read presets", t);
            return Collections.emptyList();
        }
    }

    public static void writeAll(Context context, List<KeyRowPreset> presets) {
        if (context == null || presets == null) {
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            for (KeyRowPreset p : presets) {
                arr.put(p.toJson());
            }
            prefs(context).edit().putString(KEY_PRESETS_JSON, arr.toString()).apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to write presets", t);
        }
    }

    public static String readActivePresetId(Context context) {
        if (context == null) {
            return null;
        }
        ensureMigrated(context);
        return prefs(context).getString(KEY_ACTIVE_PRESET_ID, null);
    }

    public static void writeActivePresetId(Context context, String id) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putString(KEY_ACTIVE_PRESET_ID, id).apply();
    }

    public static KeyRowPreset readActivePreset(Context context) {
        List<KeyRowPreset> all = readAll(context);
        String activeId = readActivePresetId(context);
        if (activeId != null) {
            for (KeyRowPreset p : all) {
                if (activeId.equals(p.id)) {
                    return p;
                }
            }
        }
        // Fall back to first enabled, or first overall
        for (KeyRowPreset p : all) {
            if (p.enabled) {
                return p;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    public static void savePreset(Context context, KeyRowPreset preset) {
        if (context == null || preset == null) {
            return;
        }
        List<KeyRowPreset> all = new ArrayList<>(readAll(context));
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(preset.id)) {
                all.set(i, preset);
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(preset);
        }
        writeAll(context, all);
    }

    public static void deletePreset(Context context, String id) {
        if (context == null || id == null) {
            return;
        }
        List<KeyRowPreset> all = new ArrayList<>(readAll(context));
        all.removeIf(p -> id.equals(p.id));
        writeAll(context, all);
        if (id.equals(readActivePresetId(context))) {
            writeActivePresetId(context, all.isEmpty() ? null : all.get(0).id);
        }
    }

    // ---- Export / Import (portable JSON) ----

    /**
     * Full backup document:
     * { "format": "gboardpatches.key-row-presets", "version": 1,
     *   "activePresetId": "...", "presets": [ ... ] }
     */
    public static String exportToJson(Context context) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "gboardpatches.key-row-presets");
            root.put("version", 1);
            root.put("activePresetId", readActivePresetId(context));
            JSONArray arr = new JSONArray();
            for (KeyRowPreset p : readAll(context)) {
                arr.put(p.toJson());
            }
            root.put("presets", arr);
            return root.toString(2);
        } catch (Throwable t) {
            Log.w(TAG, "exportToJson failed", t);
            return "{}";
        }
    }

    /**
     * Import a previously exported document. Replaces the current preset list.
     * Returns the number of presets loaded, or -1 on failure.
     */
    public static int importFromJson(Context context, String json) {
        if (context == null || json == null || json.isBlank()) {
            return -1;
        }
        try {
            JSONObject root = new JSONObject(json);
            String format = root.optString("format", "");
            if (!format.isEmpty() && !"gboardpatches.key-row-presets".equals(format)) {
                Log.w(TAG, "Unexpected preset export format: " + format);
                // still try to parse if presets array exists
            }
            JSONArray arr = root.optJSONArray("presets");
            if (arr == null) {
                return -1;
            }
            List<KeyRowPreset> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(KeyRowPreset.fromJson(arr.getJSONObject(i)));
            }
            if (list.isEmpty()) {
                return -1;
            }
            writeAll(context, list);
            String activeId = root.optString("activePresetId", null);
            boolean found = false;
            if (activeId != null) {
                for (KeyRowPreset p : list) {
                    if (activeId.equals(p.id)) {
                        found = true;
                        break;
                    }
                }
            }
            writeActivePresetId(context, found ? activeId : list.get(0).id);
            CustomKeyRowRuntime.invalidate();
            return list.size();
        } catch (Throwable t) {
            Log.w(TAG, "importFromJson failed", t);
            return -1;
        }
    }

    // ---- Migration from the original 10-slot Top Row ----

    private static void ensureMigrated(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_MIGRATED, false)) {
            return;
        }
        synchronized (KeyRowPresetStore.class) {
            if (p.getBoolean(KEY_MIGRATED, false)) {
                return;
            }
            try {
                List<GboardTopRowSwipeSettings.SlotText> legacy =
                        GboardTopRowSwipeSettings.readSlots(context);
                KeyRowPreset defaultPreset = KeyRowPreset.fromLegacySlots(legacy);
                JSONArray arr = new JSONArray();
                arr.put(defaultPreset.toJson());
                p.edit()
                        .putString(KEY_PRESETS_JSON, arr.toString())
                        .putString(KEY_ACTIVE_PRESET_ID, defaultPreset.id)
                        .putBoolean(KEY_MIGRATED, true)
                        .apply();
                Log.i(TAG, "Migrated legacy Top Row slots into default preset");
            } catch (Throwable t) {
                Log.w(TAG, "Legacy slot migration failed", t);
                // Still mark migrated so we don't loop forever
                p.edit().putBoolean(KEY_MIGRATED, true).apply();
            }
        }
    }
}
