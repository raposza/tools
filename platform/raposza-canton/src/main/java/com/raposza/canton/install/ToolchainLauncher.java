// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Turns a toolchain's NAME into something this JVM can actually spawn.
 *
 * <h2>A bare name does not run a `.cmd`</h2>
 *
 * On Windows both launchers are batch files - `%APPDATA%\daml\bin\daml.cmd` and
 * `%APPDATA%\dpm\bin\dpm.cmd`. `ProcessBuilder` appends `.exe` to a program
 * name that carries no extension and consults nothing else, so `dpm` fails with
 * CreateProcess error=2 whatever PATH holds. Measured: a fixture build refused
 * with the launcher's own directory on PATH, and ran only once a directory
 * holding a real `.exe` was put there instead.
 *
 * <h2>Three answers, in order</h2>
 *
 * The launcher {@link ToolchainRoots} found on PATH, which already carries its
 * extension; failing that the one under the root's `bin`, because a toolchain
 * that is installed and not on PATH is the ordinary state of a fresh machine;
 * failing both, the bare name - which is what ran until now, so nothing that
 * works today starts failing, and a caller's "has to be on PATH" message stays
 * true of the case it is printed for.
 *
 * <h2>Nothing here starts a process</h2>
 *
 * It answers with a string. The spawning is the caller's, so the rule can be
 * asserted for a Windows layout on a machine that is not one.
 *
 * Author Claude/bentzn
 */
public final class ToolchainLauncher {

    /** Most specific first, and no bare name: a name is the fallback, not a hit. */
    private static final List<String> LST_EXT_WINDOWS = List.of(".cmd", ".exe", ".bat");

    private static final List<String> LST_EXT_UNIX = List.of("");

    private ToolchainLauncher() {
    }


    /**
     * @param strName `daml` or `dpm`
     * @return what to put in argv[0] on THIS machine; never null for a name
     */
    public static String strLauncher(String strName) {
        boolean flagWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win");
        return strLauncher(strName, ToolchainRoots.ofDefaults(), flagWindows);
    }


    /**
     * Everything is an argument, so a Windows layout is answerable on a Linux
     * JVM and the other way round.
     *
     * @param strName `daml` or `dpm`; any other name is handed back unchanged
     * @param roots where the toolchains are on the machine being answered for
     * @param flagWindows whether a launcher carries an extension
     * @return what to put in argv[0]
     */
    public static String strLauncher(String strName, ToolchainRoots roots,
            boolean flagWindows) {
        if (strName == null || strName.isBlank() || roots == null)
            return strName;

        Path fileOnPath;
        Path dirRoot;
        if (ToolchainRoots.STR_NAME_DAML.equals(strName)) {
            fileOnPath = roots.fileDaml();
            dirRoot = roots.dirDaml();
        }
        else if (ToolchainRoots.STR_NAME_DPM.equals(strName)) {
            fileOnPath = roots.fileDpm();
            dirRoot = roots.dirDpm();
        }
        else {
            return strName;
        }

        if (fileOnPath != null)
            return fileOnPath.toString();

        Path fileUnderRoot = fileUnderRoot(dirRoot, strName, flagWindows);
        return fileUnderRoot != null ? fileUnderRoot.toString() : strName;
    }


    /**
     * @param dirRoot the toolchain root, or null
     * @param strName the launcher's base name
     * @param flagWindows which extensions to try
     * @return the launcher under `&lt;root&gt;/bin`, or null when there is none
     */
    private static Path fileUnderRoot(Path dirRoot, String strName, boolean flagWindows) {
        if (dirRoot == null)
            return null;

        List<String> lstExt = flagWindows ? LST_EXT_WINDOWS : LST_EXT_UNIX;
        for (String strExt : lstExt) {
            try {
                Path fileHere = dirRoot.resolve(ToolchainRoots.STR_DIR_BIN)
                        .resolve(strName + strExt);
                if (Files.isRegularFile(fileHere))
                    return fileHere;
            }
            catch (InvalidPathException ex) {
                return null;
            }
        }
        return null;
    }

}
