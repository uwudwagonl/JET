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
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.hud.HudUtil;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Command to pin/unpin recipes to HUD
 * Usage: /recipehud <recipeId>
 */
public class JETRecipeHudCommand extends AbstractCommand {
    OptionalArg<String> arg;
    public JETRecipeHudCommand() {
        super("recipehud", "Pin/unpin recipes to HUD overlay", false);
        addAliases("rhud", "pinhud");
        setPermissionGroup(GameMode.Adventure);
        arg = withOptionalArg("recipe", "Recipe ID to pin", ArgTypes.STRING);

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

            // Get args
            String args = arg.get(context);
            if (args.isEmpty()) {
                RecipeHudComponent component = store.getComponent(ref, RecipeHudComponent.getComponentType());
                if (component == null || component.pinnedRecipes.isEmpty()) {
                    playerRef.sendMessage(Message.raw("No recipes pinned to HUD. Use recipe IDs from JET browser.").color("#FFAA00"));
                } else {
                    playerRef.sendMessage(Message.raw("[JET] Pinned Recipes:").color("#55AAFF"));
                    for (String recipeId : component.pinnedRecipes) {
                        playerRef.sendMessage(Message.raw("  - " + recipeId).color("#AAAAAA"));
                    }
                }
                return;
            }

            String recipeId = args;

            // Verify recipe exists
            if (!JETPlugin.RECIPES.containsKey(recipeId)) {
                playerRef.sendMessage(Message.raw("[JET] Recipe not found: " + recipeId).color("#FF5555"));
                return;
            }

            RecipeHudComponent component = store.ensureAndGetComponent(ref, RecipeHudComponent.getComponentType());
            component.toggleRecipe(recipeId);

            if (component.hasRecipe(recipeId)) {
                playerRef.sendMessage(Message.raw("[JET] Pinned recipe to HUD: " + recipeId).color("#55FF55"));
            } else {
                playerRef.sendMessage(Message.raw("[JET] Unpinned recipe from HUD: " + recipeId).color("#FFAA00"));
            }

            // Update HUD
            HudUtil.updateHud(ref);
        }, world);
    }
}