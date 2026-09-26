// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.port.PortClass;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.sandbox.app.SandboxOptions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * What the window remembers FOR ONE CANTON VERSION.
 *
 * <h2>What is in here, and the one thing that is deliberately not</h2>
 *
 * Where the port block starts, where PostgreSQL listens, which run directory
 * the stack gets, how long a start may take, which directory the DARs come
 * from and which of them the next start uploads. All of them are things a
 * developer sets once for a version and expects to find again. The Scripts
 * tab's `script.*` keys, which a profile written before 2026-09-25 carries,
 * are read past and not written again - the tab is gone, D-850.
 *
 * <b>PQS is not one of them.</b> Whether the stack runs PQS is a decision about
 * THIS RUN - it roughly doubles what a start brings up and it is the thing most
 * often turned on to look at one query and off again. A remembered PQS would
 * mean a window that quietly starts a scribe because of something that was done
 * last week. It stays off until it is asked for, every time.
 *
 * <h2>The run directory defaults PER VERSION</h2>
 *
 * `&lt;run&gt;/dars` is uploaded at start, and a DAR is compiled against an LF
 * version. One run directory shared by a 2.x and a 3.x would offer the same
 * DARs to both, and the failure arrives as an upload rejection rather than as
 * "that DAR was for the other one". The cluster underneath is wiped at every
 * start anyway, so nothing is lost by keeping them apart.
 *
 * <h2>The DAR directory is NULLABLE, and null is not "none"</h2>
 *
 * Null means `&lt;run&gt;/dars`, which is where {@link RunDirectory} puts it and
 * where `publish.py --install` stages the pet shop fixture. It is not a default
 * COPIED into the profile at write time: a copied default stops following the
 * run directory the moment that field is edited, and a developer who moves the
 * run directory and finds the DAR list still pointing at the old one has been
 * given a setting they never set. An explicit path overrides it and is written;
 * a blank one reads back as null and therefore follows again.
 *
 * <h2>The selection is NULLABLE too, and null is not "empty"</h2>
 *
 * Null means EVERY DAR in the directory, which is what this application did
 * before there was a selection and what a profile written before this field
 * existed still asks for. An empty list means NONE, which a developer can only
 * reach by clearing the ticks - and it has to stay reachable, since "start with
 * no packages" is a state worth having.
 *
 * The names are FILE NAMES, not paths: the directory is beside them in the same
 * profile, and a path would say the same thing twice and disagree the first time
 * the directory was edited. A name that is no longer in the directory is dropped
 * when the tab reads it, not refused - a DAR deleted between two sessions is not
 * a reason to refuse to open a window.
 *
 * @param nPortFirst the lowest port the stack takes
 * @param nPortPostgres the embedded server's port
 * @param dirRun the run directory, which holds work/, data/ and dars/
 * @param nSecondsReady how long a start may take before it is called failed
 * @param dirDars where the DARs are, or null for `&lt;run&gt;/dars`
 * @param lstDarSelected the file names the next start uploads, or null for all
 *        of them
 * @param auth what the participant verifies on its Ledger API and what the
 *        JWT tab therefore mints against; never null once constructed
 *
 * Author Claude/bentzn
 */
public record SandboxProfile(int nPortFirst, int nPortPostgres, Path dirRun, int nSecondsReady,
        Path dirDars, List<String> lstDarSelected, AuthSettings auth) {

    public static final String STR_KEY_PORT_FIRST = "port.first";

    public static final String STR_KEY_PORT_POSTGRES = "port.postgres";

    public static final String STR_KEY_DIR_RUN = "dir.run";

    public static final String STR_KEY_TIMEOUT_READY = "timeout.ready.seconds";

    public static final String STR_KEY_DIR_DARS = "dir.dars";

    public static final String STR_KEY_DARS_SELECTED = "dars.selected";

    /** What separates the file names in one properties value. */
    public static final String STR_SEP_DARS = ",";

    /**
     * THE FIRST PORT OPENS THE NODE BLOCK, so it lies in 30xxx and is capped
     * where the block would leave that thousand - {@link PortClass}. Sandbox
     * Simple takes six of the block's ports and LocalNetND all of them; one cap
     * serves both because they share the block.
     */
    public static final int N_PORT_FIRST_MIN = PortClass.NODE.nLow();

    public static final int N_PORT_FIRST_MAX = LocalNetPorts.N_PORT_FIRST_MAX;

    /** PostgreSQL is an administrative port, a single one in 32xxx. */
    public static final int N_PORT_POSTGRES_MIN = PortClass.ADMIN.nLow();

    public static final int N_PORT_POSTGRES_MAX = PortClass.ADMIN.nHigh();

    public static final int N_SECONDS_READY_MIN = 10;

    public static final int N_SECONDS_READY_MAX = 3600;

    /**
     * The lowest port the stack takes. Canton's own block starts at
     * `SandboxPorts.N_DEFAULT_JSON_API` and the form's offset is the
     * difference, so the number a developer sees is a port rather than a
     * distance from one.
     */
    public static final int N_PORT_FIRST_DEFAULT = LocalNetPorts.N_PORT_FIRST_DEFAULT;

    /**
     * PostgreSQL keeps its own number and is NOT part of the block. Below
     * 32768, with the rest - see {@link RaposzaSettings} for why the
     * kernel's ephemeral range is out of bounds for a port this application
     * binds.
     */
    public static final int N_PORT_POSTGRES_DEFAULT = 32101;

    /** How long a start may take before it is called failed. */
    public static final int N_SECONDS_READY_DEFAULT = 300;


    public SandboxProfile {
        requirePort(nPortFirst, N_PORT_FIRST_MIN, N_PORT_FIRST_MAX, "first port");
        requirePort(nPortPostgres, N_PORT_POSTGRES_MIN, N_PORT_POSTGRES_MAX, "PostgreSQL port");
        if (dirRun == null)
            throw new IllegalArgumentException("a run directory is required");
        if (nSecondsReady < N_SECONDS_READY_MIN || nSecondsReady > N_SECONDS_READY_MAX) {
            throw new IllegalArgumentException("the ready timeout is outside "
                    + N_SECONDS_READY_MIN + "-" + N_SECONDS_READY_MAX + " s: " + nSecondsReady);
        }
        dirRun = dirRun.toAbsolutePath().normalize();
        if (dirDars != null)
            dirDars = dirDars.toAbsolutePath().normalize();
        if (lstDarSelected != null) {
            // COPIED and made unmodifiable. A record holding a list the caller
            // still has a reference to is a record whose value changes without
            // anybody writing to it.
            lstDarSelected = Collections.unmodifiableList(new ArrayList<>(lstDarSelected));
        }
        // NEVER NULL AFTER CONSTRUCTION. A profile written before the Sandbox
        // tab asked about authentication carries none, and every reader would
        // otherwise need the same null test.
        if (auth == null)
            auth = AuthSettings.ofDefaults();
    }


    /**
     * The shape this record had before the auth block, kept so that a caller
     * which has nothing to say about auth does not have to say `null`.
     *
     * @param nPortFirst the lowest port the stack takes
     * @param nPortPostgres the embedded server's port
     * @param dirRun the run directory
     * @param nSecondsReady how long a start may take
     * @param dirDars where the DARs are, or null
     * @param lstDarSelected the file names, or null for all of them
     */
    public SandboxProfile(int nPortFirst, int nPortPostgres, Path dirRun, int nSecondsReady,
            Path dirDars, List<String> lstDarSelected) {
        this(nPortFirst, nPortPostgres, dirRun, nSecondsReady, dirDars,
                lstDarSelected, null);
    }


    /**
     * @param version the Canton version; never null
     * @param edition its edition, or null for unknown
     * @return what a version with no profile file starts from
     */
    public static SandboxProfile ofDefaults(VersionId version, Edition edition) {
        RaposzaSettings settings = RaposzaSettings.current();
        return new SandboxProfile(settings.nPortFirst(), settings.nPortPostgres(),
                dirRunDefault(version, edition), settings.nSecondsReady(), null, null);
    }


    /**
     * @param version the Canton version; never null
     * @param edition its edition, or null for unknown
     * @return `~/.raposza/sandbox/&lt;version&gt;-&lt;edition&gt;`
     */
    public static Path dirRunDefault(VersionId version, Edition edition) {
        return SandboxOptions.dirWorkDefault().resolve(VersionKey.strOf(version, edition));
    }


    /**
     * Reads what is there and keeps the fallback for what is not.
     *
     * A profile file is hand-editable and outlives the code that wrote it, so a
     * key that is missing, empty or nonsense falls back to the SAME value the
     * version would have had with no file at all. One bad line must not cost
     * the other three settings, and must never stop the window opening.
     *
     * @param props what was on disk; may be null
     * @param profileFallback what each absent or unusable key becomes; never null
     * @return the profile
     */
    public static SandboxProfile ofProperties(Properties props, SandboxProfile profileFallback) {
        if (profileFallback == null)
            throw new IllegalArgumentException("a fallback profile is required");
        if (props == null)
            return profileFallback;

        return new SandboxProfile(
                nOf(props, STR_KEY_PORT_FIRST, profileFallback.nPortFirst(), N_PORT_FIRST_MIN,
                        N_PORT_FIRST_MAX),
                nOf(props, STR_KEY_PORT_POSTGRES, profileFallback.nPortPostgres(),
                        N_PORT_POSTGRES_MIN, N_PORT_POSTGRES_MAX),
                dirOf(props, STR_KEY_DIR_RUN, profileFallback.dirRun()),
                nOf(props, STR_KEY_TIMEOUT_READY, profileFallback.nSecondsReady(),
                        N_SECONDS_READY_MIN, N_SECONDS_READY_MAX),
                dirDarsOf(props, profileFallback.dirDars()),
                lstDarsOf(props, profileFallback.lstDarSelected()),
                AuthSettings.ofProperties(props, profileFallback.auth()));
    }


    /**
     * @return this profile as it is written to disk
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty(STR_KEY_PORT_FIRST, String.valueOf(nPortFirst));
        props.setProperty(STR_KEY_PORT_POSTGRES, String.valueOf(nPortPostgres));
        props.setProperty(STR_KEY_DIR_RUN, dirRun.toString());
        props.setProperty(STR_KEY_TIMEOUT_READY, String.valueOf(nSecondsReady));
        // WRITTEN ONLY WHEN SET. An absent key means `<run>/dars` and keeps
        // following it; a key holding today's value of that expression would
        // stop following the run directory the moment it was edited.
        if (dirDars != null)
            props.setProperty(STR_KEY_DIR_DARS, dirDars.toString());
        if (lstDarSelected != null)
            props.setProperty(STR_KEY_DARS_SELECTED, String.join(STR_SEP_DARS, lstDarSelected));
        auth.putInto(props);
        return props;
    }


    /**
     * @return where the DARs actually are: the explicit directory when there is
     *         one, and `&lt;run&gt;/dars` when there is not
     */
    public Path dirDarsEffective() {
        return dirDars != null ? dirDars : dirRun.resolve(RunDirectory.STR_DIR_DARS);
    }


    /**
     * @return whether the next start uploads every DAR in the directory, which
     *         is what a profile written before the selection existed asks for
     */
    public boolean flagAllDars() {
        return lstDarSelected == null;
    }


    /**
     * @param dirDarsNew the directory, or null to follow the run directory
     * @param lstDarSelectedNew the file names, or null for all of them
     * @return the same profile with the two DAR settings replaced
     */
    public SandboxProfile withDars(Path dirDarsNew, List<String> lstDarSelectedNew) {
        return new SandboxProfile(nPortFirst, nPortPostgres, dirRun, nSecondsReady,
                dirDarsNew, lstDarSelectedNew, auth);
    }


    /**
     * @param authNew what the participant verifies, or null for the defaults
     * @return the same profile with the auth settings replaced
     */
    public SandboxProfile withAuth(AuthSettings authNew) {
        return new SandboxProfile(nPortFirst, nPortPostgres, dirRun, nSecondsReady,
                dirDars, lstDarSelected, authNew);
    }


    private static void requirePort(int nPort, int nMin, int nMax, String strWhat) {
        if (nPort < nMin || nPort > nMax) {
            throw new IllegalArgumentException(strWhat + " is outside " + nMin + "-" + nMax
                    + ": " + nPort);
        }
    }


    private static int nOf(Properties props, String strKey, int nFallback, int nMin, int nMax) {
        String strValue = props.getProperty(strKey);
        if (strValue == null)
            return nFallback;

        int nValue;
        try {
            nValue = Integer.parseInt(strValue.trim());
        }
        catch (NumberFormatException ex) {
            return nFallback;
        }
        return nValue < nMin || nValue > nMax ? nFallback : nValue;
    }


    private static Path dirOf(Properties props, String strKey, Path dirFallback) {
        String strValue = props.getProperty(strKey);
        if (strValue == null || strValue.trim().isEmpty())
            return dirFallback;

        try {
            return Paths.get(strValue.trim());
        }
        catch (RuntimeException ex) {
            // An InvalidPathException on this platform. The other settings are
            // still readable and the window still opens.
            return dirFallback;
        }
    }


    /**
     * @param props what was on disk
     * @param dirFallback what an absent key becomes
     * @return the directory, or null to follow the run directory
     */
    private static Path dirDarsOf(Properties props, Path dirFallback) {
        String strValue = props.getProperty(STR_KEY_DIR_DARS);
        if (strValue == null)
            return dirFallback;
        // BLANK IS NULL, not the fallback. The key is only ever written when it
        // holds a path, so a blank one is a hand edit, and the thing a hand
        // edit that empties it is asking for is `<run>/dars` again.
        if (strValue.trim().isEmpty())
            return null;

        try {
            return Paths.get(strValue.trim());
        }
        catch (RuntimeException ex) {
            return dirFallback;
        }
    }


    /**
     * @param props what was on disk
     * @param lstFallback what an absent key becomes
     * @return the file names, or null for all of them
     */
    private static List<String> lstDarsOf(Properties props, List<String> lstFallback) {
        String strValue = props.getProperty(STR_KEY_DARS_SELECTED);
        if (strValue == null)
            return lstFallback;

        List<String> lstOut = new ArrayList<>();
        for (String strName : strValue.split(STR_SEP_DARS)) {
            String strTrim = strName.trim();
            // A separator with nothing between two of them is a hand edit or a
            // trailing comma, and neither names a file.
            if (!strTrim.isEmpty() && !lstOut.contains(strTrim))
                lstOut.add(strTrim);
        }
        // AN EMPTY VALUE IS AN EMPTY LIST, not the fallback. `dars.selected=`
        // is how `upload nothing` is written, and falling back there would make
        // that state unreachable from a file.
        return lstOut;
    }
}
