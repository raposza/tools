// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.settings.RaposzaSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether this is the first time Raposza runs on this machine - R1, his
 * handover of 2026-10-30.
 *
 * <h2>What "first time" means</h2>
 *
 * His words, 2026-10-30: "If there is no settings file or if there is no
 * .raposza dir." It is read ONCE, in {@link SandboxGui#open}, before the debug
 * log opens - the log creates its directory under the Raposza home, and read
 * after it the second condition could never hold.
 *
 * <h2>It ends with a settings file</h2>
 *
 * Nothing else writes the file until a setting is changed, so without one
 * every start would be a first start. {@link #done} writes what is in force
 * when the file is absent and leaves an existing file alone. Settings runs the
 * check again on request, which is the reset he asked for.
 *
 * Author Claude/bentzn
 */
final class FirstStart {

    private static volatile boolean flagFirst;

    private static volatile String strWhy;


    private FirstStart() {
    }


    /**
     * Reads the machine. Call before anything creates the Raposza home.
     */
    static void detect() {
        Path fileSettings = RaposzaSettings.fileStore();
        Path dirHome = RaposzaSettings.current().dirHome();
        strWhy = strWhyFirst(fileSettings, dirHome);
        flagFirst = strWhy != null;
    }


    /**
     * @param fileSettings the settings file
     * @param dirHome the Raposza home directory
     * @return why this is a first start, or null when it is not
     */
    static String strWhyFirst(Path fileSettings, Path dirHome) {
        if (fileSettings == null || !Files.isRegularFile(fileSettings))
            return "there is no settings file (" + fileSettings + ")";
        if (dirHome == null || !Files.isDirectory(dirHome))
            return "there is no Raposza directory (" + dirHome + ")";
        return null;
    }


    /**
     * @return whether {@link #detect} found a first start that is not done yet
     */
    static boolean isFirst() {
        return flagFirst;
    }


    /**
     * @return why, for the console; null when it is not a first start
     */
    static String strWhy() {
        return strWhy;
    }


    /**
     * The check has been shown. Writes the settings file when there is none, so
     * the next start is not a first one.
     *
     * @return null, or why the file could not be written
     */
    static String strDone() {
        flagFirst = false;
        Path fileSettings = RaposzaSettings.fileStore();
        if (Files.isRegularFile(fileSettings))
            return null;
        try {
            RaposzaSettings.store(RaposzaSettings.current());
            return null;
        }
        catch (IOException | RuntimeException ex) {
            return "the settings file could not be written: " + Failures.strWhy(ex);
        }
    }
}
