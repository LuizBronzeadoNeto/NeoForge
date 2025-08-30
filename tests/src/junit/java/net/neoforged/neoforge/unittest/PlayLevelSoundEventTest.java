/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class PlayLevelSoundEventTest {

    private static Holder<SoundEvent> soundA() {
        return SoundEvents.GENERIC_EAT;
    }

    private static Holder<SoundEvent> soundB() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Test
    void test01_initialValues_matchOriginals() {
        PlayLevelSoundEvent e = new PlayLevelSoundEvent((Level) null, soundA(), SoundSource.MASTER, 0.75f, 0.5f);
        Assertions.assertEquals(0.75f, e.getOriginalVolume(), 1.0e-6f, "Original volume should be set");
        Assertions.assertEquals(0.5f, e.getOriginalPitch(), 1.0e-6f, "Original pitch should be set");
        Assertions.assertEquals(0.75f, e.getNewVolume(), 1.0e-6f, "New volume defaults to original");
        Assertions.assertEquals(0.5f, e.getNewPitch(), 1.0e-6f, "New pitch defaults to original");
    }

    @Test
    void test02_canMutateNewVolumeAndPitch_independentOfOriginals() {
        PlayLevelSoundEvent e = new PlayLevelSoundEvent((Level) null, soundA(), SoundSource.MASTER, 1.0f, 1.0f);
        e.setNewVolume(0.25f);
        e.setNewPitch(1.5f);
        Assertions.assertEquals(1.0f, e.getOriginalVolume(), 1.0e-6f);
        Assertions.assertEquals(1.0f, e.getOriginalPitch(), 1.0e-6f);
        Assertions.assertEquals(0.25f, e.getNewVolume(), 1.0e-6f);
        Assertions.assertEquals(1.5f, e.getNewPitch(), 1.0e-6f);
    }

    @Test
    void test03_canSetSoundToNull_andBackToAnother() {
        PlayLevelSoundEvent e = new PlayLevelSoundEvent((Level) null, soundA(), SoundSource.MASTER, 1.0f, 1.0f);
        // allow null
        e.setSound(null);
        Assertions.assertNull(e.getSound(), "Sound can be set to null");
        // set to a different holder
        e.setSound(soundB());
        Assertions.assertEquals(soundB(), e.getSound(), "Sound can be changed to another holder");
    }

    @Test
    void test04_setSource_null_throws() {
        PlayLevelSoundEvent e = new PlayLevelSoundEvent((Level) null, soundA(), SoundSource.MASTER, 1.0f, 1.0f);
        Assertions.assertThrows(NullPointerException.class, () -> e.setSource(null), "setSource(null) must throw NPE");
    }

    @Test
    void test05_atPosition_storesPosition() {
        Vec3 pos = new Vec3(1.25, 64.0, -8.5);
        PlayLevelSoundEvent.AtPosition e = new PlayLevelSoundEvent.AtPosition((Level) null, pos, soundA(), SoundSource.MASTER, 0.4f, 0.9f);
        Assertions.assertEquals(pos, e.getPosition(), "AtPosition should return the same Vec3 instance");
        // also verify inherited fields are wired
        Assertions.assertEquals(0.4f, e.getNewVolume(), 1.0e-6f);
        Assertions.assertEquals(0.9f, e.getNewPitch(), 1.0e-6f);
    }
}
