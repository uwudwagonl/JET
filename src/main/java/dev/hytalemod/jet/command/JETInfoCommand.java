package dev.hytalemod.jet.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /jetinfo - Shows help for item info
 */
public class JETInfoCommand extends AbstractCommand {
    
    public JETInfoCommand() {
        super("jetinfo", "Display detailed item information", false);
        addAliases("ji");
        setPermissionGroup(GameMode.Adventure);
    }
    
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("[JET] Item Info").color("#FFAA00"));
        context.sendMessage(Message.raw("Use the GUI (/jet) to browse items").color("#AAAAAA"));
        context.sendMessage(Message.raw("Hover over any item for details").color("#AAAAAA"));
        
        return CompletableFuture.completedFuture(null);
    }
}
