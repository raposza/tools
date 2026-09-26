// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.AdminTokenOverlay;
import com.raposza.canton.topology.AuthOverlay;
import com.raposza.canton.topology.Canton3xBootstrap;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;
import com.raposza.runtime.db.SandboxPostgres;
import com.raposza.runtime.process.ProcessException;
import com.raposza.sandbox.process.SandboxLauncher;
import com.raposza.sandbox.topology.SandboxSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher choice and its refusals. Nothing is launched.
 *
 * Every refusal here fires before a port is probed, which is what makes them
 * testable without a Canton at all - and is also the property worth having:
 * a stack that cannot carry what it was asked for should not start postgres
 * first and find out afterwards.
 *
 * Author Claude/bentzn
 */
class SandboxStackLauncherTest {

    private static final String STR_URL_JWKS = "http://localhost:9999/jwks.json";

    @TempDir
    Path dirTemp;


    @Test
    void theSubcommandIsTheDefault() throws IOException {
        assertEquals(SandboxLauncher.SUBCOMMAND, stack().launcher());
    }


    @Test
    void theLauncherChoiceReturnsTheStack() throws IOException {
        SandboxStack stack = stack();
        assertSame(stack, stack.useLauncher(SandboxLauncher.DAEMON));
        assertEquals(SandboxLauncher.DAEMON, stack.launcher());
    }


    @Test
    void aNullLauncherIsRefused() throws IOException {
        SandboxStack stack = stack();
        assertThrows(IllegalArgumentException.class, () -> stack.useLauncher(null));
    }


    /**
     * The two scripts write different marker files, and reporting the wrong
     * one would say a bootstrapped daemon stack had never run its script.
     */
    @Test
    void theBootstrapMarkerFollowsTheLauncher() throws IOException {
        SandboxStack stack = stack();
        assertEquals(Canton3xBootstrap.STR_MARKER_FILE,
                stack.fileBootstrapMarker().getFileName().toString());

        stack.useLauncher(SandboxLauncher.DAEMON);
        assertEquals(Canton3xDaemonBootstrap.STR_MARKER_FILE,
                stack.fileBootstrapMarker().getFileName().toString());
    }


    @Test
    void aDaemonBootstrapOnASubcommandStackIsRefused() throws IOException {
        SandboxStack stack = stack();
        stack.useBootstrap(new Canton3xDaemonBootstrap());

        ProcessException ex = assertThrows(ProcessException.class, stack::start);
        assertTrue(ex.getMessage().contains("subcommand"), ex.getMessage());
    }


    @Test
    void aConsoleBootstrapOnADaemonStackIsRefused() throws IOException {
        SandboxStack stack = stack();
        stack.useLauncher(SandboxLauncher.DAEMON);
        stack.useBootstrap(new Canton3xBootstrap());

        ProcessException ex = assertThrows(ProcessException.class, stack::start);
        assertTrue(ex.getMessage().contains("node JVM"), ex.getMessage());
    }


    /**
     * Both of these are carried rather than refused, so what is
     * asserted is an ABSENCE - and an absence cannot be tested through
     * `start()`: a stack that gets past the check goes on to probe ports and
     * boot a PostgreSQL, which is a live test rather than this one. The check
     * is therefore called directly.
     *
     * That the composition actually hands the two files to Canton, in order
     * and after the vendor's own topology, is measured in
     * `Canton3xDaemonLiveTest.theDaemonCarriesAuthAndTheAdminToken`. Nothing
     * here could notice a stack that accepted the overlays and dropped them,
     * which is the failure this pair replaces.
     */
    @Test
    void authOnADaemonStackIsNoLongerRefused() throws IOException {
        SandboxStack stack = stack(AuthOverlay.ofJwksAudience(STR_URL_JWKS, "participant1"));
        stack.useLauncher(SandboxLauncher.DAEMON);

        assertDoesNotThrow(stack::requireLauncherCanCarry);
    }


    @Test
    void anAdminTokenOnADaemonStackIsNoLongerRefused() throws IOException {
        SandboxStack stack = stack();
        stack.useLauncher(SandboxLauncher.DAEMON);
        stack.useAdminToken(AdminTokenOverlay.ofAdmin("a-token"));

        assertDoesNotThrow(stack::requireLauncherCanCarry);
    }


    /**
     * The subcommand carried both before this increment and still does. It is
     * asserted here because the refusals that came out were on the daemon
     * branch of the same method, and a wrong edit there is a stack that
     * refuses what it has always carried.
     */
    @Test
    void theSubcommandStillCarriesBoth() throws IOException {
        SandboxStack stack = stack(AuthOverlay.ofJwksAudience(STR_URL_JWKS, "participant1"));
        stack.useAdminToken(AdminTokenOverlay.ofAdmin("a-token"));

        assertDoesNotThrow(stack::requireLauncherCanCarry);
    }


    /**
     * `--dar` is an option of the subcommand; `daemon` has none and does
     * not need one - the bootstrap uploads them. So there is no refusal,
     * and this asserts its absence rather than its message.
     *
     * A start is not attempted here for the reason this whole class exists:
     * past the refusals it reaches the port guard and an embedded PostgreSQL,
     * which is a live test. That the DARs actually reach the script is
     * `Canton3xDaemonLiveTest`.
     */
    @Test
    void darsOnADaemonStackAreNoLongerRefused() throws IOException {
        SandboxSpec spec = SandboxSpec.ofDefaults()
                .withDars(List.of(dirTemp.resolve("model.dar")));
        SandboxStack stack = new SandboxStack(installation(), spec, dirTemp.resolve("work"),
                new SandboxPostgres(), SandboxStack.STR_DEFAULT_PREFIX, null);
        stack.useLauncher(SandboxLauncher.DAEMON);

        assertDoesNotThrow(stack::requireLauncherCanCarry);
    }


    /**
     * The subcommand carries all three, and nothing here asserts that: a start
     * on the subcommand reaches the port guard and then an embedded PostgreSQL,
     * which is a live test rather than this one. `SandboxStackLiveTest` is
     * where that is measured, and a refusal wrongly firing on the subcommand
     * would turn it red.
     */
    private SandboxStack stack() throws IOException {
        return stack(null);
    }


    private SandboxStack stack(AuthOverlay auth) throws IOException {
        return new SandboxStack(installation(), SandboxSpec.ofDefaults(),
                dirTemp.resolve("work"), new SandboxPostgres(), SandboxStack.STR_DEFAULT_PREFIX,
                auth);
    }


    private CantonInstallation installation() throws IOException {
        Path dirHome = dirTemp.resolve("canton");
        Path dirLib = dirHome.resolve("lib");
        Files.createDirectories(dirLib);
        Path fileJar = dirLib.resolve("canton-open-source-3.5.11.jar");
        Files.write(fileJar, "not really a jar".getBytes(StandardCharsets.UTF_8));
        return new CantonInstallation(VersionId.parse("3.5.11"), Edition.OPEN_SOURCE,
                InstallSource.DPM, dirHome, fileJar);
    }
}
