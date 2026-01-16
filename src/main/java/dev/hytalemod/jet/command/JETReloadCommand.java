package dev.hytalemod.jet.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.hytalemod.jet.JETPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /jet reload - Forces a rescan of item registries
 */
public class JETReloadCommand extends AbstractCommand {
    
    public JETReloadCommand() {
        super("jetreload", "Reload item registry", false);
        addAliases("jr");
        setPermissionGroup(GameMode.Creative); // Require creative/admin for reload
    }
    
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("§6[JET] Registry info:"));
        context.sendMessage(Message.raw("§7Items loaded: §f" + JETPlugin.getInstance().getItemRegistry().size()));
        context.sendMessage(Message.raw("§7Note: Items are auto-loaded from LoadedAssetsEvent"));
        context.sendMessage(Message.raw("§7Registry rebuilds when world reloads"));
        
        return CompletableFuture.completedFuture(null);
    }
}
