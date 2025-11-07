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
 * Registers `/hellas` commands that expose metadata for Hellas suite mods.
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

    private static LiteralCommandNode<CommandSource> getLiteralChild(final CommandNode<CommandSource> parent,
                                                                     final String literal) {
        final CommandNode<CommandSource> child = parent.getChild(literal);
        if (child instanceof LiteralCommandNode) {
            return (LiteralCommandNode<CommandSource>) child;
        }
        return null;
    }

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

    private static boolean isModPresent(final ModCommandDefinition mod) {
        return ModList.get().isLoaded(mod.modId);
    }

    private static Optional<String> resolveInstalledVersion(final ModCommandDefinition mod) {
        final Optional<? extends ModContainer> container = ModList.get().getModContainerById(mod.modId);
        return container.map(value -> value.getModInfo().getVersion().toString());
    }

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

    private static final class ModMetadata {
        private final String version;
        private final List<String> dependencies;
        private final List<String> features;

        private ModMetadata(final String version, final List<String> dependencies, final List<String> features) {
            this.version = version;
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
            this.features = Collections.unmodifiableList(new ArrayList<>(features));
        }

        public String getVersion() {
            return version;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public List<String> getFeatures() {
            return features;
        }
    }
}
