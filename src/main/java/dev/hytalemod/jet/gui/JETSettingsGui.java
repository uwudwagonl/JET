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

public class JETSettingsGui extends InteractiveCustomUIPage<JETSettingsGui.SettingsData> {

    private final JETPlugin plugin;
    private boolean bindAltKey;
    private boolean enableGiveButtons;

    public JETSettingsGui(PlayerRef playerRef, JETPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, SettingsData.CODEC);
        this.plugin = plugin;

        JETConfig config = plugin.getConfig();
        this.bindAltKey = config.bindAltKey;
        this.enableGiveButtons = config.enableGiveButtons;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Settings.ui");

        // Set button icons
        cmd.set("#SaveButton #SaveIcon.ItemId", "JET_Icon_Save");
        cmd.set("#CancelButton #CancelIcon.ItemId", "JET_Icon_Cancel");

        // Set checkbox values
        cmd.set("#BindAltKeyCheck #CheckBox.Value", bindAltKey);
        cmd.set("#GiveButtonsCheck #CheckBox.Value", enableGiveButtons);

        // Checkbox event bindings
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

        // Button event bindings
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

        if (data.bindAltKey != null) {
            this.bindAltKey = data.bindAltKey;
        }

        if (data.enableGiveButtons != null) {
            this.enableGiveButtons = data.enableGiveButtons;
        }

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

        config.bindAltKey = this.bindAltKey;
        config.enableGiveButtons = this.enableGiveButtons;

        plugin.saveConfig();
        try {
            plugin.log(Level.INFO, "[JET] Settings saved by " + playerRef.getUsername());
            playerRef.sendMessage(Message.raw("[JET] Settings saved!").color("#55FF55"));
        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("[JET] Failed to save settings: " + e.getMessage()).color("#FF5555"));
        }
    }

    public static class SettingsData {
        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec
                .builder(SettingsData.class, SettingsData::new)
                .addField(new KeyedCodec<>("@BindAltKey", Codec.BOOLEAN), (d, v) -> d.bindAltKey = v, d -> d.bindAltKey)
                .addField(new KeyedCodec<>("@EnableGiveButtons", Codec.BOOLEAN), (d, v) -> d.enableGiveButtons = v, d -> d.enableGiveButtons)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .build();

        private Boolean bindAltKey;
        private Boolean enableGiveButtons;
        private String action;

        public SettingsData() {}
    }
}
