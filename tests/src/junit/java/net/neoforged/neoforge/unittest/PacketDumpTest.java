/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import net.neoforged.neoforge.logging.PacketDump;
import org.junit.jupiter.api.Test;

public class PacketDumpTest {

    @Test
    void emptyBufferProducesOnlyHeaderAndLength() {
        ByteBuf buf = Unpooled.wrappedBuffer(new byte[0]);
        String dump = PacketDump.getContentDump(buf);
        assertEquals("\t\nLength: 0", dump, "Empty buffer should produce a tab, newline, and length 0");
    }

    @Test
    void exactly16PrintableBytesProduceTwoAsciiSegments() {
        byte[] bytes = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
        String dump = PacketDump.getContentDump(Unpooled.wrappedBuffer(bytes));

        String hex = "30 31 32 33 34 35 36 37 38 39 41 42 43 44 45 46 ";
        String ascii = "0123456789ABCDEF";

        assertTrue(dump.startsWith(hex + "\t" + ascii + "\n"), "Hex line should be followed by ASCII and newline at 16 bytes");
        assertEquals(1, countOccurrences(dump, "\t" + ascii + "\n"), "ASCII segment should appear twice when length is multiple of 16");
        assertTrue(dump.endsWith("Length: 16"), "Should end with correct length");
    }

    @Test
    void partialLineIsPaddedAndAsciiAligned() {
        byte[] bytes = new byte[] { 0x00, 0x1F, 0x20, 0x41, 0x7E };
        String dump = PacketDump.getContentDump(Unpooled.wrappedBuffer(bytes));

        String hex = "00 1f 20 41 7e ";
        String ascii = ".. A~"; // 0x00->'.', 0x1F->'.', 0x20->' ', 0x41->'A', 0x7E->'~'

        String expected = hex + " ".repeat((16 - 5) * 3) + "\t" + ascii;
        assertTrue(dump.contains(expected), "Dump should include padded spaces then a tab and ASCII for the last partial line");
        assertTrue(dump.endsWith("\nLength: 5"), "Should end with correct length");
    }

    @Test
    void nonPrintableCharactersAreReplacedWithDots() {
        byte[] bytes = new byte[] { 0x00, 0x01, 0x02 };
        String dump = PacketDump.getContentDump(Unpooled.wrappedBuffer(bytes));

        // Expect three dots in the ASCII column for these non-printable bytes
        assertTrue(dump.contains("\t..."), "Non-printable bytes should be rendered as '.' in ASCII column");
    }

    @Test
    void hexIsLowercaseWithSingleSpaces() {
        byte[] bytes = new byte[] { (byte) 0xAB, (byte) 0xCD, 0x0E };
        String dump = PacketDump.getContentDump(Unpooled.wrappedBuffer(bytes));

        String expectedHex = "ab cd 0e ";
        assertTrue(dump.startsWith(expectedHex), "Hex bytes should be lowercase and separated by single spaces");
    }

    @Test
    void multiLineDumpBreaksEvery16BytesAndPadsLastLine() {
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) bytes[i] = (byte) (0x41 + i); // 'A'..'T'

        String dump = PacketDump.getContentDump(Unpooled.wrappedBuffer(bytes));

        String asciiFirstLine = "ABCDEFGHIJKLMNOP"; // 16 bytes
        String asciiLastLine = "QRST"; // remaining 4 bytes

        assertEquals(1, countOccurrences(dump, "\t" + asciiFirstLine + "\n"), "First 16-byte ASCII line should appear exactly once");
        String ending = " ".repeat((16 - 4) * 3) + "\t" + asciiLastLine + "\nLength: 20";
        assertTrue(dump.endsWith(ending), "Final line should be padded, followed by ASCII and correct length");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
