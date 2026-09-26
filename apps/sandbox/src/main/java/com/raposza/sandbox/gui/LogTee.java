// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Standard output and standard error, duplicated into the window without being
 * taken away from the terminal.
 *
 * The stack's own diagnosis does not come through the service's line consumer.
 * `SandboxStack` logs through slf4j and the launcher jar binds slf4j-simple,
 * which writes to standard error; Canton's log problems and the tail of its
 * output arrive on a failed start as the exception message, but everything
 * before that - the port waits, the database creation, the process lines - is
 * only on the stream. A window that showed just the consumer's lines would be
 * quieter than the terminal and would be the wrong tool for the one job it has.
 *
 * <h2>Installed FIRST, and that is not a preference</h2>
 *
 * slf4j-simple resolves `System.err` ONCE, when its first logger is created,
 * and keeps the reference. Installing this after anything has logged leaves
 * every later line going to the original stream and none of it in the window.
 * {@link #install()} is therefore the first statement of the GUI entry point,
 * before the look and feel and before any component exists - which is also why
 * lines are BUFFERED until a pane attaches.
 *
 * Author Claude/bentzn
 */
public final class LogTee {

    /** How many lines are held for a pane that has not attached yet. */
    private static final int CNT_BUFFER_MAX = 500;

    private static final Deque<String> lstPending = new ArrayDeque<>();

    private static Consumer<String> sink;

    private static boolean flagInstalled;


    private LogTee() {
    }


    /**
     * Wraps both streams. Idempotent: a second call does nothing, so this can
     * never nest one tee inside another and print every line twice.
     */
    public static synchronized void install() {
        if (flagInstalled)
            return;
        flagInstalled = true;
        System.setOut(tee(System.out));
        System.setErr(tee(System.err));
    }


    /**
     * @param sinkNew where lines go from now on, or null to buffer again
     */
    public static synchronized void attach(Consumer<String> sinkNew) {
        sink = sinkNew;
        if (sinkNew == null)
            return;
        while (!lstPending.isEmpty()) {
            sinkNew.accept(lstPending.removeFirst());
        }
    }


    /**
     * @param strLine one line, without its terminator
     */
    public static synchronized void accept(String strLine) {
        if (sink != null) {
            sink.accept(strLine);
            return;
        }
        lstPending.addLast(strLine);
        while (lstPending.size() > CNT_BUFFER_MAX) {
            lstPending.removeFirst();
        }
    }


    /**
     * @param origin the stream to keep writing to
     * @return a stream that writes to origin and to {@link #accept}
     */
    private static PrintStream tee(PrintStream origin) {
        OutputStream both = new OutputStream() {

            private final ByteArrayOutputStream bufLine = new ByteArrayOutputStream();


            @Override
            public void write(int nByte) {
                origin.write(nByte);
                if (nByte == '\n') {
                    // Decoded as a whole line rather than byte by byte: a
                    // multi-byte character split across two write() calls
                    // would otherwise reach the pane as two question marks.
                    accept(bufLine.toString(StandardCharsets.UTF_8));
                    bufLine.reset();
                }
                else if (nByte != '\r') {
                    bufLine.write(nByte);
                }
            }


            @Override
            public void flush() {
                origin.flush();
            }
        };
        return new PrintStream(both, true, StandardCharsets.UTF_8);
    }

}
