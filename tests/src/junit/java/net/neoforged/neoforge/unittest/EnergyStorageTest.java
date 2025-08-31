/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class EnergyStorageTest {
    @Test
    void ctor_initialEnergyIsClampedWithinBounds() {
        EnergyStorage s1 = new EnergyStorage(100, 10, 10, 150);
        assertEquals(100, s1.getEnergyStored(), "Initial energy must be clamped to capacity upper bound");

        EnergyStorage s2 = new EnergyStorage(100, 10, 10, -10);
        assertEquals(0, s2.getEnergyStored(), "Initial energy must be clamped to 0 lower bound");
    }

    @Test
    void receiveEnergy_respectsMaxReceiveAndCapacity() {
        EnergyStorage s = new EnergyStorage(100, 10, 0, 0);
        // First receive: limited by maxReceive (10)
        assertEquals(10, s.receiveEnergy(50, false));
        assertEquals(10, s.getEnergyStored());
        // Subsequent receive: still limited by maxReceive
        assertEquals(10, s.receiveEnergy(95, false));
        assertEquals(20, s.getEnergyStored());
        // Fill almost to capacity and ensure final clamp by remaining capacity
        assertEquals(10, s.receiveEnergy(100, false));
        assertEquals(30, s.getEnergyStored());
        // Bring to near full
        assertEquals(10, s.receiveEnergy(100, false));
        assertEquals(40, s.getEnergyStored());

        // Fast-forward to full
        for (int i = 0; i < 6; i++) s.receiveEnergy(100, false);
        assertEquals(100, s.getEnergyStored());
        // Receiving at full returns 0
        assertEquals(0, s.receiveEnergy(10, false));
    }

    @Test
    void receiveEnergy_returnsZeroWhenCannotReceiveOrNonPositive() {
        EnergyStorage s = new EnergyStorage(100, 0, 0, 0); // cannot receive
        assertEquals(0, s.receiveEnergy(50, false));
        assertEquals(0, s.receiveEnergy(0, false));
        assertEquals(0, s.receiveEnergy(-5, false));
        assertEquals(0, s.getEnergyStored());
    }

    @Test
    void extractEnergy_respectsMaxExtractAndStored() {
        EnergyStorage s = new EnergyStorage(100, 0, 7, 40);
        // Limited by maxExtract (7)
        assertEquals(7, s.extractEnergy(100, false));
        assertEquals(33, s.getEnergyStored());
        // Limited by current stored when low
        EnergyStorage s2 = new EnergyStorage(100, 0, 50, 12);
        assertEquals(12, s2.extractEnergy(100, false));
        assertEquals(0, s2.getEnergyStored());
    }

    @Test
    void simulateFlags_doNotChangeInternalState() {
        EnergyStorage s = new EnergyStorage(100, 10, 10, 20);
        // Simulate receive
        int r1 = s.receiveEnergy(10, true);
        assertEquals(10, r1);
        assertEquals(20, s.getEnergyStored(), "Simulated receive must not mutate state");
        // Simulate extract
        int e1 = s.extractEnergy(15, true);
        assertEquals(10, e1); // maxExtract = 10
        assertEquals(20, s.getEnergyStored(), "Simulated extract must not mutate state");
    }

    @Test
    void flags_canReceive_and_canExtract_reflectLimits() {
        EnergyStorage s1 = new EnergyStorage(100, 0, 5, 0);
        assertFalse(s1.canReceive());
        assertTrue(s1.canExtract());

        EnergyStorage s2 = new EnergyStorage(100, 7, 0, 0);
        assertTrue(s2.canReceive());
        assertFalse(s2.canExtract());
    }

    @Test
    void serializeDeserialize_roundTripAndMissingKeyDefaults() {
        var provider = VanillaRegistries.createLookup();
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);

        EnergyStorage s = new EnergyStorage(200, 50, 50, 123);
        s.serialize(out);

        var tag = out.buildResult();
        var in = TagValueInput.create(ProblemReporter.DISCARDING, provider, tag);
        EnergyStorage restored = new EnergyStorage(200, 50, 50, 0);
        restored.deserialize(in);
        assertEquals(123, restored.getEnergyStored(), "Energy should round-trip through serialize/deserialize");

        // Missing key defaults to 0
        var emptyIn = TagValueInput.create(ProblemReporter.DISCARDING, provider, new net.minecraft.nbt.CompoundTag());
        EnergyStorage s2 = new EnergyStorage(100, 10, 10, 77);
        s2.deserialize(emptyIn);
        assertEquals(0, s2.getEnergyStored(), "Missing 'energy' should default to 0 per getIntOr usage");
    }
}
