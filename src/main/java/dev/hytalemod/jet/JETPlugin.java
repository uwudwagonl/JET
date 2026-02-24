package dev.hytalemod.jet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.command.*;
import dev.hytalemod.jet.component.JETKeybindComponent;
import dev.hytalemod.jet.component.RecipeHudComponent;
import dev.hytalemod.jet.config.JETConfig;
import dev.hytalemod.jet.interaction.OpenJETInteraction;
import dev.hytalemod.jet.registry.DropListRegistry;
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.RecipeRegistry;
import dev.hytalemod.jet.registry.SetRegistry;
import dev.hytalemod.jet.storage.BrowserStateStorage;
import dev.hytalemod.jet.storage.PinnedItemsStorage;
import dev.hytalemod.jet.system.AltKeyBind;
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
import java.util.logging.Level;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JET - Item Encyclopedia Plugin
 */
public class JETPlugin extends JavaPlugin {

    public static final String VERSION = "1.10.2";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "JET_config.json";

    private static JETPlugin instance;
    private ItemRegistry itemRegistry;
    private RecipeRegistry recipeRegistry;
    private DropListRegistry dropListRegistry;
    private SetRegistry setRegistry;
    private PinnedItemsStorage pinnedItemsStorage;
    private BrowserStateStorage browserStateStorage;

    private JETConfig config;

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, CraftingRecipe> RECIPES = new HashMap<>();
    public static Map<String, ItemDropList> DROP_LISTS = new HashMap<>();
    public static Map<String, List<String>> ITEM_TO_RECIPES = new HashMap<>();
    public static Map<String, List<String>> ITEM_FROM_RECIPES = new HashMap<>();

    // Custom JET log file writer
    private PrintWriter jetLogWriter;
    private final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private ComponentType<EntityStore, RecipeHudComponent> recipeHudComponentType;
    private ComponentType<EntityStore, JETKeybindComponent> keybindComponentType;


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
        new HStats("1db7d557-ac47-4379-af75-332245251314", "1.10.2");

        instance = this;
        recipeHudComponentType = getEntityStoreRegistry().registerComponent(
                RecipeHudComponent.class,
                "jet:recipe_hud",
                RecipeHudComponent.CODEC
        );
        RecipeHudComponent.init(recipeHudComponentType);

        keybindComponentType = getEntityStoreRegistry().registerComponent(
                JETKeybindComponent.class,
                "jet:keybind",
                JETKeybindComponent.CODEC
        );
        JETKeybindComponent.init(keybindComponentType);

        // Setup custom JET logger that writes to UserData/Logs/jet_logs
        setupJetLogger();
        itemRegistry = new ItemRegistry();
        recipeRegistry = new RecipeRegistry();
        dropListRegistry = new DropListRegistry();
        setRegistry = new SetRegistry();
        pinnedItemsStorage = new PinnedItemsStorage();
        pinnedItemsStorage.load();
        browserStateStorage = new BrowserStateStorage();
        browserStateStorage.load();

        // Load config
        loadConfig();

        // Register commands
        getCommandRegistry().registerCommand(new JETCommand());
        getCommandRegistry().registerCommand(new JETPinnedCommand());
        getCommandRegistry().registerCommand(new JETConfigCommand());

        // Register asset load events
        getEventRegistry().register(LoadedAssetsEvent.class, Item.class, JETPlugin::onItemsLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, JETPlugin::onRecipesLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, ItemDropList.class, JETPlugin::onDropListsLoaded);
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, RecipeHudUpdateSystem::onInventoryChange);

        // Register custom interaction for Pex Glyph item
        Interaction.CODEC.register("OpenJET", OpenJETInteraction.class, OpenJETInteraction.CODEC);

        // Register Alt key bind system
        getEntityStoreRegistry().registerSystem(new AltKeyBind());

        log(Level.INFO, "[JET] Plugin enabled - v" + VERSION);
        String keyBindInfo = (config != null && config.bindAltKey) ? ", Alt key bound to /jet" : "";
        log(Level.INFO, "[JET] Use /jet or /j to open item browser" + keyBindInfo);
        log(Level.INFO, "[JET] Tip: Use '/jet <itemId>' to search directly (e.g. /jet Block_Stone)");
    }


    @Override
    protected void shutdown() {
        super.shutdown();
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
        DefaultAssetMap<String, Item> assetMap = (DefaultAssetMap<String, Item>) event.getAssetMap();
        ITEMS = assetMap.getAssetMap();
        instance.itemRegistry.reload(ITEMS);
        instance.setRegistry.reload(ITEMS);
        instance.log(Level.INFO, "[JET] Loaded " + instance.itemRegistry.size() + " items, " + instance.setRegistry.size() + " sets");

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

    public SetRegistry getSetRegistry() {
        return setRegistry;
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

    public ComponentType<EntityStore, RecipeHudComponent> getRecipeHudComponentType() {
        return recipeHudComponentType;
    }

    public ComponentType<EntityStore, JETKeybindComponent> getKeybindComponentType() {
        return keybindComponentType;
    }
}
