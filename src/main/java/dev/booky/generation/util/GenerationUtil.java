package dev.booky.generation.util;
// Created by booky10 in PacketEventsUtils (17:42 20.12.23)

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GenerationUtil {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    /**
     * Populated by {@link #initializeAfterBootstrap()}. Do not read before that runs.
     */
    public static HolderLookup.Provider VANILLA_REGISTRIES;
    public static RegistryAccess VANILLA_REGISTRY_ACCESS;

    private GenerationUtil() {
    }

    /**
     * Materialize {@link VanillaRegistries} into a real {@link RegistryAccess}, then bind
     * delayed item components against it.
     * <p>
     * Cause of PE #1563: goat_horn uses {@code delayedComponent(INSTRUMENT → PONDER)}.
     * If components are bound/encoded with a datapack-loaded instrument registry (JSON
     * file order: admire, call, dream, feel, <b>ponder</b>, …), ponder gets holder id 4
     * (wire id 5 / {@code BQ==}). PacketEvents' {@code instrument.json} follows
     * {@code Instruments.bootstrap} order (ponder first), so that same wire id decodes
     * as admire and the default is stripped on re-encode.
     * <p>
     * {@link VanillaRegistries#createWorldLookup()} uses bootstrap order. Materializing
     * those lookups into {@link MappedRegistry}s and binding + encoding with that same
     * {@link RegistryAccess} keeps wire ids aligned with PacketEvents.
     */
    public static void initializeAfterBootstrap() {
        HolderLookup.Provider vanillaLookup = VanillaRegistries.createWorldLookup();

        Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries = new HashMap<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries()
                .forEach(entry -> registries.put(entry.key(), entry.value()));
        vanillaLookup.listRegistries().forEach(lookup -> {
            if (!registries.containsKey(lookup.key())) {
                registries.put(lookup.key(), materialize(lookup));
            }
        });

        VANILLA_REGISTRY_ACCESS = new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
        VANILLA_REGISTRIES = VANILLA_REGISTRY_ACCESS;

        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VANILLA_REGISTRY_ACCESS)
                .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    private static <T> Registry<T> materialize(HolderLookup.RegistryLookup<T> lookup) {
        MappedRegistry<T> registry = new MappedRegistry<>((ResourceKey<? extends Registry<T>>) (ResourceKey<?>) lookup.key(), lookup.registryLifecycle());
        lookup.listElements().forEach(holder ->
                registry.register(holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
        return registry.freeze();
    }

    public static HolderLookup.Provider getVanillaRegistries() {
        if (VANILLA_REGISTRIES == null) {
            throw new IllegalStateException("GenerationUtil.initializeAfterBootstrap() has not run yet");
        }
        return VANILLA_REGISTRIES;
    }

    public static JsonElement loadJsonElement(Path path) throws IOException {
        return loadJsonElement(path, JsonElement.class);
    }

    public static <T> T loadJsonElement(Path path, Class<T> typeClass) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, typeClass);
        }
    }

    public static void saveJsonElement(JsonElement element, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            GSON.toJson(element, writer);
        }
    }

    @SuppressWarnings("unchecked") // this works
    public static String getRegistryName(Registry<?> registry) {
        Identifier registryKey = ((Registry<Registry<?>>) BuiltInRegistries.REGISTRY).getKey(registry);
        if (registryKey == null) {
            throw new IllegalStateException("Can't get name of unregistered registry: " + registry);
        }
        return toString(registryKey);
    }

    public static String asFieldName(Identifier location) {
        return toString(location)
                .toUpperCase(Locale.ROOT)
                .replace(File.separatorChar, '_') // remove nesting
                .replace('.', '_') // remove dots
                .replaceAll("__+", "_"); // remove adjacent underscores
    }

    public static String toString(Identifier resourceLoc) {
        if (Identifier.DEFAULT_NAMESPACE.equals(resourceLoc.getNamespace())) {
            return resourceLoc.getPath();
        }
        return resourceLoc.toString();
    }
}
