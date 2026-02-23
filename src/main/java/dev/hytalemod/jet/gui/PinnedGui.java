package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.Color;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.util.InventoryScanner;
import dev.hytalemod.jet.util.TooltipBuilder;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.lang.reflect.Method;
import java.util.*;

/**
 * GUI for viewing and managing pinned/favorited items
 */
public class PinnedGui extends InteractiveCustomUIPage<PinnedGui.GuiData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int MAX_ROWS = 8;
    private static final int MAX_ITEMS = ITEMS_PER_ROW * MAX_ROWS;
    private static final int RECIPES_PER_PAGE = 3;

    private String selectedItem;
    private String activeSection; // "craft", "usage", "drops", or "calc"
    private int craftPage;
    private int usagePage;
    private int dropsPage;
    private int calcQuantity = 1;
    private Set<String> calcCollapsedNodes = new HashSet<>();
    private Map<String, Integer> calcRecipeChoices = new HashMap<>();

    public PinnedGui(PlayerRef playerRef, CustomPageLifetime lifetime) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.selectedItem = null;
        this.activeSection = "craft";
        this.craftPage = 0;
        this.usagePage = 0;
        this.dropsPage = 0;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Gui.ui");

        // Theme/background tiling
        dev.hytalemod.jet.config.JETConfig jetConfig = JETPlugin.getInstance().getConfig();
        String bg = jetConfig.backgroundImage;
        if (bg != null && !bg.equals("none") && bg.startsWith("JET_Bg_")) {
            boolean exists = JETPlugin.ITEMS.containsKey(bg);
            if (exists) {
                cmd.set("#DefaultBg.Visible", false);
                final int tileSize = 64;
                final int cols = (int) Math.ceil(1400.0 / tileSize);
                final int rows = (int) Math.ceil(700.0 / tileSize);
                cmd.set("#BgContainer.Visible", true);
                for (int r = 0; r < rows; r++) {
                    StringBuilder row = new StringBuilder("Group { LayoutMode: Left; Anchor: (Height: " + tileSize + "); ");
                    for (int c = 0; c < cols; c++) {
                        row.append("ItemIcon { Anchor: (Width: ").append(tileSize).append(", Height: ").append(tileSize).append("); Visible: true; } ");
                    }
                    row.append("}");
                    cmd.appendInline("#BgContainer", row.toString());
                    for (int c = 0; c < cols; c++) {
                        cmd.set("#BgContainer[" + r + "][" + c + "].ItemId", bg);
                    }
                }
            }
        }

        // Hide search, filter, options, pagination, history, settings
        cmd.set("#SearchInput.Visible", false);
        cmd.set("#FilterSection.Visible", false);
        cmd.set("#OptionsBar.Visible", false);
        cmd.set("#ItemPagination.Visible", false);
        cmd.set("#HistoryBar.Visible", false);
        cmd.set("#Title #SettingsButton.Visible", false);

        // Set icon ItemIds for navigation buttons
        cmd.set("#RecipePagination #PrevRecipe #PrevRecipeIcon.ItemId", "JET_Icon_Arrow_Left");
        cmd.set("#RecipePagination #NextRecipe #NextRecipeIcon.ItemId", "JET_Icon_Arrow_Right");

        // Section buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ToggleModeButton", EventData.of("ToggleMode", "craft"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #UsesButton", EventData.of("ToggleMode", "usage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ObtainedFromButton", EventData.of("ToggleMode", "drops"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #CalcButton", EventData.of("ToggleMode", "calc"), false);

        // Pin button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #PinButton", EventData.of("PinAction", "toggle"), false);

        // Pagination
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipe", EventData.of("PageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipe", EventData.of("PageChange", "next"), false);

        buildPinnedItemsList(ref, cmd, events, store);
        buildRecipePanel(ref, cmd, events, store);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, GuiData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsItemUpdate = false;
        boolean needsRecipeUpdate = false;

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            // Check for calc tree/cycle events (reuse SelectedItem codec key with prefixes)
            String val = data.selectedItem;
            if (val.startsWith("tree:")) {
                String nodeKey = val.substring(5);
                if (calcCollapsedNodes.contains(nodeKey)) calcCollapsedNodes.remove(nodeKey);
                else calcCollapsedNodes.add(nodeKey);
                needsRecipeUpdate = true;
            } else if (val.startsWith("cycle:")) {
                String nodeKey = val.substring(6);
                int current = calcRecipeChoices.getOrDefault(nodeKey, 0);
                calcRecipeChoices.put(nodeKey, current + 1);
                needsRecipeUpdate = true;
            } else {
                boolean wasSelected = this.selectedItem != null;
                this.selectedItem = val;
                this.craftPage = 0;
                this.usagePage = 0;
                this.dropsPage = 0;
                this.activeSection = "craft";
                this.calcQuantity = 1;
                this.calcCollapsedNodes.clear();
                this.calcRecipeChoices.clear();
                needsRecipeUpdate = true;
                if (!wasSelected) needsItemUpdate = true;
            }
        }

        if (data.toggleMode != null && !data.toggleMode.isEmpty()) {
            if ("craft".equals(data.toggleMode) || "usage".equals(data.toggleMode) || "drops".equals(data.toggleMode) || "calc".equals(data.toggleMode)) {
                this.activeSection = data.toggleMode;
                this.craftPage = 0;
                this.usagePage = 0;
                this.dropsPage = 0;
                this.calcCollapsedNodes.clear();
                this.calcRecipeChoices.clear();
                needsRecipeUpdate = true;
            }
        }

        if (data.calcQuantityChange != null) {
            if ("inc".equals(data.calcQuantityChange)) { calcQuantity = Math.min(9999, calcQuantity + 1); needsRecipeUpdate = true; }
            else if ("inc10".equals(data.calcQuantityChange)) { calcQuantity = Math.min(9999, calcQuantity + 10); needsRecipeUpdate = true; }
            else if ("dec".equals(data.calcQuantityChange)) { calcQuantity = Math.max(1, calcQuantity - 1); needsRecipeUpdate = true; }
            else if ("dec10".equals(data.calcQuantityChange)) { calcQuantity = Math.max(1, calcQuantity - 10); needsRecipeUpdate = true; }
        }

        // Handle pin/unpin action
        if (data.pinAction != null && "toggle".equals(data.pinAction) && this.selectedItem != null) {
            UUID playerUuid = playerRef.getUuid();
            Set<String> pinnedItems = JETPlugin.getInstance().getPinnedItemsStorage().getPinnedItems(playerUuid);

            // If unpinning the last item, close the menu
            if (pinnedItems.size() == 1 && pinnedItems.contains(this.selectedItem)) {
                JETPlugin.getInstance().getPinnedItemsStorage().togglePin(playerUuid, this.selectedItem);
                close();
                return;
            }

            boolean isPinned = JETPlugin.getInstance().getPinnedItemsStorage().togglePin(playerUuid, this.selectedItem);
            needsRecipeUpdate = true;
            if (!isPinned) {
                needsItemUpdate = true;
            }
        }

        if (data.pageChange != null) {
            int totalPages;
            if ("craft".equals(this.activeSection)) {
                List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                totalPages = Math.max(1, (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE));
                if ("prev".equals(data.pageChange) && craftPage > 0) { craftPage--; needsRecipeUpdate = true; }
                else if ("next".equals(data.pageChange) && craftPage < totalPages - 1) { craftPage++; needsRecipeUpdate = true; }
            } else if ("usage".equals(this.activeSection)) {
                List<String> recipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                totalPages = Math.max(1, (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE));
                if ("prev".equals(data.pageChange) && usagePage > 0) { usagePage--; needsRecipeUpdate = true; }
                else if ("next".equals(data.pageChange) && usagePage < totalPages - 1) { usagePage++; needsRecipeUpdate = true; }
            } else {
                List<String> dropSources = JETPlugin.getInstance().getDropListRegistry().getDropSourcesForItem(this.selectedItem);
                totalPages = Math.max(1, (int) Math.ceil((double) dropSources.size() / RECIPES_PER_PAGE));
                if ("prev".equals(data.pageChange) && dropsPage > 0) { dropsPage--; needsRecipeUpdate = true; }
                else if ("next".equals(data.pageChange) && dropsPage < totalPages - 1) { dropsPage++; needsRecipeUpdate = true; }
            }
        }

        if (needsItemUpdate || needsRecipeUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            if (needsItemUpdate) {
                buildPinnedItemsList(ref, cmd, events, store);
            }
            if (needsRecipeUpdate) {
                buildRecipePanel(ref, cmd, events, store);
            }

            sendUpdate(cmd, events, false);
        }
    }

    private void buildPinnedItemsList(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.clear("#ItemCards");

        // Adjust panel widths based on selection
        if (selectedItem != null) {
            Anchor narrowItem = new Anchor();
            narrowItem.setWidth(Value.of(880));
            cmd.setObject("#ItemSection.Anchor", narrowItem);
        } else {
            Anchor fullItem = new Anchor();
            fullItem.setWidth(Value.of(1360));
            cmd.setObject("#ItemSection.Anchor", fullItem);
        }

        UUID playerUuid = playerRef.getUuid();
        Set<String> pinnedItemIds = JETPlugin.getInstance().getPinnedItemsStorage().getPinnedItems(playerUuid);

        if (pinnedItemIds.isEmpty()) {
            cmd.appendInline("#ItemCards", "Group { LayoutMode: Top; Padding: (Full: 30); }");

            cmd.appendInline("#ItemCards[0]",
                    "Group { LayoutMode: Middle; Padding: (Top: 60); Anchor: (Height: 64); }");
            cmd.appendInline("#ItemCards[0][0]",
                    "ItemIcon { Anchor: (Width: 48, Height: 48); Visible: true; }");
            cmd.set("#ItemCards[0][0][0].ItemId", "JET_Icon_Star");

            cmd.appendInline("#ItemCards[0]",
                    "Label { Style: (FontSize: 18, TextColor: #ffffff, HorizontalAlignment: Center, RenderBold: true); Padding: (Top: 20); }");
            cmd.set("#ItemCards[0][1].Text", "No Pinned Items Yet");

            cmd.appendInline("#ItemCards[0]",
                    "Label { Style: (FontSize: 13, TextColor: #aaaaaa, HorizontalAlignment: Center); Padding: (Top: 10); }");
            cmd.set("#ItemCards[0][2].Text", "Browse items with /jet and click the Pin button");

            cmd.appendInline("#ItemCards[0]",
                    "Label { Style: (FontSize: 11, TextColor: #777777, HorizontalAlignment: Center); Padding: (Top: 8); }");
            cmd.set("#ItemCards[0][3].Text", "to save your favorites here!");

            return;
        }

        String language = playerRef.getLanguage();
        List<String> sortedItems = new ArrayList<>(pinnedItemIds);

        sortedItems.sort((a, b) -> {
            Item itemA = JETPlugin.ITEMS.get(a);
            Item itemB = JETPlugin.ITEMS.get(b);
            String nameA = getDisplayName(itemA, language);
            String nameB = getDisplayName(itemB, language);
            return nameA.compareToIgnoreCase(nameB);
        });

        int row = 0;
        int col = 0;
        int count = 0;

        for (String itemId : sortedItems) {
            if (count >= MAX_ITEMS) break;

            Item item = JETPlugin.ITEMS.get(itemId);
            if (item == null) continue;

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
            cmd.set(sel + " #ItemName.TextSpans", getColoredItemName(item, displayName));
            cmd.set(sel + ".TooltipTextSpans", buildTooltip(itemId, item, language));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #ItemButton", EventData.of("SelectedItem", itemId), false);

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
            Anchor hideRecipe = new Anchor();
            hideRecipe.setWidth(Value.of(0));
            cmd.setObject("#RecipePanel.Anchor", hideRecipe);
            Anchor fullItem = new Anchor();
            fullItem.setWidth(Value.of(1360));
            cmd.setObject("#ItemSection.Anchor", fullItem);
            return;
        }

        cmd.set("#RecipePanel.Visible", true);
        Anchor showRecipe = new Anchor();
        showRecipe.setWidth(Value.of(480));
        cmd.setObject("#RecipePanel.Anchor", showRecipe);
        Anchor narrowItem = new Anchor();
        narrowItem.setWidth(Value.of(880));
        cmd.setObject("#ItemSection.Anchor", narrowItem);

        Item item = JETPlugin.ITEMS.get(selectedItem);
        String language = playerRef.getLanguage();

        cmd.set("#RecipePanel #SelectedIcon.ItemId", selectedItem);
        String displayName = getDisplayName(item, language);
        cmd.set("#RecipePanel #SelectedName.TextSpans", getColoredItemName(item, displayName));
        cmd.set("#RecipePanel #ItemId.Text", selectedItem);

        // Hide sections not applicable in pinned view
        cmd.set("#RecipePanel #SelectedItemBar #ItemHeader #GiveButtonGroup.Visible", false);
        cmd.set("#RecipePanel #ItemStatsSection.Visible", false);
        cmd.set("#RecipePanel #AdvancedInfoSection.Visible", false);
        cmd.set("#RecipePanel #ItemSetSection.Visible", false);
        // Hide HUD pin button (not relevant in pinned view)
        cmd.set("#RecipePanel #PinToHudButton.Visible", false);

        // Get recipe/drop counts
        List<String> craftRecipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> usageRecipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> dropSources = JETPlugin.getInstance().getDropListRegistry().getDropSourcesForItem(selectedItem);

        String recipeInfo = "Craft: " + craftRecipeIds.size() + " | Uses: " + usageRecipeIds.size() + " | Drops: " + dropSources.size();
        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw(recipeInfo));

        // Pin button icon
        UUID playerUuid = playerRef.getUuid();
        boolean isPinned = JETPlugin.getInstance().getPinnedItemsStorage().isPinned(playerUuid, selectedItem);
        cmd.set("#RecipePanel #PinButton #PinIcon.ItemId", isPinned ? "JET_Icon_Unpin" : "JET_Icon_Pin");

        // Re-bind section buttons (cleared by sendUpdate)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ToggleModeButton", EventData.of("ToggleMode", "craft"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #UsesButton", EventData.of("ToggleMode", "usage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ObtainedFromButton", EventData.of("ToggleMode", "drops"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #CalcButton", EventData.of("ToggleMode", "calc"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #PinButton", EventData.of("PinAction", "toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipe", EventData.of("PageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipe", EventData.of("PageChange", "next"), false);

        boolean noSource = craftRecipeIds.isEmpty() && usageRecipeIds.isEmpty() && dropSources.isEmpty();

        if ("craft".equals(activeSection)) {
            buildCraftSection(ref, cmd, events, craftRecipeIds, noSource);
        } else if ("usage".equals(activeSection)) {
            buildUsageSection(ref, cmd, events, usageRecipeIds, noSource);
        } else if ("calc".equals(activeSection)) {
            buildCalcSection(cmd, events, craftRecipeIds, noSource);
        } else {
            buildDropsSection(ref, cmd, events, dropSources, noSource);
        }
    }

    private void buildCraftSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds, boolean noSource) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipeIds.isEmpty()) {
            appendEmptyMessage(cmd, noSource ? "Uncraftable \u2014 no source found" : "No crafting recipe", noSource);
            return;
        }

        int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);
        if (craftPage >= totalPages) craftPage = totalPages - 1;
        if (craftPage < 0) craftPage = 0;

        int start = craftPage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipeIds.size());

        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((craftPage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String recipeId = recipeIds.get(i);
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            int idx = i - start;
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, events, recipe, rSel, ref);
        }
    }

    private void buildUsageSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds, boolean noSource) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipeIds.isEmpty()) {
            appendEmptyMessage(cmd, noSource ? "Uncraftable \u2014 no source found" : "Not used in any recipe", noSource);
            return;
        }

        int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);
        if (usagePage >= totalPages) usagePage = totalPages - 1;
        if (usagePage < 0) usagePage = 0;

        int start = usagePage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipeIds.size());

        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((usagePage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String recipeId = recipeIds.get(i);
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            int idx = i - start;
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, events, recipe, rSel, ref);
        }
    }

    private void buildDropsSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> dropListIds, boolean noSource) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (dropListIds.isEmpty()) {
            appendEmptyMessage(cmd, noSource ? "Uncraftable \u2014 no source found" : "Not dropped by any mob", noSource);
            return;
        }

        int totalPages = (int) Math.ceil((double) dropListIds.size() / RECIPES_PER_PAGE);
        if (dropsPage >= totalPages) dropsPage = totalPages - 1;
        if (dropsPage < 0) dropsPage = 0;

        int start = dropsPage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, dropListIds.size());

        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((dropsPage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String dropListId = dropListIds.get(i);
            int idx = i - start;

            String displayName = formatDropListName(dropListId);
            String dropType = getDropType(dropListId);

            cmd.appendInline("#RecipePanel #RecipeListContainer #RecipeList",
                    "Group { LayoutMode: Top; Padding: (Full: 10, Bottom: 12); Background: (Color: #1e1e1e(0.9)); Anchor: (Bottom: 8); " +
                    "Label #DropTitle { Style: (FontSize: 13, RenderBold: true, TextColor: #ffcc66); } " +
                    "Label #DropType { Style: (FontSize: 11, TextColor: #888888); Padding: (Top: 4); } }");

            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";
            cmd.set(rSel + " #DropTitle.Text", displayName);
            cmd.set(rSel + " #DropType.Text", dropType);
        }
    }

    private void appendEmptyMessage(UICommandBuilder cmd, String message, boolean highlight) {
        String color = highlight ? "#ff9966" : "#888888";
        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw(""));
        cmd.appendInline("#RecipePanel #RecipeListContainer #RecipeList",
                "Group { Padding: (Top: 24, Bottom: 8); " +
                "Label { Style: (FontSize: 12, TextColor: " + color + ", HorizontalAlignment: Center); } }");
        cmd.set("#RecipePanel #RecipeListContainer #RecipeList[0][0].Text", message);
    }

    private void buildRecipeDisplay(UICommandBuilder cmd, UIEventBuilder events, CraftingRecipe recipe, String rSel, Ref<EntityStore> ref) {
        // Resolve recipe title from primary output item name
        String recipeTitle = null;
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs != null && outputs.length > 0 && outputs[0] != null && outputs[0].getItemId() != null) {
            Item outputItem = JETPlugin.ITEMS.get(outputs[0].getItemId());
            if (outputItem != null) {
                recipeTitle = getDisplayName(outputItem, playerRef.getLanguage());
            }
        }
        if (recipeTitle == null) {
            recipeTitle = recipe.getId();
            if (recipeTitle.contains(":")) recipeTitle = recipeTitle.substring(recipeTitle.indexOf(":") + 1);
            recipeTitle = recipeTitle.replace("_", " ");
        }

        String benchInfo = "";
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            benchInfo = " [" + formatBenchName(bench.id) + " T" + bench.requiredTierLevel + "]";
        }

        cmd.set(rSel + " #RecipeTitle.TextSpans", Message.raw(recipeTitle + benchInfo));

        // Get player for inventory scanning
        Player player = null;
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                player = store.getComponent(ref, Player.getComponentType());
            }
        }

        // Add input items with inventory counts
        List<MaterialQuantity> inputs = getRecipeInputs(recipe);
        cmd.clear(rSel + " #InputItems");
        for (int j = 0; j < inputs.size(); j++) {
            MaterialQuantity input = inputs.get(j);
            String itemId = input.getItemId();
            String resourceTypeId = input.getResourceTypeId();
            int requiredQty = input.getQuantity();

            if (itemId != null) {
                cmd.appendInline(rSel + " #InputItems",
                        "Button { LayoutMode: Top; Padding: (Right: 6); Background: (Color: #00000000); Style: (Hovered: (Background: #ffffff30), Pressed: (Background: #ffffff50)); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                cmd.set(rSel + " #InputItems[" + j + "][0].ItemId", itemId);

                // Click to navigate to ingredient
                events.addEventBinding(CustomUIEventBindingType.Activating, rSel + " #InputItems[" + j + "]", EventData.of("SelectedItem", itemId), false);

                if (player != null) {
                    int inventoryCount = InventoryScanner.countItemInInventory(player, itemId);
                    String color = inventoryCount >= requiredQty ? "#00ff00" : "#ff0000";
                    cmd.set(rSel + " #InputItems[" + j + "][1].Text", inventoryCount + "/" + requiredQty);
                    cmd.set(rSel + " #InputItems[" + j + "][1].Style.TextColor", color);
                } else {
                    cmd.set(rSel + " #InputItems[" + j + "][1].Text", "x" + requiredQty);
                }
            } else if (resourceTypeId != null) {
                try {
                    ResourceType resourceType = ResourceType.getAssetMap().getAsset(resourceTypeId);
                    if (resourceType != null) {
                        cmd.appendInline(rSel + " #InputItems",
                                "Group { LayoutMode: Top; Padding: (Right: 6); AssetImage { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                        cmd.set(rSel + " #InputItems[" + j + "][0].AssetPath", resourceType.getIcon());

                        if (player != null) {
                            int inventoryCount = InventoryScanner.countResourceTypeInInventory(player, resourceTypeId);
                            String color = inventoryCount >= requiredQty ? "#00ff00" : "#ff0000";
                            cmd.set(rSel + " #InputItems[" + j + "][1].Text", inventoryCount + "/" + requiredQty);
                            cmd.set(rSel + " #InputItems[" + j + "][1].Style.TextColor", color);
                        } else {
                            cmd.set(rSel + " #InputItems[" + j + "][1].Text", "x" + requiredQty);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Add output items
        cmd.clear(rSel + " #OutputItems");
        if (outputs != null && outputs.length > 0) {
            for (int j = 0; j < outputs.length; j++) {
                MaterialQuantity output = outputs[j];
                if (output != null && output.getItemId() != null) {
                    String outputItemId = output.getItemId();
                    cmd.appendInline(rSel + " #OutputItems",
                            "Button { LayoutMode: Top; Padding: (Right: 6); Background: (Color: #00000000); Style: (Hovered: (Background: #ffffff30), Pressed: (Background: #ffffff50)); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                    cmd.set(rSel + " #OutputItems[" + j + "][0].ItemId", outputItemId);
                    cmd.set(rSel + " #OutputItems[" + j + "][1].Text", "x" + output.getQuantity());

                    events.addEventBinding(CustomUIEventBindingType.Activating, rSel + " #OutputItems[" + j + "]", EventData.of("SelectedItem", outputItemId), false);
                }
            }
        }
    }

    private String formatBenchName(String benchId) {
        if (benchId == null) return "";
        if (benchId.contains(":")) benchId = benchId.substring(benchId.indexOf(":") + 1);
        if (benchId.startsWith("hytale_")) benchId = benchId.substring(7);
        String[] parts = benchId.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    // --- Calc Section ---

    private static final int RAW_MAT_PER_ROW = 7;

    private void buildCalcSection(UICommandBuilder cmd, UIEventBuilder events, List<String> craftRecipeIds, boolean noSource) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");
        cmd.set("#RecipePagination #PrevRecipe.Visible", false);
        cmd.set("#RecipePagination #NextRecipe.Visible", false);

        if (craftRecipeIds.isEmpty()) {
            appendEmptyMessage(cmd, noSource ? "Uncraftable \u2014 no source found" : "No crafting recipe", noSource);
            return;
        }

        // Quantity controls [0]
        cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_CalcControls.ui");
        cmd.set("#CalcControls #CalcQtyLabel.Text", String.valueOf(calcQuantity));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcMinus10", EventData.of("CalcQuantityChange", "dec10"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcMinus", EventData.of("CalcQuantityChange", "dec"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcPlus", EventData.of("CalcQuantityChange", "inc"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcPlus10", EventData.of("CalcQuantityChange", "inc10"), false);

        String listSel = "#RecipePanel #RecipeListContainer #RecipeList";
        String language = playerRef.getLanguage();
        String[] depthColors = {"#88ccff", "#88ff88", "#ffcc66", "#ff8888", "#cc88ff"};

        List<TreeNode> tree = buildCraftingTree(selectedItem, calcQuantity);

        long intermediateCount = tree.stream().filter(n -> n.isCraftable).count();
        long rawCount = tree.stream().filter(n -> !n.isCraftable).count();
        cmd.set("#RecipePanel #PageInfo.TextSpans",
                Message.join(new Message[]{
                    Message.raw(rawCount + " raw").color("#aaaaaa"),
                    Message.raw("  "),
                    Message.raw(intermediateCount + " craftable").color("#55aaff")
                }));

        if (tree.isEmpty()) return;

        int appendIdx = 1;

        for (int i = 0; i < tree.size(); i++) {
            TreeNode node = tree.get(i);
            String depthColor = depthColors[Math.min(node.depth, depthColors.length - 1)];
            int indentPx = node.depth * 16;

            String displayName;
            if (node.isResourceType) {
                displayName = node.itemId.replace("_", " ");
            } else {
                Item item = JETPlugin.ITEMS.get(node.itemId);
                displayName = item != null ? getDisplayName(item, language) : node.itemId.replace("_", " ");
            }

            String qtyStr = "x" + (node.quantity >= 1000 ? String.format("%,d", node.quantity) : String.valueOf(node.quantity));

            String chevron = "";
            if (node.isCraftable) {
                chevron = node.isExpanded ? "v " : "> ";
            }

            String nameColor = node.isCraftable ? "#88ccff" : "#cccccc";

            StringBuilder rowBuilder = new StringBuilder();
            if (node.isCraftable) {
                rowBuilder.append("Button #TreeRow").append(i).append(" { LayoutMode: Left; Padding: (Bottom: 2); Background: (Color: #00000000); Style: (Hovered: (Background: #ffffff15), Pressed: (Background: #ffffff25)); ");
            } else {
                rowBuilder.append("Group #TreeRow").append(i).append(" { LayoutMode: Left; Padding: (Bottom: 2); ");
            }
            if (indentPx > 0) {
                rowBuilder.append("Group { Anchor: (Width: ").append(indentPx).append("); } ");
            }
            rowBuilder.append("Group { Anchor: (Width: 3, Height: 26); Background: (Color: ").append(depthColor).append("); } ");
            if (!node.isResourceType) {
                rowBuilder.append("ItemIcon { Anchor: (Width: 26, Height: 26); Visible: true; } ");
            }
            rowBuilder.append("Label { Style: (FontSize: 10, TextColor: ").append(nameColor).append("); } ");
            rowBuilder.append("Label { Style: (FontSize: 10, TextColor: #ffcc66); } ");
            rowBuilder.append("}");

            cmd.appendInline(listSel, rowBuilder.toString());
            String rowSel = listSel + "[" + appendIdx + "]";

            int childIdx = 0;
            if (indentPx > 0) childIdx++;
            childIdx++; // color bar

            if (!node.isResourceType) {
                cmd.set(rowSel + "[" + childIdx + "].ItemId", node.itemId);
                childIdx++;
            }

            String nameText = chevron + displayName;
            if (node.isCraftable && node.recipeCount > 1) {
                nameText += " [" + (node.recipeIndex + 1) + "/" + node.recipeCount + "]";
            }
            cmd.set(rowSel + "[" + childIdx + "].Text", nameText);
            childIdx++;

            cmd.set(rowSel + "[" + childIdx + "].Text", qtyStr);

            if (node.isCraftable) {
                String action = node.recipeCount > 1 ? "cycle:" : "tree:";
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        rowSel,
                        EventData.of("SelectedItem", action + node.itemId), false);
            }

            cmd.set(rowSel + ".TooltipTextSpans", Message.raw(displayName + "  " + qtyStr));
            appendIdx++;
        }

        // Separator
        cmd.appendInline(listSel, "Group { Anchor: (Height: 2); Background: (Color: #555555); }");
        appendIdx++;

        // Raw Materials Summary
        Map<String, Long> rawMaterials = new LinkedHashMap<>();
        resolveIngredients(selectedItem, (long) calcQuantity, rawMaterials, new HashSet<>());
        cmd.appendInline(listSel,
                "Group { Padding: (Bottom: 6); Label { Style: (FontSize: 10, TextColor: #aaaaaa, HorizontalAlignment: Center); } }");
        cmd.set(listSel + "[" + appendIdx + "][0].Text", "Raw Materials");
        appendIdx++;

        if (!rawMaterials.isEmpty()) {
            int matItemIndex = 0;

            for (Map.Entry<String, Long> entry : rawMaterials.entrySet()) {
                String matId = entry.getKey();
                long qty = entry.getValue();

                boolean isResourceType = matId.startsWith("resource:");
                String displayId = isResourceType ? matId.substring(9) : matId;

                String displayName;
                if (isResourceType) {
                    displayName = displayId.replace("_", " ");
                } else {
                    Item matItem = JETPlugin.ITEMS.get(displayId);
                    displayName = matItem != null ? getDisplayName(matItem, language) : displayId.replace("_", " ");
                }

                String qtyStr = "x" + (qty >= 1000 ? String.format("%,d", qty) : String.valueOf(qty));
                String shortName = displayName.length() > 8 ? displayName.substring(0, 7) + ".." : displayName;

                int col = matItemIndex % RAW_MAT_PER_ROW;
                if (col == 0) {
                    cmd.appendInline(listSel,
                            "Group { LayoutMode: Left; Padding: (Bottom: 2); }");
                    appendIdx++;
                }

                String matRowSel = listSel + "[" + (appendIdx - 1) + "]";

                cmd.appendInline(matRowSel,
                        "Group { LayoutMode: Top; Padding: (Right: 4, Bottom: 4); Anchor: (Width: 48); " +
                        "ItemIcon { Anchor: (Width: 36, Height: 36); Visible: true; } " +
                        "Label { Style: (FontSize: 8, TextColor: #cccccc, HorizontalAlignment: Center); } " +
                        "Label { Style: (FontSize: 9, TextColor: #ffcc66, HorizontalAlignment: Center); } }");

                if (!isResourceType) {
                    cmd.set(matRowSel + "[" + col + "][0].ItemId", displayId);
                }

                cmd.set(matRowSel + "[" + col + "][1].Text", shortName);
                cmd.set(matRowSel + "[" + col + "][2].Text", qtyStr);
                cmd.set(matRowSel + "[" + col + "].TooltipTextSpans", Message.raw(displayName + "  " + qtyStr));

                matItemIndex++;
            }
        }
    }

    private boolean isSalvagerRecipe(CraftingRecipe recipe) {
        if (recipe == null) return false;
        try {
            BenchRequirement[] benchRequirements = recipe.getBenchRequirement();
            if (benchRequirements != null) {
                for (BenchRequirement bench : benchRequirements) {
                    if (bench != null && bench.id != null && bench.id.equals("Salvagebench")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private CraftingRecipe getNonSalvagerRecipe(String itemId, List<String> recipeIds) {
        List<String> filtered = new ArrayList<>();
        for (String rid : recipeIds) {
            CraftingRecipe r = JETPlugin.RECIPES.get(rid);
            if (r != null && !isSalvagerRecipe(r)) {
                filtered.add(rid);
            }
        }
        if (filtered.isEmpty()) return null;
        int choiceIdx = calcRecipeChoices.getOrDefault(itemId, 0);
        if (choiceIdx >= filtered.size()) choiceIdx = 0;
        return JETPlugin.RECIPES.get(filtered.get(choiceIdx));
    }

    private int getNonSalvagerRecipeCount(List<String> recipeIds) {
        int count = 0;
        for (String rid : recipeIds) {
            CraftingRecipe r = JETPlugin.RECIPES.get(rid);
            if (r != null && !isSalvagerRecipe(r)) count++;
        }
        return count;
    }

    private static class TreeNode {
        String itemId;
        long quantity;
        int depth;
        boolean isCraftable;
        boolean isExpanded;
        boolean isResourceType;
        int recipeIndex;
        int recipeCount;
    }

    private List<TreeNode> buildCraftingTree(String rootItemId, int quantity) {
        List<TreeNode> result = new ArrayList<>();
        List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(rootItemId, Collections.emptyList());
        if (recipeIds.isEmpty()) return result;

        CraftingRecipe recipe = getNonSalvagerRecipe(rootItemId, recipeIds);
        if (recipe == null) return result;

        int outputQty = 1;
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs != null) {
            for (MaterialQuantity output : outputs) {
                if (output != null && rootItemId.equals(output.getItemId())) {
                    outputQty = Math.max(1, output.getQuantity());
                    break;
                }
            }
        }

        long craftsNeeded = ((long) quantity + outputQty - 1) / outputQty;

        Set<String> visited = new HashSet<>();
        visited.add(rootItemId);

        for (MaterialQuantity input : getRecipeInputs(recipe)) {
            addTreeNodes(result, input, craftsNeeded, 0, visited);
        }
        return result;
    }

    private void addTreeNodes(List<TreeNode> result, MaterialQuantity input, long parentCrafts, int depth, Set<String> visited) {
        if (depth > 10) return;

        String itemId = input.getItemId();
        boolean isResource = (itemId == null);

        if (isResource) {
            TreeNode node = new TreeNode();
            node.itemId = input.getResourceTypeId();
            node.quantity = (long) input.getQuantity() * parentCrafts;
            node.depth = depth;
            node.isCraftable = false;
            node.isResourceType = true;
            node.recipeIndex = 0;
            node.recipeCount = 0;
            result.add(node);
            return;
        }

        long needed = (long) input.getQuantity() * parentCrafts;
        List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());
        boolean circular = visited.contains(itemId);

        CraftingRecipe subRecipe = (!circular && !recipeIds.isEmpty()) ? getNonSalvagerRecipe(itemId, recipeIds) : null;
        boolean craftable = subRecipe != null;
        int nonSalvagerCount = craftable ? getNonSalvagerRecipeCount(recipeIds) : 0;
        int choiceIdx = calcRecipeChoices.getOrDefault(itemId, 0);
        if (choiceIdx >= nonSalvagerCount) choiceIdx = 0;

        TreeNode node = new TreeNode();
        node.itemId = itemId;
        node.quantity = needed;
        node.depth = depth;
        node.isCraftable = craftable;
        node.isExpanded = craftable && !calcCollapsedNodes.contains(itemId);
        node.isResourceType = false;
        node.recipeIndex = choiceIdx;
        node.recipeCount = nonSalvagerCount;
        result.add(node);

        if (craftable && node.isExpanded) {
            int subOutputQty = 1;
            MaterialQuantity[] outputs = subRecipe.getOutputs();
            if (outputs != null) {
                for (MaterialQuantity output : outputs) {
                    if (output != null && itemId.equals(output.getItemId())) {
                        subOutputQty = Math.max(1, output.getQuantity());
                        break;
                    }
                }
            }
            long subCrafts = (needed + subOutputQty - 1) / subOutputQty;

            visited.add(itemId);
            for (MaterialQuantity subInput : getRecipeInputs(subRecipe)) {
                addTreeNodes(result, subInput, subCrafts, depth + 1, visited);
            }
            visited.remove(itemId);
        }
    }

    private void resolveIngredients(String itemId, long needed, Map<String, Long> materials, Set<String> visited) {
        if (visited.contains(itemId)) {
            materials.merge(itemId, needed, Long::sum);
            return;
        }

        List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());
        if (recipeIds.isEmpty()) {
            materials.merge(itemId, needed, Long::sum);
            return;
        }

        CraftingRecipe recipe = getNonSalvagerRecipe(itemId, recipeIds);
        if (recipe == null) {
            materials.merge(itemId, needed, Long::sum);
            return;
        }

        long outputQty = 1;
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs != null) {
            for (MaterialQuantity output : outputs) {
                if (output != null && itemId.equals(output.getItemId())) {
                    outputQty = Math.max(1, output.getQuantity());
                    break;
                }
            }
        }

        long craftsNeeded = (needed + outputQty - 1) / outputQty;

        visited.add(itemId);
        List<MaterialQuantity> inputs = getRecipeInputs(recipe);
        for (MaterialQuantity input : inputs) {
            if (input.getItemId() != null) {
                resolveIngredients(input.getItemId(), (long) input.getQuantity() * craftsNeeded, materials, visited);
            } else if (input.getResourceTypeId() != null) {
                String key = "resource:" + input.getResourceTypeId();
                materials.merge(key, (long) input.getQuantity() * craftsNeeded, Long::sum);
            }
        }
        visited.remove(itemId);
    }

    private String formatDropListName(String dropListId) {
        if (dropListId == null) return "Unknown";
        if (dropListId.contains(":")) dropListId = dropListId.substring(dropListId.indexOf(":") + 1);
        return dropListId.replace("_", " ");
    }

    private String getDropType(String dropListId) {
        if (dropListId.contains("Ore_")) return "Mining";
        if (dropListId.contains("Chest")) return "Chest Loot";
        if (dropListId.contains("Plant_") || dropListId.contains("Crop_")) return "Farming";
        return "Drop";
    }

    private List<MaterialQuantity> getRecipeInputs(CraftingRecipe recipe) {
        List<MaterialQuantity> result = new ArrayList<>();
        try {
            Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            Object inputsObj = getInputMethod.invoke(recipe);
            if (inputsObj instanceof List) {
                for (Object obj : (List<?>) inputsObj) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity mq = (MaterialQuantity) obj;
                        if (mq.getItemId() != null || mq.getResourceTypeId() != null) {
                            result.add(mq);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                for (MaterialQuantity mq : (MaterialQuantity[]) inputsObj) {
                    if (mq != null && (mq.getItemId() != null || mq.getResourceTypeId() != null)) {
                        result.add(mq);
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity mq = (MaterialQuantity) inputsObj;
                if (mq.getItemId() != null || mq.getResourceTypeId() != null) {
                    result.add(mq);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String getDisplayName(Item item, String language) {
        if (item == null) return "Unknown";
        try {
            String key = item.getTranslationKey();
            if (key != null) {
                String translated = I18nModule.get().getMessage(language, key);
                if (translated != null && !translated.isEmpty()) {
                    return translated;
                }
            }
        } catch (Exception ignored) {}

        String id = item.getId();
        if (id == null) return "Unknown";
        if (id.contains(":")) id = id.substring(id.indexOf(":") + 1);
        int underscore = id.indexOf("_");
        if (underscore > 0) id = id.substring(underscore + 1);
        return id.replace("_", " ");
    }

    private Message getColoredItemName(Item item, String displayName) {
        if (item == null) return Message.raw(displayName);
        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String qualityName = quality.getId();
                String color = resolveQualityColor(qualityName);
                return Message.raw(displayName).color(color);
            }
        } catch (Exception ignored) {}
        return Message.raw(displayName);
    }

    private String resolveQualityColor(String qualityName) {
        if (qualityName == null) return "#ffffff";
        String lower = qualityName.toLowerCase();
        if (lower.contains("common")) return "#aaaaaa";
        if (lower.contains("uncommon")) return "#55cc55";
        if (lower.contains("rare")) return "#5599ff";
        if (lower.contains("epic")) return "#bb66ff";
        if (lower.contains("legendary")) return "#ffaa00";
        if (lower.contains("relic")) return "#ff5555";
        if (lower.contains("unique")) return "#ff66aa";
        return "#ffffff";
    }

    private Message buildTooltip(String itemId, Item item, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(getDisplayName(item, language)).append("\n");

        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                sb.append(quality.getId()).append("\n");
            }
        } catch (Exception ignored) {}

        sb.append("ID: ").append(itemId).append("\n");
        sb.append("Stack: ").append(item.getMaxStack());

        if (item.getMaxDurability() > 0) {
            sb.append("\nDurability: ").append((int) item.getMaxDurability());
        }

        // Show recipe/usage counts
        int craftCount = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList()).size();
        int usageCount = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(itemId, Collections.emptyList()).size();
        sb.append("\nCraft: ").append(craftCount).append(" | Uses: ").append(usageCount);

        return Message.raw(sb.toString());
    }

    public static class GuiData {
        public static final BuilderCodec<GuiData> CODEC = BuilderCodec
                .builder(GuiData.class, GuiData::new)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .addField(new KeyedCodec<>("PageChange", Codec.STRING), (d, v) -> d.pageChange = v, d -> d.pageChange)
                .addField(new KeyedCodec<>("ToggleMode", Codec.STRING), (d, v) -> d.toggleMode = v, d -> d.toggleMode)
                .addField(new KeyedCodec<>("PinAction", Codec.STRING), (d, v) -> d.pinAction = v, d -> d.pinAction)
                .addField(new KeyedCodec<>("CalcQuantityChange", Codec.STRING), (d, v) -> d.calcQuantityChange = v, d -> d.calcQuantityChange)
                .build();

        private String selectedItem;
        private String pageChange;
        private String toggleMode;
        private String pinAction;
        private String calcQuantityChange;

        public GuiData() {}
    }
}
