// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.CantonInstallations;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Workbench's reader choice, with no Canton installed.
 *
 * What an installed Canton's reader decodes is measured where `LfReaderLoader`
 * lives; this pins the choice and the fallback, and that the installation
 * finder runs on the Workbench's classpath - the one that excludes PostgreSQL.
 *
 * Author Claude/bentzn
 */
class LfReaderChoiceTest {

    /**
     * RUNS THE FINDER HERE, against empty roots. raposza-canton came into this
     * module with PostgreSQL excluded; a change that made the finder need it
     * fails on this line rather than in a user's Workbench.
     */
    @Test
    void theFinderRunsWithoutPostgres(@TempDir Path dirTmp) throws IOException {
        Path dirDaml = Files.createDirectories(dirTmp.resolve("daml"));
        Path dirDpm = Files.createDirectories(dirTmp.resolve("dpm"));
        LfReaderChoice choice = new LfReaderChoice(
                () -> new CantonInstallations(dirDaml, dirDpm).discover());

        assertTrue(choice.isBuiltIn());
        assertEquals(LfReaderChoice.STR_READER_BUILT_IN, choice.strReader());
    }


    /** A package the built-in reader refuses says what to install. */
    @Test
    void aRefusalByTheBuiltInReaderCarriesTheRemedy() {
        LfReaderChoice choice = new LfReaderChoice(List::of);

        LedgerException ex = assertThrows(LedgerException.class,
                () -> choice.decode(new byte[] {1, 2, 3}));

        assertTrue(ex.getMessage().endsWith(LfReaderChoice.STR_HINT_BUILT_IN), ex.getMessage());
        assertTrue(LfReaderChoice.STR_HINT_BUILT_IN.contains("Canton 3.5.1 or newer"));
    }


    /**
     * A Canton whose jar cannot lend its reader costs nothing but the
     * reader: the built-in one decodes as before.
     */
    @Test
    void aCantonThatCannotLendItsReaderFallsBack(@TempDir Path dirTmp) {
        CantonInstallation inst = new CantonInstallation(VersionId.parse("3.5.18"),
                Edition.OPEN_SOURCE, InstallSource.DPM, dirTmp,
                dirTmp.resolve("lib/canton-open-source-3.5.18.jar"));
        LfReaderChoice choice = new LfReaderChoice(() -> List.of(inst));

        assertTrue(choice.isBuiltIn());
    }


    /** Chosen once: the finder walks directories and the jar is large. */
    @Test
    void theInstallationsAreAskedForOnce() {
        AtomicInteger cntAsked = new AtomicInteger();
        LfReaderChoice choice = new LfReaderChoice(() -> {
            cntAsked.incrementAndGet();
            return List.of();
        });

        choice.isBuiltIn();
        choice.strReader();
        assertThrows(LedgerException.class, () -> choice.decode(new byte[] {1}));

        assertEquals(1, cntAsked.get());
        assertFalse(LfReaderChoice.STR_READER_BUILT_IN.isBlank());
    }

}
