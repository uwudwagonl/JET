package dev.hytalemod.jet.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.hud.HudUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System that tracks inventory changes and updates HUD when items change
 */
public class InventoryTrackingSystem {

    private static final Map<UUID, Map<String, Integer>> lastInventoryState = new ConcurrentHashMap<>();
    private static final long UPDATE_INTERVAL_MS = 500; // Update every 500ms max
    private static final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();

    /**
     * Check if player's inventory has changed and update HUD if needed
     */
    public static void checkInventoryChange(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        // Check if player has pinned recipes
        RecipeHudComponent component = store.getComponent(ref, RecipeHudComponent.getComponentType());
        if (component == null || component.pinnedRecipes.isEmpty()) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        // Rate limit updates
        long now = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTime.get(uuid);
        if (lastUpdate != null && (now - lastUpdate) < UPDATE_INTERVAL_MS) {
            return;
        }

        // Get current inventory state
        Map<String, Integer> currentState = getInventoryState(player);
        Map<String, Integer> previousState = lastInventoryState.get(uuid);

        // Check if inventory changed
        if (previousState != null && !hasInventoryChanged(previousState, currentState)) {
            return;
        }

        // Update state and HUD
        lastInventoryState.put(uuid, currentState);
        lastUpdateTime.put(uuid, now);
        HudUtil.updateHud(ref);
    }

    /**
     * Get simplified inventory state (item ID -> count)
     */
    private static Map<String, Integer> getInventoryState(Player player) {
        Map<String, Integer> state = new HashMap<>();
        Inventory inventory = player.getInventory();

        if (inventory == null) {
            return state;
        }

        try {
            // Count all items across all inventory sections
            inventory.getCombinedEverything().forEach((slot, itemStack) -> {
                if (itemStack != null && itemStack.getItemId() != null) {
                    String itemId = itemStack.getItemId();
                    int quantity = itemStack.getQuantity();
                    state.merge(itemId, quantity, Integer::sum);
                }
            });
        } catch (Exception ignored) {
        }

        return state;
    }

    /**
     * Check if inventory has meaningfully changed
     */
    private static boolean hasInventoryChanged(Map<String, Integer> previous, Map<String, Integer> current) {
        if (previous.size() != current.size()) {
            return true;
        }

        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            Integer prevCount = previous.get(entry.getKey());
            if (prevCount == null || !prevCount.equals(entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Clear tracking for a player (on disconnect)
     */
    public static void clearPlayer(UUID uuid) {
        lastInventoryState.remove(uuid);
        lastUpdateTime.remove(uuid);
    }

    /**
     * Force update for a player
     */
    public static void forceUpdate(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (playerRef != null) {
            lastUpdateTime.remove(playerRef.getUuid());
            checkInventoryChange(ref);
        }
    }
}