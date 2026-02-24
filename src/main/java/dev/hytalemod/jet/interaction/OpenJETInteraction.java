package dev.hytalemod.jet.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.gui.JETGui;
import dev.hytalemod.jet.storage.BrowserState;

import java.util.logging.Level;

public class OpenJETInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<OpenJETInteraction> CODEC = BuilderCodec.builder(
            OpenJETInteraction.class,
            OpenJETInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    public OpenJETInteraction() {
    }

    @Override
    protected void firstRun(InteractionType interactionType, InteractionContext context, CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        CommandBuffer commandBuffer = context.getCommandBuffer();

        Player player = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        PlayerRef playerRef = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            if (JETPlugin.getInstance().getConfig().disableGlyph) {
                world.execute(() -> playerRef.sendMessage(Message.raw("[JET] The Pex Glyph is disabled on this server.").color("#FFAA00")));
                return;
            }

            world.execute(() -> {
                try {
                    BrowserState saved = JETPlugin.getInstance().getBrowserStateStorage().getState(playerRef.getUuid());
                    JETGui gui = new JETGui(playerRef, CustomPageLifetime.CanDismiss, "", saved);
                    player.getPageManager().openCustomPage(ref, store, gui);
                } catch (Exception e) {
                    JETPlugin.getInstance().log(Level.SEVERE, "[JET] Failed to open JET GUI: " + e.getMessage());
                }
            });
        }
    }
}
