// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.settings;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Both platforms, from one machine</h2>
 *
 * The Windows answer cannot be observed from a Linux build and the reverse is
 * as true, so the rule takes its platform as an argument and both are asserted
 * wherever this runs.
 *
 * Author Claude/bentzn
 */
class StateRootTest {

    private static final String STR_HOME = "/home/x";

    private static final String STR_APPDATA = "C:\\Users\\x\\AppData\\Roaming";


    @Test
    void theDottedFormIsUsedWhereItIsTheConvention() {
        Path dirRoot = RaposzaSettings.dirHomeDefault(false, null, STR_HOME);

        assertEquals(".raposza", dirRoot.getFileName().toString());
        assertTrue(dirRoot.toString().startsWith(STR_HOME), dirRoot.toString());
    }


    /**
     * NOT dotted on Windows, and under `%APPDATA%` - the same shape the two
     * vendor toolchains use, so all three roots sit together.
     */
    @Test
    void windowsUsesTheUndottedFormUnderAppData() {
        Path dirRoot = RaposzaSettings.dirHomeDefault(true, STR_APPDATA, STR_HOME);

        assertEquals("raposza", dirRoot.getFileName().toString());
        assertTrue(dirRoot.toString().contains("Roaming"), dirRoot.toString());
    }


    @Test
    void appDataIsIgnoredWhereItDoesNotApply() {
        assertEquals(RaposzaSettings.dirHomeDefault(false, null, STR_HOME),
                RaposzaSettings.dirHomeDefault(false, STR_APPDATA, STR_HOME));
    }


    /**
     * A Windows machine with no `APPDATA` is not one this has seen, and an
     * answer beats a null that would surface as a failure in whichever path
     * happened to be built first.
     */
    @Test
    void aMissingAppDataStillAnswers() {
        Path dirRoot = RaposzaSettings.dirHomeDefault(true, null, STR_HOME);

        assertEquals("raposza", dirRoot.getFileName().toString());
        assertTrue(dirRoot.toString().contains("Roaming"), dirRoot.toString());
    }


    /** The no-argument form is the one the application uses, and it answers. */
    @Test
    void theMachinesOwnRootIsAbsolute() {
        assertTrue(RaposzaSettings.dirHomeDefault().isAbsolute());
    }

}
