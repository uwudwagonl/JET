package dev.hytalemod.jet.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.component.JETKeybindComponent;
import dev.hytalemod.jet.config.JETConfig;
import dev.hytalemod.jet.gui.JETGui;
import dev.hytalemod.jet.storage.BrowserState;

import java.util.Set;
import java.util.logging.Level;

public class AltKeyBind extends EntityTickingSystem<EntityStore> {

    @Override
    public void tick(float deltaTime, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            MovementStatesComponent movementComp = chunk.getComponent(index, MovementStatesComponent.getComponentType());
            Player player = chunk.getComponent(index, Player.getComponentType());

            if (player == null || movementComp == null) {
                return;
            }

            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }

            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }

            JETConfig config = JETPlugin.getInstance().getConfig();
            if (config == null || !config.bindAltKey) {
                return;
            }

            MovementStates states = movementComp.getMovementStates();
            boolean isWalking = states.walking;

            // Get or create the keybind component to track previous state
            JETKeybindComponent keybindComp = store.getComponent(ref, JETKeybindComponent.getComponentType());
            if (keybindComp == null) {
                keybindComp = new JETKeybindComponent();
                commandBuffer.addComponent(ref, JETKeybindComponent.getComponentType(), keybindComp);
            }

            boolean wasWalking = keybindComp.previousWalkState;
            keybindComp.previousWalkState = isWalking;

            // Only trigger on the transition from not walking to walking (Alt key press)
            if (isWalking && !wasWalking) {
                PageManager pageManager = player.getPageManager();
                if (pageManager.getCustomPage() != null) {
                    return; // Don't open if another custom page is already showing
                }

                if (config.requireCreativeOrOp) {
                    GameMode gameMode = player.getGameMode();
                    boolean isCreative = gameMode != null && gameMode.name().equals("Creative");
                    if (!isCreative) {
                        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
                        PermissionsModule perms = PermissionsModule.get();
                        Set<String> groups = perms.getGroupsForUser(uuidComponent.getUuid());
                        boolean isOp = groups != null && groups.contains("OP");
                        if (!isOp) {
                            return;
                        }
                    }
                }

                try {
                    BrowserState saved = JETPlugin.getInstance().getBrowserStateStorage().getState(playerRef.getUuid());
                    JETGui gui = new JETGui(playerRef, CustomPageLifetime.CanDismiss, "", saved);
                    pageManager.openCustomPage(ref, store, gui);
                } catch (Exception e) {
                    JETPlugin.getInstance().log(Level.WARNING, "[JET] Failed to open browser via Alt key: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            JETPlugin.getInstance().log(Level.WARNING, "[JET] Error in AltKeyBind: " + e.getMessage());
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return MovementStatesComponent.getComponentType();
    }
}
