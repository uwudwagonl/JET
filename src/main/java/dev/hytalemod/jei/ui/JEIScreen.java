package dev.hytalemod.jei.ui;

import dev.hytalemod.jei.registry.ItemInfo;
import dev.hytalemod.jei.registry.ItemRegistry;

import java.util.*;

/**
 * JEI Screen - Main UI for browsing items
 * 
 * Handles UI state and logic. Actual rendering uses Hytale's NoesisGUI (XAML).
 */
public class JEIScreen {
    
    private final ItemRegistry registry;
    private final Map<Object, PlayerUIState> playerStates;
    
    // Layout config
    private static final int ITEMS_PER_ROW = 9;
    private static final int ROWS_VISIBLE = 6;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_VISIBLE;
    
    public JEIScreen(ItemRegistry registry) {
        this.registry = registry;
        this.playerStates = new HashMap<>();
    }
    
    /**
     * Open JEI for a player
     */
    public void open(Object player) {
        open(player, "");
    }
    
    /**
     * Open JEI with search query
     */
    public void open(Object player, String searchQuery) {
        PlayerUIState state = playerStates.computeIfAbsent(
            getPlayerId(player), 
            k -> new PlayerUIState()
        );
        
        state.searchQuery = searchQuery != null ? searchQuery : "";
        state.currentPage = 0;
        state.selectedCategory = null;
        state.filteredItems = registry.search(state.searchQuery);
        
        showScreen(player, state);
    }
    
    /**
     * Close JEI for a player
     */
    public void close(Object player) {
        playerStates.remove(getPlayerId(player));
        hideScreen(player);
    }
    
    /**
     * Handle search input change
     */
    public void onSearchChanged(Object player, String newQuery) {
        PlayerUIState state = getState(player);
        if (state == null) return;
        
        state.searchQuery = newQuery;
        state.currentPage = 0;
        state.filteredItems = registry.search(newQuery);
        
        updateDisplay(player, state);
    }
    
    /**
     * Handle page navigation
     */
    public void onPageChange(Object player, int direction) {
        PlayerUIState state = getState(player);
        if (state == null) return;
        
        int maxPage = getMaxPage(state);
        state.currentPage = Math.max(0, Math.min(state.currentPage + direction, maxPage));
        
        updateDisplay(player, state);
    }
    
    /**
     * Handle category filter
     */
    public void onCategorySelected(Object player, String category) {
        PlayerUIState state = getState(player);
        if (state == null) return;
        
        if (category == null || category.equals(state.selectedCategory)) {
            state.selectedCategory = null;
            state.filteredItems = registry.search(state.searchQuery);
        } else {
            state.selectedCategory = category;
            state.filteredItems = registry.search("@" + category + " " + state.searchQuery);
        }
        
        state.currentPage = 0;
        updateDisplay(player, state);
    }
    
    /**
     * Handle item click (left = recipes, right = usages)
     */
    public void onItemClicked(Object player, String itemId, int button) {
        PlayerUIState state = getState(player);
        if (state == null) return;
        
        ItemInfo item = registry.getItem(itemId);
        if (item == null) return;
        
        state.selectedItem = item;
        
        if (button == 0) {
            // Left click - show recipes
            showItemRecipes(player, item);
        } else if (button == 1) {
            // Right click - show usages
            showItemUsages(player, item);
        }
    }
    
    /**
     * Get current page items
     */
    public List<ItemInfo> getCurrentPageItems(Object player) {
        PlayerUIState state = getState(player);
        if (state == null) return Collections.emptyList();
        
        return registry.getPage(state.filteredItems, state.currentPage, ITEMS_PER_PAGE);
    }
    
    /**
     * Get page info string
     */
    public String getPageInfo(Object player) {
        PlayerUIState state = getState(player);
        if (state == null) return "";
        
        int totalPages = Math.max(1, getMaxPage(state) + 1);
        return "Page " + (state.currentPage + 1) + "/" + totalPages + 
               " (" + state.filteredItems.size() + " items)";
    }
    
    /**
     * Check if can go to previous page
     */
    public boolean canGoPrevious(Object player) {
        PlayerUIState state = getState(player);
        return state != null && state.currentPage > 0;
    }
    
    /**
     * Check if can go to next page
     */
    public boolean canGoNext(Object player) {
        PlayerUIState state = getState(player);
        return state != null && state.currentPage < getMaxPage(state);
    }
    
    // ============================================
    // UI RENDERING - Replace with Hytale NoesisGUI API
    // ============================================
    
    private void showScreen(Object player, PlayerUIState state) {
        // TODO: Load XAML and show to player
        // Example:
        // View view = UIManager.loadXaml("hytalejei:ui/jei_screen.xaml");
        // view.setDataContext(createViewModel(state));
        // player.showUI(view);
        
        System.out.println("[HytaleJEI] Opening screen for player");
        System.out.println("  Search: '" + state.searchQuery + "'");
        System.out.println("  Items: " + state.filteredItems.size());
        System.out.println("  " + getPageInfo(player));
        
        // Print first page of items
        List<ItemInfo> pageItems = getCurrentPageItems(player);
        System.out.println("  Showing " + pageItems.size() + " items:");
        for (ItemInfo item : pageItems) {
            System.out.println("    - " + item.getName() + " (" + item.getId() + ")");
        }
    }
    
    private void hideScreen(Object player) {
        // TODO: Close UI for player
        System.out.println("[HytaleJEI] Closing screen");
    }
    
    private void updateDisplay(Object player, PlayerUIState state) {
        // TODO: Update UI data binding
        System.out.println("[HytaleJEI] Updating display: " + getPageInfo(player));
    }
    
    private void showItemRecipes(Object player, ItemInfo item) {
        // TODO: Show recipe panel
        System.out.println("[HytaleJEI] Showing recipes for: " + item.getName());
    }
    
    private void showItemUsages(Object player, ItemInfo item) {
        // TODO: Show usages panel
        System.out.println("[HytaleJEI] Showing usages for: " + item.getName());
    }
    
    // ============================================
    // HELPERS
    // ============================================
    
    private PlayerUIState getState(Object player) {
        return playerStates.get(getPlayerId(player));
    }
    
    private Object getPlayerId(Object player) {
        // TODO: Get actual player UUID
        return player.hashCode();
    }
    
    private int getMaxPage(PlayerUIState state) {
        return Math.max(0, (state.filteredItems.size() - 1) / ITEMS_PER_PAGE);
    }
    
    /**
     * Per-player UI state
     */
    private static class PlayerUIState {
        String searchQuery = "";
        int currentPage = 0;
        String selectedCategory = null;
        ItemInfo selectedItem = null;
        List<ItemInfo> filteredItems = new ArrayList<>();
    }
}
