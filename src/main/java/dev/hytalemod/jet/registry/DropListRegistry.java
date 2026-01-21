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
        // DON'T clear - merge new drop lists with existing ones
        // Assets load in multiple phases, we need to keep accumulating
        // dropLists.clear();
        // itemDropSources.clear();
        // blockToItemMapping.clear();

        // Log all available methods on ItemDropList for debugging
        try {
            java.nio.file.Path logsDir = getLogsDirectory();
            java.nio.file.Files.createDirectories(logsDir);
            java.io.FileWriter methodsFile = new java.io.FileWriter(logsDir.resolve("JET_droplist_methods.txt").toFile());
            methodsFile.write("=== All ItemDropList Methods ===\n");
            Method[] methods = ItemDropList.class.getMethods();
            for (Method m : methods) {
                methodsFile.write(m.getName() + "(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) methodsFile.write(", ");
                    methodsFile.write(params[i].getSimpleName());
                }
                methodsFile.write(") -> " + m.getReturnType().getSimpleName() + "\n");
            }
            methodsFile.close();
        } catch (Exception ex) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log("[JET] Failed to write droplist methods file: " + ex.getMessage());
        }

        for (Map.Entry<String, ItemDropList> entry : newDropLists.entrySet()) {
            ItemDropList dropList = entry.getValue();
            String dropListId = dropList.getId();

            dropLists.put(dropListId, dropList);

            // Try to discover what items this drop list contains
            indexDropListContents(dropList, dropListId);
        }

        JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Indexed " + dropLists.size() + " drop lists");
        JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Found " + itemDropSources.size() + " items with drop sources");

        // Build block-to-item mappings AFTER indexing drop lists
        // Pattern: Ore_Gold_Stone -> Ore_Gold
        buildBlockToItemMappings();

        // Write debug info to file
        writeDebugInfo();
    }

    /**
     * Try to discover what items are in a drop list using reflection
     */
    private void indexDropListContents(ItemDropList dropList, String dropListId) {
        try {
            // Use getContainer() method - we know it exists from debug logs
            Object container = dropList.getContainer();

            if (container != null) {
                // Log container type for first few drop lists
                if (dropLists.size() < 5) {
                    JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                        "[JET] Drop list " + dropListId + " has container type: " + container.getClass().getName()
                    );
                }

                // Try to extract item IDs from the container
                extractItemIds(container, dropListId);
            }
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log(
                "[JET] Error getting container for drop list " + dropListId + ": " + e.getMessage()
            );
        }
    }

    /**
     * Try to extract item IDs from various container types
     */
    private void extractItemIds(Object container, String dropListId) {
        if (container == null) return;

        // Try to handle different container types
        try {
            // If it's a collection/list
            if (container instanceof Collection) {
                Collection<?> items = (Collection<?>) container;
                for (Object item : items) {
                    String itemId = extractItemIdFromDrop(item);
                    if (itemId != null) {
                        itemDropSources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(dropListId);
                        JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                            "[JET] Item " + itemId + " can drop from " + dropListId
                        );
                    }
                }
            }
            // If it's an array
            else if (container.getClass().isArray()) {
                Object[] items = (Object[]) container;
                for (Object item : items) {
                    String itemId = extractItemIdFromDrop(item);
                    if (itemId != null) {
                        itemDropSources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(dropListId);
                    }
                }
            }
            // Try to call methods on the container object
            else {
                tryExtractFromContainer(container, dropListId);
            }
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log(
                "[JET] Error extracting items from drop list " + dropListId + ": " + e.getMessage()
            );
        }
    }

    /**
     * Try to extract item ID from a drop object
     */
    private String extractItemIdFromDrop(Object drop) {
        if (drop == null) return null;

        try {
            // Try common method names
            String[] methodNames = {"getItemId", "getId", "getItem", "getMaterial"};

            for (String methodName : methodNames) {
                try {
                    Method method = drop.getClass().getMethod(methodName);
                    Object result = method.invoke(drop);
                    if (result instanceof String) {
                        String itemId = (String) result;
                        // Log first successful extraction
                        if (itemDropSources.isEmpty()) {
                            JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                                "[JET] Successfully extracted item ID: " + itemId + " using method " + methodName
                            );
                        }
                        return itemId;
                    }
                } catch (Exception e) {
                    // Log the first failure for debugging
                    if (itemDropSources.isEmpty()) {
                        JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                            "[JET] Method " + methodName + " failed: " + e.getMessage()
                        );
                    }
                }
            }
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log(
                "[JET] Error extracting item ID: " + e.getMessage()
            );
        }

        return null;
    }

    /**
     * Try various methods to extract items from a container object
     */
    private void tryExtractFromContainer(Object container, String dropListId) {
        // Log all methods on the container for debugging (first container only)
        if (itemDropSources.isEmpty()) {
            try {
                java.nio.file.Path logsDir = getLogsDirectory();
                java.io.FileWriter fw = new java.io.FileWriter(logsDir.resolve("JET_container_methods.txt").toFile());
                fw.write("=== ItemDropContainer Methods ===\n");
                fw.write("Container class: " + container.getClass().getName() + "\n\n");

                Method[] methods = container.getClass().getMethods();
                for (Method m : methods) {
                    fw.write(m.getName() + "(");
                    Class<?>[] params = m.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        if (i > 0) fw.write(", ");
                        fw.write(params[i].getSimpleName());
                    }
                    fw.write(") -> " + m.getReturnType().getSimpleName() + "\n");
                }
                fw.close();

                JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                    "[JET] Container methods written to JET_container_methods.txt"
                );
            } catch (Exception ex) {
                JETPlugin.getInstance().getLogger().at(Level.WARNING).log(
                    "[JET] Failed to write container methods: " + ex.getMessage()
                );
            }
        }

        // Try getAllDrops(List) method - we know this exists from the debug logs
        try {
            Method getAllDropsMethod = container.getClass().getMethod("getAllDrops", List.class);
            List<Object> dropsList = new ArrayList<>();
            Object result = getAllDropsMethod.invoke(container, dropsList);

            if (result != null && result instanceof List) {
                List<?> drops = (List<?>) result;
                JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                    "[JET] getAllDrops() returned " + drops.size() + " items for " + dropListId
                );

                // Extract item IDs from the drops
                for (int i = 0; i < drops.size(); i++) {
                    Object drop = drops.get(i);

                    // Log first drop object type for debugging
                    if (i == 0 && itemDropSources.isEmpty()) {
                        JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                            "[JET] First drop object type: " + drop.getClass().getName()
                        );

                        // Log all methods on the drop object
                        try {
                            java.nio.file.Path logsDir = getLogsDirectory();
                            java.io.FileWriter fw = new java.io.FileWriter(logsDir.resolve("JET_drop_methods.txt").toFile());
                            fw.write("=== Drop Object Methods ===\n");
                            fw.write("Drop class: " + drop.getClass().getName() + "\n\n");

                            Method[] methods = drop.getClass().getMethods();
                            for (Method m : methods) {
                                fw.write(m.getName() + "(");
                                Class<?>[] params = m.getParameterTypes();
                                for (int j = 0; j < params.length; j++) {
                                    if (j > 0) fw.write(", ");
                                    fw.write(params[j].getSimpleName());
                                }
                                fw.write(") -> " + m.getReturnType().getSimpleName() + "\n");
                            }
                            fw.close();
                        } catch (Exception ignored) {}
                    }

                    String itemId = extractItemIdFromDrop(drop);
                    if (itemId != null) {
                        itemDropSources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(dropListId);

                        // Log first few successes
                        if (itemDropSources.size() <= 10) {
                            JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                                "[JET] Item " + itemId + " drops from " + dropListId
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log(
                "[JET] Error calling getAllDrops on container: " + e.getMessage()
            );
        }
    }

    /**
     * Write comprehensive debug information to file
     */
    private void writeDebugInfo() {
        try {
            java.nio.file.Path logsDir = getLogsDirectory();
            java.nio.file.Files.createDirectories(logsDir);
            java.io.FileWriter fw = new java.io.FileWriter(logsDir.resolve("JET_droplists_debug.txt").toFile());

            fw.write("=== JET Drop Lists Debug ===\n");
            fw.write("Total drop lists: " + dropLists.size() + "\n");
            fw.write("Items with drop sources: " + itemDropSources.size() + "\n\n");

            fw.write("=== All Drop List IDs ===\n");
            for (String id : dropLists.keySet()) {
                fw.write("  " + id + "\n");
            }

            fw.write("\n=== Items with Drop Sources ===\n");
            for (Map.Entry<String, List<String>> entry : itemDropSources.entrySet()) {
                fw.write("  " + entry.getKey() + " drops from:\n");
                for (String dropListId : entry.getValue()) {
                    fw.write("    - " + dropListId + "\n");
                }
            }

            fw.close();

            JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                "[JET] Drop list debug info written to: " + logsDir.resolve("JET_droplists_debug.txt")
            );
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log(
                "[JET] Failed to write drop lists debug file: " + e.getMessage()
            );
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
        // Check direct item drops
        List<String> sources = itemDropSources.get(itemId);

        // If no direct drops found, check if this is a block that drops another item
        if ((sources == null || sources.isEmpty()) && blockToItemMapping.containsKey(itemId)) {
            String droppedItem = blockToItemMapping.get(itemId);
            sources = itemDropSources.get(droppedItem);
            JETPlugin.getInstance().getLogger().at(Level.INFO).log(
                "[JET] Block " + itemId + " drops item " + droppedItem + " with " +
                (sources != null ? sources.size() : 0) + " drop sources"
            );
        }

        return sources != null ? new ArrayList<>(sources) : Collections.emptyList();
    }

    /**
     * Build mappings from block IDs to the items they drop.
     * Primarily for ore blocks: Ore_Gold_Stone -> Ore_Gold
     */
    private void buildBlockToItemMappings() {
        // Common ore types
        String[] oreTypes = {"Copper", "Iron", "Gold", "Silver", "Cobalt", "Thorium", "Mithril", "Adamantite", "Onyxium"};
        String[] rockTypes = {"Stone", "Sandstone", "Shale", "Slate", "Basalt", "Volcanic"};

        for (String ore : oreTypes) {
            for (String rock : rockTypes) {
                String blockId = "Ore_" + ore + "_" + rock;
                String itemId = "Ore_" + ore;
                blockToItemMapping.put(blockId, itemId);
            }
        }

        JETPlugin.getInstance().getLogger().at(Level.INFO).log(
            "[JET] Built " + blockToItemMapping.size() + " block-to-item mappings"
        );
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

    /**
     * Get cross-platform logs directory for Hytale
     */
    private static java.nio.file.Path getLogsDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            return java.nio.file.Paths.get(userHome, "AppData", "Roaming", "Hytale", "UserData", "Logs");
        } else if (os.contains("mac")) {
            return java.nio.file.Paths.get(userHome, "Library", "Application Support", "Hytale", "UserData", "Logs");
        } else {
            return java.nio.file.Paths.get(userHome, ".local", "share", "Hytale", "UserData", "Logs");
        }
    }
}
