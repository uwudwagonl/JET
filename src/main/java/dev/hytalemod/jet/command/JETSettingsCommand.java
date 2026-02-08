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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.gui.JETSettingsGui;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Command to open JET settings GUI
 */
public class JETSettingsCommand extends AbstractCommand {

    public JETSettingsCommand() {
        super("jetsettings", "Open JET settings", false);
        addAliases("jsettings");
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
        World world = ((EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerRef == null) {
                return;
            }

            try {
                JETSettingsGui gui = new JETSettingsGui(playerRef, JETPlugin.getInstance());
                player.getPageManager().openCustomPage(ref, store, gui);
            } catch (Exception e) {
                playerRef.sendMessage(Message.raw("[JET] Failed to open settings: " + e.getMessage()).color("#FF5555"));
                JETPlugin.getInstance().log(Level.WARNING, "[JET] Failed to open settings GUI: " + e.getMessage());
            }
        }, world);
    }
}
