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
import dev.hytalemod.jet.model.ItemCategory;
import dev.hytalemod.jet.util.CategoryUtil;
import dev.hytalemod.jet.util.InventoryScanner;
import dev.hytalemod.jet.util.SearchParser;
import com.hypixel.hytale.server.core.entity.entities.Player;

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
    private String activeSection; // "craft", "usage", or "drops"
    private int craftPage;
    private int usagePage;
    private int dropsPage;
    private int itemPage; // Pagination for item list
    private Set<ItemCategory> activeFilters; // Active category filters

    public JETGui(PlayerRef playerRef, CustomPageLifetime lifetime, String initialSearch) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = initialSearch != null ? initialSearch : "";
        this.selectedItem = null;
        this.activeSection = "craft";
        this.craftPage = 0;
        this.usagePage = 0;
        this.dropsPage = 0;
        this.itemPage = 0;
        this.activeFilters = new HashSet<>();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Gui.ui");

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

        // Toggle mode button - switches to craft mode
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #ToggleModeButton",
                EventData.of("ToggleMode", "craft"),
                false
        );

        // Uses button - switches to usage mode
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #UsesButton",
                EventData.of("ToggleMode", "usage"),
                false
        );

        // Obtained From button - switches to drops mode
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #ObtainedFromButton",
                EventData.of("ToggleMode", "drops"),
                false
        );

        // Pin button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #PinButton",
                EventData.of("PinAction", "toggle"),
                false
        );

        // Pagination for recipes
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipe", EventData.of("PageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipe", EventData.of("PageChange", "next"), false);

        // Pagination for item list
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevItemPage", EventData.of("ItemPageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextItemPage", EventData.of("ItemPageChange", "next"), false);

        // Category filter buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterTool", EventData.of("CategoryFilter", "TOOL"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterWeapon", EventData.of("CategoryFilter", "WEAPON"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterArmor", EventData.of("CategoryFilter", "ARMOR"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterConsumable", EventData.of("CategoryFilter", "CONSUMABLE"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterBlock", EventData.of("CategoryFilter", "BLOCK"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterCraftable", EventData.of("CategoryFilter", "CRAFTABLE"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterNonCraftable", EventData.of("CategoryFilter", "NON_CRAFTABLE"), false);

        // Clear filters button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearFilters", EventData.of("ClearFilters", "true"), false);

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
            this.itemPage = 0; // Reset to first page on search
            needsItemUpdate = true;
            // Deselect item when search changes
            this.selectedItem = null;
            needsRecipeUpdate = true;
        }

        // Handle category filter toggle
        if (data.categoryFilter != null && !data.categoryFilter.isEmpty()) {
            try {
                ItemCategory category = ItemCategory.valueOf(data.categoryFilter);
                if (activeFilters.contains(category)) {
                    activeFilters.remove(category);
                } else {
                    activeFilters.add(category);
                }
                this.itemPage = 0; // Reset to first page on filter change
                needsItemUpdate = true;
            } catch (IllegalArgumentException e) {
                // Invalid category, ignore
            }
        }

        // Handle clear filters
        if (data.clearFilters != null && "true".equals(data.clearFilters)) {
            if (!activeFilters.isEmpty()) {
                activeFilters.clear();
                this.itemPage = 0;
                needsItemUpdate = true;
            }
        }

        // Handle item page navigation
        if (data.itemPageChange != null) {
            if ("prev".equals(data.itemPageChange) && itemPage > 0) {
                itemPage--;
                needsItemUpdate = true;
            } else if ("next".equals(data.itemPageChange)) {
                itemPage++;
                needsItemUpdate = true;
            }
        }

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            this.selectedItem = data.selectedItem;
            this.craftPage = 0;
            this.usagePage = 0;
            this.activeSection = "craft";
            needsRecipeUpdate = true;
        }

        // Handle toggle mode - now separate buttons for craft/usage/drops
        if (data.toggleMode != null && !data.toggleMode.isEmpty()) {
            if ("craft".equals(data.toggleMode) || "usage".equals(data.toggleMode) || "drops".equals(data.toggleMode)) {
                this.activeSection = data.toggleMode;
                this.craftPage = 0;
                this.usagePage = 0;
                this.dropsPage = 0;
                needsRecipeUpdate = true;
            }
        }

        if (data.activeSection != null && !data.activeSection.isEmpty() && !data.activeSection.equals(this.activeSection)) {
            this.activeSection = data.activeSection;
            this.craftPage = 0;
            this.usagePage = 0;
            this.dropsPage = 0;
            needsRecipeUpdate = true;
        }

        // Handle pin/unpin action
        if (data.pinAction != null && "toggle".equals(data.pinAction) && this.selectedItem != null) {
            UUID playerUuid = playerRef.getUuid();
            boolean isPinned = JETPlugin.getInstance().getPinnedItemsStorage().togglePin(playerUuid, this.selectedItem);
            needsRecipeUpdate = true;
        }

        if (data.pageChange != null) {
            List<String> recipeIds;
            if ("craft".equals(this.activeSection)) {
                recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            } else if ("usage".equals(this.activeSection)) {
                recipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            } else {
                recipeIds = JETPlugin.getInstance().getDropListRegistry().getDropSourcesForItem(this.selectedItem);
            }
            int totalPages = (int) Math.ceil((double) recipeIds.size() / RECIPES_PER_PAGE);

            if ("prev".equals(data.pageChange)) {
                if ("craft".equals(this.activeSection) && craftPage > 0) {
                    craftPage--;
                    needsRecipeUpdate = true;
                } else if ("usage".equals(this.activeSection) && usagePage > 0) {
                    usagePage--;
                    needsRecipeUpdate = true;
                } else if ("drops".equals(this.activeSection) && dropsPage > 0) {
                    dropsPage--;
                    needsRecipeUpdate = true;
                }
            } else if ("next".equals(data.pageChange)) {
                if ("craft".equals(this.activeSection) && craftPage < totalPages - 1) {
                    craftPage++;
                    needsRecipeUpdate = true;
                } else if ("usage".equals(this.activeSection) && usagePage < totalPages - 1) {
                    usagePage++;
                    needsRecipeUpdate = true;
                } else if ("drops".equals(this.activeSection) && dropsPage < totalPages - 1) {
                    dropsPage++;
                    needsRecipeUpdate = true;
                }
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

        // Filter items by search and category
        for (Map.Entry<String, Item> entry : JETPlugin.ITEMS.entrySet()) {
            boolean matchesSearch = searchQuery.isEmpty() || matchesSearch(entry.getKey(), entry.getValue());
            boolean matchesCategory = activeFilters.isEmpty() || matchesActiveFilters(entry.getValue());

            if (matchesSearch && matchesCategory) {
                results.add(entry);
            }
        }

        // Sort alphabetically by ID
        results.sort(Comparator.comparing(e -> e.getKey().toLowerCase()));

        // Calculate pagination
        int totalItems = results.size();
        int totalPages = (int) Math.ceil((double) totalItems / MAX_ITEMS);
        if (totalPages == 0) totalPages = 1;

        // Clamp page to valid range
        if (itemPage >= totalPages) {
            itemPage = Math.max(0, totalPages - 1);
        }

        int startIndex = itemPage * MAX_ITEMS;
        int endIndex = Math.min(startIndex + MAX_ITEMS, totalItems);

        cmd.clear("#ItemCards");

        String language = playerRef.getLanguage();

        int row = 0;
        int col = 0;

        // Display items for current page
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, Item> entry = results.get(i);

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
        }

        // Update pagination info
        cmd.set("#ItemPageInfo.TextSpans", Message.raw(String.format("Page %d / %d (%d items)", itemPage + 1, totalPages, totalItems)));

        // Show/hide pagination buttons
        cmd.set("#PrevItemPage.Visible", itemPage > 0);
        cmd.set("#NextItemPage.Visible", itemPage < totalPages - 1);

        // Update filter button text with brackets to show active state
        cmd.set("#FilterTool.Text", activeFilters.contains(ItemCategory.TOOL) ? "[Tools]" : "Tools");
        cmd.set("#FilterWeapon.Text", activeFilters.contains(ItemCategory.WEAPON) ? "[Weapons]" : "Weapons");
        cmd.set("#FilterArmor.Text", activeFilters.contains(ItemCategory.ARMOR) ? "[Armor]" : "Armor");
        cmd.set("#FilterConsumable.Text", activeFilters.contains(ItemCategory.CONSUMABLE) ? "[Consumables]" : "Consumables");
        cmd.set("#FilterBlock.Text", activeFilters.contains(ItemCategory.BLOCK) ? "[Blocks]" : "Blocks");
        cmd.set("#FilterCraftable.Text", activeFilters.contains(ItemCategory.CRAFTABLE) ? "[Craftable]" : "Craftable");
        cmd.set("#FilterNonCraftable.Text", activeFilters.contains(ItemCategory.NON_CRAFTABLE) ? "[Non-Craftable]" : "Non-Craftable");
    }

    private boolean matchesActiveFilters(Item item) {
        for (ItemCategory category : activeFilters) {
            if (CategoryUtil.matchesCategory(item, category)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSearch(String itemId, Item item) {
        String query = searchQuery.trim();

        if (query.isEmpty()) {
            return true;
        }

        String language = playerRef.getLanguage();

        // Component filtering with # prefix (e.g., #tool, #food)
        if (query.startsWith("#")) {
            String componentTag = query.substring(1); // Remove the # prefix
            return hasComponent(item, componentTag);
        }

        // Use advanced search parser for @ (namespace) and - (exclusion) syntax
        SearchParser parser = new SearchParser(query);
        return parser.matches(item);
    }

    private boolean hasComponent(Item item, String componentTag) {
        if (item == null || componentTag == null || componentTag.isEmpty()) {
            return false;
        }

        // Check item ID - many items have their type in the ID (e.g., Weapon_Sword, Tool_Pickaxe, Food_Apple)
        try {
            Method getIdMethod = Item.class.getMethod("getId");
            Object idObj = getIdMethod.invoke(item);
            if (idObj != null) {
                String itemId = idObj.toString().toLowerCase();
                if (itemId.contains(componentTag.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        try {
            // Try getItemType() which returns the item's type category
            Method getItemTypeMethod = Item.class.getMethod("getItemType");
            Object itemType = getItemTypeMethod.invoke(item);
            if (itemType != null) {
                String itemTypeStr = itemType.toString().toLowerCase();
                if (itemTypeStr.contains(componentTag.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        try {
            // Try to get components/tags via getComponents
            Method getComponentsMethod = Item.class.getMethod("getComponents");
            Object components = getComponentsMethod.invoke(item);
            if (components != null) {
                String componentsStr = components.toString().toLowerCase();
                if (componentsStr.contains(componentTag.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        try {
            // Try hasComponent method
            Method hasComponentMethod = Item.class.getMethod("hasComponent", String.class);
            Object result = hasComponentMethod.invoke(item, componentTag);
            if (result instanceof Boolean && (Boolean) result) {
                return true;
            }
        } catch (Exception ignored) {}

        try {
            // Try getComponent method
            Method getComponentMethod = Item.class.getMethod("getComponent", String.class);
            Object component = getComponentMethod.invoke(item, componentTag);
            if (component != null) {
                return true;
            }
        } catch (Exception ignored) {}

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

        // Get recipe IDs from global maps
        List<String> craftRecipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> usageRecipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> dropSources = JETPlugin.getInstance().getDropListRegistry().getDropSourcesForItem(selectedItem);

        // Set recipe info label
        String recipeInfo = "Craft: " + craftRecipeIds.size() + " | Uses: " + usageRecipeIds.size() + " | Drops: " + dropSources.size();
        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw(recipeInfo));

        // Update pin button text based on current pin status
        UUID playerUuid = playerRef.getUuid();
        boolean isPinned = JETPlugin.getInstance().getPinnedItemsStorage().isPinned(playerUuid, selectedItem);
        cmd.set("#RecipePanel #PinButton.Text", isPinned ? "Unpin" : "Pin");

        if ("craft".equals(activeSection)) {
            buildCraftSection(ref, cmd, events, craftRecipeIds);
        } else if ("usage".equals(activeSection)) {
            buildUsageSection(ref, cmd, events, usageRecipeIds);
        } else {
            buildDropsSection(ref, cmd, events, dropSources);
        }
    }

    private void buildCraftSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipeIds.isEmpty()) {
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw("No recipes"));
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

            buildRecipeDisplay(cmd, recipe, rSel, ref);
        }
    }

    private void buildUsageSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (recipeIds.isEmpty()) {
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw("No recipes"));
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

            buildRecipeDisplay(cmd, recipe, rSel, ref);
        }
    }

    private void buildDropsSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> dropListIds) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (dropListIds.isEmpty()) {
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw("No drop sources"));
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

            // Use the recipe entry UI as a template
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            // Format the drop list ID for display
            String displayName = formatDropListName(dropListId);
            String dropType = getDropType(dropListId);

            // Set the title WITHOUT emoji icon
            cmd.set(rSel + " #RecipeTitle.TextSpans", Message.raw(displayName + " (" + dropType + ")"));

            // Hide both input and output sections for now
            cmd.set(rSel + "[1].Visible", false);  // Hide Input group
            cmd.set(rSel + "[2].Visible", false);  // Hide Output group
        }
    }

    private String getDropType(String dropListId) {
        if (dropListId.contains("Ore_")) return "Mining";
        if (dropListId.contains("Chest")) return "Chest Loot";
        if (dropListId.contains("Plant_") || dropListId.contains("Crop_")) return "Farming";
        return "Drop";
    }

    private String getDropTypeIcon(String dropType) {
        switch (dropType) {
            case "Mining": return "⛏";
            case "Chest Loot": return "📦";
            case "Farming": return "🌾";
            default: return "💎";
        }
    }

    private String getEntityIdFromDropList(String dropListId) {
        // Extract entity ID from drop list names
        // Pattern: "Zone1_Trork_Tier3" -> "Trork"
        // Pattern: "Drop_Crow" -> "Crow"
        // Pattern: "Drop_Rex_Cave" -> "Rex_Cave"

        if (dropListId == null || dropListId.isEmpty()) {
            return null;
        }

        // Handle Zone-based drops
        if (dropListId.startsWith("Zone")) {
            String[] parts = dropListId.split("_");
            if (parts.length >= 3) {
                // Entity name is everything between zone and tier
                StringBuilder entityName = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) {
                    if (i > 1) entityName.append("_");
                    entityName.append(parts[i]);
                }
                return entityName.toString();
            }
        }

        // Handle "Drop_EntityName" pattern
        if (dropListId.startsWith("Drop_")) {
            return dropListId.substring(5);  // Remove "Drop_" prefix
        }

        // For other types (ores, chests, etc.), no entity icon
        return null;
    }

    private String formatDropListName(String dropListId) {
        // Convert "Ore_Gold_Stone_Gathering_Breaking_DropList" to "Gold Ore (Stone)"
        // Convert "Zone1_Trork_Tier3" to "Trork [Z1] [T3]"
        // Convert "Drop_Crow" to "Crow"

        if (dropListId == null || dropListId.isEmpty()) {
            return dropListId;
        }

        // Handle zone-based drops (mob/entity drops)
        // Pattern: Zone1_EntityName_Tier3 -> "EntityName [Z1] [T3]"
        if (dropListId.startsWith("Zone")) {
            String[] parts = dropListId.split("_");
            if (parts.length >= 3) {
                String zone = parts[0].replace("Zone", "[Z") + "]";  // Zone1 -> [Z1]
                String tier = parts[parts.length - 1].replace("Tier", "[T") + "]";  // Tier3 -> [T3]

                // Entity name is everything between zone and tier
                StringBuilder entityName = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) {
                    if (i > 1) entityName.append(" ");
                    entityName.append(parts[i]);
                }

                return entityName.toString() + " " + zone + " " + tier;
            }
        }

        // Handle "Drop_EntityName" pattern
        if (dropListId.startsWith("Drop_")) {
            String entityName = dropListId.substring(5);  // Remove "Drop_"
            return entityName.replace("_", " ");
        }

        // Remove the common suffix
        String cleaned = dropListId.replace("_Gathering_Breaking_DropList", "")
                                   .replace("_Gathering_Soft_DropList", "");

        // Split by underscore
        String[] parts = cleaned.split("_");

        if (parts.length == 0) {
            return dropListId;
        }

        // Handle different patterns
        if (parts[0].equals("Ore")) {
            // Pattern: Ore_Material_RockType -> "Material Ore (RockType)"
            if (parts.length >= 3) {
                String material = parts[1];
                String rockType = parts[2];
                return material + " Ore (" + rockType + ")";
            } else if (parts.length == 2) {
                return parts[1] + " Ore";
            }
        } else if (parts[0].equals("Furniture")) {
            // Pattern: Furniture_Type_Chest_Size -> "Type Chest (Size)"
            if (parts.length >= 4 && parts[parts.length - 2].equals("Chest")) {
                String size = parts[parts.length - 1];
                StringBuilder typeName = new StringBuilder();
                for (int i = 1; i < parts.length - 2; i++) {
                    if (i > 1) typeName.append(" ");
                    typeName.append(parts[i]);
                }
                return typeName.toString() + " Chest (" + size + ")";
            }
        } else if (parts[0].equals("Plant")) {
            // Pattern: Plant_Crop_Apple_Block -> "Apple Crop"
            if (parts.length >= 3) {
                return parts[2] + " " + parts[1];
            }
        }

        // Fallback: just join with spaces
        return String.join(" ", parts);
    }

    private void buildRecipeDisplay(UICommandBuilder cmd, CraftingRecipe recipe, String rSel, Ref<EntityStore> ref) {
        String recipeId = recipe.getId();
        if (recipeId.contains(":")) recipeId = recipeId.substring(recipeId.indexOf(":") + 1);

        String benchInfo = "";
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            benchInfo = " [" + formatBenchName(bench.id) + " T" + bench.requiredTierLevel + "]";
        }

        cmd.set(rSel + " #RecipeTitle.TextSpans", Message.raw(recipeId + benchInfo));

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
            int requiredQty = input.getQuantity();

            cmd.appendInline(rSel + " #InputItems",
                    "Group { LayoutMode: Top; Padding: (Right: 6); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
            cmd.set(rSel + " #InputItems[" + j + "][0].ItemId", itemId);

            // Count items in inventory
            String labelText;
            if (player != null) {
                int inventoryCount = InventoryScanner.countItemInInventory(player, itemId);

                // Color code: green if enough, red if not enough
                String color = inventoryCount >= requiredQty ? "#00ff00" : "#ff0000";
                labelText = inventoryCount + "/" + requiredQty;

                // Update label with color
                cmd.set(rSel + " #InputItems[" + j + "][1].Text", labelText);
                cmd.set(rSel + " #InputItems[" + j + "][1].Style.TextColor", color);
            } else {
                // Fallback to old format if player not available
                labelText = "x" + requiredQty;
                cmd.set(rSel + " #InputItems[" + j + "][1].Text", labelText);
            }
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
                .addField(new KeyedCodec<>("PageChange", Codec.STRING), (d, v) -> d.pageChange = v, d -> d.pageChange)
                .addField(new KeyedCodec<>("ToggleMode", Codec.STRING), (d, v) -> d.toggleMode = v, d -> d.toggleMode)
                .addField(new KeyedCodec<>("PinAction", Codec.STRING), (d, v) -> d.pinAction = v, d -> d.pinAction)
                .addField(new KeyedCodec<>("ItemPageChange", Codec.STRING), (d, v) -> d.itemPageChange = v, d -> d.itemPageChange)
                .addField(new KeyedCodec<>("CategoryFilter", Codec.STRING), (d, v) -> d.categoryFilter = v, d -> d.categoryFilter)
                .addField(new KeyedCodec<>("ClearFilters", Codec.STRING), (d, v) -> d.clearFilters = v, d -> d.clearFilters)
                .build();

        private String searchQuery;
        private String selectedItem;
        private String activeSection;
        private String pageChange;
        private String toggleMode;
        private String pinAction;
        private String itemPageChange;
        private String categoryFilter;
        private String clearFilters;

        public GuiData() {}
    }
}