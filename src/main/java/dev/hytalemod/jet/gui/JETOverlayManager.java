package dev.hytalemod.jet.gui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the JET overlay that appears when inventories are opened.
 * 
 * IMPORTANT: Due to Hytale's UI architecture, we use openCustomPageWithWindows
 * to show the JET browser alongside inventory pages. This replaces the standard
 * page with our custom page that includes the inventory functionality.
 */
public class JETOverlayManager {

    private static JETOverlayManager instance;
    
    // Track overlay state per player
    private final Map<UUID, Boolean> overlayEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Page> lastKnownPage = new ConcurrentHashMap<>();

    public static JETOverlayManager getInstance() {
        if (instance == null) {
            instance = new JETOverlayManager();
        }
        return instance;
    }

    /**
     * Called when a player opens an inventory-type page (Inventory, Bench, etc.)
     * Shows the JET overlay alongside the inventory.
     */
    public void onInventoryOpened(Player player, PlayerRef playerRef, Page page) {
        UUID playerId = playerRef.getUuid();
        
        // Check if overlay is enabled for this player (default: true)
        if (!overlayEnabled.getOrDefault(playerId, true)) {
            return;
        }

        // Track the page
        lastKnownPage.put(playerId, page);

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        world.execute(() -> {
            try {
                // Create the combined inventory + JET page
                JETInventoryPage combinedPage = new JETInventoryPage(playerRef, page);
                
                // Open the combined page
                player.getPageManager().openCustomPage(ref, store, combinedPage);
                
                JETPlugin.getInstance().getLogger().at(Level.INFO)
                    .log("[JET] Opened inventory overlay for " + playerRef.getUsername() + " on page " + page);
            } catch (Exception e) {
                JETPlugin.getInstance().getLogger().at(Level.WARNING)
                    .log("[JET] Failed to open overlay: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Called when a player closes their inventory.
     */
    public void onInventoryClosed(Player player, PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        lastKnownPage.remove(playerId);
        
        JETPlugin.getInstance().getLogger().at(Level.FINE)
            .log("[JET] Inventory closed for " + playerRef.getUsername());
    }

    /**
     * Toggle the JET overlay feature for a player.
     */
    public void toggleOverlay(UUID playerId) {
        boolean current = overlayEnabled.getOrDefault(playerId, true);
        overlayEnabled.put(playerId, !current);
    }

    /**
     * Enable/disable overlay for a player.
     */
    public void setOverlayEnabled(UUID playerId, boolean enabled) {
        overlayEnabled.put(playerId, enabled);
    }

    /**
     * Check if overlay is enabled for a player.
     */
    public boolean isOverlayEnabled(UUID playerId) {
        return overlayEnabled.getOrDefault(playerId, true);
    }

    /**
     * Check if a page type should trigger the overlay.
     */
    public boolean isInventoryPage(Page page) {
        return page == Page.Inventory 
            || page == Page.Bench;
        // Add more inventory-like pages as needed
    }

    /**
     * Cleanup when player disconnects.
     */
    public void onPlayerDisconnect(UUID playerId) {
        overlayEnabled.remove(playerId);
        lastKnownPage.remove(playerId);
    }

    /**
     * Get last known page for a player.
     */
    public Page getLastKnownPage(UUID playerId) {
        return lastKnownPage.get(playerId);
    }
}
