package dev.hytalemod.jet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class JETConfigTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    @DisplayName("default config has expected values")
    void defaults() {
        JETConfig config = new JETConfig();
        assertTrue(config.bindAltKey);
        assertFalse(config.enableGiveButtons);
        assertFalse(config.disableJetCommand);
        assertEquals("none", config.backgroundImage);
        assertEquals(0.8f, config.backgroundOpacity, 0.001f);
        assertFalse(config.disableGlyph);
        assertFalse(config.requireCreativeOrOp);
    }

    @Test
    @DisplayName("GSON round-trip preserves all fields")
    void gsonRoundTrip() {
        JETConfig original = new JETConfig();
        original.bindAltKey = false;
        original.enableGiveButtons = true;
        original.disableJetCommand = true;
        original.backgroundImage = "JET_Bg_Dark";
        original.backgroundOpacity = 0.5f;
        original.disableGlyph = true;
        original.requireCreativeOrOp = true;

        String json = GSON.toJson(original);
        JETConfig restored = GSON.fromJson(json, JETConfig.class);

        assertEquals(original.bindAltKey, restored.bindAltKey);
        assertEquals(original.enableGiveButtons, restored.enableGiveButtons);
        assertEquals(original.disableJetCommand, restored.disableJetCommand);
        assertEquals(original.backgroundImage, restored.backgroundImage);
        assertEquals(original.backgroundOpacity, restored.backgroundOpacity, 0.001f);
        assertEquals(original.disableGlyph, restored.disableGlyph);
        assertEquals(original.requireCreativeOrOp, restored.requireCreativeOrOp);
    }

    @Test
    @DisplayName("missing fields in JSON get default values")
    void missingFields() {
        String json = "{}";
        JETConfig config = GSON.fromJson(json, JETConfig.class);
        // GSON leaves primitives at Java defaults (false/0) for missing fields
        assertFalse(config.enableGiveButtons);
        assertFalse(config.disableJetCommand);
        assertFalse(config.disableGlyph);
        assertFalse(config.requireCreativeOrOp);
    }
}
