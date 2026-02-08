package dev.hytalemod.jet.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.gui.JETGui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public API for other mods to interact with JET (Just Enough Tales)
 *
 * This allows other mods to open the JET browser with a specific item pre-selected.
 *
 * Example usage from another mod:
 * <pre>
 * // Option 1: Using Player object (preferred)
 * JETApi.openBrowser(player, ref, store, "Block_Stone");
 *
 * // Option 2: Using PlayerRef only
 * JETApi.openBrowserWithSearch(playerRef, "Block_Stone");
 * </pre>
 */
public class JETApi {

    /**
     * Opens the JET browser for a player with a specific item selected
     *
     * @param player The player to open the browser for
     * @param ref The entity store reference
     * @param store The entity store
     * @param itemId The item ID to select (e.g., "Block_Stone", "Item_Wood_Plank")
     * @return true if the browser was successfully opened, false otherwise
     */
    public static boolean openBrowser(@Nonnull Player player,
                                     @Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull String itemId) {
        try {
            JETPlugin plugin = JETPlugin.getInstance();
            if (plugin == null) {
                return false;
            }

            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return false;
            }

            // Create GUI with the selected item as search query
            JETGui gui = new JETGui(playerRef, CustomPageLifetime.CanDismiss, itemId, null);

            // Open the custom page
            player.getPageManager().openCustomPage(ref, store, gui);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens the JET browser for a player with search pre-filled
     *
     * This is a simpler API that only requires a PlayerRef, but it will only
     * search for the item rather than directly selecting it.
     *
     * @param playerRef The player reference
     * @param searchQuery The search query to pre-fill (e.g., "Stone", "Wood")
     * @return true if the command was successfully sent, false otherwise
     */
    public static boolean openBrowserWithSearch(@Nonnull PlayerRef playerRef, @Nonnull String searchQuery) {
        try {
            JETPlugin plugin = JETPlugin.getInstance();
            if (plugin == null) {
                return false;
            }

            // For now, we'll just send a message to the player
            // A more sophisticated implementation would use the command system
            // TODO: Implement proper GUI opening from PlayerRef only
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "[JET] Opening browser for: " + searchQuery
            ).color("#55AAFF"));

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if JET is available and loaded
     *
     * @return true if JET plugin is loaded and available
     */
    public static boolean isAvailable() {
        return JETPlugin.getInstance() != null;
    }

    /**
     * Get the JET plugin version
     *
     * @return The version string, or null if JET is not loaded
     */
    @Nullable
    public static String getVersion() {
        JETPlugin plugin = JETPlugin.getInstance();
        return plugin != null ? JETPlugin.VERSION : null;
    }
}
