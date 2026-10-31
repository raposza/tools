// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ONE FILE WITH EVERYTHING THE WINDOW WRITES - his instruction, 2026-10-30:
 * "We need to incorporate everything in the debug log. That is what devs will
 * send. They will not want to go 17 different places and find stuff to give
 * me."
 *
 * Until then a run was spread over the terminal, the Main and Ledger panes, a
 * sub-tab per component, the OIDC, Web and JSON API panes and the run
 * directory's own files - and postgres, PQS and the JSON API had no file at
 * all. A report from a machine nobody here can see arrived as whichever of
 * those the developer thought to copy.
 *
 * <h2>What goes in</h2>
 *
 * A header - the jar, Java, the operating system, the arguments, the
 * environment variables a toolchain is found through, and the settings file
 * verbatim. Then every line, in the order it was produced, stamped in UTC and
 * tagged with where it came from: `console` for standard output and standard
 * error, which carries slf4j, every process the window starts and every
 * uncaught exception, the event thread's included; and every log pane by its
 * tab name. Nothing is filtered and nothing is trimmed - the panes drop their
 * oldest half at 32 MB, this file does not.
 *
 * <h2>Where</h2>
 *
 * `logs/sandbox-<UTC stamp>-<pid>.log` under the Raposza home - `~/.raposza`,
 * or `%APPDATA%\raposza` on Windows. A new file per window, and the newest
 * {@link #CNT_KEEP} kept: a LocalNetND run writes Splice's log into it, which
 * is large.
 *
 * <h2>It never stops the window</h2>
 *
 * A file that cannot be opened is said once on standard error and the window
 * runs without it. Lines are written by one daemon thread and flushed whenever
 * the queue runs dry, so a burst of Canton output costs the producer a queue
 * insert, and a shutdown hook drains what is left.
 *
 * Author Claude/bentzn
 */
public final class DebugLog {

    /** Under the Raposza home. */
    public static final String STR_DIR = "logs";

    public static final String STR_PREFIX = "sandbox-";

    public static final String STR_SUFFIX = ".log";

    /** Files kept, the new one included. */
    public static final int CNT_KEEP = 10;

    /** Standard output and standard error. */
    public static final String SRC_CONSOLE = "console";

    /**
     * Lines produced before the file is open - slf4j's first lines can come
     * that early - held and written after the header.
     */
    private static final int CNT_EARLY_MAX = 10_000;

    /** How long the shutdown hook waits for the queue to drain. */
    private static final long N_MS_DRAIN = 5_000L;

    /** The variables a toolchain, a JVM or a temporary directory is found through. */
    private static final List<String> LST_ENV = List.of("JAVA_HOME", "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS", "DPM_HOME", "DAML_HOME", "APPDATA", "LOCALAPPDATA", "TMP", "TEMP",
            "HTTPS_PROXY", "HTTP_PROXY", "NO_PROXY", "PATH");

    private static final DateTimeFormatter FMT_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter FMT_NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** The marker the writer stops on. A real line is never empty: it carries a stamp. */
    private static final String STR_END = "";

    private static final Deque<String> lstEarly = new ArrayDeque<>();

    private static DebugLog held;

    private final Path file;

    private final Writer writer;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    private final Thread threadWrite;

    /** Set when the disk refused a write: lines are dropped rather than queued for ever. */
    private volatile boolean flagDead;


    private DebugLog(Path fileNew, Writer writerNew) {
        this.file = fileNew;
        this.writer = writerNew;
        this.threadWrite = new Thread(this::drain, "raposza-debug-log");
        threadWrite.setDaemon(true);
    }


    /**
     * Opens this window's file and makes it the one every line goes to. A
     * second call does nothing.
     *
     * @param dirLogs where the files are kept
     * @param arrArg the command line, for the header
     * @param fileSettings the settings file, copied into the header, or null
     * @return the file, or null when it could not be opened
     */
    public static Path open(Path dirLogs, String[] arrArg, Path fileSettings) {
        // SAID OUTSIDE THE LOCK. Standard error is teed, and the tee calls
        // line(), which takes this lock - from another thread, that is a
        // deadlock with whoever holds the tee's.
        String strFail;
        synchronized (DebugLog.class) {
            if (held != null)
                return held.file;
            try {
                Files.createDirectories(dirLogs);
                prune(dirLogs, CNT_KEEP - 1);
                Path fileNew = dirLogs.resolve(STR_PREFIX + FMT_NAME.format(Instant.now()) + "-"
                        + ProcessHandle.current().pid() + STR_SUFFIX);
                Writer writerNew = new BufferedWriter(Files.newBufferedWriter(fileNew,
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE));
                DebugLog log = new DebugLog(fileNew, writerNew);
                writerNew.write(strHeader(fileNew, arrArg, strSettings(fileSettings)));
                writerNew.flush();
                while (!lstEarly.isEmpty()) {
                    log.queue.add(lstEarly.removeFirst());
                }
                log.threadWrite.start();
                Runtime.getRuntime().addShutdownHook(
                        new Thread(log::close, "raposza-debug-log-close"));
                held = log;
                return fileNew;
            }
            catch (IOException | RuntimeException ex) {
                lstEarly.clear();
                strFail = "the debug log could not be opened in " + dirLogs + ": " + ex;
            }
        }
        System.err.println(strFail);
        return null;
    }


    /**
     * @return this window's file, or null when none is open
     */
    public static synchronized Path fileCurrent() {
        return held == null ? null : held.file;
    }


    /**
     * Safe from any thread, and never blocks on the disk.
     *
     * @param strSource where the line came from - `console` or a pane's tab name
     * @param strLine one line, without its terminator
     */
    public static void line(String strSource, String strLine) {
        String strOut = FMT_STAMP.format(Instant.now()) + " [" + strSource + "] "
                + (strLine == null ? "" : strLine) + "\n";
        DebugLog log;
        synchronized (DebugLog.class) {
            log = held;
            if (log == null) {
                lstEarly.addLast(strOut);
                while (lstEarly.size() > CNT_EARLY_MAX) {
                    lstEarly.removeFirst();
                }
                return;
            }
        }
        if (!log.flagDead)
            log.queue.add(strOut);
    }


    /**
     * Writes what is queued and closes the file. For the shutdown hook and
     * for a test; the window never calls it.
     */
    void close() {
        queue.add(STR_END);
        try {
            threadWrite.join(N_MS_DRAIN);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }


    /** For a test: forgets the open file, so the next {@link #open} opens another. */
    static synchronized void closeForTest() {
        if (held != null)
            held.close();
        held = null;
        lstEarly.clear();
    }


    private void drain() {
        try {
            while (true) {
                String strLine = queue.poll(1, TimeUnit.SECONDS);
                if (strLine == null)
                    continue;
                if (strLine.isEmpty())
                    break;
                writer.write(strLine);
                if (queue.isEmpty())
                    writer.flush();
            }
        }
        catch (IOException ex) {
            flagDead = true;
            queue.clear();
            System.err.println("the debug log stopped: " + ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        finally {
            try {
                writer.close();
            }
            catch (IOException ex) {
                // nothing left to tell it to
            }
        }
    }


    /**
     * Deletes the oldest of this application's files until at most cntLeave
     * remain. The names sort by time.
     *
     * @param dirLogs where the files are
     * @param cntLeave how many to leave
     */
    static void prune(Path dirLogs, int cntLeave) throws IOException {
        List<Path> lstFile = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirLogs,
                STR_PREFIX + "*" + STR_SUFFIX)) {
            for (Path file : stream) {
                lstFile.add(file);
            }
        }
        Collections.sort(lstFile);
        for (int idx = 0; idx < lstFile.size() - cntLeave; idx++) {
            try {
                Files.deleteIfExists(lstFile.get(idx));
            }
            catch (IOException ex) {
                // HELD BY A WINDOW STILL RUNNING, on Windows. It goes next time.
            }
        }
    }


    /**
     * @return what a report needs before its first line
     */
    static String strHeader(Path fileLog, String[] arrArg, String strSettings) {
        StringBuilder buf = new StringBuilder();
        buf.append("Raposza Sandbox - debug log\n");
        buf.append("file      ").append(fileLog).append('\n');
        buf.append("started   ").append(FMT_STAMP.format(Instant.now())).append('\n');
        buf.append("jar       ").append(strJar()).append('\n');
        buf.append("java      ").append(System.getProperty("java.version")).append(", ")
                .append(System.getProperty("java.vendor")).append(", ")
                .append(System.getProperty("java.home")).append('\n');
        buf.append("os        ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(' ')
                .append(System.getProperty("os.arch")).append('\n');
        buf.append("cpus      ").append(Runtime.getRuntime().availableProcessors())
                .append(", heap max ").append(Runtime.getRuntime().maxMemory() / (1024L * 1024L))
                .append(" MiB\n");
        buf.append("user      ").append(System.getProperty("user.name")).append(", home ")
                .append(System.getProperty("user.home")).append('\n');
        buf.append("cwd       ").append(System.getProperty("user.dir")).append('\n');
        buf.append("jvm args  ").append(ManagementFactory.getRuntimeMXBean().getInputArguments())
                .append('\n');
        buf.append("args      ").append(arrArg == null ? "[]" : List.of(arrArg)).append('\n');
        for (String strName : LST_ENV) {
            String strValue = System.getenv(strName);
            buf.append("env       ").append(strName).append('=')
                    .append(strValue == null ? "(unset)" : strValue).append('\n');
        }
        buf.append("settings  ").append(strSettings == null ? "(none)" : strSettings);
        if (strSettings != null && !strSettings.endsWith("\n"))
            buf.append('\n');
        buf.append("----------------------------------------------------------------------\n");
        return buf.toString();
    }


    /**
     * @param fileSettings the settings file, or null
     * @return its path and its content, or why there is none
     */
    static String strSettings(Path fileSettings) {
        if (fileSettings == null)
            return null;
        if (!Files.isRegularFile(fileSettings))
            return fileSettings + " (absent - every setting is its default)";
        try {
            return fileSettings + "\n" + Files.readString(fileSettings, StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException ex) {
            return fileSettings + " (unreadable: " + ex + ")";
        }
    }


    /** The jar this runs from, its size and when it was written - the version is in its name. */
    private static String strJar() {
        try {
            CodeSource source = DebugLog.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null)
                return "(unknown)";
            Path fileJar = Path.of(source.getLocation().toURI());
            if (!Files.isRegularFile(fileJar))
                return fileJar.toString();
            return fileJar + ", " + Files.size(fileJar) + " bytes, written "
                    + Files.getLastModifiedTime(fileJar);
        }
        catch (IOException | URISyntaxException | RuntimeException ex) {
            return "(unknown: " + ex + ")";
        }
    }
}
