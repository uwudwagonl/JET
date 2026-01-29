package dev.hytalemod.jet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.GameMode;
import dev.hytalemod.jet.command.JETBindCommand;
import dev.hytalemod.jet.command.JETCommand;
import dev.hytalemod.jet.command.JETDropsCommand;
import dev.hytalemod.jet.command.JETInfoCommand;
import dev.hytalemod.jet.command.JETListCommand;
import dev.hytalemod.jet.command.JETPinnedCommand;
import dev.hytalemod.jet.command.JETSettingsCommand;
import dev.hytalemod.jet.config.JETConfig;
import dev.hytalemod.jet.filter.OKeyPacketFilter;
import dev.hytalemod.jet.gui.JETGui;
import dev.hytalemod.jet.registry.DropListRegistry;
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.RecipeRegistry;
import dev.hytalemod.jet.storage.BrowserStateStorage;
import dev.hytalemod.jet.storage.PinnedItemsStorage;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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

    public JETPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        instance = this;
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
        getCommandRegistry().registerCommand(new JETDropsCommand());
        getCommandRegistry().registerCommand(new JETInfoCommand());
        getCommandRegistry().registerCommand(new JETListCommand());
        getCommandRegistry().registerCommand(new JETPinnedCommand());
        getCommandRegistry().registerCommand(new JETSettingsCommand());
        getCommandRegistry().registerCommand(new JETBindCommand());

        // Register asset load events
        getEventRegistry().register(LoadedAssetsEvent.class, Item.class, JETPlugin::onItemsLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, JETPlugin::onRecipesLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, ItemDropList.class, JETPlugin::onDropListsLoaded);

        // Setup O key binding if enabled
        if (config != null && config.bindOKey) {
            setupOKeyBinding();
        }

        getLogger().at(Level.INFO).log("[JET] Plugin enabled - v" + VERSION);
        String keyBindInfo = (config != null && config.bindOKey) ? ", O key bound to /jet" : "";
        getLogger().at(Level.INFO).log("[JET] Use /jet or /j to open item browser" + keyBindInfo);
        getLogger().at(Level.INFO).log("[JET] Tip: Use '/jet <itemId>' to search directly (e.g. /jet Block_Stone)");
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        if (oKeyFilter != null) {
            PacketAdapters.deregisterInbound(oKeyFilter);
        }
    }

    @SuppressWarnings("unchecked")
    private static void onItemsLoaded(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = ((DefaultAssetMap<String, Item>) event.getAssetMap()).getAssetMap();
        instance.itemRegistry.reload(ITEMS);
        instance.getLogger().at(Level.INFO).log("[JET] Loaded " + instance.itemRegistry.size() + " items");
    }

    @SuppressWarnings("unchecked")
    private static void onRecipesLoaded(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        Map<String, CraftingRecipe> recipes = event.getLoadedAssets();

        if (recipes == null || recipes.isEmpty()) {
            instance.getLogger().at(Level.WARNING).log("[JET] No recipes in LoadedAssetsEvent");
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
        instance.getLogger().at(Level.INFO).log("[JET] Loaded " + instance.recipeRegistry.size() + " recipes");
        instance.getLogger().at(Level.INFO).log("[JET] Built recipe maps - ITEM_TO_RECIPES: " + ITEM_TO_RECIPES.size() + " items, ITEM_FROM_RECIPES: " + ITEM_FROM_RECIPES.size() + " items");
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
            instance.getLogger().at(Level.WARNING).log("[JET] No drop lists in LoadedAssetsEvent");
            return;
        }

        DROP_LISTS = new HashMap<>(dropLists);
        instance.dropListRegistry.reload(dropLists);
        instance.getLogger().at(Level.INFO).log("[JET] Loaded " + instance.dropListRegistry.size() + " drop lists");
    }

    // ==================== Config Management ====================

    private void loadConfig() {
        try {
            Path configDir = getFile().getParent().resolve("JET");
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve(CONFIG_FILE);

            if (!Files.exists(configPath)) {
                config = new JETConfig();
                saveConfig(configPath);
                getLogger().at(Level.INFO).log("[JET] Created default config at " + configPath);
            } else {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                config = GSON.fromJson(json, JETConfig.class);
                getLogger().at(Level.INFO).log("[JET] Loaded config from " + configPath);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).log("[JET] Failed to load config, using defaults: " + e.getMessage());
            config = new JETConfig();
        }
    }

    private void saveConfig(Path configPath) {
        try {
            String json = GSON.toJson(config);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().at(Level.WARNING).log("[JET] Failed to save config: " + e.getMessage());
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

        getLogger().at(Level.INFO).log("[JET] O key binding enabled");
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
}