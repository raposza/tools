// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import com.raposza.runtime.settings.RaposzaSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the staged scribe binaries on this machine.
 *
 * The staging layout is
 *
 * <pre>
 * &lt;pqsRoot&gt;/&lt;version&gt;/scribe.jar    the binary
 * &lt;pqsRoot&gt;/&lt;version&gt;/VERSION.txt   its raw --version output
 * &lt;pqsRoot&gt;/&lt;version&gt;/SOURCE.txt    where it came from
 * &lt;pqsRoot&gt;/line/&lt;canton-line&gt;/     the canonical binary for that line
 * </pre>
 *
 * Callers resolve through the LINE ALIAS and never name a patch version, so
 * repointing a line - as happened when 3.4.1's interface-view defect moved the
 * 3.4 line to 3.4.3 - is a filesystem change rather than a code change.
 *
 * The DPM cache is WALKED, never composed. Canton sits at
 * components/&lt;name&gt;/&lt;version&gt;/ but scribe inserts a generation
 * segment - components/scribe/daml3.4/3.4.1/ - so a path template built for
 * one misses the other entirely. And the jar's NAME is not a contract either:
 * DPM delivered `scribe/3.5.8/scribe.jar` and `scribe/3.6.2/pqs.jar` to one
 * Windows guest, 2026-10-24.
 *
 * <h2>{@link #resolve} - what a stack runs, in order</h2>
 *
 * <ol>
 * <li>the staged line alias under the PQS root, exactly as before;</li>
 * <li>the DPM cache - the DPM root {@link ToolchainRoots} finds, the same one
 *     Canton is found in, so `%APPDATA%\dpm` on Windows - newest version of the
 *     line first;</li>
 * <li>the jar `file.pqs` names in the settings, by its full path, whatever it
 *     is called. Its own banner says which line it belongs to, and a jar for
 *     another line is a MISMATCH, reported and not run.</li>
 * </ol>
 *
 * Nothing found is an answer with a reason, {@link PqsResolution}, so the
 * caller can tell the person why the mock runs instead.
 *
 * Absence is a normal answer here. A caller that finds none should skip rather
 * than fail.
 *
 * The Canton line is read from SOURCE.txt, not derived from the version. It is
 * derivable on the 3.x generation and not on the 2.x one, where scribe is
 * versioned 0.x and v0.5.5 pairs with Canton 2.10.
 *
 * Author Claude/bentzn
 */
public final class PqsInstallations {

    private static final Logger log = LoggerFactory.getLogger(PqsInstallations.class);

    private static final String STR_JAR = "scribe.jar";

    private static final String STR_VERSION_FILE = "VERSION.txt";

    private static final String STR_SOURCE_FILE = "SOURCE.txt";

    private static final String STR_DIR_LINE = "line";

    private static final int N_CACHE_SEARCH_DEPTH = 6;

    private static final String STR_EXT_JAR = ".jar";

    /** How long `java -jar &lt;jar&gt; --version` may take before it is given up on. */
    private static final long N_SECONDS_BANNER = 30L;

    /**
     * One banner per jar per JVM, keyed on path, size and modification time,
     * so a start does not pay a JVM launch for a jar it has already read and a
     * replaced jar is read again.
     */
    private static final Map<String, Optional<String>> MAP_BANNER = new ConcurrentHashMap<>();

    private static final Pattern PAT_SOURCE_MOCK =
            Pattern.compile("(?mi)^\\s*source\\s*:\\s*mock");

    private static final Pattern PAT_LICENSED_NO =
            Pattern.compile("(?mi)^\\s*licensed\\s*:\\s*no\\b");

    private static final Pattern PAT_CANTON_LINE =
            Pattern.compile("(?mi)^\\s*canton\\s+line\\s*:\\s*(\\S+)\\s*$");

    private final Path dirPqsRoot;
    private final Path dirDpmRoot;
    private final Path filePqsSet;
    private final Function<Path, String> fnBanner;


    /**
     * @param dirPqsRoot the staging root, normally ~/.pqs
     * @param dirDpmRoot the DPM root, normally ~/.dpm
     */
    public PqsInstallations(Path dirPqsRoot, Path dirDpmRoot) {
        this(dirPqsRoot, dirDpmRoot, null, PqsInstallations::strBannerOf);
    }


    /**
     * @param dirPqsRoot the staging root, normally ~/.pqs
     * @param dirDpmRoot the DPM root
     * @param filePqsSet the jar the settings name, or null for none
     * @param fnBanner what a jar prints for `--version`, or null when it
     *        printed nothing usable; an argument so the order can be asserted
     *        without a scribe on the machine
     */
    public PqsInstallations(Path dirPqsRoot, Path dirDpmRoot, Path filePqsSet,
            Function<Path, String> fnBanner) {
        if (dirPqsRoot == null || dirDpmRoot == null)
            throw new IllegalArgumentException("both roots are required");
        if (fnBanner == null)
            throw new IllegalArgumentException("a banner reader is required");
        this.dirPqsRoot = dirPqsRoot;
        this.dirDpmRoot = dirDpmRoot;
        this.filePqsSet = filePqsSet;
        this.fnBanner = fnBanner;
    }


    /**
     * THE DPM ROOT IS {@link ToolchainRoots}'s, not `~/.dpm`. That literal
     * found nothing on Windows, where DPM keeps its cache under
     * `%APPDATA%\dpm`, so every PQS start there ran the mock - raposza/tools#4.
     *
     * @return the installations this machine and its settings describe
     */
    public static PqsInstallations ofDefaults() {
        Path dirHome = Path.of(System.getProperty("user.home"));
        return new PqsInstallations(dirHome.resolve(".pqs"),
                ToolchainRoots.ofDefaults().dirDpm(), RaposzaSettings.current().filePqs(),
                PqsInstallations::strBannerOf);
    }


    /**
     * What a stack starting this Canton line runs - the type comment has the
     * order.
     *
     * @param strCantonLine a Canton minor line such as "3.5"
     * @return the scribe to run, or why there is none
     */
    public PqsResolution resolve(String strCantonLine) {
        if (strCantonLine == null || strCantonLine.isBlank())
            return PqsResolution.none("no Canton line was given to resolve PQS for");

        String strLine = strCantonLine.trim();
        Optional<PqsInstallation> optStaged = forCantonLine(strLine);
        if (optStaged.isPresent())
            return PqsResolution.of(optStaged.get());

        Optional<PqsInstallation> optDpm = fromDpmCache(strLine);
        if (optDpm.isPresent())
            return PqsResolution.of(optDpm.get());

        String strNoDpm = "there is no scribe for Canton " + strLine + " in " + dirDpmScribe();
        if (filePqsSet == null)
            return PqsResolution.none(strNoDpm + ", and no PQS scribe.jar is set in Settings");
        return fromSetting(strLine, strNoDpm);
    }


    /**
     * The newest DPM-cached scribe of the line. The version is the directory
     * the jar sits in - DPM's component version - and the banner, when the jar
     * prints one, is the check on it: a jar whose banner names another line is
     * passed over.
     */
    private Optional<PqsInstallation> fromDpmCache(String strLine) {
        List<PqsInstallation> lstFound = new ArrayList<>();
        for (Path fileJar : discoverDpmCacheJars()) {
            Optional<VersionId> optVersion = versionOfDir(fileJar);
            if (optVersion.isEmpty() || !optVersion.get().isLine(strLine))
                continue;
            lstFound.add(new PqsInstallation(optVersion.get(), strLine, fileJar, null, null,
                    PqsSource.DPM));
        }
        Collections.sort(lstFound);

        for (PqsInstallation inst : lstFound) {
            ScribeBanner banner = bannerOf(inst.fileJar());
            if (banner == null)
                return Optional.of(inst);
            if (!banner.version().isLine(strLine)) {
                log.warn("{} sits under version {} and reports scribe {}; passed over",
                        inst.fileJar(), inst.version(), banner.version());
                continue;
            }
            return Optional.of(new PqsInstallation(banner.version(), strLine, inst.fileJar(),
                    banner.strSchemaRevision(), banner.strDamlSdkVersion(), PqsSource.DPM));
        }
        return Optional.empty();
    }


    /**
     * The jar the settings name, accepted only for the line it belongs to.
     *
     * Its line comes from its own banner. A 3.x scribe's major.minor IS the
     * Canton line; a 2.x scribe is versioned 0.x - v0.5.5 pairs with Canton
     * 2.10 - so all its banner can say is that it belongs to the 2.x
     * generation, and that is what it is checked against.
     */
    private PqsResolution fromSetting(String strLine, String strNoDpm) {
        String strSet = "the PQS scribe.jar set in Settings, " + filePqsSet;
        if (!Files.isRegularFile(filePqsSet))
            return PqsResolution.none(strNoDpm + ", and " + strSet + ", is not a file");

        ScribeBanner banner = bannerOf(filePqsSet);
        if (banner == null) {
            return PqsResolution.none(strNoDpm + ", and " + strSet
                    + ", did not report a scribe version for java -jar --version");
        }

        VersionId version = banner.version();
        boolean flagJar2x = version.major() == 0;
        boolean flagMatch = flagJar2x ? PqsInstallation.isCanton2xLine(strLine)
                : version.isLine(strLine);
        if (!flagMatch) {
            String strJarLine = flagJar2x ? "2.x" : banner.cantonLine();
            return PqsResolution.none("PQS mismatched: " + strSet + ", is scribe " + version
                    + ", for Canton " + strJarLine + ", and this stack runs Canton " + strLine);
        }
        return PqsResolution.of(new PqsInstallation(version, strLine, filePqsSet,
                banner.strSchemaRevision(), banner.strDamlSdkVersion(), PqsSource.MANUAL));
    }


    private Path dirDpmScribe() {
        return dirDpmRoot.resolve("cache").resolve("components").resolve("scribe");
    }


    private static Optional<VersionId> versionOfDir(Path fileJar) {
        Path dirParent = fileJar.getParent();
        if (dirParent == null || dirParent.getFileName() == null)
            return Optional.empty();
        return VersionId.tryParse(dirParent.getFileName().toString());
    }


    /**
     * @return the banner the jar printed, parsed, or null when it printed none
     *         that parses
     */
    private ScribeBanner bannerOf(Path fileJar) {
        String strText = fnBanner.apply(fileJar);
        if (strText == null)
            return null;
        try {
            return ScribeBanner.parse(strText);
        }
        catch (InstallException ex) {
            log.warn("{} printed no scribe banner: {}", fileJar, ex.getMessage());
            return null;
        }
    }


    /**
     * `java -jar &lt;jar&gt; --version`, with the `java` on PATH - the same one
     * {@link com.raposza.canton.pqs.ScribeProcess} starts scribe with - and
     * standard error folded in.
     *
     * @param fileJar the jar to ask
     * @return what it printed, or null when it could not be run or did not
     *         finish in time
     */
    static String strBannerOf(Path fileJar) {
        String strKey;
        try {
            strKey = fileJar.toAbsolutePath() + "|" + Files.size(fileJar) + "|"
                    + Files.getLastModifiedTime(fileJar).toMillis();
        }
        catch (IOException ex) {
            log.warn("could not read {}: {}", fileJar, ex.toString());
            return null;
        }
        return MAP_BANNER.computeIfAbsent(strKey, strIgnored -> runVersion(fileJar)).orElse(null);
    }


    /**
     * THE OUTPUT GOES TO A FILE, not a pipe. Reading a pipe to its end blocks
     * for as long as the process lives, so a jar that never exits would hold
     * the start with it and the timeout would never be reached.
     */
    private static Optional<String> runVersion(Path fileJar) {
        Path fileOut = null;
        Process proc = null;
        try {
            fileOut = Files.createTempFile("raposza-scribe-version-", ".txt");
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", fileJar.toString(),
                    "--version");
            pb.redirectErrorStream(true);
            pb.redirectOutput(fileOut.toFile());
            proc = pb.start();
            // Nothing to say to it, and an open stdin is one more way to wait.
            proc.getOutputStream().close();
            if (!proc.waitFor(N_SECONDS_BANNER, TimeUnit.SECONDS)) {
                log.warn("{} --version did not finish in {} s", fileJar, N_SECONDS_BANNER);
                return Optional.empty();
            }
            return Optional.of(Files.readString(fileOut, StandardCharsets.UTF_8));
        }
        catch (IOException | UncheckedIOException ex) {
            log.warn("could not run java -jar {} --version: {}", fileJar, ex.toString());
            return Optional.empty();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        finally {
            if (proc != null && proc.isAlive())
                proc.destroyForcibly();
            deleteQuietly(fileOut);
        }
    }


    private static void deleteQuietly(Path file) {
        if (file == null)
            return;
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException ex) {
            log.debug("could not delete {}: {}", file, ex.toString());
        }
    }


    /**
     * @return every staged binary, newest first; empty when nothing is staged
     */
    public List<PqsInstallation> discover() {
        List<PqsInstallation> lstFound = new ArrayList<>();
        for (Path dirVersion : listDirectories(dirPqsRoot)) {
            Path fileJar = dirVersion.resolve(STR_JAR);
            if (!Files.isRegularFile(fileJar))
                continue;

            Optional<PqsInstallation> optInst = read(dirVersion, fileJar);
            optInst.ifPresent(lstFound::add);
        }
        Collections.sort(lstFound);
        return lstFound;
    }


    /**
     * The supported resolution path for harness code.
     *
     * @param strCantonLine a Canton minor line such as "3.4"
     * @return the jar the line alias points at, or empty when the line has no
     *         PQS staged
     */
    public Optional<Path> resolveLine(String strCantonLine) {
        if (strCantonLine == null || strCantonLine.isBlank())
            return Optional.empty();

        Path fileJar = dirPqsRoot.resolve(STR_DIR_LINE).resolve(strCantonLine).resolve(STR_JAR);
        return Files.isRegularFile(fileJar) ? Optional.of(fileJar) : Optional.empty();
    }


    /**
     * @param strCantonLine a Canton minor line such as "3.5"
     * @return the staged binary the line alias resolves to, with its banner
     *         facts, or empty when the line has no PQS
     */
    public Optional<PqsInstallation> forCantonLine(String strCantonLine) {
        Optional<Path> optJar = resolveLine(strCantonLine);
        if (optJar.isEmpty())
            return Optional.empty();

        Path fileJar = optJar.get();
        Optional<PqsInstallation> optInst = read(fileJar.getParent(), fileJar);
        if (optInst.isPresent())
            return optInst;

        // The alias resolved but carried no VERSION.txt of its own. Match the
        // jar back to a staged version rather than reporting nothing, because
        // an alias is normally a link into one of those directories.
        Path fileReal = toRealPath(fileJar);
        for (PqsInstallation inst : discover()) {
            if (toRealPath(inst.fileJar()).equals(fileReal))
                return Optional.of(inst);
        }
        return Optional.empty();
    }


    /**
     * Every jar in the DPM scribe cache that sits in a version directory,
     * found by walking it. The name is not checked - `scribe.jar` and
     * `pqs.jar` both occur - the directory is: a jar anywhere else is not a
     * component DPM installed.
     *
     * @return the jars found, in no guaranteed order
     */
    public List<Path> discoverDpmCacheJars() {
        List<Path> lstJar = new ArrayList<>();
        Path dirScribe = dirDpmScribe();
        if (!Files.isDirectory(dirScribe))
            return lstJar;

        try (Stream<Path> strmPaths = Files.walk(dirScribe, N_CACHE_SEARCH_DEPTH)) {
            Iterator<Path> itPath = strmPaths.iterator();
            while (itPath.hasNext()) {
                Path file = itPath.next();
                if (!Files.isRegularFile(file))
                    continue;
                String strName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (strName.endsWith(STR_EXT_JAR) && versionOfDir(file).isPresent())
                    lstJar.add(file);
            }
        }
        catch (IOException | UncheckedIOException ex) {
            log.warn("could not walk the DPM scribe cache {}: {}", dirScribe, ex.toString());
        }
        return lstJar;
    }


    /**
     * Reads one staged directory. The version comes from VERSION.txt, which is
     * the banner the binary printed; the directory name is a fallback only,
     * and one that yields no schema revision.
     */
    private Optional<PqsInstallation> read(Path dirVersion, Path fileJar) {
        String strBanner = readText(dirVersion.resolve(STR_VERSION_FILE));
        String strSource = readText(dirVersion.resolve(STR_SOURCE_FILE));
        PqsSource source = sourceOf(strSource);
        String strDeclaredLine = declaredCantonLine(strSource);

        if (strBanner != null) {
            try {
                ScribeBanner banner = ScribeBanner.parse(strBanner);
                String strLine =
                        strDeclaredLine != null ? strDeclaredLine : banner.cantonLine();
                return Optional.of(new PqsInstallation(banner.version(), strLine, fileJar,
                        banner.strSchemaRevision(), banner.strDamlSdkVersion(), source));
            }
            catch (InstallException ex) {
                log.warn("unreadable {} in {}: {}", STR_VERSION_FILE, dirVersion, ex.getMessage());
            }
        }

        // The directory name is a fallback, and a weak one: a staging directory
        // may be named for a generation rather than a version - "2.x" - in
        // which case only the banner identifies the binary.
        Optional<VersionId> optVersion = VersionId.tryParse(dirVersion.getFileName().toString());
        if (optVersion.isEmpty())
            return Optional.empty();

        log.debug("no banner for {}, falling back to the directory name", dirVersion);
        String strLine = strDeclaredLine != null ? strDeclaredLine : optVersion.get().line();
        return Optional.of(
                new PqsInstallation(optVersion.get(), strLine, fileJar, null, null, source));
    }


    /**
     * The Canton line SOURCE.txt declares, which is the only reliable route on
     * the 2.x generation: scribe there is versioned 0.x, so v0.5.5 would derive
     * to "0.5" and resolve for no Canton at all.
     *
     * @return the declared line, or null when SOURCE.txt does not carry one
     */
    private static String declaredCantonLine(String strText) {
        if (strText == null)
            return null;

        Matcher mtc = PAT_CANTON_LINE.matcher(strText);
        return mtc.find() ? mtc.group(1) : null;
    }


    /**
     * HEURISTIC. SOURCE.txt records an origin path, a sha256 and a staging
     * timestamp, and its exact wording is not a contract. Anything unrecognised
     * stays UNKNOWN rather than being guessed into a licensing claim.
     *
     * MOCK is tested FIRST and on its own declared fields, never on the origin
     * path. A stand-in built under a directory containing "docker" would
     * otherwise classify as OCI_IMAGE, which is the one misreading that turns a
     * local build into a vendor-provenance claim.
     */
    private static PqsSource sourceOf(String strText) {
        if (strText == null)
            return PqsSource.UNKNOWN;

        if (PAT_SOURCE_MOCK.matcher(strText).find() || PAT_LICENSED_NO.matcher(strText).find())
            return PqsSource.MOCK;

        String str = strText.toLowerCase(Locale.ROOT);
        if (str.contains("docker") || str.contains("oci") || str.contains("pkg.dev"))
            return PqsSource.OCI_IMAGE;
        if (str.contains(".dpm/") || str.contains("dpm cache") || str.contains("dpm install"))
            return PqsSource.DPM;
        return PqsSource.MANUAL;
    }


    private static String readText(Path file) {
        if (!Files.isRegularFile(file))
            return null;

        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            log.warn("could not read {}: {}", file, ex.toString());
            return null;
        }
    }


    private static Path toRealPath(Path file) {
        try {
            return file.toRealPath();
        }
        catch (IOException ex) {
            return file.toAbsolutePath().normalize();
        }
    }


    private static List<Path> listDirectories(Path dirParent) {
        return InstallFs.listDirectories(dirParent);
    }
}
