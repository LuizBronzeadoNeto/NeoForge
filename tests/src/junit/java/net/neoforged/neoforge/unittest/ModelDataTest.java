/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.*;

import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class ModelDataTest {

    private static final ModelProperty<String> P1 = new ModelProperty<>(s -> s != null && !s.isEmpty());
    private static final ModelProperty<Integer> P2 = new ModelProperty<>(i -> i != null && i >= 0);
    private static final ModelProperty<Double> P3 = new ModelProperty<>(d -> d != null);
    private static final ModelProperty<Boolean> P4 = new ModelProperty<>(b -> b != null);

    @Test
    void builder_buildWithoutProperties_returnsEmptySingleton() {
        ModelData data = ModelData.builder().build();
        assertSame(ModelData.EMPTY, data, "Empty build should return the EMPTY singleton");
        assertTrue(data.getProperties().isEmpty(), "EMPTY should report no properties");
    }

    @Test
    void of_singlePropertyStoresAndRetrievesValue() {
        ModelData data = ModelData.of(P1, "hello");
        assertTrue(data.has(P1));
        assertEquals("hello", data.get(P1));
        assertFalse(data.has(P2));
        assertNull(data.get(P2));
    }

    @Test
    void of_invalidValue_throws() {
        assertThrows(IllegalStateException.class, () -> ModelData.of(P1, ""), "Invalid value should be rejected by preconditions");
    }

    @Test
    void builder_withInvalidValue_throws() {
        var builder = ModelData.builder();
        assertThrows(IllegalStateException.class, () -> builder.with(P2, -1), "Invalid value should be rejected by preconditions");
    }

    @Test
    void derive_fromParent_preservesParentAndAddsNewProperties() {
        ModelData parent = ModelData.builder().with(P1, "x").build();
        var child = parent.derive().with(P2, 3).build();

        // Parent unchanged
        assertTrue(parent.has(P1));
        assertFalse(parent.has(P2));

        // Child contains both
        assertTrue(child.has(P1));
        assertTrue(child.has(P2));
        assertEquals("x", child.get(P1));
        assertEquals(3, child.get(P2));
    }

    @Test
    void builder_reachingHashThreshold_keepsAllEntries() {
        // Threshold is 4; add 4 entries and verify they are all present
        var data = ModelData.builder()
            .with(P1, "a")
            .with(P2, 1)
            .with(P3, 2.0)
            .with(P4, true)
            .build();

        assertTrue(data.has(P1));
        assertTrue(data.has(P2));
        assertTrue(data.has(P3));
        assertTrue(data.has(P4));
        assertEquals(4, data.getProperties().size());
    }

    @Test
    void getProperties_isUnmodifiableAndCached() {
        var data = ModelData.builder().with(P1, "v1").with(P2, 2).build();
        var set1 = data.getProperties();
        var set2 = data.getProperties();

        // Cached instance: repeated calls return the same object
        assertSame(set1, set2, "getProperties should cache and reuse the same Set instance");

        // Unmodifiable: any structural modification should throw
        assertThrows(UnsupportedOperationException.class, () -> set1.add(new ModelProperty<>()));
    }
}
