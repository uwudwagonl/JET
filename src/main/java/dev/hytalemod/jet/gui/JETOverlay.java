package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
 * JET Overlay - Shows item browser on the right side when inventory is open.
 */
public class JETOverlay extends InteractiveCustomUIPage<JETOverlay.OverlayData> {

    private static final int ITEMS_PER_ROW = 9;
    private static final int MAX_ROWS = 9;
    private static final int MAX_ITEMS = ITEMS_PER_ROW * MAX_ROWS;

    private String searchQuery = "";
    private int currentPage = 0;
    private List<Map.Entry<String, Item>> filteredItems = new ArrayList<>();

    public JETOverlay(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, OverlayData.CODEC);
        refreshFilteredItems();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Overlay.ui");

        // Search bar binding
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#JETOverlay #SearchInput",
                EventData.of("@SearchQuery", "#JETOverlay #SearchInput.Value"),
                false
        );

        // Pagination
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JETOverlay #PrevPage",
                EventData.of("PageAction", "prev"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#JETOverlay #NextPage",
                EventData.of("PageAction", "next"),
                false
        );

        buildItemGrid(cmd, events);
        updatePageInfo(cmd);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, OverlayData data) {
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

        // Handle item click - open full JET GUI with that item selected
        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            openFullGuiForItem(ref, store, data.selectedItem);
            return; // Don't update, we're switching to full GUI
        }

        if (needsUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildItemGrid(cmd, events);
            updatePageInfo(cmd);
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
        cmd.clear("#JETOverlay #ItemGrid");

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
                cmd.appendInline("#JETOverlay #ItemGrid", 
                    "Group { LayoutMode: Left; Anchor: (Height: 36); }");
            }

            // Add item slot
            cmd.append("#JETOverlay #ItemGrid[" + row + "]", "Pages/JET_SmallIcon.ui");
            String sel = "#JETOverlay #ItemGrid[" + row + "][" + col + "]";

            cmd.set(sel + " #SlotIcon.ItemId", itemId);
            
            String displayName = getDisplayName(item, language);
            cmd.set(sel + ".TooltipTextSpans", Message.raw(displayName + "\n" + itemId));

            // Click to open full GUI
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
        
        cmd.set("#JETOverlay #PageInfo.Text", (currentPage + 1) + "/" + totalPages);
        cmd.set("#JETOverlay #ItemCount.Text", filteredItems.size() + " items");
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

    private void openFullGuiForItem(Ref<EntityStore> ref, Store<EntityStore> store, String itemId) {
        // Close this overlay and open the full JET GUI with the item pre-selected
        close();
        
        // Get player and open full GUI
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            JETGui fullGui = new JETGui(playerRef, CustomPageLifetime.CanDismiss, "");
            // The full GUI will need to handle pre-selecting the item
            // For now, just open it - user can search for the item
            player.getPageManager().openCustomPage(ref, store, fullGui);
        }
    }

    public static class OverlayData {
        public static final BuilderCodec<OverlayData> CODEC = BuilderCodec
                .builder(OverlayData.class, OverlayData::new)
                .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .addField(new KeyedCodec<>("PageAction", Codec.STRING), (d, v) -> d.pageAction = v, d -> d.pageAction)
                .build();

        private String searchQuery;
        private String selectedItem;
        private String pageAction;

        public OverlayData() {}
    }
}
