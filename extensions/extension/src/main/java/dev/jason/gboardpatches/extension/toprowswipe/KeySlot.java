package dev.jason.gboardpatches.extension.toprowswipe;

/**
 * One custom key. Extends the original SlotText fields with row membership
 * so a single preset can hold Number / Q–P / A–L / Z–M rows at once.
 *
 * Backward compatible: the old 10-slot Top Row is simply a list of KeySlots
 * all belonging to {@link RowType#Q_TO_P}.
 */
public final class KeySlot {
    public final String displayText;
    public final String commitText;
    public final boolean isJavaScript;
    public final String scriptText;
    public final int timeoutMs;
    public final RowType rowType;
    /** 0-based index inside its row (e.g. 0 = Q, 9 = P on the Q–P row). */
    public final int columnIndex;

    public KeySlot(String displayText, String commitText, boolean isJavaScript,
            String scriptText, int timeoutMs, RowType rowType, int columnIndex) {
        this.displayText = displayText != null ? displayText : "";
        this.commitText = commitText != null ? commitText : "";
        this.isJavaScript = isJavaScript;
        this.scriptText = scriptText != null ? scriptText : "";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 1000;
        this.rowType = rowType != null ? rowType : RowType.Q_TO_P;
        this.columnIndex = Math.max(0, columnIndex);
    }

    /** Convenience from the legacy SlotText (always Q–P row). */
    public static KeySlot fromLegacy(GboardTopRowSwipeSettings.SlotText legacy, int columnIndex) {
        if (legacy == null) {
            return new KeySlot("", "", false, "", 1000, RowType.Q_TO_P, columnIndex);
        }
        return new KeySlot(
                legacy.displayText,
                legacy.commitText,
                legacy.isJavaScript,
                legacy.scriptText,
                legacy.timeoutMs,
                RowType.Q_TO_P,
                columnIndex);
    }

    /** Convert back to the legacy SlotText for existing write paths. */
    public GboardTopRowSwipeSettings.SlotText toLegacy() {
        return new GboardTopRowSwipeSettings.SlotText(
                displayText, commitText, isJavaScript, scriptText, timeoutMs);
    }

    public KeySlot withDisplay(String display) {
        return new KeySlot(display, commitText, isJavaScript, scriptText, timeoutMs,
                rowType, columnIndex);
    }

    public KeySlot withCommit(String commit) {
        return new KeySlot(displayText, commit, isJavaScript, scriptText, timeoutMs,
                rowType, columnIndex);
    }

    public KeySlot withJavaScript(boolean js, String script, int timeout) {
        return new KeySlot(displayText, commitText, js, script, timeout, rowType, columnIndex);
    }
}
