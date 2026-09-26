// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Follows a file that another process is writing, line by line.
 *
 * <h2>Why the pane needed this at all</h2>
 *
 * Canton is started with `--log-file-name`, so its standard output carries
 * five lines - the ports, "ready", a jline warning and the bootstrap - and
 * EVERYTHING else goes to `canton.log`. A pane fed only by the process pump
 * was therefore correct and nearly empty, which reads exactly like a pane that
 * is broken.
 *
 * <h2>Two mechanics that are not preferences</h2>
 *
 * It starts at the END of the file rather than at byte zero: the work
 * directory is reused between runs, and a tail that began at zero would open
 * the pane with the PREVIOUS run's log and no marker saying where it stopped.
 * A file that SHRINKS is treated as a new file and re-read from zero, which is
 * what a truncating restart looks like from here.
 *
 * Bytes are carried across polls, not characters. A chunk boundary can fall
 * inside a UTF-8 sequence, and decoding each chunk on its own turns that into
 * a replacement character in the middle of a word.
 *
 * Author Claude/bentzn
 */
public final class LogTail {

    private static final int N_MS_POLL = 400;

    /** Read at most this much per poll, so a huge write cannot block the UI. */
    private static final int CNT_CHUNK = 256 * 1024;

    private final Supplier<Path> supFile;

    private final Consumer<String> sinkLine;

    private final ByteArrayOutputStream bufCarry = new ByteArrayOutputStream();

    private volatile Thread threadPoll;

    private volatile boolean flagRun;

    private long nPos;


    /**
     * @param supFileNew where the file is, asked each poll because it does not
     *        exist until the process starts and changes between runs
     * @param sinkLineNew where each line goes; called from the poll thread, so
     *        it has to be safe from one - {@link LogPane#append} is
     */
    public LogTail(Supplier<Path> supFileNew, Consumer<String> sinkLineNew) {
        this.supFile = supFileNew;
        this.sinkLine = sinkLineNew;
    }


    /**
     * Starts following. Idempotent: a second call while running does nothing,
     * so a restart cannot leave two threads on one pane.
     */
    public synchronized void start() {
        if (flagRun)
            return;
        flagRun = true;
        bufCarry.reset();
        nPos = sizeNow();

        Thread threadNew = new Thread(this::poll, "sandbox-gui-log-tail");
        threadNew.setDaemon(true);
        threadPoll = threadNew;
        threadNew.start();
    }


    public synchronized void stop() {
        flagRun = false;
        Thread threadHere = threadPoll;
        threadPoll = null;
        if (threadHere != null)
            threadHere.interrupt();
    }


    /**
     * @return the file's length now, or 0 when there is no file yet
     */
    private long sizeNow() {
        Path file = supFile.get();
        if (file == null || !Files.isRegularFile(file))
            return 0L;
        try {
            return Files.size(file);
        }
        catch (Exception ex) {
            return 0L;
        }
    }


    private void poll() {
        while (flagRun) {
            try {
                readOnce();
            }
            catch (Exception ex) {
                // A log that cannot be read this instant is normal: the file
                // is created after the process starts and is written while
                // this reads it. The next poll tries again.
            }
            try {
                Thread.sleep(N_MS_POLL);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }


    private void readOnce() throws Exception {
        Path file = supFile.get();
        if (file == null || !Files.isRegularFile(file))
            return;

        long nLen = Files.size(file);
        if (nLen < nPos) {
            // Truncated under us: a new run into the same path.
            nPos = 0L;
            bufCarry.reset();
        }
        if (nLen <= nPos)
            return;

        int cntRead = (int) Math.min(nLen - nPos, CNT_CHUNK);
        byte[] arrChunk = new byte[cntRead];
        try (RandomAccessFile fileRandom = new RandomAccessFile(file.toFile(), "r")) {
            fileRandom.seek(nPos);
            fileRandom.readFully(arrChunk);
        }
        nPos += cntRead;

        bufCarry.write(arrChunk);
        byte[] arrAll = bufCarry.toByteArray();

        int nLast = -1;
        for (int cntByte = arrAll.length - 1; cntByte >= 0; cntByte--) {
            if (arrAll[cntByte] == '\n') {
                nLast = cntByte;
                break;
            }
        }
        if (nLast < 0)
            return;

        String strWhole = new String(arrAll, 0, nLast, StandardCharsets.UTF_8);
        bufCarry.reset();
        bufCarry.write(arrAll, nLast + 1, arrAll.length - nLast - 1);

        for (String strLine : strWhole.split("\n", -1)) {
            sinkLine.accept(strLine.endsWith("\r")
                    ? strLine.substring(0, strLine.length() - 1)
                    : strLine);
        }
    }

}
