package dev.hytalemod.jet.filter;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemod.jet.JETPlugin;

/**
 * Intercepts O key presses (GameModeSwap packets) to trigger JET browser
 */
public class OKeyPacketFilter implements PlayerPacketFilter {
    private final JETPlugin plugin;

    public OKeyPacketFilter(JETPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean test(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof SyncInteractionChains)) {
            return false;
        }

        SyncInteractionChains chains = (SyncInteractionChains) packet;
        if (chains.updates == null || chains.updates.length == 0) {
            return false;
        }

        for (SyncInteractionChain chain : chains.updates) {
            if (chain == null) continue;

            if (chain.interactionType == InteractionType.GameModeSwap) {
                plugin.getLogger().at(java.util.logging.Level.INFO).log("[JET] O key pressed by " + playerRef.getUsername());
                boolean result = plugin.handleGameModeSwap(playerRef);
                plugin.getLogger().at(java.util.logging.Level.INFO).log("[JET] GameModeSwap handled, cancelled: " + result);
                return result;
            }
        }

        return false;
    }
}
