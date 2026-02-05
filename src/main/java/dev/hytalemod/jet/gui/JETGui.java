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
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.protocol.Color;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.model.ItemCategory;
import dev.hytalemod.jet.util.TooltipBuilder;
import dev.hytalemod.jet.storage.BrowserState;
import dev.hytalemod.jet.util.CategoryUtil;
import dev.hytalemod.jet.util.InventoryScanner;
import dev.hytalemod.jet.util.SearchParser;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.LinkedList;
import java.util.logging.Level;

/**
 * JET Browser GUI with item browsing, recipes, and uses.
 */
public class JETGui extends InteractiveCustomUIPage<JETGui.GuiData> {

    private static final int DEFAULT_ITEMS_PER_ROW = 7;
    private static final int DEFAULT_MAX_ROWS = 8;
    private static final int MIN_GRID_SIZE = 5;
    private static final int MAX_GRID_SIZE = 10;
    private static final int RECIPES_PER_PAGE = 3;

    private String searchQuery;
    private String selectedItem;
    private String activeSection; // "craft", "usage", or "drops"
    private int craftPage;
    private int usagePage;
    private int dropsPage;
    private int itemPage; // Pagination for item list
    private Set<ItemCategory> activeFilters; // Active category filters
    private String sortMode; // "category", "name_asc", "name_desc", "quality"
    private String modFilter; // Filter by asset pack/mod
    private int gridColumns; // Configurable grid columns
    private int gridRows; // Configurable grid rows
    private boolean showHiddenItems; // Show items with hidden quality
    private boolean showSalvagerRecipes; // Show salvager recipes
    private LinkedList<String> viewHistory; // Recently viewed items
    private boolean historyCollapsed; // Whether history bar is collapsed
    private static final int MAX_HISTORY_SIZE = 20;

    public JETGui(PlayerRef playerRef, CustomPageLifetime lifetime, String initialSearch, BrowserState saved) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.activeFilters = new HashSet<>();
        this.viewHistory = new LinkedList<>();
        this.historyCollapsed = false;

        if (saved != null) {
            applySavedState(saved);
        } else {
            this.searchQuery = initialSearch != null ? initialSearch : "";
            this.selectedItem = null;
            this.activeSection = "craft";
            this.craftPage = 0;
            this.usagePage = 0;
            this.dropsPage = 0;
            this.itemPage = 0;
            this.sortMode = "category";
            this.modFilter = "";
            this.gridColumns = DEFAULT_ITEMS_PER_ROW;
            this.gridRows = DEFAULT_MAX_ROWS;
            this.showHiddenItems = true; // Default to true so items like Storm Saplings are visible
            this.showSalvagerRecipes = true;
        }
    }

    private void applySavedState(BrowserState s) {
        this.searchQuery = s.searchQuery != null ? s.searchQuery : "";
        this.selectedItem = s.selectedItem;
        if (this.selectedItem != null && !JETPlugin.ITEMS.containsKey(this.selectedItem)) {
            this.selectedItem = null;
        }
        this.activeSection = s.activeSection != null && (s.activeSection.equals("craft") || s.activeSection.equals("usage") || s.activeSection.equals("drops"))
                ? s.activeSection : "craft";
        this.craftPage = Math.max(0, s.craftPage);
        this.usagePage = Math.max(0, s.usagePage);
        this.dropsPage = Math.max(0, s.dropsPage);
        this.itemPage = Math.max(0, s.itemPage);
        this.sortMode = s.sortMode != null ? s.sortMode : "category";
        this.modFilter = s.modFilter != null ? s.modFilter : "";
        this.gridColumns = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, s.gridColumns > 0 ? s.gridColumns : DEFAULT_ITEMS_PER_ROW));
        this.gridRows = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, s.gridRows > 0 ? s.gridRows : DEFAULT_MAX_ROWS));
        this.showHiddenItems = s.showHiddenItems;
        this.showSalvagerRecipes = s.showSalvagerRecipes;
        this.activeFilters.clear();
        if (s.activeFilters != null) {
            for (String name : s.activeFilters) {
                try {
                    this.activeFilters.add(ItemCategory.valueOf(name));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        // Restore history
        this.viewHistory.clear();
        if (s.viewHistory != null) {
            for (String itemId : s.viewHistory) {
                if (JETPlugin.ITEMS.containsKey(itemId)) {
                    this.viewHistory.add(itemId);
                }
            }
        }
        this.historyCollapsed = s.historyCollapsed;
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

        // Checkbox bindings for show hidden items and salvager recipes
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ShowHiddenItems #CheckBox",
                EventData.of("@ShowHiddenItems", "#ShowHiddenItems #CheckBox.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ShowSalvager #CheckBox",
                EventData.of("@ShowSalvagerRecipes", "#ShowSalvager #CheckBox.Value"),
                false
        );

        // Populate grid layout dropdown
        List<com.hypixel.hytale.server.core.ui.DropdownEntryInfo> gridLayouts = new ArrayList<>();
        for (int cols = 5; cols <= 10; cols++) {
            for (int rows = 5; rows <= 10; rows++) {
                String value = cols + "x" + rows;
                gridLayouts.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                        com.hypixel.hytale.server.core.ui.LocalizableString.fromString(value),
                        value
                ));
            }
        }
        cmd.set("#GridLayout.Entries", gridLayouts);
        cmd.set("#GridLayout.Value", gridColumns + "x" + gridRows);
        cmd.set("#SearchInput.Value", searchQuery != null ? searchQuery : "");

        // Set checkbox states to match defaults
        cmd.set("#ShowHiddenItems #CheckBox.Value", showHiddenItems);
        cmd.set("#ShowSalvager #CheckBox.Value", showSalvagerRecipes);

        // Grid layout dropdown binding
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#GridLayout",
                EventData.of("@GridLayout", "#GridLayout.Value"),
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

        // History bar buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleHistory", EventData.of("ToggleHistory", "toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearHistory", EventData.of("ClearHistory", "clear"), false);

        buildItemList(ref, cmd, events, store);
        buildRecipePanel(ref, cmd, events, store);
        buildHistoryBar(cmd, events);
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
                this.itemPage = 0;
                this.selectedItem = null;
                needsItemUpdate = true;
                needsRecipeUpdate = true;
            } catch (IllegalArgumentException e) {
                // Invalid category, ignore
            }
        }

        // Handle clear filters
        if (data.clearFilters != null && "true".equals(data.clearFilters)) {
            if (!activeFilters.isEmpty() || (modFilter != null && !modFilter.isEmpty())) {
                activeFilters.clear();
                modFilter = "";
                sortMode = "category";
                this.itemPage = 0;
                this.selectedItem = null;
                needsItemUpdate = true;
                needsRecipeUpdate = true;
            }
        }

        // Handle sort mode change
        if (data.sortMode != null && !data.sortMode.equals(this.sortMode)) {
            this.sortMode = data.sortMode;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        // Handle mod filter change
        if (data.modFilter != null && !data.modFilter.equals(this.modFilter)) {
            this.modFilter = data.modFilter;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        // Handle grid layout change
        if (data.gridLayout != null) {
            try {
                String[] parts = data.gridLayout.split("x");
                int newCols = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, Integer.parseInt(parts[0])));
                int newRows = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, Integer.parseInt(parts[1])));
                if (newCols != this.gridColumns || newRows != this.gridRows) {
                    this.gridColumns = newCols;
                    this.gridRows = newRows;
                    this.itemPage = 0;
                    needsItemUpdate = true;
                }
            } catch (Exception ignored) {}
        }

        // Handle show hidden items checkbox
        if (data.showHiddenItems != null && data.showHiddenItems != this.showHiddenItems) {
            this.showHiddenItems = data.showHiddenItems;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        // Handle show salvager recipes checkbox
        if (data.showSalvagerRecipes != null && data.showSalvagerRecipes != this.showSalvagerRecipes) {
            this.showSalvagerRecipes = data.showSalvagerRecipes;
            needsRecipeUpdate = true;
        }

        // Handle give item
        if (data.giveItem != null && !data.giveItem.isEmpty()) {
            giveItemToPlayer(ref, store, data.giveItem, false);
        }

        // Handle give item stack
        if (data.giveItemStack != null && !data.giveItemStack.isEmpty()) {
            giveItemToPlayer(ref, store, data.giveItemStack, true);
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
            addToHistory(data.selectedItem);
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

        // Handle history toggle
        if (data.toggleHistory != null && "toggle".equals(data.toggleHistory)) {
            historyCollapsed = !historyCollapsed;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildHistoryBar(cmd, events);
            sendUpdate(cmd, events, false);
        }

        // Handle clear history
        if (data.clearHistory != null && "clear".equals(data.clearHistory)) {
            viewHistory.clear();
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildHistoryBar(cmd, events);
            sendUpdate(cmd, events, false);
        }

        // Handle history item click
        if (data.historyItemClick != null && !data.historyItemClick.isEmpty()) {
            this.selectedItem = data.historyItemClick;
            this.craftPage = 0;
            this.usagePage = 0;
            this.activeSection = "craft";
            addToHistory(data.historyItemClick);
            needsRecipeUpdate = true;
        }

        // Handle opening drop source (MobInfoGui)
        if (data.openDropSource != null && !data.openDropSource.isEmpty()) {
            JETPlugin.getInstance().log(Level.INFO, "[JET] Opening MobInfoGui for drop source: " + data.openDropSource);

            BrowserState currentState = captureState();
            close();

            MobInfoGui mobInfoGui = new MobInfoGui(
                    playerRef,
                    CustomPageLifetime.CanDismiss,
                    data.openDropSource,
                    this.selectedItem,
                    currentState
            );

            com.hypixel.hytale.server.core.entity.entities.Player player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, mobInfoGui);
            }
            return;
        }

        if (needsItemUpdate || needsRecipeUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            if (needsItemUpdate) {
                buildItemList(ref, cmd, events, store);
            }
            if (needsRecipeUpdate) {
                buildRecipePanel(ref, cmd, events, store);
                buildHistoryBar(cmd, events);
            }

            sendUpdate(cmd, events, false);
        }

        maybeSaveState();
    }

    private BrowserState captureState() {
        BrowserState s = new BrowserState();
        s.searchQuery = searchQuery != null ? searchQuery : "";
        s.selectedItem = selectedItem;
        s.activeSection = activeSection != null ? activeSection : "craft";
        s.craftPage = craftPage;
        s.usagePage = usagePage;
        s.dropsPage = dropsPage;
        s.itemPage = itemPage;
        s.sortMode = sortMode != null ? sortMode : "category";
        s.modFilter = modFilter != null ? modFilter : "";
        s.gridColumns = gridColumns;
        s.gridRows = gridRows;
        s.showHiddenItems = showHiddenItems;
        s.showSalvagerRecipes = showSalvagerRecipes;
        s.activeFilters = new ArrayList<>();
        for (ItemCategory c : activeFilters) {
            s.activeFilters.add(c.name());
        }
        s.viewHistory = new ArrayList<>(viewHistory);
        s.historyCollapsed = historyCollapsed;
        return s;
    }

    private void maybeSaveState() {
        JETPlugin.getInstance().getBrowserStateStorage().saveState(playerRef.getUuid(), captureState());
    }

    private void addToHistory(String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        // Remove if already in history (to move it to front)
        viewHistory.remove(itemId);
        // Add to front
        viewHistory.addFirst(itemId);
        // Trim if too long
        while (viewHistory.size() > MAX_HISTORY_SIZE) {
            viewHistory.removeLast();
        }
    }

    private void buildHistoryBar(UICommandBuilder cmd, UIEventBuilder events) {
        // Show/hide content based on collapsed state
        cmd.set("#HistoryBar #HistoryContent.Visible", !historyCollapsed);
        cmd.set("#HistoryBar #ClearHistory.Visible", !historyCollapsed && !viewHistory.isEmpty());

        // Update history bar height based on collapsed state
        Anchor barAnchor = new Anchor();
        barAnchor.setHeight(Value.of(historyCollapsed ? 22 : 55));
        cmd.setObject("#HistoryBar.Anchor", barAnchor);

        // Build history items
        cmd.clear("#HistoryBar #HistoryItems");

        // Update toggle button text with arrow
        String arrow = historyCollapsed ? ">" : "v";
        cmd.set("#HistoryBar #ToggleHistory.Text", arrow);

        if (viewHistory.isEmpty()) {
            cmd.set("#HistoryBar #HistoryLabel.Text", "(empty)");
            return;
        }

        cmd.set("#HistoryBar #HistoryLabel.Text", "(" + viewHistory.size() + ")");

        String language = playerRef.getLanguage();
        int displayCount = Math.min(viewHistory.size(), 15); // Show up to 15 items

        for (int i = 0; i < displayCount; i++) {
            String itemId = viewHistory.get(i);
            Item item = JETPlugin.ITEMS.get(itemId);
            if (item == null) continue;

            // Create a clickable button with item icon
            cmd.appendInline("#HistoryBar #HistoryItems",
                    "Button #HistoryItem" + i + " { Padding: (Right: 3); Background: (Color: #00000000); Style: (Hovered: (Background: #ffffff30), Pressed: (Background: #ffffff50)); ItemIcon { Anchor: (Width: 28, Height: 28); Visible: true; } }");
            cmd.set("#HistoryBar #HistoryItems[" + i + "][0].ItemId", itemId);

            // Add tooltip with item name
            String displayName = getDisplayName(item, language);
            cmd.set("#HistoryBar #HistoryItems[" + i + "].TooltipTextSpans", Message.raw(displayName));

            // Add click event binding
            events.addEventBinding(CustomUIEventBindingType.Activating, "#HistoryBar #HistoryItems[" + i + "]", EventData.of("HistoryItemClick", itemId), false);
        }
    }

    private void buildItemList(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        List<Map.Entry<String, Item>> results = new ArrayList<>();

        // Get filtered items by mod if filter is active
        Set<String> modItemIds = null;
        if (modFilter != null && !modFilter.isEmpty()) {
            modItemIds = Item.getAssetMap().getKeysForPack(modFilter);
        }

        // Filter items by search, category, mod, and quality
        for (Map.Entry<String, Item> entry : JETPlugin.ITEMS.entrySet()) {
            Item item = entry.getValue();

            // Mod filter check
            if (modItemIds != null && !modItemIds.contains(entry.getKey())) {
                continue;
            }

            // Quality filter check
            if (!showHiddenItems) {
                try {
                    int qualityIndex = item.getQualityIndex();
                    ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
                    if (quality != null) {
                        if (quality.isHiddenFromSearch()) {
                            continue;
                        }
                        // Filter out Developer quality items
                        String qualityId = quality.getId();
                        if (qualityId != null && qualityId.equals("Developer")) {
                            continue;
                        }
                    }
                } catch (Exception ignored) {}
            }

            boolean matchesSearch = searchQuery.isEmpty() || matchesSearch(entry.getKey(), item);
            boolean matchesCategory = activeFilters.isEmpty() || matchesActiveFilters(item);

            if (matchesSearch && matchesCategory) {
                results.add(entry);
            }
        }

        // Apply sorting
        results.sort(getSortComparator());

        // Calculate pagination
        int maxItemsPerPage = gridColumns * gridRows;
        int totalItems = results.size();
        int totalPages = (int) Math.ceil((double) totalItems / maxItemsPerPage);
        if (totalPages == 0) totalPages = 1;

        // Clamp page to valid range
        if (itemPage >= totalPages) {
            itemPage = Math.max(0, totalPages - 1);
        }

        int startIndex = itemPage * maxItemsPerPage;
        int endIndex = Math.min(startIndex + maxItemsPerPage, totalItems);

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

            // Scale item card based on grid size - container is ~950px wide
            int cardWidth = (950 / gridColumns) - 10;
            int iconSize = Math.max(32, Math.min(64, cardWidth - 20));

            // Set button anchor with dynamic width
            com.hypixel.hytale.server.core.ui.Anchor buttonAnchor = new com.hypixel.hytale.server.core.ui.Anchor();
            buttonAnchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(cardWidth));
            buttonAnchor.setBottom(com.hypixel.hytale.server.core.ui.Value.of(10));
            buttonAnchor.setRight(com.hypixel.hytale.server.core.ui.Value.of(10));
            cmd.setObject(sel + " #ItemButton.Anchor", buttonAnchor);

            // Set icon anchor with dynamic size
            com.hypixel.hytale.server.core.ui.Anchor iconAnchor = new com.hypixel.hytale.server.core.ui.Anchor();
            iconAnchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(iconSize));
            iconAnchor.setHeight(com.hypixel.hytale.server.core.ui.Value.of(iconSize));
            iconAnchor.setBottom(com.hypixel.hytale.server.core.ui.Value.of(5));
            cmd.setObject(sel + " #ItemButton #ItemIcon.Anchor", iconAnchor);

            // Set quality background texture on the AssetImage wrapper
            try {
                int qualityIndex = item.getQualityIndex();
                ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
                if (quality != null) {
                    String slotTexture = quality.getSlotTexture();
                    if (slotTexture != null && !slotTexture.isEmpty()) {
                        cmd.set(sel + " #ItemButton #QualityBg.AssetPath", slotTexture);
                    }
                }
            } catch (Exception e) {
                // Ignore if quality system not available
            }

            cmd.set(sel + " #ItemButton #ItemIcon.ItemId", key);

            String displayName = getDisplayName(item, language);
            if (displayName.length() > 14) {
                displayName = displayName.substring(0, 12) + "...";
            }

            // Apply quality color to item name
            Message nameMessage = getColoredItemName(item, displayName);
            cmd.set(sel + " #ItemButton #ItemName.TextSpans", nameMessage);
            cmd.set(sel + " #ItemButton.TooltipTextSpans", buildTooltip(key, item, language));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #ItemButton", EventData.of("SelectedItem", key), false);
            events.addEventBinding(CustomUIEventBindingType.RightClicking, sel + " #ItemButton", EventData.of("GiveItem", key), false);

            col++;
            if (col >= gridColumns) {
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
        cmd.set("#FilterTool.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.TOOL) ? "#18E314" : "#FFFFFF");
        cmd.set("#FilterWeapon.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.WEAPON) ?   "#18E314" : "#FFFFFF");
        cmd.set("#FilterArmor.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.ARMOR) ?   "#18E314" : "#FFFFFF");
        cmd.set("#FilterConsumable.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.CONSUMABLE) ?   "#18E314" : "#FFFFFF");
        cmd.set("#FilterBlock.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.BLOCK) ?   "#18E314" : "#FFFFFF");
        cmd.set("#FilterCraftable.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.CRAFTABLE) ?   "#18E314" : "#FFFFFF");
        cmd.set("#FilterNonCraftable.Style.Default.LabelStyle.TextColor", activeFilters.contains(ItemCategory.NON_CRAFTABLE) ?   "#18E314" : "#FFFFFF");
    }


    private Comparator<Map.Entry<String, Item>> getSortComparator() {
        String language = playerRef.getLanguage();

        switch (sortMode) {
            case "name_asc":
                // Sort by translated name A-Z
                return Comparator.comparing(e -> getDisplayName(e.getValue(), language).toLowerCase());

            case "name_desc":
                // Sort by translated name Z-A
                return Comparator.comparing(e -> getDisplayName(e.getValue(), language).toLowerCase(), Comparator.reverseOrder());

            case "quality":
                // Sort by quality (higher quality first)
                return Comparator.comparingInt((Map.Entry<String, Item> e) -> {
                    try {
                        ItemQuality quality = ItemQuality.getAssetMap().getAsset(e.getValue().getQualityIndex());
                        return quality != null ? quality.getQualityValue() : 0;
                    } catch (Exception ex) {
                        return 0;
                    }
                }).reversed().thenComparing(e -> getDisplayName(e.getValue(), language).toLowerCase());

            case "category":
            default:
                // Sort by category, then by name
                return Comparator.comparing((Map.Entry<String, Item> e) -> {
                    // Get primary category for sorting
                    ItemCategory primaryCat = CategoryUtil.getPrimaryCategory(e.getValue());
                    return primaryCat != null ? primaryCat.name() : "ZZZZ"; // Put uncategorized at end
                }).thenComparing(e -> getDisplayName(e.getValue(), language).toLowerCase());
        }
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

        // Tag/Resource Type filtering with # prefix (e.g., #ore, #wood, #metal)
        if (query.startsWith("#")) {
            String tag = query.substring(1).toLowerCase(); // Remove the # prefix
            return matchesResourceTypeTag(item, tag) || hasComponent(item, tag);
        }

        // Use advanced search parser for @ (namespace) and - (exclusion) syntax
        // Pass translated name so search works on both item ID and display name
        SearchParser parser = new SearchParser(query);
        String translatedName = getDisplayName(item, language);
        return parser.matches(item, translatedName);
    }

    private boolean matchesResourceTypeTag(Item item, String tag) {
        if (item == null || tag == null || tag.isEmpty()) {
            return false;
        }

        try {
            // Try to get resource types from item
            Method getResourceTypesMethod = Item.class.getMethod("getResourceTypes");
            Object resourceTypesObj = getResourceTypesMethod.invoke(item);

            if (resourceTypesObj != null && resourceTypesObj.getClass().isArray()) {
                Object[] resourceTypes = (Object[]) resourceTypesObj;

                for (Object resourceTypeObj : resourceTypes) {
                    if (resourceTypeObj != null) {
                        // Try to get the ID from the resource type
                        try {
                            Method getIdMethod = resourceTypeObj.getClass().getMethod("getId");
                            Object idObj = getIdMethod.invoke(resourceTypeObj);

                            if (idObj != null) {
                                String resourceTypeId = idObj.toString().toLowerCase();
                                // Check if resource type ID contains the tag
                                if (resourceTypeId.contains(tag)) {
                                    return true;
                                }
                            }
                        } catch (Exception ignored) {}

                        // Also check string representation
                        String resourceTypeStr = resourceTypeObj.toString().toLowerCase();
                        if (resourceTypeStr.contains(tag)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
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

    private boolean isSalvagerRecipe(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }

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

    private void giveItemToPlayer(Ref<EntityStore> ref, Store<EntityStore> store, String itemId, boolean maxStack) {
        if (!ref.isValid()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        // Check if player is in Creative mode or is OP
        try {
            com.hypixel.hytale.protocol.GameMode gameMode = player.getGameMode();
            boolean isCreative = gameMode != null && gameMode.name().equals("Creative");

            boolean isOp = false;
            try {
                com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent =
                        store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    com.hypixel.hytale.server.core.permissions.PermissionsModule perms =
                            com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
                    java.util.Set<String> groups = perms.getGroupsForUser(uuidComponent.getUuid());
                    isOp = groups != null && groups.contains("OP");
                }
            } catch (Exception ignored) {}

            if (!isCreative && !isOp) {
                return;
            }

            Item item = JETPlugin.ITEMS.get(itemId);
            if (item == null) {
                return;
            }

            com.hypixel.hytale.server.core.inventory.ItemStack stack =
                    new com.hypixel.hytale.server.core.inventory.ItemStack(itemId);

            if (maxStack) {
                stack = stack.withQuantity(item.getMaxStack());
            }

            player.getInventory().getCombinedHotbarFirst().addItemStack(stack);
        } catch (Exception ignored) {}
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
        String displayName = getDisplayName(item, language);
        Message coloredName = getColoredItemName(item, displayName);
        cmd.set("#RecipePanel #SelectedName.TextSpans", coloredName);

        // Add item stats display
        buildItemStats(item, cmd, language);

        // Get recipe IDs from global maps
        List<String> craftRecipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> usageRecipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> dropSources = JETPlugin.getInstance().getDropListRegistry().getDropSourcesForItem(selectedItem);

        // Set recipe info label
        String recipeInfo = "Craft: " + craftRecipeIds.size() + " | Uses: " + usageRecipeIds.size() + " | Drops: " + dropSources.size();
        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw(recipeInfo));

        // Check if player can use give buttons (Creative or OP)
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean canGive = false;
        if (player != null) {
            com.hypixel.hytale.protocol.GameMode gameMode = player.getGameMode();
            boolean isCreative = gameMode != null && gameMode.name().equals("Creative");

            boolean isOp = false;
            try {
                com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent =
                        store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    com.hypixel.hytale.server.core.permissions.PermissionsModule perms =
                            com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
                    java.util.Set<String> groups = perms.getGroupsForUser(uuidComponent.getUuid());
                    isOp = groups != null && groups.contains("OP");
                }
            } catch (Exception ignored) {}

            canGive = isCreative || isOp;
        }
        cmd.set("#RecipePanel #SelectedItemBar #ItemHeader #GiveButtonGroup.Visible", canGive);

        // Update pin button text based on current pin state
        UUID playerUuid = playerRef.getUuid();
        boolean isPinned = JETPlugin.getInstance().getPinnedItemsStorage().isPinned(playerUuid, selectedItem);
        cmd.set("#RecipePanel #PinButton.Text", isPinned ? "[-]" : "[+]");


        // Add event bindings for give item buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #GiveItemButton",
                EventData.of("GiveItem", selectedItem), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #GiveItemStackButton",
                EventData.of("GiveItemStack", selectedItem), false);

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

        // Filter recipes based on salvager setting
        List<String> filteredRecipeIds = new ArrayList<>();
        for (String recipeId : recipeIds) {
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            if (!showSalvagerRecipes && isSalvagerRecipe(recipe)) {
                continue;
            }

            filteredRecipeIds.add(recipeId);
        }

        if (filteredRecipeIds.isEmpty()) {
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw("No recipes"));
            return;
        }

        int totalPages = (int) Math.ceil((double) filteredRecipeIds.size() / RECIPES_PER_PAGE);
        if (craftPage >= totalPages) craftPage = totalPages - 1;
        if (craftPage < 0) craftPage = 0;

        int start = craftPage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, filteredRecipeIds.size());

        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((craftPage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String recipeId = filteredRecipeIds.get(i);
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            int idx = i - start;
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, events, recipe, rSel, ref);
        }
    }

    private void buildUsageSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        // Filter recipes based on salvager setting
        List<String> filteredRecipeIds = new ArrayList<>();
        for (String recipeId : recipeIds) {
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            if (!showSalvagerRecipes && isSalvagerRecipe(recipe)) {
                continue;
            }

            filteredRecipeIds.add(recipeId);
        }

        if (filteredRecipeIds.isEmpty()) {
            cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw("No recipes"));
            return;
        }

        int totalPages = (int) Math.ceil((double) filteredRecipeIds.size() / RECIPES_PER_PAGE);
        if (usagePage >= totalPages) usagePage = totalPages - 1;
        if (usagePage < 0) usagePage = 0;

        int start = usagePage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, filteredRecipeIds.size());

        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw((usagePage + 1) + " / " + totalPages));

        for (int i = start; i < end; i++) {
            String recipeId = filteredRecipeIds.get(i);
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;

            int idx = i - start;
            cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_RecipeEntry.ui");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, events, recipe, rSel, ref);
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

            // Format the drop list ID for display
            String displayName = formatDropListName(dropListId);
            String dropType = getDropType(dropListId);
            String fullDropTitle = displayName + " (" + dropType + ")";

            // Use a Button wrapper so it can receive click events
            cmd.appendInline("#RecipePanel #RecipeListContainer #RecipeList",
                    "Button #DropEntry" + idx + " { " +
                    "LayoutMode: Top; " +
                    "Padding: (Full: 10, Bottom: 12); " +
                    "Background: (Color: #1e1e1e(0.9)); " +
                    "Anchor: (Bottom: 8); " +
                    "Style: (Hovered: (Background: #2a2a2a), Pressed: (Background: #3a3a3a)); " +
                    "Label #DropTitle { Style: (FontSize: 13, RenderBold: true, TextColor: #ffcc66); } " +
                    "Label #DropHint { Style: (FontSize: 11, TextColor: #888888); Padding: (Top: 4); } " +
                    "}");

            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";
            cmd.set(rSel + " #DropTitle.Text", fullDropTitle);
            cmd.set(rSel + " #DropHint.Text", "Click to view loot table");

            // Add click event to open MobInfoGui for this drop source
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rSel,
                    EventData.of("OpenDropSource", dropListId),
                    false
            );
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

    private void buildRecipeDisplay(UICommandBuilder cmd, UIEventBuilder events, CraftingRecipe recipe, String rSel, Ref<EntityStore> ref) {
        String recipeId = recipe.getId();
        if (recipeId.contains(":")) recipeId = recipeId.substring(recipeId.indexOf(":") + 1);

        String benchInfo = "";
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            benchInfo = " [" + formatBenchName(bench.id) + " T" + bench.requiredTierLevel + "]";
        }

        String fullTitle = recipeId + benchInfo;
        cmd.set(rSel + " #RecipeTitle.TextSpans", Message.raw(fullTitle));

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
                // Handle specific item - wrap in button for click handling
                cmd.appendInline(rSel + " #InputItems",
                        "Button { LayoutMode: Top; Padding: (Right: 6); Background: (Color: #00000000); Style: (Hovered: (Background: #ffffff30), Pressed: (Background: #ffffff50)); ItemIcon { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                cmd.set(rSel + " #InputItems[" + j + "][0].ItemId", itemId);

                // Add click event to navigate to this item
                events.addEventBinding(CustomUIEventBindingType.Activating, rSel + " #InputItems[" + j + "]", EventData.of("SelectedItem", itemId), false);

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
            } else if (resourceTypeId != null) {
                // Handle resource type (e.g., "any meat", "any wood")
                try {
                    ResourceType resourceType = ResourceType.getAssetMap().getAsset(resourceTypeId);
                    if (resourceType != null) {
                        cmd.appendInline(rSel + " #InputItems",
                                "Group { LayoutMode: Top; Padding: (Right: 6); AssetImage { Anchor: (Width: 32, Height: 32); Visible: true; } Label { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center); Padding: (Top: 2); } }");
                        cmd.set(rSel + " #InputItems[" + j + "][0].AssetPath", resourceType.getIcon());

                        // Count resource type items in inventory
                        String labelText;
                        if (player != null) {
                            int inventoryCount = InventoryScanner.countResourceTypeInInventory(player, resourceTypeId);

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
                } catch (Exception e) {
                    // Skip this resource type if there's an error
                }
            }
        }

        // Add output items
        MaterialQuantity[] outputs = recipe.getOutputs();
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

                    // Add click event to navigate to this item
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

    private String stripColorTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<color[^>]*>", "").replaceAll("</color>", "");
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

            if (quality != null && quality.getTextColor() != null) {
                Color color = quality.getTextColor();

                // Try different methods to get RGB values
                int r = 255, g = 255, b = 255;
                try {
                    // Try accessing fields directly via reflection
                    Class<?> colorClass = color.getClass();

                    try {
                        Method getRed = colorClass.getMethod("getRed");
                        Method getGreen = colorClass.getMethod("getGreen");
                        Method getBlue = colorClass.getMethod("getBlue");
                        r = (int) getRed.invoke(color);
                        g = (int) getGreen.invoke(color);
                        b = (int) getBlue.invoke(color);
                    } catch (Exception e) {
                        // Try alternative methods
                        try {
                            java.lang.reflect.Field rField = colorClass.getField("r");
                            java.lang.reflect.Field gField = colorClass.getField("g");
                            java.lang.reflect.Field bField = colorClass.getField("b");
                            Object rObj = rField.get(color);
                            Object gObj = gField.get(color);
                            Object bObj = bField.get(color);

                            // Handle both int and float
                            if (rObj instanceof Integer) {
                                r = (int) rObj;
                                g = (int) gObj;
                                b = (int) bObj;
                            } else if (rObj instanceof Float) {
                                r = (int) ((float) rObj * 255);
                                g = (int) ((float) gObj * 255);
                                b = (int) ((float) bObj * 255);
                            }
                        } catch (Exception ex) {
                            // Fallback: use toString and parse
                            String colorStr = color.toString();
                            if (colorStr.contains("#")) {
                                return Message.raw(displayName).color(colorStr);
                            }
                        }
                    }
                } catch (Exception ex) {
                    return Message.raw(displayName);
                }

                String hexColor = String.format("#%02x%02x%02x", r, g, b);
                return Message.raw(displayName).color(hexColor);
            }
        } catch (Exception ignored) {}

        // Fallback to white
        return Message.raw(displayName);
    }

    private Message buildTooltip(String itemId, Item item, String language) {
        TooltipBuilder tooltip = TooltipBuilder.create();

        // Title with colored quality
        Message coloredName = getColoredItemName(item, getDisplayName(item, language));
        tooltip.append(coloredName.bold(true)).nl();

        try {
            String descKey = item.getDescriptionTranslationKey();
            if (descKey != null && !descKey.isEmpty()) {
                String description = I18nModule.get().getMessage(language, descKey);
                if (description != null && !description.isEmpty()) {
                    description = stripColorTags(description);
                    if (description.length() > 150) {
                        description = description.substring(0, 147) + "...";
                    }
                    tooltip.nl();
                    tooltip.append(description, "#aaaaaa");
                    tooltip.nl();
                }
            }
        } catch (Exception ignored) {}

        // General Info
        tooltip.separator();
        if (item.getMaxDurability() > 0) {
            tooltip.line("Durability", String.format("%.0f", item.getMaxDurability()));
        }
        tooltip.line("Max Stack", String.valueOf(item.getMaxStack()));

        // Quality with color
        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String qualityName = I18nModule.get().getMessage(language, quality.getLocalizationKey());
                if (qualityName != null) {
                    tooltip.line("Quality", qualityName);
                }
            }
        } catch (Exception ignored) {}

        // Weapon Stats
        if (item.getWeapon() != null) {
            tooltip.separator();
            boolean hasWeaponStats = false;

            try {
                // Damage interactions
                Method getDamageMethod = item.getWeapon().getClass().getMethod("getDamageInteractions");
                Object damageInteractions = getDamageMethod.invoke(item.getWeapon());
                if (damageInteractions instanceof java.util.Map) {
                    java.util.Map<?, ?> damageMap = (java.util.Map<?, ?>) damageInteractions;
                    if (!damageMap.isEmpty()) {
                        hasWeaponStats = true;
                        for (java.util.Map.Entry<?, ?> entry : damageMap.entrySet()) {
                            String interactionName = entry.getKey().toString().replace("_", " ");
                            String value = entry.getValue().toString();
                            tooltip.line(interactionName, value);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Stat modifiers (e.g., attack speed)
            try {
                Method getStatModsMethod = item.getWeapon().getClass().getMethod("getStatModifiers");
                Object statMods = getStatModsMethod.invoke(item.getWeapon());
                if (statMods != null) {
                    // Handle Int2ObjectMap
                    Method int2ObjectEntrySetMethod = statMods.getClass().getMethod("int2ObjectEntrySet");
                    Object entrySet = int2ObjectEntrySetMethod.invoke(statMods);

                    if (entrySet instanceof java.util.Set) {
                        for (Object entryObj : (java.util.Set<?>) entrySet) {
                            try {
                                Method getIntKeyMethod = entryObj.getClass().getMethod("getIntKey");
                                int statTypeIndex = (Integer) getIntKeyMethod.invoke(entryObj);

                                // Get the stat type name
                                Class<?> entityStatTypeClass = Class.forName("com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType");
                                Method getAssetMapMethod = entityStatTypeClass.getMethod("getAssetMap");
                                Object assetMap = getAssetMapMethod.invoke(null);
                                Method getAssetMethod = assetMap.getClass().getMethod("getAsset", int.class);
                                Object entityStatType = getAssetMethod.invoke(assetMap, statTypeIndex);

                                if (entityStatType != null) {
                                    Method getIdMethod = entityStatType.getClass().getMethod("getId");
                                    String statId = (String) getIdMethod.invoke(entityStatType);

                                    Method getValueMethod = entryObj.getClass().getMethod("getValue");
                                    Object[] modifiers = (Object[]) getValueMethod.invoke(entryObj);

                                    for (Object modifier : modifiers) {
                                        String formatted = formatStaticModifier(modifier);
                                        tooltip.line(statId, "+" + formatted);
                                        hasWeaponStats = true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Armor Stats
        try {
            Object armor = item.getArmor();
            if (armor != null) {
                boolean hasArmorStats = false;

                // Damage Resistance
                Method getResMethod = armor.getClass().getMethod("getDamageResistanceValues");
                Object resistValues = getResMethod.invoke(armor);
                if (resistValues != null && resistValues instanceof java.util.Map) {
                    java.util.Map<?, ?> resMap = (java.util.Map<?, ?>) resistValues;
                    if (!resMap.isEmpty()) {
                        if (!hasArmorStats) {
                            tooltip.separator();
                            hasArmorStats = true;
                        }
                        for (java.util.Map.Entry<?, ?> entry : resMap.entrySet()) {
                            try {
                                Object damageCause = entry.getKey();
                                Method getIdMethod = damageCause.getClass().getMethod("getId");
                                String causeId = (String) getIdMethod.invoke(damageCause);

                                Object[] modifiers = (Object[]) entry.getValue();
                                for (Object modifier : modifiers) {
                                    String formatted = formatStaticModifier(modifier);
                                    tooltip.line(causeId + " Resistance", "+" + formatted);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Tool Stats
        if (item.getTool() != null) {
            tooltip.separator();
            try {
                Object[] specs = item.getTool().getSpecs();
                if (specs != null && specs.length > 0) {
                    for (Object spec : specs) {
                        Method getGatherType = spec.getClass().getMethod("getGatherType");
                        Method getPower = spec.getClass().getMethod("getPower");
                        String gatherType = (String) getGatherType.invoke(spec);
                        float power = (Float) getPower.invoke(spec);
                        tooltip.line(gatherType, String.format("%.2f", power));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Usage hint
        tooltip.separator();
        tooltip.append("Click to view recipes", "#55AAFF");

        return tooltip.build();
    }

    private void buildItemStats(Item item, UICommandBuilder cmd, String language) {
        if (item == null) {
            cmd.set("#RecipePanel #ItemStatsSection.Visible", false);
            return;
        }

        StringBuilder stats = new StringBuilder();
        boolean hasStats = false;

        // Weapon stats
        try {
            Object weaponObj = item.getClass().getMethod("getWeapon").invoke(item);
            if (weaponObj != null) {
                hasStats = true;
                stats.append("Weapon Stats:\n");

                // Try to get damage interactions
                try {
                    Method getDamageMethod = weaponObj.getClass().getMethod("getDamageInteractions");
                    Object damageInteractions = getDamageMethod.invoke(weaponObj);

                    if (damageInteractions instanceof java.util.Map) {
                        java.util.Map<?, ?> damageMap = (java.util.Map<?, ?>) damageInteractions;
                        for (java.util.Map.Entry<?, ?> entry : damageMap.entrySet()) {
                            String interactionName = entry.getKey().toString().replace("_", " ");
                            stats.append("  ").append(interactionName).append(": ").append(entry.getValue()).append("\n");
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Armor stats
        try {
            Object armorObj = item.getClass().getMethod("getArmor").invoke(item);
            if (armorObj != null) {
                if (hasStats) stats.append("\n");
                hasStats = true;
                stats.append("Armor Stats:\n");

                // Try to get damage resistance
                try {
                    Method getResistanceMethod = armorObj.getClass().getMethod("getDamageResistanceValues");
                    Object resistance = getResistanceMethod.invoke(armorObj);
                    if (resistance != null && resistance instanceof java.util.Map) {
                        java.util.Map<?, ?> resMap = (java.util.Map<?, ?>) resistance;
                        for (java.util.Map.Entry<?, ?> entry : resMap.entrySet()) {
                            try {
                                // Cast key to DamageCause and get ID
                                Class<?> damageCauseClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.damage.DamageCause");
                                Object damageCause = damageCauseClass.cast(entry.getKey());
                                Method getIdMethod = damageCauseClass.getMethod("getId");
                                String damageId = (String) getIdMethod.invoke(damageCause);

                                // The value is an array of StaticModifier
                                Object[] modifiers = (Object[]) entry.getValue();
                                for (Object modifier : modifiers) {
                                    stats.append("  ").append(damageId).append(" Resistance: +");
                                    stats.append(formatStaticModifier(modifier));
                                    stats.append("\n");
                                }
                            } catch (Exception e) {
                                // Fallback: just show the raw value
                                stats.append("  ").append(entry.getKey().toString()).append(": ").append(entry.getValue()).append("\n");
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Tool stats
        try {
            Object toolObj = item.getClass().getMethod("getTool").invoke(item);
            if (toolObj != null) {
                if (hasStats) stats.append("\n");
                hasStats = true;
                stats.append("Tool Stats:\n");

                try {
                    Method getSpecsMethod = toolObj.getClass().getMethod("getSpecs");
                    Object specs = getSpecsMethod.invoke(toolObj);

                    if (specs != null && specs.getClass().isArray()) {
                        Object[] specsArray = (Object[]) specs;
                        for (Object spec : specsArray) {
                            try {
                                Method getGatherTypeMethod = spec.getClass().getMethod("getGatherType");
                                Method getPowerMethod = spec.getClass().getMethod("getPower");
                                String gatherType = (String) getGatherTypeMethod.invoke(spec);
                                float power = (Float) getPowerMethod.invoke(spec);
                                stats.append("  ").append(gatherType).append(": ").append(String.format("%.2f", power)).append("\n");
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (hasStats) {
            cmd.set("#RecipePanel #ItemStatsSection #ItemStats.TextSpans", Message.raw(stats.toString()));
            cmd.set("#RecipePanel #ItemStatsSection.Visible", true);
        } else {
            cmd.set("#RecipePanel #ItemStatsSection.Visible", false);
        }
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

        // Process inputs - accept both items and resource types
        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                    result.add(input);
                }
            } else if (inputsObj instanceof List) {
                List<?> inputs = (List<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                            result.add(input);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                for (MaterialQuantity input : inputs) {
                    if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
                        result.add(input);
                    }
                }
            } else if (inputsObj instanceof Collection) {
                Collection<?> inputs = (Collection<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && (input.getItemId() != null || input.getResourceTypeId() != null)) {
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
                .addField(new KeyedCodec<>("SortMode", Codec.STRING), (d, v) -> d.sortMode = v, d -> d.sortMode)
                .addField(new KeyedCodec<>("ModFilter", Codec.STRING), (d, v) -> d.modFilter = v, d -> d.modFilter)
                .addField(new KeyedCodec<>("@GridLayout", Codec.STRING), (d, v) -> d.gridLayout = v, d -> d.gridLayout)
                .addField(new KeyedCodec<>("@ShowHiddenItems", Codec.BOOLEAN), (d, v) -> d.showHiddenItems = v, d -> d.showHiddenItems)
                .addField(new KeyedCodec<>("@ShowSalvagerRecipes", Codec.BOOLEAN), (d, v) -> d.showSalvagerRecipes = v, d -> d.showSalvagerRecipes)
                .addField(new KeyedCodec<>("GiveItem", Codec.STRING), (d, v) -> d.giveItem = v, d -> d.giveItem)
                .addField(new KeyedCodec<>("GiveItemStack", Codec.STRING), (d, v) -> d.giveItemStack = v, d -> d.giveItemStack)
                .addField(new KeyedCodec<>("ToggleHistory", Codec.STRING), (d, v) -> d.toggleHistory = v, d -> d.toggleHistory)
                .addField(new KeyedCodec<>("ClearHistory", Codec.STRING), (d, v) -> d.clearHistory = v, d -> d.clearHistory)
                .addField(new KeyedCodec<>("HistoryItemClick", Codec.STRING), (d, v) -> d.historyItemClick = v, d -> d.historyItemClick)
                .addField(new KeyedCodec<>("OpenDropSource", Codec.STRING), (d, v) -> d.openDropSource = v, d -> d.openDropSource)
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
        private String sortMode;
        private String modFilter;
        private String gridLayout;
        private Boolean showHiddenItems;
        private Boolean showSalvagerRecipes;
        private String giveItem;
        private String giveItemStack;
        private String toggleHistory;
        private String clearHistory;
        private String historyItemClick;
        private String openDropSource;

        public GuiData() {}
    }

    private String formatStaticModifier(Object modifier) {
        try {
            // Cast to StaticModifier directly
            com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier staticMod =
                (com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier) modifier;

            com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType calcType =
                staticMod.getCalculationType();
            float amount = staticMod.getAmount();

            switch (calcType) {
                case ADDITIVE:
                    return String.format("%.0f", amount);
                case MULTIPLICATIVE:
                    return String.format("%.0f%%", amount * 100.0f);
                default:
                    return String.valueOf(amount);
            }
        } catch (Exception e) {
            return "?";
        }
    }
}