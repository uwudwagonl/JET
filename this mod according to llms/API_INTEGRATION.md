# JET API Integration Guide

## Overview

JET (Just Enough Tales) provides a public API that allows other mods to open the JET item browser programmatically. This is useful for mods that want to provide "view in JET" functionality from their own GUIs.

## Adding JET as a Dependency

### 1. Add JET to your mod's dependencies

In your `manifest.json`, add JET as an optional dependency:

```json
{
  "OptionalDependencies": {
    "JET": "*"
  }
}
```

### 2. Include JET classes in your mod

You have two options:

#### Option A: Compile-time dependency (Recommended)
Add JET JAR to your project's classpath during development. You don't need to bundle it - JET will be loaded separately on the server.

#### Option B: Reflection (No compile dependency needed)
Use Java reflection to call JET methods without adding a compile-time dependency.

## Using the API

### Method 1: Direct API Call (Preferred)

```java
import dev.hytalemod.jet.api.JETApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// In your GUI event handler:
public void handleItemClick(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String itemId) {
    // Check if JET is available
    if (!JETApi.isAvailable()) {
        playerRef.sendMessage(Message.raw("JET is not installed!").color("#FF5555"));
        return;
    }

    // Open JET browser with the item
    boolean success = JETApi.openBrowser(player, ref, store, itemId);

    if (!success) {
        playerRef.sendMessage(Message.raw("Failed to open JET browser").color("#FF5555"));
    }
}
```

### Method 2: Using Reflection (No compile dependency)

```java
public void handleItemClick(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String itemId) {
    try {
        // Check if JET is loaded
        Class<?> jetApiClass = Class.forName("dev.hytalemod.jet.api.JETApi");

        // Check availability
        Method isAvailable = jetApiClass.getMethod("isAvailable");
        boolean available = (Boolean) isAvailable.invoke(null);

        if (!available) {
            playerRef.sendMessage(Message.raw("JET is not installed!").color("#FF5555"));
            return;
        }

        // Open browser
        Method openBrowser = jetApiClass.getMethod("openBrowser",
            Player.class, Ref.class, Store.class, String.class);
        boolean success = (Boolean) openBrowser.invoke(null, player, ref, store, itemId);

        if (!success) {
            playerRef.sendMessage(Message.raw("Failed to open JET browser").color("#FF5555"));
        }
    } catch (ClassNotFoundException e) {
        // JET not installed
        playerRef.sendMessage(Message.raw("JET is not installed!").color("#FF5555"));
    } catch (Exception e) {
        playerRef.sendMessage(Message.raw("Error opening JET: " + e.getMessage()).color("#FF5555"));
    }
}
```

## Example: PrefabBuilder Integration

Here's a complete example showing how PrefabBuilder could integrate with JET:

```java
// In PrefabConstructionPage.java

public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                 @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
    // ... existing code ...

    // Add "View in JET" button for each resource
    for (Map.Entry<String, Integer> entry : required.entrySet()) {
        String itemId = entry.getKey();
        String effectiveItemId = site.getEffectiveItemId(itemId);

        // ... existing button code ...

        // Add JET button
        commands.appendInline("#ResourcesContainer",
            "TextButton #JETBtn" + resourceIndex + " {\n" +
            "    Background: (Color: #2255AA);\n" +
            "    Anchor: (Width: 40, Height: 32, Right: 4);\n" +
            "    Text: \"JET\";\n" +
            "    TooltipText: \"View recipes in JET\";\n" +
            "}\n"
        );

        events.addEventBinding(CustomUIEventBindingType.Activating,
            "#JETBtn" + resourceIndex,
            EventData.of("Action", "view_in_jet").append("ItemId", effectiveItemId)
        );

        resourceIndex++;
    }
}

public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull PrefabEventData data) {
    String action = data.action;
    Player player = (Player) store.getComponent(ref, Player.getComponentType());

    switch (action) {
        case "view_in_jet":
            if (data.itemId != null && player != null) {
                openJETBrowser(player, ref, store, data.itemId);
            }
            break;
        // ... other cases ...
    }
}

private void openJETBrowser(Player player, Ref<EntityStore> ref,
                           Store<EntityStore> store, String itemId) {
    try {
        Class<?> jetApiClass = Class.forName("dev.hytalemod.jet.api.JETApi");
        Method openBrowser = jetApiClass.getMethod("openBrowser",
            Player.class, Ref.class, Store.class, String.class);
        boolean success = (Boolean) openBrowser.invoke(null, player, ref, store, itemId);

        if (!success) {
            playerRef.sendMessage(Message.raw("Failed to open JET").color("#FF5555"));
        }
    } catch (ClassNotFoundException e) {
        // JET not installed - fail silently or show message
        playerRef.sendMessage(Message.raw("Install JET mod to view recipes!").color("#FFAA00"));
    } catch (Exception e) {
        playerRef.sendMessage(Message.raw("Error: " + e.getMessage()).color("#FF5555"));
    }
}
```

## API Reference

### `JETApi.openBrowser(Player, Ref<EntityStore>, Store<EntityStore>, String)`

Opens the JET browser with a specific item pre-searched.

**Parameters:**
- `player` - The player to open the browser for
- `ref` - The entity store reference
- `store` - The entity store
- `itemId` - The item ID to search for (e.g., "Block_Stone", "Item_Wood_Plank")

**Returns:** `boolean` - true if successful, false otherwise

### `JETApi.openBrowserWithSearch(PlayerRef, String)`

Opens the JET browser with a search query pre-filled. This is a simpler API that only requires a PlayerRef.

**Parameters:**
- `playerRef` - The player reference
- `searchQuery` - The search query to pre-fill

**Returns:** `boolean` - true if successful, false otherwise

**Note:** This method is currently limited and will be improved in future versions.

### `JETApi.isAvailable()`

Checks if JET is loaded and available.

**Returns:** `boolean` - true if JET is loaded

### `JETApi.getVersion()`

Gets the JET version string.

**Returns:** `String` - Version string, or null if JET is not loaded

## Item ID Format

JET works with standard Hytale item IDs:

- **Blocks:** `Block_Stone`, `Block_Wood_Plank`, `Block_Dirt`
- **Items:** `Item_Wood_Sword`, `Item_Iron_Pickaxe`
- **Block States:** `Block_Stone[state=polished]` (JET will strip the state automatically)

When passing item IDs to JET, you can include or exclude the state definitions - JET will handle both formats.

## Best Practices

1. **Always check availability:** Use `JETApi.isAvailable()` before calling API methods
2. **Handle failures gracefully:** Check return values and provide user feedback
3. **Use reflection for optional integration:** If JET is an optional dependency, use reflection to avoid hard dependencies
4. **Strip prefixes if needed:** JET handles both `Block_Stone` and `Stone` formats
5. **Provide fallback UI:** If JET is not installed, provide alternative functionality in your mod

## Support

For issues or feature requests related to JET API integration, please contact:
- Discord: uwudwagon
- GitHub: [JET Issues](https://github.com/uwudwagon/jet/issues)

## Version Compatibility

- JET API introduced in: v1.3.0
- Minimum JET version required: 1.3.0
- Hytale Server API: Compatible with current release

## Changelog

### v1.3.0
- Initial API release
- Added `openBrowser()` method
- Added `openBrowserWithSearch()` method
- Added `isAvailable()` and `getVersion()` helper methods
