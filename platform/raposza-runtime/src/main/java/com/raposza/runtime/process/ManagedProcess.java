// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An external process with a readiness question and a tail of its output.
 *
 * Readiness is asked two ways because one is not enough. A line in the output
 * says the process believes it is up; a file on disk says so independently of
 * whatever the log happens to phrase this release. Canton writes its port file
 * only when the stack is serving, so the file is the stronger signal and the
 * line is the one that arrives first. Either satisfies {@link #awaitReady}.
 *
 * The output is pumped by one thread onto listeners and into a bounded tail.
 * Bounded because an unbounded one is a memory leak with a long fuse: a
 * participant left running overnight produces a great deal of INFO, and the
 * only part of it anyone reads is the end.
 *
 * Author Claude/bentzn
 */
public abstract class ManagedProcess implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ManagedProcess.class);

    private static final int CNT_TAIL_LINES = 500;

    private static final Duration WAIT_POLL = Duration.ofMillis(200);

    private static final Duration WAIT_PUMP_DRAIN = Duration.ofSeconds(5);

    protected final String strName;

    private final Deque<String> dequeTail = new ArrayDeque<>();

    private final List<Consumer<String>> lstListener = new CopyOnWriteArrayList<>();

    private volatile Process proc;

    private volatile Thread threadPump;

    private volatile boolean flagReadyLine;

    /**
     * What {@link #start} actually handed the operating system.
     *
     * KEPT rather than rebuilt on demand. `buildCommand()` is protected, it
     * reads the file system, and it can throw; a surface asking "what was this
     * started with" wants the answer for the process that is RUNNING, not a
     * fresh rendering that might differ from it.
     */
    private volatile List<String> lstCommandStarted = List.of();


    protected ManagedProcess(String strName) {
        if (strName == null || strName.isBlank())
            throw new IllegalArgumentException("a process needs a name");
        this.strName = strName;
    }


    /**
     * @return the command line, fully resolved; never a shell string
     * @throws IOException when the command cannot be assembled, e.g. a missing
     *         working directory
     */
    protected abstract List<String> buildCommand() throws IOException;


    /**
     * @return the directory the process runs in; created if it does not exist
     */
    protected abstract Path workingDir();


    /**
     * @param strLine one line of merged output
     * @return whether this line means the process is serving
     */
    protected abstract boolean isReadyLine(String strLine);


    /**
     * Readiness that does not depend on the log's wording.
     *
     * @return whether an out-of-band signal says the process is serving; false
     *         by default
     */
    protected boolean isReadyOutOfBand() {
        return false;
    }


    public String name() {
        return strName;
    }


    public void addOutputListener(Consumer<String> listener) {
        if (listener != null)
            lstListener.add(listener);
    }


    /**
     * Serving, as opposed to merely alive.
     *
     * The same question {@link #awaitReady} loops on, asked once. It exists
     * because a caller with a status lamp had nothing better than
     * {@link #isRunning}, which is true from the instant the process is
     * spawned - so a participant read as up for the twenty seconds it spends
     * starting, and the things that start AFTER it looked like they had begun
     * before it finished.
     *
     * @return whether the process has reported ready, by its line or by its
     *         out-of-band signal
     */
    public boolean isReady() {
        return flagReadyLine || isReadyOutOfBand();
    }


    /**
     * @return the command line this process was started with, empty before it
     *         has been started
     */
    public List<String> lstCommandStarted() {
        return lstCommandStarted;
    }


    /**
     * The command line as it may be logged and shown. The default is the
     * command itself; a subclass that puts a credential on its command line
     * overrides this and elides it.
     *
     * @param lstCommand the command about to be started
     * @return the same arguments, with any credential elided
     */
    protected List<String> lstCommandForLog(List<String> lstCommand) {
        return lstCommand;
    }


    public boolean isRunning() {
        Process procHere = proc;
        return procHere != null && procHere.isAlive();
    }


    /**
     * @throws ProcessException when the process is already running or will not
     *         start
     */
    public synchronized void start() {
        if (isRunning())
            throw new ProcessException(strName + " is already running");

        flagReadyLine = false;
        synchronized (dequeTail) {
            dequeTail.clear();
        }

        try {
            Path dirWork = workingDir();
            if (!Files.isDirectory(dirWork))
                Files.createDirectories(dirWork);

            List<String> lstCommand = buildCommand();
            this.lstCommandStarted = List.copyOf(lstCommand);
            // THE LOGGED LINE IS THE MASKED ONE. The log and the pane are
            // copied around, and a subclass whose command line carries a
            // token or a secret says so in lstCommandForLog.
            String strLogged = String.join(" ", lstCommandForLog(lstCommand));
            log.info("starting {}: {}", strName, strLogged);
            // To the LISTENERS as well, and not into the tail. A pane that
            // shows a process's output should show what was run to produce
            // it; the tail is the diagnosis of a failed start and stays
            // exactly what the process wrote.
            notifyListeners("$ " + strLogged);

            ProcessBuilder builder = new ProcessBuilder(lstCommand);
            builder.directory(dirWork.toFile());
            builder.redirectErrorStream(true);
            proc = builder.start();
        }
        catch (IOException ex) {
            throw new ProcessException("could not start " + strName, ex);
        }

        threadPump = new Thread(this::pump, "raposza-" + strName + "-out");
        threadPump.setDaemon(true);
        threadPump.start();
    }


    /**
     * @param timeout how long to wait
     * @return true when the process reported ready, false when the timeout
     *         passed
     * @throws ProcessException when the process died while being waited for
     */
    public boolean awaitReady(Duration timeout) {
        Instant tsDeadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(tsDeadline)) {
            if (flagReadyLine || isReadyOutOfBand())
                return true;
            if (!isRunning()) {
                // A process can print its ready line and exit before the loop
                // next looks. Draining the pump first is the difference
                // between reporting what happened and reporting the race.
                joinPump(WAIT_PUMP_DRAIN);
                if (flagReadyLine || isReadyOutOfBand())
                    return true;
                throw new ProcessException(strName + " died during start-up; last output:\n"
                        + String.join("\n", tail(40)));
            }
            sleep(WAIT_POLL);
        }
        return flagReadyLine || isReadyOutOfBand();
    }


    /**
     * Asks, then insists. A participant given no chance to close its database
     * connections leaves them for the next run to time out on.
     *
     * @param timeoutGraceful how long to wait after asking politely
     */
    public synchronized void stop(Duration timeoutGraceful) {
        Process procHere = proc;
        if (procHere == null)
            return;

        if (procHere.isAlive()) {
            log.info("stopping {}", strName);
            procHere.destroy();
            try {
                if (!procHere.waitFor(timeoutGraceful.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("{} ignored the stop request after {}; killing it",
                            strName, timeoutGraceful);
                    procHere.destroyForcibly();
                    procHere.waitFor(10, TimeUnit.SECONDS);
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                procHere.destroyForcibly();
            }
        }

        Thread threadHere = threadPump;
        if (threadHere != null)
            threadHere.interrupt();
        proc = null;
        threadPump = null;
    }


    /**
     * @return the exit code, or null while the process is still running or was
     *         never started
     */
    public Integer exitCode() {
        Process procHere = proc;
        if (procHere == null || procHere.isAlive())
            return null;
        return procHere.exitValue();
    }


    /**
     * @param cntLine how many lines to take from the end
     * @return the last lines of output, oldest first
     */
    public List<String> tail(int cntLine) {
        synchronized (dequeTail) {
            List<String> lstLine = new ArrayList<>(dequeTail);
            int idxFrom = Math.max(0, lstLine.size() - cntLine);
            return List.copyOf(lstLine.subList(idxFrom, lstLine.size()));
        }
    }


    public List<String> tail() {
        return tail(CNT_TAIL_LINES);
    }


    @Override
    public void close() {
        stop(Duration.ofSeconds(30));
    }


    private void pump() {
        Process procHere = proc;
        if (procHere == null)
            return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(procHere.getInputStream(), StandardCharsets.UTF_8))) {
            String strLine = reader.readLine();
            while (strLine != null) {
                record(strLine);
                if (!flagReadyLine && isReadyLine(strLine)) {
                    log.info("{} reported ready", strName);
                    flagReadyLine = true;
                }
                for (Consumer<String> listener : lstListener) {
                    try {
                        listener.accept(strLine);
                    }
                    catch (RuntimeException ex) {
                        log.warn("an output listener for {} threw: {}", strName, ex.toString());
                    }
                }
                strLine = reader.readLine();
            }
        }
        catch (IOException ex) {
            // Expected on stop: the stream closes under the reader.
            log.debug("{} output stream closed: {}", strName, ex.toString());
        }
    }


    /**
     * @param strLine one line for every attached listener; a listener that
     *        throws is logged and skipped, exactly as in the pump
     */
    private void notifyListeners(String strLine) {
        for (Consumer<String> listener : lstListener) {
            try {
                listener.accept(strLine);
            }
            catch (RuntimeException ex) {
                log.warn("an output listener for {} threw: {}", strName, ex.toString());
            }
        }
    }


    private void record(String strLine) {
        synchronized (dequeTail) {
            dequeTail.addLast(strLine);
            while (dequeTail.size() > CNT_TAIL_LINES) {
                dequeTail.removeFirst();
            }
        }
    }


    private void joinPump(Duration timeout) {
        Thread threadHere = threadPump;
        if (threadHere == null)
            return;

        try {
            threadHere.join(timeout.toMillis());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }


    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
