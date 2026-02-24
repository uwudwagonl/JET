package dev.hytalemod.jet.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.config.JETConfig;
import dev.hytalemod.jet.gui.PinnedGui;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * /pinned or /fav - Opens the pinned items viewer
 */
public class JETPinnedCommand extends AbstractCommand {

    public JETPinnedCommand() {
        super("pinned", "Opens the pinned items viewer", false);
        addAliases("fav", "favorites", "favourites", "p");
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
            PlayerRef playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());

            if (playerRef != null) {
                JETConfig config = JETPlugin.getInstance().getConfig();

                if (config.requireCreativeOrOp) {
                    GameMode gameMode = player.getGameMode();
                    boolean isCreative = gameMode != null && gameMode.name().equals("Creative");
                    if (!isCreative) {
                        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
                        PermissionsModule perms = PermissionsModule.get();
                        Set<String> groups = perms.getGroupsForUser(uuidComponent.getUuid());
                        boolean isOp = groups != null && groups.contains("OP");
                        if (!isOp) {
                            playerRef.sendMessage(Message.raw("[JET] This command requires Creative mode or OP.").color("#FF5555"));
                            return;
                        }
                    }
                }

                Set<String> pinnedItems = JETPlugin.getInstance().getPinnedItemsStorage().getPinnedItems(playerRef.getUuid());

                if (pinnedItems.isEmpty()) {
                    player.sendMessage(Message.raw("No pinned items yet! Use /jet to browse items and pin your favorites.").color("#ffaa00"));
                    return;
                }

                PinnedGui gui = new PinnedGui(playerRef, CustomPageLifetime.CanDismiss);
                player.getPageManager().openCustomPage(ref, store, gui);
            }
        }, world);
    }
}
