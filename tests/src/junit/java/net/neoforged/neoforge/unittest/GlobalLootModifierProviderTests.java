/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.NeverCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class GlobalLootModifierProviderTests {
    private static class TestProvider extends GlobalLootModifierProvider {
        private final Runnable onStart;

        TestProvider(PackOutput out, String modid, Runnable onStart) {
            super(out, CompletableFuture.completedFuture(null), modid);
            this.onStart = onStart;
        }

        @Override
        protected void start() {
            if (onStart != null) onStart.run();
        }

        CompletableFuture<Void> runNow(CachedOutput cache) {
            return super.run(cache, (HolderLookup.Provider) null);
        }
    }

    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("glm-prov-test-");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    @DisplayName("run() writes global_loot_modifiers.json with replace=false and empty entries when none added")
    void run_writes_global_with_defaults() throws Exception {
        Path root = createTempDir();
        PackOutput out = new PackOutput(root);
        TestProvider prov = new TestProvider(out, "testmod", () -> {});

        prov.runNow(CachedOutput.NO_CACHE).get();

        Path global = out.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve("neoforge").resolve("loot_modifiers").resolve("global_loot_modifiers.json");
        assertThat(Files.exists(global)).isTrue();

        JsonObject json = readJson(global);
        assertThat(json.get("replace").getAsBoolean()).isFalse();
        assertThat(json.get("entries").getAsJsonArray()).isEmpty();
    }

    @Test
    @DisplayName("replacing() sets replace=true in global_loot_modifiers.json")
    void replacing_sets_replace_true() throws Exception {
        Path root = createTempDir();
        PackOutput out = new PackOutput(root);
        TestProvider prov = new TestProvider(out, "mymod", () -> {}) {
            @Override
            protected void start() {
                replacing();
            }
        };

        prov.runNow(CachedOutput.NO_CACHE).get();

        Path global = out.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve("neoforge").resolve("loot_modifiers").resolve("global_loot_modifiers.json");
        JsonObject json = readJson(global);
        assertThat(json.get("replace").getAsBoolean()).isTrue();
        assertThat(json.get("entries").getAsJsonArray()).isEmpty();
    }

    @Test
    @DisplayName("add(varargs) collects provided conditions into WithConditions for serialization map")
    void add_varargs_collects_conditions() throws Exception {
        Path root = createTempDir();
        PackOutput out = new PackOutput(root);
        final List<ICondition> conds = Arrays.asList(NeverCondition.INSTANCE, NeverCondition.INSTANCE);

        TestProvider prov = new TestProvider(out, "modx", () -> {}) {
            @Override
            protected void start() {
                IGlobalLootModifier dummy = new IGlobalLootModifier() {
                    @Override
                    public it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack> apply(it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack> generatedLoot, net.minecraft.world.level.storage.loot.LootContext context) {
                        return generatedLoot;
                    }

                    @Override
                    public com.mojang.serialization.MapCodec<? extends IGlobalLootModifier> codec() {
                        throw new UnsupportedOperationException("Not used in this test");
                    }
                };
                add("a", dummy, conds);
            }
        };

        // Invoke start() to populate toSerialize without running full provider
        prov.getClass().getDeclaredMethod("start").invoke(prov);

        Field f = GlobalLootModifierProvider.class.getDeclaredField("toSerialize");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) f.get(prov);
        assertThat(map).containsKey("a");
        Object withConditions = map.get("a");
        @SuppressWarnings("unchecked")
        List<ICondition> stored = (List<ICondition>) withConditions.getClass().getMethod("conditions").invoke(withConditions);
        assertThat(stored).hasSize(2);
    }

    @Test
    @DisplayName("Insertion order of add calls is preserved in the serialization map")
    void add_preserves_insertion_order() throws Exception {
        Path root = createTempDir();
        PackOutput out = new PackOutput(root);
        List<String> order = new ArrayList<>();

        TestProvider prov = new TestProvider(out, "order", () -> {}) {
            @Override
            protected void start() {
                IGlobalLootModifier dummy = new IGlobalLootModifier() {
                    @Override
                    public it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack> apply(it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.item.ItemStack> generatedLoot, net.minecraft.world.level.storage.loot.LootContext context) {
                        return generatedLoot;
                    }

                    @Override
                    public com.mojang.serialization.MapCodec<? extends IGlobalLootModifier> codec() {
                        throw new UnsupportedOperationException("Not used in this test");
                    }
                };
                add("first", dummy, NeverCondition.INSTANCE);
                add("second", dummy, NeverCondition.INSTANCE);
                add("third", dummy, NeverCondition.INSTANCE);
            }
        };

        // Invoke start() to populate toSerialize
        prov.getClass().getDeclaredMethod("start").invoke(prov);

        Field f = GlobalLootModifierProvider.class.getDeclaredField("toSerialize");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, ?> map = (LinkedHashMap<String, ?>) f.get(prov);
        order.addAll(map.keySet());
        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("getName reports the expected provider name with modid")
    void getName_returns_expected() throws Exception {
        PackOutput out = new PackOutput(createTempDir());
        TestProvider prov = new TestProvider(out, "abcmod", () -> {});
        assertThat(prov.getName()).isEqualTo("Global Loot Modifiers : abcmod");
    }
}
