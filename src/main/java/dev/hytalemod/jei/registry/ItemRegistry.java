package dev.hytalemod.jei.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry of all items in the game.
 * Scans Hytale's registries and caches item data for fast searching.
 */
public class ItemRegistry {
    
    private final Map<String, ItemInfo> itemsById;
    private final Map<String, List<ItemInfo>> itemsByCategory;
    private List<ItemInfo> sortedItems;
    
    // Search cache for performance
    private final Map<String, List<ItemInfo>> searchCache;
    private static final int MAX_CACHE_SIZE = 100;
    
    public ItemRegistry() {
        this.itemsById = new ConcurrentHashMap<>();
        this.itemsByCategory = new ConcurrentHashMap<>();
        this.sortedItems = new ArrayList<>();
        this.searchCache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<ItemInfo>> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }
    
    /**
     * Scan all registered items from Hytale's registries
     * 
     * TODO: Replace stub with actual Hytale API calls when available
     */
    public void scanAllItems() {
        System.out.println("[HytaleJEI] Scanning item registries...");
        
        itemsById.clear();
        itemsByCategory.clear();
        searchCache.clear();
        
        // ============================================
        // TODO: Replace with actual Hytale API
        // ============================================
        // Look for patterns like:
        // Server.getItemRegistry().getAll()
        // BlockRegistry.getAllBlocks()
        // AssetManager.getItemDefinitions()
        
        scanHytaleItems();
        scanHytaleBlocks();
        
        // Sort alphabetically
        sortedItems = new ArrayList<>(itemsById.values());
        sortedItems.sort(Comparator.comparing(ItemInfo::getName));
        
        System.out.println("[HytaleJEI] Found " + itemsById.size() + " items.");
    }
    
    /**
     * STUB: Scan items - Replace with actual Hytale API
     */
    private void scanHytaleItems() {
        // Example items (placeholder data)
        registerItem(ItemInfo.builder("hytale:wooden_sword", "Wooden Sword")
            .description("A basic wooden sword")
            .category("weapons")
            .maxStackSize(1)
            .addTag("weapon").addTag("sword").addTag("melee").addTag("wood")
            .build());
        
        registerItem(ItemInfo.builder("hytale:stone_sword", "Stone Sword")
            .description("A stone sword")
            .category("weapons")
            .maxStackSize(1)
            .addTag("weapon").addTag("sword").addTag("melee").addTag("stone")
            .build());
        
        registerItem(ItemInfo.builder("hytale:iron_sword", "Iron Sword")
            .description("A sturdy iron sword")
            .category("weapons")
            .maxStackSize(1)
            .addTag("weapon").addTag("sword").addTag("melee").addTag("iron").addTag("metal")
            .build());
        
        registerItem(ItemInfo.builder("hytale:wooden_pickaxe", "Wooden Pickaxe")
            .description("A basic pickaxe for mining")
            .category("tools")
            .maxStackSize(1)
            .addTag("tool").addTag("pickaxe").addTag("mining").addTag("wood")
            .build());
        
        registerItem(ItemInfo.builder("hytale:stone_pickaxe", "Stone Pickaxe")
            .description("A stone pickaxe")
            .category("tools")
            .maxStackSize(1)
            .addTag("tool").addTag("pickaxe").addTag("mining").addTag("stone")
            .build());
        
        registerItem(ItemInfo.builder("hytale:iron_pickaxe", "Iron Pickaxe")
            .description("An iron pickaxe for mining ore")
            .category("tools")
            .maxStackSize(1)
            .addTag("tool").addTag("pickaxe").addTag("mining").addTag("iron").addTag("metal")
            .build());
        
        registerItem(ItemInfo.builder("hytale:apple", "Apple")
            .description("A fresh apple that restores hunger")
            .category("food")
            .maxStackSize(64)
            .addTag("food").addTag("fruit").addTag("healing")
            .build());
        
        registerItem(ItemInfo.builder("hytale:bread", "Bread")
            .description("Freshly baked bread")
            .category("food")
            .maxStackSize(64)
            .addTag("food").addTag("baked")
            .build());
        
        registerItem(ItemInfo.builder("hytale:iron_ingot", "Iron Ingot")
            .description("A refined iron ingot")
            .category("materials")
            .maxStackSize(64)
            .addTag("material").addTag("metal").addTag("iron").addTag("ingot")
            .build());
        
        registerItem(ItemInfo.builder("hytale:gold_ingot", "Gold Ingot")
            .description("A shiny gold ingot")
            .category("materials")
            .maxStackSize(64)
            .addTag("material").addTag("metal").addTag("gold").addTag("ingot").addTag("precious")
            .build());
        
        registerItem(ItemInfo.builder("hytale:copper_ingot", "Copper Ingot")
            .description("A copper ingot")
            .category("materials")
            .maxStackSize(64)
            .addTag("material").addTag("metal").addTag("copper").addTag("ingot")
            .build());
        
        registerItem(ItemInfo.builder("hytale:stick", "Stick")
            .description("A wooden stick")
            .category("materials")
            .maxStackSize(64)
            .addTag("material").addTag("wood").addTag("crafting")
            .build());
    }
    
    /**
     * STUB: Scan blocks - Replace with actual Hytale API
     */
    private void scanHytaleBlocks() {
        registerItem(ItemInfo.builder("hytale:stone", "Stone")
            .description("Basic stone block")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("natural").addTag("stone")
            .build());
        
        registerItem(ItemInfo.builder("hytale:dirt", "Dirt")
            .description("A block of dirt")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("natural").addTag("dirt")
            .build());
        
        registerItem(ItemInfo.builder("hytale:grass_block", "Grass Block")
            .description("A dirt block with grass on top")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("natural").addTag("grass")
            .build());
        
        registerItem(ItemInfo.builder("hytale:oak_log", "Oak Log")
            .description("A log from an oak tree")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("wood").addTag("log").addTag("oak")
            .build());
        
        registerItem(ItemInfo.builder("hytale:oak_planks", "Oak Planks")
            .description("Wooden planks from oak")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("wood").addTag("planks").addTag("oak")
            .build());
        
        registerItem(ItemInfo.builder("hytale:cobblestone", "Cobblestone")
            .description("Rough cobblestone")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("stone").addTag("cobble")
            .build());
        
        registerItem(ItemInfo.builder("hytale:iron_ore", "Iron Ore")
            .description("Ore containing iron")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("ore").addTag("iron").addTag("mining")
            .build());
        
        registerItem(ItemInfo.builder("hytale:gold_ore", "Gold Ore")
            .description("Ore containing gold")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("ore").addTag("gold").addTag("mining").addTag("precious")
            .build());
        
        registerItem(ItemInfo.builder("hytale:copper_ore", "Copper Ore")
            .description("Ore containing copper")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("ore").addTag("copper").addTag("mining")
            .build());
        
        registerItem(ItemInfo.builder("hytale:crafting_table", "Crafting Table")
            .description("Used to craft items")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("utility").addTag("crafting")
            .build());
        
        registerItem(ItemInfo.builder("hytale:furnace", "Furnace")
            .description("Used to smelt ores and cook food")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("utility").addTag("smelting")
            .build());
        
        registerItem(ItemInfo.builder("hytale:chest", "Chest")
            .description("Storage container")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("utility").addTag("storage")
            .build());
        
        registerItem(ItemInfo.builder("hytale:sand", "Sand")
            .description("Sandy block")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("natural").addTag("sand")
            .build());
        
        registerItem(ItemInfo.builder("hytale:glass", "Glass")
            .description("Transparent glass block")
            .category("blocks")
            .maxStackSize(64)
            .addTag("block").addTag("transparent").addTag("glass")
            .build());
    }
    
    /**
     * Register an item
     */
    public void registerItem(ItemInfo item) {
        itemsById.put(item.getId(), item);
        itemsByCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
    }
    
    /**
     * Get item by ID
     */
    public ItemInfo getItem(String id) {
        return itemsById.get(id);
    }
    
    /**
     * Get all items sorted
     */
    public List<ItemInfo> getAllItems() {
        return new ArrayList<>(sortedItems);
    }
    
    /**
     * Get items by category
     */
    public List<ItemInfo> getItemsByCategory(String category) {
        return new ArrayList<>(itemsByCategory.getOrDefault(category, Collections.emptyList()));
    }
    
    /**
     * Get all categories
     */
    public Set<String> getCategories() {
        return new HashSet<>(itemsByCategory.keySet());
    }
    
    /**
     * Search items with caching
     */
    public List<ItemInfo> search(String query) {
        if (query == null || query.isEmpty()) {
            return getAllItems();
        }
        
        String normalizedQuery = query.toLowerCase().trim();
        
        // Check cache
        List<ItemInfo> cached = searchCache.get(normalizedQuery);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        
        // Perform search
        List<ItemInfo> results = sortedItems.stream()
            .filter(item -> item.matchesSearch(normalizedQuery))
            .collect(Collectors.toList());
        
        // Cache results
        searchCache.put(normalizedQuery, results);
        
        return results;
    }
    
    /**
     * Get paginated results
     */
    public List<ItemInfo> getPage(List<ItemInfo> items, int page, int pageSize) {
        int start = page * pageSize;
        int end = Math.min(start + pageSize, items.size());
        
        if (start >= items.size()) {
            return Collections.emptyList();
        }
        
        return items.subList(start, end);
    }
    
    /**
     * Get total item count
     */
    public int getItemCount() {
        return itemsById.size();
    }
    
    /**
     * Clear search cache
     */
    public void clearSearchCache() {
        searchCache.clear();
    }
}
