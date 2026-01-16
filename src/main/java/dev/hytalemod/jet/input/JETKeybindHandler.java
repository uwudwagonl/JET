package dev.hytalemod.jet.input;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.gui.JETGui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles keybind interception for toggling the JET GUI.
 * Uses Mouse Side Button 1 (X1) to toggle the GUI.
 * 
 * Default keybind: Mouse Button X1 (side button)
 * Alternative: Can also use /jet command
 */
public class JETKeybindHandler {
    
    // Track which players have JET open
    private final Map<UUID, Boolean> jetOpenState = new ConcurrentHashMap<>();
    
    // Configurable keybind - default to X1 (mouse side button 1)
    private MouseButtonType toggleButton = MouseButtonType.X1;
    
    private static JETKeybindHandler instance;
    
    public static JETKeybindHandler getInstance() {
        if (instance == null) {
            instance = new JETKeybindHandler();
        }
        return instance;
    }
    
    /**
     * Handle mouse button event - check if it's our toggle keybind
     */
    public void onMouseButton(PlayerMouseButtonEvent event) {
        var mouseEvent = event.getMouseButton();
        
        // Only handle our configured button on press (not release)
        if (mouseEvent.mouseButtonType != toggleButton) {
            return;
        }
        
        if (mouseEvent.state != MouseButtonState.Pressed) {
            return;
        }
        
        // Get player info
        Player player = event.getPlayer();
        if (player == null) return;
        
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        
        UUID playerId = playerRef.getUuid();
        boolean isOpen = jetOpenState.getOrDefault(playerId, false);
        
        if (isOpen) {
            closeJETGui(player, playerRef);
        } else {
            openJETGui(player, playerRef);
        }
        
        // Cancel the event to prevent other actions
        event.setCancelled(true);
    }
    
    /**
     * Opens the JET GUI for a player
     */
    public void openJETGui(Player player, PlayerRef playerRef) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        
        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        
        world.execute(() -> {
            JETGui gui = new JETGui(playerRef, CustomPageLifetime.CanDismiss, "");
            player.getPageManager().openCustomPage(ref, store, gui);
            jetOpenState.put(playerRef.getUuid(), true);
            
            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Opened GUI for " + playerRef.getUsername());
        });
    }
    
    /**
     * Closes the JET GUI for a player
     */
    public void closeJETGui(Player player, PlayerRef playerRef) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        // Note: Page will auto-close when user presses ESC (CanDismiss lifetime)
        // No explicit close needed - just track the state
        jetOpenState.put(playerRef.getUuid(), false);

        JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] GUI marked for close for " + playerRef.getUsername());
    }
    
    /**
     * Called when a player disconnects - cleanup state
     */
    public void onPlayerDisconnect(UUID playerId) {
        jetOpenState.remove(playerId);
    }
    
    /**
     * Called when JET GUI is closed (e.g., by pressing Escape)
     */
    public void onGuiClosed(UUID playerId) {
        jetOpenState.put(playerId, false);
    }
    
    /**
     * Check if a player has JET open
     */
    public boolean isJETOpen(UUID playerId) {
        return jetOpenState.getOrDefault(playerId, false);
    }
    
    /**
     * Set the toggle button (for configuration)
     */
    public void setToggleButton(MouseButtonType button) {
        this.toggleButton = button;
    }
    
    /**
     * Get the current toggle button
     */
    public MouseButtonType getToggleButton() {
        return toggleButton;
    }
}
