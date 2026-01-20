package dev.hytalemod.jet.model;

/**
 * Categories for item classification
 */
public enum ItemCategory {
    ALL("All"),
    TOOL("Tools"),
    WEAPON("Weapons"),
    ARMOR("Armor"),
    CONSUMABLE("Consumables"),
    BLOCK("Blocks"),
    CRAFTABLE("Craftable"),
    NON_CRAFTABLE("Non-Craftable");

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
