package dev.hytalemod.jet.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;

import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.config.JETConfig;

public class JETSettingsGui extends InteractiveCustomUIPage<JETSettingsGui.SettingsData> {

    private final JETPlugin plugin;

    public JETSettingsGui(PlayerRef playerRef, JETPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, SettingsData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/JET_Settings.ui");

        JETConfig config = plugin.getConfig();
        cmd.set("#BindAltKeyCheck #CheckBox.Value", config.bindAltKey);
        cmd.set("#GiveButtonsCheck #CheckBox.Value", config.enableGiveButtons);

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
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, SettingsData data) {
        super.handleDataEvent(ref, store, data);

        JETConfig config = plugin.getConfig();
        boolean changed = false;

        if (data.bindAltKey != null) {
            config.bindAltKey = data.bindAltKey;
            changed = true;
        }

        if (data.enableGiveButtons != null) {
            config.enableGiveButtons = data.enableGiveButtons;
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
        }
    }

    public static class SettingsData {
        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec
                .builder(SettingsData.class, SettingsData::new)
                .addField(new KeyedCodec<>("@BindAltKey", Codec.BOOLEAN), (d, v) -> d.bindAltKey = v, d -> d.bindAltKey)
                .addField(new KeyedCodec<>("@EnableGiveButtons", Codec.BOOLEAN), (d, v) -> d.enableGiveButtons = v, d -> d.enableGiveButtons)
                .build();

        private Boolean bindAltKey;
        private Boolean enableGiveButtons;

        public SettingsData() {}
    }
}
