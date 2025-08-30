/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.logging;

import io.netty.buffer.ByteBuf;

public class PacketDump {
    public static String getContentDump(ByteBuf buffer) {
        int length = buffer.readableBytes();
        StringBuilder sb = new StringBuilder(length * 4 + 40);

        int i;
        for (i = 0; i < length; i++) {
            // Print hex
            sb.append(String.format("%02x ", buffer.getByte(i)));

            // Every 16 bytes, print ASCII
            if ((i + 1) % 16 == 0) {
                sb.append('\t');
                appendAscii(buffer, sb, i - 15, i + 1);
                sb.append('\n');
            }
        }
        // Handle empty buffer
        if (length == 0)
        {
            sb.append('\t').append('\n');
        }
        // Handle final incomplete line
        if (i % 16 != 0) {
            int padding = (16 - (i % 16)) * 3;
            sb.append(" ".repeat(padding));
            sb.append('\t');
            appendAscii(buffer, sb, i - (i % 16), i);
            sb.append('\n');
        }

        sb.append("Length: ").append(length);
        return sb.toString();
    }

    private static void appendAscii(ByteBuf buffer, StringBuilder sb, int start, int end) {
        for (int j = start; j < end; j++) {
            byte b = buffer.getByte(j);
            sb.append((b < 0x20 || b > 0x7F) ? '.' : (char) b);
        }
    }
}
