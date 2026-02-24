package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.ItemResourceType;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.protocol.Color;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.hud.HudUtil;
import dev.hytalemod.jet.model.ItemCategory;
import dev.hytalemod.jet.util.TooltipBuilder;
import dev.hytalemod.jet.storage.BrowserState;
import dev.hytalemod.jet.util.CategoryUtil;
import dev.hytalemod.jet.util.InventoryScanner;
import dev.hytalemod.jet.registry.SetRegistry;
import dev.hytalemod.jet.util.SearchParser;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private String categoryFilter; // Active category filter display name ("All", "Tools", etc.)
    private String sortMode; // "category", "name_asc", "name_desc", "quality"
    private String modFilter; // Filter by asset pack/mod
    private int gridColumns; // Configurable grid columns
    private int gridRows; // Configurable grid rows
    private boolean showHiddenItems; // Show items with hidden quality
    private boolean showSalvagerRecipes; // Show salvager recipes
    private LinkedList<String> viewHistory; // Recently viewed items
    private boolean historyCollapsed; // Whether history bar is collapsed
    private boolean advancedInfoCollapsed; // Whether advanced info section is collapsed
    private boolean statsCollapsed; // Whether item stats section is collapsed
    private boolean setCollapsed; // Whether set section is collapsed
    private int calcQuantity; // Desired quantity for bulk material calculator
    private String calcSelectedIngredient = null; // Ingredient selected for inline recipe detail
    private Set<String> calcCollapsedNodes = new HashSet<>(); // Collapsed tree branches in calc
    private Map<String, Integer> calcRecipeChoices = new HashMap<>(); // Per-item recipe index for multi-recipe items
    private static final int MAX_HISTORY_SIZE = 20;

    public JETGui(PlayerRef playerRef, CustomPageLifetime lifetime, String initialSearch, BrowserState saved) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.viewHistory = new LinkedList<>();
        this.historyCollapsed = false;
        this.advancedInfoCollapsed = true; // Collapsed by default
        this.statsCollapsed = false; // Expanded by default

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
            this.sortMode = "name_asc";
            this.modFilter = "";
            this.categoryFilter = "All";
            this.setCollapsed = false;
            this.gridColumns = DEFAULT_ITEMS_PER_ROW;
            this.gridRows = DEFAULT_MAX_ROWS;
            this.showHiddenItems = true;
            this.showSalvagerRecipes = true;
            this.calcQuantity = 1;
        }
    }

    private void applySavedState(BrowserState s) {
        this.searchQuery = s.searchQuery != null ? s.searchQuery : "";
        this.selectedItem = s.selectedItem;
        if (this.selectedItem != null && !JETPlugin.ITEMS.containsKey(this.selectedItem)) {
            this.selectedItem = null;
        }
        this.activeSection = s.activeSection != null && (s.activeSection.equals("craft") || s.activeSection.equals("usage") || s.activeSection.equals("drops") || s.activeSection.equals("calc"))
                ? s.activeSection : "craft";
        this.calcQuantity = 1;
        this.craftPage = Math.max(0, s.craftPage);
        this.usagePage = Math.max(0, s.usagePage);
        this.dropsPage = Math.max(0, s.dropsPage);
        this.itemPage = Math.max(0, s.itemPage);
        this.sortMode = s.sortMode != null ? s.sortMode : "name_asc";
        this.modFilter = s.modFilter != null ? s.modFilter : "";
        this.categoryFilter = s.categoryFilter != null ? s.categoryFilter : "All";
        this.setCollapsed = s.setCollapsed;
        this.gridColumns = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, s.gridColumns > 0 ? s.gridColumns : DEFAULT_ITEMS_PER_ROW));
        this.gridRows = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, s.gridRows > 0 ? s.gridRows : DEFAULT_MAX_ROWS));
        this.showHiddenItems = s.showHiddenItems;
        this.showSalvagerRecipes = s.showSalvagerRecipes;
        this.viewHistory.clear();
        if (s.viewHistory != null) {
            for (String itemId : s.viewHistory) {
                if (JETPlugin.ITEMS.containsKey(itemId)) {
                    this.viewHistory.add(itemId);
                }
            }
        }
        this.historyCollapsed = s.historyCollapsed;
        this.advancedInfoCollapsed = s.advancedInfoCollapsed;
        this.statsCollapsed = s.statsCollapsed;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Gui.ui");

        // Background theme: tile 64x64 ItemIcons across the browser.
        // Single large ItemIcon always shows red X; tiling at small proven size works.
        dev.hytalemod.jet.config.JETConfig jetConfig = JETPlugin.getInstance().getConfig();
        String bg = jetConfig.backgroundImage;
        JETPlugin.getInstance().log(Level.INFO, "[JET BG] backgroundImage='" + bg + "'");
        if (bg != null && !bg.equals("none") && bg.startsWith("JET_Bg_")) {
            boolean exists = JETPlugin.ITEMS.containsKey(bg);
            JETPlugin.getInstance().log(Level.INFO, "[JET BG] item exists: " + exists);
            if (exists) {
                cmd.set("#DefaultBg.Visible", false); // hide default solid-color bg when a theme is active
                final int tileSize = 64;
                final int cols = (int) Math.ceil(1400.0 / tileSize); // 22
                final int rows = (int) Math.ceil(700.0 / tileSize);  // 11
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
                JETPlugin.getInstance().log(Level.INFO, "[JET BG] Tiled " + (cols * rows) + " icons @ " + tileSize + "px");
            } else {
                JETPlugin.getInstance().log(Level.WARNING, "[JET BG] Item '" + bg + "' not in JETPlugin.ITEMS");
            }
        }

        cmd.set("#ClearFilters #ClearFiltersIcon.ItemId", "JET_Icon_Clear");
        cmd.set("#ItemPagination #PrevItemPage #PrevItemPageIcon.ItemId", "JET_Icon_Arrow_Left");
        cmd.set("#ItemPagination #NextItemPage #NextItemPageIcon.ItemId", "JET_Icon_Arrow_Right");
        cmd.set("#HistoryBar #ToggleHistory #ToggleHistoryIcon.ItemId", "JET_Icon_Chevron_Down");
        cmd.set("#HistoryBar #ClearHistory #ClearHistoryIcon.ItemId", "JET_Icon_Clear");
        cmd.set("#RecipePanel #PinToHudButton #HudIcon.ItemId", "JET_Icon_Hud");
        cmd.set("#RecipePanel #AdvancedInfoSection #ToggleAdvancedInfo #ToggleAdvancedInfoIcon.ItemId", "JET_Icon_Arrow_Right");
        cmd.set("#RecipePanel #ItemStatsSection #ToggleStats #ToggleStatsIcon.ItemId", statsCollapsed ? "JET_Icon_Arrow_Right" : "JET_Icon_Chevron_Down");
        cmd.set("#RecipePanel #ItemSetSection #ToggleSet #ToggleSetIcon.ItemId", setCollapsed ? "JET_Icon_Arrow_Right" : "JET_Icon_Chevron_Down");
        cmd.set("#RecipePagination #PrevRecipe #PrevRecipeIcon.ItemId", "JET_Icon_Arrow_Left");
        cmd.set("#RecipePagination #NextRecipe #NextRecipeIcon.ItemId", "JET_Icon_Arrow_Right");
        cmd.set("#Title #SettingsButton #SettingsIcon.ItemId", "JET_Icon_Settings");

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );

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

        cmd.set("#ShowHiddenItems #CheckBox.Value", showHiddenItems);
        cmd.set("#ShowSalvager #CheckBox.Value", showSalvagerRecipes);

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#GridLayout",
                EventData.of("@GridLayout", "#GridLayout.Value"),
                false
        );

        // Sort mode dropdown
        List<com.hypixel.hytale.server.core.ui.DropdownEntryInfo> sortEntries = new ArrayList<>();
        sortEntries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                com.hypixel.hytale.server.core.ui.LocalizableString.fromString("Name"), "name_asc"));
        sortEntries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                com.hypixel.hytale.server.core.ui.LocalizableString.fromString("Quality"), "quality"));
        sortEntries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                com.hypixel.hytale.server.core.ui.LocalizableString.fromString("Craftable First"), "craftable"));
        cmd.set("#SortMode.Entries", sortEntries);
        cmd.set("#SortMode.Value", sortMode != null ? sortMode : "name_asc");

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SortMode",
                EventData.of("@SortMode", "#SortMode.Value"),
                false
        );

        // Mod filter dropdown
        LinkedHashMap<String, String> packLabels = JETPlugin.getInstance().getItemRegistry().getAvailablePackLabels();
        List<com.hypixel.hytale.server.core.ui.DropdownEntryInfo> modEntries = new ArrayList<>();
        modEntries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                com.hypixel.hytale.server.core.ui.LocalizableString.fromString("All Mods"), ""));
        for (Map.Entry<String, String> pe : packLabels.entrySet()) {
            modEntries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                    com.hypixel.hytale.server.core.ui.LocalizableString.fromString(pe.getValue()),
                    pe.getKey()));
        }
        cmd.set("#ModFilter.Entries", modEntries);
        cmd.set("#ModFilter.Value", modFilter != null ? modFilter : "");

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ModFilter",
                EventData.of("@ModFilter", "#ModFilter.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #ToggleModeButton",
                EventData.of("ToggleMode", "craft"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #UsesButton",
                EventData.of("ToggleMode", "usage"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #ObtainedFromButton",
                EventData.of("ToggleMode", "drops"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #CalcButton",
                EventData.of("ToggleMode", "calc"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #PinButton",
                EventData.of("PinAction", "toggle"),
                false
        );

        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipe", EventData.of("PageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipe", EventData.of("PageChange", "next"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevItemPage", EventData.of("ItemPageChange", "prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextItemPage", EventData.of("ItemPageChange", "next"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CategoryFilter", EventData.of("@CategoryFilter", "#CategoryFilter.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearFilters", EventData.of("ClearFilters", "true"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsButton", EventData.of("OpenSettings", "true"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleHistory", EventData.of("ToggleHistory", "toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #AdvancedInfoSection #ToggleAdvancedInfo", EventData.of("ToggleAdvancedInfo", "toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ItemStatsSection #ToggleStats", EventData.of("ToggleStats", "toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ItemSetSection #ToggleSet", EventData.of("ToggleSet", "toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearHistory", EventData.of("ClearHistory", "clear"), false);

        buildItemList(ref, cmd, events, store);
        buildRecipePanel(ref, cmd, events, store);
        buildHistoryBar(cmd, events);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, GuiData data) {
        super.handleDataEvent(ref, store, data);

        if (data.openSettings != null) {
            com.hypixel.hytale.server.core.entity.entities.Player player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            close();

            if (player != null) {
                java.util.concurrent.CompletableFuture.delayedExecutor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> com.hypixel.hytale.server.core.command.system.CommandManager.get().handleCommand(player, "jetconfig"));
            }
            return;
        }

        if (data.openDropSource != null && !data.openDropSource.isEmpty()) {
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

        boolean needsItemUpdate = false;
        boolean needsRecipeUpdate = false;

        if (data.searchQuery != null && !data.searchQuery.equals(this.searchQuery)) {
            this.searchQuery = data.searchQuery.trim();
            this.itemPage = 0; // Reset to first page on search
            needsItemUpdate = true;
            this.selectedItem = null;
            needsRecipeUpdate = true;
        }

        if (data.categoryFilter != null && !data.categoryFilter.equals(this.categoryFilter)) {
            this.categoryFilter = data.categoryFilter;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        if (data.clearFilters != null && "true".equals(data.clearFilters)) {
            categoryFilter = "All";
            modFilter = "";
            sortMode = "name_asc";
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        if (data.sortMode != null && !data.sortMode.equals(this.sortMode)) {
            this.sortMode = data.sortMode;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        if (data.modFilter != null && !data.modFilter.equals(this.modFilter)) {
            this.modFilter = data.modFilter;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

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

        if (data.showHiddenItems != null && data.showHiddenItems != this.showHiddenItems) {
            this.showHiddenItems = data.showHiddenItems;
            this.itemPage = 0;
            this.selectedItem = null;
            needsItemUpdate = true;
            needsRecipeUpdate = true;
        }

        if (data.showSalvagerRecipes != null && data.showSalvagerRecipes != this.showSalvagerRecipes) {
            this.showSalvagerRecipes = data.showSalvagerRecipes;
            needsRecipeUpdate = true;
        }

        if (JETPlugin.getInstance().getConfig().enableGiveButtons && data.giveItem != null && !data.giveItem.isEmpty()) {
            giveItemToPlayer(ref, store, data.giveItem, false);
        }

        if (JETPlugin.getInstance().getConfig().enableGiveButtons && data.giveItemStack != null && !data.giveItemStack.isEmpty()) {
            giveItemToPlayer(ref, store, data.giveItemStack, true);
        }

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
            boolean wasSelected = this.selectedItem != null;
            this.selectedItem = data.selectedItem;
            this.craftPage = 0;
            this.usagePage = 0;
            this.activeSection = "craft";
            this.calcQuantity = 1;
            this.calcSelectedIngredient = null;
            this.calcCollapsedNodes.clear();
            this.calcRecipeChoices.clear();
            needsRecipeUpdate = true;
            // Rebuild item grid so card widths adjust for the narrowed ItemSection
            if (!wasSelected) needsItemUpdate = true;
            addToHistory(data.selectedItem);
        }

        if (data.toggleMode != null && !data.toggleMode.isEmpty()) {
            if ("craft".equals(data.toggleMode) || "usage".equals(data.toggleMode) || "drops".equals(data.toggleMode) || "calc".equals(data.toggleMode)) {
                this.activeSection = data.toggleMode;
                this.craftPage = 0;
                this.usagePage = 0;
                this.dropsPage = 0;
                this.calcSelectedIngredient = null;
                this.calcCollapsedNodes.clear();
                this.calcRecipeChoices.clear();
                needsRecipeUpdate = true;
            }
        }

        if (data.calcQuantityChange != null) {
            if ("inc".equals(data.calcQuantityChange)) {
                calcQuantity = Math.min(9999, calcQuantity + 1);
                needsRecipeUpdate = true;
            } else if ("inc10".equals(data.calcQuantityChange)) {
                calcQuantity = Math.min(9999, calcQuantity + 10);
                needsRecipeUpdate = true;
            } else if ("dec".equals(data.calcQuantityChange)) {
                calcQuantity = Math.max(1, calcQuantity - 1);
                needsRecipeUpdate = true;
            } else if ("dec10".equals(data.calcQuantityChange)) {
                calcQuantity = Math.max(1, calcQuantity - 10);
                needsRecipeUpdate = true;
            }
        }

        if (data.calcIngredientSelect != null) {
            String val = data.calcIngredientSelect;
            if (val.startsWith("tree:")) {
                // Expand/collapse tree node
                String nodeId = val.substring(5);
                if (calcCollapsedNodes.contains(nodeId)) {
                    calcCollapsedNodes.remove(nodeId);
                } else {
                    calcCollapsedNodes.add(nodeId);
                }
                needsRecipeUpdate = true;
            } else if (val.startsWith("cycle:")) {
                // Cycle recipe for a multi-recipe item
                String cycleItemId = val.substring(6);
                List<String> recipes = JETPlugin.ITEM_TO_RECIPES.getOrDefault(cycleItemId, Collections.emptyList());
                if (recipes.size() > 1) {
                    int current = calcRecipeChoices.getOrDefault(cycleItemId, 0);
                    calcRecipeChoices.put(cycleItemId, (current + 1) % recipes.size());
                }
                needsRecipeUpdate = true;
            } else {
                // Legacy ingredient select (toggle)
                if (val.equals(this.calcSelectedIngredient)) {
                    this.calcSelectedIngredient = null;
                } else {
                    this.calcSelectedIngredient = val;
                }
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

        if (data.toggleHistory != null && "toggle".equals(data.toggleHistory)) {
            historyCollapsed = !historyCollapsed;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildHistoryBar(cmd, events);
            sendUpdate(cmd, events, false);
        }

        if (data.toggleAdvancedInfo != null && "toggle".equals(data.toggleAdvancedInfo)) {
            advancedInfoCollapsed = !advancedInfoCollapsed;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildRecipePanel(ref, cmd, events, store);
            sendUpdate(cmd, events, false);
        }

        if (data.toggleStats != null && "toggle".equals(data.toggleStats)) {
            statsCollapsed = !statsCollapsed;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildRecipePanel(ref, cmd, events, store);
            sendUpdate(cmd, events, false);
        }

        if (data.toggleSet != null && "toggle".equals(data.toggleSet)) {
            setCollapsed = !setCollapsed;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildRecipePanel(ref, cmd, events, store);
            sendUpdate(cmd, events, false);
        }

        if (data.clearHistory != null && "clear".equals(data.clearHistory)) {
            viewHistory.clear();
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildHistoryBar(cmd, events);
            sendUpdate(cmd, events, false);
        }

        if (data.historyItemClick != null && !data.historyItemClick.isEmpty()) {
            boolean wasSelected = this.selectedItem != null;
            this.selectedItem = data.historyItemClick;
            this.craftPage = 0;
            this.usagePage = 0;
            this.activeSection = "craft";
            addToHistory(data.historyItemClick);
            needsRecipeUpdate = true;
            if (!wasSelected) needsItemUpdate = true;
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

        // Pin-to-HUD feature adapted from BIV (BetterItemViewer)
        if (data.pinToHud != null && "toggle".equals(data.pinToHud) && this.selectedItem != null) {
            // Get the first crafting recipe for this item to pin to HUD
            List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            if (!recipeIds.isEmpty()) {
                String recipeId = recipeIds.get(0); // Pin first recipe

                RecipeHudComponent component = store.ensureAndGetComponent(ref, RecipeHudComponent.getComponentType());
                component.toggleRecipe(recipeId);

                if (component.hasRecipe(recipeId)) {
                    playerRef.sendMessage(Message.raw("[JET] Pinned recipe to HUD").color("#55FF55"));
                    if (component.pinnedRecipes.size() > 1) {
                        playerRef.sendMessage(Message.raw("[JET] Tip: Install MultipleHUD for multiple pinned recipes to display correctly").color("#AAAAAA"));
                    }
                } else {
                    playerRef.sendMessage(Message.raw("[JET] Unpinned recipe from HUD").color("#FFAA00"));
                }

                // Update HUD deferred so it runs outside the current event handler context
                World world = ((EntityStore) store.getExternalData()).getWorld();
                CompletableFuture.runAsync(() -> HudUtil.updateHud(ref), world);
                needsRecipeUpdate = true;
            }
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
        s.categoryFilter = categoryFilter != null ? categoryFilter : "All";
        s.setCollapsed = setCollapsed;
        s.gridColumns = gridColumns;
        s.gridRows = gridRows;
        s.showHiddenItems = showHiddenItems;
        s.showSalvagerRecipes = showSalvagerRecipes;
        s.viewHistory = new ArrayList<>(viewHistory);
        s.historyCollapsed = historyCollapsed;
        s.advancedInfoCollapsed = advancedInfoCollapsed;
        s.statsCollapsed = statsCollapsed;
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

        // Update toggle button icon with chevron direction
        String chevronItem = historyCollapsed ? "JET_Icon_Arrow_Right" : "JET_Icon_Chevron_Down";
        cmd.set("#HistoryBar #ToggleHistory #ToggleHistoryIcon.ItemId", chevronItem);

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

            // Add tooltip with colored name and basic info
            cmd.set("#HistoryBar #HistoryItems[" + i + "].TooltipTextSpans", buildIngredientTooltip(itemId, item, 0, language));

            // Add click event binding
            events.addEventBinding(CustomUIEventBindingType.Activating, "#HistoryBar #HistoryItems[" + i + "]", EventData.of("HistoryItemClick", itemId), false);
        }
    }

    private void buildItemList(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        List<Map.Entry<String, Item>> results = new ArrayList<>();

        // Pre-compute allowed item IDs for the active pack filter
        Set<String> modFilterItems = JETPlugin.getInstance().getItemRegistry().getItemIdsForPack(modFilter);

        // Pre-compute inventory cache if "Can Craft" filter is active
        boolean isCanCraftFilter = "Can Craft".equals(categoryFilter);
        Player canCraftPlayer = null;
        Map<String, Integer> inventoryCache = null;
        if (isCanCraftFilter) {
            canCraftPlayer = store.getComponent(ref, Player.getComponentType());
            if (canCraftPlayer != null) {
                inventoryCache = InventoryScanner.getAllItemCounts(canCraftPlayer);
            }
        }
        ItemCategory singleCategory = displayNameToCategory(categoryFilter);

        // Filter items by search, category, mod, and quality
        for (Map.Entry<String, Item> entry : JETPlugin.ITEMS.entrySet()) {
            Item item = entry.getValue();

            // Mod/pack filter check
            if (modFilterItems != null && !modFilterItems.contains(entry.getKey())) {
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
            boolean matchesCategory;
            if (isCanCraftFilter) {
                matchesCategory = canCraftWithInventory(item, canCraftPlayer, inventoryCache);
            } else if (singleCategory != null) {
                matchesCategory = CategoryUtil.matchesCategory(item, singleCategory);
            } else {
                matchesCategory = true; // "All" or unknown → no filter
            }

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

            // Scale item card based on grid size.
            // When recipe panel is visible, ItemSection shrinks to 880px (~850px usable after padding+scrollbar).
            // When hidden, ItemSection is 1360px (~1330px usable) — fill the full width.
            int availableWidth = (selectedItem != null) ? 850 : 1330;
            int cardWidth = availableWidth / gridColumns;
            int iconSize = Math.max(32, Math.min(64, cardWidth - 20));

            // Set button anchor with dynamic width
            com.hypixel.hytale.server.core.ui.Anchor buttonAnchor = new com.hypixel.hytale.server.core.ui.Anchor();
            buttonAnchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(cardWidth));
            buttonAnchor.setBottom(com.hypixel.hytale.server.core.ui.Value.of(10));
            cmd.setObject(sel + " #ItemButton.Anchor", buttonAnchor);

            // Set icon and quality background anchors with dynamic size
            com.hypixel.hytale.server.core.ui.Anchor qualityAnchor = new com.hypixel.hytale.server.core.ui.Anchor();
            qualityAnchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(iconSize));
            qualityAnchor.setHeight(com.hypixel.hytale.server.core.ui.Value.of(iconSize));
            qualityAnchor.setBottom(com.hypixel.hytale.server.core.ui.Value.of(5));
            cmd.setObject(sel + " #ItemButton #QualityBg.Anchor", qualityAnchor);

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

        // Update category filter dropdown
        List<com.hypixel.hytale.server.core.ui.DropdownEntryInfo> categoryEntries = new ArrayList<>();
        for (String name : new String[]{"All", "Tools", "Weapons", "Armor", "Consumables", "Blocks", "Craftable", "Non-Craftable", "Can Craft"}) {
            categoryEntries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                    com.hypixel.hytale.server.core.ui.LocalizableString.fromString(name), name));
        }
        cmd.set("#CategoryFilter.Entries", categoryEntries);
        cmd.set("#CategoryFilter.Value", categoryFilter != null ? categoryFilter : "All");
        cmd.set("#SortMode.Value", sortMode != null ? sortMode : "name_asc");
        cmd.set("#ModFilter.Value", modFilter != null ? modFilter : "");
    }


    private Comparator<Map.Entry<String, Item>> getSortComparator() {
        String language = playerRef.getLanguage();

        switch (sortMode) {
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

            case "craftable":
                // Craftable items first, then non-craftable, alphabetical within each group
                return Comparator.comparingInt((Map.Entry<String, Item> e) -> {
                    List<CraftingRecipe> recipes = JETPlugin.getInstance().getRecipeRegistry().getCraftingRecipes(e.getKey());
                    return (recipes != null && !recipes.isEmpty()) ? 0 : 1;
                }).thenComparing(e -> getDisplayName(e.getValue(), language).toLowerCase());

            case "name_asc":
            default:
                // Sort by translated name A-Z
                return Comparator.comparing(e -> getDisplayName(e.getValue(), language).toLowerCase());
        }
    }

    private static ItemCategory displayNameToCategory(String name) {
        if (name == null) return null;
        switch (name) {
            case "Tools": return ItemCategory.TOOL;
            case "Weapons": return ItemCategory.WEAPON;
            case "Armor": return ItemCategory.ARMOR;
            case "Consumables": return ItemCategory.CONSUMABLE;
            case "Blocks": return ItemCategory.BLOCK;
            case "Craftable": return ItemCategory.CRAFTABLE;
            case "Non-Craftable": return ItemCategory.NON_CRAFTABLE;
            default: return null;
        }
    }

    private boolean canCraftWithInventory(Item item, Player player, Map<String, Integer> inventoryCache) {
        if (player == null || inventoryCache == null) return false;
        List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.get(item.getId());
        if (recipeIds == null || recipeIds.isEmpty()) return false;
        for (String recipeId : recipeIds) {
            CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeId);
            if (recipe == null) continue;
            List<MaterialQuantity> inputs = getRecipeInputs(recipe);
            if (inputs.isEmpty()) continue;
            boolean canCraft = true;
            for (MaterialQuantity input : inputs) {
                int needed = input.getQuantity();
                int have;
                if (input.getItemId() != null) {
                    have = inventoryCache.getOrDefault(input.getItemId(), 0);
                } else if (input.getResourceTypeId() != null) {
                    have = InventoryScanner.countResourceTypeInInventory(player, input.getResourceTypeId());
                } else {
                    have = 0;
                }
                if (have < needed) { canCraft = false; break; }
            }
            if (canCraft) return true;
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

        // Use global ITEMS map
        Item item = JETPlugin.ITEMS.get(selectedItem);
        String language = playerRef.getLanguage();

        cmd.set("#RecipePanel #SelectedIcon.ItemId", selectedItem);
        String displayName = getDisplayName(item, language);
        Message coloredName = getColoredItemName(item, displayName);
        cmd.set("#RecipePanel #SelectedName.TextSpans", coloredName);
        cmd.set("#RecipePanel #ItemId.Text", selectedItem);

        // Add item stats display
        buildItemStats(item, cmd, language);

        // Add advanced info display
        buildAdvancedInfo(item, cmd, events, language);

        // Add item set display
        buildSetSection(cmd, events, language);

        // Get recipe IDs from global maps
        List<String> craftRecipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> usageRecipeIds = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(selectedItem, Collections.emptyList());
        List<String> dropSources = JETPlugin.getInstance().getDropListRegistry().getDropSourcesForItem(selectedItem);

        // Set recipe info label
        String recipeInfo = "Craft: " + craftRecipeIds.size() + " | Uses: " + usageRecipeIds.size() + " | Drops: " + dropSources.size();
        cmd.set("#RecipePanel #RecipeInfo.TextSpans", Message.raw(recipeInfo));

        // Check if player can use give buttons (Creative or OP, and config enabled)
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean canGive = false;
        if (player != null && JETPlugin.getInstance().getConfig().enableGiveButtons) {
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
        cmd.set("#RecipePanel #PinButton #PinIcon.ItemId", isPinned ? "JET_Icon_Unpin" : "JET_Icon_Pin");


        // Add event bindings for give item buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #GiveItemButton",
                EventData.of("GiveItem", selectedItem), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #GiveItemStackButton",
                EventData.of("GiveItemStack", selectedItem), false);

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
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipePanel #PinToHudButton",
                EventData.of("PinToHud", "toggle"),
                false
        );
    }

    private void buildCraftSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds, boolean noSource) {
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
            appendEmptyMessage(cmd, noSource ? "Uncraftable — no source found" : "No crafting recipe", noSource);
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
            cmd.set("#InputIcon.ItemId", "JET_Icon_Input");
            cmd.set("#OutputIcon.ItemId", "JET_Icon_Output");
            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, events, recipe, rSel, ref);
        }
    }

    private void buildUsageSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> recipeIds, boolean noSource) {
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
            appendEmptyMessage(cmd, noSource ? "Uncraftable — no source found" : "Not used in any recipe", noSource);
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
            cmd.set("#InputIcon.ItemId", "JET_Icon_Input");
            cmd.set("#OutputIcon.ItemId", "JET_Icon_Output");

            String rSel = "#RecipePanel #RecipeListContainer #RecipeList[" + idx + "]";

            buildRecipeDisplay(cmd, events, recipe, rSel, ref);
        }
    }

    private void buildDropsSection(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, List<String> dropListIds, boolean noSource) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");

        if (dropListIds.isEmpty()) {
            appendEmptyMessage(cmd, noSource ? "Uncraftable — no source found" : "Not dropped by any mob", noSource);
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

    private void appendEmptyMessage(UICommandBuilder cmd, String message, boolean highlight) {
        String color = highlight ? "#ff9966" : "#888888";
        cmd.set("#RecipePanel #PageInfo.TextSpans", Message.raw(""));
        cmd.appendInline("#RecipePanel #RecipeListContainer #RecipeList",
                "Group #NoSource { Padding: (Top: 24, Bottom: 8); " +
                "Label { Style: (FontSize: 12, TextColor: " + color + ", HorizontalAlignment: Center); } }");
        cmd.set("#RecipePanel #RecipeListContainer #RecipeList[0][0].Text", message);
    }

    private void buildRecipeDisplay(UICommandBuilder cmd, UIEventBuilder events, CraftingRecipe recipe, String rSel, Ref<EntityStore> ref) {
        // Resolve recipe title from primary output item name
//        applyBgToRecipeEntry(cmd, rSel);
        String recipeTitle = null;
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs != null && outputs.length > 0 && outputs[0] != null && outputs[0].getItemId() != null) {
            Item outputItem = JETPlugin.ITEMS.get(outputs[0].getItemId());
            if (outputItem != null) {
                recipeTitle = getDisplayName(outputItem, playerRef.getLanguage());
            }
        }
        if (recipeTitle == null) {
            // Fallback to recipe ID
            recipeTitle = recipe.getId();
            if (recipeTitle.contains(":")) recipeTitle = recipeTitle.substring(recipeTitle.indexOf(":") + 1);
            recipeTitle = recipeTitle.replace("_", " ");
        }

        String benchInfo = "";
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            benchInfo = " [" + formatBenchName(bench.id) + " T" + bench.requiredTierLevel + "]";
        }

        String fullTitle = recipeTitle + benchInfo;
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

                // Tooltip for ingredient
                Item inputItem = JETPlugin.ITEMS.get(itemId);
                if (inputItem != null) {
                    cmd.set(rSel + " #InputItems[" + j + "].TooltipTextSpans", buildIngredientTooltip(itemId, inputItem, requiredQty, playerRef.getLanguage()));
                }

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

                        // Tooltip for resource type
                        String rtName = resourceTypeId.replace("_", " ");
                        cmd.set(rSel + " #InputItems[" + j + "].TooltipTextSpans",
                                Message.raw("Any " + rtName).color("#FFAA00"));

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

                    // Tooltip for output
                    Item outputItem = JETPlugin.ITEMS.get(outputItemId);
                    if (outputItem != null) {
                        Message outputName = getColoredItemName(outputItem, getDisplayName(outputItem, playerRef.getLanguage()));
                        cmd.set(rSel + " #OutputItems[" + j + "].TooltipTextSpans", outputName);
                    }

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
        // Strip color tags
        text = text.replaceAll("<color[^>]*>", "").replaceAll("</color>", "");
        // Replace <item is="ItemId"/> with the item's display name
        java.util.regex.Matcher itemMatcher = java.util.regex.Pattern.compile("<item\\s+is=\"([^\"]+)\"\\s*/>").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (itemMatcher.find()) {
            String itemId = itemMatcher.group(1);
            Item item = JETPlugin.ITEMS.get(itemId);
            String name = item != null ? getDisplayName(item, playerRef.getLanguage()) : itemId.replace("_", " ");
            itemMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(name));
        }
        itemMatcher.appendTail(sb);
        text = sb.toString();
        // Strip any remaining XML-like tags
        text = text.replaceAll("<[^>]+/>", "").replaceAll("<[^>]+>", "");
        return text;
    }

    private String wordWrap(String text, int maxChars) {
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (result.length() > 0) result.append("\n");
            if (line.length() <= maxChars) {
                result.append(line);
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) {
                if (current.length() > 0 && current.length() + 1 + word.length() > maxChars) {
                    result.append(current).append("\n");
                    current = new StringBuilder(word);
                } else {
                    if (current.length() > 0) current.append(" ");
                    current.append(word);
                }
            }
            if (current.length() > 0) result.append(current);
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


    private String resolveQualityColor(ItemQuality quality, String qualityName) {
        try {
            if (quality.getTextColor() != null) {
                Color color = quality.getTextColor();
                try {
                    Method getRed = color.getClass().getMethod("getRed");
                    Method getGreen = color.getClass().getMethod("getGreen");
                    Method getBlue = color.getClass().getMethod("getBlue");
                    int r = (int) getRed.invoke(color);
                    int g = (int) getGreen.invoke(color);
                    int b = (int) getBlue.invoke(color);
                    if (r != 255 || g != 255 || b != 255) {
                        return String.format("#%02x%02x%02x", r, g, b);
                    }
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field rField = color.getClass().getField("r");
                        Object rObj = rField.get(color);
                        java.lang.reflect.Field gField = color.getClass().getField("g");
                        Object gObj = gField.get(color);
                        java.lang.reflect.Field bField = color.getClass().getField("b");
                        Object bObj = bField.get(color);
                        int r, g, b;
                        if (rObj instanceof Float) {
                            r = (int) ((float) rObj * 255);
                            g = (int) ((float) gObj * 255);
                            b = (int) ((float) bObj * 255);
                        } else {
                            r = ((Number) rObj).intValue();
                            g = ((Number) gObj).intValue();
                            b = ((Number) bObj).intValue();
                        }
                        if (r != 255 || g != 255 || b != 255) {
                            return String.format("#%02x%02x%02x", r, g, b);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

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
        TooltipBuilder tooltip = TooltipBuilder.create();

        // Title with colored quality
        Message coloredName = getColoredItemName(item, getDisplayName(item, language));
        tooltip.append(coloredName.bold(true)).nl();

        try {
            String descKey = item.getDescriptionTranslationKey();
            if (descKey != null && !descKey.isEmpty()) {
                String description = I18nModule.get().getMessage(language, descKey);
                if (description != null && !description.isEmpty()) {
                    description = wordWrap(stripColorTags(description), 50);
                    tooltip.nl();
                    tooltip.append(description, "#aaaaaa");
                    tooltip.nl();
                }
            }
        } catch (Exception ignored) {}

        // Ore biome spawn information
        List<String> biomeSpawns = JETPlugin.getInstance().getDropListRegistry().getOreBiomeSpawns(itemId);
        if (!biomeSpawns.isEmpty()) {
            tooltip.separator();
            tooltip.append("Ore Spawn Locations", "#ffaa00").nl();
            for (String biome : biomeSpawns) {
                tooltip.append("  ⛏ " + biome, "#88ff88").nl();
            }
        }

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

        // Weapon Stats - read damage from JSON (getDamageInteractions returns empty)
        if (item.getWeapon() != null) {
            try {
                Map<String, int[]> attackDamage = readWeaponDamageFromJson(itemId);
                if (!attackDamage.isEmpty()) {
                    tooltip.separator();
                    for (Map.Entry<String, int[]> entry : attackDamage.entrySet()) {
                        String attackName = entry.getKey().replace("_", " ");
                        int[] dmg = entry.getValue();
                        String dmgStr = (dmg[0] == dmg[1]) ? String.valueOf(dmg[0]) : dmg[0] + "-" + dmg[1];
                        tooltip.line(attackName, dmgStr + " dmg");
                    }
                }
            } catch (Exception ignored) {}

            // Stat modifiers (e.g., attack speed)
            try {
                Method getStatModsMethod = item.getWeapon().getClass().getMethod("getStatModifiers");
                Object statMods = getStatModsMethod.invoke(item.getWeapon());
                if (statMods != null) {
                    Method int2ObjectEntrySetMethod = statMods.getClass().getMethod("int2ObjectEntrySet");
                    Object entrySet = int2ObjectEntrySetMethod.invoke(statMods);

                    if (entrySet instanceof java.util.Set) {
                        for (Object entryObj : (java.util.Set<?>) entrySet) {
                            try {
                                Method getIntKeyMethod = entryObj.getClass().getMethod("getIntKey");
                                int statTypeIndex = (Integer) getIntKeyMethod.invoke(entryObj);

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

        // Recipe/usage counts
        List<String> craftRecipes = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());
        List<String> usageRecipes = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(itemId, Collections.emptyList());
        if (!craftRecipes.isEmpty() || !usageRecipes.isEmpty()) {
            tooltip.separator();
            if (!craftRecipes.isEmpty()) {
                tooltip.append(craftRecipes.size() + " recipe" + (craftRecipes.size() > 1 ? "s" : ""), "#55FF55").nl();
            }
            if (!usageRecipes.isEmpty()) {
                tooltip.append("Used in " + usageRecipes.size() + " recipe" + (usageRecipes.size() > 1 ? "s" : ""), "#FFAA00").nl();
            }
        }

        // Set info
        String setName = JETPlugin.getInstance().getSetRegistry().getSetForItem(itemId);
        if (setName != null) {
            int setSize = JETPlugin.getInstance().getSetRegistry().getSetItems(setName).size();
            tooltip.append(JETPlugin.getInstance().getSetRegistry().getDisplayName(setName) + " Set (" + setSize + " pcs)", "#bb66ff").nl();
        }

        // Usage hint
        tooltip.separator();
        tooltip.append("Click to view recipes", "#55AAFF");

        return tooltip.build();
    }

    private Message buildIngredientTooltip(String itemId, Item item, int quantity, String language) {
        if (item == null) {
            return quantity > 0 ? Message.raw(itemId + " x" + quantity) : Message.raw(itemId);
        }

        TooltipBuilder tooltip = TooltipBuilder.create();

        // Item name with quality color
        String displayName = getDisplayName(item, language);
        if (displayName == null || displayName.isEmpty()) displayName = itemId;
        Message coloredName = getColoredItemName(item, displayName);
        tooltip.append(coloredName);

        // Quantity needed
        if (quantity > 0) {
            tooltip.append(Message.raw(" x" + quantity).color("#FFAA00"));
        }
        tooltip.nl();

        // Quality
        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String qualityName = quality.getId();
                if (qualityName != null && !qualityName.isEmpty()) {
                    String qColor = resolveQualityColor(quality, qualityName);
                    tooltip.append(Message.raw(qualityName).color(qColor));
                    tooltip.nl();
                }
            }
        } catch (Exception ignored) {}

        // Recipe availability hints
        List<String> craftRecipes = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, List.of());
        List<String> usageRecipes = JETPlugin.ITEM_FROM_RECIPES.getOrDefault(itemId, List.of());
        if (!craftRecipes.isEmpty() || !usageRecipes.isEmpty()) {
            tooltip.separator();
            if (!craftRecipes.isEmpty()) {
                tooltip.append(craftRecipes.size() + " recipe" + (craftRecipes.size() > 1 ? "s" : ""), "#55FF55");
                tooltip.nl();
            }
            if (!usageRecipes.isEmpty()) {
                tooltip.append("Used in " + usageRecipes.size() + " recipe" + (usageRecipes.size() > 1 ? "s" : ""), "#AAAAAA");
                tooltip.nl();
            }
        }

        return tooltip.build();
    }

    private void buildItemStats(Item item, UICommandBuilder cmd, String language) {
        if (item == null) {
            cmd.set("#RecipePanel #ItemStatsSection.Visible", false);
            return;
        }

        List<Message> parts = new ArrayList<>();
        boolean hasStats = false;
        String itemId = item.getId();

        boolean hasGeneralInfo = false;
        List<Message> generalParts = new ArrayList<>();

        try {
            String descKey = item.getDescriptionTranslationKey();
            if (descKey != null && !descKey.isEmpty()) {
                String description = I18nModule.get().getMessage(language, descKey);
                if (description != null && !description.isEmpty() && !description.equals(descKey)) {
                    description = wordWrap(stripColorTags(description), 58);
                    generalParts.add(Message.raw("  " + description + "\n").color("#aaaaaa"));
                    hasGeneralInfo = true;
                }
            }
        } catch (Exception ignored) {}

        if (item.getMaxDurability() > 0) {
            generalParts.add(Message.raw("  Durability: ").color("#aaccff"));
            generalParts.add(Message.raw(String.format("%.0f", item.getMaxDurability()) + "\n").color("#ffffff"));
            hasGeneralInfo = true;
        }

        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String qualityName = I18nModule.get().getMessage(language, quality.getLocalizationKey());
                if (qualityName != null && !qualityName.isEmpty()) {
                    generalParts.add(Message.raw("  Quality: ").color("#aaccff"));
                    String qColor = resolveQualityColor(quality, qualityName);
                    generalParts.add(Message.raw(qualityName + "\n").color(qColor));
                    hasGeneralInfo = true;
                }
            }
        } catch (Exception ignored) {}

        if (item.getMaxStack() != 1) {
            generalParts.add(Message.raw("  Max Stack: ").color("#aaccff"));
            generalParts.add(Message.raw(item.getMaxStack() + "\n").color("#ffffff"));
            hasGeneralInfo = true;
        }

        if (hasGeneralInfo) {
            hasStats = true;
            parts.add(Message.raw("General:\n").color("#ffcc44").bold(true));
            parts.addAll(generalParts);
        }

        List<String> biomeSpawns = JETPlugin.getInstance().getDropListRegistry().getOreBiomeSpawns(itemId);
        if (!biomeSpawns.isEmpty()) {
            if (hasStats) parts.add(Message.raw("\n"));
            hasStats = true;
            parts.add(Message.raw("Ore Spawn Locations:\n").color("#ffcc44").bold(true));
            for (String biome : biomeSpawns) {
                parts.add(Message.raw("  ⛏ " + biome + "\n").color("#88ff88"));
            }
        }

        try {
            if (item.getWeapon() != null) {
                boolean hasWeaponStats = false;
                List<Message> weaponParts = new ArrayList<>();

                Map<String, int[]> attackDamage = readWeaponDamageFromJson(itemId);
                if (!attackDamage.isEmpty()) {
                    int overallMax = 0;
                    int overallMinSum = 0;
                    int count = 0;
                    for (Map.Entry<String, int[]> dmgEntry : attackDamage.entrySet()) {
                        String attackName = dmgEntry.getKey().replace("_", " ");
                        int[] minMax = dmgEntry.getValue();
                        if (minMax[0] == minMax[1]) {
                            weaponParts.add(Message.raw("  " + attackName + ": ").color("#aaccff"));
                            weaponParts.add(Message.raw(minMax[0] + "\n").color("#ff6666"));
                        } else {
                            weaponParts.add(Message.raw("  " + attackName + ": ").color("#aaccff"));
                            weaponParts.add(Message.raw(minMax[0] + " - " + minMax[1] + "\n").color("#ff6666"));
                        }
                        overallMax = Math.max(overallMax, minMax[1]);
                        overallMinSum += minMax[0];
                        count++;
                        hasWeaponStats = true;
                    }

                    if (count > 0) {
                        List<Message> dmgSummary = new ArrayList<>();
                        dmgSummary.add(Message.raw("  Max Damage: ").color("#aaccff"));
                        dmgSummary.add(Message.raw(overallMax + "\n").color("#ff4444").bold(true));
                        weaponParts.addAll(0, dmgSummary);
                    }
                }

                if (readStatModifiers(item.getWeapon(), weaponParts)) {
                    hasWeaponStats = true;
                }

                if (hasWeaponStats) {
                    if (hasStats) parts.add(Message.raw("\n"));
                    hasStats = true;
                    parts.add(Message.raw("Weapon Stats:\n").color("#ffcc44").bold(true));
                    parts.addAll(weaponParts);
                }
            }
        } catch (Exception ignored) {}

        try {
            Object armorObj = item.getArmor();
            if (armorObj != null) {
                boolean hasArmorStats = false;
                List<Message> armorParts = new ArrayList<>();

                Method getResistanceMethod = armorObj.getClass().getMethod("getDamageResistanceValues");
                Object resistance = getResistanceMethod.invoke(armorObj);
                if (resistance instanceof java.util.Map) {
                    java.util.Map<?, ?> resMap = (java.util.Map<?, ?>) resistance;
                    for (java.util.Map.Entry<?, ?> entry : resMap.entrySet()) {
                        try {
                            Object damageCause = entry.getKey();
                            Method getIdMethod = damageCause.getClass().getMethod("getId");
                            String causeId = (String) getIdMethod.invoke(damageCause);

                            Object[] modifiers = (Object[]) entry.getValue();
                            for (Object modifier : modifiers) {
                                String formatted = formatStaticModifier(modifier);
                                armorParts.add(Message.raw("  " + causeId + " Resistance: ").color("#aaccff"));
                                armorParts.add(Message.raw("+" + formatted + "\n").color("#55aaff"));
                                hasArmorStats = true;
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (hasArmorStats) {
                    if (hasStats) parts.add(Message.raw("\n"));
                    hasStats = true;
                    parts.add(Message.raw("Armor Stats:\n").color("#ffcc44").bold(true));
                    parts.addAll(armorParts);
                }
            }
        } catch (Exception ignored) {}

        try {
            if (item.getTool() != null) {
                boolean hasToolStats = false;
                List<Message> toolParts = new ArrayList<>();

                Object[] specs = item.getTool().getSpecs();
                if (specs != null && specs.length > 0) {
                    for (Object spec : specs) {
                        try {
                            Method getGatherTypeMethod = spec.getClass().getMethod("getGatherType");
                            Method getPowerMethod = spec.getClass().getMethod("getPower");
                            String gatherType = (String) getGatherTypeMethod.invoke(spec);
                            float power = (Float) getPowerMethod.invoke(spec);
                            toolParts.add(Message.raw("  " + gatherType + ": ").color("#aaccff"));
                            toolParts.add(Message.raw(String.format("%.2f", power) + "\n").color("#ffffff"));
                            hasToolStats = true;
                        } catch (Exception ignored) {}
                    }
                }

                if (hasToolStats) {
                    if (hasStats) parts.add(Message.raw("\n"));
                    hasStats = true;
                    parts.add(Message.raw("Tool Stats:\n").color("#ffcc44").bold(true));
                    parts.addAll(toolParts);
                }
            }
        } catch (Exception ignored) {}

        if (hasStats) {
            cmd.set("#RecipePanel #ItemStatsSection.Visible", true);
            cmd.set("#RecipePanel #ItemStatsSection #StatsContent.Visible", !statsCollapsed);
            cmd.set("#RecipePanel #ItemStatsSection #StatsContent #ItemStats.TextSpans",
                    Message.join(parts.toArray(new Message[0])));
            String chevronItem = statsCollapsed ? "JET_Icon_Arrow_Right" : "JET_Icon_Chevron_Down";
            cmd.set("#RecipePanel #ItemStatsSection #ToggleStats #ToggleStatsIcon.ItemId", chevronItem);
        } else {
            cmd.set("#RecipePanel #ItemStatsSection.Visible", false);
        }
    }

    private Object resolveInteractionVar(Object value, java.util.Map<String, Object> varsMap) {
        if (value == null) return "null";
        String valueStr = value.toString();
        if (valueStr.startsWith("*")) {
            String refKey = valueStr.substring(1);
            Object resolved = varsMap.get(refKey);
            if (resolved != null) return resolveInteractionVar(resolved, varsMap);
            return valueStr;
        }
        if (value instanceof Number) {
            Number num = (Number) value;
            if (value instanceof Float || value instanceof Double) {
                return String.format("%.1f", num.doubleValue());
            }
            return num.toString();
        }
        return valueStr;
    }

    private void buildAdvancedInfo(Item item, UICommandBuilder cmd, UIEventBuilder events, String language) {
        if (item == null) {
            cmd.set("#RecipePanel #AdvancedInfoSection.Visible", false);
            return;
        }

        cmd.set("#RecipePanel #AdvancedInfoSection.Visible", true);

        cmd.set("#RecipePanel #AdvancedInfoSection #AdvancedInfoContent.Visible", !advancedInfoCollapsed);

        String chevronItem = advancedInfoCollapsed ? "JET_Icon_Arrow_Right" : "JET_Icon_Chevron_Down";
        cmd.set("#RecipePanel #AdvancedInfoSection #ToggleAdvancedInfo #ToggleAdvancedInfoIcon.ItemId", chevronItem);

        StringBuilder advInfo = new StringBuilder();
        String itemId = item.getId();

        advInfo.append("--- Item Properties ---\n");
        advInfo.append("  Item ID: ").append(itemId).append("\n");
        advInfo.append("  Namespace: ").append(extractNamespace(itemId)).append("\n");
        advInfo.append("  Max Stack: ").append(item.getMaxStack()).append("\n");

        if (item.getMaxDurability() > 0) {
            advInfo.append("  Max Durability: ").append(String.format("%.0f", item.getMaxDurability())).append("\n");
        }

        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String qualityKey = quality.getLocalizationKey();
                if (qualityKey != null) {
                    String qualityName = I18nModule.get().getMessage(language, qualityKey);
                    if (qualityName != null && !qualityName.isEmpty()) {
                        advInfo.append("  Quality: ").append(qualityName).append(" (").append(qualityIndex).append(")\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            ItemResourceType[] resourceTypes = item.getResourceTypes();
            if (resourceTypes != null && resourceTypes.length > 0) {
                advInfo.append("\n--- Resource Types ---\n");
                for (ItemResourceType rt : resourceTypes) {
                    if (rt != null && rt.id != null) {
                        advInfo.append("  • ").append(rt.id).append("\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            String transKey = item.getTranslationKey();
            String descKey = item.getDescriptionTranslationKey();
            if ((transKey != null && !transKey.isEmpty()) || (descKey != null && !descKey.isEmpty())) {
                advInfo.append("\n--- Translation Keys ---\n");
                if (transKey != null && !transKey.isEmpty()) {
                    advInfo.append("  Name: ").append(transKey).append("\n");
                }
                if (descKey != null && !descKey.isEmpty()) {
                    advInfo.append("  Description: ").append(descKey).append("\n");
                }
            }
        } catch (Exception ignored) {}

        Message advInfoMessage = Message.raw(advInfo.toString());
        cmd.set("#RecipePanel #AdvancedInfoSection #AdvancedInfoText.TextSpans", advInfoMessage);
        cmd.set("#RecipePanel #AdvancedInfoSection #AdvancedInfoText.TooltipTextSpans", advInfoMessage);
    }

    private static final int SET_ITEMS_PER_ROW = 7;

    private void buildSetSection(UICommandBuilder cmd, UIEventBuilder events, String language) {
        SetRegistry setRegistry = JETPlugin.getInstance().getSetRegistry();
        String setName = selectedItem != null ? setRegistry.getSetForItem(selectedItem) : null;

        if (setName == null) {
            cmd.set("#RecipePanel #ItemSetSection.Visible", false);
            return;
        }

        cmd.set("#RecipePanel #ItemSetSection.Visible", true);
        cmd.set("#RecipePanel #ItemSetSection #SetContent.Visible", !setCollapsed);

        String chevronItem = setCollapsed ? "JET_Icon_Arrow_Right" : "JET_Icon_Chevron_Down";
        cmd.set("#RecipePanel #ItemSetSection #ToggleSet #ToggleSetIcon.ItemId", chevronItem);

        List<String> setItems = setRegistry.getSetItems(setName);
        String displayName = setRegistry.getDisplayName(setName);
        cmd.set("#RecipePanel #ItemSetSection #SetLabel.TextSpans",
                Message.raw("Item Set: " + displayName + " (" + setItems.size() + " pieces)").color("#ffaa00"));

        cmd.clear("#RecipePanel #ItemSetSection #SetContent #SetItems");

        if (!setCollapsed) {
            int itemIndex = 0;
            int rowIndex = 0;

            for (int i = 0; i < setItems.size(); i++) {
                String setItemId = setItems.get(i);
                Item setItem = JETPlugin.ITEMS.get(setItemId);
                if (setItem == null) continue;

                // Create a new row group when needed
                int col = itemIndex % SET_ITEMS_PER_ROW;
                if (col == 0) {
                    rowIndex = itemIndex / SET_ITEMS_PER_ROW;
                    cmd.appendInline("#RecipePanel #ItemSetSection #SetContent #SetItems",
                            "Group #SetRow" + rowIndex + " { LayoutMode: Left; Padding: (Bottom: 2); }");
                }

                String rowSel = "#RecipePanel #ItemSetSection #SetContent #SetItems[" + rowIndex + "]";

                boolean isCurrent = setItemId.equals(selectedItem);
                String bgColor = isCurrent ? "#ffffff30" : "#00000000";
                String borderStyle = isCurrent
                        ? "Style: (Default: (Background: " + bgColor + "), Hovered: (Background: #ffffff40), Pressed: (Background: #ffffff50));"
                        : "Style: (Hovered: (Background: #ffffff30), Pressed: (Background: #ffffff50));";

                cmd.appendInline(rowSel,
                        "Button #SetItem" + itemIndex + " { Padding: (Right: 4, Bottom: 4); Background: (Color: " + bgColor + "); " + borderStyle +
                        " LayoutMode: Top; Anchor: (Width: 48); " +
                        "ItemIcon { Anchor: (Width: 36, Height: 36); Visible: true; } " +
                        "Label { Style: (FontSize: 8, TextColor: #cccccc, HorizontalAlignment: Center); } }");

                cmd.set(rowSel + "[" + col + "][0].ItemId", setItemId);

                // Set truncated display name
                String itemDisplayName = getDisplayName(setItem, language);
                if (itemDisplayName.length() > 8) {
                    itemDisplayName = itemDisplayName.substring(0, 7) + "..";
                }
                cmd.set(rowSel + "[" + col + "][1].Text", itemDisplayName);

                // Tooltip with full name
                cmd.set(rowSel + "[" + col + "].TooltipTextSpans",
                        Message.raw(getDisplayName(setItem, language)));

                // Click to navigate (unless it's the current item)
                if (!isCurrent) {
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            rowSel + "[" + col + "]",
                            EventData.of("SelectedItem", setItemId), false);
                }

                itemIndex++;
            }
        }
    }

    private String extractNamespace(String itemId) {
        if (itemId == null) return "Unknown";
        int colonIndex = itemId.indexOf(':');
        if (colonIndex > 0) {
            return itemId.substring(0, colonIndex);
        }
        return "Common";
    }

    private void buildCalcSection(UICommandBuilder cmd, UIEventBuilder events, List<String> craftRecipeIds, boolean noSource) {
        cmd.clear("#RecipePanel #RecipeListContainer #RecipeList");
        cmd.set("#RecipePagination #PrevRecipe.Visible", false);
        cmd.set("#RecipePagination #NextRecipe.Visible", false);

        if (craftRecipeIds.isEmpty()) {
            appendEmptyMessage(cmd, noSource ? "Uncraftable — no source found" : "No crafting recipe", noSource);
            return;
        }

        // Quantity controls row [0]
        cmd.append("#RecipePanel #RecipeListContainer #RecipeList", "Pages/JET_CalcControls.ui");
        cmd.set("#CalcControls #CalcQtyLabel.Text", String.valueOf(calcQuantity));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcMinus10", EventData.of("CalcQuantityChange", "dec10"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcMinus", EventData.of("CalcQuantityChange", "dec"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcPlus", EventData.of("CalcQuantityChange", "inc"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CalcControls #CalcPlus10", EventData.of("CalcQuantityChange", "inc10"), false);

        String listSel = "#RecipePanel #RecipeListContainer #RecipeList";
        String language = playerRef.getLanguage();
        String[] depthColors = {"#88ccff", "#88ff88", "#ffcc66", "#ff8888", "#cc88ff"};

        // --- Part A: Crafting Tree ---
        List<TreeNode> tree = buildCraftingTree(selectedItem, calcQuantity);

        long intermediateCount = tree.stream().filter(n -> n.isCraftable).count();
        long rawCount = tree.stream().filter(n -> !n.isCraftable).count();
        cmd.set("#RecipePanel #PageInfo.TextSpans",
                Message.join(new Message[]{
                    Message.raw(rawCount + " raw").color("#aaaaaa"),
                    Message.raw("  "),
                    Message.raw(intermediateCount + " craftable").color("#55aaff")
                }));

        if (tree.isEmpty()) {
            return;
        }

        int appendIdx = 1; // [0] = controls, appendInline items start at [1]

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

            // Build chevron prefix for craftable nodes
            String chevron = "";
            if (node.isCraftable) {
                chevron = node.isExpanded ? "v " : "> ";
            }

            String nameColor = node.isCraftable ? "#88ccff" : "#cccccc";

            // Build tree row — exact same pattern as working version, just with Button for craftable
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

            // Name with recipe indicator for multi-recipe
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
                        EventData.of("CalcIngredientSelect", action + node.itemId), false);
            }

            cmd.set(rowSel + ".TooltipTextSpans", Message.raw(displayName + "  " + qtyStr));

            appendIdx++;
        }

        // --- Part B: Separator ---
        cmd.appendInline(listSel,
                "Group { Anchor: (Height: 2); Background: (Color: #555555); }");
        appendIdx++;

        // --- Part C: Raw Materials Summary ---
        Map<String, Long> rawMaterials = new LinkedHashMap<>();
        resolveIngredients(selectedItem, (long) calcQuantity, rawMaterials, new HashSet<>());
        cmd.appendInline(listSel,
                "Group { Padding: (Bottom: 6); Label { Style: (FontSize: 10, TextColor: #aaaaaa, HorizontalAlignment: Center); } }");
        cmd.set(listSel + "[" + appendIdx + "][0].Text", "Raw Materials");
        appendIdx++;

        if (!rawMaterials.isEmpty()) {
            int matItemIndex = 0;
            int matRowIndex = 0;

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

                int col = matItemIndex % SET_ITEMS_PER_ROW;
                if (col == 0) {
                    matRowIndex = matItemIndex / SET_ITEMS_PER_ROW;
                    cmd.appendInline(listSel,
                            "Group #RawRow" + matRowIndex + " { LayoutMode: Left; Padding: (Bottom: 2); }");
                    appendIdx++;
                }

                String matRowSel = listSel + "[" + (appendIdx - 1) + "]";

                cmd.appendInline(matRowSel,
                        "Group #RawItem" + matItemIndex + " { LayoutMode: Top; Padding: (Right: 4, Bottom: 4); Anchor: (Width: 48); " +
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

    // --- Crafting Tree ---

    /**
     * Get non-salvager recipes for an item, with the user's selected recipe index applied.
     * Returns null if no valid (non-salvager) recipe exists.
     */
    private CraftingRecipe getNonSalvagerRecipe(String itemId, List<String> recipeIds) {
        // Filter to non-salvager recipes
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

        // Get non-salvager recipe
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

    private Map<String, Long> calculateRawMaterials(String itemId, int quantity) {
        Map<String, Long> materials = new LinkedHashMap<>();

        List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());
        if (recipeIds.isEmpty()) return materials;

        CraftingRecipe recipe = JETPlugin.RECIPES.get(recipeIds.get(0));
        if (recipe == null) return materials;

        // Find how many the recipe produces per craft
        int outputQty = 1;
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs != null) {
            for (MaterialQuantity output : outputs) {
                if (output != null && itemId.equals(output.getItemId())) {
                    outputQty = Math.max(1, output.getQuantity());
                    break;
                }
            }
        }

        long craftsNeeded = ((long) quantity + outputQty - 1) / outputQty;

        // Same inputs the Craft tab shows, scaled by craftsNeeded
        for (MaterialQuantity input : getRecipeInputs(recipe)) {
            if (input.getItemId() != null) {
                materials.merge(input.getItemId(), (long) input.getQuantity() * craftsNeeded, Long::sum);
            } else if (input.getResourceTypeId() != null) {
                materials.merge("resource:" + input.getResourceTypeId(), (long) input.getQuantity() * craftsNeeded, Long::sum);
            }
        }

        return materials;
    }

    private void resolveIngredients(String itemId, long needed, Map<String, Long> materials, Set<String> visited) {
        if (visited.contains(itemId)) {
            // Circular reference protection — treat as raw material
            materials.merge(itemId, needed, Long::sum);
            return;
        }

        List<String> recipeIds = JETPlugin.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());
        if (recipeIds.isEmpty()) {
            // Raw material — no crafting recipe
            materials.merge(itemId, needed, Long::sum);
            return;
        }

        CraftingRecipe recipe = getNonSalvagerRecipe(itemId, recipeIds);
        if (recipe == null) {
            materials.merge(itemId, needed, Long::sum);
            return;
        }

        // Find how many of this item the recipe produces
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

        long craftsNeeded = (needed + outputQty - 1) / outputQty; // ceiling division

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
        private String pinToHud;
        public static final BuilderCodec<GuiData> CODEC = BuilderCodec
                .builder(GuiData.class, GuiData::new)
                .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .addField(new KeyedCodec<>("ActiveSection", Codec.STRING), (d, v) -> d.activeSection = v, d -> d.activeSection)
                .addField(new KeyedCodec<>("PageChange", Codec.STRING), (d, v) -> d.pageChange = v, d -> d.pageChange)
                .addField(new KeyedCodec<>("ToggleMode", Codec.STRING), (d, v) -> d.toggleMode = v, d -> d.toggleMode)
                .addField(new KeyedCodec<>("PinAction", Codec.STRING), (d, v) -> d.pinAction = v, d -> d.pinAction)
                .addField(new KeyedCodec<>("ItemPageChange", Codec.STRING), (d, v) -> d.itemPageChange = v, d -> d.itemPageChange)
                .addField(new KeyedCodec<>("@CategoryFilter", Codec.STRING), (d, v) -> d.categoryFilter = v, d -> d.categoryFilter)
                .addField(new KeyedCodec<>("ClearFilters", Codec.STRING), (d, v) -> d.clearFilters = v, d -> d.clearFilters)
                .addField(new KeyedCodec<>("@SortMode", Codec.STRING), (d, v) -> d.sortMode = v, d -> d.sortMode)
                .addField(new KeyedCodec<>("@ModFilter", Codec.STRING), (d, v) -> d.modFilter = v, d -> d.modFilter)
                .addField(new KeyedCodec<>("@GridLayout", Codec.STRING), (d, v) -> d.gridLayout = v, d -> d.gridLayout)
                .addField(new KeyedCodec<>("@ShowHiddenItems", Codec.BOOLEAN), (d, v) -> d.showHiddenItems = v, d -> d.showHiddenItems)
                .addField(new KeyedCodec<>("@ShowSalvagerRecipes", Codec.BOOLEAN), (d, v) -> d.showSalvagerRecipes = v, d -> d.showSalvagerRecipes)
                .addField(new KeyedCodec<>("GiveItem", Codec.STRING), (d, v) -> d.giveItem = v, d -> d.giveItem)
                .addField(new KeyedCodec<>("GiveItemStack", Codec.STRING), (d, v) -> d.giveItemStack = v, d -> d.giveItemStack)
                .addField(new KeyedCodec<>("ToggleHistory", Codec.STRING), (d, v) -> d.toggleHistory = v, d -> d.toggleHistory)
                .addField(new KeyedCodec<>("ClearHistory", Codec.STRING), (d, v) -> d.clearHistory = v, d -> d.clearHistory)
                .addField(new KeyedCodec<>("HistoryItemClick", Codec.STRING), (d, v) -> d.historyItemClick = v, d -> d.historyItemClick)
                .addField(new KeyedCodec<>("OpenDropSource", Codec.STRING), (d, v) -> d.openDropSource = v, d -> d.openDropSource)
                .addField(new KeyedCodec<>("PinToHud", Codec.STRING), (d, v) -> d.pinToHud = v, d -> d.pinToHud)
                .addField(new KeyedCodec<>("ToggleAdvancedInfo", Codec.STRING), (d, v) -> d.toggleAdvancedInfo = v, d -> d.toggleAdvancedInfo)
                .addField(new KeyedCodec<>("ToggleStats", Codec.STRING), (d, v) -> d.toggleStats = v, d -> d.toggleStats)
                .addField(new KeyedCodec<>("OpenSettings", Codec.STRING), (d, v) -> d.openSettings = v, d -> d.openSettings)
                .addField(new KeyedCodec<>("ToggleSet", Codec.STRING), (d, v) -> d.toggleSet = v, d -> d.toggleSet)
                .addField(new KeyedCodec<>("CalcQuantityChange", Codec.STRING), (d, v) -> d.calcQuantityChange = v, d -> d.calcQuantityChange)
                .addField(new KeyedCodec<>("CalcIngredientSelect", Codec.STRING), (d, v) -> d.calcIngredientSelect = v, d -> d.calcIngredientSelect)
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
        private String toggleAdvancedInfo;
        private String toggleStats;
        private String openSettings;
        private String toggleSet;
        private String calcQuantityChange;
        private String calcIngredientSelect;

        public GuiData() {}
    }

    private String formatStaticModifier(Object modifier) {
        try {
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

    private boolean readStatModifiers(Object weaponOrArmor, List<Message> parts) {
        boolean found = false;
        try {
            Method getStatModsMethod = weaponOrArmor.getClass().getMethod("getStatModifiers");
            Object statMods = getStatModsMethod.invoke(weaponOrArmor);
            if (statMods == null) return false;

            Method int2ObjectEntrySetMethod = statMods.getClass().getMethod("int2ObjectEntrySet");
            Object entrySet = int2ObjectEntrySetMethod.invoke(statMods);
            if (!(entrySet instanceof java.util.Set)) return false;

            Class<?> entityStatTypeClass = Class.forName("com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType");
            Method getAssetMapMethod = entityStatTypeClass.getMethod("getAssetMap");
            Object assetMap = getAssetMapMethod.invoke(null);
            Method getAssetMethod = assetMap.getClass().getMethod("getAsset", int.class);

            for (Object entryObj : (java.util.Set<?>) entrySet) {
                try {
                    int statTypeIndex = (Integer) entryObj.getClass().getMethod("getIntKey").invoke(entryObj);
                    Object entityStatType = getAssetMethod.invoke(assetMap, statTypeIndex);
                    if (entityStatType == null) continue;

                    String statId = (String) entityStatType.getClass().getMethod("getId").invoke(entityStatType);
                    String statName = statId.replace("_", " ");
                    Object[] modifiers = (Object[]) entryObj.getClass().getMethod("getValue").invoke(entryObj);

                    for (Object modifier : modifiers) {
                        parts.add(Message.raw("  " + statName + ": ").color("#aaccff"));
                        parts.add(Message.raw("+" + formatStaticModifier(modifier) + "\n").color("#55ff55"));
                        found = true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return found;
    }

    private Map<String, int[]> readWeaponDamageFromJson(String itemId) {
        Map<String, int[]> result = new LinkedHashMap<>();
        try {
            Path itemPath = Item.getAssetMap().getPath(itemId);
            if (itemPath == null) return result;

            AssetPack pack = AssetModule.get().findAssetPackForPath(itemPath);
            if (pack == null) return result;

            Path fullPath = pack.getRoot().resolve(itemPath);
            if (!Files.exists(fullPath)) return result;

            String jsonContent = Files.readString(fullPath);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(jsonContent).getAsJsonObject();
            if (!root.has("InteractionVars")) return result;

            com.google.gson.JsonObject interactionVars = root.getAsJsonObject("InteractionVars");
            for (Map.Entry<String, com.google.gson.JsonElement> varEntry : interactionVars.entrySet()) {
                parseDamageFromInteractionVar(varEntry.getKey(), varEntry.getValue(), result);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void parseDamageFromInteractionVar(String varName, com.google.gson.JsonElement varValue, Map<String, int[]> result) {
        if (!varValue.isJsonObject()) return;
        com.google.gson.JsonObject varObj = varValue.getAsJsonObject();
        if (!varObj.has("Interactions")) return;

        for (com.google.gson.JsonElement interactionEl : varObj.getAsJsonArray("Interactions")) {
            if (!interactionEl.isJsonObject()) continue;
            com.google.gson.JsonObject interaction = interactionEl.getAsJsonObject();
            if (!interaction.has("DamageCalculator")) continue;

            com.google.gson.JsonObject damageCalc = interaction.getAsJsonObject("DamageCalculator");
            if (!damageCalc.has("BaseDamage")) continue;

            float totalDamage = 0;
            for (Map.Entry<String, com.google.gson.JsonElement> dmgEntry : damageCalc.getAsJsonObject("BaseDamage").entrySet()) {
                totalDamage += dmgEntry.getValue().getAsFloat();
            }

            if (totalDamage > 0) {
                int minDmg = (int) totalDamage;
                int maxDmg = (int) totalDamage;
                if (damageCalc.has("RandomPercentageModifier")) {
                    float modifier = damageCalc.get("RandomPercentageModifier").getAsFloat();
                    minDmg = (int) (totalDamage * (1.0f - modifier));
                    maxDmg = (int) (totalDamage * (1.0f + modifier));
                }
                result.put(varName, new int[]{minDmg, maxDmg});
            }
        }
    }
//    private void applyBgToRecipeEntry(UICommandBuilder cmd, String rSel) {
//        dev.hytalemod.jet.config.JETConfig jetConfig = JETPlugin.getInstance().getConfig();
//        String bg = jetConfig.backgroundImage;
//        if (bg == null || bg.equals("none") || !bg.startsWith("JET_Bg_") || !JETPlugin.ITEMS.containsKey(bg)) return;
//
//        final int tileSize = 32;
//        final int cols = (int) Math.ceil(460.0 / tileSize); // recipe panel is ~460px wide
//        final int rows = 3; // recipe entries are short, 3 rows is plenty
//
//        cmd.set(rSel + " #RecipeBg.Visible", true);
//        for (int r = 0; r < rows; r++) {
//            StringBuilder row = new StringBuilder("Group { LayoutMode: Left; Anchor: (Height: " + tileSize + "); ");
//            for (int c = 0; c < cols; c++) {
//                row.append("ItemIcon { Anchor: (Width: ").append(tileSize).append(", Height: ").append(tileSize).append("); Visible: true; } ");
//            }
//            row.append("}");
//            cmd.appendInline(rSel + " #RecipeBg", row.toString());
//            for (int c = 0; c < cols; c++) {
//                cmd.set(rSel + " #RecipeBg[" + r + "][" + c + "].ItemId", bg);
//            }
//        }
//    }
}