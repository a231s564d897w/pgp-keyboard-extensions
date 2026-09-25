package dev.jason.gboardpatches.extension.toprowswipe;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Pure JSON roundtrip checks for {@link KeyRowPreset}.
 * Call {@link #runAll()} from a unit test or debug panel action.
 */
public final class KeyRowPresetJsonTest {
    private KeyRowPresetJsonTest() {
    }

    public static void runAll() throws Exception {
        List<KeySlot> slots = new ArrayList<>();
        slots.add(new KeySlot("A", "a", false, "", 1000, RowType.Q_TO_P, 0));
        slots.add(new KeySlot("JS", "x", true, "return 1", 500, RowType.Q_TO_P, 1));
        KeyRowPreset original = new KeyRowPreset(
                "test-id",
                "Test Preset",
                EnumSet.of(RowType.Q_TO_P, RowType.NUMBER),
                slots,
                true,
                12345L);
        JSONObject json = original.toJson();
        KeyRowPreset restored = KeyRowPreset.fromJson(json);
        if (!"test-id".equals(restored.id)) {
            throw new AssertionError("id");
        }
        if (!"Test Preset".equals(restored.name)) {
            throw new AssertionError("name");
        }
        if (!restored.enabledRows.contains(RowType.NUMBER)
                || !restored.enabledRows.contains(RowType.Q_TO_P)) {
            throw new AssertionError("rows");
        }
        if (restored.slots.size() != 2) {
            throw new AssertionError("slots size");
        }
        if (!restored.slots.get(1).isJavaScript) {
            throw new AssertionError("js flag");
        }
        KeyRowPreset renamed = restored.withName("Renamed");
        if (!"Renamed".equals(renamed.name) || !"test-id".equals(renamed.id)) {
            throw new AssertionError("withName");
        }
    }
}
