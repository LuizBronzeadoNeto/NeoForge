/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.configuration.SyncRegistries;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handlers.ServerPayloadHandler;
import net.neoforged.neoforge.network.payload.FrozenRegistrySyncCompletedPayload;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class ServerPayloadHandlerTest {

    private static class StubContext implements IPayloadContext {
        int finishCount = 0;
        ConfigurationTask.Type lastType = null;
        int replyCalls = 0;
        int disconnectCalls = 0;
        int handlePayloadCalls = 0;
        int handlePacketCalls = 0;
        int enqueueWorkCalls = 0;
        int playerCalls = 0;
        int flowCalls = 0;

        @Override
        public ICommonPacketListener listener() {
            throw new UnsupportedOperationException("listener should not be used by ServerPayloadHandler");
        }

        @Override
        public net.minecraft.world.entity.player.Player player() {
            playerCalls++;
            throw new UnsupportedOperationException("player should not be requested by ServerPayloadHandler");
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            enqueueWorkCalls++;
            throw new UnsupportedOperationException("enqueueWork should not be used by ServerPayloadHandler");
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
            enqueueWorkCalls++;
            throw new UnsupportedOperationException("enqueueWork should not be used by ServerPayloadHandler");
        }

        @Override
        public PacketFlow flow() {
            flowCalls++;
            return PacketFlow.CLIENTBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            handlePayloadCalls++;
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            finishCount++;
            lastType = type;
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            replyCalls++;
        }

        @Override
        public void disconnect(Component reason) {
            disconnectCalls++;
        }

        @Override
        public void handle(Packet<?> packet) {
            handlePacketCalls++;
        }
    }

    @Test
    void handle_calls_finishCurrentTask_with_SyncRegistries_TYPE() {
        StubContext ctx = new StubContext();
        ServerPayloadHandler.handle(FrozenRegistrySyncCompletedPayload.INSTANCE, ctx);
        assertEquals(1, ctx.finishCount);
        assertSame(SyncRegistries.TYPE, ctx.lastType, "Should complete the SyncRegistries configuration task type");
    }

    @Test
    void handle_multipleInvocations_incrementsFinishCount() {
        StubContext ctx = new StubContext();
        ServerPayloadHandler.handle(FrozenRegistrySyncCompletedPayload.INSTANCE, ctx);
        ServerPayloadHandler.handle(FrozenRegistrySyncCompletedPayload.INSTANCE, ctx);
        assertEquals(2, ctx.finishCount);
        assertSame(SyncRegistries.TYPE, ctx.lastType);
    }

    @Test
    void handle_doesNotInvoke_reply_disconnect_or_handlePayload() {
        StubContext ctx = new StubContext();
        ServerPayloadHandler.handle(FrozenRegistrySyncCompletedPayload.INSTANCE, ctx);
        assertEquals(0, ctx.replyCalls, "Should not reply during handling");
        assertEquals(0, ctx.disconnectCalls, "Should not disconnect during handling");
        assertEquals(0, ctx.handlePayloadCalls, "Should not handle any sub-payloads");
        assertEquals(0, ctx.handlePacketCalls, "Should not handle any vanilla packets");
        assertEquals(0, ctx.enqueueWorkCalls, "Should not enqueue work");
    }

    @Test
    void handle_doesNotAccess_player_or_flow() {
        StubContext ctx = new StubContext();
        ServerPayloadHandler.handle(FrozenRegistrySyncCompletedPayload.INSTANCE, ctx);
        assertEquals(0, ctx.playerCalls, "Should not access player in configuration phase completion");
        assertEquals(0, ctx.flowCalls, "Should not access flow for this payload");
    }

    @Test
    void handle_accepts_nullPayload_andStillFinishesTask() {
        StubContext ctx = new StubContext();
        // Method does not dereference the payload; null should be safe
        ServerPayloadHandler.handle(null, ctx);
        assertEquals(1, ctx.finishCount);
        assertSame(SyncRegistries.TYPE, ctx.lastType);
    }
}
