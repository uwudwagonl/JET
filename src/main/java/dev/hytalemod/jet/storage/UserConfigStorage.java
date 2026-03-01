package dev.hytalemod.jet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.hytalemod.jet.JETPlugin;
import dev.hytalemod.jet.config.JETUserConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-player config storage. Each player gets their own JETUserConfig
 * with personal preferences (background, alt key, etc.).
 */
public class UserConfigStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "JET_user_configs.json";

    private final Map<UUID, JETUserConfig> configs = new ConcurrentHashMap<>();

    private static Path getDataDirectory() {
        return JETPlugin.getJetDataDirectory();
    }

    public void load() {
        try {
            Path dataDir = getDataDirectory();
            Path storageFile = dataDir.resolve(STORAGE_FILE);
            if (!Files.exists(storageFile)) {
                JETPlugin.getInstance().log(Level.INFO, "[JET] No existing user configs file found, starting fresh");
                return;
            }
            try (Reader reader = new FileReader(storageFile.toFile())) {
                Type type = new TypeToken<Map<String, JETUserConfig>>() {}.getType();
                Map<String, JETUserConfig> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    configs.clear();
                    for (Map.Entry<String, JETUserConfig> e : loaded.entrySet()) {
                        try {
                            configs.put(UUID.fromString(e.getKey()), e.getValue());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    JETPlugin.getInstance().log(Level.INFO, "[JET] Loaded user configs for " + configs.size() + " players");
                }
            }
        } catch (Exception e) {
            JETPlugin.getInstance().log(Level.SEVERE, "[JET] Failed to load user configs: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Path dataDir = getDataDirectory();
            Files.createDirectories(dataDir);
            Path storageFile = dataDir.resolve(STORAGE_FILE);
            Map<String, JETUserConfig> out = new HashMap<>();
            for (Map.Entry<UUID, JETUserConfig> e : configs.entrySet()) {
                out.put(e.getKey().toString(), e.getValue());
            }
            try (Writer w = new FileWriter(storageFile.toFile())) {
                GSON.toJson(out, w);
            }
        } catch (Exception e) {
            JETPlugin.getInstance().log(Level.SEVERE, "[JET] Failed to save user configs: " + e.getMessage());
        }
    }

    /**
     * Get config for a player. Returns a new default config if none exists yet.
     */
    public JETUserConfig getConfig(UUID playerUuid) {
        return configs.computeIfAbsent(playerUuid, k -> new JETUserConfig());
    }

    /**
     * Save a player's config and persist to disk.
     */
    public void saveConfig(UUID playerUuid, JETUserConfig config) {
        configs.put(playerUuid, config);
        save();
    }
}
