package dev.jason.gboardpatches.extension.protonpass;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local persistence for Proton Pass overlay entries.
 *
 * Not a vault: stores only what was published via
 * {@link ProtonPassAutofillBridge#publishEntries} or the demo loader so the
 * floating window is useful across keyboard sessions until real SDK data arrives.
 */
public final class ProtonPassEntryStore {
    private static final String TAG = "PGP";
    private static final String PREFS = "gboard_patches_proton_pass_entries";
    private static final String KEY_JSON = "entries_json";

    private ProtonPassEntryStore() {
    }

    public static void save(Context context, List<GboardProtonPassOverlayHost.Entry> entries) {
        if (context == null) {
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            if (entries != null) {
                for (GboardProtonPassOverlayHost.Entry e : entries) {
                    if (e == null) {
                        continue;
                    }
                    JSONObject o = new JSONObject();
                    o.put("title", e.title != null ? e.title : "");
                    o.put("username", e.username != null ? e.username : "");
                    o.put("password", e.password != null ? e.password : "");
                    arr.put(o);
                }
            }
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_JSON, arr.toString())
                    .apply();
        } catch (Throwable t) {
            Log.w(TAG, "ProtonPassEntryStore.save failed", t);
        }
    }

    public static List<GboardProtonPassOverlayHost.Entry> load(Context context) {
        List<GboardProtonPassOverlayHost.Entry> out = new ArrayList<>();
        if (context == null) {
            return out;
        }
        try {
            String json = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_JSON, "[]");
            JSONArray arr = new JSONArray(json != null ? json : "[]");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new GboardProtonPassOverlayHost.Entry(
                        o.optString("title", ""),
                        o.optString("username", ""),
                        o.optString("password", "")));
            }
        } catch (Throwable t) {
            Log.w(TAG, "ProtonPassEntryStore.load failed", t);
        }
        return out;
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_JSON)
                .apply();
    }
}
