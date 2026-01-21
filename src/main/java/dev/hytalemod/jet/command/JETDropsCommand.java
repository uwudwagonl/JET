package dev.hytalemod.jet.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.hytalemod.jet.JETPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Debug command to test drop list loading
 */
public class JETDropsCommand extends AbstractCommand {

    public JETDropsCommand() {
        super("jetdrops", "View drop list debug info", false);
        addAliases("jdrops");
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        int dropListCount = JETPlugin.getInstance().getDropListRegistry().size();

        if (dropListCount == 0) {
            context.sendMessage(Message.raw("[JET] No drop lists loaded yet!").color("#FF5555"));
            context.sendMessage(Message.raw("Check logs for details.").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("[JET] Drop Lists Info:").color("#55FF55"));
        context.sendMessage(Message.raw("Total drop lists: " + dropListCount).color("#AAAAAA"));

        // Show first 5 drop list IDs as examples
        var dropLists = JETPlugin.getInstance().getDropListRegistry().getAllDropLists();
        int count = 0;
        context.sendMessage(Message.raw("Example drop list IDs:").color("#AAAAAA"));

        for (String id : dropLists.keySet()) {
            if (count++ >= 5) break;
            context.sendMessage(Message.raw("  - " + id).color("#AAAAAA"));
        }

        if (dropLists.size() > 5) {
            context.sendMessage(Message.raw("  ... and " + (dropLists.size() - 5) + " more").color("#AAAAAA"));
        }

        context.sendMessage(Message.raw("Check logs for detailed debug info:").color("#AAAAAA"));
        context.sendMessage(Message.raw("  JET_droplists_debug.txt").color("#AAAAAA"));
        context.sendMessage(Message.raw("  JET_droplist_methods.txt").color("#AAAAAA"));

        return CompletableFuture.completedFuture(null);
    }
}
