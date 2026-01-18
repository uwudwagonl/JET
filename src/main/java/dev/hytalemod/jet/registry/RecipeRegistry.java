package dev.hytalemod.jet.registry;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.hytalemod.jet.JETPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Registry for crafting recipes with lookup by input/output items.
 */
public class RecipeRegistry {
    
    // All recipes by ID
    private final Map<String, CraftingRecipe> recipes = new LinkedHashMap<>();
    
    // Item ID -> Recipe IDs that produce this item (how to craft)
    private final Map<String, List<String>> craftingRecipes = new HashMap<>();
    
    // Item ID -> Recipe IDs that use this item as input (uses)
    private final Map<String, List<String>> usageRecipes = new HashMap<>();
    
    // Cached method for getInput (may not exist in all versions)
    private Method getInputMethod = null;
    
    public void reload(Map<String, CraftingRecipe> newRecipes) {
        recipes.clear();
        craftingRecipes.clear();
        usageRecipes.clear();

        // DEBUG: List all methods on CraftingRecipe
        try {
            java.nio.file.Path logsDir = java.nio.file.Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "Hytale", "UserData", "Logs");
            java.nio.file.Files.createDirectories(logsDir);
            java.io.FileWriter methodsFile = new java.io.FileWriter(logsDir.resolve("JET_methods.txt").toFile());
            methodsFile.write("=== All CraftingRecipe Methods ===\n");
            java.lang.reflect.Method[] methods = CraftingRecipe.class.getMethods();
            for (java.lang.reflect.Method m : methods) {
                methodsFile.write(m.getName() + "(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) methodsFile.write(", ");
                    methodsFile.write(params[i].getSimpleName());
                }
                methodsFile.write(") -> " + m.getReturnType().getSimpleName() + "\n");
            }
            methodsFile.close();
        } catch (Exception ex) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log("[JET] Failed to write methods file: " + ex.getMessage());
        }

        // Try to get getInput method via reflection
        try {
            getInputMethod = CraftingRecipe.class.getMethod("getInput");
            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Found getInput method");
        } catch (Exception e) {
            getInputMethod = null;
            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] No getInput method found, will try alternatives");
        }

        int outputCount = 0;
        int inputCount = 0;

        for (Map.Entry<String, CraftingRecipe> entry : newRecipes.entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            String recipeId = recipe.getId();

            recipes.put(recipeId, recipe);

            // Index by outputs (how to craft this item)
            MaterialQuantity[] outputs = recipe.getOutputs();
            if (outputs != null) {
                for (MaterialQuantity output : outputs) {
                    if (output != null && output.getItemId() != null) {
                        craftingRecipes.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(recipeId);
                        outputCount++;
                    }
                }
            }

            // Index by inputs (what uses this item)
            int inputsBefore = usageRecipes.size();
            indexInputs(recipe, recipeId);
            if (usageRecipes.size() > inputsBefore) {
                inputCount++;
            }
        }

        JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Indexed " + outputCount + " recipe outputs across " + craftingRecipes.size() + " items");
        JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Indexed " + inputCount + " recipes with inputs across " + usageRecipes.size() + " items");

        // Debug: Show first few items with usage recipes
        int debugCount = 0;
        for (Map.Entry<String, List<String>> entry : usageRecipes.entrySet()) {
            if (debugCount++ < 5) {
                JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET]   - Item " + entry.getKey() + " used in " + entry.getValue().size() + " recipes");
            }
        }

        // Write debug info to file
        try {
            java.nio.file.Path logsDir = java.nio.file.Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "Hytale", "UserData", "Logs");
            java.nio.file.Files.createDirectories(logsDir);
            java.io.FileWriter fw = new java.io.FileWriter(logsDir.resolve("JET_debug.txt").toFile());
            fw.write("=== JET Recipe Registry Debug ===\n");
            fw.write("Total recipes: " + recipes.size() + "\n");
            fw.write("Items with crafting recipes: " + craftingRecipes.size() + "\n");
            fw.write("Items with usage recipes: " + usageRecipes.size() + "\n\n");
            fw.write("First 10 items with usage recipes:\n");
            int count = 0;
            for (Map.Entry<String, List<String>> entry : usageRecipes.entrySet()) {
                if (count++ >= 10) break;
                fw.write("  " + entry.getKey() + ": " + entry.getValue().size() + " recipes\n");
            }
            fw.write("\nChecking gold/copper usage recipes:\n");
            for (String key : usageRecipes.keySet()) {
                if (key.contains("gold") || key.contains("copper") || key.contains("Gold") || key.contains("Copper") || key.contains("Ingot")) {
                    fw.write("  " + key + ": " + usageRecipes.get(key).size() + " uses\n");
                }
            }

            fw.write("\nChecking gold/copper/ingot crafting recipes:\n");
            for (String key : craftingRecipes.keySet()) {
                if (key.contains("gold") || key.contains("copper") || key.contains("Gold") || key.contains("Copper") || key.contains("Ingot") || key.contains("Ingredient")) {
                    fw.write("  " + key + ": " + craftingRecipes.get(key).size() + " craft recipes\n");
                }
            }

            fw.write("\nAll items with 'Ingredient' in name:\n");
            for (String key : usageRecipes.keySet()) {
                if (key.contains("Ingredient")) {
                    fw.write("  USAGE: " + key + ": " + usageRecipes.get(key).size() + " uses\n");
                }
            }
            for (String key : craftingRecipes.keySet()) {
                if (key.contains("Ingredient")) {
                    fw.write("  CRAFT: " + key + ": " + craftingRecipes.get(key).size() + " recipes\n");
                }
            }

            // Debug specific recipes
            fw.write("\nDEBUG: Checking specific recipe inputs:\n");
            CraftingRecipe arcanebench = recipes.get("hytale:Arcanebench");
            if (arcanebench != null) {
                fw.write("Found Arcanebench recipe\n");
                try {
                    Object inputs = getInputMethod.invoke(arcanebench);
                    if (inputs == null) {
                        fw.write("  getInput() returned null\n");
                    } else if (inputs instanceof MaterialQuantity[]) {
                        MaterialQuantity[] arr = (MaterialQuantity[]) inputs;
                        fw.write("  getInput() returned array of length: " + arr.length + "\n");
                        for (int i = 0; i < arr.length; i++) {
                            MaterialQuantity mq = arr[i];
                            if (mq != null) {
                                fw.write("    [" + i + "] " + mq.getItemId() + " x" + mq.getQuantity() + "\n");
                            }
                        }
                    } else {
                        fw.write("  getInput() returned unexpected type: " + inputs.getClass().getName() + "\n");
                    }
                } catch (Exception ex) {
                    fw.write("  Error calling getInput(): " + ex.getMessage() + "\n");
                }
            } else {
                fw.write("Arcanebench recipe not found\n");
            }

            fw.close();
        } catch (Exception e) {
            JETPlugin.getInstance().getLogger().at(Level.WARNING).log("[JET] Failed to write debug file: " + e.getMessage());
        }
    }
    
    private void indexInputs(CraftingRecipe recipe, String recipeId) {
        Object inputsObj = null;
        String successMethod = null;

        // Try getInput method first
        if (getInputMethod != null) {
            try {
                inputsObj = getInputMethod.invoke(recipe);
                // Check if it's a non-empty array (getInput() returns MaterialQuantity[])
                if (inputsObj != null) {
                    if (inputsObj instanceof MaterialQuantity[] && ((MaterialQuantity[]) inputsObj).length > 0) {
                        successMethod = "getInput";
                    } else if (!(inputsObj instanceof MaterialQuantity[])) {
                        // It's not an array but something else, keep it
                        successMethod = "getInput";
                    } else {
                        // It's an empty array, treat as null
                        inputsObj = null;
                    }
                }
            } catch (Exception e) {
                // Method exists but failed, try alternatives
            }
        }

        // If getInput failed or doesn't exist, try alternative methods
        if (inputsObj == null) {
            String[] methodNames = {
                "getInputs",
                "getIngredients",
                "getMaterials",
                "getRecipeInputs",
                "getRequiredMaterials"
            };

            for (String methodName : methodNames) {
                try {
                    Method method = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = method.invoke(recipe);
                    if (inputsObj != null) {
                        successMethod = methodName;
                        break;
                    }
                } catch (Exception ignored) {
                    // Try next method
                }
            }
        }

        if (inputsObj == null) {
            return;
        }

        // Debug successful input detection
        if (successMethod != null && recipes.size() < 10) {
            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Recipe " + recipeId + " inputs detected via " + successMethod);
        }

        // Process the inputs object (supports multiple types)
        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                // Single input
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null && input.getItemId() != null) {
                    String itemId = input.getItemId();
                    usageRecipes.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipeId);
                    if (itemId.contains("gold") || itemId.contains("copper")) {
                        JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Found usage (single): " + itemId + " in " + recipeId);
                    }
                }
            } else if (inputsObj instanceof List) {
                // List of inputs
                List<?> inputs = (List<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && input.getItemId() != null) {
                            String itemId = input.getItemId();
                            usageRecipes.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipeId);
                            if (itemId.contains("gold") || itemId.contains("copper")) {
                                JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Found usage (list): " + itemId + " in " + recipeId);
                            }
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                // Array of inputs
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                for (MaterialQuantity input : inputs) {
                    if (input != null && input.getItemId() != null) {
                        String itemId = input.getItemId();
                        usageRecipes.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipeId);
                        if (itemId.contains("gold") || itemId.contains("copper")) {
                            JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Found usage (array): " + itemId + " in " + recipeId);
                        }
                    }
                }
            } else if (inputsObj instanceof Collection) {
                // Collection of inputs
                Collection<?> inputs = (Collection<?>) inputsObj;
                for (Object obj : inputs) {
                    if (obj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) obj;
                        if (input != null && input.getItemId() != null) {
                            String itemId = input.getItemId();
                            usageRecipes.computeIfAbsent(itemId, k -> new ArrayList<>()).add(recipeId);
                            if (itemId.contains("gold") || itemId.contains("copper")) {
                                JETPlugin.getInstance().getLogger().at(Level.INFO).log("[JET] Found usage (collection): " + itemId + " in " + recipeId);
                            }
                        }
                    }
                }
            }
        }
    }
    
    public int size() {
        return recipes.size();
    }
    
    public CraftingRecipe get(String recipeId) {
        return recipes.get(recipeId);
    }
    
    /**
     * Get recipes that produce the given item (how to craft)
     */
    public List<CraftingRecipe> getCraftingRecipes(String itemId) {
        List<String> recipeIds = craftingRecipes.get(itemId);
        if (recipeIds == null) return Collections.emptyList();
        
        List<CraftingRecipe> result = new ArrayList<>();
        for (String id : recipeIds) {
            CraftingRecipe r = recipes.get(id);
            if (r != null) result.add(r);
        }
        return result;
    }
    
    /**
     * Get recipes that use the given item as input (uses)
     */
    public List<CraftingRecipe> getUsageRecipes(String itemId) {
        List<String> recipeIds = usageRecipes.get(itemId);
        if (recipeIds == null) return Collections.emptyList();
        
        List<CraftingRecipe> result = new ArrayList<>();
        for (String id : recipeIds) {
            CraftingRecipe r = recipes.get(id);
            if (r != null) result.add(r);
        }
        return result;
    }
    
    public boolean hasCraftingRecipes(String itemId) {
        return craftingRecipes.containsKey(itemId) && !craftingRecipes.get(itemId).isEmpty();
    }
    
    public boolean hasUsageRecipes(String itemId) {
        return usageRecipes.containsKey(itemId) && !usageRecipes.get(itemId).isEmpty();
    }
}
