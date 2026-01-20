# JET Keybinds Guide

JET provides multiple ways to open menus: a built-in Alt key binding that works automatically, and command aliases that can be bound to custom keys.

## Built-in Keybind (Works Automatically)

### Alt Key
- **Keybind:** Alt (Hold)
- **Action:** Opens JET Item Browser
- **Note:** Works immediately, no setup required

## Available Commands

### Item Browser
- **Command:** `/jet` or `/j`
- **Aliases:** `/jei`, `/items`
- **Description:** Opens the main JET item browser with search and recipe viewing

### Pinned Items
- **Command:** `/pinned` or `/p`
- **Aliases:** `/fav`, `/favorites`, `/favourites`
- **Description:** Opens your pinned/favorited items menu


## How to Set Up Custom Keybinds

Since Hytale doesn't allow server mods to register custom keyboard keys directly, you need to bind commands to keys through Hytale's built-in keybind system:

### Method 1: Client-Side Keybind (Recommended)
1. Open Hytale's Settings
2. Go to Controls/Keybinds section
3. Find or create a "Custom Command" keybind slot
4. Assign your desired key (e.g., `J` for item browser)
5. Set the command to execute: `/j`
6. Repeat for pinned items with `P` key → `/p`

### Method 2: Quick Access via Chat
Simply type the short command:
- Press `T` or `/` to open chat
- Type `j` and press Enter (item browser)
- Type `p` and press Enter (pinned items)

## Recommended Setup

- **Alt key** → Item Browser (already works, built-in)
- **`P` key** → `/p` (Pinned Items) - bind this in Hytale settings

## Notes

- Server-side mods in Hytale cannot directly intercept keyboard input
- The server only receives interaction packets, not raw key presses
- Commands are the official way to trigger mod functionality
- Short aliases (`/j`, `/p`) provide quick access similar to keybinds

## Alternative: Command Macros

Some Hytale clients or external tools may support command macros that let you bind keys to execute commands automatically. Check your client documentation for this feature.
