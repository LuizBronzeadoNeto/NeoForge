/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class BuildCreativeModeTabContentsEventTest {
    private static BuildCreativeModeTabContentsEvent newEvent() {
        // Use null for CreativeModeTab and ItemDisplayParameters as they are not needed for the tested behaviors
        var parent = new InsertableLinkedOpenCustomHashSet<ItemStack>(ItemStackLinkedSet.TYPE_AND_TAG);
        var search = new InsertableLinkedOpenCustomHashSet<ItemStack>(ItemStackLinkedSet.TYPE_AND_TAG);
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("unittest", "dummy"));
        return new BuildCreativeModeTabContentsEvent(null, key, null, parent, search);
    }

    private static ItemStack s(net.minecraft.world.level.ItemLike item) {
        return new ItemStack(item);
    }

    @Test
    void test01_accept_respectsVisibilityAndPreventsDuplicates() {
        var event = newEvent();
        // Add to parent and search
        event.accept(s(Items.STONE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        // Add only to parent
        event.accept(s(Items.DIRT), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        // Add only to search
        event.accept(s(Items.SAND), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);

        ObjectSortedSet<ItemStack> parent = event.getParentEntries();
        ObjectSortedSet<ItemStack> search = event.getSearchEntries();

        Assertions.assertTrue(parent.contains(s(Items.STONE)) && search.contains(s(Items.STONE)), "STONE should be in both parent and search");
        Assertions.assertTrue(parent.contains(s(Items.DIRT)) && !search.contains(s(Items.DIRT)), "DIRT should be only in parent");
        Assertions.assertTrue(!parent.contains(s(Items.SAND)) && search.contains(s(Items.SAND)), "SAND should be only in search");

        // Duplicates should throw
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.accept(s(Items.STONE), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY));
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.accept(s(Items.STONE), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.accept(s(Items.STONE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }

    @Test
    void test02_insertAfter_validatesTargetAndOrder() {
        var event = newEvent();
        var a = s(Items.STONE);
        var b = s(Items.GRANITE);
        var c = s(Items.DIORITE);

        // Seed parent with two entries in order: a, c
        event.accept(a, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        event.accept(c, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

        // insert b after a => a, b, c
        event.insertAfter(a, b, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        var parent = event.getParentEntries().stream().toList();
        Assertions.assertEquals(a, parent.get(0));
        Assertions.assertEquals(b, parent.get(1));
        Assertions.assertEquals(c, parent.get(2));

        // Missing target in search set should throw
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertAfter(a, s(Items.SAND), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));

        // Duplicate insert should throw
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertAfter(a, b, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY));
    }

    @Test
    void test03_insertBefore_validatesTargetAndOrder() {
        var event = newEvent();
        var a = s(Items.STONE);
        var b = s(Items.GRANITE);

        // Seed search with a
        event.accept(a, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        // Insert b before a => b, a
        event.insertBefore(a, b, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        var search = event.getSearchEntries().stream().toList();
        Assertions.assertEquals(b, search.get(0));
        Assertions.assertEquals(a, search.get(1));

        // Missing target in parent set should throw
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertBefore(a, s(Items.SAND), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY));

        // Duplicate insert should throw
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertBefore(a, b, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));
    }

    @Test
    void test04_insertFirst_and_remove_respectVisibility() {
        var event = newEvent();
        var a = s(Items.STONE);
        var b = s(Items.GRANITE);

        // Seed both sets with a
        event.accept(a, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        // Insert b at the front for parent only
        event.insertFirst(b, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

        var parent = event.getParentEntries().stream().toList();
        var search = event.getSearchEntries().stream().toList();
        Assertions.assertEquals(b, parent.get(0), "insertFirst should place element at the start of parent set");
        Assertions.assertEquals(a, search.get(0), "search set should be unchanged when modifying parent only");

        // remove only from search, leaving parent intact
        event.remove(a, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        Assertions.assertTrue(event.getParentEntries().contains(a));
        Assertions.assertFalse(event.getSearchEntries().contains(a));

        // remove from both sets
        event.remove(b, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        Assertions.assertFalse(event.getParentEntries().contains(b));
        Assertions.assertFalse(event.getSearchEntries().contains(b));
    }

    @Test
    void test05_stackCountValidation_allEntryPoints() {
        var event = newEvent();
        var bad = new ItemStack(Items.DIRT, 4); // count != 1
        var a = s(Items.STONE);
        // Seed target for insertBefore/After validations
        event.accept(a, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);

        Assertions.assertThrows(IllegalArgumentException.class, () -> event.accept(bad, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY));
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertAfter(a, bad, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY));
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertBefore(a, bad, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));
        Assertions.assertThrows(IllegalArgumentException.class, () -> event.insertFirst(bad, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
}
