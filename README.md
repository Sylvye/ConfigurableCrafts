# ConfigurableCrafts

ConfigurableCrafts is a Paper plugin for managing custom and overridden crafting recipes in game.

## Features

- Create shaped, shapeless, and workstation custom recipes through an inventory GUI.
- Browse and override supported vanilla crafting recipes.
- Configure brewing, furnace-like cooking, stonecutting, and smithing recipes.
- Configure ingredient matching and recipe result items from in-game item stacks.
- Restrict recipes by dimension, biome, weather, and minimum experience level.
- Admin right-click recipe entries to toggle enabled state; left-click edits and shift-right-click removes/reverts. Disabled recipes are hidden from normal lists, search and discovery; admins can re-enable them through the Disabled filter.
- Plugin-owned recipe toggles notify their owner through the backward-compatible `OwnedRecipe.enabledChanged(boolean)` callback. `ManagedRecipeRegistry.setEnabled(id, enabled)` persists and refreshes the recipe; disabled cached events remain recognizable and are rejected.
- Persist managed recipes to `plugins/ConfigurableCrafts/recipes.yml`.

## Requirements

- Java 25
- Paper 26.2 build 129 or newer compatible build
- Gradle wrapper included in this repository

## Build

On Windows:

```powershell
.\gradlew.bat build
```

On macOS/Linux:

```sh
./gradlew build
```

The built plugin jar is written to `build/libs/ConfigurableCrafts-0.1.0.jar`.

## Install

1. Build the project.
2. Copy `build/libs/ConfigurableCrafts-0.1.0.jar` into your Paper server `plugins/` directory.
3. Start or restart the server.
4. Run `/configurablecrafts` or `/cc` in game.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/configurablecrafts` | Open the ConfigurableCrafts menu. | `configurablecrafts.view` |
| `/cc` | Alias for `/configurablecrafts`. | `configurablecrafts.view` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `configurablecrafts.view` | `true` | Open the read-only recipe catalog. |
| `configurablecrafts.admin` | `op` | Create, edit, delete, and revert configurable recipes. |

## Test

```sh
./gradlew test
```

On Windows, use `.\gradlew.bat test`.

## Plugin-owned mythical recipes

MythicItems registers six owned recipes through `registry().registerOwned(...)`. Ingredients, conditions, and enabled status remain editable in the existing GUI. Their output, shaped recipe type, permanent global allowance of one, and crafter exclusion are enforced by the owning plugin. Deleting an owned recipe disables it without deleting its crafting history.

`consumeOwnedAllowance(id, owner, playerId)` claims one crafting allowance only for the currently registered owner. It returns `null` on success or the limit denial message; owner mismatches throw. Permanent allowance writes complete before success is returned, and failed writes roll back the counter. Command grants must commit their own identity/custody before delivering an item. `resetOwnedAllowance(id, owner)` restores the permanent global allowance during owner-controlled recovery.

`openRecipe(player, id)` opens its editor directly. `OwnedCraftEvent` provides final cancellable validation before an owned output is created and its permanent allowance is persisted. The owner receives completion and abort callbacks. Existing timed crafting limits remain supported.

## GitHub

This repository includes a GitHub Actions workflow that runs tests and builds the plugin on pushes and pull requests.
