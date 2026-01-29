# O Key Binding for JET

## Overview
JET now supports binding the O key to open the item browser instead of toggling creative mode, inspired by the Okey mod.

## Features

### 1. O Key Binding System
- Intercepts the O key press (GameModeSwap packet)
- Cancels the gamemode change event
- Opens JET browser instead
- Configurable cooldown to prevent spam

### 2. Configuration File
Location: `mods/JET/JET_config.json`

```json
{
  "bindOKey": false,
  "appliesOnOP": false,
  "cooldownMs": 250
}
```

**Settings:**
- `bindOKey`: Enable/disable O key binding (default: `false`)
- `appliesOnOP`: Whether to apply binding to OPs (default: `false`)
- `cooldownMs`: Cooldown between O key presses in milliseconds (default: `250`)

### 3. Commands

#### `/jetbind` (alias: `/jbind`)
**Permission:** Creative mode / OP only
**Description:** Toggles O key binding on/off

Shows a nice status display:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[JET] O Key Binding
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Status: ✓ ENABLED
  Cooldown: 250ms

  ✓ O key will open JET browser

  ⚠ Restart required for changes to take effect
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### `/jetsettings` (alias: `/jsettings`)
**Permission:** Creative mode / OP only
**Description:** Opens GUI to configure JET settings (WIP)

## How It Works

### 1. Packet Interception
The `OKeyPacketFilter` class intercepts `SyncInteractionChains` packets that contain `GameModeSwap` interactions (O key presses).

### 2. Event Cancellation
When a `ChangeGameModeEvent` is triggered for Creative mode:
1. Checks if there's a pending trigger within 500ms
2. If found, cancels the gamemode change event
3. Opens JET browser instead (TODO: currently just cancels)

### 3. Permission System
- Only players with Creative mode permission can use `/jetbind`
- Normal Adventure mode players won't have access to O key binding configuration
- The `appliesOnOP` setting controls whether OPs get the O key binding

## Installation

1. Place JET mod in `mods` folder
2. Start server - config will be auto-generated
3. Use `/jetbind` to enable O key binding
4. Restart server for changes to take effect

## Default Behavior

**By default, O key binding is DISABLED** to avoid interfering with normal gamemode switching. It must be explicitly enabled via `/jetbind` command or by editing the config file.

## Technical Details

### Files Added
- `JETConfig.java` - Configuration data class
- `OKeyPacketFilter.java` - Packet interceptor for O key presses
- `JETBindCommand.java` - Command to toggle binding
- `JETSettingsCommand.java` - GUI settings command (WIP)
- `JETSettingsGui.java` - Settings GUI (WIP)

### Implementation Based On
This implementation is based on the Okey mod's approach:
- Intercepts `InteractionType.GameModeSwap` packets
- Uses `ChangeGameModeEvent` cancellation
- Stores pending triggers with 500ms timeout
- Configurable via JSON file

## TODO
- Actually open JET GUI when O key is pressed (currently just cancels gamemode change)
- Add per-player settings for O key binding
- Complete settings GUI implementation
- Add permission node system instead of just gamemode-based access
