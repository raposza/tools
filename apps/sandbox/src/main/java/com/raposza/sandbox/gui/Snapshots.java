// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.settings.RaposzaSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Named copies of a stack's PostgreSQL cluster, kept OUTSIDE any run directory.
 *
 * <h2>What a snapshot is</h2>
 *
 * The whole data directory, copied file for file. Not a dump: a dump of five
 * databases taken one after another gives five states that need not agree with
 * each other, and a participant whose contracts are a second ahead of the
 * sequencer's events is a torn ledger that fails later and further away. One
 * directory copied while nothing is running is one moment, for every database
 * at once, and it needs no `pg_dump` binary to exist or be findable.
 *
 * That is why saving STOPS THE STACK. It is not a limitation to be worked
 * around later: a cluster copied out from under a running server is a cluster
 * mid-write.
 *
 * <h2>Where they live, and why not under the run directory</h2>
 *
 * {@link RunDirectory} wipes `data/` at the start of every run. A snapshot
 * stored beneath it would be destroyed by the next Start - which is the one
 * moment someone is most likely to want it.
 *
 * <h2>What is recorded beside it</h2>
 *
 * A raw cluster copy is only readable by the same PostgreSQL MAJOR version, and
 * only meaningful to the Canton that wrote it. Both are recorded and both are
 * checked on load. The PostgreSQL version is read from the cluster's own
 * `PG_VERSION` file rather than asked of an API: it is a fact of the directory
 * being copied, present whether or not a server is running.
 *
 * <h2>A snapshot BELONGS TO the version that took it</h2>
 *
 * There is one snapshot directory per Canton version and edition, named by
 * {@link VersionKey}, and a window only ever lists and restores the one
 * belonging to what is selected. There is no migration and no cross-version
 * restore, so there is no path by which a participant opens a store another
 * generation wrote - the schemas are not the same object and Canton discovers
 * that some way into a start, as a failure about topology rather than about a
 * snapshot.
 *
 * The version and edition recorded in the meta file therefore say the same
 * thing as the directory name. That is deliberate: the check on load is what
 * catches a directory copied between versions BY HAND, which is the only way
 * left for one to arrive in the wrong place.
 *
 * Author Claude/bentzn
 */
public final class Snapshots {

    /** The parent of the per-version roots. Never holds a snapshot itself. */
    /** The ROOT is global and lives in the settings; this is its default. */
    public static final String STR_DIR_DEFAULT = ".raposza/snapshots";

    /** The reserved root for the LocalNetND topology - see {@link #ofLocalNet}. */
    public static final String STR_DIR_LOCALNET = "localnet";

    /** The cluster, under the snapshot directory. */
    public static final String STR_DIR_DATA = "data";

    public static final String STR_FILE_META = "snapshot.properties";

    /** A stopped server's leftover lock, which a restore must not bring. */
    public static final String STR_FILE_POSTMASTER_PID = "postmaster.pid";

    /** PostgreSQL's own major-version marker, at the root of a cluster. */
    public static final String STR_FILE_PG_VERSION = "PG_VERSION";

    public static final String STR_KEY_CANTON = "canton.version";

    public static final String STR_KEY_EDITION = "canton.edition";

    public static final String STR_KEY_PREFIX = "db.prefix";

    /**
     * The Splice bundle that wrote the cluster. LOCALNETND ONLY: a Sandbox
     * cluster was written by a Canton and nothing else, and this row is absent
     * there rather than empty.
     */
    public static final String STR_KEY_SPLICE = "splice.version";

    /**
     * The port block, as `&lt;first&gt;-&lt;postgres&gt;`. Canton's topology
     * state names the ports it was founded on, so a cluster restored onto
     * another block is a cluster the participant cannot open - the same reason
     * `db.prefix` is already recorded here.
     */
    public static final String STR_KEY_PORTS = "ports";

    public static final String STR_KEY_PG = "postgres.version";

    public static final String STR_KEY_PQS = "pqs";

    public static final String STR_KEY_CREATED = "created";

    /**
     * WHAT A NAME MAY NOT CONTAIN. Path separators, the characters Windows
     * refuses in a file name, and control characters. Everything else is
     * allowed and is escaped on the way to disk, so a name reads the way it
     * was typed - `pet shop, 3 orders` - and the directory beside it is
     * something both file systems accept.
     */
    private static final Pattern PAT_NAME_BAD = Pattern.compile("[\\x00-\\x1f/\\\\<>:\"|?*]");

    /** How long a display name may be. Characters, not encoded bytes. */
    public static final int N_NAME_MAX = 64;

    /**
     * The characters that survive into a directory name unescaped. Everything
     * else becomes `%XX` per UTF-8 byte, which is reversible, is the same on
     * both file systems, and needs no table to read.
     */
    private static final String STR_NAME_PLAIN =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-";

    /**
     * The MS-DOS device names, which Windows still refuses as file names in
     * any directory and with any extension. A snapshot called `con` is a
     * legal thing to type and an illegal directory to create, so the first
     * character is escaped and the name round-trips anyway.
     */
    private static final Set<String> SET_NAME_DEVICE = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final Path dirRoot;


    /**
     * @param dirRootNew where snapshots are kept; never null
     */
    public Snapshots(Path dirRootNew) {
        if (dirRootNew == null)
            throw new IllegalArgumentException("a snapshot directory is required");
        this.dirRoot = dirRootNew.toAbsolutePath().normalize();
    }


    /**
     * @param version the Canton version the snapshots belong to; never null
     * @param edition its edition, or null for unknown
     * @return the store for that version alone
     */
    public static Snapshots ofVersion(VersionId version, Edition edition) {
        return new Snapshots(dirRootOf(version, edition));
    }


    /**
     * @param version the Canton version; never null
     * @param edition its edition, or null for unknown
     * @return `~/.raposza/snapshots/&lt;version&gt;-&lt;edition&gt;`
     */
    public static Path dirRootOf(VersionId version, Edition edition) {
        return RaposzaSettings.current().dirSnapshots()
                .resolve(VersionKey.strOf(version, edition));
    }


    /**
     * THE LOCALNETND STORE, one per key, a SIBLING of the per-version roots.
     *
     * The discriminator there is three things rather than one - Splice bundle,
     * Canton jar, port block - so it cannot be a version key, and a root named
     * for it has to live somewhere a per-version listing will never reach.
     * `founding` set that precedent; nothing can collide, because
     * {@link VersionKey#strOf} always begins with a version.
     *
     * @param strKey the key, from `Founding.strKeyOfLocalNet`; never null or
     *        empty
     * @return the store for that key alone
     */
    public static Snapshots ofLocalNet(String strKey) {
        if (strKey == null || strKey.isEmpty())
            throw new IllegalArgumentException("a localnet key is required");
        return new Snapshots(RaposzaSettings.current().dirSnapshots()
                .resolve(STR_DIR_LOCALNET).resolve(strEncode(strKey)));
    }


    public Path dirRoot() {
        return dirRoot;
    }


    /**
     * @param strName a snapshot name, as it is displayed
     * @return where it is, whether or not it exists
     */
    public Path dirOf(String strName) {
        return dirRoot.resolve(strEncode(strName));
    }


    /**
     * @return every snapshot present, oldest name first
     */
    public List<String> lstNames() {
        List<String> lstName = new ArrayList<>();
        if (!Files.isDirectory(dirRoot))
            return lstName;

        try (Stream<Path> strmPath = Files.list(dirRoot)) {
            for (Path dir : strmPath.toList()) {
                if (Files.isRegularFile(dir.resolve(STR_FILE_META)))
                    lstName.add(strDecode(dir.getFileName().toString()));
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not list " + dirRoot + ": " + ex.getMessage(),
                    ex);
        }
        Collections.sort(lstName);
        return lstName;
    }


    /**
     * @param strName what to check, as a DISPLAY name
     * @throws IllegalArgumentException when it is not a usable snapshot name
     */
    public static void requireName(String strName) {
        if (strName == null || strName.isEmpty())
            throw new IllegalArgumentException("a snapshot needs a name");
        if (strName.length() > N_NAME_MAX) {
            throw new IllegalArgumentException("a snapshot name is at most " + N_NAME_MAX
                    + " characters: " + strName);
        }
        if (PAT_NAME_BAD.matcher(strName).find()) {
            throw new IllegalArgumentException("a snapshot name may not contain a path separator,"
                    + " a control character, or any of < > : \" | ? * : " + strName);
        }
        // LEADING AND TRAILING SPACE, which encodes and round-trips perfectly
        // and is invisible in the list. Two snapshots differing only by it are
        // two rows a reader cannot tell apart.
        if (!strName.equals(strName.strip()))
            throw new IllegalArgumentException("a snapshot name may not begin or end with a space");
        if (".".equals(strName) || "..".equals(strName))
            throw new IllegalArgumentException("that is a directory, not a name: " + strName);
    }


    /**
     * A display name to the directory name that holds it.
     *
     * Percent-encoding per UTF-8 byte, which is reversible without a table and
     * is accepted by both file systems. `%` itself is encoded, or the mapping
     * would not be one-to-one.
     *
     * @param strName the name as it was typed
     * @return the directory name, which may be the same string
     */
    public static String strEncode(String strName) {
        StringBuilder bldOut = new StringBuilder();
        for (byte idxByte : strName.getBytes(StandardCharsets.UTF_8)) {
            char chHere = (char) (idxByte & 0xff);
            if (STR_NAME_PLAIN.indexOf(chHere) >= 0)
                bldOut.append(chHere);
            else
                bldOut.append('%').append(String.format("%02X", Integer.valueOf(idxByte & 0xff)));
        }

        // A TRAILING DOT is legal here and is silently dropped by Windows,
        // which turns `v1.` into `v1` and makes two names one directory.
        int nLast = bldOut.length() - 1;
        if (nLast >= 0 && bldOut.charAt(nLast) == '.')
            bldOut.replace(nLast, nLast + 1, "%2E");

        String strOut = bldOut.toString();
        int idxDot = strOut.indexOf('.');
        String strStem = idxDot < 0 ? strOut : strOut.substring(0, idxDot);
        if (SET_NAME_DEVICE.contains(strStem.toUpperCase(Locale.ROOT))) {
            strOut = "%" + String.format("%02X",
                    Integer.valueOf(strOut.charAt(0) & 0xff)) + strOut.substring(1);
        }
        return strOut;
    }


    /**
     * A directory name back to the display name.
     *
     * A directory that was not written by {@link #strEncode} - one made by
     * hand, or holding a stray `%` - is returned unchanged rather than
     * refused. It is still a real snapshot directory and hiding it would be
     * worse than showing a name with a `%` in it.
     *
     * @param strDir the directory name
     * @return the name as it was typed
     */
    public static String strDecode(String strDir) {
        if (strDir.indexOf('%') < 0)
            return strDir;

        ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
        int idxChar = 0;
        while (idxChar < strDir.length()) {
            char chHere = strDir.charAt(idxChar);
            if (chHere != '%') {
                bufOut.write(chHere);
                idxChar++;
                continue;
            }
            if (idxChar + 2 >= strDir.length())
                return strDir;
            try {
                bufOut.write(Integer.parseInt(strDir.substring(idxChar + 1, idxChar + 3), 16));
            }
            catch (NumberFormatException ex) {
                return strDir;
            }
            idxChar += 3;
        }
        return new String(bufOut.toByteArray(), StandardCharsets.UTF_8);
    }


    /**
     * Copies a STOPPED cluster into a named snapshot.
     *
     * @param strName what to call it
     * @param dirData the cluster to copy, which nothing may be writing to
     * @param props what was running when it was taken
     * @throws IllegalArgumentException when the name is unusable or taken
     * @throws IllegalStateException when the copy fails
     */
    public void save(String strName, Path dirData, Properties props) {
        requireName(strName);
        Path dirSnap = dirOf(strName);
        if (Files.exists(dirSnap))
            throw new IllegalArgumentException("there is already a snapshot called " + strName);
        if (!Files.isDirectory(dirData))
            throw new IllegalStateException("there is no cluster at " + dirData);

        Properties propsAll = new Properties();
        propsAll.putAll(props);
        propsAll.setProperty(STR_KEY_PG, strPgVersionOf(dirData));
        propsAll.setProperty(STR_KEY_CREATED, Instant.now().toString());

        try {
            Files.createDirectories(dirSnap);
            copyTree(dirData, dirSnap.resolve(STR_DIR_DATA));
            try (OutputStream out = Files.newOutputStream(dirSnap.resolve(STR_FILE_META))) {
                propsAll.store(out, "raposza sandbox snapshot");
            }
        }
        catch (IOException ex) {
            // A HALF-WRITTEN SNAPSHOT IS WORSE THAN NONE: it would list, load
            // and produce a cluster PostgreSQL refuses in a way that reads as a
            // Canton fault. The meta file is written LAST, so a failure before
            // it leaves something lstNames() will not offer - and the partial
            // directory goes anyway.
            deleteTree(dirSnap);
            throw new IllegalStateException("could not write the snapshot " + strName + ": "
                    + ex.getMessage(), ex);
        }
    }


    /**
     * @param strName the snapshot to read
     * @return what was recorded with it
     */
    public Properties read(String strName) {
        Path fileMeta = dirOf(strName).resolve(STR_FILE_META);
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(fileMeta)) {
            props.load(in);
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not read " + fileMeta + ": " + ex.getMessage(),
                    ex);
        }
        return props;
    }


    /**
     * Puts a snapshot's cluster where a run directory expects its own.
     *
     * The destination is REPLACED, not merged. A restore over a live cluster's
     * leftovers is a directory PostgreSQL may open and may not, depending on
     * which files happened to survive.
     *
     * @param strName the snapshot to restore
     * @param dirData where the cluster goes
     */
    public void restore(String strName, Path dirData) {
        Path dirFrom = dirOf(strName).resolve(STR_DIR_DATA);
        if (!Files.isDirectory(dirFrom))
            throw new IllegalStateException("the snapshot " + strName + " has no cluster in it");

        deleteTree(dirData);
        try {
            copyTree(dirFrom, dirData);
            // THE LOCK OF A SERVER THAT IS NOT RUNNING. It was copied in with
            // everything else and names a process id from the run that took
            // the snapshot; PostgreSQL reads it before it reads anything else.
            Files.deleteIfExists(dirData.resolve(STR_FILE_POSTMASTER_PID));
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not restore " + strName + ": " + ex.getMessage(),
                    ex);
        }
    }


    /**
     * @param strName the snapshot to remove
     */
    public void delete(String strName) {
        requireName(strName);
        deleteTree(dirOf(strName));
    }


    /**
     * @param dirData a cluster directory
     * @return its PostgreSQL major version, or "unknown"
     */
    public static String strPgVersionOf(Path dirData) {
        Path file = dirData.resolve(STR_FILE_PG_VERSION);
        try {
            return Files.readString(file).trim();
        }
        catch (IOException ex) {
            return "unknown";
        }
    }


    private static void copyTree(Path dirFrom, Path dirTo) throws IOException {
        Files.walkFileTree(dirFrom, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                Path dirTarget = dirTo.resolve(dirFrom.relativize(dir).toString());
                Files.createDirectories(dirTarget);
                copyPerms(dir, dirTarget);
                return FileVisitResult.CONTINUE;
            }


            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                // Sockets and anything else that is not a regular file are
                // SKIPPED. A stopped cluster should have none, and copying one
                // fails with an error about the destination that says nothing
                // about the cause.
                if (attrs.isRegularFile()) {
                    Files.copy(file, dirTo.resolve(dirFrom.relativize(file).toString()),
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }


    /**
     * MODE MATTERS ON A CLUSTER. PostgreSQL refuses to open a data directory
     * that is not 0700 or 0750 and exits at once, which reaches the window as
     * a start that timed out rather than as a permission problem.
     * `Files.createDirectories` applies the umask - 0755 on this machine - so
     * a restored cluster was unopenable however faithfully its FILES had been
     * copied.
     *
     * @param pathFrom what to read the mode from
     * @param pathTo what to put it on
     */
    private static void copyPerms(Path pathFrom, Path pathTo) {
        try {
            Files.setPosixFilePermissions(pathTo,
                    Files.getPosixFilePermissions(pathFrom));
        }
        catch (UnsupportedOperationException | IOException ex) {
            // Not a POSIX file store. Nothing to carry over and nothing that
            // would read it if there were.
        }
    }


    private static void deleteTree(Path dir) {
        if (!Files.exists(dir))
            return;

        try (Stream<Path> strmPath = Files.walk(dir)) {
            // Deepest first, and collected before deleting: the walk is lazy
            // and a stream that deletes what it is walking is undefined.
            for (Path path : strmPath.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not remove " + dir + ": " + ex.getMessage(), ex);
        }
    }
}
