package dev.hytalemod.jet.registry;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry for all items with categorization and search capabilities.
 */
public class ItemRegistry {
    
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<Category, Set<String>> categoryIndex = new EnumMap<>(Category.class);
    private final Map<String, Set<String>> tagIndex = new HashMap<>();
    
    public enum Category {
        ALL("All"),
        TOOLS("Tools"),
        WEAPONS("Weapons"),
        ARMOR("Armor"),
        FOOD("Food"),
        BLOCKS("Blocks"),
        MATERIALS("Materials"),
        MISC("Misc");
        
        private final String displayName;
        
        Category(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public void reload(Map<String, Item> newItems) {
        items.clear();
        categoryIndex.clear();
        tagIndex.clear();
        
        // Initialize category sets
        for (Category cat : Category.values()) {
            categoryIndex.put(cat, new HashSet<>());
        }
        
        // Process all items
        for (Map.Entry<String, Item> entry : newItems.entrySet()) {
            String id = entry.getKey();
            Item item = entry.getValue();
            
            items.put(id, item);
            categoryIndex.get(Category.ALL).add(id);
            
            // Categorize by item properties
            if (item.getTool() != null) {
                categoryIndex.get(Category.TOOLS).add(id);
            }
            if (item.getWeapon() != null) {
                categoryIndex.get(Category.WEAPONS).add(id);
            }
            if (item.getArmor() != null) {
                categoryIndex.get(Category.ARMOR).add(id);
            }
            if (item.isConsumable()) {
                categoryIndex.get(Category.FOOD).add(id);
            }
            
            // Categorize by naming patterns
            String idLower = id.toLowerCase();
            if (idLower.contains("block") || idLower.contains("brick") || idLower.contains("plank") || idLower.contains("stone") || idLower.contains("ore")) {
                categoryIndex.get(Category.BLOCKS).add(id);
            }
            
            // Index by tags (extracted from item ID parts)
            indexTags(id);
            
            // Default to misc if not categorized elsewhere
            boolean categorized = false;
            for (Category cat : Category.values()) {
                if (cat != Category.ALL && cat != Category.MISC && categoryIndex.get(cat).contains(id)) {
                    categorized = true;
                    break;
                }
            }
            if (!categorized) {
                categoryIndex.get(Category.MISC).add(id);
            }
        }
    }
    
    private void indexTags(String itemId) {
        // Extract tags from item ID (e.g., "Hytale:Iron_Sword" -> ["iron", "sword"])
        String[] parts = itemId.split("[:_]");
        for (String part : parts) {
            String tag = part.toLowerCase();
            if (tag.length() > 2) { // Skip very short parts
                tagIndex.computeIfAbsent(tag, k -> new HashSet<>()).add(itemId);
            }
        }
    }
    
    public int size() {
        return items.size();
    }
    
    public Item get(String id) {
        return items.get(id);
    }
    
    public Map<String, Item> getAll() {
        return Collections.unmodifiableMap(items);
    }
    
    public Set<String> getByCategory(Category category) {
        return Collections.unmodifiableSet(categoryIndex.getOrDefault(category, Collections.emptySet()));
    }
    
    public Set<String> getByTag(String tag) {
        return Collections.unmodifiableSet(tagIndex.getOrDefault(tag.toLowerCase(), Collections.emptySet()));
    }
    
    /**
     * Search items with smart prefix support:
     * - @category filters by category
     * - #tag filters by tag
     * - plain text searches name
     */
    public List<Map.Entry<String, Item>> search(String query) {
        if (query == null || query.isEmpty()) {
            return items.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        }
        
        query = query.trim();
        Set<String> matchingIds = new HashSet<>(items.keySet());
        
        // Parse multiple filter terms
        String[] terms = query.split("\\s+");
        String textSearch = "";
        
        for (String term : terms) {
            if (term.startsWith("@")) {
                // Category filter
                String catName = term.substring(1).toLowerCase();
                Set<String> catIds = new HashSet<>();
                for (Category cat : Category.values()) {
                    if (cat.name().toLowerCase().contains(catName) || 
                        cat.getDisplayName().toLowerCase().contains(catName)) {
                        catIds.addAll(categoryIndex.getOrDefault(cat, Collections.emptySet()));
                    }
                }
                if (!catIds.isEmpty()) {
                    matchingIds.retainAll(catIds);
                }
            } else if (term.startsWith("#")) {
                // Tag filter
                String tag = term.substring(1).toLowerCase();
                Set<String> tagIds = tagIndex.getOrDefault(tag, Collections.emptySet());
                if (!tagIds.isEmpty()) {
                    matchingIds.retainAll(tagIds);
                } else {
                    // Partial tag match
                    Set<String> partialMatch = new HashSet<>();
                    for (Map.Entry<String, Set<String>> e : tagIndex.entrySet()) {
                        if (e.getKey().contains(tag)) {
                            partialMatch.addAll(e.getValue());
                        }
                    }
                    if (!partialMatch.isEmpty()) {
                        matchingIds.retainAll(partialMatch);
                    }
                }
            } else {
                // Regular text search
                textSearch = (textSearch + " " + term).trim();
            }
        }
        
        // Apply text search filter
        if (!textSearch.isEmpty()) {
            final String searchText = textSearch.toLowerCase();
            matchingIds.removeIf(id -> !id.toLowerCase().contains(searchText));
        }
        
        // Return sorted results
        return matchingIds.stream()
            .sorted()
            .map(id -> Map.entry(id, items.get(id)))
            .collect(Collectors.toList());
    }
    
    public List<Category> getCategories() {
        return Arrays.asList(Category.values());
    }
}
