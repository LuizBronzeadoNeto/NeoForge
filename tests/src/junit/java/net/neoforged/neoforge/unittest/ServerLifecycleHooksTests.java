/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
public class ServerLifecycleHooksTests {

    private static final LevelResource SERVERCONFIG = new LevelResource("serverconfig");

    private static Method getServerConfigPathReflect;

    @BeforeEach
    void setupReflection() throws NoSuchMethodException {
        // Access the private method getServerConfigPath(MinecraftServer)
        getServerConfigPathReflect = ServerLifecycleHooks.class.getDeclaredMethod("getServerConfigPath", MinecraftServer.class);
        getServerConfigPathReflect.setAccessible(true);
    }

    @AfterEach
    void restoreServer(MinecraftServer server) {
        // Ensure the static currentServer is restored if a test changed it, so other tests are not impacted.
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            ServerLifecycleHooks.handleServerAboutToStart(server);
        }
    }

    private static Path invokeGetServerConfigPath(MinecraftServer server)
            throws InvocationTargetException, IllegalAccessException {
        return (Path) getServerConfigPathReflect.invoke(null, server);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) return;
        // Delete readme if exists then directory
        Files.walk(path)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children before parents
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @DisplayName("getServerConfigPath creates serverconfig directory and readme.txt when missing")
    void getServerConfigPath_creates_dir_and_readme(MinecraftServer server) throws Exception {
        Path serverConfigDir = server.getWorldPath(SERVERCONFIG);
        // Ensure a clean state
        deleteRecursively(serverConfigDir);
        assertThat(Files.exists(serverConfigDir)).isFalse();

        Path result = invokeGetServerConfigPath(server);

        assertThat(result).isEqualTo(serverConfigDir);
        assertThat(Files.isDirectory(result)).as("serverconfig directory should be created").isTrue();
        Path readme = result.resolve("readme.txt");
        assertThat(Files.exists(readme)).as("readme.txt should be created").isTrue();
        String contents = Files.readString(readme, StandardCharsets.UTF_8);
        assertThat(contents)
                .as("readme content should explain override behavior")
                .contains("Any server configs put in this folder will override the corresponding server config");
    }

    @Test
    @DisplayName("getServerConfigPath does not overwrite existing readme.txt")
    void getServerConfigPath_does_not_overwrite_existing_readme(MinecraftServer server) throws Exception {
        Path serverConfigDir = server.getWorldPath(SERVERCONFIG);
        Files.createDirectories(serverConfigDir);
        Path readme = serverConfigDir.resolve("readme.txt");
        String custom = "CUSTOM-README-CONTENT";
        Files.writeString(readme, custom, StandardCharsets.UTF_8);

        Path result = invokeGetServerConfigPath(server);
        assertThat(result).isEqualTo(serverConfigDir);
        assertThat(Files.readString(readme, StandardCharsets.UTF_8)).isEqualTo(custom);
    }

    @Test
    @DisplayName("handleServerStopped posts event, nulls currentServer, and counts down latch when expected")
    void handleServerStopped_behaviour(MinecraftServer server) throws Exception {
        // Observe ServerStoppedEvent firing
        AtomicReference<MinecraftServer> stoppedEventServer = new AtomicReference<>();
        NeoForge.EVENT_BUS.addListener((final ServerStoppedEvent event) -> stoppedEventServer.set(event.getServer()));

        // Prepare the internal latch via expectServerStopped()
        ServerLifecycleHooks.expectServerStopped();

        // Reflectively fetch the AtomicReference<CountDownLatch> to assert countdown afterwards
        Field f = ServerLifecycleHooks.class.getDeclaredField("exitLatch");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<CountDownLatch> latchRef = (AtomicReference<CountDownLatch>) f.get(null);
        CountDownLatch latch = latchRef.get();
        assertThat(latch).as("expectServerStopped should set the latch").isNotNull();

        // Act
        ServerLifecycleHooks.handleServerStopped(server);

        // Assert state and effects
        assertThat(ServerLifecycleHooks.getCurrentServer()).as("currentServer should be null after stop").isNull();
        assertThat(latch.getCount()).as("latch should be counted down").isZero();
        assertThat(stoppedEventServer.get()).as("ServerStoppedEvent should have been posted").isEqualTo(server);

        // Restore static state for subsequent tests
        ServerLifecycleHooks.handleServerAboutToStart(server);
    }

    @Test
    @DisplayName("handleServerStopped without prior expectServerStopped does not throw and posts event")
    void handleServerStopped_without_expect(MinecraftServer server) throws Exception {
        // Ensure latch is null to simulate no prior expect call
        Field f = ServerLifecycleHooks.class.getDeclaredField("exitLatch");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<CountDownLatch> latchRef = (AtomicReference<CountDownLatch>) f.get(null);
        latchRef.set(null);

        AtomicReference<MinecraftServer> stoppedEventServer = new AtomicReference<>();
        NeoForge.EVENT_BUS.addListener((final ServerStoppedEvent event) -> stoppedEventServer.set(event.getServer()));

        ServerLifecycleHooks.handleServerStopped(server);

        assertThat(ServerLifecycleHooks.getCurrentServer()).isNull();
        assertThat(stoppedEventServer.get()).isEqualTo(server);

        // Restore for following tests
        ServerLifecycleHooks.handleServerAboutToStart(server);
    }

    @Test
    @DisplayName("expectServerStopped replaces previous latch and handleServerStopped clears it")
    void expectServerStopped_replaces_and_clears(MinecraftServer server) throws Exception {
        Field f = ServerLifecycleHooks.class.getDeclaredField("exitLatch");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<CountDownLatch> latchRef = (AtomicReference<CountDownLatch>) f.get(null);

        ServerLifecycleHooks.expectServerStopped();
        CountDownLatch first = latchRef.get();
        assertThat(first).isNotNull();

        ServerLifecycleHooks.expectServerStopped();
        CountDownLatch second = latchRef.get();
        assertThat(second).isNotNull().isNotSameAs(first);

        ServerLifecycleHooks.handleServerStopped(server);

        assertThat(latchRef.get()).isNull();

        // Restore for following tests
        ServerLifecycleHooks.handleServerAboutToStart(server);
    }
}
