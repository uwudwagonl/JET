package dev.hytalemod.jei;

import dev.hytalemod.jei.command.JEICommand;
import dev.hytalemod.jei.registry.ItemRegistry;
import dev.hytalemod.jei.ui.JEIScreen;

/**
 * HytaleJEI - A JEI-style item encyclopedia for Hytale
 * 
 * Features:
 * - Browse all registered items/blocks
 * - Real-time search with filters (@category, #tag)
 * - Item info display
 * - Keybind support (R key)
 */
public class HytaleJEIPlugin {
    
    private static HytaleJEIPlugin instance;
    
    private ItemRegistry itemRegistry;
    private JEIScreen jeiScreen;
    private JEICommand jeiCommand;
    
    /**
     * Constructor - Called when plugin is loaded
     */
    public HytaleJEIPlugin() {
        instance = this;
        log("HytaleJEI loading...");
    }
    
    /**
     * Called when the plugin is enabled
     */
    public void onEnable() {
        // Initialize item registry
        this.itemRegistry = new ItemRegistry();
        this.itemRegistry.scanAllItems();
        
        // Initialize UI screen manager
        this.jeiScreen = new JEIScreen(itemRegistry);
        
        // Initialize command handler
        this.jeiCommand = new JEICommand(this);
        
        // TODO: Register command with Hytale API
        // Server.getCommandManager().register("jei", jeiCommand);
        
        // TODO: Register keybind with Hytale API
        // Server.getKeybindManager().register("R", this::onKeybindPressed);
        
        log("HytaleJEI enabled! Found " + itemRegistry.getItemCount() + " items.");
        log("Use /jei or press R to open the item browser.");
    }
    
    /**
     * Called when the plugin is disabled
     */
    public void onDisable() {
        log("HytaleJEI disabled.");
    }
    
    /**
     * Open JEI screen for a player
     */
    public void openJEIScreen(Object player) {
        jeiScreen.open(player);
    }
    
    /**
     * Open JEI screen with search query
     */
    public void openJEIScreenWithSearch(Object player, String searchQuery) {
        jeiScreen.open(player, searchQuery);
    }
    
    /**
     * Handle keybind press
     */
    public void onKeybindPressed(Object player) {
        openJEIScreen(player);
    }
    
    // Getters
    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }
    
    public JEIScreen getJeiScreen() {
        return jeiScreen;
    }
    
    public JEICommand getJeiCommand() {
        return jeiCommand;
    }
    
    public static HytaleJEIPlugin getInstance() {
        return instance;
    }
    
    // Logging helper
    private void log(String message) {
        System.out.println("[HytaleJEI] " + message);
    }
}
