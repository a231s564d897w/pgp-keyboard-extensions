package dev.jason.gboardpatches.extension.toprowswipe;

/**
 * Identifies which physical keyboard row a set of custom keys occupies.
 * Used by the keyboard-style editor and by the future multi-row runtime.
 */
public enum RowType {
    /** Number / symbols row (the row above the letter keys on most layouts). */
    NUMBER(10, "Number"),

    /** Q–P row (the classic 10-slot top-row swipe target). */
    Q_TO_P(10, "Q–P"),

    /**
     * A–L / A–Ñ home row.
     * The language listener decides whether the final key is shown as L or Ñ.
     */
    A_TO_L(9, "A–L / A–Ñ"),

    /** Z–M bottom letter row. */
    Z_TO_M(7, "Z–M");

    public final int defaultSlotCount;
    public final String displayName;

    RowType(int defaultSlotCount, String displayName) {
        this.defaultSlotCount = defaultSlotCount;
        this.displayName = displayName;
    }
}
