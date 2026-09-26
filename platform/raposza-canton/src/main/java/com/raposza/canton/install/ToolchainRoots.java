// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Where the Daml Assistant and DPM keep their state on this machine, and
 * whether either of them is reachable at all.
 *
 * <h2>The root is read off the launcher on PATH, never composed from the home
 * directory</h2>
 *
 * Both toolchains install a launcher into `&lt;root&gt;/bin` and put that
 * directory on PATH, so the root is the parent of the directory the launcher
 * sits in. One rule, both platforms - which composing a path from the home
 * directory is not: the roots are `~/.daml` and `~/.dpm` on Linux and
 * `%APPDATA%\daml` and `%APPDATA%\dpm` on Windows, with no leading dot.
 * Hardcoding the Linux form finds nothing on Windows, before or after a
 * successful install, so the window reports the same thing either way and the
 * install cannot be told from the failure.
 *
 * <b>The launcher is NOT resolved through its symlink.</b> On Linux
 * `~/.daml/bin/daml` points into `~/.daml/sdk/&lt;version&gt;/daml/daml`, so
 * following the link answers a version directory rather than a root. On Windows
 * the launcher is a `.cmd` and there is no link to follow. The unresolved path
 * is the one that carries the answer.
 *
 * <h2>A root off PATH has to look like one</h2>
 *
 * It is accepted only when it carries the directory that makes it that root -
 * `sdk` for the assistant, `cache` for DPM. A launcher copied onto PATH
 * somewhere else answers its own grandparent, which is not a root at all, and
 * the platform default is a better answer than a confidently wrong one.
 *
 * <h2>`DPM_HOME` wins for DPM, and the assistant has no equivalent</h2>
 *
 * Measured on this machine: `DPM_HOME=&lt;dir&gt; dpm version` creates
 * `&lt;dir&gt;/cache` and reports the bundles under it rather than the ones in
 * the user's own root, so DPM's root is relocatable by environment. Nothing of
 * the kind has been established for the assistant - it exposes no such variable
 * and none is set here - so PATH and the platform default are the whole of its
 * answer.
 *
 * Author Claude/bentzn
 *
 * @param dirDaml the Daml Assistant root, whether or not it exists
 * @param dirDpm the DPM root, whether or not it exists
 * @param fileDaml the assistant launcher found on PATH, or null when there is
 *        none - which is what "the assistant is not installed" means here
 * @param fileDpm the DPM launcher found on PATH, or null
 */
public record ToolchainRoots(Path dirDaml, Path dirDpm, Path fileDaml, Path fileDpm) {

    /** The variable DPM reads its root from. */
    public static final String STR_ENV_DPM_HOME = "DPM_HOME";

    /** Where Windows keeps per-user application state. */
    public static final String STR_ENV_APPDATA = "APPDATA";

    public static final String STR_NAME_DAML = "daml";

    public static final String STR_NAME_DPM = "dpm";

    /** The directory a launcher sits in, under both roots. */
    public static final String STR_DIR_BIN = "bin";

    /** What makes the assistant root the assistant root. */
    public static final String STR_MARKER_DAML = "sdk";

    /** And DPM's, which holds `components` and `sdk` beneath it. */
    public static final String STR_MARKER_DPM = "cache";

    private static final String STR_DIR_DAML_UNIX = ".daml";

    private static final String STR_DIR_DPM_UNIX = ".dpm";

    private static final String STR_DIR_DAML_WINDOWS = "daml";

    private static final String STR_DIR_DPM_WINDOWS = "dpm";

    /**
     * The extensions a launcher can carry on Windows, most specific first. The
     * empty string is last so a bare name still answers.
     */
    private static final List<String> LST_EXT_WINDOWS = List.of(".cmd", ".exe", ".bat", "");

    private static final List<String> LST_EXT_UNIX = List.of("");

    private static final Logger log = LoggerFactory.getLogger(ToolchainRoots.class);


    /**
     * @return the roots this JVM's environment describes
     */
    public static ToolchainRoots ofDefaults() {
        String strOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return of(System.getenv("PATH"), System.getenv(), strOs.contains("win"),
                Path.of(System.getProperty("user.home", ".")));
    }


    /**
     * Everything is an argument so this is answerable against a fixture tree on
     * a machine that has neither toolchain, which is the case the Windows guest
     * tests.
     *
     * @param strPath the PATH to search, which may be null or empty
     * @param mapEnv the environment; never null
     * @param flagWindows whether the path separator, the launcher extensions
     *        and the default roots are the Windows ones
     * @param dirHome the user's home directory; never null
     * @return the roots
     */
    public static ToolchainRoots of(String strPath, Map<String, String> mapEnv,
            boolean flagWindows, Path dirHome) {
        if (mapEnv == null || dirHome == null)
            throw new IllegalArgumentException("an environment and a home directory are required");

        Path fileDaml = fileOnPath(strPath, STR_NAME_DAML, flagWindows);
        Path fileDpm = fileOnPath(strPath, STR_NAME_DPM, flagWindows);

        Path dirDamlDefault = dirDefault(mapEnv, flagWindows, dirHome,
                STR_DIR_DAML_WINDOWS, STR_DIR_DAML_UNIX);
        Path dirDpmDefault = dirDefault(mapEnv, flagWindows, dirHome,
                STR_DIR_DPM_WINDOWS, STR_DIR_DPM_UNIX);

        Path dirDaml = dirRootOf(fileDaml, STR_MARKER_DAML);
        if (dirDaml == null)
            dirDaml = dirDamlDefault;

        // DPM_HOME OUTRANKS THE LAUNCHER. The launcher is where the program is;
        // the variable is where it was told to keep its state, and it wins for
        // the same run.
        Path dirDpm = dirOfEnv(mapEnv.get(STR_ENV_DPM_HOME));
        if (dirDpm == null)
            dirDpm = dirRootOf(fileDpm, STR_MARKER_DPM);
        if (dirDpm == null)
            dirDpm = dirDpmDefault;

        return new ToolchainRoots(dirDaml, dirDpm, fileDaml, fileDpm);
    }


    /**
     * @return whether a Daml Assistant launcher is on PATH
     */
    public boolean flagDaml() {
        return fileDaml != null;
    }


    /**
     * @return whether a DPM launcher is on PATH
     */
    public boolean flagDpm() {
        return fileDpm != null;
    }


    /**
     * @param strPath the PATH to search
     * @param strName the launcher's base name
     * @param flagWindows which extensions to try
     * @return the first match, unresolved, or null
     */
    private static Path fileOnPath(String strPath, String strName, boolean flagWindows) {
        if (strPath == null || strPath.isBlank())
            return null;

        List<String> lstExt = flagWindows ? LST_EXT_WINDOWS : LST_EXT_UNIX;
        for (String strEntry : lstPathEntry(strPath, flagWindows)) {
            for (String strExt : lstExt) {
                try {
                    Path file = Path.of(strEntry).resolve(strName + strExt);
                    if (Files.isRegularFile(file))
                        return file;
                }
                catch (InvalidPathException ex) {
                    log.debug("skipping unusable PATH entry {}: {}", strEntry, ex.toString());
                }
            }
        }
        return null;
    }


    /**
     * The separator is taken from the platform argument rather than from
     * `File.pathSeparator`, so a Windows PATH can be answered on a Linux JVM
     * and the other way round.
     *
     * @param strPath the PATH to split
     * @param flagWindows whether entries are separated by a semicolon
     * @return the entries, blanks dropped
     */
    private static List<String> lstPathEntry(String strPath, boolean flagWindows) {
        List<String> lstOut = new ArrayList<>();
        String strSep = flagWindows ? ";" : ":";
        for (String strEntry : strPath.split(Pattern.quote(strSep), -1)) {
            if (!strEntry.isBlank())
                lstOut.add(strEntry.trim());
        }
        return lstOut;
    }


    /**
     * @param fileLauncher the launcher found on PATH, or null
     * @param strMarker the directory the resulting root must carry
     * @return the root, or null when there is no launcher, when it does not sit
     *         in a `bin`, or when what that would make the root does not look
     *         like one
     */
    private static Path dirRootOf(Path fileLauncher, String strMarker) {
        if (fileLauncher == null)
            return null;

        Path dirBin = fileLauncher.getParent();
        if (dirBin == null || !STR_DIR_BIN.equals(strNameOf(dirBin)))
            return null;

        Path dirRoot = dirBin.getParent();
        if (dirRoot == null || !Files.isDirectory(dirRoot.resolve(strMarker)))
            return null;

        return dirRoot;
    }


    /**
     * @param strDir an environment value naming a directory
     * @return it as a path, or null when it is absent, blank or unusable
     */
    private static Path dirOfEnv(String strDir) {
        if (strDir == null || strDir.isBlank())
            return null;

        try {
            return Path.of(strDir.trim());
        }
        catch (InvalidPathException ex) {
            log.warn("ignoring unusable {}: {}", STR_ENV_DPM_HOME, ex.toString());
            return null;
        }
    }


    /**
     * @param mapEnv the environment, read for `APPDATA`
     * @param flagWindows which layout applies
     * @param dirHome the user's home directory
     * @param strWindows the directory name under `%APPDATA%`
     * @param strUnix the dotted directory name under the home directory
     * @return where the root is when nothing on PATH says otherwise
     */
    private static Path dirDefault(Map<String, String> mapEnv, boolean flagWindows,
            Path dirHome, String strWindows, String strUnix) {
        if (!flagWindows)
            return dirHome.resolve(strUnix);

        Path dirAppData = dirOfEnv(mapEnv.get(STR_ENV_APPDATA));
        if (dirAppData == null)
            dirAppData = dirHome.resolve("AppData").resolve("Roaming");

        return dirAppData.resolve(strWindows);
    }


    /**
     * @param dir a directory
     * @return its own name, or the empty string for a root
     */
    private static String strNameOf(Path dir) {
        Path name = dir.getFileName();
        return name == null ? "" : name.toString();
    }

}
