// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.port.PortClass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every setting that is NOT per version and edition, in one place, read by
 * every entry point.
 *
 * <h2>Why this is not a panel with a properties file behind it</h2>
 *
 * The window is not the only way into this application: `run-sandbox.sh`
 * headless runs in a JVM with no window in it at all. A settings pane that
 * only the window read would let a developer move the mint keys in the GUI
 * and have the next headless run write to the old place with no message
 * anywhere - the failure would be a directory that quietly stopped filling
 * up. So the settings live here, in
 * the module that knows about the machine and nothing about Canton, and the
 * constants that used to hold these values are now this class's DEFAULTS.
 *
 * <h2>ONE directory setting, and the rest is derived</h2>
 *
 * ONE state root - `~/.raposza`, or `%APPDATA%\raposza` on Windows - and
 * every directory the application uses hangs under it:
 * `sandbox`, `snapshots`, `profiles`, `fixtures/petshop`,
 * `jwtmint/keys`. Settings that could only ever be moved together are one
 * setting, and the layout beneath it is not a question anybody has to answer.
 *
 * THE SETTINGS FILE ITSELF DOES NOT MOVE. It is `settings.properties` in
 * the state root, always, because a file cannot say where it is. Point
 * `dir.home` somewhere else and the DATA moves; the one line that says so stays
 * where the application can find it without being told.
 *
 * Beyond that: two ports, the values a version gets when it has no profile
 * yet, and the one port LocalNetND takes from nowhere else - the first port of
 * its web UI block. NOT here: anything already keyed by version and edition, which is the
 * profile's job, and the CaQL audit log, which belongs to Workbench.
 *
 * <h2>Three directories this application does NOT own - todo.md A-45</h2>
 *
 * `dir.daml`, `dir.dpm` and `dir.splice` point at installations that already
 * exist - a developer's own, or a corporate install directory. BLANK MEANS
 * TODAY'S DEFAULT, so a file without the three keys behaves exactly as before
 * they existed, and a set one WINS over `DAML_HOME`, `DPM_HOME` and the
 * launcher on PATH - the operator's answer of 2026-09-26, "Setting wins". They
 * are held as null when unset, never as the default they stand for: the
 * default is decided where it is used, `ToolchainRoots` and
 * `SpliceInstallations`, from the environment of the JVM that uses it.
 *
 * <h2>Reading never throws, writing does</h2>
 *
 * The same rule the profile store follows. A settings file that cannot be read
 * gives the defaults, because the worst case is an application behaving as it
 * did before anyone edited anything; a save that silently did nothing would be
 * discovered a week later when the directory it named turned out to be empty.
 *
 * A key that is missing, blank or nonsense falls back to the SAME value it
 * would have had with no file at all, per key. One bad line must not cost the
 * other thirteen.
 *
 * ONE EXCEPTION WRITES ON A READ: a port that is a number outside its class is
 * rewritten in the file as the value it fell back to, once. That is a file
 * from before the port classes - `default.port.first=22010` - and without the
 * rewrite nothing ever replaced it: the Settings tab showed 30010, a save of
 * an unchanged tab writes nothing, and every JVM warned about the same line
 * at start, measured at his console 2026-09-23. A value that is not a number
 * is left as typed, because that is somebody's edit rather than an old
 * default. The rewrite cannot throw; a file it cannot write is warned about.
 *
 * <h2>Read at the point of use</h2>
 *
 * {@link #current()} is what callers use, and it re-reads nothing: the instance
 * is loaded once and held. {@link #store} replaces it in the same call that
 * writes the file, so a save is visible to everything in the JVM immediately
 * and to the next JVM through the file. Nothing caches a path derived from it.
 *
 * Author Claude/bentzn
 *
 * @param dirHome the one directory everything else hangs under
 * @param nPortMint the port the JWT mint binds
 * @param nPortDiscovery the port the discovery endpoint binds
 * @param strLine the Canton line taken when none was given
 * @param strLauncher the 3.x launcher taken when none was given
 * @param nPortFirst the first stack port a version gets before it has a profile
 * @param nPortPostgres the PostgreSQL port a version gets before it has one
 * @param nSecondsReady the ready timeout a version gets before it has one
 * @param flagOfferAviation whether a start may offer to build the Aviation fixture
 * @param flagOfferPharma the same for the Pharma fixture, which is a different
 *        question: the two are offered on different topologies and a developer
 *        who never wants one may well want the other
 * @param strUrlOidc an EXTERNAL OpenID Provider's base url, or "" to start one
 * @param nPortUiFirst the first port of LocalNetND's web UI block, in 31xxx -
 *        `todo.md` A-42. LocalNetND has no profile, so its node block takes
 *        {@link #nPortFirst} and its cluster {@link #nPortPostgres} directly;
 *        see {@link #portsLocalNet()}
 * @param dirDaml the Daml Assistant root to use, or null for the default
 * @param dirDpm the DPM root to use, or the directory its launcher sits in,
 *        or null for the default
 * @param dirSplice the directory the Splice bundles sit under, or null for
 *        `~/.splice`
 * @param nPortRawar the port the RAWAR server binds - one port in the 31xxx
 *        web UI class, mounts and not ports separating the RAWARs on it;
 *        `rawar.md` section 5
 */
public record RaposzaSettings(Path dirHome, int nPortMint, int nPortDiscovery, String strLine,
        String strLauncher, int nPortFirst, int nPortPostgres, int nSecondsReady,
        boolean flagOfferAviation, boolean flagOfferPharma, String strUrlOidc,
        int nPortUiFirst, Path dirDaml, Path dirDpm, Path dirSplice, int nPortRawar) {

    /** Points the whole application at another settings file. Tests use it. */
    public static final String STR_PROP_FILE = "raposza.settings";

    public static final String STR_FILE = "settings.properties";

    /** What the root is called where a dotted directory is the convention. */
    public static final String STR_DIR_HOME = ".raposza";

    /**
     * And what it is called on Windows, where it is NOT dotted - the same shape
     * the two vendor toolchains use, which put their roots at
     * `%APPDATA%\daml` and `%APPDATA%\dpm`.
     */
    public static final String STR_DIR_HOME_WINDOWS = "raposza";

    /** Where Windows keeps per-user application state. */
    public static final String STR_ENV_APPDATA = "APPDATA";

    public static final String STR_KEY_DIR_HOME = "dir.home";

    public static final String STR_KEY_PORT_MINT = "port.mint";

    /** An existing Daml Assistant root; blank for the default. */
    public static final String STR_KEY_DIR_DAML = "dir.daml";

    /** An existing DPM root, or its launcher's directory; blank for the default. */
    public static final String STR_KEY_DIR_DPM = "dir.dpm";

    /** Where existing Splice bundles sit, `<dir>/<version>/splice-node`; blank for `~/.splice`. */
    public static final String STR_KEY_DIR_SPLICE = "dir.splice";

    /**
     * An EXTERNAL OpenID Provider's base url, or empty to start one.
     *
     * EMPTY IS THE DEFAULT AND IT MEANS "START ONE". The provider is then a
     * child process of the window on the loopback address, which is what a
     * developer with nothing else gets. Naming a url here suppresses the child
     * entirely: nothing is started, the window reports the remote as up or
     * down, and the discovery document publishes the remote - the operator's
     * instruction of 2026-09-21.
     *
     * IT NEED NOT BE A RAPOSZA OIDC. What a stack requires of a provider is a
     * key set the participant can fetch, so any conforming OpenID Provider
     * serves. What a foreign one cannot serve is this application's own
     * minting surface - `/mint.txt`, and the extra parameters on the token
     * endpoint - so the controls that use it are disabled with a reason rather
     * than left to fail against a 404.
     */
    public static final String STR_KEY_URL_OIDC = "oidc.url";

    public static final String STR_KEY_PORT_DISCOVERY = "port.discovery";

    /**
     * The RAWAR server's port - `rawar.md` section 5. A WEB UI PORT, 31xxx,
     * because what it serves is pages a person opens; one port per Sandbox,
     * and the RAWARs on it are told apart by their mounts.
     */
    public static final String STR_KEY_PORT_RAWAR = "port.rawar";

    public static final String STR_KEY_LINE = "default.line";

    public static final String STR_KEY_LAUNCHER = "default.launcher";

    public static final String STR_KEY_PORT_FIRST = "default.port.first";

    public static final String STR_KEY_PORT_POSTGRES = "default.port.postgres";

    /** LocalNetND's web UI block - `LocalNetPorts`, and nothing else reads it. */
    public static final String STR_KEY_PORT_UI_FIRST = "default.port.ui.first";

    public static final String STR_KEY_SECONDS_READY = "default.timeout.ready.seconds";

    /**
     * Whether a start may offer to build the Aviation fixture.
     *
     * ONE ANSWER FOR THE MACHINE, not one per version. The question a developer
     * is answering is whether they want test data offered at all; asking it
     * again on the next Canton they select would be the same question wearing a
     * version number.
     */
    public static final String STR_KEY_OFFER_AVIATION = "fixture.aviation.offer";

    /**
     * Whether a window may offer to build the Pharma fixture.
     *
     * ONE ANSWER FOR THE MACHINE, as above - and a SEPARATE one from Aviation's.
     * `Never` to a single-participant fixture on the Sandbox says nothing about
     * a two-participant one on LocalNetND.
     */
    public static final String STR_KEY_OFFER_PHARMA = "fixture.pharma.offer";

    /**
     * 3.5 rather than the newest installed of anything: 3.4 and 2.x are
     * supported to different degrees, and a default that moved with what a
     * machine happens to have would make two developers' `sandbox` mean
     * different things. Moved here from `SandboxOptions` unchanged.
     */
    public static final String STR_LINE_DEFAULT = "3.5";

    /**
     * The subcommand, and it stays the default - the one every 2.x-shaped
     * assumption in this application was measured against.
     */
    public static final String STR_LAUNCHER_DEFAULT = "SUBCOMMAND";

    /**
     * EVERY DEFAULT PORT BELOW 32768, WHICH IS WHERE THE KERNEL'S EPHEMERAL
     * RANGE BEGINS. `net.ipv4.ip_local_port_range` is 32768-60999 on this
     * machine, so a port taken from inside it can already be held by a
     * transient client socket when a bind is attempted. That was measured
     * the expensive way, as intermittent `already in use: postgres (34xxx)`
     * failures on a machine under load.
     */
    public static final int N_PORT_MINT_DEFAULT = 32002;

    /** The discovery endpoint. Window-scoped and bound once. */
    public static final int N_PORT_DISCOVERY_DEFAULT = 32001;

    /** The node block's first port - `LocalNetPorts`, which both products share. */
    public static final int N_PORT_FIRST_DEFAULT = LocalNetPorts.N_PORT_FIRST_DEFAULT;

    public static final int N_PORT_POSTGRES_DEFAULT = 32101;

    /** The web UI block's first port - `LocalNetPorts`, 31000, 31010, 31020. */
    public static final int N_PORT_UI_FIRST_DEFAULT = LocalNetPorts.N_PORT_UI_FIRST_DEFAULT;

    /**
     * The RAWAR server's default port: in the web UI class and clear of
     * LocalNetND's UI block at its default, 31000 to 31020.
     */
    public static final int N_PORT_RAWAR_DEFAULT = 31100;

    public static final int N_SECONDS_READY_DEFAULT = 300;

    /**
     * ON by default.
     *
     * A ledger with nothing on it is what a new installation comes up as, and a
     * browser cannot be told from a broken one against it. The offer is one
     * dialog with a `Never` on it, so the cost of being wrong here is a click,
     * and the cost of the other default is a first impression of an empty tree.
     */
    public static final boolean FLAG_OFFER_AVIATION_DEFAULT = true;

    public static final boolean FLAG_OFFER_PHARMA_DEFAULT = true;

    /** No external provider named: the window starts one of its own. */
    public static final String STR_URL_OIDC_DEFAULT = "";

    private static final String STR_DIR_SANDBOX = "sandbox";

    private static final String STR_DIR_SNAPSHOTS = "snapshots";

    private static final String STR_DIR_PROFILES = "profiles";

    private static final String STR_DIR_FIXTURES = "fixtures/petshop";

    /**
     * Where a fixture store hangs, one directory per fixture.
     *
     * The pet shop predates this and keeps its own literal above, so nothing
     * that already reads {@link #dirFixtures()} moves.
     */
    private static final String STR_DIR_FIXTURE_ROOT = "fixtures";

    private static final String STR_DIR_MINT_KEYS = "jwtmint/keys";

    /** Where the RAWARs the Sandbox serves are developed, one directory each. */
    private static final String STR_DIR_RAWARS = "rawars";

    private static final Logger LOG = LoggerFactory.getLogger(RaposzaSettings.class);

    private static volatile RaposzaSettings settingsHeld;


    /**
     * The eight-component form, which takes the defaults for the last seven.
     *
     * IT EXISTS SO A COMPONENT COULD BE ADDED WITHOUT REWRITING EVERY CALLER.
     * A caller with no opinion about the fixture offer or about an external
     * provider should not have to state one.
     *
     * THERE IS DELIBERATELY NO NINE-, TEN- OR ELEVEN-COMPONENT FORM. One
     * existed for `flagOfferAviation` and a caller that used it would silently
     * reset `oidc.url` to empty - turning a configured external provider back
     * into a child process on the next settings write, with nothing on screen
     * saying so. The same holds for `nPortUiFirst`, added 2026-09-23: an
     * eleven-component form would put a moved web UI block back on 31000 at
     * the next unrelated write, and for the three installation directories,
     * added 2026-09-27: a shorter form would forget a corporate install on the
     * next save. The compiler is the only thing that reliably catches that,
     * so the overloads it would need are not written.
     *
     * @param dirHome the one directory everything else hangs under
     * @param nPortMint the port the JWT mint binds
     * @param nPortDiscovery the port the discovery endpoint binds
     * @param strLine the Canton line taken when none was given
     * @param strLauncher the 3.x launcher taken when none was given
     * @param nPortFirst the first stack port a version gets before it has a profile
     * @param nPortPostgres the PostgreSQL port a version gets before it has one
     * @param nSecondsReady the ready timeout a version gets before it has one
     */
    public RaposzaSettings(Path dirHome, int nPortMint, int nPortDiscovery, String strLine,
            String strLauncher, int nPortFirst, int nPortPostgres, int nSecondsReady) {
        this(dirHome, nPortMint, nPortDiscovery, strLine, strLauncher, nPortFirst,
                nPortPostgres, nSecondsReady, FLAG_OFFER_AVIATION_DEFAULT,
                FLAG_OFFER_PHARMA_DEFAULT, STR_URL_OIDC_DEFAULT, N_PORT_UI_FIRST_DEFAULT,
                null, null, null, N_PORT_RAWAR_DEFAULT);
    }


    public RaposzaSettings {
        dirHome = dirRequired(dirHome, STR_KEY_DIR_HOME);
        nPortMint = nPortRequired(nPortMint, STR_KEY_PORT_MINT);
        nPortDiscovery = nPortRequired(nPortDiscovery, STR_KEY_PORT_DISCOVERY);
        nPortFirst = nPortRequired(nPortFirst, STR_KEY_PORT_FIRST);
        nPortPostgres = nPortRequired(nPortPostgres, STR_KEY_PORT_POSTGRES);
        nPortUiFirst = nPortRequired(nPortUiFirst, STR_KEY_PORT_UI_FIRST);
        nPortRawar = nPortRequired(nPortRawar, STR_KEY_PORT_RAWAR);
        strLine = (strLine == null || strLine.isBlank()) ? STR_LINE_DEFAULT : strLine.trim();
        strLauncher = (strLauncher == null || strLauncher.isBlank()) ? STR_LAUNCHER_DEFAULT
                : strLauncher.trim().toUpperCase(Locale.ROOT);
        // NORMALISED HERE AND NOWHERE ELSE, so that a trailing slash cannot
        // make two spellings of one provider. Every url this application
        // composes appends a path to it.
        strUrlOidc = strUrlNormalised(strUrlOidc);
        dirDaml = dirOptional(dirDaml);
        dirDpm = dirOptional(dirDpm);
        dirSplice = dirOptional(dirSplice);
        if (nSecondsReady < 1)
            throw new IllegalArgumentException(STR_KEY_SECONDS_READY + " must be at least 1: "
                    + nSecondsReady);
    }


    /**
     * Where the settings file is - and it is NOT under {@link #dirHome()},
     * because a file cannot say where it is.
     *
     * @return the file, whether or not it exists
     */
    public static Path fileStore() {
        String strProp = System.getProperty(STR_PROP_FILE);
        if (strProp != null && !strProp.isBlank())
            return Path.of(strProp.trim()).toAbsolutePath().normalize();
        return dirHomeDefault().resolve(STR_FILE);
    }


    /**
     * <b>The root follows the platform.</b> A developer's Windows machine
     * already has the two vendor toolchains under `%APPDATA%`, and a dotted
     * directory in the profile root beside them is a Unix habit that nothing on
     * that platform looks in. Everywhere else the dotted form is the
     * convention and stays.
     *
     * This is the ONE place the root is composed. Nothing else in the
     * application builds it from the home directory - the rule itself is
     * separated below so it can be asserted for a platform this JVM is not
     * running on.
     *
     * @return the state root for this machine
     */
    public static Path dirHomeDefault() {
        String strOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return dirHomeDefault(strOs.contains("win"), System.getenv(STR_ENV_APPDATA),
                System.getProperty("user.home"));
    }


    /**
     * @param flagWindows whether the Windows layout applies
     * @param strAppData what `APPDATA` says, or null
     * @param strHome the user's home directory
     * @return the state root those three describe
     */
    public static Path dirHomeDefault(boolean flagWindows, String strAppData, String strHome) {
        Path dirHomeUser = Path.of(strHome == null || strHome.isBlank() ? "." : strHome);
        if (!flagWindows)
            return dirHomeUser.resolve(STR_DIR_HOME).toAbsolutePath().normalize();

        // A Windows machine without APPDATA is not one this has seen, and
        // answering null would put a NullPointerException between the operator
        // and every path the application uses.
        Path dirBase = strAppData == null || strAppData.isBlank()
                ? dirHomeUser.resolve("AppData").resolve("Roaming")
                : Path.of(strAppData);
        return dirBase.resolve(STR_DIR_HOME_WINDOWS).toAbsolutePath().normalize();
    }


    public static RaposzaSettings ofDefaults() {
        return new RaposzaSettings(dirHomeDefault(), N_PORT_MINT_DEFAULT,
                N_PORT_DISCOVERY_DEFAULT, STR_LINE_DEFAULT, STR_LAUNCHER_DEFAULT,
                N_PORT_FIRST_DEFAULT, N_PORT_POSTGRES_DEFAULT,
                N_SECONDS_READY_DEFAULT, FLAG_OFFER_AVIATION_DEFAULT,
                FLAG_OFFER_PHARMA_DEFAULT, STR_URL_OIDC_DEFAULT, N_PORT_UI_FIRST_DEFAULT,
                null, null, null, N_PORT_RAWAR_DEFAULT);
    }


    /**
     * Where the Sandbox's RAWARs are developed: one directory per RAWAR, each
     * served at its mount while the window is open - `rawar.md` section 5.
     *
     * UNDER THE HOME, NOT UNDER A RUN DIRECTORY. A run directory belongs to
     * one Canton version, and a page a developer is writing does not change
     * when the version under it does.
     *
     * @return the directory, whether or not it exists
     */
    public Path dirRawars() {
        return dirHome.resolve(STR_DIR_RAWARS);
    }


    /**
     * @return the root the per-version run directories hang under
     */
    public Path dirSandbox() {
        return dirHome.resolve(STR_DIR_SANDBOX);
    }


    /**
     * @return the root the per-version snapshot stores hang under
     */
    public Path dirSnapshots() {
        return dirHome.resolve(STR_DIR_SNAPSHOTS);
    }


    /**
     * @return where the per-version profile files are
     */
    public Path dirProfiles() {
        return dirHome.resolve(STR_DIR_PROFILES);
    }


    /**
     * @return the fixture project store
     */
    public Path dirFixtures() {
        return dirHome.resolve(STR_DIR_FIXTURES);
    }


    /**
     * One named fixture's store.
     *
     * THE NAME IS A PARAMETER. One fixture ships today - Aviation, in the
     * jar, reaching a machine that has only the application - and a second
     * one would be a second store under the same root rather than a second
     * tenant of this one, because a DAR is keyed by version and edition and
     * two packages under one key is an upload rejection.
     *
     * @param strName the fixture, e.g. `aviation`
     * @return `&lt;home&gt;/fixtures/&lt;name&gt;`
     */
    public Path dirFixture(String strName) {
        if (strName == null || strName.trim().isEmpty())
            throw new IllegalArgumentException("a fixture name is required");
        return dirHome.resolve(STR_DIR_FIXTURE_ROOT).resolve(strName.trim());
    }


    /**
     * @return where the JWT mint's JWKS lives
     */
    public Path dirMintKeys() {
        return dirHome.resolve(STR_DIR_MINT_KEYS);
    }


    /**
     * THE NUMBERING A LOCALNETND START BINDS, and the only place it is
     * composed - `todo.md` A-42.
     *
     * LocalNetND has no per-version profile, so the two numbers that are
     * Sandbox DEFAULTS are LocalNetND's VALUES: the node block opens at
     * {@link #nPortFirst} and the cluster listens on {@link #nPortPostgres}.
     * The web UI block is {@link #nPortUiFirst}, which only LocalNetND reads.
     * Until 2026-09-23 the window started on `LocalNetPorts.ofDefaults()`
     * whatever these said.
     *
     * NEVER THROWS: every one of the three is held to the class the numbering
     * requires by the constructor above, with the same caps.
     *
     * @return the numbering
     */
    public LocalNetPorts portsLocalNet() {
        return LocalNetPorts.ofFirst(nPortFirst, nPortUiFirst, nPortPostgres);
    }


    /**
     * @return whether an external OpenID Provider is named, in which case this
     *         application starts none of its own
     */
    public boolean flagOidcExternal() {
        return !strUrlOidc.isEmpty();
    }


    /**
     * The settings this JVM is running on. Loaded on first call and held.
     *
     * @return never null
     */
    public static RaposzaSettings current() {
        RaposzaSettings settingsNow = settingsHeld;
        if (settingsNow != null)
            return settingsNow;
        synchronized (RaposzaSettings.class) {
            if (settingsHeld == null)
                settingsHeld = read(fileStore());
            return settingsHeld;
        }
    }


    /**
     * Re-reads the file and installs what it finds.
     *
     * @return the settings now in force
     */
    public static RaposzaSettings reload() {
        synchronized (RaposzaSettings.class) {
            settingsHeld = read(fileStore());
            return settingsHeld;
        }
    }


    /**
     * Writes the settings and installs them, in that order.
     *
     * @param settingsNew what to write; never null
     * @throws IOException when the file cannot be written
     */
    public static void store(RaposzaSettings settingsNew) throws IOException {
        if (settingsNew == null)
            throw new IllegalArgumentException("settings are required");
        Path file = fileStore();
        Path dirParent = file.getParent();
        if (dirParent != null)
            Files.createDirectories(dirParent);
        Properties props = settingsNew.toProperties();
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Raposza settings - global, not per version and edition");
        }
        synchronized (RaposzaSettings.class) {
            settingsHeld = settingsNew;
        }
    }


    /**
     * A file that cannot be read gives the defaults, and so does a key that
     * cannot be parsed - per key, not per file.
     *
     * @param file where to read from; may be absent
     * @return never null
     */
    public static RaposzaSettings read(Path file) {
        RaposzaSettings settingsElse = ofDefaults();
        if (file == null || !Files.isRegularFile(file))
            return settingsElse;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        catch (IOException | IllegalArgumentException ex) {
            LOG.warn("settings could not be read, using defaults: {}: {}", file, ex.getMessage());
            return settingsElse;
        }
        RaposzaSettings settingsOut = ofProperties(props, settingsElse);
        repairPorts(file, props, settingsOut);
        return settingsOut;
    }


    /**
     * REWRITES A PORT OUTSIDE ITS CLASS AS THE VALUE IT FELL BACK TO - see the
     * type comment. Every other line of the file, keys this class does not
     * know included, is written back as it was read.
     *
     * @param file the file that was read
     * @param props what it held
     * @param settings what was made of it
     */
    private static void repairPorts(Path file, Properties props, RaposzaSettings settings) {
        Map<String, Integer> mapPort = new LinkedHashMap<>();
        mapPort.put(STR_KEY_PORT_MINT, settings.nPortMint());
        mapPort.put(STR_KEY_PORT_DISCOVERY, settings.nPortDiscovery());
        mapPort.put(STR_KEY_PORT_FIRST, settings.nPortFirst());
        mapPort.put(STR_KEY_PORT_POSTGRES, settings.nPortPostgres());
        mapPort.put(STR_KEY_PORT_UI_FIRST, settings.nPortUiFirst());
        mapPort.put(STR_KEY_PORT_RAWAR, settings.nPortRawar());

        List<String> lstMoved = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mapPort.entrySet()) {
            String strVal = props.getProperty(entry.getKey());
            if (strVal == null || strVal.isBlank())
                continue;
            int nVal;
            try {
                nVal = Integer.parseInt(strVal.trim());
            }
            catch (NumberFormatException ex) {
                continue;
            }
            if (nVal == entry.getValue().intValue())
                continue;
            props.setProperty(entry.getKey(), Integer.toString(entry.getValue()));
            lstMoved.add(entry.getKey() + " " + nVal + " -> " + entry.getValue());
        }
        if (lstMoved.isEmpty())
            return;

        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Raposza settings - global, not per version and edition");
            LOG.info("{} rewritten, ports outside their class: {}", file,
                    String.join(", ", lstMoved));
        }
        catch (IOException ex) {
            LOG.warn("{} could not be rewritten: {}", file, ex.getMessage());
        }
    }


    /**
     * @param props what was on disk; may be null
     * @param settingsElse what a missing or unusable key becomes; never null
     * @return never null
     */
    public static RaposzaSettings ofProperties(Properties props,
            RaposzaSettings settingsElse) {
        if (settingsElse == null)
            throw new IllegalArgumentException("a fallback is required");
        if (props == null)
            return settingsElse;
        return new RaposzaSettings(
                dirOf(props, STR_KEY_DIR_HOME, settingsElse.dirHome()),
                nPortOf(props, STR_KEY_PORT_MINT, settingsElse.nPortMint()),
                nPortOf(props, STR_KEY_PORT_DISCOVERY, settingsElse.nPortDiscovery()),
                strOf(props, STR_KEY_LINE, settingsElse.strLine()),
                strOf(props, STR_KEY_LAUNCHER, settingsElse.strLauncher()),
                nPortOf(props, STR_KEY_PORT_FIRST, settingsElse.nPortFirst()),
                nPortOf(props, STR_KEY_PORT_POSTGRES, settingsElse.nPortPostgres()),
                nSecondsOf(props, STR_KEY_SECONDS_READY, settingsElse.nSecondsReady()),
                flagOf(props, STR_KEY_OFFER_AVIATION, settingsElse.flagOfferAviation()),
                flagOf(props, STR_KEY_OFFER_PHARMA, settingsElse.flagOfferPharma()),
                strUrlOf(props, STR_KEY_URL_OIDC, settingsElse.strUrlOidc()),
                nPortOf(props, STR_KEY_PORT_UI_FIRST, settingsElse.nPortUiFirst()),
                dirOf(props, STR_KEY_DIR_DAML, settingsElse.dirDaml()),
                dirOf(props, STR_KEY_DIR_DPM, settingsElse.dirDpm()),
                dirOf(props, STR_KEY_DIR_SPLICE, settingsElse.dirSplice()),
                nPortOf(props, STR_KEY_PORT_RAWAR, settingsElse.nPortRawar()));
    }


    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty(STR_KEY_DIR_HOME, dirHome.toString());
        props.setProperty(STR_KEY_PORT_MINT, Integer.toString(nPortMint));
        props.setProperty(STR_KEY_PORT_DISCOVERY, Integer.toString(nPortDiscovery));
        props.setProperty(STR_KEY_LINE, strLine);
        props.setProperty(STR_KEY_LAUNCHER, strLauncher);
        props.setProperty(STR_KEY_PORT_FIRST, Integer.toString(nPortFirst));
        props.setProperty(STR_KEY_PORT_POSTGRES, Integer.toString(nPortPostgres));
        props.setProperty(STR_KEY_PORT_UI_FIRST, Integer.toString(nPortUiFirst));
        props.setProperty(STR_KEY_PORT_RAWAR, Integer.toString(nPortRawar));
        props.setProperty(STR_KEY_SECONDS_READY, Integer.toString(nSecondsReady));
        props.setProperty(STR_KEY_OFFER_AVIATION, Boolean.toString(flagOfferAviation));
        props.setProperty(STR_KEY_OFFER_PHARMA, Boolean.toString(flagOfferPharma));
        props.setProperty(STR_KEY_URL_OIDC, strUrlOidc);
        // WRITTEN BLANK WHEN UNSET, so the file shows the three keys a reader
        // can fill in, and blank reads back as the default.
        props.setProperty(STR_KEY_DIR_DAML, strOfDir(dirDaml));
        props.setProperty(STR_KEY_DIR_DPM, strOfDir(dirDpm));
        props.setProperty(STR_KEY_DIR_SPLICE, strOfDir(dirSplice));
        return props;
    }


    /**
     * @param dir an optional directory
     * @return it absolute and normalised, or null
     */
    private static Path dirOptional(Path dir) {
        return dir == null ? null : dir.toAbsolutePath().normalize();
    }


    private static String strOfDir(Path dir) {
        return dir == null ? "" : dir.toString();
    }


    private static Path dirRequired(Path dir, String strKey) {
        if (dir == null)
            throw new IllegalArgumentException(strKey + " is required");
        return dir.toAbsolutePath().normalize();
    }


    /**
     * EACH PORT IN ITS OWN CLASS - {@link PortClass}. The first port opens the
     * node block and is capped so the block stays inside 30xxx; the web UI
     * first port opens the UI block and is capped so it stays inside 31xxx;
     * the mint, discovery and PostgreSQL are administrative singletons in
     * 32xxx.
     */
    private static int nPortRequired(int nPort, String strKey) {
        if (STR_KEY_PORT_FIRST.equals(strKey))
            return PortClass.NODE.requireFirst(nPort, LocalNetPorts.N_SPAN_NODE, strKey);
        if (STR_KEY_PORT_UI_FIRST.equals(strKey))
            return PortClass.UI.requireFirst(nPort, LocalNetPorts.N_SPAN_UI, strKey);
        if (STR_KEY_PORT_RAWAR.equals(strKey))
            return PortClass.UI.require(nPort, strKey);
        return PortClass.ADMIN.require(nPort, strKey);
    }


    private static boolean isPortValid(int nPort, String strKey) {
        try {
            nPortRequired(nPort, strKey);
            return true;
        }
        catch (IllegalArgumentException ex) {
            return false;
        }
    }


    /**
     * ONLY `true` AND `false` ARE READ, and anything else is the fallback
     * rather than false. A key somebody has hand-edited to `yes` is a key with
     * an intention behind it, and reading it as an answer of `no` would turn a
     * typo into a silently different application.
     *
     * @param props what was on disk
     * @param strKey the key to read
     * @param flagElse what a missing or unreadable key becomes
     * @return the value, or the fallback
     */
    private static boolean flagOf(Properties props, String strKey, boolean flagElse) {
        String strVal = props.getProperty(strKey);
        if (strVal == null || strVal.isBlank())
            return flagElse;

        String strTrim = strVal.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(strTrim))
            return true;
        if ("false".equals(strTrim))
            return false;
        return flagElse;
    }


    private static Path dirOf(Properties props, String strKey, Path dirElse) {
        String strVal = props.getProperty(strKey);
        if (strVal == null || strVal.isBlank())
            return dirElse;
        try {
            return Path.of(strVal.trim()).toAbsolutePath().normalize();
        }
        catch (RuntimeException ex) {
            LOG.warn("{} is not a path, keeping {}: {}", strKey, dirElse, ex.getMessage());
            return dirElse;
        }
    }


    /**
     * A port outside its class keeps the fallback rather than refusing the
     * whole file. THE PER-KEY RULE IS THE POINT: a hand-edited settings file
     * with one bad port must not cost the other thirteen settings, and must
     * never stop the application starting. It is also what moves a file
     * written before the port classes: its 22010 is outside 30xxx and reads as
     * the default.
     */
    private static int nPortOf(Properties props, String strKey, int nElse) {
        int nVal = nOf(props, strKey, nElse);
        if (!isPortValid(nVal, strKey)) {
            LOG.warn("{} is outside its port class, keeping {}: {}", strKey, nElse, nVal);
            return nElse;
        }
        return nVal;
    }


    private static int nSecondsOf(Properties props, String strKey, int nElse) {
        int nVal = nOf(props, strKey, nElse);
        if (nVal < 1) {
            LOG.warn("{} must be at least 1, keeping {}: {}", strKey, nElse, nVal);
            return nElse;
        }
        return nVal;
    }


    private static int nOf(Properties props, String strKey, int nElse) {
        String strVal = props.getProperty(strKey);
        if (strVal == null || strVal.isBlank())
            return nElse;
        try {
            return Integer.parseInt(strVal.trim());
        }
        catch (NumberFormatException ex) {
            LOG.warn("{} is not a number, keeping {}: {}", strKey, nElse, strVal);
            return nElse;
        }
    }


    private static String strOf(Properties props, String strKey, String strElse) {
        String strVal = props.getProperty(strKey);
        return (strVal == null || strVal.isBlank()) ? strElse : strVal.trim();
    }


    /**
     * A url that does not parse keeps the fallback, per the per-key rule above.
     *
     * @param props what was on disk
     * @param strKey the key to read
     * @param strElse what a missing or unusable key becomes
     * @return the value, normalised, or the fallback
     */
    private static String strUrlOf(Properties props, String strKey, String strElse) {
        String strVal = props.getProperty(strKey);
        if (strVal == null || strVal.isBlank())
            return strElse;
        try {
            return strUrlNormalised(strVal);
        }
        catch (IllegalArgumentException ex) {
            LOG.warn("{} is not usable, keeping {}: {}", strKey, strElse, ex.getMessage());
            return strElse;
        }
    }


    /**
     * A base url with no trailing slash, or "" for none.
     *
     * PUBLIC AND STATIC so the settings form can reject a half-typed url
     * before it is written, by the same rule that reads one off disk.
     *
     * @param strUrl a base url, or null or blank for none
     * @return it, trimmed and without trailing slashes, or ""
     * @throws IllegalArgumentException when it is neither http nor https, or
     *         names no host
     */
    public static String strUrlNormalised(String strUrl) {
        if (strUrl == null || strUrl.isBlank())
            return "";

        String strTrim = strUrl.trim();
        while (strTrim.endsWith("/")) {
            strTrim = strTrim.substring(0, strTrim.length() - 1);
        }
        String strLower = strTrim.toLowerCase(Locale.ROOT);
        if (!strLower.startsWith("http://") && !strLower.startsWith("https://")) {
            throw new IllegalArgumentException(STR_KEY_URL_OIDC
                    + " must be an http or https url: " + strUrl);
        }
        // A SCHEME IS NOT AN ADDRESS. Without this, `https://` is accepted
        // here and arrives at the participant as a JWKS url that cannot
        // resolve, which reads as a start timeout rather than as a typo.
        if (strTrim.length() <= strLower.indexOf("://") + 3) {
            throw new IllegalArgumentException(STR_KEY_URL_OIDC + " names no host: "
                    + strUrl);
        }
        return strTrim;
    }
}
