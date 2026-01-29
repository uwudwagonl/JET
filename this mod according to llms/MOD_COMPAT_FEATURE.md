# JET Mod Compatibility API

## Overview

JET v1.3.0 now includes a public API that allows other mods to integrate with JET by opening the item browser programmatically. This was specifically designed for compatibility with mods like PrefabBuilder, where users can click on items in resource lists to view recipes in JET.

## Feature Summary

### What it does
- Other mods can open JET browser from their own GUIs
- Item can be pre-selected/searched when opening
- No hard dependency required (works with reflection)
- Graceful fallback if JET is not installed

### Use Case
User is in PrefabBuilder viewing required resources for a prefab. They see they need "Block_Stone" but don't know how to craft it. They click a "JET" button next to the item, and JET browser opens showing the recipe for Block_Stone.

## Implementation Details

### Files Added

1. **`src/main/java/dev/hytalemod/jet/api/JETApi.java`**
   - Public API class with static methods
   - `openBrowser(Player, Ref, Store, itemId)` - Opens JET with item
   - `openBrowserWithSearch(PlayerRef, searchQuery)` - Simpler API (WIP)
   - `isAvailable()` - Check if JET is loaded
   - `getVersion()` - Get JET version string

2. **`API_INTEGRATION.md`**
   - Complete documentation for mod developers
   - Example code for both direct API and reflection approaches
   - Best practices and troubleshooting

3. **`EXAMPLE_INTEGRATION.java`**
   - Real-world example showing PrefabBuilder integration
   - Shows exact code to add to PrefabConstructionPage
   - Includes both direct and reflection approaches

4. **`MOD_COMPAT_FEATURE.md`** (this file)
   - Feature summary and implementation notes

### How It Works

```
PrefabBuilder GUI              JET API                    JET Browser
    |                            |                            |
    | User clicks item           |                            |
    |--------------------------->|                            |
    | JETApi.openBrowser()       |                            |
    |                            | Creates JETGui             |
    |                            |--------------------------->|
    |                            |  with search=itemId        |
    |                            |                            |
    |                            |<---------------------------|
    |                            |    Browser opens           |
    |                            |                            |
    | Browser displays           |                            |
    |<----------------------------------------------------------|
```

## Integration Methods

### Method 1: Direct API (Compile Dependency)

**Pros:**
- Type-safe, compile-time checking
- Better IDE support
- Cleaner code

**Cons:**
- Requires JET JAR in classpath during compilation
- Creates compile-time dependency

**When to use:**
- JET is a required dependency for your mod
- You want clean, maintainable code
- Your mod heavily integrates with JET

### Method 2: Reflection (Runtime Discovery)

**Pros:**
- No compile-time dependency
- Mod works without JET installed
- Truly optional integration

**Cons:**
- No compile-time type checking
- More verbose code
- Potential runtime errors if API changes

**When to use:**
- JET is an optional feature
- You don't want to add build dependencies
- Users may or may not have JET installed

## API Design Decisions

### Why Static Methods?
- Easy to call from any context
- No need to find JET plugin instance
- Works well with reflection
- Common pattern for mod APIs

### Why Two openBrowser Methods?
- `openBrowser(Player, Ref, Store, itemId)` - Full-featured, requires all components
- `openBrowserWithSearch(PlayerRef, searchQuery)` - Simpler, but limited (WIP)

The full method is preferred because it provides complete context to open the GUI properly. The simpler method is included for future expansion.

### Item ID Handling
- API accepts full item IDs: `Block_Stone`, `Item_Wood_Plank`
- Also accepts with state: `Block_Stone[state=polished]`
- JET strips state definitions internally
- API is flexible about format

## Testing

### Manual Test Plan

1. **Test without JET installed:**
   - PrefabBuilder button should be hidden
   - No errors in console

2. **Test with JET installed:**
   - Button appears next to resources
   - Clicking button opens JET
   - Item is pre-searched in JET
   - Can return to PrefabBuilder

3. **Test with various items:**
   - Blocks: `Block_Stone`, `Block_Dirt`
   - Items: `Item_Wood_Sword`
   - With states: `Block_Stone[state=polished]`

4. **Test error cases:**
   - Invalid item ID
   - Player disconnects mid-operation
   - JET unloaded while GUI open

## Future Improvements

### v1.4.0 Ideas

1. **Direct Selection API**
   ```java
   JETApi.openBrowserWithSelection(player, ref, store, itemId);
   ```
   Opens browser with item already selected (not just searched)

2. **Tab Pre-selection**
   ```java
   JETApi.openBrowserOnTab(player, ref, store, itemId, "recipes");
   ```
   Opens browser directly on recipes tab for the item

3. **Callback Support**
   ```java
   JETApi.openBrowser(player, ref, store, itemId, (selectedItem) -> {
       // User selected a different item in JET
   });
   ```

4. **Batch Operations**
   ```java
   JETApi.openBrowserWithMultiple(player, ref, store, itemIds);
   ```
   Opens browser showing multiple items (e.g., all missing resources)

## Known Limitations

1. **openBrowserWithSearch() is incomplete**
   - Currently just sends a message
   - Needs proper GUI opening from PlayerRef only
   - Will be improved in future version

2. **No state handling**
   - Item states are stripped
   - Can't specifically search for "polished stone" vs "normal stone"
   - May add in future if needed

3. **Single item only**
   - Can only open browser for one item at a time
   - No bulk/batch operations yet

## Migration Guide

### For Existing Integrations

If you previously integrated with JET through other means:

**Before (hypothetical):**
```java
// Had to manually construct command
playerRef.executeCommand("/jet " + itemId);
```

**After (v1.3.0):**
```java
// Clean API call
JETApi.openBrowser(player, ref, store, itemId);
```

### Version Compatibility

- JET v1.3.0+ required for API
- Older JET versions: API will return `isAvailable() == false`
- Check version: `JETApi.getVersion()`

## FAQ

**Q: Do I need to add JET as a dependency in manifest.json?**
A: Only if you want it to be required. For optional integration, add it to `OptionalDependencies`.

**Q: What happens if user doesn't have JET installed?**
A: `JETApi.isAvailable()` returns false. Show an appropriate message to the user.

**Q: Can I use this from commands?**
A: Yes! API works from commands, GUIs, events, anywhere you have Player/PlayerRef.

**Q: Does this work with modded items?**
A: Yes, JET indexes all items including from other mods.

**Q: How do I report bugs?**
A: Create an issue on GitHub or contact uwudwagon on Discord.

## Credits

- **Concept:** Inspired by Jodo2410's request for PrefabBuilder integration
- **Implementation:** uwudwagon (JET developer)
- **Based on:** Minecraft's JEI (Just Enough Items) integration patterns

## License

This API is part of JET and follows the same license. Free to use for all Hytale mod development.
