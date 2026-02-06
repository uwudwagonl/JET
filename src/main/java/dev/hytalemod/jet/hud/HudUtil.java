package dev.hytalemod.jet.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Utility for managing recipe HUD updates
 */
public class HudUtil {

    /**
     * Update the recipe HUD for a player
     */
    public static void updateHud(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        // Update the HUD
        player.getHudManager().setCustomHud(playerRef, new RecipeHud(playerRef));
    }
}