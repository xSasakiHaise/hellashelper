# HellasHelper

HellasHelper is the information kiosk mod for the Hellas suite of Pixelmon/Forge sidemods.
It exposes `/hellas` commands that let staff and players inspect which Hellas components are
installed on the server, what versions they run, and which features or dependencies they
expect. The mod also verifies that the shared HellasControl core is present before allowing
any helper commands to be used.

## Feature overview
- **Unified `/hellas` command tree** – Creates a root command with subcommands for every
  known Hellas module, so users have a consistent interface regardless of the specific mod
  they are interested in.
- **Per-mod metadata queries** – Supports `/hellas <mod> version`, `/hellas <mod> dependencies`,
  and `/hellas <mod> features` to expose data drawn from simple JSON metadata files bundled
  with each Hellas mod.
- **Rollcall summary** – Provides `/hellas helper rollcall`, which enumerates every known
  Hellas component, reports whether it is currently installed, and prints the detected
  version or indicates when a mod is missing.
- **Core entitlement validation** – Reuses the HellasControl `CoreCheck` API to make sure the
  helper functionality only activates when the central control module is loaded and the
  runtime is entitled to run this helper mod.

## Technical overview
- `com.xsasakihaise.hellashelper.HellasHelper` is the Forge `@Mod` entry point. It wires the
  mod into the Forge lifecycle, executes entitlement checks during common setup, and subscribes
  to `RegisterCommandsEvent` to install the helper command tree.
- `com.xsasakihaise.hellashelper.command.HellasCommandRegistrar` owns the Brigadier command
  definitions. It knows about every Hellas mod, resolves their metadata from
  `config/<modid>.json`, and builds the `/hellas` hierarchy with version/dependency/feature
  subcommands plus the optional rollcall handler.
- Metadata is loaded lazily and only when a command is executed. Each metadata file is a
  simple JSON document with three top-level keys: `version`, `dependencies` (array of strings),
  and `features` (array of strings). The registrar tolerates missing files and falls back to
  Forge's own mod info to fill in the version number when necessary.

## Extension points
- **Adding another Hellas mod to the command tree** – Define a new `ModCommandDefinition`
  entry in `HellasCommandRegistrar`. Provide a friendly display name, the mod id, and if
  desired a custom command literal or rollcall flag. Ensure the target mod bundles a
  `config/<modid>.json` metadata file containing its version, dependencies, and feature list.
- **Customizing metadata** – Update the JSON metadata shipped with each mod. The helper will
  automatically reflect the changes during the next reload because the data is read on-demand
  each time a command runs.

## Dependencies & environment
- **Minecraft:** 1.16.5 (official mappings)
- **Forge:** 36.2.42 via ForgeGradle 6
- **Pixelmon:** Not referenced directly, but the mod is intended for Pixelmon-based Hellas
  servers.
- **Required Hellas modules:** HellasControl 2.0.0+ (for `CoreCheck`), plus any mods listed in
  the `MODS` array when their metadata is queried.

## Migration notes
- The helper mod relies heavily on Forge's Brigadier-based command system and on
  `ModList`/`ModFileInfo` internals to look up metadata paths inside other mod JARs. These
  APIs changed significantly across major Forge versions, so revisiting
  `HellasCommandRegistrar` is necessary when porting to a different Minecraft or Forge
  release.
- The entitlement workflow is coupled to the HellasControl API. Updating to a new control
  core version may require touching the `HellasHelper#onCommonSetup` logic.
