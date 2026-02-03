package dev.hytalemod.jet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.hytalemod.jet.JETPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persists per-player JET browser state (search, filters, selection, pagination)
 * so the browser restores when the user reopens it.
 */
public class BrowserStateStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "JET_browser_state.json";
    private static final long SAVE_DEBOUNCE_MS = 2_000;

    private final Map<UUID, BrowserState> states = new ConcurrentHashMap<>();
    private volatile long lastSaveTimeMs = 0;

    private static Path getDataDirectory() {
        return JETPlugin.getJetDataDirectory();
    }

    public void load() {
        try {
            Path dataDir = getDataDirectory();
            Path storageFile = dataDir.resolve(STORAGE_FILE);
            if (!Files.exists(storageFile)) {
                return;
            }
            try (Reader reader = new FileReader(storageFile.toFile())) {
                Type type = new TypeToken<Map<String, BrowserState>>() {}.getType();
                Map<String, BrowserState> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    states.clear();
                    for (Map.Entry<String, BrowserState> e : loaded.entrySet()) {
                        try {
                            states.put(UUID.fromString(e.getKey()), e.getValue());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    JETPlugin.getInstance().log(Level.INFO,"[JET] Loaded browser state for " + states.size() + " players");
                }
            }
        } catch (Exception e) {
            JETPlugin.getInstance().log(Level.SEVERE,"[JET] Failed to load browser state: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Path dataDir = getDataDirectory();
            Files.createDirectories(dataDir);
            Path storageFile = dataDir.resolve(STORAGE_FILE);
            Map<String, BrowserState> out = new HashMap<>();
            for (Map.Entry<UUID, BrowserState> e : states.entrySet()) {
                out.put(e.getKey().toString(), e.getValue());
            }
            try (Writer w = new FileWriter(storageFile.toFile())) {
                GSON.toJson(out, w);
            }
        } catch (Exception e) {
            JETPlugin.getInstance().log(Level.SEVERE,"[JET] Failed to save browser state: " + e.getMessage());
        }
    }

    public BrowserState getState(UUID playerUuid) {
        BrowserState s = states.get(playerUuid);
        return s != null ? s : null;
    }

    public void saveState(UUID playerUuid, BrowserState state) {
        if (state == null) return;
        states.put(playerUuid, state);
        long now = System.currentTimeMillis();
        if (now - lastSaveTimeMs >= SAVE_DEBOUNCE_MS) {
            lastSaveTimeMs = now;
            save();
        }
    }
}
