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
import dev.hytalemod.jet.config.JETConfig;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * [THIS DOES NOT CURRENTLY WORK] 
 * Command to enable/disable O key binding
 * Usage: /jetbind <on|off|status>
 */
public class JETBindCommand extends AbstractCommand {

    public JETBindCommand() {
        super("jetbind", "Toggle O key binding status for JET", false);
        addAliases("jbind");
        setPermissionGroup(GameMode.Creative); // Only creative (admin/op) can use this
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

            // Toggle the binding on/off
            toggleBinding(playerRef);
        }, world);
    }

    private void toggleBinding(PlayerRef playerRef) {
        JETPlugin plugin = JETPlugin.getInstance();
        JETConfig config = plugin.getConfig();

        // Toggle the setting
        config.bindOKey = !config.bindOKey;
        plugin.saveConfig();

        playerRef.sendMessage(Message.raw("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color("#55AAFF"));
        playerRef.sendMessage(Message.raw("[JET] O Key Binding").color("#55AAFF"));
        playerRef.sendMessage(Message.raw("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color("#55AAFF"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  Status: " + (config.bindOKey ? "✓ ENABLED" : "✗ DISABLED")).color(config.bindOKey ? "#55FF55" : "#FF5555"));
        playerRef.sendMessage(Message.raw("  Cooldown: " + config.cooldownMs + "ms").color("#AAAAAA"));
        playerRef.sendMessage(Message.raw(""));

        if (config.bindOKey) {
            playerRef.sendMessage(Message.raw("  ✓ O key will open JET browser").color("#55FF55"));
        } else {
            playerRef.sendMessage(Message.raw("  ✗ O key will toggle creative mode").color("#FF5555"));
        }

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  ⚠ Restart required for changes to take effect").color("#FFAA00"));
        playerRef.sendMessage(Message.raw("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color("#55AAFF"));

        String action = config.bindOKey ? "enabled" : "disabled";
        plugin.log(Level.INFO, "[JET] O key binding " + action + " by " + playerRef.getUsername());
    }
}
