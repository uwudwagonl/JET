package dev.hytalemod.jet.registry;

import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import dev.hytalemod.jet.JETPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Registry for item drop lists (loot tables).
 */
public class DropListRegistry {

    // All drop lists by ID
    private final Map<String, ItemDropList> dropLists = new LinkedHashMap<>();

    // Item ID -> Drop list IDs that can drop this item
    private final Map<String, List<String>> itemDropSources = new HashMap<>();

    // Block ID patterns -> Items they drop (for linking ore blocks to ore items)
    private final Map<String, String> blockToItemMapping = new HashMap<>();

    public void reload(Map<String, ItemDropList> newDropLists) {
        for (Map.Entry<String, ItemDropList> entry : newDropLists.entrySet()) {
            ItemDropList dropList = entry.getValue();
            String dropListId = dropList.getId();

            dropLists.put(dropListId, dropList);

            // Try to discover what items this drop list contains
            indexDropListContents(dropList, dropListId);
        }

        JETPlugin.getInstance().log(Level.INFO, "[JET] Indexed " + dropLists.size() + " drop lists");
        JETPlugin.getInstance().log(Level.INFO, "[JET] Found " + itemDropSources.size() + " items with drop sources");

        // Build block-to-item mappings AFTER indexing drop lists
        buildBlockToItemMappings();
    }

    /**
     * Try to discover what items are in a drop list using reflection
     */
    private void indexDropListContents(ItemDropList dropList, String dropListId) {
        try {
            Object container = dropList.getContainer();
            if (container != null) {
                extractItemIds(container, dropListId);
            }
        } catch (Exception e) {
            // Silently ignore - not all drop lists have extractable contents
        }
    }

    /**
     * Try to extract item IDs from various container types
     */
    private void extractItemIds(Object container, String dropListId) {
        if (container == null) return;

        try {
            if (container instanceof Collection) {
                Collection<?> items = (Collection<?>) container;
                for (Object item : items) {
                    String itemId = extractItemIdFromDrop(item);
                    if (itemId != null) {
                        itemDropSources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(dropListId);
                    }
                }
            } else if (container.getClass().isArray()) {
                Object[] items = (Object[]) container;
                for (Object item : items) {
                    String itemId = extractItemIdFromDrop(item);
                    if (itemId != null) {
                        itemDropSources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(dropListId);
                    }
                }
            } else {
                tryExtractFromContainer(container, dropListId);
            }
        } catch (Exception e) {
            // Silently ignore extraction errors
        }
    }

    /**
     * Try to extract item ID from a drop object
     */
    private String extractItemIdFromDrop(Object drop) {
        if (drop == null) return null;

        try {
            String[] methodNames = {"getItemId", "getId", "getItem", "getMaterial"};

            for (String methodName : methodNames) {
                try {
                    Method method = drop.getClass().getMethod(methodName);
                    Object result = method.invoke(drop);
                    if (result instanceof String) {
                        return (String) result;
                    }
                } catch (Exception e) {
                    // Try next method
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return null;
    }

    /**
     * Try various methods to extract items from a container object
     */
    private void tryExtractFromContainer(Object container, String dropListId) {
        try {
            Method getAllDropsMethod = container.getClass().getMethod("getAllDrops", List.class);
            List<Object> dropsList = new ArrayList<>();
            Object result = getAllDropsMethod.invoke(container, dropsList);

            if (result != null && result instanceof List) {
                List<?> drops = (List<?>) result;
                for (Object drop : drops) {
                    String itemId = extractItemIdFromDrop(drop);
                    if (itemId != null) {
                        itemDropSources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(dropListId);
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore - not all containers support getAllDrops
        }
    }

    public int size() {
        return dropLists.size();
    }

    public ItemDropList get(String dropListId) {
        return dropLists.get(dropListId);
    }

    /**
     * Get all drop list IDs that can drop the given item.
     * Also checks if the itemId is an ore block and looks up the item it drops.
     */
    public List<String> getDropSourcesForItem(String itemId) {
        List<String> sources = itemDropSources.get(itemId);

        // If no direct drops found, check if this is a block that drops another item
        if ((sources == null || sources.isEmpty()) && blockToItemMapping.containsKey(itemId)) {
            String droppedItem = blockToItemMapping.get(itemId);
            sources = itemDropSources.get(droppedItem);
        }

        return sources != null ? new ArrayList<>(sources) : Collections.emptyList();
    }

    /**
     * Build mappings from block IDs to the items they drop.
     */
    private void buildBlockToItemMappings() {
        String[] oreTypes = {"Copper", "Iron", "Gold", "Silver", "Cobalt", "Thorium", "Mithril", "Adamantite", "Onyxium"};
        String[] rockTypes = {"Stone", "Sandstone", "Shale", "Slate", "Basalt", "Volcanic"};

        for (String ore : oreTypes) {
            for (String rock : rockTypes) {
                String blockId = "Ore_" + ore + "_" + rock;
                String itemId = "Ore_" + ore;
                blockToItemMapping.put(blockId, itemId);
            }
        }

        JETPlugin.getInstance().log(Level.INFO, "[JET] Built " + blockToItemMapping.size() + " block-to-item mappings");
    }

    /**
     * Check if an item has any known drop sources
     */
    public boolean hasDropSources(String itemId) {
        return itemDropSources.containsKey(itemId) && !itemDropSources.get(itemId).isEmpty();
    }

    /**
     * Get all drop lists
     */
    public Map<String, ItemDropList> getAllDropLists() {
        return new HashMap<>(dropLists);
    }
}
