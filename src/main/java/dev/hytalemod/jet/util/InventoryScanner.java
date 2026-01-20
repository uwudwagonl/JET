package dev.hytalemod.jet.util;

import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for scanning player inventory and counting items.
 */
public class InventoryScanner {

    /**
     * Counts the total quantity of a specific item across all inventory sections.
     *
     * @param entity The living entity (player) to scan
     * @param itemId The item ID to count
     * @return The total quantity of the item found, or 0 if none
     */
    public static int countItemInInventory(LivingEntity entity, String itemId) {
        if (entity == null || itemId == null) {
            return 0;
        }

        try {
            Inventory inventory = entity.getInventory();
            if (inventory == null) {
                return 0;
            }

            int totalCount = 0;

            // Scan all inventory sections
            totalCount += countInContainer(inventory.getHotbar(), itemId);
            totalCount += countInContainer(inventory.getStorage(), itemId);
            totalCount += countInContainer(inventory.getBackpack(), itemId);
            totalCount += countInContainer(inventory.getArmor(), itemId);
            totalCount += countInContainer(inventory.getUtility(), itemId);
            totalCount += countInContainer(inventory.getTools(), itemId);

            return totalCount;
        } catch (Exception e) {
            // Silently fail if inventory access isn't available
            return 0;
        }
    }

    /**
     * Counts items in a specific container section.
     *
     * @param container The item container to scan
     * @param itemId The item ID to count
     * @return The quantity found in this container
     */
    private static int countInContainer(ItemContainer container, String itemId) {
        if (container == null || itemId == null) {
            return 0;
        }

        try {
            return container.countItemStacks(itemStack -> {
                if (itemStack == null || itemStack.getItemId() == null) {
                    return false;
                }
                return itemStack.getItemId().equals(itemId);
            });
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Gets a map of all items in the player's inventory with their quantities.
     *
     * @param entity The living entity (player) to scan
     * @return A map of item ID to total quantity
     */
    public static Map<String, Integer> getAllItemCounts(LivingEntity entity) {
        Map<String, Integer> itemCounts = new HashMap<>();

        if (entity == null) {
            return itemCounts;
        }

        try {
            Inventory inventory = entity.getInventory();
            if (inventory == null) {
                return itemCounts;
            }

            // Scan all sections
            collectFromContainer(inventory.getHotbar(), itemCounts);
            collectFromContainer(inventory.getStorage(), itemCounts);
            collectFromContainer(inventory.getBackpack(), itemCounts);
            collectFromContainer(inventory.getArmor(), itemCounts);
            collectFromContainer(inventory.getUtility(), itemCounts);
            collectFromContainer(inventory.getTools(), itemCounts);

            return itemCounts;
        } catch (Exception e) {
            return itemCounts;
        }
    }

    /**
     * Collects all items from a container into a map.
     *
     * @param container The container to scan
     * @param itemCounts The map to add items to
     */
    private static void collectFromContainer(ItemContainer container, Map<String, Integer> itemCounts) {
        if (container == null) {
            return;
        }

        try {
            container.forEach((slot, itemStack) -> {
                if (itemStack != null && itemStack.getItemId() != null) {
                    String itemId = itemStack.getItemId();
                    int quantity = itemStack.getQuantity();
                    itemCounts.merge(itemId, quantity, Integer::sum);
                }
            });
        } catch (Exception ignored) {
            // Silently fail if forEach isn't available
        }
    }
}
