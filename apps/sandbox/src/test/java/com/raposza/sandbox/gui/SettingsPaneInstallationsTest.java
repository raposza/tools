// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.raposza.runtime.settings.RaposzaSettings;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three installation rows at the foot of the Settings tab - `todo.md`
 * A-45: each exists, a path typed into it is saved, and a blank one saves as
 * the default.
 *
 * <h2>Skipped where there is no toolkit</h2>
 *
 * Constructing the pane needs one, and this project runs headless builds.
 *
 * Author Claude/bentzn
 */
class SettingsPaneInstallationsTest {

    private String strWas;

    private SettingsPane pane;


    @BeforeAll
    static void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "no display");
    }


    /**
     * THE PANE'S SETTLE TIMER IS STOPPED AND THE EVENT QUEUE DRAINED BEFORE
     * THE FILE IS PUT BACK. Without it the timer fired 600 ms after this test
     * ended, against the operator's own `settings.properties`, and wrote this
     * test's temporary directories into it - measured at his console,
     * 2026-09-27.
     */
    @AfterEach
    void restore() throws InterruptedException, InvocationTargetException {
        if (pane != null) {
            pane.cancelPendingSave();
            SwingUtilities.invokeAndWait(() -> pane.cancelPendingSave());
        }
        if (strWas == null)
            System.clearProperty(RaposzaSettings.STR_PROP_FILE);
        else
            System.setProperty(RaposzaSettings.STR_PROP_FILE, strWas);
        RaposzaSettings.reload();
    }


    @Test
    void theThreeRowsExistAndSave(@TempDir Path dirTmp) {
        strWas = System.getProperty(RaposzaSettings.STR_PROP_FILE);
        System.setProperty(RaposzaSettings.STR_PROP_FILE,
                dirTmp.resolve("settings.properties").toString());
        RaposzaSettings.reload();
        pane = new SettingsPane();

        assertNotNull(pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DAML));
        assertNotNull(pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DPM));
        assertNotNull(pane.fieldOf(RaposzaSettings.STR_KEY_DIR_SPLICE));
        assertEquals("", pane.fieldOf(RaposzaSettings.STR_KEY_DIR_SPLICE).getText());

        pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DAML).setText(dirTmp.resolve("daml").toString());
        pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DPM).setText(dirTmp.resolve("dpm").toString());
        pane.fieldOf(RaposzaSettings.STR_KEY_DIR_SPLICE)
                .setText(dirTmp.resolve("splice").toString());
        pane.saveIfValid();

        RaposzaSettings settings = RaposzaSettings.reload();
        assertEquals(dirTmp.resolve("daml"), settings.dirDaml());
        assertEquals(dirTmp.resolve("dpm"), settings.dirDpm());
        assertEquals(dirTmp.resolve("splice"), settings.dirSplice());

        pane.fieldOf(RaposzaSettings.STR_KEY_DIR_DPM).setText("");
        pane.saveIfValid();
        assertNull(RaposzaSettings.reload().dirDpm());
        assertEquals(dirTmp.resolve("daml"), RaposzaSettings.current().dirDaml());
    }
}
