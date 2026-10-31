// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.runtime.localnet.SpliceInstallations;
import com.raposza.runtime.settings.RaposzaSettings;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R3, his handover of 2026-10-30: a blank directory field shows what it stands
 * for, and showing it writes nothing.
 *
 * Skipped where there is no toolkit, as {@link SettingsPaneInstallationsTest}.
 *
 * Author Claude/bentzn
 */
class SettingsPaneDefaultsTest {

    private static final long N_MS_WAIT = 5000;

    @TempDir
    Path dirTmp;

    private String strWas;

    private SettingsPane pane;


    @BeforeAll
    static void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "no display");
    }


    @AfterEach
    void restore() throws InterruptedException, InvocationTargetException {
        if (pane != null)
            SwingUtilities.invokeAndWait(() -> pane.cancelPendingSave());
        if (strWas == null)
            System.clearProperty(RaposzaSettings.STR_PROP_FILE);
        else
            System.setProperty(RaposzaSettings.STR_PROP_FILE, strWas);
        RaposzaSettings.reload();
    }


    @Test
    void aBlankDirectoryShowsItsDefaultAndWritesNothing() throws Exception {
        strWas = System.getProperty(RaposzaSettings.STR_PROP_FILE);
        Path fileSettings = dirTmp.resolve("settings.properties");
        System.setProperty(RaposzaSettings.STR_PROP_FILE, fileSettings.toString());
        RaposzaSettings.reload();
        SwingUtilities.invokeAndWait(() -> pane = new SettingsPane());

        AtomicReference<Object> refShown = new AtomicReference<>();
        long nMsEnd = System.currentTimeMillis() + N_MS_WAIT;
        while (refShown.get() == null && System.currentTimeMillis() < nMsEnd) {
            SwingUtilities.invokeAndWait(() -> refShown.set(pane
                    .fieldOf(RaposzaSettings.STR_KEY_DIR_SPLICE)
                    .getClientProperty(SettingsPane.STR_PROP_PLACEHOLDER)));
            Thread.sleep(20);
        }

        assertNotNull(refShown.get());
        assertEquals(SettingsPane.strDefault(SpliceInstallations.dirRootDefault(),
                com.raposza.canton.install.ToolchainRoots.STR_FROM_DEFAULT), refShown.get());
        assertNotNull(pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DPM)
                .getClientProperty(SettingsPane.STR_PROP_PLACEHOLDER));
        assertTrue(pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DAML)
                .getClientProperty(SettingsPane.STR_PROP_PLACEHOLDER).toString()
                .startsWith("default: "));
        assertEquals("", pane.fieldOf(RaposzaSettings.STR_KEY_DIR_SPLICE).getText());
        assertFalse(Files.exists(fileSettings));
    }


    @Test
    void theCheckButtonWaitsForItsAction() throws Exception {
        strWas = System.getProperty(RaposzaSettings.STR_PROP_FILE);
        System.setProperty(RaposzaSettings.STR_PROP_FILE,
                dirTmp.resolve("settings.properties").toString());
        RaposzaSettings.reload();
        SwingUtilities.invokeAndWait(() -> pane = new SettingsPane());
        assertFalse(pane.buttonMachineCheck().isEnabled());

        AtomicReference<Boolean> refRan = new AtomicReference<>(false);
        SwingUtilities.invokeAndWait(() -> {
            pane.useMachineCheckAction(() -> refRan.set(true));
            pane.buttonMachineCheck().doClick();
        });
        assertTrue(refRan.get());

        SwingUtilities.invokeAndWait(() -> pane.setMachineCheckEnabled(false));
        assertFalse(pane.buttonMachineCheck().isEnabled());
    }
}
