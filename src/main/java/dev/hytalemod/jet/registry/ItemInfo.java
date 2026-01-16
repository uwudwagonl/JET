package dev.hytalemod.jet.registry;

import java.util.*;

/**
 * Simple data class representing an item in the registry.
 */
public class ItemInfo {
    
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final Set<String> tags;
    private final int maxStackSize;
    
    public ItemInfo(String id, String name, String description, String category, Set<String> tags, int maxStackSize) {
        this.id = id;
        this.name = name;
        this.description = description != null ? description : "";
        this.category = category != null ? category : "misc";
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
        this.maxStackSize = maxStackSize > 0 ? maxStackSize : 64;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getCategory() {
        return category;
    }
    
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }
    
    public int getMaxStackSize() {
        return maxStackSize;
    }
    
    /**
     * Check if this item matches a search query.
     * Supports:
     *   - Plain text (matches name, id, description)
     *   - @category prefix
     *   - #tag prefix
     */
    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        
        String q = query.toLowerCase().trim();
        
        // Category filter
        if (q.startsWith("@")) {
            String catQuery = q.substring(1);
            return category.toLowerCase().contains(catQuery);
        }
        
        // Tag filter
        if (q.startsWith("#")) {
            String tagQuery = q.substring(1);
            return tags.stream().anyMatch(t -> t.toLowerCase().contains(tagQuery));
        }
        
        // Plain text search
        return id.toLowerCase().contains(q) ||
               name.toLowerCase().contains(q) ||
               description.toLowerCase().contains(q);
    }
    
    @Override
    public String toString() {
        return "ItemInfo{id='" + id + "', name='" + name + "', category='" + category + "'}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemInfo itemInfo = (ItemInfo) o;
        return Objects.equals(id, itemInfo.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
