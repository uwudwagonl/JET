package dev.hytalemod.jet.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeOverlayManager {

    private static RecipeOverlayManager instance;
    private final Map<UUID, RecipeOverlayHud> activeHuds = new ConcurrentHashMap<>();

    public static RecipeOverlayManager getInstance() {
        if (instance == null) instance = new RecipeOverlayManager();
        return instance;
    }

    public void pinRecipe(PlayerRef playerRef, String recipeId) {
        UUID uuid = playerRef.getUuid();

        // Remove existing HUD if any
        unpinRecipe(playerRef);

        if (recipeId == null || !JETPlugin.RECIPES.containsKey(recipeId)) {
            return;
        }

        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            RecipeOverlayHud hud = new RecipeOverlayHud(playerRef);
            hud.setPinnedRecipe(recipeId);
            activeHuds.put(uuid, hud);
            player.getHudManager().setCustomHud(playerRef, hud);
        } catch (Exception e) {
            activeHuds.remove(uuid);
        }
    }

    public void unpinRecipe(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        RecipeOverlayHud hud = activeHuds.remove(uuid);
        if (hud != null) {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getHudManager().setCustomHud(playerRef, null);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public void toggleRecipe(PlayerRef playerRef, String recipeId) {
        UUID uuid = playerRef.getUuid();
        RecipeOverlayHud existing = activeHuds.get(uuid);

        if (existing != null && recipeId.equals(existing.getPinnedRecipeId())) {
            unpinRecipe(playerRef);
        } else {
            pinRecipe(playerRef, recipeId);
        }
    }

    public void refreshHud(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        RecipeOverlayHud hud = activeHuds.get(uuid);
        if (hud != null && hud.hasPinnedRecipe()) {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getHudManager().setCustomHud(playerRef, hud);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean hasPinnedRecipe(UUID uuid) {
        RecipeOverlayHud hud = activeHuds.get(uuid);
        return hud != null && hud.hasPinnedRecipe();
    }

    public String getPinnedRecipeId(UUID uuid) {
        RecipeOverlayHud hud = activeHuds.get(uuid);
        return hud != null ? hud.getPinnedRecipeId() : null;
    }

    public void onPlayerDisconnect(UUID uuid) {
        activeHuds.remove(uuid);
    }
}
