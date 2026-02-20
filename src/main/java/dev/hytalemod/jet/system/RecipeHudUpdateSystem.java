package dev.hytalemod.jet.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.hud.HudUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System that runs every tick to check for inventory changes
 */
public class RecipeHudUpdateSystem {
    private static final long UPDATE_COOLDOWN_MS = 250; // Debounce updates
    private static final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();

    /**
     * Register this as an event listener in JETPlugin:
     * getEventRegistry().register(InventoryChangeEvent.class, EventBasedHudUpdateSystem::onInventoryChange);
     */
    public static void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        Ref<EntityStore> ref = event.getEntity().getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();

        // Check if this is a player
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Check if player has pinned recipes
        RecipeHudComponent component = store.getComponent(ref, RecipeHudComponent.getComponentType());
        if (component == null || component.pinnedRecipes.isEmpty()) {
            return;
        }

        // Debounce updates to avoid spam
        UUID uuid = playerRef.getUuid();
        long now = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTime.get(uuid);

        if (lastUpdate != null && (now - lastUpdate) < UPDATE_COOLDOWN_MS) {
            return;
        }

        lastUpdateTime.put(uuid, now);
        World world = ((EntityStore) store.getExternalData()).getWorld();
        CompletableFuture.runAsync(() -> HudUtil.updateHud(ref), world);
    }

    /**
     * Clear tracking for a player
     */
    public static void clearPlayer(UUID uuid) {
        lastUpdateTime.remove(uuid);
    }

}