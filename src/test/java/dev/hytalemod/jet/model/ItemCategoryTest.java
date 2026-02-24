package dev.hytalemod.jet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class ItemCategoryTest {

    @Test
    @DisplayName("all categories have display names")
    void allCategoriesHaveDisplayNames() {
        for (ItemCategory category : ItemCategory.values()) {
            assertNotNull(category.getDisplayName());
            assertFalse(category.getDisplayName().isEmpty());
        }
    }

    @Test
    @DisplayName("display names match expected values")
    void displayNames() {
        assertEquals("All", ItemCategory.ALL.getDisplayName());
        assertEquals("Tools", ItemCategory.TOOL.getDisplayName());
        assertEquals("Weapons", ItemCategory.WEAPON.getDisplayName());
        assertEquals("Armor", ItemCategory.ARMOR.getDisplayName());
        assertEquals("Consumables", ItemCategory.CONSUMABLE.getDisplayName());
        assertEquals("Blocks", ItemCategory.BLOCK.getDisplayName());
        assertEquals("Craftable", ItemCategory.CRAFTABLE.getDisplayName());
        assertEquals("Non-Craftable", ItemCategory.NON_CRAFTABLE.getDisplayName());
    }

    @Test
    @DisplayName("enum has exactly 8 values")
    void enumSize() {
        assertEquals(8, ItemCategory.values().length);
    }
}
