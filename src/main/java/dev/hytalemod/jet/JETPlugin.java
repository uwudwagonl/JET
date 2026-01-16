package dev.hytalemod.jet;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemod.jet.command.JETCommand;
import dev.hytalemod.jet.command.JETInfoCommand;
import dev.hytalemod.jet.command.JETListCommand;
import dev.hytalemod.jet.input.JETKeybindHandler;
import dev.hytalemod.jet.registry.ItemRegistry;
import dev.hytalemod.jet.registry.RecipeRegistry;

import java.util.Map;
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
        Map<String, Item> items = event.getAssetMap().getAssetMap();
        instance.itemRegistry.reload(items);
        instance.getLogger().at(Level.INFO).log("[JET] Loaded " + instance.itemRegistry.size() + " items");
    }
    
    @SuppressWarnings("unchecked")
    private static void onRecipesLoaded(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        // Use getLoadedAssets() like Lumenia does
        Map<String, CraftingRecipe> recipes = event.getLoadedAssets();

        if (recipes != null && !recipes.isEmpty()) {
            instance.recipeRegistry.reload(recipes);
            instance.getLogger().at(Level.INFO).log("[JET] Loaded " + instance.recipeRegistry.size() + " recipes");
        } else {
            instance.getLogger().at(Level.WARNING).log("[JET] No recipes in LoadedAssetsEvent");
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
