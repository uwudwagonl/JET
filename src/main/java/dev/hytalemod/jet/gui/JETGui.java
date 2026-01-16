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
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.RecipeRegistry;

import java.util.List;
import java.util.Map;

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
    private String viewMode;
    private int recipePage;
    
    public JETGui(PlayerRef playerRef, CustomPageLifetime lifetime, String initialSearch) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = initialSearch != null ? initialSearch : "";
        this.selectedItem = null;
        this.viewMode = "craft";
        this.recipePage = 0;
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
        buildRecipePanel(cmd);
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
            this.recipePage = 0;
            needsRecipeUpdate = true;
        }
        
        if (data.viewMode != null && !data.viewMode.isEmpty() && !data.viewMode.equals(this.viewMode)) {
            this.viewMode = data.viewMode;
            this.recipePage = 0;
            needsRecipeUpdate = true;
        }

        if (data.toggleMode != null && "toggle".equals(data.toggleMode)) {
            // Toggle between craft and uses modes
            this.viewMode = "craft".equals(this.viewMode) ? "uses" : "craft";
            this.recipePage = 0;
            needsRecipeUpdate = true;
        }
        
        if (data.pageChange != null) {
            if ("prev".equals(data.pageChange) && recipePage > 0) {
                recipePage--;
                needsRecipeUpdate = true;
            } else if ("next".equals(data.pageChange)) {
                recipePage++;
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
                buildRecipePanel(cmd);
            }
            
            sendUpdate(cmd, events, false);
        }
    }
    
    private void buildItemList(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        ItemRegistry registry = JETPlugin.getInstance().getItemRegistry();
        List<Map.Entry<String, Item>> results = registry.search(searchQuery);
        
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
    
    private void buildRecipePanel(UICommandBuilder cmd) {
        if (selectedItem == null) {
            cmd.set("#RecipePanel.Visible", false);
            return;
        }

        cmd.set("#RecipePanel.Visible", true);

        RecipeRegistry recipeReg = JETPlugin.getInstance().getRecipeRegistry();
        ItemRegistry itemReg = JETPlugin.getInstance().getItemRegistry();
        Item item = itemReg.get(selectedItem);
        String language = playerRef.getLanguage();

        cmd.set("#RecipePanel #SelectedIcon.ItemId", selectedItem);
        cmd.set("#RecipePanel #SelectedName.TextSpans", Message.raw(getDisplayName(item, language)));

        // Get recipes
        List<CraftingRecipe> recipes = "craft".equals(viewMode)
            ? recipeReg.getCraftingRecipes(selectedItem)
            : recipeReg.getUsageRecipes(selectedItem);

        // Debug logging
        System.out.println("[JET] Item: " + selectedItem + ", Mode: " + viewMode + ", Recipes found: " + recipes.size());
        if ("craft".equals(viewMode)) {
            System.out.println("[JET] Has crafting recipes: " + recipeReg.hasCraftingRecipes(selectedItem));
        } else {
            System.out.println("[JET] Has usage recipes: " + recipeReg.hasUsageRecipes(selectedItem));
        }

        // Clear recipe list - use full path like Lumenia
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipes.isEmpty()) {
            cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw("uses".equals(viewMode)
                ? "No recipes use this item"
                : "No recipes create this item"));
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw(""));
            return;
        }
        
        // Pagination
        int totalPages = (recipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE;
        if (recipePage >= totalPages) recipePage = totalPages - 1;
        if (recipePage < 0) recipePage = 0;

        int start = recipePage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipes.size());

        String modeText = "craft".equals(viewMode) ? "Craft" : "Uses";
        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw(modeText + " (" + recipes.size() + "):"));
        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((recipePage + 1) + " / " + totalPages));

        // Build recipe entries - use FULL PATH like Lumenia does
        for (int i = start; i < end; i++) {
            CraftingRecipe recipe = recipes.get(i);
            int idx = i - start;

            // Use append (not appendInline) with full path
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            // Format recipe title
            String recipeId = recipe.getId();
            if (recipeId.contains(":")) recipeId = recipeId.substring(recipeId.indexOf(":") + 1);

            // Add bench/station info to title
            String benchInfo = "";
            try {
                Object benchReq = recipe.getBenchRequirement();
                if (benchReq != null && benchReq.getClass().isArray()) {
                    Object[] benches = (Object[]) benchReq;
                    if (benches.length > 0) {
                        Object bench = benches[0];
                        try {
                            java.lang.reflect.Method getIdMethod = bench.getClass().getMethod("getId");
                            String benchId = (String) getIdMethod.invoke(bench);
                            if (benchId != null && !benchId.isEmpty()) {
                                benchInfo = " [" + formatBenchName(benchId) + "]";
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            cmd.set(rSel + " #RecipeTitle.TextSpans", Message.raw(recipeId + benchInfo));

            // Add input items with quantities
            List<MaterialQuantity> inputs = getRecipeInputs(recipe);
            cmd.clear(rSel + " #InputItems");
            if (!inputs.isEmpty()) {
                for (int j = 0; j < inputs.size(); j++) {
                    MaterialQuantity input = inputs.get(j);
                    cmd.appendInline(rSel + " #InputItems",
                        "Group { LayoutMode: Top; Padding: (Right: 6); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                    cmd.set(rSel + " #InputItems[" + j + "][0].ItemId", input.getItemId());
                    cmd.set(rSel + " #InputItems[" + j + "][1].Text", "x" + input.getQuantity());
                }
            }

            // Add output items with quantities
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
    }

    private String formatBenchName(String benchId) {
        if (benchId == null) return "";
        // Remove namespace
        if (benchId.contains(":")) benchId = benchId.substring(benchId.indexOf(":") + 1);
        // Remove hytale_ prefix
        if (benchId.startsWith("hytale_")) benchId = benchId.substring(7);
        // Convert to title case
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
        List<MaterialQuantity> result = new java.util.ArrayList<>();
        Object inputsObj = null;

        // Try getInput method first
        try {
            java.lang.reflect.Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            inputsObj = getInputMethod.invoke(recipe);
        } catch (Exception e) {
            // Method doesn't exist or failed, try alternatives
        }

        // If getInput failed, try alternative methods
        if (inputsObj == null) {
            String[] methodNames = {
                "getInputs",
                "getIngredients",
                "getMaterials",
                "getRecipeInputs",
                "getRequiredMaterials"
            };

            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = method.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (Exception ignored) {}
            }
        }

        // Process the inputs object (supports multiple types)
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
            } else if (inputsObj instanceof java.util.Collection) {
                java.util.Collection<?> inputs = (java.util.Collection<?>) inputsObj;
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
            .addField(new KeyedCodec<>("ViewMode", Codec.STRING), (d, v) -> d.viewMode = v, d -> d.viewMode)
            .addField(new KeyedCodec<>("PageChange", Codec.STRING), (d, v) -> d.pageChange = v, d -> d.pageChange)
            .addField(new KeyedCodec<>("ToggleMode", Codec.STRING), (d, v) -> d.toggleMode = v, d -> d.toggleMode)
            .build();

        private String searchQuery;
        private String selectedItem;
        private String viewMode;
        private String pageChange;
        private String toggleMode;

        public GuiData() {}
    }
}
