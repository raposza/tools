// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command line, which is where this subcommand punishes a wrong guess.
 *
 * `sandbox-console` names the participant's Ledger API `--port`, while the
 * `sandbox` subcommand calls the same thing `--ledger-api-port`. scopt rejects
 * an unknown option by refusing the WHOLE command line, so one sandbox spelling
 * left in here does not degrade the console - it stops it starting, and prints
 * a usage block rather than naming the offending flag. That is how the probe on
 * 2026-08-12 failed, and it is worth a test that no sandbox-only flag survives.
 *
 * Author Claude/bentzn
 */
class CantonConsoleProcessTest {

    private static CantonInstallation installation(Path dirHome, String strVersion)
            throws IOException {
        Path fileJar = dirHome.resolve("canton.jar");
        Files.createDirectories(dirHome);
        Files.writeString(fileJar, "not really a jar");
        return new CantonInstallation(VersionId.parse(strVersion), Edition.OPEN_SOURCE,
                InstallSource.DPM, dirHome, fileJar);
    }


    private static Path conf(Path dirTemp) throws IOException {
        Path fileConf = dirTemp.resolve("console.conf");
        Files.writeString(fileConf, "canton.remote-participants.sandbox {}\n");
        return fileConf;
    }


    private static Path script(Path dirTemp) throws IOException {
        Path fileScript = dirTemp.resolve("bootstrap.canton");
        Files.writeString(fileScript, "println(\"x\")\n");
        return fileScript;
    }


    @Test
    void theCommandIsTheConsoleSubcommandWithAConfigAndAScript(@TempDir Path dirTemp)
            throws Exception {
        List<String> lstCommand = new CantonConsoleProcess(
                installation(dirTemp.resolve("canton"), "3.5.11"), script(dirTemp), conf(dirTemp),
                dirTemp).buildCommand();

        assertEquals("java", lstCommand.get(0));
        assertTrue(lstCommand.contains("sandbox-console"));
        assertEquals(conf(dirTemp).toAbsolutePath().normalize().toString(),
                lstCommand.get(lstCommand.indexOf("-c") + 1));
        assertEquals(script(dirTemp).toAbsolutePath().normalize().toString(),
                lstCommand.get(lstCommand.indexOf("--bootstrap") + 1));
        assertTrue(lstCommand.contains("--no-tty"));
    }


    /**
     * The endpoints and the token travel in the config. A command line carrying
     * --host and the port flags connects and then fails UNAUTHENTICATED on the
     * first Ledger API call, because there is no token flag to add.
     */
    @Test
    void noConnectionFlagsSurvive(@TempDir Path dirTemp) throws Exception {
        List<String> lstCommand = new CantonConsoleProcess(
                installation(dirTemp.resolve("canton"), "3.5.11"), script(dirTemp), conf(dirTemp),
                dirTemp).buildCommand();

        for (String strFlag : List.of("--host", "--port", "--admin-api-port",
                "--sequencer-public-port", "--sequencer-admin-port", "--mediator-admin-port",
                "--ledger-api-port", "--canton-port-file")) {
            assertFalse(lstCommand.contains(strFlag),
                    strFlag + " puts the connection on the command line, where the token cannot"
                            + " follow it");
        }
    }


    @Test
    void aMissingFileIsNamed(@TempDir Path dirTemp) throws Exception {
        CantonConsoleProcess consoleNoScript = new CantonConsoleProcess(
                installation(dirTemp.resolve("canton"), "3.5.11"),
                dirTemp.resolve("absent.canton"), conf(dirTemp), dirTemp);
        assertThrows(IOException.class, consoleNoScript::buildCommand);

        CantonConsoleProcess consoleNoConf = new CantonConsoleProcess(
                installation(dirTemp.resolve("canton2"), "3.5.11"), script(dirTemp),
                dirTemp.resolve("absent.conf"), dirTemp);
        assertThrows(IOException.class, consoleNoConf::buildCommand);
    }


    @Test
    void twoXIsRefused(@TempDir Path dirTemp) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new CantonConsoleProcess(
                installation(dirTemp.resolve("canton2x"), "2.9.7"), script(dirTemp),
                conf(dirTemp), dirTemp));
    }
}
