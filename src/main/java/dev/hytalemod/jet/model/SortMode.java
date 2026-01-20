package dev.hytalemod.jet.model;

/**
 * Sorting modes for items
 */
public enum SortMode {
    ALPHABETICAL("A-Z"),
    REVERSE_ALPHABETICAL("Z-A"),
    NAMESPACE("Namespace"),
    CATEGORY("Category");

    private final String displayName;

    SortMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
