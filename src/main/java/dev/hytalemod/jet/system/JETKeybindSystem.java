package dev.hytalemod.jet.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.gui.JETGui;
import dev.hytalemod.jet.gui.PinnedGui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System that checks for keybind presses via movement states.
 * Uses Hytale's walking state (Alt key) to open JET browser.
 */
public class JETKeybindSystem extends EntityTickingSystem<EntityStore> {

    private final Map<UUID, Boolean> wasWalkingLastTick = new ConcurrentHashMap<>();

    @Override
    @Nonnull
    public Archetype<EntityStore> getQuery() {
        // Query for entities that have both Player and MovementStatesComponent
        return Archetype.of(Player.getComponentType(), MovementStatesComponent.getComponentType());
    }

    @Override
    public void tick(float delta, int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        // Get player components
        MovementStatesComponent statesComponent = (MovementStatesComponent) archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType());
        Player player = (Player) archetypeChunk.getComponent(index, Player.getComponentType());

        if (player == null || statesComponent == null) {
            return;
        }

        Ref playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return;
        }

        UUID playerId = playerRefComponent.getUuid();
        MovementStates movementStates = statesComponent.getMovementStates();

        if (movementStates == null) {
            return;
        }

        PageManager pageManager = player.getPageManager();
        if (pageManager == null) {
            return;
        }

        CustomUIPage currentPage = pageManager.getCustomPage();

        try {
            // Check for Alt key (walking state) - opens JET browser
            boolean isWalkingNow = movementStates.walking;
            Boolean wasWalkingBefore = wasWalkingLastTick.getOrDefault(playerId, false);

            // Detect key press (transition from false to true)
            if (isWalkingNow && !wasWalkingBefore) {
                // Alt key was just pressed
                if (currentPage == null) {
                    // Open JET browser
                    JETGui gui = new JETGui(playerRefComponent, CustomPageLifetime.CanDismiss, "");
                    player.getPageManager().openCustomPage(playerRef, store, gui);
                }
            }

            wasWalkingLastTick.put(playerId, isWalkingNow);

        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.SEVERE).log("JET: Error in keybind system: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up player data when they disconnect
     */
    public void onPlayerDisconnect(UUID playerId) {
        wasWalkingLastTick.remove(playerId);
    }
}
