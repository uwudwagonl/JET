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
 * JET Browser GUI with item browsing, recipes, and uses.
 */
public class JETGui extends InteractiveCustomUIPage<JETGui.GuiData> {

    private static final int ITEMS_PER_ROW = 7;
    private static final int MAX_ROWS = 8;
    private static final int MAX_ITEMS = ITEMS_PER_ROW * MAX_ROWS;
    private static final int RECIPES_PER_PAGE = 3;

    private String searchQuery;
    private String selectedItem;
    private String activeSection; // "craft" or "usage"
    private int craftPage;
    private int usagePage;

    public JETGui(PlayerRef playerRef, CustomPageLifetime lifetime, String initialSearch) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = initialSearch != null ? initialSearch : "";
        this.selectedItem = null;
        this.activeSection = "craft";
        this.craftPage = 0;
        this.usagePage = 0;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Gui.ui");
        cmd.set("#SearchInput.Value", searchQuery);

        // Search input event
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

        // Section toggle buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #CraftButton", EventData.of("ActiveSection", "craft"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #UsageButton", EventData.of("ActiveSection", "usage"), false);

        // Pagination
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipe", EventData.of("CraftPageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipe", EventData.of("CraftPageChange", "next"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevUsage", EventData.of("UsagePageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextUsage", EventData.of("UsagePageChange", "next"), false);

        buildItemList(ref, cmd, events, store);
        buildRecipePanel(ref, cmd, events, store);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, GuiData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsItemUpdate = false;
        boolean needsRecipeUpdate = false;

        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            needsItemUpdate = true;
        }

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.selectedItem = data.selectedItem;
            this.craftPage = 0;
            this.usagePage = 0;
            this.activeSection = "craft";
            needsRecipeUpdate = true;
        }

        if (data.activeSection != null && !data.activeSection.isEmpty() && !data.activeSection.equals(this.activeSection)) {
            this.activeSection = data.activeSection;
            this.craftPage = 0;
            this.usagePage = 0;
            needsRecipeUpdate = true;
        }

        if (data.craftPageChange != null && "craft".equals(this.activeSection)) {
            List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);

            if ("prev".equals(data.craftPageChange) && craftPage > 0) {
                craftPage--;
                needsRecipeUpdate = true;
            } else if ("next".equals(data.craftPageChange) && craftPage < totalPages - 1) {
                craftPage++;
                needsRecipeUpdate = true;
            }
        }

        if (data.usagePageChange != null && "usage".equals(this.activeSection)) {
            List<String> recipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);

            if ("prev".equals(data.usagePageChange) && usagePage > 0) {
                usagePage--;
                needsRecipeUpdate = true;
            } else if ("next".equals(data.usagePageChange) && usagePage < totalPages - 1) {
                usagePage++;
                needsRecipeUpdate = true;
            }
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
        List<Map.Entry<String, Item>> results = new ArrayList<>();

        // Use global ITEMS map like Lumenia
        for (Map.Entry<String, Item> entry : JETPlugin.ITEMS.entrySet()) {
            if (searchQuery.isEmpty() || matchesSearch(entry.getKey(), entry.getValue())) {
                results.add(entry);
            }
        }

        cmd.clear("#ItemCards");

        String language = playerRef.getLanguage();

        int row = 0;
        int col = 0;
        int count = 0;

        for (Map.Entry<String, Item> entry : results) {
            if (count >= MAX_ITEMS) break;

            String key = entry.getKey();
            Item item = entry.getValue();

            if (col == 0) {
                cmd.appendInline("#ItemCards", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            cmd.append("#ItemCards[" + row + "]", "Pages/JET_ItemIcon.ui");
            String sel = "#ItemCards[" + row + "][" + col + "]";

            cmd.set(sel + " #ItemIcon.ItemId", key);

            String displayName = getDisplayName(item, language);
            if (displayName.length() > 14) {
                displayName = displayName.substring(0, 12) + "...";
            }
            cmd.set(sel + " #ItemName.TextSpans", Message.raw(displayName));
            cmd.set(sel + ".TooltipTextSpans", buildTooltip(key, item, language));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel, EventData.of("SelectedItem", key), false);

            col++;
            if (col >= ITEMS_PER_ROW) {
                col = 0;
                row++;
            }
            count++;
        }
    }

    private boolean matchesSearch(String itemId, Item item) {
        String query = searchQuery.toLowerCase();
        String language = playerRef.getLanguage();

        // Check translated name
        String translatedName = getDisplayName(item, language).toLowerCase();
        if (translatedName.contains(query)) return true;

        // Check item ID
        if (itemId.toLowerCase().contains(query)) return true;

        return false;
    }

    private void buildRecipePanel(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        if (selectedItem == null || selectedItem.isEmpty()) {
            cmd.set("#RecipePanel.Visible", false);
            return;
        }

        cmd.set("#RecipePanel.Visible", true);

        // Use global ITEMS map
        Item item = JETPlugin.ITEMS.get(selectedItem);
        String language = playerRef.getLanguage();

        cmd.set("#RecipePanel #SelectedIcon.ItemId", selectedItem);
        cmd.set("#RecipePanel #SelectedName.TextSpans", Message.raw(getDisplayName(item, language)));

        // Get recipe IDs from global maps like Lumenia
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
            cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("Not used in recipes"));
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw(""));
            return;
        }

        int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);
        if (usagePage >= totalPages) usagePage = totalPages - 1;
        if (usagePage < 0) usagePage = 0;

        int start = usagePage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipeIds.size());

        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("Uses (" + recipeIds.size() + "):"));
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

    private void buildRecipeDisplay(UICommandBuilder cmd, CraftingRecipe recipe, String rSel) {
        String recipeId = recipe.getId();
        if (recipeId.contains(":")) recipeId = recipeId.substring(recipeId.indexOf(":") + 1);

        String benchInfo = "";
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            benchInfo = " [" + formatBenchName(bench.id) + " T" + bench.requiredTierLevel + "]";
        }

        cmd.set(rSel + " #RecipeTitle.TextSpans", Message.raw(recipeId + benchInfo));

        // Add input items
        List<MaterialQuantity> inputs = getRecipeInputs(recipe);
        cmd.clear(rSel + " #InputItems");
        for (int j = 0; j < inputs.size(); j++) {
            MaterialQuantity input = inputs.get(j);
            cmd.appendInline(rSel + " #InputItems",
                    "Group { LayoutMode: Top; Padding: (Right: 6); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
            cmd.set(rSel + " #InputItems[" + j + "][0].ItemId", input.getItemId());
            cmd.set(rSel + " #InputItems[" + j + "][1].Text", "x" + input.getQuantity());
        }

        // Add output items
        MaterialQuantity[] outputs = recipe.getOutputs();
        cmd.clear(rSel + " #OutputItems");
        if (outputs != null && outputs.length > 0) {
            for (int j = 0; j < outputs.length; j++) {
                MaterialQuantity output = outputs[j];
                if (output != null && output.getItemId() != null) {
                    cmd.appendInline(rSel + " #OutputItems",
                            "Group { LayoutMode: Top; Padding: (Right: 6); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                    cmd.set(rSel + " #OutputItems[" + j + "][0].ItemId", output.getItemId());
                    cmd.set(rSel + " #OutputItems[" + j + "][1].Text", "x" + output.getQuantity());
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

    private Message buildTooltip(String itemId, Item item, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(getDisplayName(item, language)).append("\n");
        sb.append("-------------------\n");
        sb.append("ID: ").append(itemId).append("\n");
        sb.append("Stack: ").append(item.getMaxStack());

        if (item.getMaxDurability() > 0) {
            sb.append("\nDurability: ").append((int)item.getMaxDurability());
        }

        sb.append("\n-------------------");
        sb.append("\nClick to view recipes");

        return Message.raw(sb.toString());
    }

    private List<MaterialQuantity> getRecipeInputs(CraftingRecipe recipe) {
        List<MaterialQuantity> result = new ArrayList<>();
        Object inputsObj = null;

        // Try getInput method first
        try {
            Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            inputsObj = getInputMethod.invoke(recipe);
        } catch (Exception e) {
            // Try alternatives
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
            for (String methodName : methodNames) {
                try {
                    Method method = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = method.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (Exception ignored) {}
            }
        }

        // Process inputs
        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null && input.getItemId() != null) {
                    result.add(input);
                }
            } else if (inputsObj instanceof List) {
                List<?> inputs = (List<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && input.getItemId() != null) {
                            result.add(input);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                for (MaterialQuantity input : inputs) {
                    if (input != null && input.getItemId() != null) {
                        result.add(input);
                    }
                }
            } else if (inputsObj instanceof Collection) {
                Collection<?> inputs = (Collection<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && input.getItemId() != null) {
                            result.add(input);
                        }
                    }
                }
            }
        }

        return result;
    }

    public static class GuiData {
        public static final BuilderCodec<GuiData> CODEC = BuilderCodec
                .builder(GuiData.class, GuiData::new)
                .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .addField(new KeyedCodec<>("ActiveSection", Codec.STRING), (d, v) -> d.activeSection = v, d -> d.activeSection)
                .addField(new KeyedCodec<>("CraftPageChange", Codec.STRING), (d, v) -> d.craftPageChange = v, d -> d.craftPageChange)
                .addField(new KeyedCodec<>("UsagePageChange", Codec.STRING), (d, v) -> d.usagePageChange = v, d -> d.usagePageChange)
                .build();

        private String searchQuery;
        private String selectedItem;
        private String activeSection;
        private String craftPageChange;
        private String usagePageChange;

        public GuiData() {}
    }
}