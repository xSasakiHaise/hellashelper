package com.xsasakihaise.hellashelper.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Registers {@code /hellas} commands that expose metadata for Hellas suite mods.
 * <p>
 * The Helper mod acts as a centralized information hub for players and staff.
 * This registrar inspects the installed Hellas mods and dynamically exposes
 * subcommands for each of them. Each subcommand can report the installed
 * version, declared dependencies, and a human-friendly list of features that
 * is stored in {@code config/<modid>.json} files within the respective mod JARs.
 * </p>
 */
public final class HellasCommandRegistrar {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<ModCommandDefinition> MODS = Collections.unmodifiableList(Arrays.asList(
            new ModCommandDefinition("HellasAudio", "hellasaudio"),
            new ModCommandDefinition("HellasBattlebuddy", "hellasbattlebuddy"),
            new ModCommandDefinition("HellasControl", "hellascontrol"),
            new ModCommandDefinition("HellasDeck", "hellasdeck"),
            new ModCommandDefinition("HellasElo", "hellaselo"),
            new ModCommandDefinition("HellasForms", "hellasforms"),
            new ModCommandDefinition("HellasGardens", "hellasgardens"),
            new ModCommandDefinition("HellasHelper", "hellashelper", "helper", true),
            new ModCommandDefinition("HellasLibrary", "hellaslibrary"),
            new ModCommandDefinition("HellasMineralogy", "hellasmineralogy"),
            new ModCommandDefinition("HellasPatcher", "hellaspatcher"),
            new ModCommandDefinition("HellasTextures", "hellastextures"),
            new ModCommandDefinition("HellasWilds", "hellaswilds")
    ));

    private HellasCommandRegistrar() {
    }

    /**
     * Entrypoint for Forge's command registration event.
     *
     * @param event the command registration event fired during server startup
     */
    public static void register(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();
        LiteralCommandNode<CommandSource> hellasRoot = getLiteralChild(dispatcher.getRoot(), "hellas");

        if (hellasRoot == null) {
            hellasRoot = dispatcher.register(Commands.literal("hellas"));
        }

        if (hellasRoot == null) {
            LOGGER.warn("Unable to register /hellas commands because the root literal could not be obtained.");
            return;
        }

        for (ModCommandDefinition mod : MODS) {
            final LiteralCommandNode<CommandSource> modNode = findOrCreateModNode(hellasRoot, mod.commandLiteral);
            if (modNode == null) {
                continue;
            }

            registerMetadataCommands(modNode, mod);
        }
    }

    /**
     * Attempts to retrieve a literal child node with the given name.
     *
     * @param parent  root node to inspect
     * @param literal child literal name to resolve
     * @return the literal node if present, otherwise {@code null}
     */
    private static LiteralCommandNode<CommandSource> getLiteralChild(final CommandNode<CommandSource> parent,
                                                                     final String literal) {
        final CommandNode<CommandSource> child = parent.getChild(literal);
        if (child instanceof LiteralCommandNode) {
            return (LiteralCommandNode<CommandSource>) child;
        }
        return null;
    }

    /**
     * Retrieves or creates the literal node under {@code /hellas} for a specific mod.
     *
     * @param hellasRoot the {@code /hellas} root literal node
     * @param literal    the literal command identifier for the mod
     * @return a literal node that can receive additional children
     */
    private static LiteralCommandNode<CommandSource> findOrCreateModNode(final LiteralCommandNode<CommandSource> hellasRoot,
                                                                          final String literal) {
        final LiteralCommandNode<CommandSource> existing = getLiteralChild(hellasRoot, literal);
        if (existing != null) {
            return existing;
        }

        final LiteralCommandNode<CommandSource> node = Commands.literal(literal).build();
        hellasRoot.addChild(node);
        return node;
    }

    /**
     * Creates the metadata subcommands for the supplied mod definition.
     *
     * @param modNode the literal node representing the mod's subcommand tree
     * @param mod     metadata describing how to interact with the mod
     */
    private static void registerMetadataCommands(final LiteralCommandNode<CommandSource> modNode,
                                                  final ModCommandDefinition mod) {
        if (modNode.getChild("version") == null) {
            modNode.addChild(Commands.literal("version")
                    .executes(context -> sendVersion(context.getSource(), mod))
                    .build());
        }

        if (modNode.getChild("dependencies") == null) {
            modNode.addChild(Commands.literal("dependencies")
                    .executes(context -> sendDependencies(context.getSource(), mod))
                    .build());
        }

        if (modNode.getChild("features") == null) {
            modNode.addChild(Commands.literal("features")
                    .executes(context -> sendFeatures(context.getSource(), mod))
                    .build());
        }

        if (mod.rollcall && modNode.getChild("rollcall") == null) {
            modNode.addChild(Commands.literal("rollcall")
                    .executes(context -> runRollcall(context.getSource()))
                    .build());
        }
    }

    /**
     * Sends the resolved version information for the requested mod.
     *
     * @param source destination for feedback messages
     * @param mod    definition for the mod being queried
     * @return brigadier command result
     */
    private static int sendVersion(final CommandSource source, final ModCommandDefinition mod) {
        if (!isModPresent(mod)) {
            source.sendFailure(new StringTextComponent(mod.displayName + " is not present on this server."));
            return 0;
        }

        final Optional<ModMetadata> metadata = loadMetadata(mod);
        final String version = metadata.map(ModMetadata::getVersion)
                .orElseGet(() -> resolveInstalledVersion(mod).orElse("Unknown"));

        source.sendSuccess(new StringTextComponent(mod.displayName + " version: " + version), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Outputs the dependency declarations parsed from the mod's metadata file.
     *
     * @param source destination for feedback messages
     * @param mod    definition for the mod being queried
     * @return brigadier command result
     */
    private static int sendDependencies(final CommandSource source, final ModCommandDefinition mod) {
        if (!isModPresent(mod)) {
            source.sendFailure(new StringTextComponent(mod.displayName + " is not present on this server."));
            return 0;
        }

        final Optional<ModMetadata> metadata = loadMetadata(mod);
        if (!metadata.isPresent() || metadata.get().getDependencies().isEmpty()) {
            source.sendSuccess(new StringTextComponent("No dependency information available for " + mod.displayName + '.'), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(new StringTextComponent(mod.displayName + " dependencies:"), false);
        for (String dependency : metadata.get().getDependencies()) {
            source.sendSuccess(new StringTextComponent(dependency), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Outputs the feature highlights declared in the mod's metadata file.
     *
     * @param source destination for feedback messages
     * @param mod    definition for the mod being queried
     * @return brigadier command result
     */
    private static int sendFeatures(final CommandSource source, final ModCommandDefinition mod) {
        if (!isModPresent(mod)) {
            source.sendFailure(new StringTextComponent(mod.displayName + " is not present on this server."));
            return 0;
        }

        final Optional<ModMetadata> metadata = loadMetadata(mod);
        if (!metadata.isPresent() || metadata.get().getFeatures().isEmpty()) {
            source.sendSuccess(new StringTextComponent("No feature information available for " + mod.displayName + '.'), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(new StringTextComponent(mod.displayName + " features:"), false);
        for (String feature : metadata.get().getFeatures()) {
            source.sendSuccess(new StringTextComponent(feature), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Executes the {@code /hellas helper rollcall} command which lists the
     * installation status of every known Hellas module.
     *
     * @param source command sender that will receive rollcall output
     * @return brigadier command result
     */
    private static int runRollcall(final CommandSource source) {
        source.sendSuccess(new StringTextComponent("Hellas suite rollcall:"), false);
        for (ModCommandDefinition mod : MODS) {
            if (!isModPresent(mod)) {
                source.sendSuccess(new StringTextComponent("- " + mod.displayName + ": missing"), false);
                continue;
            }

            final String version = loadMetadata(mod)
                    .map(ModMetadata::getVersion)
                    .orElseGet(() -> resolveInstalledVersion(mod).orElse("Unknown"));
            source.sendSuccess(new StringTextComponent("- " + mod.displayName + ": " + version), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Checks whether the provided Hellas mod is currently loaded in this runtime.
     *
     * @param mod definition of the mod to check
     * @return {@code true} if the mod is available, {@code false} otherwise
     */
    private static boolean isModPresent(final ModCommandDefinition mod) {
        return ModList.get().isLoaded(mod.modId);
    }

    /**
     * Attempts to read the installed version number directly from Forge's
     * mod container if the metadata file does not provide it.
     *
     * @param mod definition for the mod being queried
     * @return optional version string if the container is available
     */
    private static Optional<String> resolveInstalledVersion(final ModCommandDefinition mod) {
        final Optional<? extends ModContainer> container = ModList.get().getModContainerById(mod.modId);
        return container.map(value -> value.getModInfo().getVersion().toString());
    }

    /**
     * Loads the JSON metadata document bundled inside the target mod.
     * <p>
     * The document is expected to live under {@code config/<modid>.json} and
     * can include the version, dependencies, and a set of friendly feature
     * descriptions. Invalid or missing files are tolerated and result in an
     * empty {@link Optional}.
     * </p>
     *
     * @param mod definition describing the metadata file to load
     * @return optional metadata representation
     */
    private static Optional<ModMetadata> loadMetadata(final ModCommandDefinition mod) {
        if (!isModPresent(mod)) {
            return Optional.empty();
        }

        final ModFileInfo fileInfo = ModList.get().getModFileById(mod.modId);
        if (fileInfo == null) {
            LOGGER.debug("No mod file information available for {}.", mod.modId);
            return Optional.empty();
        }

        final Path metadataPath = fileInfo.getFile().findResource("config/" + mod.metadataFile);
        if (metadataPath == null) {
            LOGGER.debug("No metadata file found for {} at config/{}.", mod.modId, mod.metadataFile);
            return Optional.empty();
        }

        if (!Files.exists(metadataPath)) {
            LOGGER.debug("Metadata path {} for {} does not exist.", metadataPath, mod.modId);
            return Optional.empty();
        }

        try (BufferedReader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            final JsonElement element = new JsonParser().parse(reader);
            if (!element.isJsonObject()) {
                LOGGER.warn("Metadata for {} is not a JSON object.", mod.modId);
                return Optional.empty();
            }

            final JsonObject object = element.getAsJsonObject();
            final String version = readString(object, "version")
                    .orElseGet(() -> resolveInstalledVersion(mod).orElse("Unknown"));
            final List<String> dependencies = readStringArray(object.get("dependencies"));
            final List<String> features = readStringArray(object.get("features"));

            return Optional.of(new ModMetadata(version, dependencies, features));
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Failed to read metadata for {}", mod.modId, ex);
            return Optional.empty();
        }
    }

    /**
     * Reads a string property from the supplied JSON object.
     *
     * @param object object to inspect
     * @param key    property name
     * @return optional string value when the key is present and primitive
     */
    private static Optional<String> readString(final JsonObject object, final String key) {
        if (!object.has(key)) {
            return Optional.empty();
        }

        final JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return Optional.empty();
        }

        return Optional.of(element.getAsString());
    }

    /**
     * Converts a JSON array into an immutable list of strings.
     *
     * @param element JSON element to convert
     * @return list of string values, or an empty list when the element is null/invalid
     */
    private static List<String> readStringArray(final JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }

        final JsonArray array = element.getAsJsonArray();
        final List<String> values = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (entry.isJsonPrimitive()) {
                values.add(entry.getAsString());
            }
        }
        return values;
    }

    /**
     * Describes how to expose a specific Hellas mod via the {@code /hellas}
     * command tree. Each definition includes the mod id, a friendly display
     * name, and the literal that users type to reach it.
     */
    private static final class ModCommandDefinition {
        private final String displayName;
        private final String modId;
        private final String commandLiteral;
        private final String metadataFile;
        private final boolean rollcall;

        private ModCommandDefinition(final String displayName, final String modId) {
            this(displayName, modId, false);
        }

        private ModCommandDefinition(final String displayName, final String modId, final boolean rollcall) {
            this(displayName, modId, modId, rollcall);
        }

        private ModCommandDefinition(final String displayName, final String modId, final String commandLiteral, final boolean rollcall) {
            this.displayName = Objects.requireNonNull(displayName);
            this.modId = Objects.requireNonNull(modId);
            this.commandLiteral = Objects.requireNonNull(commandLiteral);
            this.metadataFile = modId + ".json";
            this.rollcall = rollcall;
        }
    }

    /**
     * Immutable DTO that represents the metadata file payload for a specific mod.
     */
    private static final class ModMetadata {
        private final String version;
        private final List<String> dependencies;
        private final List<String> features;

        private ModMetadata(final String version, final List<String> dependencies, final List<String> features) {
            this.version = version;
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.features = Collections.unmodifiableList(new ArrayList<>(features));
        }

        /**
         * @return string version resolved from metadata or Forge
         */
        public String getVersion() {
            return version;
        }

        /**
         * @return list of textual dependency entries defined in the metadata file
         */
        public List<String> getDependencies() {
            return dependencies;
        }

        /**
         * @return list of human-friendly feature blurbs sourced from metadata
         */
        public List<String> getFeatures() {
            return features;
        }
    }
}
