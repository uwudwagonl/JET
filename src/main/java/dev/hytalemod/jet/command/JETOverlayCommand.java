package dev.hytalemod.jet.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.gui.JETOverlayManager;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /jetoverlay - Toggle the inventory overlay on/off
 */
public class JETOverlayCommand extends AbstractCommand {
    
    public JETOverlayCommand() {
        super("jetoverlay", "Toggle JET inventory overlay", false);
        addAliases("jeto");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandSender sender = context.sender();
        
        if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture(null);
        }
        
        Player player = (Player) sender;
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        
        if (playerRef == null) {
            return CompletableFuture.completedFuture(null);
        }

        JETOverlayManager manager = JETOverlayManager.getInstance();
        manager.toggleOverlay(playerRef.getUuid());
        
        boolean isEnabled = manager.isOverlayEnabled(playerRef.getUuid());
        if (isEnabled) {
            playerRef.sendMessage(Message.raw("§a[JET] Inventory overlay enabled"));
        } else {
            playerRef.sendMessage(Message.raw("§c[JET] Inventory overlay disabled"));
        }
        
        return CompletableFuture.completedFuture(null);
    }
}
