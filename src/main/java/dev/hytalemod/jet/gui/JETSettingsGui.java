package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.config.JETConfig;

import java.util.ArrayList;
import java.util.List;

public class JETSettingsGui extends InteractiveCustomUIPage<JETSettingsGui.SettingsData> {

    private final JETPlugin plugin;
    private boolean isOp = false;

    // Built-in background options
    private static final String[] BUILTIN_BACKGROUNDS = {
        "none",
        "JET_Bg_Default",
        "JET_Bg_Dark",
        "JET_Bg_Crimson",
        "JET_Bg_Emerald",
        "JET_Bg_Galaxy",
        "JET_Bg_Parchment",
        "JET_Bg_Sepia",
        "JET_Bg_Stone",
        "JET_Bg_Wood"
    };

    private static final String[] BUILTIN_BACKGROUND_NAMES = {
        "None (Default)",
        "Blue Gradient",
        "Dark",
        "Crimson",
        "Emerald",
        "Galaxy",
        "Parchment",
        "Sepia",
        "Stone",
        "Wood"
    };

    public JETSettingsGui(PlayerRef playerRef, JETPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, SettingsData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Settings.ui");

        // Check if player is OP
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

        JETConfig config = plugin.getConfig();
        cmd.set("#BindAltKeyCheck #CheckBox.Value", config.bindAltKey);
        cmd.set("#GiveButtonsCheck #CheckBox.Value", config.enableGiveButtons);
        cmd.set("#DisableJetCommandCheck #CheckBox.Value", config.disableJetCommand);
        cmd.set("#DisableGlyphCheck #CheckBox.Value", config.disableGlyph);
        cmd.set("#RequireCreativeOrOpCheck #CheckBox.Value", config.requireCreativeOrOp);

        // Build background dropdown entries
        List<DropdownEntryInfo> bgEntries = new ArrayList<>();
        for (int i = 0; i < BUILTIN_BACKGROUNDS.length; i++) {
            bgEntries.add(new DropdownEntryInfo(
                LocalizableString.fromString(BUILTIN_BACKGROUND_NAMES[i]),
                BUILTIN_BACKGROUNDS[i]
            ));
        }

        // Add any custom JET_Bg_ items found in the game
        for (String itemId : JETPlugin.ITEMS.keySet()) {
            if (itemId.startsWith("JET_Bg_") && !isBuiltinBackground(itemId)) {
                String displayName = itemId.substring(7).replace("_", " ");
                bgEntries.add(new DropdownEntryInfo(
                    LocalizableString.fromString(displayName + " (Custom)"),
                    itemId
                ));
            }
        }

        cmd.set("#BackgroundDropdown.Entries", bgEntries);
        cmd.set("#BackgroundDropdown.Value", config.backgroundImage != null ? config.backgroundImage : "none");

        // Build opacity dropdown
        List<DropdownEntryInfo> opacityEntries = new ArrayList<>();
        opacityEntries.add(new DropdownEntryInfo(LocalizableString.fromString("20%"), "0.2"));
        opacityEntries.add(new DropdownEntryInfo(LocalizableString.fromString("40%"), "0.4"));
        opacityEntries.add(new DropdownEntryInfo(LocalizableString.fromString("60%"), "0.6"));
        opacityEntries.add(new DropdownEntryInfo(LocalizableString.fromString("80%"), "0.8"));
        opacityEntries.add(new DropdownEntryInfo(LocalizableString.fromString("100%"), "1.0"));
        cmd.set("#OpacityDropdown.Entries", opacityEntries);
        cmd.set("#OpacityDropdown.Value", String.valueOf(config.backgroundOpacity));

        // Set preview image
        updatePreview(cmd, config.backgroundImage);

        // Event bindings
        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BindAltKeyCheck #CheckBox",
            EventData.of("@BindAltKey", "#BindAltKeyCheck #CheckBox.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#GiveButtonsCheck #CheckBox",
            EventData.of("@EnableGiveButtons", "#GiveButtonsCheck #CheckBox.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#DisableJetCommandCheck #CheckBox",
            EventData.of("@DisableJetCommand", "#DisableJetCommandCheck #CheckBox.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#DisableGlyphCheck #CheckBox",
            EventData.of("@DisableGlyph", "#DisableGlyphCheck #CheckBox.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#RequireCreativeOrOpCheck #CheckBox",
            EventData.of("@RequireCreativeOrOp", "#RequireCreativeOrOpCheck #CheckBox.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BackgroundDropdown",
            EventData.of("@BackgroundImage", "#BackgroundDropdown.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#OpacityDropdown",
            EventData.of("@BackgroundOpacity", "#OpacityDropdown.Value"),
            false
        );
    }

    private boolean isBuiltinBackground(String itemId) {
        for (String builtin : BUILTIN_BACKGROUNDS) {
            if (builtin.equals(itemId)) return true;
        }
        return false;
    }

    private void updatePreview(UICommandBuilder cmd, String backgroundImage) {
        if (backgroundImage == null || backgroundImage.equals("none") || !JETPlugin.ITEMS.containsKey(backgroundImage)) {
            cmd.set("#PreviewImage.Visible", false);
        } else {
            cmd.set("#PreviewImage.ItemId", backgroundImage);
            cmd.set("#PreviewImage.Visible", true);
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, SettingsData data) {
        super.handleDataEvent(ref, store, data);

        JETConfig config = plugin.getConfig();
        boolean changed = false;
        boolean needsPreviewUpdate = false;

        if (data.bindAltKey != null) {
            config.bindAltKey = data.bindAltKey;
            changed = true;
        }

        if (data.enableGiveButtons != null) {
            config.enableGiveButtons = data.enableGiveButtons;
            changed = true;
        }

        if (data.disableJetCommand != null && isOp) {
            config.disableJetCommand = data.disableJetCommand;
            changed = true;
        }

        if (data.disableGlyph != null && isOp) {
            config.disableGlyph = data.disableGlyph;
            changed = true;
        }

        if (data.requireCreativeOrOp != null && isOp) {
            config.requireCreativeOrOp = data.requireCreativeOrOp;
            changed = true;
        }

        if (data.backgroundImage != null && !data.backgroundImage.equals(config.backgroundImage)) {
            config.backgroundImage = data.backgroundImage;
            changed = true;
            needsPreviewUpdate = true;
        }

        if (data.backgroundOpacity != null) {
            try {
                float opacity = Float.parseFloat(data.backgroundOpacity);
                if (opacity != config.backgroundOpacity) {
                    config.backgroundOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
                    changed = true;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (changed) {
            plugin.saveConfig();
        }

        if (needsPreviewUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            updatePreview(cmd, config.backgroundImage);
            sendUpdate(cmd, events, false);
        }
    }

    public static class SettingsData {
        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec
                .builder(SettingsData.class, SettingsData::new)
                .addField(new KeyedCodec<>("@BindAltKey", Codec.BOOLEAN), (d, v) -> d.bindAltKey = v, d -> d.bindAltKey)
                .addField(new KeyedCodec<>("@EnableGiveButtons", Codec.BOOLEAN), (d, v) -> d.enableGiveButtons = v, d -> d.enableGiveButtons)
                .addField(new KeyedCodec<>("@DisableJetCommand", Codec.BOOLEAN), (d, v) -> d.disableJetCommand = v, d -> d.disableJetCommand)
                .addField(new KeyedCodec<>("@DisableGlyph", Codec.BOOLEAN), (d, v) -> d.disableGlyph = v, d -> d.disableGlyph)
                .addField(new KeyedCodec<>("@RequireCreativeOrOp", Codec.BOOLEAN), (d, v) -> d.requireCreativeOrOp = v, d -> d.requireCreativeOrOp)
                .addField(new KeyedCodec<>("@BackgroundImage", Codec.STRING), (d, v) -> d.backgroundImage = v, d -> d.backgroundImage)
                .addField(new KeyedCodec<>("@BackgroundOpacity", Codec.STRING), (d, v) -> d.backgroundOpacity = v, d -> d.backgroundOpacity)
                .build();

        private Boolean bindAltKey;
        private Boolean enableGiveButtons;
        private Boolean disableJetCommand;
        private Boolean disableGlyph;
        private Boolean requireCreativeOrOp;
        private String backgroundImage;
        private String backgroundOpacity;

        public SettingsData() {}
    }
}
