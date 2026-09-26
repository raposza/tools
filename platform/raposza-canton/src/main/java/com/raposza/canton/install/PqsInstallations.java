// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

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
import java.util.Optional;
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
 * The DPM cache is a secondary source and is WALKED, never composed. Canton
 * sits at components/&lt;name&gt;/&lt;version&gt;/ but scribe inserts a generation
 * segment - components/scribe/daml3.4/3.4.1/ - so a path template built for
 * one misses the other entirely.
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

    private static final Pattern PAT_SOURCE_MOCK =
            Pattern.compile("(?mi)^\\s*source\\s*:\\s*mock");

    private static final Pattern PAT_LICENSED_NO =
            Pattern.compile("(?mi)^\\s*licensed\\s*:\\s*no\\b");

    private static final Pattern PAT_CANTON_LINE =
            Pattern.compile("(?mi)^\\s*canton\\s+line\\s*:\\s*(\\S+)\\s*$");

    private final Path dirPqsRoot;
    private final Path dirDpmRoot;


    /**
     * @param dirPqsRoot the staging root, normally ~/.pqs
     * @param dirDpmRoot the DPM root, normally ~/.dpm
     */
    public PqsInstallations(Path dirPqsRoot, Path dirDpmRoot) {
        if (dirPqsRoot == null || dirDpmRoot == null)
            throw new IllegalArgumentException("both roots are required");
        this.dirPqsRoot = dirPqsRoot;
        this.dirDpmRoot = dirDpmRoot;
    }


    public static PqsInstallations ofDefaults() {
        Path dirHome = Path.of(System.getProperty("user.home"));
        return new PqsInstallations(dirHome.resolve(".pqs"), dirHome.resolve(".dpm"));
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
     * Every scribe.jar in the DPM cache, found by walking it.
     *
     * Provided for staging and reporting, not for launching: harness code
     * launches what is staged under the PQS root.
     *
     * @return the jars found, in no guaranteed order
     */
    public List<Path> discoverDpmCacheJars() {
        List<Path> lstJar = new ArrayList<>();
        Path dirScribe = dirDpmRoot.resolve("cache").resolve("components").resolve("scribe");
        if (!Files.isDirectory(dirScribe))
            return lstJar;

        try (Stream<Path> strmPaths = Files.walk(dirScribe, N_CACHE_SEARCH_DEPTH)) {
            Iterator<Path> itPath = strmPaths.iterator();
            while (itPath.hasNext()) {
                Path file = itPath.next();
                if (!Files.isRegularFile(file))
                    continue;
                String strName = file.getFileName().toString();
                if (strName.startsWith("scribe") && strName.endsWith(".jar"))
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
