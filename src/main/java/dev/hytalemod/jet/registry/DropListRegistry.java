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

    // Item ID -> Biome spawn info for ores
    private final Map<String, List<String>> oreBiomeSpawns = new HashMap<>();

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

        // Build ore biome spawn data
        buildOreBiomeSpawns();
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
            // Best effort: some drop lists don't expose extractable contents
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
            // Best effort: skip entries that fail extraction
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
            // Best effort: return null if no supported extraction path is available
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
            // Best effort: some container types don't support getAllDrops
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
     * Build ore biome spawn data based on drop list patterns and known ore spawns
     */
    private void buildOreBiomeSpawns() {
        // Common ore biome patterns - these are educated guesses based on typical Hytale zones
        Map<String, List<String>> orePatterns = new HashMap<>();

        // Zone 1 (Emerald Grove) - Common surface ores
        orePatterns.put("Copper", Arrays.asList("Zone 1: Emerald Grove (Surface)", "Zone 1: Caves"));
        orePatterns.put("Iron", Arrays.asList("Zone 1: Emerald Grove (Common)", "Zone 1: Caves", "Zone 2: Howling Sands"));

        // Precious ores
        orePatterns.put("Gold", Arrays.asList("Zone 1: Deep Caves", "Zone 2: Howling Sands", "Zone 3: Borea"));
        orePatterns.put("Silver", Arrays.asList("Zone 1: Caves", "Zone 3: Borea (Common)"));

        // Advanced ores
        orePatterns.put("Cobalt", Arrays.asList("Zone 1: Deep Caves (Rare)", "Zone 4: Devastated Lands"));
        orePatterns.put("Thorium", Arrays.asList("Zone 4: Devastated Lands", "Zone 5: Deep Underground"));
        orePatterns.put("Mithril", Arrays.asList("Zone 3: Borea (Deep)", "Zone 5: Orbis (Rare)"));
        orePatterns.put("Adamantite", Arrays.asList("Zone 5: Orbis (Deep)", "Zone 6: Varyn (Very Rare)"));
        orePatterns.put("Onyxium", Arrays.asList("Zone 6: Varyn (Extremely Rare)", "Void Below"));

        // Map ore items to their spawn locations
        for (Map.Entry<String, List<String>> entry : orePatterns.entrySet()) {
            String oreType = entry.getKey();
            List<String> biomes = entry.getValue();

            // Store for the ore item
            String oreItemId = "Ore_" + oreType;
            oreBiomeSpawns.put(oreItemId, new ArrayList<>(biomes));

            // Also store for ore blocks
            String[] rockTypes = {"Stone", "Sandstone", "Shale", "Slate", "Basalt", "Volcanic"};
            for (String rock : rockTypes) {
                String oreBlockId = "Ore_" + oreType + "_" + rock;
                oreBiomeSpawns.put(oreBlockId, new ArrayList<>(biomes));
            }
        }

        JETPlugin.getInstance().log(Level.INFO, "[JET] Built biome spawn data for " + orePatterns.size() + " ore types");
    }

    /**
     * Get biome spawn information for an ore item/block
     */
    public List<String> getOreBiomeSpawns(String itemId) {
        List<String> spawns = oreBiomeSpawns.get(itemId);
        return spawns != null ? new ArrayList<>(spawns) : Collections.emptyList();
    }

    /**
     * Check if an item is an ore with biome spawn data
     */
    public boolean hasOreBiomeData(String itemId) {
        return oreBiomeSpawns.containsKey(itemId) && !oreBiomeSpawns.get(itemId).isEmpty();
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
