// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.topology.Canton3xBootstrap;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which bootstrap an argument list produces, asserted without a Canton.
 *
 * This is the whole of increment D that can go wrong quietly. `SandboxStack`
 * refuses a script of the wrong KIND loudly - a console bootstrap under
 * `daemon` throws before anything starts - but it cannot tell that a daemon
 * script was built without the party the command line asked for. That failure
 * is a stack which comes up green, reports ready, and has provisioned nothing.
 *
 * Author Claude/bentzn
 */
class SandboxAppTest {

    private static final String STR_HINT = "alice";

    private static final String STR_USER = "alice-user";


    private static SandboxOptions parse(String... arrArg) {
        return SandboxOptions.parse(arrArg);
    }


    @Test
    void theDaemonLauncherGetsTheInJvmScript() {
        SandboxOptions options = parse("--launcher", "daemon");

        assertTrue(options.isDaemon());

        Canton3xDaemonBootstrap bootstrap = SandboxApp.bootstrapDaemonFor(options);
        assertFalse(bootstrap.flagProvisions());
        assertFalse(bootstrap.flagPings());
    }


    @Test
    void theSameArgumentsProduceTheSameWorkUnderBothLaunchers() {
        SandboxOptions optSub = parse("--party", STR_HINT, "--user", STR_USER, "--ping");
        SandboxOptions optDaemon = parse("--launcher", "daemon", "--party", STR_HINT, "--user",
                STR_USER, "--ping");

        Canton3xBootstrap bootstrapSub = SandboxApp.bootstrapFor(optSub);
        Canton3xDaemonBootstrap bootstrapDaemon = SandboxApp.bootstrapDaemonFor(optDaemon);

        // The point is the PAIR. A launcher flag that quietly dropped the
        // party would leave a stack that starts, reports ready and has
        // provisioned nothing - and no start-up assertion would see it.
        assertTrue(bootstrapSub.flagProvisions());
        assertTrue(bootstrapDaemon.flagProvisions());
        assertTrue(bootstrapSub.flagPings());
        assertTrue(bootstrapDaemon.flagPings());
        assertEquals(STR_HINT, bootstrapDaemon.strPartyHint());
        assertEquals(STR_USER, bootstrapDaemon.strUserId());
    }


    /**
     * The DARs are not on either script. The subcommand renders `--dar` from
     * the spec and `SandboxStack.buildDaemon` attaches the same spec list to
     * the daemon bootstrap, so a `--dars` handled here as well would upload
     * everything twice.
     */
    @Test
    void neitherBootstrapCarriesTheDars(@TempDir Path dirTemp) throws IOException {
        // A REAL DIRECTORY, because `--dars` is expanded into a list of files
        // when the arguments are read rather than carried as a path.
        // The path used here was `/tmp/dars` and was never looked at.
        Path dirDars = Files.createDirectory(dirTemp.resolve("dars"));
        Files.createFile(dirDars.resolve("model.dar"));

        SandboxOptions options = parse("--launcher", "daemon", "--dars", dirDars.toString());

        assertFalse(options.lstFileDar().isEmpty(), "there is a DAR to go up twice");
        assertFalse(SandboxApp.bootstrapDaemonFor(options).flagUploadsDars(),
                "the app attached the DARs as well, so they would go up twice");
    }
}
