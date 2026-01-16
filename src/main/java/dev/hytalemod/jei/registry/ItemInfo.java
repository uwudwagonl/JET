package dev.hytalemod.jei.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents information about a single item/block
 */
public class ItemInfo {
    
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final String iconPath;
    private final int maxStackSize;
    private final List<String> tags;
    
    private ItemInfo(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.category = builder.category;
        this.iconPath = builder.iconPath;
        this.maxStackSize = builder.maxStackSize;
        this.tags = new ArrayList<>(builder.tags);
    }
    
    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getIconPath() { return iconPath; }
    public int getMaxStackSize() { return maxStackSize; }
    public List<String> getTags() { return new ArrayList<>(tags); }
    
    /**
     * Check if this item matches a search query
     */
    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        
        // Check ID, name, description
        if (id.toLowerCase().contains(lowerQuery)) return true;
        if (name.toLowerCase().contains(lowerQuery)) return true;
        if (description.toLowerCase().contains(lowerQuery)) return true;
        if (category.toLowerCase().contains(lowerQuery)) return true;
        
        // Check tags
        for (String tag : tags) {
            if (tag.toLowerCase().contains(lowerQuery)) return true;
        }
        
        // Special prefix searches
        if (lowerQuery.startsWith("@")) {
            // @category filter
            String catQuery = lowerQuery.substring(1);
            return category.toLowerCase().contains(catQuery);
        }
        
        if (lowerQuery.startsWith("#")) {
            // #tag filter
            String tagQuery = lowerQuery.substring(1);
            for (String tag : tags) {
                if (tag.toLowerCase().contains(tagQuery)) return true;
            }
            return false;
        }
        
        return false;
    }
    
    @Override
    public String toString() {
        return "ItemInfo{id='" + id + "', name='" + name + "'}";
    }
    
    /**
     * Builder for ItemInfo
     */
    public static class Builder {
        private final String id;
        private final String name;
        private String description = "";
        private String category = "misc";
        private String iconPath = "";
        private int maxStackSize = 64;
        private List<String> tags = new ArrayList<>();
        
        public Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public Builder description(String desc) {
            this.description = desc;
            return this;
        }
        
        public Builder category(String cat) {
            this.category = cat;
            return this;
        }
        
        public Builder iconPath(String path) {
            this.iconPath = path;
            return this;
        }
        
        public Builder maxStackSize(int size) {
            this.maxStackSize = size;
            return this;
        }
        
        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }
        
        public ItemInfo build() {
            return new ItemInfo(this);
        }
    }
}
