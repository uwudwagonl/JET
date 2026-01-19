package dev.hytalemod.jet.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.gui.PinnedGui;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /pinned - Opens the pinned/favorited items browser
 */
public class PinnedCommand extends AbstractCommand {

    public PinnedCommand() {
        super("pinned", "Opens the pinned/favorited items browser", false);
        addAliases("fav", "favorites", "favourites");
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
                PinnedGui gui = new PinnedGui(playerRef, CustomPageLifetime.CanDismiss);
                player.getPageManager().openCustomPage(ref, store, gui);
            }
        }, world);
    }
}
