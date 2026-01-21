package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;

import java.util.*;

/**
 * Combined Inventory + JET Browser Page.
 */
public class JETInventoryPage extends InteractiveCustomUIPage<JETInventoryPage.PageData> {

    private static final int ITEMS_PER_ROW = 9;
    private static final int MAX_ROWS = 8;
    private static final int MAX_ITEMS = ITEMS_PER_ROW * MAX_ROWS;

    private final Page basePage;
    private String searchQuery = "";
    private int currentPage = 0;
    private String selectedItem = null;
    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();

    public JETInventoryPage(PlayerRef playerRef, Page basePage) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.basePage = basePage;
        refreshFilteredItems();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        // Load the combined inventory + JET UI
        cmd.append("Pages/JET_InventoryOverlay.ui");

        // Set up which base inventory page to show
        cmd.set("#InventoryContainer.Visible", basePage == Page.Inventory);
        cmd.set("#BenchContainer.Visible", basePage == Page.Bench);

        // Search bar binding
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#JETPanel #SearchInput",
                EventData.of("@SearchQuery", "#JETPanel #SearchInput.Value"),
                false
        );

        // Pagination
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JETPanel #PrevPage",
                EventData.of("PageAction", "prev"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JETPanel #NextPage",
                EventData.of("PageAction", "next"),
                false
        );

        // Close/back button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JETPanel #CloseRecipe",
                EventData.of("CloseRecipe", "close"),
                false
        );

        buildItemGrid(cmd, events);
        updatePageInfo(cmd);
        buildRecipePreview(cmd, events);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        super.handleDataEvent(ref, store, data);

        boolean needsUpdate = false;

        // Handle search
        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            this.currentPage = 0;
            refreshFilteredItems();
            needsUpdate = true;
        }

        // Handle pagination
        if (data.pageAction != null) {
            int totalPages = getTotalPages();
            if ("prev".equals(data.pageAction) && currentPage > 0) {
                currentPage--;
                needsUpdate = true;
            } else if ("next".equals(data.pageAction) && currentPage < totalPages - 1) {
                currentPage++;
                needsUpdate = true;
            }
        }

        // Handle item selection for recipe preview
        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.selectedItem = data.selectedItem;
            needsUpdate = true;
        }

        // Handle close recipe
        if (data.closeRecipe != null) {
            this.selectedItem = null;
            needsUpdate = true;
        }

        if (needsUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildItemGrid(cmd, events);
            updatePageInfo(cmd);
            buildRecipePreview(cmd, events);
            sendUpdate(cmd, events, false);
        }
    }

    private void refreshFilteredItems() {
        filteredItems.clear();
        String query = searchQuery.toLowerCase().trim();
        String language = playerRef.getLanguage();

        for (Map.Entry<String, Item> entry : JETPlugin.ITEMS.entrySet()) {
            if (query.isEmpty() || matchesSearch(entry.getKey(), entry.getValue(), query, language)) {
                filteredItems.add(entry);
            }
        }

        // Sort alphabetically by display name
        filteredItems.sort((a, b) -> {
            String nameA = getDisplayName(a.getValue(), language);
            String nameB = getDisplayName(b.getValue(), language);
            return nameA.compareToIgnoreCase(nameB);
        });
    }

    private boolean matchesSearch(String itemId, Item item, String query, String language) {
        // Component filtering with # prefix
        if (query.startsWith("#")) {
            String componentTag = query.substring(1);
            return itemId.toLowerCase().contains(componentTag.toLowerCase());
        }

        // Check translated name
        String translatedName = getDisplayName(item, language).toLowerCase();
        if (translatedName.contains(query)) return true;

        // Check item ID
        if (itemId.toLowerCase().contains(query)) return true;

        return false;
    }

    private void buildItemGrid(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.clear("#JETPanel #ItemGrid");

        int startIdx = currentPage * MAX_ITEMS;
        int endIdx = Math.min(startIdx + MAX_ITEMS, filteredItems.size());

        String language = playerRef.getLanguage();

        int row = 0;
        int col = 0;

        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, Item> entry = filteredItems.get(i);
            String itemId = entry.getKey();
            Item item = entry.getValue();

            // Create new row if needed
            if (col == 0) {
                cmd.appendInline("#JETPanel #ItemGrid", 
                    "Group { LayoutMode: Left; Anchor: (Height: 34); }");
            }

            // Add item slot
            cmd.appendInline("#JETPanel #ItemGrid[" + row + "]",
                "Group { Anchor: (Width: 34, Height: 34); LayoutMode: Center; Background: #2a2a2a(0.7); Padding: (Full: 1); " +
                "ItemIcon #Icon { Anchor: (Width: 30, Height: 30); Visible: true; } }");
            
            String sel = "#JETPanel #ItemGrid[" + row + "][" + col + "]";

            cmd.set(sel + " #Icon.ItemId", itemId);
            
            String displayName = getDisplayName(item, language);
            cmd.set(sel + ".TooltipTextSpans", Message.raw(displayName + "\n§7" + itemId));

            // Click to show recipe preview
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel,
                    EventData.of("SelectedItem", itemId),
                    false
            );

            col++;
            if (col >= ITEMS_PER_ROW) {
                col = 0;
                row++;
            }
        }
    }

    private void updatePageInfo(UICommandBuilder cmd) {
        int totalPages = getTotalPages();
        if (totalPages == 0) totalPages = 1;
        
        cmd.set("#JETPanel #PageInfo.Text", (currentPage + 1) + "/" + totalPages);
        cmd.set("#JETPanel #ItemCount.Text", filteredItems.size() + " items");
    }

    private void buildRecipePreview(UICommandBuilder cmd, UIEventBuilder events) {
        if (selectedItem == null || selectedItem.isEmpty()) {
            cmd.set("#JETPanel #RecipePreview.Visible", false);
            return;
        }

        cmd.set("#JETPanel #RecipePreview.Visible", true);

        Item item = JETPlugin.ITEMS.get(selectedItem);
        String language = playerRef.getLanguage();

        cmd.set("#JETPanel #RecipePreview #SelectedIcon.ItemId", selectedItem);
        cmd.set("#JETPanel #RecipePreview #SelectedName.TextSpans", 
                Message.raw(getDisplayName(item, language)));

        // Get recipes
        List<String> craftRecipes = JETPlugin.ITEM_TO_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> usageRecipes = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(selectedItem, Collections.emptyList());

        StringBuilder info = new StringBuilder();
        info.append("Craft: ").append(craftRecipes.size()).append(" | ");
        info.append("Uses: ").append(usageRecipes.size());
        cmd.set("#JETPanel #RecipePreview #RecipeInfo.TextSpans", Message.raw(info.toString()));

        // Show first craft recipe if available
        cmd.clear("#JETPanel #RecipePreview #RecipeDisplay");
        
        if (!craftRecipes.isEmpty()) {
            CraftingRecipe recipe = JETPlugin.RECIPES.get(craftRecipes.get(0));
            if (recipe != null) {
                buildMiniRecipe(cmd, recipe, "#JETPanel #RecipePreview #RecipeDisplay");
            }
        }
    }

    private void buildMiniRecipe(UICommandBuilder cmd, CraftingRecipe recipe, String selector) {
        // Show inputs -> outputs in a compact format
        cmd.appendInline(selector, 
            "Group { LayoutMode: Left; Anchor: (Height: 40); }");
        
        // Add recipe ID as title
        String recipeId = recipe.getId();
        if (recipeId.contains(":")) recipeId = recipeId.substring(recipeId.indexOf(":") + 1);
        
        cmd.appendInline(selector + "[0]",
            "Label { Style: (FontSize: 10, TextColor: #888888); Text: \"" + recipeId + "\"; Anchor: (Width: 200); }");
    }

    private int getTotalPages() {
        return (int) Math.ceil((double) filteredItems.size() / MAX_ITEMS);
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

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec
                .builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .addField(new KeyedCodec<>("PageAction", Codec.STRING), (d, v) -> d.pageAction = v, d -> d.pageAction)
                .addField(new KeyedCodec<>("CloseRecipe", Codec.STRING), (d, v) -> d.closeRecipe = v, d -> d.closeRecipe)
                .build();

        private String searchQuery;
        private String selectedItem;
        private String pageAction;
        private String closeRecipe;

        public PageData() {}
    }
}
