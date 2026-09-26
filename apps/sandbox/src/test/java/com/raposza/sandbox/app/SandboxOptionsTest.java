// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.Edition;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.process.SandboxLauncher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The argument surface, tested without a Canton on the machine.
 *
 * That separation is the point of {@link SandboxOptions} existing at all: a
 * parser that could only be exercised through a live start would be tested by
 * the one suite nobody runs on every commit.
 *
 * Author Claude/bentzn
 */
class SandboxOptionsTest {

    @Test
    void oneDarAtATimeAndItCombinesWithADirectory(@TempDir Path dirTemp) throws IOException {
        Path dirDars = Files.createDirectory(dirTemp.resolve("dars"));
        Path fileInside = Files.createFile(dirDars.resolve("inside.dar"));
        Path fileOutside = Files.createFile(dirTemp.resolve("outside.dar"));

        SandboxOptions options = SandboxOptions.parse(new String[] {
                "--dars", dirDars.toString(), "--dar", fileOutside.toString() });

        assertEquals(List.of(fileInside, fileOutside), options.lstFileDar());
    }


    @Test
    void aDarDirectoryThatIsNotThereIsAnArgumentErrorRatherThanAFailedStart(
            @TempDir Path dirTemp) {
        // NAMED AFTER THE ARGUMENT. Carried as a directory this only failed a
        // minute into a start, where it reads as the stack refusing something
        // rather than as a path that was never there.
        assertThrows(IllegalArgumentException.class, () -> SandboxOptions.parse(
                new String[] { "--dars", dirTemp.resolve("absent").toString() }));
        assertThrows(IllegalArgumentException.class, () -> SandboxOptions.parse(
                new String[] { "--dar", dirTemp.resolve("absent.dar").toString() }));
    }


    @Test
    void noDarArgumentIsAnEmptyListRatherThanNull() {
        // ONE SPELLING of `nothing to upload`. Two - a null and an empty list -
        // is how one of them gets missed at the one call site that matters.
        assertTrue(SandboxOptions.parse(new String[] {}).lstFileDar().isEmpty());
        assertTrue(SandboxOptions.ofDefaults().lstFileDar().isEmpty());
    }


    @Test
    void anEmptyCommandLineStartsTheDefaultLine() {
        SandboxOptions options = SandboxOptions.parse(new String[] {});

        assertNull(options.version());
        assertEquals(SandboxOptions.strLineDefault(), options.strLine());
        assertNull(options.edition(), "no edition means any, and null is what the finder takes");
        assertEquals(0, options.nPortOffset());
        // THE SETTING, NOT THE CONSTANT. `SandboxOptions` reads
        // `RaposzaSettings.current()`, which reads the operator's own
        // settings file, so asserting a compiled default made this test pass
        // only on a machine whose settings.properties happened to agree with
        // it - and it went red the moment the defaults moved.
        assertEquals(RaposzaSettings.current().nPortPostgres(), options.nPortPostgres());
        assertEquals(SandboxOptions.PqsMode.OFF, options.pqs(),
                "PQS starts when it is asked for; a default that started scribe would write a"
                        + " database nobody asked for");
        assertFalse(options.isPersistent());
        assertFalse(options.flagProvisions());

        // The subcommand, and not because `daemon` is unmeasured - it carries
        // auth, an admin token and DARs. It is the default because it is what
        // this application was built against.
        assertEquals(SandboxLauncher.SUBCOMMAND, options.launcher());
        assertFalse(options.isDaemon());
    }


    @Test
    void everyValueArgumentIsRead(@TempDir Path dirTemp) throws IOException {
        Path dirDars = Files.createDirectory(dirTemp.resolve("dars"));
        Files.createFile(dirDars.resolve("beta.dar"));
        Files.createFile(dirDars.resolve("alpha.dar"));
        Files.createFile(dirDars.resolve("notes.txt"));
        SandboxOptions options = SandboxOptions.parse(new String[] {
                "--canton", "3.5.11",
                "--edition", "enterprise",
                "--port-offset", "10000",
                "--pg-port", "35925",
                "--db-prefix", "demo",
                "--work-dir", "/tmp/work",
                "--data-dir", "/tmp/data",
                "--dars", dirDars.toString(),
                "--heap-mb", "4096",
                "--launcher", "daemon",
                "--pqs", "on",
                "--party", "alice",
                "--user", "alice-user",
                "--timeout", "600",
                "--dev",
                "--static-time",
                "--ping" });

        assertEquals("3.5.11", options.version().toString());
        assertNull(options.strLine(), "a version was given, so no line is taken");
        assertEquals(Edition.ENTERPRISE, options.edition());
        assertEquals(10000, options.nPortOffset());
        assertEquals(35925, options.nPortPostgres());
        assertEquals("demo", options.strDbPrefix());
        assertEquals(Paths.get("/tmp/work"), options.dirWork());
        assertEquals(Paths.get("/tmp/data"), options.dirData());
        // EXPANDED AT PARSE, and in file-name order rather than in whatever
        // order the filesystem lists them: upload order decides which package
        // a participant sees first, and a directory listing is not the same on
        // two machines holding identical files. `notes.txt` is not a DAR.
        assertEquals(List.of(dirDars.resolve("alpha.dar"), dirDars.resolve("beta.dar")),
                options.lstFileDar());
        assertEquals(4096, options.nHeapMb());
        assertEquals(SandboxLauncher.DAEMON, options.launcher());
        assertTrue(options.isDaemon());
        assertEquals(SandboxOptions.PqsMode.ON, options.pqs());
        assertEquals("alice", options.strPartyHint());
        assertEquals("alice-user", options.strUserId());
        assertEquals(600, options.timeoutReady().toSeconds());
        assertTrue(options.flagDev());
        assertTrue(options.flagStaticTime());
        assertTrue(options.flagPing());
        assertTrue(options.isPersistent());
        assertTrue(options.flagProvisions());
    }


    @Test
    void anEditionOfAnyIsNoEditionAtAll() {
        assertNull(SandboxOptions.parse(new String[] { "--edition", "any" }).edition());
        assertEquals(Edition.OPEN_SOURCE,
                SandboxOptions.parse(new String[] { "--edition", "open-source" }).edition());
    }


    @Test
    void aVersionAndALineTogetherIsRefused() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--canton", "3.5.11", "--line", "3.4" }));
        assertTrue(ex.getMessage().contains("--canton"));
    }


    @Test
    void aPartyWithoutAUserIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--party", "alice" }));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--user", "alice-user" }));
    }


    @Test
    void aMissingValueNamesTheArgumentRatherThanTheParser() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--canton" }));
        assertTrue(ex.getMessage().contains("--canton"));

        // The next option consumed as a value is the same mistake with a
        // worse outcome: it would parse and start something unasked for.
        IllegalArgumentException exNext = assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--work-dir", "--dev" }));
        assertTrue(exNext.getMessage().contains("--dev"));
    }


    @Test
    void anUnknownArgumentIsRefusedRatherThanIgnored() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--auth" }));
        assertTrue(ex.getMessage().contains("--auth"));
    }


    @Test
    void aVersionThatIsNotOneIsAUsageErrorAndNotAnInstallException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--canton", "3.5" }));
        assertTrue(ex.getMessage().contains("3.5"));
    }


    @Test
    void badNumbersAndBadModesNameWhatWasWrong() {
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--pg-port", "later" }));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--timeout", "0" }));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--pqs", "yes" }));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--launcher", "sandbox" }));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--edition", "community" }));
    }


    @Test
    void helpAndListAreParsedRatherThanActedOn() {
        assertTrue(SandboxOptions.parse(new String[] { "--help" }).flagHelp());
        assertTrue(SandboxOptions.parse(new String[] { "-h" }).flagHelp());
        assertTrue(SandboxOptions.parse(new String[] { "--list" }).flagList());
    }


    @Test
    void theUsageNamesEveryArgumentTheParserAccepts() {
        String strUsage = SandboxOptions.usage();
        String[] arrArg = { "--canton", "--line", "--edition", "--list", "--port-offset",
                "--pg-port", "--work-dir", "--data-dir", "--db-prefix", "--dars",
                "--dar",
                "--party", "--user", "--ping", "--dev", "--static-time", "--heap-mb", "--launcher",
                "--pqs", "--timeout", "--help" };
        for (String strArg : arrArg) {
            assertTrue(strUsage.contains(strArg), "the usage does not mention " + strArg);
        }

        // The two things a user would otherwise find out by hitting them.
        assertTrue(strUsage.contains("UNAUTHENTICATED"));

        // And the one that was TRUE OF THE PRODUCT once and is now true
        // of one launcher. A usage that still said "a --data-dir stack cannot
        // be restarted" full stop would be the defect this increment removed,
        // so the assertion is on the qualified sentence rather than the phrase.
        assertTrue(strUsage.contains("cannot be restarted ON THE SUBCOMMAND"));
        assertTrue(strUsage.contains("--launcher daemon runs a bootstrap that asks first"));
    }
}
