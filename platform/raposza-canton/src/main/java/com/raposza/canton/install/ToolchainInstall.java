// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/**
 * Acquiring a toolchain, end to end.
 *
 * <h2>DPM is the only channel the UNATTENDED offer uses</h2>
 *
 * An unattended install acquires DPM and the newest stable Canton it publishes,
 * and nothing else. The assistant's line is never what that offer proposes: it
 * is closed at 2.10, and a machine with nothing on it is better served by the
 * channel that is still publishing.
 *
 * <h2>An ASKED-FOR install goes through the toolchain that owns the version</h2>
 *
 * {@link #installSdkAssistant} runs `daml install` and {@link #installSdkDpm}
 * runs `dpm install`, so a version acquired from a window is installed by the
 * command a developer would have typed and lands in the root that command uses.
 * That is also what makes the result visible to the walks this module already
 * does. The tarball path - {@link #installSdk} - stays for the machine that has
 * no assistant yet, because there is nothing there to drive.
 *
 * <b>Newest, not chosen.</b> The registry publishes stable patches beside
 * candidates, snapshots and floating line tags; the offer takes the highest
 * stable patch and does not ask. A version picker is a separate feature for
 * someone who already has a toolchain.
 *
 * <h2>The default location, and no other</h2>
 *
 * Nothing here relocates a root. An install driven from the window has to be
 * indistinguishable from one the developer ran themselves - same directories,
 * same cache, same everything a later `dpm` call on the command line will see.
 * The environment CAN carry a root override, and this passes none.
 *
 * <h2>Acquiring a component needs a project, and the project is scratch</h2>
 *
 * `dpm add component` adds to a project, so a throwaway one is created for the
 * act. What lands permanently is in the DPM cache; the project holds a
 * reference and is not needed again.
 *
 * <h2>The seams are here so the flow can be asserted without a network</h2>
 *
 * Fetching and running are interfaces with production defaults. An install is
 * hundreds of megabytes over someone else's server; the ORDER of the steps, the
 * paths they use and the refusal to continue past a failure are the parts worth
 * holding still, and none of them should need a download to check.
 *
 * Author Claude/bentzn
 */
public final class ToolchainInstall {

    /** Where an archive is unpacked, under the caller's work directory. */
    public static final String STR_DIR_UNPACKED = "unpacked";

    /** The throwaway project a component is added through. */
    public static final String STR_DIR_PROJECT = "project";

    public static final String STR_CMD_INIT = "init";

    public static final String STR_CMD_ADD = "add";

    public static final String STR_CMD_COMPONENT = "component";

    public static final String STR_CMD_TAGS = "tags";

    public static final String STR_CMD_INSTALL = "install";

    public static final String STR_CMD_VERSION = "version";

    public static final String STR_ARG_ALL = "--all";

    public static final String STR_ARG_OUT = "-o";

    public static final String STR_ARG_JSON = "json";

    public static final long N_BYTES_MIB = 1024L * 1024L;

    private static final Logger log = LoggerFactory.getLogger(ToolchainInstall.class);


    /** Where bytes come from. */
    public interface Fetcher {

        /**
         * @param strUrl what to fetch
         * @param fileOut where to put it
         * @param progress told as bytes arrive, or null
         * @throws IOException on any failure, including a status that is not
         *         200
         */
        void fetch(String strUrl, Path fileOut, Download.Progress progress)
                throws IOException;


        /**
         * @param strUrl what to read
         * @return the body as text, trimmed
         * @throws IOException on any failure
         */
        String strFetch(String strUrl) throws IOException;
    }


    /** How an external program is run. */
    public interface Runner {

        /**
         * The sink is PER CALL rather than per runner, because one step's
         * output is read back - a tag listing is an answer, not just something
         * to show - while every step's output still reaches the window.
         *
         * @param lstCommand the command and its arguments
         * @param dirWorking the working directory, or null
         * @param lineOut told each output line, or null
         * @return the exit code
         * @throws IOException when it cannot be started
         */
        int nRun(List<String> lstCommand, Path dirWorking, Consumer<String> lineOut)
                throws IOException;
    }


    private final Fetcher fetcher;

    private final Runner runner;

    private final HostPlatform platform;

    private final ToolchainRoots roots;

    private final Consumer<String> lineOut;

    /**
     * Told the CURRENT figure of a fetch, repeatedly. Separate from
     * {@link #lineOut} because a pane shows the two differently - one
     * accumulates, the other is rewritten in place.
     */
    private final Consumer<String> lineProgress;


    /**
     * @param fetcher where bytes come from; never null
     * @param runner how programs are run; never null
     * @param platform the platform being installed on; never null
     * @param roots what is already present; never null
     * @param lineOut told what is happening, or null
     * @param lineProgress told how far a fetch has got, or null
     */
    public ToolchainInstall(Fetcher fetcher, Runner runner, HostPlatform platform,
            ToolchainRoots roots, Consumer<String> lineOut, Consumer<String> lineProgress) {
        if (fetcher == null || runner == null || platform == null || roots == null)
            throw new IllegalArgumentException("a fetcher, a runner, a platform and roots "
                    + "are required");
        this.fetcher = fetcher;
        this.runner = runner;
        this.platform = platform;
        this.roots = roots;
        this.lineOut = lineOut;
        this.lineProgress = lineProgress;
    }


    /**
     * @param platform the platform; never null
     * @param roots what is already present; never null
     * @param dirStage a directory an installer may expand into, or null
     * @param lineOut told what is happening, or null
     * @param lineProgress told how far a fetch has got, or null
     * @param onStart handed each live process, or null
     * @return an install that really fetches and really runs, into the default
     *         locations
     */
    public static ToolchainInstall ofDefaults(HostPlatform platform, ToolchainRoots roots,
            Path dirStage, Consumer<String> lineOut, Consumer<String> lineProgress,
            Consumer<Process> onStart) {
        Fetcher fetcherReal = new Fetcher() {

            @Override
            public void fetch(String strUrl, Path fileOut, Download.Progress progress)
                    throws IOException {
                Download.fetch(strUrl, fileOut, progress);
            }


            @Override
            public String strFetch(String strUrl) throws IOException {
                return Download.strFetch(strUrl);
            }
        };
        // NO ROOT OVERRIDE. The window's install must land where a hand-run
        // install lands, so the third argument stays null.
        // ONE SINK PER CALL. A step that reads its own output owns it; the
        // window sees what that step chooses to report. Merging the two put
        // the tag listing's several hundred lines into the pane to find one
        // version in them.
        Runner runnerReal = (lstCommand, dirWorking, lineStep) -> InstallRunner.nRun(lstCommand,
                dirWorking, platform, dirStage, null, lineStep, onStart);

        return new ToolchainInstall(fetcherReal, runnerReal, platform, roots,
                lineOut, lineProgress);
    }


    /**
     * The whole unattended acquisition: DPM if it is missing, then the newest
     * stable Canton the registry publishes.
     *
     * @param dirWork a directory this may write into; created if absent
     * @return the Canton version that was added
     * @throws IOException when any step fails, including an add that left no
     *         runtime jar on disk
     */
    public VersionId versionInstallLatest(Path dirWork) throws IOException {
        if (dirWork == null)
            throw new IllegalArgumentException("a work directory is required");

        Path fileDpm = roots.fileDpm();
        if (fileDpm == null)
            fileDpm = fileInstallDpm(dirWork);

        VersionId version = versionNewest(fileDpm, dirWork);
        say("adding Canton " + version);
        addComponent(fileDpm, dirWork, version);
        requireRuntime(version);
        say("Canton " + version + " installed");
        return version;
    }


    /**
     * dpm's exit code is not the evidence that a Canton arrived.
     *
     * MEASURED on a developer machine: a mirrored `canton-open-source` pull
     * that wrote `component.yaml`, `LICENSE`, `linked-daml-version` and an
     * EMPTY `lib/`, all in the same minute, and never its jar. A window that
     * reported that as "installed" hands the user a version the picker then
     * lists as `[no runtime jar]`. So success is read off the disk, through the
     * same discovery the picker uses: some installation of this version must
     * carry a runtime jar.
     *
     * @param version the version just added
     * @throws IOException when no installation of it carries a runtime jar
     */
    private void requireRuntime(VersionId version) throws IOException {
        Optional<CantonInstallation> optInst = new CantonInstallations(roots.dirDaml(),
                roots.dirDpm()).find(version, Edition.OPEN_SOURCE);
        if (optInst.isPresent() && optInst.get().hasRuntime())
            return;

        throw new IOException("dpm reported Canton " + version + " added, but no runtime jar "
                + "for it is under " + roots.dirDpm().resolve("cache").resolve("components")
                + " - the pull did not complete");
    }


    /**
     * Fetches the newest DPM, unpacks it and lets it install itself.
     *
     * @param dirWork a directory this may write into
     * @return the launcher inside the tree it installed from, which is what the
     *         steps after this one are run with - the one on PATH will not be
     *         found until the shell is new
     * @throws IOException when any step fails
     */
    public Path fileInstallDpm(Path dirWork) throws IOException {
        String strVersion = fetcher.strFetch(DpmBootstrap.STR_URL_LATEST);
        if (strVersion.isEmpty())
            throw new IOException("the latest-version endpoint answered nothing");

        say("installing DPM " + strVersion);
        Path dirUnpacked = dirUnpack(dirWork, DpmBootstrap.strArchive(strVersion, platform),
                DpmBootstrap.strUrl(strVersion, platform));

        nRunChecked(DpmBootstrap.lstCommand(dirUnpacked, platform), dirUnpacked, null,
                "the DPM bootstrap");
        say("DPM " + strVersion + " installed");
        return DpmBootstrap.fileLauncher(dirUnpacked, platform);
    }


    /**
     * Fetches an SDK and runs the installer inside it. NOT part of the
     * unattended offer - see the type comment.
     *
     * @param version the SDK version; must be one the catalogue publishes
     * @param dirWork a directory this may write into
     * @throws IOException when any step fails, including a platform this
     *         version publishes no asset for
     */
    public void installSdk(VersionId version, Path dirWork) throws IOException {
        if (version == null || dirWork == null)
            throw new IllegalArgumentException("a version and a work directory are required");

        String strUrl = SdkCatalogue.strUrl(version, platform);
        if (strUrl == null)
            throw new IOException("no SDK " + version + " is published for " + platform);

        say("Installing DAML SDK " + version);
        Path dirUnpacked = dirUnpack(dirWork, SdkCatalogue.strAsset(version, platform), strUrl);

        Path fileInstaller = dirUnpacked.resolve(SdkCatalogue.strInstaller(platform));
        if (!Files.isRegularFile(fileInstaller))
            throw new IOException("the SDK carries no " + fileInstaller.getFileName()
                    + " at " + dirUnpacked);

        nRunChecked(List.of(fileInstaller.toString()), dirUnpacked, null, "the SDK installer");
        say("DAML SDK " + version + " installed");
    }


    /**
     * Reads the SDK bundles DPM publishes, and which of them are installed.
     *
     * <b>NO BOOTSTRAP.</b> A machine with no DPM contributes no bundles, which
     * is a fact about it. Installing a toolchain to answer a listing is not
     * something to do behind a reader who asked to see a list.
     *
     * @param dirWork a directory to run in; created if absent
     * @return what the bundle catalogue says, or empty where there is no DPM
     * @throws IOException when the command fails, or prints no JSON array of
     *         bundles
     */
    public List<SdkOffer> lstOfferDpm(Path dirWork) throws IOException {
        if (dirWork == null)
            throw new IllegalArgumentException("a work directory is required");

        Path fileDpm = roots.fileDpm();
        if (fileDpm == null)
            return List.of();

        Files.createDirectories(dirWork);
        List<String> lstLine = new ArrayList<>();
        nRunChecked(List.of(fileDpm.toString(), STR_CMD_VERSION, STR_ARG_ALL,
                STR_ARG_OUT, STR_ARG_JSON), dirWork, lstLine::add, "the bundle listing");
        try {
            return DpmVersions.lstOffer(String.join("\n", lstLine));
        }
        catch (IllegalArgumentException ex) {
            throw new IOException("the bundle listing: " + ex.getMessage(), ex);
        }
    }


    /**
     * Installs one SDK through the toolchain the offer names.
     *
     * @param offer which SDK, and which channel installs it; never null
     * @param dirWork a directory this may write into
     * @throws IOException when any step fails
     */
    public void installOffer(SdkOffer offer, Path dirWork) throws IOException {
        if (offer == null)
            throw new IllegalArgumentException("an offer is required");

        if (offer.channel() == SdkChannel.DPM) {
            installSdkDpm(offer.version(), dirWork);
            return;
        }
        installSdkAssistant(offer.version(), dirWork);
    }


    /**
     * `daml install &lt;version&gt;`, which is what a developer would have run.
     *
     * THE ASSISTANT WHEN THERE IS ONE. It resolves the platform asset itself and
     * lands the version in its own root, so the result is indistinguishable from
     * a hand-run install. With no assistant on PATH there is nothing to drive,
     * and the tarball carries its own installer.
     *
     * @param version the SDK version
     * @param dirWork a directory this may write into
     * @throws IOException when any step fails
     */
    public void installSdkAssistant(VersionId version, Path dirWork) throws IOException {
        if (version == null || dirWork == null)
            throw new IllegalArgumentException("a version and a work directory are "
                    + "required");

        Path fileDaml = roots.fileDaml();
        if (fileDaml == null) {
            installSdk(version, dirWork);
            return;
        }

        Files.createDirectories(dirWork);
        say("Installing DAML SDK " + version + " with the assistant");
        nRunChecked(List.of(fileDaml.toString(), STR_CMD_INSTALL, version.toString()),
                dirWork, null, "daml install");
        say("DAML SDK " + version + " installed");
    }


    /**
     * `dpm install &lt;version&gt;`, with DPM acquired first where it is missing.
     *
     * <b>The argument is the BUNDLE version</b>, which is what the catalogue
     * lists and is not the Canton component version inside it.
     *
     * @param version the SDK bundle version
     * @param dirWork a directory this may write into
     * @throws IOException when any step fails
     */
    public void installSdkDpm(VersionId version, Path dirWork) throws IOException {
        if (version == null || dirWork == null)
            throw new IllegalArgumentException("a version and a work directory are "
                    + "required");

        Path fileDpm = roots.fileDpm();
        if (fileDpm == null)
            fileDpm = fileInstallDpm(dirWork);

        Files.createDirectories(dirWork);
        say("Installing DAML SDK " + version + " with dpm");
        // HIS LOG, 2026-09-23: dpm's own lines with a capital, and not its
        // closing "Successfully installed SDK" - the line after this says it.
        // THE FIGURE IS READ OFF THE DISK WHILE A COMPONENT PULLS - dpm says
        // nothing then - DpmPullWatch, D-833.
        AtomicReference<String> refPulling = new AtomicReference<>();
        Thread threadWatch = roots.dirDpm() == null || lineProgress == null ? null
                : DpmPullWatch.start(roots.dirDpm(), refPulling::get, lineProgress);
        try {
            nRunChecked(List.of(fileDpm.toString(), STR_CMD_INSTALL, version.toString()),
                    dirWork, strLine -> {
                        if (strLine.startsWith(STR_DPM_DONE))
                            return;
                        String strShown = strCapital(strLine);
                        refPulling.set(strShown.startsWith(STR_PULLING) ? strShown : null);
                        say(strShown);
                    }, "dpm install");
        }
        finally {
            if (threadWatch != null)
                threadWatch.interrupt();
        }
        say("DAML SDK " + version + " installed");
    }


    /**
     * @param fileDpm the launcher to ask
     * @param dirWork where to run it
     * @return the highest stable Canton the registry publishes
     */
    private VersionId versionNewest(Path fileDpm, Path dirWork) throws IOException {
        List<String> lstLine = new ArrayList<>();
        say("reading the published versions");
        nRunChecked(List.of(fileDpm.toString(), STR_CMD_TAGS,
                DpmCatalogue.strUri(DpmCatalogue.STR_COMPONENT_OPEN_SOURCE)),
                dirWork, lstLine::add, "the tag listing");

        List<VersionId> lstVersion = DpmCatalogue.lstStable(String.join("\n", lstLine));
        // AN EMPTY LISTING IS NOT A FACT ABOUT THE REGISTRY. The command
        // reports a wrong path and an empty repository identically, so nothing
        // here reads "no tags" as "no versions" - it fails instead.
        if (lstVersion.isEmpty())
            throw new IOException("the registry listed no stable version");

        // THE RELEASES, NOT THE LISTING. What the registry answers is mostly
        // snapshots, candidates and line tags - several hundred lines to say
        // what these twenty do.
        say("published: " + lstVersion.stream().map(VersionId::toString)
                .collect(Collectors.joining(" ")));
        return lstVersion.get(0);
    }


    /**
     * @param fileDpm the launcher to run
     * @param dirWork where the scratch project goes
     * @param version the version to pin
     */
    private void addComponent(Path fileDpm, Path dirWork, VersionId version) throws IOException {
        Path dirProject = dirWork.resolve(STR_DIR_PROJECT);
        Files.createDirectories(dirProject);

        nRunChecked(List.of(fileDpm.toString(), STR_CMD_INIT), dirProject, null,
                "the project init");
        nRunChecked(List.of(fileDpm.toString(), STR_CMD_ADD, STR_CMD_COMPONENT,
                DpmCatalogue.strUriAt(DpmCatalogue.STR_COMPONENT_OPEN_SOURCE, version)),
                dirProject, null, "adding the component");
    }


    /**
     * @param dirWork where to work
     * @param strArchive the archive's file name
     * @param strUrl where to fetch it
     * @return the directory its contents were unpacked into
     */
    private Path dirUnpack(Path dirWork, String strArchive, String strUrl) throws IOException {
        Files.createDirectories(dirWork);
        Path fileArchive = dirWork.resolve(strArchive);

        say("fetching " + strUrl);
        fetcher.fetch(strUrl, fileArchive, progressOf(strArchive));

        // AFTER THE FETCH, NOT INSTEAD OF CHECKING IT. A fetch that reported
        // success and wrote nothing would otherwise be reported as a broken
        // archive.
        if (!Files.isRegularFile(fileArchive))
            throw new IOException("the fetch of " + strUrl + " left no file at " + fileArchive);

        Path dirUnpacked = dirWork.resolve(STR_DIR_UNPACKED);
        Files.createDirectories(dirUnpacked);

        say("unpacking " + strArchive);
        nRunChecked(Extract.lstCommand(fileArchive, dirUnpacked), dirWork, null, "the unpack");
        return dirUnpacked;
    }


    /**
     * @param lstCommand what to run
     * @param dirWorking where to run it
     * @param lineStep reads the output itself, or null to let the window
     *        have it
     * @param strWhat what to call it in the failure
     */
    private void nRunChecked(List<String> lstCommand, Path dirWorking,
            Consumer<String> lineStep, String strWhat) throws IOException {
        int nExit = runner.nRun(lstCommand, dirWorking,
                lineStep != null ? lineStep : lineOut);
        if (nExit != 0)
            throw new IOException(strWhat + " exited " + nExit);
    }


    /**
     * @param strName what is being fetched
     * @param cntBytes how many bytes have arrived
     * @param cntTotal how many there are, or 0 when the server did not say
     * @return one line saying how far it has got
     */
    public static String strProgress(String strName, long cntBytes, long cntTotal) {
        if (cntTotal > 0)
            return strName + "  " + (cntBytes * 100 / cntTotal) + "% of " + strMib(cntTotal);

        // NO PERCENTAGE WITHOUT A TOTAL. A bar that cannot reach the end is
        // worse than a number that never claimed to.
        return strName + "  " + strMib(cntBytes);
    }


    private static String strMib(long cntBytes) {
        return String.format(Locale.ROOT, "%.1f MiB", cntBytes / (double) N_BYTES_MIB);
    }


    /**
     * @param strName what is being fetched
     * @return something to hand the fetcher, or null when nobody is listening
     */
    private Download.Progress progressOf(String strName) {
        if (lineProgress == null)
            return null;

        long[] nLastStep = { Long.MIN_VALUE };
        return (cntBytes, cntTotal) -> {
            // ONE UPDATE PER STEP. This is called once per chunk, which on a
            // fast link is thousands of times a second, and every one of them
            // would be a hop onto the event thread to say what the last one
            // said. A percent when the size is known, a megabyte when it is
            // not.
            long nStep = cntTotal > 0 ? cntBytes * 100 / cntTotal : cntBytes / N_BYTES_MIB;
            if (nStep == nLastStep[0])
                return;

            nLastStep[0] = nStep;
            lineProgress.accept(strProgress(strName, cntBytes, cntTotal));
        };
    }


    /** How dpm closes a successful install; the window's own line replaces it. */
    static final String STR_DPM_DONE = "Successfully installed";


    /** How dpm starts the line for each component it fetches. */
    static final String STR_PULLING = "Pulling ";


    /**
     * @param strLine a line
     * @return it with its first letter capital
     */
    static String strCapital(String strLine) {
        if (strLine == null || strLine.isEmpty())
            return strLine;
        return Character.toUpperCase(strLine.charAt(0)) + strLine.substring(1);
    }


    private void say(String strLine) {
        log.info(strLine);
        if (lineOut != null)
            lineOut.accept(strLine);
    }

}
