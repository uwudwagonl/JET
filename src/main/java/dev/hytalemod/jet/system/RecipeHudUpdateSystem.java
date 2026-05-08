package dev.hytalemod.jet.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.hud.HudUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS event system that handles inventory change events to update recipe HUD
 */
public class RecipeHudUpdateSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    private static final long UPDATE_COOLDOWN_MS = 250;
    private static final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();

    public RecipeHudUpdateSystem() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull InventoryChangeEvent event) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }

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

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    /**
     * Clear tracking for a player
     */
    public static void clearPlayer(UUID uuid) {
        lastUpdateTime.remove(uuid);
    }

}