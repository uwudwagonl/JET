package dev.hytalemod.jet.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.registry.ItemRegistry;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /jetlist - Lists categories and item counts
 */
public class JETListCommand extends AbstractCommand {
    
    public JETListCommand() {
        super("jetlist", "List item categories", false);
        addAliases("jl");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        ItemRegistry registry = JETPlugin.getInstance().getItemRegistry();
        
        context.sendMessage(Message.raw("========= JET Categories =========").color("#FFAA00"));
        
        for (ItemRegistry.Category cat : registry.getCategories()) {
            int count = registry.getByCategory(cat).size();
            context.sendMessage(Message.raw(cat.getDisplayName() + ": " + count + " items"));
        }
        
        context.sendMessage(Message.raw("==================================").color("#FFAA00"));
        context.sendMessage(Message.raw("Use /jet to open the browser").color("#AAAAAA"));
        context.sendMessage(Message.raw("Search with @category or #tag").color("#AAAAAA"));
        
        return CompletableFuture.completedFuture(null);
    }
}
