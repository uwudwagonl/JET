package dev.hytalemod.jet.util;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.model.ItemCategory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility for categorizing items
 */
public class CategoryUtil {

    public static Set<ItemCategory> getCategories(Item item) {
        Set<ItemCategory> categories = new HashSet<>();
        String itemId = item.getId().toLowerCase();

        // Check if item is craftable
        List<String> recipes = JETPlugin.ITEM_TO_RECIPES.get(item.getId());
        if (recipes != null && !recipes.isEmpty()) {
            categories.add(ItemCategory.CRAFTABLE);
        } else {
            categories.add(ItemCategory.NON_CRAFTABLE);
        }

        // Detect tools
        if (itemId.contains("pickaxe") || itemId.contains("axe") || itemId.contains("shovel") ||
            itemId.contains("hoe") || itemId.contains("shears") || itemId.contains("fishing")) {
            categories.add(ItemCategory.TOOL);
        }

        // Detect weapons
        if (itemId.contains("sword") || itemId.contains("bow") || itemId.contains("crossbow") ||
            itemId.contains("dagger") || itemId.contains("spear") || itemId.contains("mace")) {
            categories.add(ItemCategory.WEAPON);
        }

        // Detect armor
        if (itemId.contains("helmet") || itemId.contains("chestplate") || itemId.contains("leggings") ||
            itemId.contains("boots") || itemId.contains("armor")) {
            categories.add(ItemCategory.ARMOR);
        }

        // Detect consumables
        if (itemId.contains("food") || itemId.contains("potion") || itemId.contains("elixir") ||
            itemId.contains("bread") || itemId.contains("meat") || itemId.contains("fish") ||
            itemId.contains("fruit") || itemId.contains("stew") || itemId.contains("cake")) {
            categories.add(ItemCategory.CONSUMABLE);
        }

        // Detect blocks
        if (itemId.contains("block") || itemId.contains("brick") || itemId.contains("stone") ||
            itemId.contains("wood") || itemId.contains("plank") || itemId.contains("tile") ||
            itemId.contains("ore") || itemId.contains("log") || itemId.contains("dirt") ||
            itemId.contains("sand") || itemId.contains("gravel") || itemId.contains("clay")) {
            categories.add(ItemCategory.BLOCK);
        }

        return categories;
    }

    public static boolean matchesCategory(Item item, ItemCategory category) {
        if (category == ItemCategory.ALL) {
            return true;
        }
        return getCategories(item).contains(category);
    }

    /**
     * Get the primary category for an item (for sorting purposes)
     * Priority: WEAPON > TOOL > ARMOR > CONSUMABLE > BLOCK > CRAFTABLE > NON_CRAFTABLE
     */
    public static ItemCategory getPrimaryCategory(Item item) {
        Set<ItemCategory> categories = getCategories(item);

        // Return first matching category in priority order
        if (categories.contains(ItemCategory.WEAPON)) return ItemCategory.WEAPON;
        if (categories.contains(ItemCategory.TOOL)) return ItemCategory.TOOL;
        if (categories.contains(ItemCategory.ARMOR)) return ItemCategory.ARMOR;
        if (categories.contains(ItemCategory.CONSUMABLE)) return ItemCategory.CONSUMABLE;
        if (categories.contains(ItemCategory.BLOCK)) return ItemCategory.BLOCK;
        if (categories.contains(ItemCategory.CRAFTABLE)) return ItemCategory.CRAFTABLE;
        if (categories.contains(ItemCategory.NON_CRAFTABLE)) return ItemCategory.NON_CRAFTABLE;

        return null; // Uncategorized
    }
}
