package dev.hytalemod.jet.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemod.jet.JETPlugin;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Component for storing pinned recipes shown in HUD
 */
public class RecipeHudComponent implements Component<EntityStore> {

    public static final BuilderCodec<RecipeHudComponent> CODEC = BuilderCodec
            .builder(RecipeHudComponent.class, RecipeHudComponent::new)
            .append(new KeyedCodec<>("PinnedRecipes", new SetCodec<>(Codec.STRING, LinkedHashSet::new, false)),
                    (c, v) -> c.pinnedRecipes = v,
                    c -> c.pinnedRecipes)
            .add()
            .build();

    private static ComponentType<EntityStore, RecipeHudComponent> COMPONENT_TYPE;

    public Set<String> pinnedRecipes = new LinkedHashSet<>();

    public RecipeHudComponent() {
    }

    private RecipeHudComponent(Set<String> pinnedRecipes) {
        this.pinnedRecipes = new LinkedHashSet<>(pinnedRecipes);
    }

    public static void init(ComponentType<EntityStore, RecipeHudComponent> type) {
        COMPONENT_TYPE = type;
    }

    public static ComponentType<EntityStore, RecipeHudComponent> getComponentType() {
        return COMPONENT_TYPE;
    }

    public void addRecipe(String recipeId) {
        pinnedRecipes.add(recipeId);
    }

    public void removeRecipe(String recipeId) {
        pinnedRecipes.remove(recipeId);
    }

    public boolean hasRecipe(String recipeId) {
        return pinnedRecipes.contains(recipeId);
    }

    public void toggleRecipe(String recipeId) {
        if (hasRecipe(recipeId)) {
            removeRecipe(recipeId);
        } else {
            addRecipe(recipeId);
        }
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new RecipeHudComponent(pinnedRecipes);
    }
}