/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import joptsimple.internal.Strings;
import net.neoforged.neoforge.logging.CrashReportExtender;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class CrashReportExtenderTest {
    @Test
    void generateEnhancedStackTrace_includesThrowableClassAndMessage() {
        RuntimeException ex = new RuntimeException("boom");
        String trace = CrashReportExtender.generateEnhancedStackTrace(ex);
        assertNotNull(trace);
        assertTrue(trace.contains(RuntimeException.class.getName()), "Trace should include exception class name");
        assertTrue(trace.contains("boom"), "Trace should include exception message");
    }

    @Test
    void generateEnhancedStackTrace_withoutHeader_isSuffixOfWithHeader() {
        IllegalStateException ex = new IllegalStateException("state");
        String withHeader = CrashReportExtender.generateEnhancedStackTrace(ex);
        String withoutHeader = CrashReportExtender.generateEnhancedStackTrace(ex, false);

        int firstNl = withHeader.indexOf(Strings.LINE_SEPARATOR);
        assertTrue(firstNl >= 0, "Enhanced stack trace should contain a line separator");
        String expectedSuffix = withHeader.substring(firstNl);
        assertEquals(expectedSuffix, withoutHeader, "Headerless trace should equal the suffix after first line break");
    }

    @Test
    void generateEnhancedStackTrace_fromStackTraceElements_containsProvidedElement() {
        StackTraceElement element = new StackTraceElement("com.example.TestClass", "myMethod", "TestClass.java", 42);
        StackTraceElement[] arr = new StackTraceElement[] { element };

        String trace = CrashReportExtender.generateEnhancedStackTrace(arr);
        assertNotNull(trace);
        assertTrue(trace.contains("com.example.TestClass"), "Trace should include provided class name");
        assertTrue(trace.contains("myMethod"), "Trace should include provided method name");
    }

    @Test
    void generateEnhancedStackTrace_fromStackTraceElements_matchesThrowableVariantWithoutHeader() {
        StackTraceElement element = new StackTraceElement("com.example.OtherClass", "otherMethod", "OtherClass.java", 7);
        StackTraceElement[] arr = new StackTraceElement[] { element };

        // Using the API that accepts stack trace elements
        String fromElements = CrashReportExtender.generateEnhancedStackTrace(arr);

        // Construct an equivalent throwable and compare with headerless variant
        Throwable t = new Throwable("synthetic");
        t.setStackTrace(arr);
        String fromThrowableNoHeader = CrashReportExtender.generateEnhancedStackTrace(t, false);

        assertEquals(fromThrowableNoHeader, fromElements, "Both paths should produce identical output for the same stack");
    }

    @Test
    void generateEnhancedStackTrace_withoutHeader_startsWithLineSeparator() {
        Throwable t = new Throwable("header-check");
        String noHeader = CrashReportExtender.generateEnhancedStackTrace(t, false);
        assertNotNull(noHeader);
        assertTrue(noHeader.startsWith(Strings.LINE_SEPARATOR), "Headerless output should start with a line separator");
    }
}
