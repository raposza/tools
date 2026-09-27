// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import com.raposza.runtime.settings.RaposzaSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * <h2>A directory in the settings wins over all of it - todo.md A-45</h2>
 *
 * `dir.daml` and `dir.dpm` name an installation that already exists, a
 * developer's own or a corporate one; the operator's answer of 2026-09-26 is
 * "Setting wins". The order is the setting, then `DPM_HOME` for DPM, then the
 * launcher on PATH, then the platform default. With a root set, its own
 * `bin` is searched for the launcher before PATH, so {@link #flagDaml()} and
 * {@link #flagDpm()} answer from it.
 *
 * THE DPM SETTING MAY ALSO NAME THE LAUNCHER'S OWN DIRECTORY - his answer of
 * 2026-09-27, `todo.md` WS-7. A guest provisioned by hand keeps `dpm.exe` in
 * one place and the cache under `%APPDATA%\dpm` with no `bin`, and a single
 * root cannot name both. A directory holding the launcher itself decides the
 * launcher; the root is then found as though nothing were set.
 *
 * A SET ROOT THAT DOES NOT LOOK LIKE ONE IS USED AND SAID. It is not replaced
 * by the default - the setting wins - but the log names what it lacks, so a
 * mistyped directory reads as that rather than as an empty version list.
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
     * ONCE PER SETTING AND VALUE. `ofDefaults()` runs on every refresh of the
     * window, and a warning per call filled the console with one line
     * repeated - measured at his console, 2026-09-27.
     */
    private static final Set<String> SET_WARNED = ConcurrentHashMap.newKeySet();


    /**
     * @return the roots this JVM's environment and settings describe
     */
    public static ToolchainRoots ofDefaults() {
        String strOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        RaposzaSettings settings = RaposzaSettings.current();
        return of(System.getenv("PATH"), System.getenv(), strOs.contains("win"),
                Path.of(System.getProperty("user.home", ".")), settings.dirDaml(),
                settings.dirDpm());
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
        return of(strPath, mapEnv, flagWindows, dirHome, null, null);
    }


    /**
     * The same, with the two settings - see the type comment for the order.
     *
     * @param strPath the PATH to search, which may be null or empty
     * @param mapEnv the environment; never null
     * @param flagWindows whether the path separator, the launcher extensions
     *        and the default roots are the Windows ones
     * @param dirHome the user's home directory; never null
     * @param dirDamlSet `dir.daml`, or null when it is not set
     * @param dirDpmSet `dir.dpm` - a root or the launcher's own directory - or
     *        null when it is not set
     * @return the roots
     */
    public static ToolchainRoots of(String strPath, Map<String, String> mapEnv,
            boolean flagWindows, Path dirHome, Path dirDamlSet, Path dirDpmSet) {
        if (mapEnv == null || dirHome == null)
            throw new IllegalArgumentException("an environment and a home directory are required");

        // THE ASSISTANT: a set root, its own bin before PATH.
        Path fileDaml = null;
        Path dirDaml = null;
        if (dirDamlSet != null) {
            dirDaml = dirDamlSet;
            fileDaml = fileIn(dirDamlSet.resolve(STR_DIR_BIN), STR_NAME_DAML, flagWindows);
            warnUnlessRoot(dirDamlSet, STR_MARKER_DAML, RaposzaSettings.STR_KEY_DIR_DAML);
        }
        if (fileDaml == null)
            fileDaml = fileOnPath(strPath, STR_NAME_DAML, flagWindows);
        if (dirDaml == null)
            dirDaml = dirRootOf(fileDaml, STR_MARKER_DAML);
        if (dirDaml == null) {
            dirDaml = dirDefault(mapEnv, flagWindows, dirHome, STR_DIR_DAML_WINDOWS,
                    STR_DIR_DAML_UNIX);
        }

        // DPM: a directory holding the launcher itself names the launcher and
        // nothing else; any other set directory is the root.
        Path fileDpm = null;
        Path dirDpm = null;
        if (dirDpmSet != null) {
            fileDpm = fileIn(dirDpmSet, STR_NAME_DPM, flagWindows);
            if (fileDpm == null) {
                dirDpm = dirDpmSet;
                fileDpm = fileIn(dirDpmSet.resolve(STR_DIR_BIN), STR_NAME_DPM, flagWindows);
                warnUnlessRoot(dirDpmSet, STR_MARKER_DPM, RaposzaSettings.STR_KEY_DIR_DPM);
            }
        }
        if (fileDpm == null)
            fileDpm = fileOnPath(strPath, STR_NAME_DPM, flagWindows);

        // DPM_HOME OUTRANKS THE LAUNCHER. The launcher is where the program is;
        // the variable is where it was told to keep its state, and it wins for
        // the same run. A set root outranks both.
        if (dirDpm == null)
            dirDpm = dirOfEnv(mapEnv.get(STR_ENV_DPM_HOME));
        if (dirDpm == null)
            dirDpm = dirRootOf(fileDpm, STR_MARKER_DPM);
        if (dirDpm == null) {
            dirDpm = dirDefault(mapEnv, flagWindows, dirHome, STR_DIR_DPM_WINDOWS,
                    STR_DIR_DPM_UNIX);
        }

        return new ToolchainRoots(dirDaml, dirDpm, fileDaml, fileDpm);
    }


    /**
     * @return whether a Daml Assistant launcher was found, under a set root or
     *         on PATH
     */
    public boolean flagDaml() {
        return fileDaml != null;
    }


    /**
     * @return whether a DPM launcher was found, where the setting says or on
     *         PATH
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

        for (String strEntry : lstPathEntry(strPath, flagWindows)) {
            Path dirEntry;
            try {
                dirEntry = Path.of(strEntry);
            }
            catch (InvalidPathException ex) {
                log.debug("skipping unusable PATH entry {}: {}", strEntry, ex.toString());
                continue;
            }
            Path file = fileIn(dirEntry, strName, flagWindows);
            if (file != null)
                return file;
        }
        return null;
    }


    /**
     * @param dir a directory to look in
     * @param strName the launcher's base name
     * @param flagWindows which extensions to try
     * @return the launcher in it, unresolved, or null
     */
    private static Path fileIn(Path dir, String strName, boolean flagWindows) {
        List<String> lstExt = flagWindows ? LST_EXT_WINDOWS : LST_EXT_UNIX;
        for (String strExt : lstExt) {
            try {
                Path file = dir.resolve(strName + strExt);
                if (Files.isRegularFile(file))
                    return file;
            }
            catch (InvalidPathException ex) {
                log.debug("skipping unusable name in {}: {}", dir, ex.toString());
            }
        }
        return null;
    }


    /**
     * SAID, NOT REPLACED - see the type comment.
     *
     * @param dirSet the root a setting names
     * @param strMarker the directory a root of that kind carries
     * @param strKey the setting, for the log line
     */
    private static void warnUnlessRoot(Path dirSet, String strMarker, String strKey) {
        if (!Files.isDirectory(dirSet.resolve(strMarker))
                && SET_WARNED.add(strKey + "=" + dirSet)) {
            log.warn("{} = {} has no {} directory; it is used as set", strKey, dirSet,
                    strMarker);
        }
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
