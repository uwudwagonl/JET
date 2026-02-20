// File adapted from BIV (Better Item Viewer)
package dev.hytalemod.jet.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.component.RecipeHudComponent;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HUD overlay showing pinned recipes with ingredient counts
 */
public class RecipeHud extends CustomUIHud {

    public RecipeHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append("Huds/JET_HudContainer.ui");

        Ref<EntityStore> ref = getPlayerRef().getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        RecipeHudComponent component = store.getComponent(ref, RecipeHudComponent.getComponentType());
        if (component == null) {
            return;
        }
        AtomicInteger index = new AtomicInteger(0);

        for (String recipeId : component.pinnedRecipes) {
            buildRecipe(player, recipeId, cmd, index);
        }
    }

    private void buildRecipe(Player player, String recipeId, UICommandBuilder cmd, AtomicInteger index) {
        CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
        if (recipe == null) {
            return;
        }

        cmd.append("#Recipes", "Huds/JET_HudRecipe.ui");
        String recipeTag = "#Recipes[" + index.get() + "]";
        index.getAndIncrement();

        // Set output item
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs != null && outputs.length > 0) {
            MaterialQuantity output = outputs[0];
            String itemId = output.getItemId();
            if (itemId != null) {
                cmd.set(recipeTag + " #OutputItemIcon.ItemId", itemId);
                Item item = JETPlugin.ITEMS.get(itemId);
                if (item != null) {
                    cmd.set(recipeTag + " #OutputItemName.TextSpans", Message.translation(item.getTranslationKey()));
                }
            }
        }

        // Build input items
        AtomicInteger i = new AtomicInteger(0);
        String tag = "#Inputs";
        cmd.appendInline(recipeTag + " #InputItems", "Group " + tag + " {LayoutMode: Top;}");

        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs != null) {
            for (MaterialQuantity materialQuantity : inputs) {
                addMaterialQuantity(player, materialQuantity, cmd, recipeTag + " " + tag, i);
            }
        }
    }

    private void addMaterialQuantity(Player player, MaterialQuantity materialQuantity, UICommandBuilder cmd, String tag, AtomicInteger i) {
        Inventory inventory = player.getInventory();
        String itemId = materialQuantity.getItemId();
        String resourceTypeId = materialQuantity.getResourceTypeId();

        if (itemId != null) {
            Item inputItem = JETPlugin.ITEMS.get(itemId);
            if (inputItem == null) {
                return;
            }

            int count = inventory.getCombinedEverything().countItemStacks(itemStack ->
                    itemStack.getItemId().equals(itemId)
            );

            cmd.append(tag, "Huds/JET_HudRecipeEntry.ui");
            cmd.set(tag + "[" + i + "] #ItemIcon.ItemId", itemId);
            cmd.set(tag + "[" + i + "] #ItemIcon.Visible", true);

            String value = count + "/" + materialQuantity.getQuantity() + " ";
            Message name = Message.empty();

            if (count < materialQuantity.getQuantity()) {
                name.insert(Message.raw(value).color("#dd1111"));
            } else {
                name.insert(Message.raw(value).color("#11dd11"));
            }

            name.insert(" ").insert(Message.translation(inputItem.getTranslationKey()));
            cmd.set(tag + "[" + i + "] #ItemName.TextSpans", name);
            i.getAndIncrement();

        } else if (resourceTypeId != null) {
            ResourceType resourceType = ResourceType.getAssetMap().getAsset(resourceTypeId);
            if (resourceType == null) {
                return;
            }

            int count = inventory.getCombinedEverything().countItemStacks(itemStack -> {
                ItemResourceType[] resourceTypes = itemStack.getItem().getResourceTypes();
                if (resourceTypes != null) {
                    for (ItemResourceType type : resourceTypes) {
                        if (type.id != null && type.id.equals(resourceTypeId)) {
                            return true;
                        }
                    }
                }
                return false;
            });

            String icon = resourceType.getIcon();
            if (icon == null) {
                icon = "Icons/ResourceTypes/Unknown.png";
            }

            cmd.append(tag, "Huds/JET_HudRecipeEntry.ui");
            cmd.set(tag + "[" + i + "] #ResourceIcon.AssetPath", icon);
            cmd.set(tag + "[" + i + "] #ResourceIcon.Visible", true);

            String value = count + "/" + materialQuantity.getQuantity() + " ";
            Message name = Message.empty();

            if (count < materialQuantity.getQuantity()) {
                name.insert(Message.raw(value).color("#ff2222"));
            } else {
                name.insert(Message.raw(value).color("#22ff22"));
            }

            name.insert(" ").insert(Message.translation("server.resourceType." + resourceTypeId + ".name"));
            cmd.set(tag + "[" + i + "] #ItemName.TextSpans", name);
            i.getAndIncrement();
        }
    }
}
