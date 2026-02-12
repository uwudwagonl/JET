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
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.storage.BrowserState;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

public class MobInfoGui extends InteractiveCustomUIPage<MobInfoGui.GuiData> {

    private final String dropListId;
    private final String selectedItemId;
    private final BrowserState previousBrowserState;

    private List<DropEntry> parsedDrops;

    public MobInfoGui(PlayerRef playerRef, CustomPageLifetime lifetime, String dropListId, String selectedItemId, BrowserState previousBrowserState) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.dropListId = dropListId;
        this.selectedItemId = selectedItemId;
        this.previousBrowserState = previousBrowserState;
        this.parsedDrops = new ArrayList<>();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_MobInfo.ui");

        String mobName = formatMobName(dropListId);
        String mobCategory = getMobCategory(dropListId);

        cmd.set("#Title #MobName.TextSpans", Message.raw(mobName).bold(true));
        cmd.set("#Title #MobCategory.TextSpans", Message.raw(mobCategory).color("#888888"));

        parseDropList();

        // Build model preview section
        buildModelSection(cmd, mobName, mobCategory);

        // Build loot table
        buildDropTable(cmd, events);
    }

    private void buildModelSection(UICommandBuilder cmd, String mobName, String mobCategory) {
        // Show creature name as placeholder
        cmd.set("#Content #CreatureIconContainer #CreatureName.Text", mobName);

        // Show representative drops (up to 4 unique items)
        List<String> previewItems = getPreviewItems();
        for (int i = 0; i < 4; i++) {
            if (i < previewItems.size()) {
                cmd.set("#Content #PreviewIcon" + i + ".ItemId", previewItems.get(i));
                cmd.set("#Content #PreviewIcon" + i + ".Visible", true);
            } else {
                cmd.set("#Content #PreviewIcon" + i + ".Visible", false);
            }
        }

        // Set stats
        cmd.set("#Content #StatType.Text", mobCategory);
        cmd.set("#Content #StatSourceId.Text", dropListId);
        cmd.set("#Content #StatTotalDrops.Text", String.valueOf(parsedDrops.size()));
    }

    private String extractCreatureName(String dropListId) {
        if (dropListId == null) return null;

        String name = dropListId;

        // Remove common prefixes
        if (name.startsWith("Drop_")) {
            name = name.substring(5);
        }

        // Remove suffixes like _Gathering_, _Breaking_, _Loot, etc.
        String[] suffixes = {"_Gathering_", "_Breaking_", "_Loot", "_Drop", "_Default"};
        for (String suffix : suffixes) {
            int idx = name.indexOf(suffix);
            if (idx > 0) {
                name = name.substring(0, idx);
            }
        }

        // Handle zone prefixes (Zone1_Creature_Name -> Creature_Name)
        if (name.startsWith("Zone") && name.length() > 5) {
            int underscore = name.indexOf('_');
            if (underscore > 0 && underscore < name.length() - 1) {
                name = name.substring(underscore + 1);
            }
        }

        return name;
    }

    private List<String> getPreviewItems() {
        // Get up to 4 unique items from the drops to display as preview
        List<String> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // First add the selected item if it exists
        if (selectedItemId != null && !selectedItemId.isEmpty()) {
            items.add(selectedItemId);
            seen.add(selectedItemId);
        }

        // Then add other unique drops
        for (DropEntry drop : parsedDrops) {
            if (!seen.contains(drop.itemId)) {
                seen.add(drop.itemId);
                items.add(drop.itemId);
                if (items.size() >= 4) break;
            }
        }
        return items;
    }

    private String getCategoryBackgroundColor(String category) {
        switch (category) {
            case "Beast": return "#3d2d1a(0.9)";      // Brown - animals
            case "Undead": return "#2d1a3d(0.9)";     // Purple - undead
            case "Intelligent": return "#1a3d2d(0.9)"; // Green - humanoids
            case "Mining": return "#3d3d3d(0.9)";     // Gray - mining/ores
            case "Farming": return "#2d3d1a(0.9)";    // Olive - farming
            case "Container": return "#3d2d2d(0.9)";  // Dark red - containers
            case "Critter": return "#2d3d3d(0.9)";    // Teal - small creatures
            default: return "#1a3d5c(0.9)";           // Blue - default
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, GuiData data) {
        super.handleDataEvent(ref, store, data);

        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            close();

            BrowserState newState = previousBrowserState != null ? previousBrowserState : new BrowserState();
            newState.selectedItem = data.selectedItem;
            newState.activeSection = "craft";

            JETGui browserGui = new JETGui(playerRef, CustomPageLifetime.CanDismiss, null, newState);
            com.hypixel.hytale.server.core.entity.entities.Player player = getPlayer(ref, store);
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, browserGui);
            }
        }
    }

    private com.hypixel.hytale.server.core.entity.entities.Player getPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) return null;
        return store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
    }

    private void parseDropList() {
        parsedDrops.clear();

        // Use the DropListRegistry which indexes by dropList.getId()
        ItemDropList dropList = JETPlugin.getInstance().getDropListRegistry().get(dropListId);
        if (dropList == null) {
            return;
        }

        try {
            Object container = dropList.getContainer();
            if (container == null) {
                return;
            }

            try {
                Method getAllDropsMethod = container.getClass().getMethod("getAllDrops", List.class);
                List<Object> dropsList = new ArrayList<>();
                Object result = getAllDropsMethod.invoke(container, dropsList);

                List<?> drops = null;
                if (result instanceof List) {
                    drops = (List<?>) result;
                } else if (!dropsList.isEmpty()) {
                    drops = dropsList;
                }

                if (drops != null && !drops.isEmpty()) {
                    for (Object drop : drops) {
                        extractDropInfo(drop);
                    }
                }
            } catch (NoSuchMethodException e) {
                tryAlternativeExtraction(container);
            }

        } catch (Exception e) {
            // silently ignore
        }
    }

    private void extractDropInfo(Object drop) {
        if (drop == null) return;

        try {
            String itemId = null;
            int quantityMin = 1;
            int quantityMax = 1;

            // Try to get item ID
            String[] idMethods = {"getItemId", "getId", "getItem", "getMaterial"};
            for (String methodName : idMethods) {
                try {
                    Method method = drop.getClass().getMethod(methodName);
                    Object result = method.invoke(drop);
                    if (result instanceof String) {
                        itemId = (String) result;
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (itemId == null || itemId.isEmpty()) {
                return;
            }

            // Try to get quantity
            try {
                Method getQuantityMin = drop.getClass().getMethod("getQuantityMin");
                Object minObj = getQuantityMin.invoke(drop);
                if (minObj instanceof Number) {
                    quantityMin = ((Number) minObj).intValue();
                }
            } catch (Exception ignored) {}

            try {
                Method getQuantityMax = drop.getClass().getMethod("getQuantityMax");
                Object maxObj = getQuantityMax.invoke(drop);
                if (maxObj instanceof Number) {
                    quantityMax = ((Number) maxObj).intValue();
                }
            } catch (Exception ignored) {}

            // Note: ItemDrop objects don't expose drop chance/weight - that's handled at container level
            // We can't determine individual item drop rates from the API
            parsedDrops.add(new DropEntry(itemId, quantityMin, quantityMax));

        } catch (Exception e) {
            // silently ignore
        }
    }

    private void tryAlternativeExtraction(Object container) {
        // Try to iterate over container if it's a collection
        if (container instanceof Collection) {
            Collection<?> items = (Collection<?>) container;
            for (Object item : items) {
                extractDropInfo(item);
            }
            return;
        }

        if (container.getClass().isArray()) {
            Object[] items = (Object[]) container;
            for (Object item : items) {
                extractDropInfo(item);
            }
            return;
        }

        // Try getContainers for nested structure
        try {
            Method getContainers = container.getClass().getMethod("getContainers");
            Object containersObj = getContainers.invoke(container);

            if (containersObj instanceof List) {
                for (Object child : (List<?>) containersObj) {
                    tryAlternativeExtraction(child);
                }
            } else if (containersObj != null && containersObj.getClass().isArray()) {
                for (Object child : (Object[]) containersObj) {
                    tryAlternativeExtraction(child);
                }
            }
        } catch (Exception ignored) {}

        // Try getItem for single item containers
        try {
            Method getItem = container.getClass().getMethod("getItem");
            Object itemObj = getItem.invoke(container);
            if (itemObj != null) {
                extractDropInfo(itemObj);
            }
        } catch (Exception ignored) {}
    }

    private void buildDropTable(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.clear("#Content #DropList");

        String language = playerRef.getLanguage();

        if (parsedDrops.isEmpty()) {
            cmd.appendInline("#Content #DropList",
                    "Label { Style: (FontSize: 14, TextColor: #888888, HorizontalAlignment: Center); Padding: (Top: 20); }");
            cmd.set("#Content #DropList[0].Text", "No drop data available");
            return;
        }

        // Merge duplicate items
        Map<String, DropEntry> mergedDrops = new LinkedHashMap<>();
        for (DropEntry drop : parsedDrops) {
            if (mergedDrops.containsKey(drop.itemId)) {
                DropEntry existing = mergedDrops.get(drop.itemId);
                existing.quantityMin = Math.min(existing.quantityMin, drop.quantityMin);
                existing.quantityMax = Math.max(existing.quantityMax, drop.quantityMax);
            } else {
                mergedDrops.put(drop.itemId, new DropEntry(drop.itemId, drop.quantityMin, drop.quantityMax));
            }
        }

        List<DropEntry> sortedDrops = new ArrayList<>(mergedDrops.values());
        // Sort alphabetically by item ID since we don't have drop rates
        sortedDrops.sort((a, b) -> a.itemId.compareToIgnoreCase(b.itemId));

        int idx = 0;
        for (DropEntry drop : sortedDrops) {
            Item item = JETPlugin.ITEMS.get(drop.itemId);
            String displayName = item != null ? getDisplayName(item, language) : drop.itemId;

            // Removed the percentage column since drop rates aren't available from the API
            cmd.appendInline("#Content #DropList",
                    "Group { LayoutMode: Left; Padding: (Full: 8); Background: (Color: #1e1e1e(0.8)); Anchor: (Bottom: 4, Height: 50); " +
                    "Button #DropItem" + idx + " { Background: (Color: #00000000); Style: (Hovered: (Background: #ffffff20), Pressed: (Background: #ffffff30)); Anchor: (Width: 48, Height: 48); " +
                    "ItemIcon #Icon" + idx + " { Anchor: (Width: 40, Height: 40); Visible: true; } } " +
                    "Group { LayoutMode: Top; Padding: (Left: 10); FlexWeight: 1; " +
                    "Label #ItemName" + idx + " { Style: (FontSize: 14, TextColor: #ffffff, RenderBold: true); } " +
                    "Label #ItemQuantity" + idx + " { Style: (FontSize: 12, TextColor: #aaaaaa); } } }");

            cmd.set("#Content #DropList[" + idx + "] #Icon" + idx + ".ItemId", drop.itemId);
            cmd.set("#Content #DropList[" + idx + "] #ItemName" + idx + ".Text", displayName);

            String quantityText;
            if (drop.quantityMin == drop.quantityMax) {
                quantityText = "Quantity: " + drop.quantityMin;
            } else {
                quantityText = "Quantity: " + drop.quantityMin + "-" + drop.quantityMax;
            }
            cmd.set("#Content #DropList[" + idx + "] #ItemQuantity" + idx + ".Text", quantityText);

            // Event binding for item clicks
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#Content #DropList[" + idx + "] #DropItem" + idx,
                    EventData.of("SelectedItem", drop.itemId),
                    false
            );

            idx++;
        }

        cmd.set("#Content #DropCount.TextSpans", Message.raw(sortedDrops.size() + " unique drops").color("#888888"));
    }

    private String formatMobName(String dropListId) {
        if (dropListId == null) return "Unknown";

        String name = dropListId;

        if (name.startsWith("Drop_")) {
            name = name.substring(5);
        }

        if (name.contains("_Gathering_") || name.contains("_Breaking_")) {
            int idx = name.indexOf("_Gathering_");
            if (idx == -1) idx = name.indexOf("_Breaking_");
            if (idx > 0) name = name.substring(0, idx);
        }

        if (name.startsWith("Zone") && name.contains("_")) {
            String[] parts = name.split("_");
            if (parts.length >= 2) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].startsWith("Tier")) continue;
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(parts[i]);
                }
                name = sb.toString();
            }
        }

        return name.replace("_", " ");
    }

    private String getMobCategory(String dropListId) {
        if (dropListId == null) return "Unknown";

        if (dropListId.contains("Beast") || dropListId.contains("Wolf") || dropListId.contains("Bear") ||
            dropListId.contains("Spider") || dropListId.contains("Rat") || dropListId.contains("Scarak") ||
            dropListId.contains("Raptor") || dropListId.contains("Rex") || dropListId.contains("Scorpion")) {
            return "Beast";
        }
        if (dropListId.contains("Undead") || dropListId.contains("Zombie") || dropListId.contains("Skeleton") ||
            dropListId.contains("Wraith") || dropListId.contains("Ghoul")) {
            return "Undead";
        }
        if (dropListId.contains("Trork") || dropListId.contains("Goblin") || dropListId.contains("Outlander") ||
            dropListId.contains("Feran") || dropListId.contains("Kweebec")) {
            return "Intelligent";
        }
        if (dropListId.contains("Ore_")) {
            return "Mining";
        }
        if (dropListId.contains("Plant_") || dropListId.contains("Crop_")) {
            return "Farming";
        }
        if (dropListId.contains("Chest") || dropListId.contains("Furniture")) {
            return "Container";
        }
        if (dropListId.contains("Critter") || dropListId.contains("Frog") || dropListId.contains("Mouse") ||
            dropListId.contains("Gecko") || dropListId.contains("Squirrel")) {
            return "Critter";
        }

        return "Creature";
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

    private static class DropEntry {
        String itemId;
        int quantityMin;
        int quantityMax;

        DropEntry(String itemId, int quantityMin, int quantityMax) {
            this.itemId = itemId;
            this.quantityMin = quantityMin;
            this.quantityMax = quantityMax;
        }
    }

    public static class GuiData {
        public static final BuilderCodec<GuiData> CODEC = BuilderCodec
                .builder(GuiData.class, GuiData::new)
                .addField(new KeyedCodec<>("SelectedItem", Codec.STRING), (d, v) -> d.selectedItem = v, d -> d.selectedItem)
                .build();

        private String selectedItem;

        public GuiData() {}
    }
}
