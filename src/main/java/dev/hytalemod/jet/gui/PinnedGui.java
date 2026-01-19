package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Pinned/Favorited Items GUI
 */
public class PinnedGui extends InteractiveCustomUIPage<PinnedGui.GuiData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int MAX_ROWS = 8;
    private static final int MAX_ITEMS = ITEMS_PER_ROW * MAX_ROWS;
    private static final int RECIPES_PER_PAGE = 3;

    private String selectedItem;
    private String activeSection;
    private int craftPage;
    private int usagePage;

    public PinnedGui(PlayerRef playerRef, CustomPageLifetime lifetime) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.selectedItem = null;
        this.activeSection = "craft";
        this.craftPage = 0;
        this.usagePage = 0;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Gui.ui");

        // Hide search bar for pinned items view
        cmd.set("#SearchInput.Visible", false);

        // Mode toggle button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #ToggleModeButton",
                EventData.of("ToggleMode", "toggle"),
                false
        );

        // Pagination
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipe", EventData.of("PageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipe", EventData.of("PageChange", "next"), false);

        buildItemList(ref, cmd, events, store);
        buildRecipePanel(ref, cmd, events, store);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, GuiData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsItemUpdate = false;
        boolean needsRecipeUpdate = false;

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.selectedItem = data.selectedItem;
            this.craftPage = 0;
            this.usagePage = 0;
            this.activeSection = "craft";
            needsRecipeUpdate = true;
        }

        if (data.toggleMode != null && "toggle".equals(data.toggleMode)) {
            this.activeSection = "craft".equals(this.activeSection) ? "usage" : "craft";
            this.craftPage = 0;
            this.usagePage = 0;
            needsRecipeUpdate = true;
        }

        if (data.activeSection != null && !data.activeSection.isEmpty() && !data.activeSection.equals(this.activeSection)) {
            this.activeSection = data.activeSection;
            this.craftPage = 0;
            this.usagePage = 0;
            needsRecipeUpdate = true;
        }

        if (data.pageChange != null) {
            List<String> recipeIds = "craft".equals(this.activeSection)
                    ? JETPlugin.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList())
                    : JETPlugin.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);

            if ("prev".equals(data.pageChange)) {
                if ("craft".equals(this.activeSection) && craftPage > 0) {
                    craftPage--;
                    needsRecipeUpdate = true;
                } else if ("usage".equals(this.activeSection) && usagePage > 0) {
                    usagePage--;
                    needsRecipeUpdate = true;
                }
            } else if ("next".equals(data.pageChange)) {
                if ("craft".equals(this.activeSection) && craftPage < totalPages - 1) {
                    craftPage++;
                    needsRecipeUpdate = true;
                } else if ("usage".equals(this.activeSection) && usagePage < totalPages - 1) {
                    usagePage++;
                    needsRecipeUpdate = true;
                }
            }
        }

        // Handle pin toggle
        if (data.pinToggle != null && !data.pinToggle.isEmpty()) {
            UUID playerId = playerRef.getUuid();
            JETPlugin.getInstance().getPinManager().togglePin(playerId, data.pinToggle);
            needsItemUpdate = true; // Refresh to remove unpinned items
        }

        if (needsItemUpdate || needsRecipeUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            if (needsItemUpdate) {
                buildItemList(ref, cmd, events, store);
            }
            if (needsRecipeUpdate) {
                buildRecipePanel(ref, cmd, events, store);
            }

            sendUpdate(cmd, events, false);
        }
    }

    private void buildItemList(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.clear("#ItemCards");

        UUID playerId = playerRef.getUuid();
        List<String> pinnedItemIds = JETPlugin.getInstance().getPinManager().getPinnedItems(playerId);

        if (pinnedItemIds.isEmpty()) {
            // Show "No pinned items" message
            cmd.appendInline("#ItemCards", "Label { Style: (FontSize: 18, TextColor: #ffffff); }");
            cmd.set("#ItemCards[0].TextSpans", Message.raw("No pinned items yet!\nPin items from the main browser."));
            return;
        }

        String language = playerRef.getLanguage();

        int row = 0;
        int col = 0;
        int count = 0;

        for (String itemId : pinnedItemIds) {
            if (count >= MAX_ITEMS) break;

            Item item = JETPlugin.ITEMS.get(itemId);
            if (item == null) continue; // Skip if item doesn't exist

            if (col == 0) {
                cmd.appendInline("#ItemCards", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            cmd.append("#ItemCards[" + row + "]", "Pages/JET_ItemIcon.ui");
            String sel = "#ItemCards[" + row + "][" + col + "]";

            cmd.set(sel + " #ItemIcon.ItemId", itemId);

            String displayName = getDisplayName(item, language);
            if (displayName.length() > 14) {
                displayName = displayName.substring(0, 12) + "...";
            }
            cmd.set(sel + " #ItemName.TextSpans", Message.raw(displayName));
            cmd.set(sel + ".TooltipTextSpans", buildTooltip(itemId, item, language));

            // Set pin button to filled star
            cmd.set(sel + " #PinButton #PinIcon.TextSpans", Message.raw("★"));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel, EventData.of("SelectedItem", itemId), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PinButton", EventData.of("PinToggle", itemId), false);

            col++;
            if (col >= ITEMS_PER_ROW) {
                col = 0;
                row++;
            }
            count++;
        }
    }

    private void buildRecipePanel(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        if (selectedItem == null || selectedItem.isEmpty()) {
            cmd.set("#RecipePanel.Visible", false);
            return;
        }

        cmd.set("#RecipePanel.Visible", true);

        Item item = JETPlugin.ITEMS.get(selectedItem);
        String language = playerRef.getLanguage();

        cmd.set("#RecipePanel #SelectedIcon.ItemId", selectedItem);
        cmd.set("#RecipePanel #SelectedName.TextSpans", Message.raw(getDisplayName(item, language)));

        List<String> craftRecipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> usageRecipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(selectedItem, Collections.emptyList());

        if ("craft".equals(activeSection)) {
            buildCraftSection(cmd, events, craftRecipeIds);
        } else {
            buildUsageSection(cmd, events, usageRecipeIds);
        }
    }

    private void buildCraftSection(UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipeIds.isEmpty()) {
            cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("No crafting recipes"));
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw(""));
            return;
        }

        int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);
        if (craftPage >= totalPages) craftPage = totalPages - 1;
        if (craftPage < 0) craftPage = 0;

        int start = craftPage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipeIds.size());

        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("Craft (" + recipeIds.size() + "):"));
        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((craftPage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String recipeId = recipeIds.get(i);
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            int idx = i - start;
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, recipe, rSel);
        }
    }

    private void buildUsageSection(UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipeIds.isEmpty()) {
            cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("No usage recipes"));
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw(""));
            return;
        }

        int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);
        if (usagePage >= totalPages) usagePage = totalPages - 1;
        if (usagePage < 0) usagePage = 0;

        int start = usagePage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipeIds.size());

        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("Usage (" + recipeIds.size() + "):"));
        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((usagePage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String recipeId = recipeIds.get(i);
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            int idx = i - start;
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, recipe, rSel);
        }
    }

    private void buildRecipeDisplay(UICommandBuilder cmd, CraftingRecipe recipe, String selector) {
        List<MaterialQuantity> inputs = getRecipeInputs(recipe);
        MaterialQuantity[] outputs = recipe.getOutputs();

        cmd.clear(selector + " #InputItems");
        for (int i = 0; i < inputs.size() && i < 9; i++) {
            MaterialQuantity input = inputs.get(i);
            cmd.appendInline(selector + " #InputItems", "ItemIcon { Anchor: (Width: 32, Height: 32); }");
            cmd.set(selector + " #InputItems[" + i + "].ItemId", input.getItemId());
            if (input.getQuantity() > 1) {
                cmd.set(selector + " #InputItems[" + i + "].TooltipTextSpans",
                    Message.raw(input.getItemId() + " x" + input.getQuantity()));
            }
        }

        cmd.clear(selector + " #OutputItems");
        for (int i = 0; i < outputs.length; i++) {
            MaterialQuantity output = outputs[i];
            cmd.appendInline(selector + " #OutputItems", "ItemIcon { Anchor: (Width: 48, Height: 48); }");
            cmd.set(selector + " #OutputItems[" + i + "].ItemId", output.getItemId());
            if (output.getQuantity() > 1) {
                cmd.set(selector + " #OutputItems[" + i + "].TooltipTextSpans",
                    Message.raw(output.getItemId() + " x" + output.getQuantity()));
            }
        }
    }

    private List<MaterialQuantity> getRecipeInputs(CraftingRecipe recipe) {
        List<MaterialQuantity> result = new ArrayList<>();

        Method getInputMethod = null;
        try {
            getInputMethod = CraftingRecipe.class.getMethod("getInput");
        } catch (NoSuchMethodException e) {
            // Try alternative methods
        }

        Object inputsObj = null;
        if (getInputMethod != null) {
            try {
                inputsObj = getInputMethod.invoke(recipe);
            } catch (Exception ignored) {}
        }

        if (inputsObj == null) {
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials"};
            for (String methodName : methodNames) {
                try {
                    Method fallbackMethod = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = fallbackMethod.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (Exception ignored) {}
            }
        }

        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                result.add((MaterialQuantity) inputsObj);
            } else if (inputsObj instanceof List) {
                List<?> inputs = (List<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        result.add((MaterialQuantity) obj);
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                result.addAll(Arrays.asList((MaterialQuantity[]) inputsObj));
            }
        }

        return result;
    }

    private String getDisplayName(Item item, String language) {
        if (item == null) return "Unknown";

        try {
            String translationKey = item.getTranslationKey();
            if (translationKey != null && !translationKey.isEmpty()) {
                return I18nModule.translateText(translationKey, language);
            }
        } catch (Exception ignored) {}

        try {
            Method getIdMethod = Item.class.getMethod("getId");
            Object idObj = getIdMethod.invoke(item);
            if (idObj != null) {
                String id = idObj.toString();
                if (id.contains(":")) {
                    return id.substring(id.indexOf(":") + 1).replace("_", " ");
                }
                return id.replace("_", " ");
            }
        } catch (Exception ignored) {}

        return "Unknown Item";
    }

    private Message buildTooltip(String itemId, Item item, String language) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append(getDisplayName(item, language)).append("\n");
        tooltip.append("ID: ").append(itemId);
        return Message.raw(tooltip.toString());
    }

    public static class GuiData {
        public static final BuilderCodec<GuiData> CODEC = BuilderCodec
                .builder(GuiData.class, GuiData::new)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .addField(new KeyedCodec<>("ActiveSection", Codec.STRING), (d, v) -> d.activeSection = v, d -> d.activeSection)
                .addField(new KeyedCodec<>("PageChange", Codec.STRING), (d, v) -> d.pageChange = v, d -> d.pageChange)
                .addField(new KeyedCodec<>("ToggleMode", Codec.STRING), (d, v) -> d.toggleMode = v, d -> d.toggleMode)
                .addField(new KeyedCodec<>("PinToggle", Codec.STRING), (d, v) -> d.pinToggle = v, d -> d.pinToggle)
                .build();

        private String selectedItem;
        private String activeSection;
        private String pageChange;
        private String toggleMode;
        private String pinToggle;

        public GuiData() {}
    }
}
