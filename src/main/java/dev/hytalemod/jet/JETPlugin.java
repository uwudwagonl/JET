package dev.hytalemod.jet;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemod.jet.command.JETCommand;
import dev.hytalemod.jet.command.JETInfoCommand;
import dev.hytalemod.jet.command.JETListCommand;
import dev.hytalemod.jet.input.JETKeybindHandler;
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.RecipeRegistry;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * JET (Just Enough Things) - Item Encyclopedia Plugin
 *
 * Features:
 * - Browse all items with search functionality
 * - View recipes and item info
 * - Toggle with Mouse Side Button (X1) or /jet command
 */
public class JETPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0-beta.10";

    private static JETPlugin instance;
    private ItemRegistry itemRegistry;
    private RecipeRegistry recipeRegistry;

    // Global recipe maps like Lumenia
    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, CraftingRecipe> RECIPES = new HashMap<>();
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

        // Register commands
        getCommandRegistry().registerCommand(new JETCommand());
        getCommandRegistry().registerCommand(new JETInfoCommand());
        getCommandRegistry().registerCommand(new JETListCommand());

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

        getLogger().at(Level.INFO).log("[JET] Plugin enabled - v" + VERSION);
        getLogger().at(Level.INFO).log("[JET] Press Mouse Side Button (X1) to toggle item browser");
        getLogger().at(Level.INFO).log("[JET] Or use /jet command");
    }

    @SuppressWarnings("unchecked")
    private static void onItemsLoaded(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        // Store in global ITEMS map like Lumenia
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

        // Build recipe maps exactly like Lumenia does
        Method getInputMethod = null;
        try {
            getInputMethod = CraftingRecipe.class.getMethod("getInput");
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, will try alternatives later
        }

        for (CraftingRecipe recipe : recipes.values()) {
            RECIPES.put(recipe.getId(), recipe);

            // Build ITEM_TO_RECIPES (output items -> recipe IDs)
            for (MaterialQuantity output : recipe.getOutputs()) {
                ITEM_TO_RECIPES.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
            }

            // Build ITEM_FROM_RECIPES (input items -> recipe IDs)
            if (getInputMethod != null) {
                try {
                    Object inputsObj = getInputMethod.invoke(recipe);
                    if (inputsObj != null) {
                        processRecipeInputs(inputsObj, recipe.getId());
                    }
                } catch (Exception e) {
                    // Ignore
                }
            } else {
                // Try alternative methods
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
                        // Continue trying
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
}