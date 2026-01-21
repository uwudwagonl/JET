package dev.hytalemod.jet;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemod.jet.command.JETCommand;
import dev.hytalemod.jet.command.JETDropsCommand;
import dev.hytalemod.jet.command.JETInfoCommand;
import dev.hytalemod.jet.command.JETListCommand;
import dev.hytalemod.jet.command.PinnedCommand;
import dev.hytalemod.jet.input.JETKeybindHandler;
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.PinManager;
import dev.hytalemod.jet.registry.RecipeRegistry;
import dev.hytalemod.jet.storage.PinnedItemsStorage;
import dev.hytalemod.jet.system.JETKeybindSystem;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * JET - Item Encyclopedia Plugin
 */
public class JETPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0-beta.10";

    private static JETPlugin instance;
    private ItemRegistry itemRegistry;
    private RecipeRegistry recipeRegistry;
    private PinManager pinManager;

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
        pinManager = new PinManager(getLogger(), getDataFolder());

        // Keybind system temporarily disabled due to issues
        // keybindSystem = new JETKeybindSystem();
        // getEntityStoreRegistry().registerSystem(keybindSystem);
        getCommandRegistry().registerCommand(new JETCommand());
        getCommandRegistry().registerCommand(new JETDropsCommand());
        getCommandRegistry().registerCommand(new JETInfoCommand());
        getCommandRegistry().registerCommand(new JETListCommand());
        getCommandRegistry().registerCommand(new PinnedCommand());

        // Register mouse button event for keybind toggle
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, event -> {
            JETKeybindHandler.getInstance().onMouseButton(event);
        });

        // Register disconnect event for cleanup
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            JETKeybindHandler.getInstance().onPlayerDisconnect(event.getPlayerRef().getUuid());
        });

        // Register asset loading events
        getEventRegistry().register(LoadedAssetsEvent.class, Item.class, JETPlugin::onItemsLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, JETPlugin::onRecipesLoaded);
        getEventRegistry().register(LoadedAssetsEvent.class, ItemDropList.class, JETPlugin::onDropListsLoaded);

        getLogger().at(Level.INFO).log("[JET] Plugin enabled - v" + VERSION);
        getLogger().at(Level.INFO).log("[JET] Use /jet or /j to open item browser, /pinned or /p for pinned items");
        // getLogger().at(Level.INFO).log("[JET] Keybind: Alt = Item Browser");
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

    public PinManager getPinManager() {
        return pinManager;
    }
}