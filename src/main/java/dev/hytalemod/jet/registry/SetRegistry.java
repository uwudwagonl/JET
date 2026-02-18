package dev.hytalemod.jet.registry;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-detects equipment sets from item naming conventions.
 * Hytale items follow various patterns:
 *   "Armor_Adamantite_Chest", "Tool_Adamantite_Pickaxe", "Shield_Adamantite"
 * Items sharing the same material name are grouped into a set.
 */
public class SetRegistry {

    // Category prefixes that Hytale prepends to item IDs
    private static final Set<String> CATEGORY_PREFIXES = Set.of(
            "armor", "weapon", "tool"
    );

    // Root words that identify equipment type segments (matched via contains)
    // Kept short to catch compound names: "Longsword" contains "sword", "Shortbow" contains "bow", etc.
    private static final List<String> SLOT_ROOTS = List.of(
            // Armor
            "helm", "head", "chest", "cuirass", "leg", "boot", "feet", "hand", "gauntlet",
            // Shields
            "shield",
            // Weapons
            "sword", "dagger", "spear", "mace", "bow", "staff", "wand",
            // Tools
            "pickaxe", "axe", "shovel", "hoe", "shear",
            // Misc
            "fishing"
    );

    private final Map<String, List<String>> sets = new LinkedHashMap<>();
    private final Map<String, String> itemToSet = new HashMap<>();

    public void reload(Map<String, Item> items) {
        sets.clear();
        itemToSet.clear();

        Map<String, List<String>> materialGroups = new HashMap<>();

        for (Map.Entry<String, Item> entry : items.entrySet()) {
            String itemId = entry.getKey();
            Item item = entry.getValue();

            String material = extractMaterial(itemId, item);
            if (material != null && !material.isEmpty()) {
                materialGroups.computeIfAbsent(material, k -> new ArrayList<>()).add(itemId);
            }
        }

        // Keep only groups with 2+ members
        List<String> sortedMaterials = materialGroups.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        for (String material : sortedMaterials) {
            List<String> members = materialGroups.get(material);
            Collections.sort(members);
            sets.put(material, members);
            for (String itemId : members) {
                itemToSet.put(itemId, material);
            }
        }
    }

    /**
     * Check if a segment is a slot/type word by seeing if it contains any known root.
     */
    private boolean isSlotWord(String segment) {
        String lower = segment.toLowerCase();
        for (String root : SLOT_ROOTS) {
            if (lower.contains(root)) return true;
        }
        return false;
    }

    private String extractMaterial(String itemId, Item item) {
        // Check if item is equipment via API
        boolean isEquipment = item.getArmor() != null || item.getWeapon() != null || item.getTool() != null;

        // Strip namespace (e.g., "Hytale:")
        String rawId = itemId;
        int colonIdx = rawId.indexOf(':');
        if (colonIdx >= 0) {
            rawId = rawId.substring(colonIdx + 1);
        }

        // Split into segments: e.g. "Armor_Adamantite_Chest" -> ["Armor", "Adamantite", "Chest"]
        String[] parts = rawId.split("_");
        if (parts.length < 2) return null;

        // Also consider it equipment if the ID contains a category prefix or slot word
        if (!isEquipment) {
            boolean hasEquipmentWord = false;
            for (String part : parts) {
                String lower = part.toLowerCase();
                if (CATEGORY_PREFIXES.contains(lower) || isSlotWord(part)) {
                    hasEquipmentWord = true;
                    break;
                }
            }
            if (!hasEquipmentWord) return null;
        }

        // Collect non-category, non-slot segments as the material name
        List<String> materialParts = new ArrayList<>();
        for (String part : parts) {
            String lower = part.toLowerCase();
            if (CATEGORY_PREFIXES.contains(lower)) continue;
            if (isSlotWord(part)) continue;
            materialParts.add(part);
        }

        if (materialParts.isEmpty()) return null;
        return String.join("_", materialParts);
    }

    public String getDisplayName(String setPrefix) {
        return setPrefix.replace("_", " ");
    }

    public List<String> getSetNames() {
        return new ArrayList<>(sets.keySet());
    }

    public List<String> getSetItems(String setPrefix) {
        return sets.getOrDefault(setPrefix, Collections.emptyList());
    }

    public String getSetForItem(String itemId) {
        return itemToSet.get(itemId);
    }

    public int size() {
        return sets.size();
    }
}
