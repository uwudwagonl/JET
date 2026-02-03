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

        // Try to get getInput method via reflection
        try {
            getInputMethod = CraftingRecipe.class.getMethod("getInput");
        } catch (Exception e) {
            getInputMethod = null;
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

        JETPlugin.getInstance().log(Level.INFO, "[JET] Indexed " + outputCount + " recipe outputs across " + craftingRecipes.size() + " items");
        JETPlugin.getInstance().log(Level.INFO, "[JET] Indexed " + inputCount + " recipes with inputs across " + usageRecipes.size() + " items");
    }

    private void indexInputs(CraftingRecipe recipe, String recipeId) {
        Object inputsObj = null;

        // Try getInput method first
        if (getInputMethod != null) {
            try {
                inputsObj = getInputMethod.invoke(recipe);
                if (inputsObj != null) {
                    if (inputsObj instanceof MaterialQuantity[] && ((MaterialQuantity[]) inputsObj).length == 0) {
                        inputsObj = null;
                    }
                }
            } catch (Exception e) {
                // Method exists but failed, try alternatives
            }
        }

        // If getInput failed or doesn't exist, try alternative methods
        if (inputsObj == null) {
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};

            for (String methodName : methodNames) {
                try {
                    Method method = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = method.invoke(recipe);
                    if (inputsObj != null) {
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

        // Process the inputs object (supports multiple types)
        if (inputsObj instanceof MaterialQuantity) {
            MaterialQuantity input = (MaterialQuantity) inputsObj;
            if (input != null && input.getItemId() != null) {
                usageRecipes.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
            }
        } else if (inputsObj instanceof List) {
            List<?> inputs = (List<?>) inputsObj;
            for (Object obj : inputs) {
                if (obj instanceof MaterialQuantity) {
                    MaterialQuantity input = (MaterialQuantity) obj;
                    if (input != null && input.getItemId() != null) {
                        usageRecipes.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                    }
                }
            }
        } else if (inputsObj instanceof MaterialQuantity[]) {
            MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
            for (MaterialQuantity input : inputs) {
                if (input != null && input.getItemId() != null) {
                    usageRecipes.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                }
            }
        } else if (inputsObj instanceof Collection) {
            Collection<?> inputs = (Collection<?>) inputsObj;
            for (Object obj : inputs) {
                if (obj instanceof MaterialQuantity) {
                    MaterialQuantity input = (MaterialQuantity) obj;
                    if (input != null && input.getItemId() != null) {
                        usageRecipes.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
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
