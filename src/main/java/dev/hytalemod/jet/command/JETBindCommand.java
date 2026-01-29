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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
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
        saveConfig(plugin);

        playerRef.sendMessage(Message.raw("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        playerRef.sendMessage(Message.raw("§b[JET] O Key Binding"));
        playerRef.sendMessage(Message.raw("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("§7  Status: " + (config.bindOKey ? "§a✓ ENABLED" : "§c✗ DISABLED")));
        playerRef.sendMessage(Message.raw("§7  Cooldown: §f" + config.cooldownMs + "ms"));
        playerRef.sendMessage(Message.raw(""));

        if (config.bindOKey) {
            playerRef.sendMessage(Message.raw("§a  ✓ O key will open JET browser"));
        } else {
            playerRef.sendMessage(Message.raw("§c  ✗ O key will toggle creative mode"));
        }

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("§e  ⚠ Restart required for changes to take effect"));
        playerRef.sendMessage(Message.raw("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        String action = config.bindOKey ? "enabled" : "disabled";
        plugin.getLogger().at(Level.INFO).log("[JET] O key binding " + action + " by " + playerRef.getUsername());
    }

    private void saveConfig(JETPlugin plugin) {
        try {
            Path configDir = plugin.getFile().getParent().resolve("JET");
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve("JET_config.json");

            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(plugin.getConfig());
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().at(Level.WARNING).log("[JET] Failed to save config: " + e.getMessage());
        }
    }
}
