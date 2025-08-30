/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class ItemStackHandlerTest {

    static class TrackingHandler extends ItemStackHandler {
        int changes;
        int lastSlot = -1;
        TrackingHandler(int size) { super(size); }
        @Override protected void onContentsChanged(int slot) { changes++; lastSlot = slot; }
        @Override protected void onLoad() { /* no-op */ }
    }

    static class RejectingHandler extends ItemStackHandler {
        RejectingHandler(int size) { super(size); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }

    @Test
    void getSlots_and_validateSlotIndex() {
        TrackingHandler h = new TrackingHandler(3);
        assertEquals(3, h.getSlots());
        assertThrows(RuntimeException.class, () -> h.getStackInSlot(-1));
        assertThrows(RuntimeException.class, () -> h.getStackInSlot(3));
    }

    @Test
    void setStackInSlot_setsDirectly_andTriggersChange() {
        TrackingHandler h = new TrackingHandler(2);
        ItemStack apple = new ItemStack(Items.APPLE, 5);
        h.setStackInSlot(1, apple);
        assertEquals(1, h.changes);
        assertEquals(1, h.lastSlot);
        assertSame(apple, h.getStackInSlot(1));
    }

    @Test
    void insertItem_intoEmptySlot_respectsLimits_andSimulateFlag() {
        TrackingHandler h = new TrackingHandler(1);
        ItemStack dirt120 = new ItemStack(Items.DIRT, 120);

        // Simulate first: should not change state
        ItemStack remainderSim = h.insertItem(0, dirt120, true);
        assertEquals(0, h.changes);
        assertEquals(120 - Math.min(Item.ABSOLUTE_MAX_STACK_SIZE, dirt120.getMaxStackSize()), remainderSim.getCount());

        // Real insert
        ItemStack remainder = h.insertItem(0, dirt120, false);
        assertEquals(1, h.changes);
        int expectedInserted = Math.min(Item.ABSOLUTE_MAX_STACK_SIZE, dirt120.getMaxStackSize());
        assertEquals(dirt120.getCount() - expectedInserted, remainder.getCount());
        assertEquals(expectedInserted, h.getStackInSlot(0).getCount());
    }

    @Test
    void insertItem_mergesWithSameItem_rejectsDifferentItem() {
        TrackingHandler h = new TrackingHandler(1);
        h.setStackInSlot(0, new ItemStack(Items.APPLE, 10));

        // Merge same item
        ItemStack rem = h.insertItem(0, new ItemStack(Items.APPLE, 10), false);
        assertEquals(0, rem.getCount());
        assertEquals(20, h.getStackInSlot(0).getCount());

        // Different item returns unchanged
        ItemStack stone = new ItemStack(Items.STONE, 3);
        ItemStack out = h.insertItem(0, stone, false);
        assertSame(stone.getItem(), out.getItem());
        assertEquals(3, out.getCount());
        assertEquals(20, h.getStackInSlot(0).getCount());
    }

    @Test
    void getSlotLimit_and_getStackLimit() {
        TrackingHandler h = new TrackingHandler(1);
        assertEquals(Item.ABSOLUTE_MAX_STACK_SIZE, h.getSlotLimit(0));
        assertEquals(Math.min(Item.ABSOLUTE_MAX_STACK_SIZE, Items.APPLE.getDefaultMaxStackSize()),
                h.insertItem(0, new ItemStack(Items.APPLE, 999), true).getCount() == 0 ? 0 : 999 - h.insertItem(0, new ItemStack(Items.APPLE, 999), true).getCount());
    }

    @Test
    void deserialize_restoresSizeAndStacks() {
        TrackingHandler h1 = new TrackingHandler(3);
        h1.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        h1.setStackInSlot(1, new ItemStack(Items.STONE, 7));

        var provider = VanillaRegistries.createLookup();
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
        h1.serialize(out);
        CompoundTag tag = out.buildResult();

        TrackingHandler h2 = new TrackingHandler(1);
        var in = TagValueInput.create(ProblemReporter.DISCARDING, provider, tag);
        h2.deserialize(in);

        assertEquals(3, h2.getSlots());
        assertEquals(2, h2.getStackInSlot(0).getCount());
        assertEquals(7, h2.getStackInSlot(1).getCount());
        assertTrue(h2.getStackInSlot(2).isEmpty());
    }

    @Test
    void setSize_resetsAllStacks() {
        TrackingHandler h = new TrackingHandler(2);
        h.setStackInSlot(0, new ItemStack(Items.APPLE, 1));
        h.setStackInSlot(1, new ItemStack(Items.STONE, 1));
        h.setSize(3);
        assertEquals(3, h.getSlots());
        assertTrue(h.getStackInSlot(0).isEmpty());
        assertTrue(h.getStackInSlot(1).isEmpty());
        assertTrue(h.getStackInSlot(2).isEmpty());
    }

    @Test
    void isItemValid_defaultTrue_butCanBeOverridden() {
        RejectingHandler h = new RejectingHandler(1);
        ItemStack dirt = new ItemStack(Items.DIRT, 1);
        ItemStack out = h.insertItem(0, dirt, false);
        // Should be rejected and returned unchanged
        assertSame(dirt.getItem(), out.getItem());
        assertEquals(1, out.getCount());
        assertTrue(h.getStackInSlot(0).isEmpty());
    }
}
