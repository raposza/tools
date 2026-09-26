// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.settings.RaposzaSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One file per version, and nothing shared between two of them.
 *
 * Headless throughout, like every other test in this module: nothing here
 * opens a toolkit, and the constants the profile defaults to are on
 * {@link SandboxProfile} rather than on the panel for exactly that reason.
 *
 * Author Claude/bentzn
 */
class ProfileStoreTest {

    private static final VersionId VERSION_3 = VersionId.parse("3.5.11");

    private static final VersionId VERSION_2 = VersionId.parse("2.10.4");


    @Test
    void twoVersionsDoNotShareAFileAndDoNotShareAValue(@TempDir Path dirTemp) {
        ProfileStore store = new ProfileStore(dirTemp);

        store.write(VERSION_3, Edition.OPEN_SOURCE, new SandboxProfile(30211, 32321,
                dirTemp.resolve("three"), 300, null, null));
        store.write(VERSION_2, Edition.UNKNOWN, new SandboxProfile(30400, 32400,
                dirTemp.resolve("two"), 600, null, null));

        assertNotEquals(store.fileOf(VERSION_3, Edition.OPEN_SOURCE),
                store.fileOf(VERSION_2, Edition.UNKNOWN));
        assertEquals(32321, store.read(VERSION_3, Edition.OPEN_SOURCE, null).nPortPostgres());
        assertEquals(32400, store.read(VERSION_2, Edition.UNKNOWN, null).nPortPostgres());
        assertEquals(600, store.read(VERSION_2, Edition.UNKNOWN, null).nSecondsReady());
    }


    @Test
    void theEditionIsPartOfTheKeyBecauseThreeFourFourShippedAsBoth(@TempDir Path dirTemp) {
        ProfileStore store = new ProfileStore(dirTemp);
        VersionId version = VersionId.parse("3.4.4");

        store.write(version, Edition.OPEN_SOURCE, new SandboxProfile(30211, 32321,
                dirTemp.resolve("os"), 300, null, null));
        store.write(version, Edition.ENTERPRISE, new SandboxProfile(30500, 32500,
                dirTemp.resolve("ee"), 300, null, null));

        assertEquals(32321, store.read(version, Edition.OPEN_SOURCE, null).nPortPostgres());
        assertEquals(32500, store.read(version, Edition.ENTERPRISE, null).nPortPostgres());
    }


    @Test
    void anAbsentProfileIsTheVersionsOwnDefaults(@TempDir Path dirTemp) {
        ProfileStore store = new ProfileStore(dirTemp);
        SandboxProfile profile = store.read(VERSION_3, Edition.OPEN_SOURCE, null);

        assertEquals(RaposzaSettings.current().nPortFirst(), profile.nPortFirst());
        assertEquals(RaposzaSettings.current().nPortPostgres(), profile.nPortPostgres());
        assertEquals(SandboxProfile.N_SECONDS_READY_DEFAULT, profile.nSecondsReady());
        // THE RUN DIRECTORY IS PER VERSION. `<run>/dars` is uploaded at start
        // and a DAR is compiled against an LF version, so a directory shared
        // between two generations offers each of them the other's DARs.
        assertTrue(profile.dirRun().toString().endsWith(
                VersionKey.strOf(VERSION_3, Edition.OPEN_SOURCE)));
    }


    @Test
    void oneUnusableKeyCostsThatKeyAndNoOther(@TempDir Path dirTemp) throws Exception {
        ProfileStore store = new ProfileStore(dirTemp);
        SandboxProfile profileFallback = SandboxProfile.ofDefaults(VERSION_3,
                Edition.OPEN_SOURCE);

        Properties props = new Properties();
        props.setProperty(SandboxProfile.STR_KEY_PORT_FIRST, "not a number");
        props.setProperty(SandboxProfile.STR_KEY_PORT_POSTGRES, "32600");
        props.setProperty(SandboxProfile.STR_KEY_TIMEOUT_READY, "999999");

        Files.createDirectories(dirTemp);
        try (java.io.OutputStream out = Files.newOutputStream(
                store.fileOf(VERSION_3, Edition.OPEN_SOURCE))) {
            props.store(out, "hand-edited");
        }

        SandboxProfile profile = store.read(VERSION_3, Edition.OPEN_SOURCE, profileFallback);
        assertEquals(profileFallback.nPortFirst(), profile.nPortFirst());
        assertEquals(profileFallback.nSecondsReady(), profile.nSecondsReady());
        assertEquals(32600, profile.nPortPostgres());
        assertEquals(profileFallback.dirRun(), profile.dirRun());
    }


    @Test
    void aProfileRoundTripsThroughItsProperties(@TempDir Path dirTemp) {
        SandboxProfile profile = new SandboxProfile(30300, 32421,
                dirTemp.resolve("run"), 450, dirTemp.resolve("dars"),
                List.of("petshop-0.0.1.dar", "other.dar"));
        SandboxProfile profileBack = SandboxProfile.ofProperties(profile.toProperties(),
                SandboxProfile.ofDefaults(VERSION_3, Edition.OPEN_SOURCE));

        assertEquals(profile, profileBack);
    }


    @Test
    void noDarDirectoryMeansTheRunDirectoryAndKeepsFollowingIt(@TempDir Path dirTemp) {
        SandboxProfile profile = new SandboxProfile(30211, 32321, dirTemp.resolve("run"), 300,
                null, null);

        assertNull(profile.dirDars(), "nothing explicit was set, so nothing is remembered");
        assertEquals(dirTemp.resolve("run").resolve(RunDirectory.STR_DIR_DARS).toAbsolutePath(),
                profile.dirDarsEffective());
        assertFalse(profile.toProperties().containsKey(SandboxProfile.STR_KEY_DIR_DARS),
                "an absent key is what keeps it following the run directory");

        // MOVED, and the DAR directory moves with it. A default copied into the
        // profile at write time would still point at the old run directory.
        SandboxProfile profileMoved = new SandboxProfile(30211, 32321, dirTemp.resolve("moved"),
                300, null, null);
        assertEquals(dirTemp.resolve("moved").resolve(RunDirectory.STR_DIR_DARS).toAbsolutePath(),
                profileMoved.dirDarsEffective());
    }


    @Test
    void noSelectionMeansEveryDarAndAnEmptyOneMeansNone(@TempDir Path dirTemp) {
        SandboxProfile profileAll = new SandboxProfile(30211, 32321, dirTemp.resolve("run"), 300,
                null, null);
        assertTrue(profileAll.flagAllDars(),
                "a profile written before the selection existed uploads everything, as it did");
        assertFalse(profileAll.toProperties().containsKey(SandboxProfile.STR_KEY_DARS_SELECTED));

        SandboxProfile profileNone = profileAll.withDars(null, List.of());
        assertFalse(profileNone.flagAllDars());
        assertEquals("", profileNone.toProperties()
                .getProperty(SandboxProfile.STR_KEY_DARS_SELECTED));

        // AND IT SURVIVES THE ROUND TRIP. `upload nothing` is a state a
        // developer can reach by clearing the ticks, so a read that fell back
        // to `all` there would undo it on the next window.
        SandboxProfile profileBack = SandboxProfile.ofProperties(profileNone.toProperties(),
                profileAll);
        assertFalse(profileBack.flagAllDars());
        assertTrue(profileBack.lstDarSelected().isEmpty());
    }


    @Test
    void aHandEditedProfileLosesOnlyTheKeyThatIsNonsense(@TempDir Path dirTemp) {
        SandboxProfile profileFallback = new SandboxProfile(30211, 32321, dirTemp.resolve("run"),
                300, dirTemp.resolve("dars"), List.of("kept.dar"));

        Properties props = new Properties();
        props.setProperty(SandboxProfile.STR_KEY_DIR_DARS, "  ");
        props.setProperty(SandboxProfile.STR_KEY_DARS_SELECTED, " one.dar , ,two.dar,one.dar ");

        SandboxProfile profile = SandboxProfile.ofProperties(props, profileFallback);
        assertNull(profile.dirDars(), "a blank directory is a hand edit asking to follow again");
        assertEquals(List.of("one.dar", "two.dar"), profile.lstDarSelected(),
                "empty segments are not file names, and a name is not listed twice");
        assertEquals(32321, profile.nPortPostgres(), "the other keys are untouched");
    }
}
