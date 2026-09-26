// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The catalogue path rule and the failure that follows from a missing file.
 *
 * Headless on purpose: everything here is decided before a window exists, and a
 * module whose only tests need a display is a module that goes untested on a
 * build machine.
 *
 * Author Claude/bentzn
 */
class CatalogueTest {

    @Test
    void noArgumentMeansTheDefaultUnderHome() {
        Path dirHome = Path.of("/home/someone");
        assertEquals(dirHome.resolve(Catalogue.PATH_DEFAULT),
                Catalogue.resolve(new String[0], dirHome));
    }


    @Test
    void nullAndBlankArgumentsFallBackToTheDefault() {
        Path dirHome = Path.of("/home/someone");
        assertEquals(dirHome.resolve(Catalogue.PATH_DEFAULT),
                Catalogue.resolve(null, dirHome));
        assertEquals(dirHome.resolve(Catalogue.PATH_DEFAULT),
                Catalogue.resolve(new String[] { "  " }, dirHome));
    }


    @Test
    void anArgumentWins() {
        assertEquals(Path.of("/etc/canton/hosts"),
                Catalogue.resolve(new String[] { "/etc/canton/hosts" }, Path.of("/home/someone")));
    }


    @Test
    void readsProfilesFromACatalogue(@TempDir Path dirTmp) throws Exception {
        Path file = dirTmp.resolve("ledger_hosts");
        Files.writeString(file, "# comment\n"
                + "Local Sandbox  http  localhost  6865  7575  -  -  rw  #2e7d32\n"
                + "Shared DEV     https  canton-dev  5011  7575  -  -  ro  #c62828\n");

        List<HostProfile> lstProfile = Catalogue.read(file);

        assertEquals(2, lstProfile.size());
        assertEquals("Local Sandbox", lstProfile.get(0).nameDisplay());
        assertEquals(6865, lstProfile.get(0).portLedger());
        assertEquals(AccessMode.READ_WRITE, lstProfile.get(0).mode());
        assertEquals(AccessMode.READ_ONLY, lstProfile.get(1).mode());
        assertTrue(lstProfile.get(1).isTls());
    }


    /**
     * A missing or empty catalogue must name the file it looked for. An empty
     * window with no participants is indistinguishable from a broken tool.
     */
    @Test
    void anEmptyCatalogueFailsNamingThePath(@TempDir Path dirTmp) throws Exception {
        Path file = dirTmp.resolve("ledger_hosts");
        Files.writeString(file, "# nothing but comments\n");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Catalogue.read(file));
        assertTrue(ex.getMessage().contains(file.toAbsolutePath().toString()), ex.getMessage());
    }

}
