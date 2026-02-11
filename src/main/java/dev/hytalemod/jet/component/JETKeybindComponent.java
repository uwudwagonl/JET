package dev.hytalemod.jet.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public class JETKeybindComponent implements Component<EntityStore> {

    public static final BuilderCodec<JETKeybindComponent> CODEC = BuilderCodec
            .builder(JETKeybindComponent.class, JETKeybindComponent::new)
            .append(new KeyedCodec<>("PreviousWalkState", Codec.BOOLEAN),
                    (c, v) -> c.previousWalkState = v,
                    c -> c.previousWalkState)
            .add()
            .build();

    private static ComponentType<EntityStore, JETKeybindComponent> COMPONENT_TYPE;

    public boolean previousWalkState = false;

    public JETKeybindComponent() {
    }

    public static void init(ComponentType<EntityStore, JETKeybindComponent> type) {
        COMPONENT_TYPE = type;
    }

    public static ComponentType<EntityStore, JETKeybindComponent> getComponentType() {
        return COMPONENT_TYPE;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        JETKeybindComponent copy = new JETKeybindComponent();
        copy.previousWalkState = this.previousWalkState;
        return copy;
    }
}
