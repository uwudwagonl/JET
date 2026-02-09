package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;

import java.util.logging.Level;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.config.JETConfig;

/**
 * Settings GUI for JET configuration
 */
public class JETSettingsGui extends InteractiveCustomUIPage<JETSettingsGui.SettingsData> {

    private final JETPlugin plugin;
    private boolean bindOKey;
    private boolean appliesOnOP;
    private int cooldownMs;
    private boolean enableGiveButtons;

    public JETSettingsGui(PlayerRef playerRef, JETPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, SettingsData.CODEC);
        this.plugin = plugin;

        // Load current settings
        JETConfig config = plugin.getConfig();
        this.bindOKey = config.bindOKey;
        this.appliesOnOP = config.appliesOnOP;
        this.cooldownMs = config.cooldownMs;
        this.enableGiveButtons = config.enableGiveButtons;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        // Use inline UI definition
        cmd.appendInline("",
            "Group { " +
            "  LayoutMode: Top; " +
            "  Anchor: (CenterX: 0.5, CenterY: 0.5, Width: 600, Height: 400); " +
            "  Background: (Color: #1a1a1a(0.95)); " +
            "  Padding: (Full: 30); " +
            "  " +
            "  Label #Title { " +
            "    Style: (FontSize: 24, RenderBold: true, TextColor: #ffffff, HorizontalAlignment: Center); " +
            "    Padding: (Bottom: 20); " +
            "  } " +
            "  " +
            "  Group #Settings { " +
            "    LayoutMode: Top; " +
            "    Anchor: (Bottom: 0); " +
            "    Padding: (Top: 10); " +
            "    " +
            "    Group #BindOKeySetting { " +
            "      LayoutMode: Left; " +
            "      Anchor: (Bottom: 10); " +
            "      CheckBox #BindOKeyCheck { Anchor: (Width: 24, Height: 24, Right: 10); } " +
            "      Label #BindOKeyLabel { Style: (FontSize: 16, TextColor: #ffffff); } " +
            "    } " +
            "    " +
            "    Group #AppliesOnOPSetting { " +
            "      LayoutMode: Left; " +
            "      Anchor: (Bottom: 10); " +
            "      CheckBox #AppliesOnOPCheck { Anchor: (Width: 24, Height: 24, Right: 10); } " +
            "      Label #AppliesOnOPLabel { Style: (FontSize: 16, TextColor: #ffffff); } " +
            "    } " +
            "    " +
            "    Group #GiveButtonsSetting { " +
            "      LayoutMode: Left; " +
            "      Anchor: (Bottom: 10); " +
            "      CheckBox #GiveButtonsCheck { Anchor: (Width: 24, Height: 24, Right: 10); } " +
            "      Label #GiveButtonsLabel { Style: (FontSize: 16, TextColor: #ffffff); } " +
            "    } " +
            "  } " +
            "  " +
            "  Group #Buttons { " +
            "    LayoutMode: Left; " +
            "    Anchor: (Bottom: 0, CenterX: 0.5); " +
            "    Padding: (Top: 30); " +
            "    " +
            "    Button #SaveButton { " +
            "      Anchor: (Width: 120, Height: 40, Right: 10); " +
            "      Background: (Color: #00aa00); " +
            "      Style: (Hovered: (Background: #00cc00)); " +
            "      Label { " +
            "        Text: \"Save\"; " +
            "        Style: (FontSize: 16, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center); " +
            "      } " +
            "    } " +
            "    " +
            "    Button #CancelButton { " +
            "      Anchor: (Width: 120, Height: 40); " +
            "      Background: (Color: #aa0000); " +
            "      Style: (Hovered: (Background: #cc0000)); " +
            "      Label { " +
            "        Text: \"Cancel\"; " +
            "        Style: (FontSize: 16, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center); " +
            "      } " +
            "    } " +
            "  } " +
            "}"
        );

        // Set title
        cmd.set("#Title.TextSpans", Message.raw("JET Settings"));

        // Set checkbox labels
        cmd.set("#BindOKeyLabel.TextSpans", Message.raw("Bind O key to open JET"));
        cmd.set("#AppliesOnOPLabel.TextSpans", Message.raw("Apply O key binding to OPs"));
        cmd.set("#GiveButtonsLabel.TextSpans", Message.raw("Enable Give Item buttons (Creative/OP)"));

        // Set checkbox values
        cmd.set("#BindOKeyCheck.Value", bindOKey);
        cmd.set("#AppliesOnOPCheck.Value", appliesOnOP);
        cmd.set("#GiveButtonsCheck.Value", enableGiveButtons);

        // Event bindings [WIP]
        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BindOKeyCheck",
            EventData.of("@BindOKey", "#BindOKeyCheck.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#AppliesOnOPCheck",
            EventData.of("@AppliesOnOP", "#AppliesOnOPCheck.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#GiveButtonsCheck",
            EventData.of("@EnableGiveButtons", "#GiveButtonsCheck.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveButton",
            EventData.of("Action", "save"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelButton",
            EventData.of("Action", "cancel"),
            false
        );
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, SettingsData data) {
        super.handleDataEvent(ref, store, data);

        // Update local state
        if (data.bindOKey != null) {
            this.bindOKey = data.bindOKey;
        }

        if (data.appliesOnOP != null) {
            this.appliesOnOP = data.appliesOnOP;
        }

        if (data.enableGiveButtons != null) {
            this.enableGiveButtons = data.enableGiveButtons;
        }

        // Handle button actions
        if (data.action != null) {
            if ("save".equals(data.action)) {
                saveSettings();
                close();
            } else if ("cancel".equals(data.action)) {
                close();
            }
        }
    }

    private void saveSettings() {
        JETConfig config = plugin.getConfig();
        boolean needsRestart = config.bindOKey != this.bindOKey;

        config.bindOKey = this.bindOKey;
        config.appliesOnOP = this.appliesOnOP;
        config.cooldownMs = this.cooldownMs;
        config.enableGiveButtons = this.enableGiveButtons;

        // Save config file
        plugin.saveConfig();
        try {
            plugin.log(Level.INFO, "[JET] Settings saved by " + playerRef.getUsername());
            if (needsRestart) {
                playerRef.sendMessage(Message.raw("[JET] O key binding changed. Restart required for changes to take effect.").color("#FFAA00"));
            } else {
                playerRef.sendMessage(Message.raw("[JET] Settings saved successfully!").color("#55FF55"));
            }
        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("[JET] Failed to save settings: " + e.getMessage()).color("#FF5555"));
        }
    }

    public static class SettingsData {
        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec
                .builder(SettingsData.class, SettingsData::new)
                .addField(new KeyedCodec<>("@BindOKey", Codec.BOOLEAN), (d, v) -> d.bindOKey = v, d -> d.bindOKey)
                .addField(new KeyedCodec<>("@AppliesOnOP", Codec.BOOLEAN), (d, v) -> d.appliesOnOP = v, d -> d.appliesOnOP)
                .addField(new KeyedCodec<>("@EnableGiveButtons", Codec.BOOLEAN), (d, v) -> d.enableGiveButtons = v, d -> d.enableGiveButtons)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .build();

        private Boolean bindOKey;
        private Boolean appliesOnOP;
        private Boolean enableGiveButtons;
        private String action;

        public SettingsData() {}
    }
}
