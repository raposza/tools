// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import com.raposza.runtime.localnet.SpliceCheck;
import com.raposza.runtime.localnet.SpliceInstallations;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Puts a Splice bundle under `~/.splice` - todo.md A-38.
 *
 * <h2>Two ways in, one way down - the operator's instruction of 2026-09-23</h2>
 *
 * The Sandbox topology has DAML Assistant and DPM to fetch for it; LocalNetND
 * has no vendor installer at all. So this offers both a download straight
 * into `~/.splice` and an install of an archive somebody fetched by hand, and
 * both end in the same {@link #install} - the downloaded archive is simply an
 * archive that happens to be in place already.
 *
 * <h2>Where from - `splice_inventory.md` section 2, MEASURED</h2>
 *
 * The release repository is `digital-asset/decentralized-canton-sync`; the
 * source repository `canton-network/splice` has no releases. The archive URL is
 * a template with one substitution, and the version list is the releases API,
 * which is unauthenticated, paged, holds tags that are not versions and sorts
 * by publish date rather than version. All three are handled here.
 *
 * <h2>Where to - D-305, and what is never done</h2>
 *
 * `~/.splice/&lt;v&gt;/splice-node/` the bundle, `&lt;v&gt;_splice-node.tar.gz`
 * the archive KEPT beside it, `SOURCE.txt` its provenance - the layout
 * `probe_bundle.sh` staged every bundle on the workstation with. The archive
 * is COPIED, never moved, and nothing here deletes anything. On Windows the
 * root is the same `.splice` under the user's home, which is where
 * {@link SpliceInstallations} already looks.
 *
 * <h2>The version is the ARCHIVE NAME's prefix</h2>
 *
 * `0.7.4_splice-node.tar.gz` is how the vendor names it, and it is the only
 * place the version is written before the archive is opened. A file named
 * otherwise is REFUSED rather than staged as `unversioned`: the Splice box
 * lists directory names as versions, and a directory called `unversioned` would
 * sit in it as though it were one.
 *
 * <h2>Unpacked with `tar`, unstripped</h2>
 *
 * The tarball carries one top directory, `splice-node/`, so it is unpacked into
 * the version directory as it is - exactly what `probe_bundle.sh` did. `tar`
 * is on Windows too, per {@link Extract}. The result counts only when
 * `bin/splice-node` is executable, the same test {@link SpliceInstallations}
 * lists by.
 *
 * Author Claude/bentzn
 */
public final class SpliceAcquire {

    public static final String STR_URL_RELEASES =
            "https://api.github.com/repos/digital-asset/decentralized-canton-sync/releases?per_page=100&page=";

    public static final String STR_URL_ARCHIVE =
            "https://github.com/digital-asset/decentralized-canton-sync/releases/download/v%s/%s";

    public static final String STR_SUFFIX_ARCHIVE = "_splice-node.tar.gz";

    public static final String STR_FILE_SOURCE = "SOURCE.txt";

    /** More pages than the line has ever needed; 80 releases fit one. */
    static final int N_PAGE_MAX = 10;

    private static final Pattern PAT_TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v(\\d+\\.\\d+\\.\\d+)\"");

    private static final Pattern PAT_ARCHIVE = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)"
            + Pattern.quote(STR_SUFFIX_ARCHIVE) + "$");

    private static final long N_MB = 1024L * 1024L;


    private SpliceAcquire() {
    }


    /**
     * @param strVersion a version such as `0.7.4`
     * @return the name the vendor gives its archive
     */
    public static String strArchive(String strVersion) {
        return strVersion + STR_SUFFIX_ARCHIVE;
    }


    /**
     * @param strVersion a version
     * @return where the vendor publishes its archive
     */
    public static String strUrlArchive(String strVersion) {
        return String.format(STR_URL_ARCHIVE, strVersion, strArchive(strVersion));
    }


    /**
     * @param strName a file name
     * @return the version its name carries, or null when it is not a vendor
     *         archive name
     */
    public static String strVersionOfArchive(String strName) {
        Matcher matcher = PAT_ARCHIVE.matcher(strName == null ? "" : strName);
        return matcher.matches() ? matcher.group(1) : null;
    }


    /**
     * The versions one page of the releases API names. A tag that is not
     * `v&lt;major&gt;.&lt;minor&gt;.&lt;patch&gt;` is not a version and is
     * skipped - `token-standard-v2-upcoming` and `java-codegen` share the stream.
     *
     * @param strJson one page of the releases API
     * @return its versions, in page order, each once
     */
    static List<String> lstVersionOfPage(String strJson) {
        Set<String> setOut = new LinkedHashSet<>();
        Matcher matcher = PAT_TAG.matcher(strJson);
        while (matcher.find()) {
            setOut.add(matcher.group(1));
        }
        return new ArrayList<>(setOut);
    }


    /**
     * @param collVersion versions in any order
     * @return them newest first, by number and not by string
     */
    static List<String> lstNewestFirst(java.util.Collection<String> collVersion) {
        List<String> lstOut = new ArrayList<>(new LinkedHashSet<>(collVersion));
        lstOut.sort(Comparator.comparing(SpliceAcquire::strSortKey).reversed());
        return lstOut;
    }


    /**
     * Every published version, newest first. Pages until a page names no
     * release at all.
     *
     * @return the versions
     * @throws IOException when the API cannot be read
     */
    public static List<String> lstVersionPublished() throws IOException {
        List<String> lstAll = new ArrayList<>();
        for (int nPage = 1; nPage <= N_PAGE_MAX; nPage++) {
            String strJson = Download.strFetch(STR_URL_RELEASES + nPage);
            if (!strJson.contains("\"tag_name\""))
                break;
            lstAll.addAll(lstVersionOfPage(strJson));
        }
        return lstNewestFirst(lstAll);
    }


    /**
     * What the vendor's server says the archive weighs, by a HEAD that follows
     * the redirect to where the bytes are - the `Content-Length` measured in
     * `splice_inventory.md` section 2.2.
     *
     * @param strVersion a version
     * @return its size in bytes, or -1 when the server does not say
     * @throws IOException when the archive is not there
     */
    public static long nBytesPublished(String strVersion) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(Download.N_SECONDS_CONNECT))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(strUrlArchive(strVersion)))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200)
                throw new IOException(strArchive(strVersion) + " answered HTTP " + response.statusCode());
            return response.headers().firstValueAsLong("content-length").orElse(-1L);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while asking for " + strArchive(strVersion), ex);
        }
    }


    /**
     * @param nBytes a size, or -1
     * @return it for a person, in MB, or `size unknown`
     */
    public static String strSize(long nBytes) {
        if (nBytes < 0)
            return "size unknown";
        return (nBytes + N_MB / 2) / N_MB + " MB";
    }


    /**
     * Fetches the vendor's archive into its version directory, then installs it.
     *
     * THE PROGRESS IS ONE LINE, REWRITTEN - his instruction of 2026-09-23. It
     * goes to `lineUpdate`, which a window shows in place; everything else goes
     * to `lineOut` as a line of its own. An archive already in place is not
     * fetched again and says nothing about it.
     *
     * @param strVersion a published version
     * @param dirRoot the `.splice` root
     * @param lineOut told each step, once
     * @param lineUpdate told the download's figure, repeatedly, or null
     * @return the bundle directory
     * @throws IOException when any step fails
     */
    public static Path download(String strVersion, Path dirRoot, Consumer<String> lineOut,
            Consumer<String> lineUpdate) throws IOException {
        Path fileArchive = dirRoot.resolve(strVersion).resolve(strArchive(strVersion));
        if (!Files.isRegularFile(fileArchive)) {
            long[] arrMbLast = {-1L};
            Download.fetch(strUrlArchive(strVersion), fileArchive, (cntBytes, cntTotal) -> {
                long nMb = cntBytes / N_MB;
                if (nMb != arrMbLast[0]) {
                    arrMbLast[0] = nMb;
                    say(lineUpdate, strDownloading(strVersion, nMb, cntTotal));
                }
            });
        }
        return install(fileArchive, dirRoot, strUrlArchive(strVersion), lineOut);
    }


    /**
     * @param strVersion a version
     * @param nMb how much has arrived
     * @param cntTotal the whole, in bytes, or -1
     * @return the download's line, as the log shows it
     */
    static String strDownloading(String strVersion, long nMb, long cntTotal) {
        return "Downloading Splice " + strVersion + ": " + nMb + " of "
                + (cntTotal < 0 ? "?" : String.valueOf(cntTotal / N_MB)) + " MB";
    }


    /**
     * Installs an archive: copies it into its version directory unless it is
     * already there, unpacks it, checks the launcher and writes `SOURCE.txt`.
     *
     * @param fileArchive a vendor archive, named `&lt;v&gt;_splice-node.tar.gz`
     * @param dirRoot the `.splice` root
     * @param strFrom where it came from, for `SOURCE.txt`
     * @param lineOut told what is happening
     * @return the bundle directory
     * @throws IOException when the name carries no version, the version is
     *         already installed, `tar` fails or the bundle has no launcher
     */
    public static Path install(Path fileArchive, Path dirRoot, String strFrom, Consumer<String> lineOut)
            throws IOException {
        String strName = fileArchive.getFileName().toString();
        String strVersion = strVersionOfArchive(strName);
        if (strVersion == null)
            throw new IOException(strName + " is not named <version>" + STR_SUFFIX_ARCHIVE
                    + ", so it carries no version; rename it as the vendor names it");

        Path dirVersion = dirRoot.resolve(strVersion);
        Path dirBundle = dirVersion.resolve(SpliceInstallations.STR_BUNDLE);
        if (isInstalled(dirBundle))
            throw new IOException("Splice " + strVersion + " is already installed at " + dirBundle);

        Files.createDirectories(dirVersion);
        Path fileKept = dirVersion.resolve(strName);
        if (!Files.exists(fileKept) || !Files.isSameFile(fileKept, fileArchive)) {
            Files.copy(fileArchive, fileKept, StandardCopyOption.REPLACE_EXISTING);
        }

        say(lineOut, "Unpacking Splice " + strVersion + " - about 1.5 GB on disk");
        List<String> lstCommand = List.of(Extract.STR_BIN_TAR, Extract.STR_FLAG_EXTRACT,
                fileKept.toAbsolutePath().toString(), Extract.STR_FLAG_DIR,
                dirVersion.toAbsolutePath().toString());
        int nExit = InstallRunner.nRun(lstCommand, null, HostPlatform.ofDefaults(), null, null,
                lineOut, null);
        if (nExit != 0)
            throw new IOException("tar exited " + nExit + " unpacking " + fileKept);
        if (!isInstalled(dirBundle))
            throw new IOException(strName + " unpacked, but " + dirBundle
                    + "/bin/splice-node is not there or not executable");

        // THE POST-INSTALL CHECK - his instruction, 2026-09-23, after 0.8.2 was
        // installed from this window and could not start. A bundle that fails
        // it is taken back out, so the Splice box never lists it; the archive
        // stays, because it is what was published and it is the evidence.
        say(lineOut, "Checking that Splice " + strVersion + " starts");
        String strWhyNot = SpliceCheck.strWhyNotStarts(dirBundle);
        if (strWhyNot != null) {
            deleteTree(dirBundle);
            throw new IOException("Splice " + strVersion + " was NOT installed: " + strWhyNot
                    + ". The unpacked bundle was removed; the archive is kept at " + fileKept);
        }

        writeSource(dirVersion, fileKept, strFrom, strVersion);
        say(lineOut, "Installed Splice " + strVersion);
        return dirBundle;
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @return whether its launcher is executable - the test the Splice box lists by
     */
    static boolean isInstalled(Path dirBundle) {
        return Files.isExecutable(dirBundle.resolve("bin").resolve(SpliceInstallations.STR_BUNDLE));
    }


    /**
     * @param dir a directory to remove with everything under it
     * @throws IOException when any of it cannot be removed
     */
    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir))
            return;
        try (Stream<Path> strm = Files.walk(dir)) {
            for (Path path : strm.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }


    private static void writeSource(Path dirVersion, Path fileKept, String strFrom, String strVersion)
            throws IOException {
        String strText = "archive:  " + fileKept.getFileName() + "\n"
                + "staged:   " + Instant.now().truncatedTo(ChronoUnit.SECONDS) + "\n"
                + "from:     " + strFrom + "\n"
                + "sha256:   " + strSha256(fileKept) + "\n"
                + "bytes:    " + Files.size(fileKept) + "\n"
                + "version:  " + strVersion + "  (read off the archive name)\n"
                + "by:       the Sandbox window\n";
        Files.writeString(dirVersion.resolve(STR_FILE_SOURCE), strText);
    }


    /**
     * @param file a file
     * @return its sha256, lowercase hex
     * @throws IOException when it cannot be read
     */
    static String strSha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IOException("no SHA-256 on this JVM", ex);
        }
        byte[] arrBuf = new byte[65536];
        try (InputStream in = Files.newInputStream(file)) {
            int cntRead = in.read(arrBuf);
            while (cntRead >= 0) {
                digest.update(arrBuf, 0, cntRead);
                cntRead = in.read(arrBuf);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }


    private static void say(Consumer<String> lineOut, String strLine) {
        if (lineOut != null)
            lineOut.accept(strLine);
    }


    /**
     * @param strVersion `major.minor.patch`
     * @return a key that sorts numerically, each segment padded to four digits
     */
    private static String strSortKey(String strVersion) {
        StringBuilder bld = new StringBuilder();
        for (String strPart : strVersion.split("\\.")) {
            StringBuilder bldPart = new StringBuilder(strPart.replaceAll("[^0-9]", ""));
            while (bldPart.length() < 4) {
                bldPart.insert(0, '0');
            }
            bld.append(bldPart);
        }
        return bld.toString();
    }
}
