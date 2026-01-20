package dev.hytalemod.jet.listener;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.gui.JETOverlayManager;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Listens for page change packets to show/hide the JET overlay.
 * 
 * When a player opens their inventory (Page.Inventory) or a crafting bench (Page.Bench),
 * we show the JET item browser overlay on the right side of the screen.
 */
public class PageChangeListener {

    private static final String REQUEST_SET_PAGE_CLASS = "com.hypixel.hytale.protocol.packets.interface_.RequestSetPage";
    private PacketFilter packetFilter;

    public void register() {
        try {
            // Register inbound packet filter to intercept RequestSetPage packets
            packetFilter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onPacket);
            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Registered page change packet listener");
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log("[JET] Failed to register packet listener: " + e.getMessage());
        }
    }

    public void unregister() {
        // PacketFilter doesn't have a simple unregister - it will be cleaned up when plugin unloads
        packetFilter = null;
    }

    private boolean onPacket(PlayerRef playerRef, Packet packet) {
        if (packet == null || playerRef == null) {
            return true; // Continue processing
        }

        String className = packet.getClass().getName();
        
        // Check if this is a RequestSetPage packet
        if (className.equals(REQUEST_SET_PAGE_CLASS) || className.endsWith("RequestSetPage")) {
            handlePageChangePacket(playerRef, packet);
        }

        return true; // Continue processing (don't block the packet)
    }

    private void handlePageChangePacket(PlayerRef playerRef, Packet packet) {
        try {
            // Get the page field from the packet using reflection
            Page page = null;
            
            // Try to get the 'page' field
            try {
                Field pageField = packet.getClass().getDeclaredField("page");
                pageField.setAccessible(true);
                Object pageObj = pageField.get(packet);
                if (pageObj instanceof Page) {
                    page = (Page) pageObj;
                }
            } catch (NoSuchFieldException e) {
                // Try alternative field names
                for (Field field : packet.getClass().getDeclaredFields()) {
                    if (field.getType() == Page.class) {
                        field.setAccessible(true);
                        page = (Page) field.get(packet);
                        break;
                    }
                }
            }

            if (page == null) {
                return;
            }

            JETPlugin.getInstance().getLogger().at(Level.FINE)
                .log("[JET] Player " + playerRef.getUsername() + " changing to page: " + page);

            // Get player component
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            JETOverlayManager manager = JETOverlayManager.getInstance();

            // Show overlay for inventory-type pages, hide for others
            if (manager.isInventoryPage(page)) {
                manager.onInventoryOpened(player, playerRef, page);
            } else if (page == Page.None) {
                manager.onInventoryClosed(player, playerRef);
            }

        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING)
                .log("[JET] Error handling page change: " + e.getMessage());
        }
    }
}
