# ConfigurableCrafts

ConfigurableCrafts is a Paper plugin for managing custom and overridden crafting recipes in game.

## Features

- Create shaped, shapeless, and workstation custom recipes through an inventory GUI.
- Browse and override supported vanilla crafting recipes.
- Configure brewing, furnace-like cooking, stonecutting, and smithing recipes.
- Configure ingredient matching and recipe result items from in-game item stacks.
- Restrict recipes by dimension, biome, weather, and minimum experience level.
- Persist managed recipes to `plugins/ConfigurableCrafts/recipes.yml`.

## Requirements

- Java 21
- Paper API `1.21.11`
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

## GitHub

This repository includes a GitHub Actions workflow that runs tests and builds the plugin on pushes and pull requests.
