package dev.hytalemod.jet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.command.*;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.config.JETConfig;
import dev.hytalemod.jet.interaction.OpenJETInteraction;
import dev.hytalemod.jet.filter.OKeyPacketFilter;
import dev.hytalemod.jet.gui.JETGui;
import dev.hytalemod.jet.registry.DropListRegistry;
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.RecipeRegistry;
import dev.hytalemod.jet.storage.BrowserStateStorage;
import dev.hytalemod.jet.storage.PinnedItemsStorage;
import dev.hytalemod.jet.system.RecipeHudUpdateSystem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JET - Item Encyclopedia Plugin
 */
public class JETPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0-beta.10";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "JET_config.json";

    private static JETPlugin instance;
    private ItemRegistry itemRegistry;
    private RecipeRegistry recipeRegistry;
    private DropListRegistry dropListRegistry;
    private PinnedItemsStorage pinnedItemsStorage;
    private BrowserStateStorage browserStateStorage;

    // O Key binding
    private JETConfig config;
    private PacketFilter oKeyFilter;
    private final Map<UUID, Long> lastTriggerTime = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRef> pendingTriggers = new ConcurrentHashMap<>();
    private static final long PENDING_TRIGGER_TIMEOUT_MS = 500L;

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, CraftingRecipe> RECIPES = new HashMap<>();
    public static Map<String, ItemDropList> DROP_LISTS = new HashMap<>();
    public static Map<String, List<String>> ITEM_TO_RECIPES = new HashMap<>();
    public static Map<String, List<String>> ITEM_FROM_RECIPES = new HashMap<>();

    // Custom JET log file writer
    private PrintWriter jetLogWriter;
    private final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private ComponentType<EntityStore, RecipeHudComponent> recipeHudComponentType;


    public JETPlugin(JavaPluginInit init) {
        super(init);
    }

    /**
     * Get the JET data directory in UserData/JET (not Mods folder)
     */
    public static Path getJetDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        if (os.contains("win")) {
            return Paths.get(userHome, "AppData", "Roaming", "Hytale", "UserData", "JET");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "Hytale", "UserData", "JET");
        } else {
            return Paths.get(userHome, ".local", "share", "Hytale", "UserData", "JET");
        }
    }

    /**
     * Get the JET logs directory in UserData/Logs/jet_logs
     */
    public static Path getLogsDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        if (os.contains("win")) {
            return Paths.get(userHome, "AppData", "Roaming", "Hytale", "UserData", "Logs", "jet_logs");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "Hytale", "UserData", "Logs", "jet_logs");
        } else {
            return Paths.get(userHome, ".local", "share", "Hytale", "UserData", "Logs", "jet_logs");
        }
    }

    @Override
    protected void setup() {
        super.setup();

        instance = this;
        recipeHudComponentType = getEntityStoreRegistry().registerComponent(
                RecipeHudComponent.class,
                "jet:recipe_hud",
                RecipeHudComponent.CODEC
        );
        RecipeHudComponent.init(recipeHudComponentType);

        // Setup custom JET logger that writes to UserData/Logs/jet_logs
        setupJetLogger();
        itemRegistry = new ItemRegistry();
        recipeRegistry = new RecipeRegistry();
        dropListRegistry = new DropListRegistry();
        pinnedItemsStorage = new PinnedItemsStorage();
        pinnedItemsStorage.load();
        browserStateStorage = new BrowserStateStorage();
        browserStateStorage.load();

        // Load config
        loadConfig();

        // Register commands
        getCommandRegistry().registerCommand(new JETCommand());
        getCommandRegistry().registerCommand(new JETPinnedCommand());
        // WIP: getCommandRegistry().registerCommand(new JETSettingsCommand());
        // WIP: getCommandRegistry().registerCommand(new JETBindCommand());
        // WIP: getCommandRegistry().registerCommand(new JETRecipeHudCommand());
        // Register asset load events
        getEventRegistry().register(LoadedAssetsEvent.class, Item.class, JETPlugin::onItemsLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, JETPlugin::onRecipesLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, ItemDropList.class, JETPlugin::onDropListsLoaded);
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, RecipeHudUpdateSystem::onInventoryChange);

        // Register custom interaction for Pex Glyph item
        Interaction.CODEC.register("OpenJET", OpenJETInteraction.class, OpenJETInteraction.CODEC);

        // Setup O key binding if enabled
        if (config != null && config.bindOKey) {
            setupOKeyBinding();
        }

        log(Level.INFO, "[JET] Plugin enabled - v" + VERSION);
        String keyBindInfo = (config != null && config.bindOKey) ? ", O key bound to /jet" : "";
        log(Level.INFO, "[JET] Use /jet or /j to open item browser" + keyBindInfo);
        log(Level.INFO, "[JET] Tip: Use '/jet <itemId>' to search directly (e.g. /jet Block_Stone)");


    }


    @Override
    protected void shutdown() {
        super.shutdown();
        if (oKeyFilter != null) {
            PacketAdapters.deregisterInbound(oKeyFilter);
        }
        // Close JET log writer
        if (jetLogWriter != null) {
            jetLogWriter.close();
        }
    }

    /**
     * Setup custom JET logger that writes to UserData/Logs/jet_logs
     */
    private void setupJetLogger() {
        try {
            Path logsDir = getLogsDirectory();
            Files.createDirectories(logsDir);

            // Create log file with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            Path logFile = logsDir.resolve("jet_" + timestamp + ".log");

            // Open file for appending
            jetLogWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);

        } catch (Exception e) {
            // Silently fail - can't log if logger setup failed
        }
    }

    /**
     * Log a message to the JET log file only (not to Hytale's main log)
     */
    public void log(Level level, String message) {
        // Write to our JET log file only
        if (jetLogWriter != null) {
            String timestamp = logDateFormat.format(new Date());
            jetLogWriter.println("[" + timestamp + "] [" + level.getName() + "] " + message);
        }
    }

    @SuppressWarnings("unchecked")
    private static void onItemsLoaded(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = ((DefaultAssetMap<String, Item>) event.getAssetMap()).getAssetMap();
        instance.itemRegistry.reload(ITEMS);
        instance.log(Level.INFO, "[JET] Loaded " + instance.itemRegistry.size() + " items");
    }

    @SuppressWarnings("unchecked")
    private static void onRecipesLoaded(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        Map<String, CraftingRecipe> recipes = event.getLoadedAssets();

        if (recipes == null || recipes.isEmpty()) {
            instance.log(Level.WARNING, "[JET] No recipes in LoadedAssetsEvent");
            return;
        }

        Method getInputMethod = null;
        try {
            getInputMethod = CraftingRecipe.class.getMethod("getInput");
        } catch (NoSuchMethodException e) {
        }

        for (CraftingRecipe recipe : recipes.values()) {
            RECIPES.put(recipe.getId(), recipe);
            for (MaterialQuantity output : recipe.getOutputs()) {
                ITEM_TO_RECIPES.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
            }

            if (getInputMethod != null) {
                try {
                    Object inputsObj = getInputMethod.invoke(recipe);
                    if (inputsObj != null) {
                        processRecipeInputs(inputsObj, recipe.getId());
                    }
                } catch (Exception e) {
                }
            } else {
                String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
                for (String methodName : methodNames) {
                    try {
                        Method fallbackMethod = CraftingRecipe.class.getMethod(methodName);
                        Object inputsObj = fallbackMethod.invoke(recipe);
                        if (inputsObj != null) {
                            processRecipeInputs(inputsObj, recipe.getId());
                            break;
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }

        instance.recipeRegistry.reload(recipes);
        instance.log(Level.INFO, "[JET] Loaded " + instance.recipeRegistry.size() + " recipes");
        instance.log(Level.INFO, "[JET] Built recipe maps - ITEM_TO_RECIPES: " + ITEM_TO_RECIPES.size() + " items, ITEM_FROM_RECIPES: " + ITEM_FROM_RECIPES.size() + " items");
    }

    private static void processRecipeInputs(Object inputsObj, String recipeId) {
        if (inputsObj instanceof MaterialQuantity) {
            MaterialQuantity input = (MaterialQuantity) inputsObj;
            if (input != null && input.getItemId() != null) {
                ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
            }
        } else if (inputsObj instanceof List) {
            List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
            if (inputs != null && !inputs.isEmpty()) {
                for (MaterialQuantity input : inputs) {
                    if (input != null && input.getItemId() != null) {
                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                    }
                }
            }
        } else if (inputsObj instanceof MaterialQuantity[]) {
            MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
            if (inputs != null && inputs.length > 0) {
                for (MaterialQuantity input : inputs) {
                    if (input != null && input.getItemId() != null) {
                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                    }
                }
            }
        } else if (inputsObj instanceof Collection) {
            Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
            if (inputs != null && !inputs.isEmpty()) {
                for (MaterialQuantity input : inputs) {
                    if (input != null && input.getItemId() != null) {
                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                    }
                }
            }
        }
    }

    public static JETPlugin getInstance() {
        return instance;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public RecipeRegistry getRecipeRegistry() {
        return recipeRegistry;
    }

    public PinnedItemsStorage getPinnedItemsStorage() {
        return pinnedItemsStorage;
    }

    public BrowserStateStorage getBrowserStateStorage() {
        return browserStateStorage;
    }

    public DropListRegistry getDropListRegistry() {
        return dropListRegistry;
    }

    @SuppressWarnings("unchecked")
    private static void onDropListsLoaded(LoadedAssetsEvent<String, ItemDropList, DefaultAssetMap<String, ItemDropList>> event) {
        Map<String, ItemDropList> dropLists = event.getLoadedAssets();

        if (dropLists == null || dropLists.isEmpty()) {
            instance.log(Level.WARNING, "[JET] No drop lists in LoadedAssetsEvent");
            return;
        }

        DROP_LISTS = new HashMap<>(dropLists);
        instance.dropListRegistry.reload(dropLists);
        instance.log(Level.INFO, "[JET] Loaded " + instance.dropListRegistry.size() + " drop lists");
    }

    // ==================== Config Management ====================

    private void loadConfig() {
        try {
            Path configDir = getJetDataDirectory();
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve(CONFIG_FILE);

            if (!Files.exists(configPath)) {
                config = new JETConfig();
                saveConfig(configPath);
                log(Level.INFO, "[JET] Created default config at " + configPath);
            } else {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                config = GSON.fromJson(json, JETConfig.class);
                log(Level.INFO, "[JET] Loaded config from " + configPath);
            }
        } catch (Exception e) {
            log(Level.WARNING, "[JET] Failed to load config, using defaults: " + e.getMessage());
            config = new JETConfig();
        }
    }

    private void saveConfig(Path configPath) {
        try {
            String json = GSON.toJson(config);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log(Level.WARNING, "[JET] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Save the current config to the data directory
     */
    public void saveConfig() {
        try {
            Path configDir = getJetDataDirectory();
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve(CONFIG_FILE);
            saveConfig(configPath);
        } catch (IOException e) {
            log(Level.WARNING, "[JET] Failed to save config: " + e.getMessage());
        }
    }

    public JETConfig getConfig() {
        return config;
    }

    // ==================== O Key Binding ====================

    private void setupOKeyBinding() {
        // Register packet filter to intercept O key presses
        oKeyFilter = PacketAdapters.registerInbound(new OKeyPacketFilter(this));

        // Listen for gamemode change events
        getEventRegistry().registerGlobal(ChangeGameModeEvent.class, event -> {
            if (event.getGameMode() == GameMode.Creative) {
                PlayerRef playerRef = consumePendingTrigger();
                if (playerRef != null && handleGameModeChangeEvent(playerRef)) {
                    event.setCancelled(true);
                }
            }
        });

        // Clean up on disconnect
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef != null && playerRef.getUuid() != null) {
                lastTriggerTime.remove(playerRef.getUuid());
                pendingTriggers.remove(playerRef.getUuid());
            }
        });

        log(Level.INFO, "[JET] O key binding enabled");
    }

    public boolean handleGameModeSwap(PlayerRef playerRef) {
        if (playerRef != null && playerRef.getUuid() != null) {
            pendingTriggers.put(playerRef.getUuid(), playerRef);
        }
        return false;
    }

    private boolean handleGameModeChangeEvent(PlayerRef playerRef) {
        if (playerRef == null || config == null || !config.bindOKey) {
            return false;
        }

        // Check if player has permission (only OPs or creative players)
        if (!hasOKeyPermission(playerRef)) {
            return false; // Let normal gamemode change happen
        }

        // Check if on cooldown
        if (isOnCooldown(playerRef)) {
            return true;
        }

        // Open JET browser - need to get Player and refs
        // For now, just return true to cancel gamemode change
        // TODO: Properly open GUI from PlayerRef
        return true;
    }

    private boolean hasOKeyPermission(PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }

        // If appliesOnOP is false, only allow for non-OPs
        // If appliesOnOP is true, allow for everyone with creative mode access
        try {
            // Check if player has creative permission/gamemode
            // For now, we assume if they pressed O key they have some level of access
            // The config.appliesOnOP setting controls if OPs get the binding
            return true; // Will be controlled by ChangeGameModeEvent access
        } catch (Exception e) {
            return false;
        }
    }

    private PlayerRef consumePendingTrigger() {
        // Just return the first pending trigger since we store PlayerRef now
        if (pendingTriggers.isEmpty()) {
            return null;
        }

        Map.Entry<UUID, PlayerRef> entry = pendingTriggers.entrySet().iterator().next();
        pendingTriggers.remove(entry.getKey());
        return entry.getValue();
    }

    private boolean isOnCooldown(PlayerRef playerRef) {
        if (config.cooldownMs <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastTriggerTime.get(playerRef.getUuid());

        if (lastTime != null && now - lastTime < config.cooldownMs) {
            return true;
        }

        lastTriggerTime.put(playerRef.getUuid(), now);
        return false;
    }
    public ComponentType<EntityStore, RecipeHudComponent> getRecipeHudComponentType() {
        return recipeHudComponentType;
    }
}