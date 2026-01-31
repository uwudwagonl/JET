package dev.hytalemod.jet.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.util.InventoryScanner;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;

public class RecipeOverlayHud extends CustomUIHud {

    private String pinnedRecipeId;
    private CraftingRecipe pinnedRecipe;

    public RecipeOverlayHud(@NotNull PlayerRef playerRef) {
        super(playerRef);
        this.pinnedRecipeId = null;
        this.pinnedRecipe = null;
    }

    @Override
    protected void build(@NotNull UICommandBuilder cmd) {
        if (pinnedRecipe == null) {
            return;
        }

        PlayerRef playerRef = getPlayerRef();
        String language = playerRef.getLanguage();
        
        // Try to get Player for inventory counts
        Player player = null;
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                player = store.getComponent(ref, Player.getComponentType());
            }
        } catch (Exception ignored) {}

        // Build overlay inline since .ui files have issues
        StringBuilder sb = new StringBuilder();
        sb.append("Group #RecipeOverlay { ");
        sb.append("Anchor: (Top: 10, Right: 10, Width: 220); ");
        sb.append("LayoutMode: Top; ");
        sb.append("Background: (Color: #1a1a1a(0.85)); ");
        sb.append("Padding: (Full: 10); ");
        
        // Title
        String title = "Pinned: " + formatRecipeId(pinnedRecipeId);
        sb.append("Label #Title { Text: \"").append(escapeText(title)).append("\"; ");
        sb.append("Style: (FontSize: 14, TextColor: #ffaa00, RenderBold: true); ");
        sb.append("Anchor: (Bottom: 8); } ");
        
        // Output row
        MaterialQuantity[] outputs = pinnedRecipe.getOutputs();
        if (outputs != null && outputs.length > 0 && outputs[0] != null) {
            String outputItemId = outputs[0].getItemId();
            Item outputItem = JETPlugin.ITEMS.get(outputItemId);
            String outputName = getDisplayName(outputItem, language);
            int outputQty = outputs[0].getQuantity();
            
            sb.append("Group #OutputRow { LayoutMode: Left; Anchor: (Bottom: 8); ");
            sb.append("ItemIcon #OutputIcon { Anchor: (Width: 32, Height: 32, Right: 8); Visible: true; ItemId: \"").append(outputItemId).append("\"; } ");
            sb.append("Label #OutputName { Text: \"").append(escapeText(outputName + " x" + outputQty)).append("\"; ");
            sb.append("Style: (FontSize: 12, TextColor: #88ff88, VerticalAlignment: Center); FlexWeight: 1; } ");
            sb.append("} ");
        }
        
        // Divider
        sb.append("Group #Divider { Anchor: (Height: 1, Bottom: 8); Background: (Color: #444444); } ");
        
        // Materials label
        sb.append("Label #MaterialsLabel { Text: \"Materials:\"; Style: (FontSize: 11, TextColor: #aaaaaa); Anchor: (Bottom: 4); } ");
        
        // Materials group
        sb.append("Group #Materials { LayoutMode: Top; ");
        
        List<MaterialQuantity> inputs = getRecipeInputs(pinnedRecipe);
        for (MaterialQuantity input : inputs) {
            String itemId = input.getItemId();
            String resourceTypeId = input.getResourceTypeId();
            int required = input.getQuantity();
            
            sb.append("Group { LayoutMode: Left; Anchor: (Bottom: 4); ");
            
            if (itemId != null) {
                Item item = JETPlugin.ITEMS.get(itemId);
                String name = getDisplayName(item, language);
                if (name.length() > 15) name = name.substring(0, 13) + "..";
                
                int have = player != null ? InventoryScanner.countItemInInventory(player, itemId) : 0;
                String color = have >= required ? "#00ff00" : "#ff4444";
                
                sb.append("ItemIcon { Anchor: (Width: 24, Height: 24, Right: 6); Visible: true; ItemId: \"").append(itemId).append("\"; } ");
                sb.append("Label { Text: \"").append(escapeText(name)).append("\"; Style: (FontSize: 11, TextColor: #cccccc, VerticalAlignment: Center); FlexWeight: 1; } ");
                sb.append("Label { Text: \"").append(have).append("/").append(required).append("\"; Style: (FontSize: 11, TextColor: ").append(color).append(", VerticalAlignment: Center, HorizontalAlignment: Right); Anchor: (Width: 50); } ");
            } else if (resourceTypeId != null) {
                String rtName = formatResourceType(resourceTypeId);
                int have = player != null ? InventoryScanner.countResourceTypeInInventory(player, resourceTypeId) : 0;
                String color = have >= required ? "#00ff00" : "#ff4444";
                
                sb.append("Label { Text: \"").append(escapeText(rtName)).append("\"; Style: (FontSize: 11, TextColor: #cccccc, VerticalAlignment: Center); FlexWeight: 1; } ");
                sb.append("Label { Text: \"").append(have).append("/").append(required).append("\"; Style: (FontSize: 11, TextColor: ").append(color).append(", VerticalAlignment: Center, HorizontalAlignment: Right); Anchor: (Width: 50); } ");
            }
            
            sb.append("} ");
        }
        
        sb.append("} "); // Close Materials
        
        // Bench requirement
        BenchRequirement[] benches = pinnedRecipe.getBenchRequirement();
        if (benches != null && benches.length > 0 && benches[0] != null) {
            String benchName = "Requires: " + formatBenchName(benches[0].id) + " T" + benches[0].requiredTierLevel;
            sb.append("Group #BenchRow { LayoutMode: Left; Anchor: (Top: 6); ");
            sb.append("Label { Text: \"").append(escapeText(benchName)).append("\"; Style: (FontSize: 10, TextColor: #ffaa00); } ");
            sb.append("} ");
        }
        
        sb.append("}"); // Close RecipeOverlay
        
        cmd.appendInline("#hud-root", sb.toString());
    }
    
    private String escapeText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void setPinnedRecipe(String recipeId) {
        this.pinnedRecipeId = recipeId;
        this.pinnedRecipe = recipeId != null ? JETPlugin.RECIPES.get(recipeId) : null;
    }

    public String getPinnedRecipeId() {
        return pinnedRecipeId;
    }

    public boolean hasPinnedRecipe() {
        return pinnedRecipe != null;
    }

    private String formatRecipeId(String recipeId) {
        if (recipeId == null) return "";
        if (recipeId.contains(":")) recipeId = recipeId.substring(recipeId.indexOf(":") + 1);
        return recipeId.replace("_", " ");
    }

    private String formatBenchName(String benchId) {
        if (benchId == null) return "";
        if (benchId.contains(":")) benchId = benchId.substring(benchId.indexOf(":") + 1);
        return benchId.replace("_", " ");
    }

    private String formatResourceType(String resourceTypeId) {
        if (resourceTypeId == null) return "Any";
        if (resourceTypeId.contains(":")) resourceTypeId = resourceTypeId.substring(resourceTypeId.indexOf(":") + 1);
        return "Any " + resourceTypeId.replace("_", " ");
    }

    private String getDisplayName(Item item, String language) {
        if (item == null) return "Unknown";
        try {
            String key = item.getTranslationKey();
            if (key != null) {
                String translated = I18nModule.get().getMessage(language, key);
                if (translated != null && !translated.isEmpty()) return translated;
            }
        } catch (Exception ignored) {}

        String id = item.getId();
        if (id == null) return "Unknown";
        if (id.contains(":")) id = id.substring(id.indexOf(":") + 1);
        int underscore = id.indexOf("_");
        if (underscore > 0) id = id.substring(underscore + 1);
        return id.replace("_", " ");
    }

    private List<MaterialQuantity> getRecipeInputs(CraftingRecipe recipe) {
        List<MaterialQuantity> result = new ArrayList<>();
        Object inputsObj = null;

        try {
            Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            inputsObj = getInputMethod.invoke(recipe);
        } catch (Exception e) {
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials"};
            for (String methodName : methodNames) {
                try {
                    Method method = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = method.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (Exception ignored) {}
            }
        }

        if (inputsObj instanceof MaterialQuantity mq) {
            if (mq.getItemId() != null || mq.getResourceTypeId() != null) result.add(mq);
        } else if (inputsObj instanceof MaterialQuantity[] arr) {
            for (MaterialQuantity mq : arr) {
                if (mq != null && (mq.getItemId() != null || mq.getResourceTypeId() != null)) result.add(mq);
            }
        } else if (inputsObj instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof MaterialQuantity mq) {
                    if (mq.getItemId() != null || mq.getResourceTypeId() != null) result.add(mq);
                }
            }
        } else if (inputsObj instanceof Collection<?> coll) {
            for (Object obj : coll) {
                if (obj instanceof MaterialQuantity mq) {
                    if (mq.getItemId() != null || mq.getResourceTypeId() != null) result.add(mq);
                }
            }
        }

        return result;
    }
}
