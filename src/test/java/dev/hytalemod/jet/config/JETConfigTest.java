package dev.hytalemod.jet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class JETConfigTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    @DisplayName("default server config has expected values")
    void defaults() {
        JETConfig config = new JETConfig();
        assertFalse(config.enableGiveButtons);
        assertFalse(config.disableJetCommand);
        assertFalse(config.disableGlyph);
        assertFalse(config.requireCreativeOrOp);
    }

    @Test
    @DisplayName("GSON round-trip preserves all server config fields")
    void gsonRoundTrip() {
        JETConfig original = new JETConfig();
        original.enableGiveButtons = true;
        original.disableJetCommand = true;
        original.disableGlyph = true;
        original.requireCreativeOrOp = true;

        String json = GSON.toJson(original);
        JETConfig restored = GSON.fromJson(json, JETConfig.class);

        assertEquals(original.enableGiveButtons, restored.enableGiveButtons);
        assertEquals(original.disableJetCommand, restored.disableJetCommand);
        assertEquals(original.disableGlyph, restored.disableGlyph);
        assertEquals(original.requireCreativeOrOp, restored.requireCreativeOrOp);
    }

    @Test
    @DisplayName("missing fields in JSON get default values")
    void missingFields() {
        String json = "{}";
        JETConfig config = GSON.fromJson(json, JETConfig.class);
        assertFalse(config.enableGiveButtons);
        assertFalse(config.disableJetCommand);
        assertFalse(config.disableGlyph);
        assertFalse(config.requireCreativeOrOp);
    }

    @Test
    @DisplayName("default user config has expected values")
    void userDefaults() {
        JETUserConfig config = new JETUserConfig();
        assertTrue(config.bindAltKey);
        assertEquals("none", config.backgroundImage);
        assertEquals(0.8f, config.backgroundOpacity, 0.001f);
    }

    @Test
    @DisplayName("GSON round-trip preserves all user config fields")
    void userGsonRoundTrip() {
        JETUserConfig original = new JETUserConfig();
        original.bindAltKey = false;
        original.backgroundImage = "JET_Bg_Dark";
        original.backgroundOpacity = 0.5f;

        String json = GSON.toJson(original);
        JETUserConfig restored = GSON.fromJson(json, JETUserConfig.class);

        assertEquals(original.bindAltKey, restored.bindAltKey);
        assertEquals(original.backgroundImage, restored.backgroundImage);
        assertEquals(original.backgroundOpacity, restored.backgroundOpacity, 0.001f);
    }
}
