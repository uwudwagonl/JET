package dev.hytalemod.jet.model;

/**
 * Grid layout options for item display
 */
public enum GridLayout {
    COMPACT_9x8(9, 8, "Compact (9x8)"),
    STANDARD_11x6(11, 6, "Standard (11x6)"),
    WIDE_13x5(13, 5, "Wide (13x5)"),
    LARGE_15x4(15, 4, "Large (15x4)");

    private final int columns;
    private final int rows;
    private final String displayName;

    GridLayout(int columns, int rows, String displayName) {
        this.columns = columns;
        this.rows = rows;
        this.displayName = displayName;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getTotalSlots() {
        return columns * rows;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GridLayout next() {
        GridLayout[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
