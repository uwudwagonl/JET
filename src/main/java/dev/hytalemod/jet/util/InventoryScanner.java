package dev.hytalemod.jet.util;

import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.protocol.ItemResourceType;

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

    /**
     * Counts items matching a resource type across all inventory sections.
     *
     * @param entity The living entity (player) to scan
     * @param resourceTypeId The resource type ID to match (e.g., "hytale:meat")
     * @return The total quantity of items with this resource type
     */
    public static int countResourceTypeInInventory(LivingEntity entity, String resourceTypeId) {
        if (entity == null || resourceTypeId == null) {
            return 0;
        }

        try {
            Inventory inventory = entity.getInventory();
            if (inventory == null) {
                return 0;
            }

            int totalCount = 0;

            // Scan all inventory sections
            totalCount += countResourceTypeInContainer(inventory.getHotbar(), resourceTypeId);
            totalCount += countResourceTypeInContainer(inventory.getStorage(), resourceTypeId);
            totalCount += countResourceTypeInContainer(inventory.getBackpack(), resourceTypeId);
            totalCount += countResourceTypeInContainer(inventory.getArmor(), resourceTypeId);
            totalCount += countResourceTypeInContainer(inventory.getUtility(), resourceTypeId);
            totalCount += countResourceTypeInContainer(inventory.getTools(), resourceTypeId);

            return totalCount;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Counts items matching a resource type in a specific container.
     *
     * @param container The item container to scan
     * @param resourceTypeId The resource type ID to match
     * @return The quantity found in this container
     */
    private static int countResourceTypeInContainer(ItemContainer container, String resourceTypeId) {
        if (container == null || resourceTypeId == null) {
            return 0;
        }

        try {
            return container.countItemStacks(itemStack -> {
                if (itemStack == null) {
                    return false;
                }

                Item item = itemStack.getItem();
                if (item == null) {
                    return false;
                }

                ItemResourceType[] resourceTypes = item.getResourceTypes();
                if (resourceTypes != null) {
                    for (ItemResourceType type : resourceTypes) {
                        if (type.id != null && type.id.equals(resourceTypeId)) {
                            return true;
                        }
                    }
                }
                return false;
            });
        } catch (Exception e) {
            return 0;
        }
    }
}
