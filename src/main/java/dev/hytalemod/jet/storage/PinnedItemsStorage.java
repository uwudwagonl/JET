package dev.hytalemod.jet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.hytalemod.jet.JETPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages persistent storage of pinned items per player.
 * Stores pinned items in JSON format in the Hytale user data directory.
 */
public class PinnedItemsStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "JET_pinned_items.json";

    // In-memory cache: Player UUID -> Set of pinned item IDs
    private final Map<UUID, Set<String>> pinnedItems = new ConcurrentHashMap<>();

    /**
     * Get the data directory for JET plugin storage
     */
    private static Path getDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            // Windows: %APPDATA%\Roaming\Hytale\UserData\JET
            return Paths.get(userHome, "AppData", "Roaming", "Hytale", "UserData", "JET");
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/Hytale/UserData/JET
            return Paths.get(userHome, "Library", "Application Support", "Hytale", "UserData", "JET");
        } else {
            // Linux: ~/.local/share/Hytale/UserData/JET
            return Paths.get(userHome, ".local", "share", "Hytale", "UserData", "JET");
        }
    }

    /**
     * Load pinned items from disk
     */
    public void load() {
        try {
            Path dataDir = getDataDirectory();
            Path storageFile = dataDir.resolve(STORAGE_FILE);

            if (!Files.exists(storageFile)) {
                JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] No existing pinned items file found, starting fresh");
                return;
            }

            Reader reader = new FileReader(storageFile.toFile());
            Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
            Map<String, Set<String>> loadedData = GSON.fromJson(reader, type);
            reader.close();

            if (loadedData != null) {
                pinnedItems.clear();
                // Convert String UUIDs back to UUID objects
                for (Map.Entry<String, Set<String>> entry : loadedData.entrySet()) {
                    try {
                        UUID playerUuid = UUID.fromString(entry.getKey());
                        pinnedItems.put(playerUuid, new HashSet<>(entry.getValue()));
                    } catch (IllegalArgumentException e) {
                        JETPlugin.getInstance().getLogger().at(Level.WARNING).log("[JET] Invalid UUID in pinned items: " + entry.getKey());
                    }
                }
                JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Loaded pinned items for " + pinnedItems.size() + " players");
            }
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.SEVERE).log("[JET] Failed to load pinned items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save pinned items to disk
     */
    public void save() {
        try {
            Path dataDir = getDataDirectory();
            Files.createDirectories(dataDir);
            Path storageFile = dataDir.resolve(STORAGE_FILE);

            // Convert UUID keys to strings for JSON serialization
            Map<String, Set<String>> saveData = new HashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : pinnedItems.entrySet()) {
                saveData.put(entry.getKey().toString(), entry.getValue());
            }

            Writer writer = new FileWriter(storageFile.toFile());
            GSON.toJson(saveData, writer);
            writer.close();

            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Saved pinned items for " + pinnedItems.size() + " players");
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.SEVERE).log("[JET] Failed to save pinned items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get pinned items for a player
     */
    public Set<String> getPinnedItems(UUID playerUuid) {
        return pinnedItems.getOrDefault(playerUuid, new HashSet<>());
    }

    /**
     * Check if an item is pinned for a player
     */
    public boolean isPinned(UUID playerUuid, String itemId) {
        Set<String> items = pinnedItems.get(playerUuid);
        return items != null && items.contains(itemId);
    }

    /**
     * Pin an item for a player
     */
    public void pinItem(UUID playerUuid, String itemId) {
        pinnedItems.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(itemId);
        save();
    }

    /**
     * Unpin an item for a player
     */
    public void unpinItem(UUID playerUuid, String itemId) {
        Set<String> items = pinnedItems.get(playerUuid);
        if (items != null) {
            items.remove(itemId);
            if (items.isEmpty()) {
                pinnedItems.remove(playerUuid);
            }
            save();
        }
    }

    /**
     * Toggle pin status for an item
     * @return true if item is now pinned, false if unpinned
     */
    public boolean togglePin(UUID playerUuid, String itemId) {
        if (isPinned(playerUuid, itemId)) {
            unpinItem(playerUuid, itemId);
            return false;
        } else {
            pinItem(playerUuid, itemId);
            return true;
        }
    }

    /**
     * Clear all pinned items for a player
     */
    public void clearPinnedItems(UUID playerUuid) {
        pinnedItems.remove(playerUuid);
        save();
    }

    /**
     * Get count of pinned items for a player
     */
    public int getPinnedCount(UUID playerUuid) {
        Set<String> items = pinnedItems.get(playerUuid);
        return items != null ? items.size() : 0;
    }
}
