package dev.hytalemod.jet.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SetRegistry pure-logic methods.
 * Tests extractMaterial/reload indirectly (requires Item stubs) are excluded —
 * only pure string logic (isSlotWord, getDisplayName, empty state) is tested here.
 */
class SetRegistryTest {

    private SetRegistry registry;
    private Method isSlotWordMethod;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SetRegistry();
        isSlotWordMethod = SetRegistry.class.getDeclaredMethod("isSlotWord", String.class);
        isSlotWordMethod.setAccessible(true);
    }

    @Test
    @DisplayName("armor slot words are detected")
    void armorSlotWords() throws Exception {
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Chest"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Head"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Legs"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Feet"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Hands"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Gauntlets"));
    }

    @Test
    @DisplayName("weapon slot words are detected")
    void weaponSlotWords() throws Exception {
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Longsword"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Shortsword"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Shortbow"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Dagger"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Spear"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Mace"));
    }

    @Test
    @DisplayName("tool slot words are detected")
    void toolSlotWords() throws Exception {
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Pickaxe"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Shovel"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Hoe"));
    }

    @Test
    @DisplayName("shield is detected as slot word")
    void shieldSlotWord() throws Exception {
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "Shield"));
    }

    @Test
    @DisplayName("material names are not slot words")
    void materialNamesNotSlotWords() throws Exception {
        assertFalse((boolean) isSlotWordMethod.invoke(registry, "Iron"));
        assertFalse((boolean) isSlotWordMethod.invoke(registry, "Adamantite"));
        assertFalse((boolean) isSlotWordMethod.invoke(registry, "Stone"));
        assertFalse((boolean) isSlotWordMethod.invoke(registry, "Block"));
        assertFalse((boolean) isSlotWordMethod.invoke(registry, "Copper"));
    }

    @Test
    @DisplayName("case insensitive slot word matching")
    void caseInsensitive() throws Exception {
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "SWORD"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "chest"));
        assertTrue((boolean) isSlotWordMethod.invoke(registry, "PickAxe"));
    }

    @Test
    @DisplayName("empty registry has no sets")
    void emptyRegistry() {
        assertEquals(0, registry.size());
        assertTrue(registry.getSetNames().isEmpty());
        assertNull(registry.getSetForItem("anything"));
    }

    @Test
    @DisplayName("getDisplayName replaces underscores with spaces")
    void displayName() {
        assertEquals("Dark Iron", registry.getDisplayName("Dark_Iron"));
        assertEquals("Adamantite", registry.getDisplayName("Adamantite"));
        assertEquals("Void Crystal", registry.getDisplayName("Void_Crystal"));
    }

    @Test
    @DisplayName("getSetItems returns empty list for unknown set")
    void unknownSet() {
        assertTrue(registry.getSetItems("nonexistent").isEmpty());
    }
}
