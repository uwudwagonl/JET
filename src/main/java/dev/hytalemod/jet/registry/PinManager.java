package dev.hytalemod.jet.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages pinned/favorited items per player with persistent storage.
 */
public class PinManager {

    private static final String PINS_FILE = "jet_pins.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Set<String>> pinnedItems = new ConcurrentHashMap<>();
    private final Logger logger;
    private final File dataFile;

    public PinManager(Logger logger, File dataDirectory) {
        this.logger = logger;
        this.dataFile = new File(dataDirectory, PINS_FILE);
        load();
    }

    /**
     * Pin an item for a player
     */
    public boolean pinItem(UUID playerId, String itemId) {
        Set<String> pins = pinnedItems.computeIfAbsent(playerId, k -> new HashSet<>());
        boolean added = pins.add(itemId);
        if (added) {
            save();
        }
        return added;
    }

    /**
     * Unpin an item for a player
     */
    public boolean unpinItem(UUID playerId, String itemId) {
        Set<String> pins = pinnedItems.get(playerId);
        if (pins != null) {
            boolean removed = pins.remove(itemId);
            if (removed) {
                if (pins.isEmpty()) {
                    pinnedItems.remove(playerId);
                }
                save();
            }
            return removed;
        }
        return false;
    }

    /**
     * Toggle pin state for an item
     */
    public boolean togglePin(UUID playerId, String itemId) {
        if (isPinned(playerId, itemId)) {
            unpinItem(playerId, itemId);
            return false;
        } else {
            pinItem(playerId, itemId);
            return true;
        }
    }

    /**
     * Check if an item is pinned for a player
     */
    public boolean isPinned(UUID playerId, String itemId) {
        Set<String> pins = pinnedItems.get(playerId);
        return pins != null && pins.contains(itemId);
    }

    /**
     * Get all pinned items for a player (sorted)
     */
    public List<String> getPinnedItems(UUID playerId) {
        Set<String> pins = pinnedItems.get(playerId);
        if (pins == null || pins.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sorted = new ArrayList<>(pins);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Get count of pinned items for a player
     */
    public int getPinnedCount(UUID playerId) {
        Set<String> pins = pinnedItems.get(playerId);
        return pins == null ? 0 : pins.size();
    }

    /**
     * Clear all pins for a player
     */
    public void clearPins(UUID playerId) {
        if (pinnedItems.remove(playerId) != null) {
            save();
        }
    }

    /**
     * Load pinned items from JSON file
     */
    private void load() {
        if (!dataFile.exists()) {
            logger.at(Level.INFO).log("[JET] No pins file found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Set<String>>>(){}.getType();
            Map<String, Set<String>> loaded = GSON.fromJson(reader, type);

            if (loaded != null) {
                // Convert string UUIDs back to UUID objects
                for (Map.Entry<String, Set<String>> entry : loaded.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        pinnedItems.put(uuid, new HashSet<>(entry.getValue()));
                    } catch (IllegalArgumentException e) {
                        logger.at(Level.WARNING).log("[JET] Invalid UUID in pins file: " + entry.getKey());
                    }
                }

                int totalPins = pinnedItems.values().stream().mapToInt(Set::size).sum();
                logger.at(Level.INFO).log("[JET] Loaded " + totalPins + " pinned items for " + pinnedItems.size() + " players");
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).log("[JET] Failed to load pins file: " + e.getMessage());
        }
    }

    /**
     * Save pinned items to JSON file
     */
    private void save() {
        try {
            // Ensure parent directory exists
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // Convert UUID keys to strings for JSON
            Map<String, Set<String>> toSave = new HashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : pinnedItems.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }

            try (FileWriter writer = new FileWriter(dataFile)) {
                GSON.toJson(toSave, writer);
            }
        } catch (IOException e) {
            logger.at(Level.SEVERE).log("[JET] Failed to save pins file: " + e.getMessage());
        }
    }
}
