package dev.hytalemod.jet.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.config.JETConfig;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * /pinrecipe [recipeId] - Pin a recipe to the HUD overlay
 * /pinrecipe - Unpin current recipe
 */
public class JETPinRecipeCommand extends AbstractCommand {

    private final OptionalArg<String> recipeArg;

    public JETPinRecipeCommand() {
        super("pinrecipe", "Pin a recipe to your screen", false);
        addAliases("pr", "trackrecipe", "tr");
        setPermissionGroup(GameMode.Adventure);
        
        recipeArg = withOptionalArg("recipe", "Recipe ID to pin", ArgTypes.STRING);
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
            if (playerRef == null) return;

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

            String recipeId = recipeArg.get(context);


            // Try to find the recipe
            String finalRecipeId = resolveRecipeId(recipeId);
            
            if (finalRecipeId == null) {
                // Search for partial match
                String searchLower = recipeId.toLowerCase();
                List<String> matches = JETPlugin.RECIPES.keySet().stream()
                    .filter(id -> id.toLowerCase().contains(searchLower))
                    .limit(5)
                    .toList();

                if (matches.isEmpty()) {
                    playerRef.sendMessage(Message.raw("[JET] Recipe not found: " + recipeId).color("#FF5555"));
                    return;
                } else if (matches.size() == 1) {
                    finalRecipeId = matches.get(0);
                } else {
                    playerRef.sendMessage(Message.raw("[JET] Multiple recipes found:").color("#FFFF55"));
                    for (String match : matches) {
                        playerRef.sendMessage(Message.raw("  - " + match).color("#AAAAAA"));
                    }
                    return;
                }
            }


            String displayId = finalRecipeId.contains(":") ? finalRecipeId.substring(finalRecipeId.indexOf(":") + 1) : finalRecipeId;
            playerRef.sendMessage(Message.raw("[JET] Pinned recipe: " + displayId).color("#55FF55"));
            playerRef.sendMessage(Message.raw("[JET] Use /pinrecipe again to unpin.").color("#AAAAAA"));
        }, world);
    }
    
    private String resolveRecipeId(String input) {
        if (JETPlugin.RECIPES.containsKey(input)) {
            return input;
        }
        if (JETPlugin.RECIPES.containsKey("hytale:" + input)) {
            return "hytale:" + input;
        }
        return null;
    }
}
