/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.common.ticket.ITicketManager;
import net.neoforged.neoforge.common.ticket.SimpleTicket;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class SimpleTicketTest {
    private static class TestTicket extends SimpleTicket<String> {
        private final String match;

        TestTicket(String match) {
            this.match = match;
        }

        @Override
        public boolean matches(String toMatch) {
            return match.equals(toMatch);
        }
    }

    private static class CountingManager<T> implements ITicketManager<T> {
        int addCalls = 0;
        int removeCalls = 0;

        @Override
        public void add(SimpleTicket<T> ticket) {
            addCalls++;
        }

        @Override
        public void remove(SimpleTicket<T> ticket) {
            removeCalls++;
        }
    }

    @Test
    void ctor_isValidFalseByDefault() {
        TestTicket t = new TestTicket("x");
        assertFalse(t.isValid());
    }

    @Test
    void setManager_calledTwice_throwsPrecondition() {
        TestTicket t = new TestTicket("x");
        CountingManager<String> master = new CountingManager<>();
        CountingManager<String> dummy = new CountingManager<>();
        t.setManager(master, dummy);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> t.setManager(master, dummy));
        assertTrue(ex.getMessage().contains("Ticket is already registered to a managing system"));
    }

    @Test
    void validate_withoutManager_throwsPrecondition() {
        TestTicket t = new TestTicket("x");
        IllegalStateException ex = assertThrows(IllegalStateException.class, t::validate);
        assertTrue(ex.getMessage().contains("Ticket is not registered to a managing system"));
    }

    @Test
    void validate_addsToAllManagers_onlyOnce_untilInvalidated() {
        TestTicket t = new TestTicket("x");
        CountingManager<String> master = new CountingManager<>();
        CountingManager<String> d1 = new CountingManager<>();
        CountingManager<String> d2 = new CountingManager<>();
        t.setManager(master, d1, d2);

        // First validate: adds to master and all dummies
        t.validate();
        assertTrue(t.isValid());
        assertEquals(1, master.addCalls);
        assertEquals(1, d1.addCalls);
        assertEquals(1, d2.addCalls);
        assertEquals(0, master.removeCalls);
        assertEquals(0, d1.removeCalls);
        assertEquals(0, d2.removeCalls);

        // Second validate while already valid: no additional adds
        t.validate();
        assertEquals(1, master.addCalls);
        assertEquals(1, d1.addCalls);
        assertEquals(1, d2.addCalls);

        // Invalidate: removes from all managers
        t.invalidate();
        assertFalse(t.isValid());
        assertEquals(1, master.removeCalls);
        assertEquals(1, d1.removeCalls);
        assertEquals(1, d2.removeCalls);

        // Invalidate again while already invalid: no extra removes
        t.invalidate();
        assertEquals(1, master.removeCalls);
        assertEquals(1, d1.removeCalls);
        assertEquals(1, d2.removeCalls);

        // Re-validate: adds again once to all managers
        t.validate();
        assertTrue(t.isValid());
        assertEquals(2, master.addCalls);
        assertEquals(2, d1.addCalls);
        assertEquals(2, d2.addCalls);
    }

    @Test
    void unload_withMaster_removesOnlyFromDummies_andReturnsTrue_andSetsInvalid() {
        TestTicket t = new TestTicket("x");
        CountingManager<String> master = new CountingManager<>();
        CountingManager<String> d1 = new CountingManager<>();
        CountingManager<String> d2 = new CountingManager<>();
        t.setManager(master, d1, d2);

        boolean result = t.unload(master);
        assertTrue(result, "Unload called by master should return true");
        // Only dummies are removed, master stays untouched per contract
        assertEquals(0, master.removeCalls);
        assertEquals(1, d1.removeCalls);
        assertEquals(1, d2.removeCalls);
        // Ticket becomes invalid
        assertFalse(t.isValid());
    }

    @Test
    void unload_withNonMaster_returnsFalse_andMakesNoChanges() {
        TestTicket t = new TestTicket("x");
        CountingManager<String> master = new CountingManager<>();
        CountingManager<String> d1 = new CountingManager<>();
        CountingManager<String> d2 = new CountingManager<>();
        t.setManager(master, d1, d2);

        boolean result = t.unload(d1);
        assertFalse(result, "Unload called by non-master should return false");
        assertEquals(0, master.removeCalls);
        assertEquals(0, d1.removeCalls);
        assertEquals(0, d2.removeCalls);
        // Validity remains unchanged (default false before validate)
        assertFalse(t.isValid());
    }
}
