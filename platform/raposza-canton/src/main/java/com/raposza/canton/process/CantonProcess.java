// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.runtime.process.JvmCommand;
import com.raposza.runtime.process.ManagedProcess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Canton process of either line, and the diagnostics that reading its log
 * correctly turned out to require.
 *
 * Extracted when the 2.x launcher arrived, because everything here is a
 * property of Canton's logging rather than of a launcher. It cost three failed
 * runs to get right on 3.x and duplicating it for 2.x would have meant getting
 * it wrong again independently.
 *
 * Author Claude/bentzn
 */
public abstract class CantonProcess extends ManagedProcess {

    protected static final String STR_LOG_FILE = "canton.log";

    private final Path dirWork;


    /**
     * @param strName the process label used in logs and messages
     * @param dirWork where the log and Canton's own scratch go
     */
    protected CantonProcess(String strName, Path dirWork) {
        super(strName);
        if (dirWork == null)
            throw new IllegalArgumentException("dirWork is required");
        this.dirWork = dirWork.toAbsolutePath().normalize();
    }


    @Override
    protected Path workingDir() {
        return dirWork;
    }


    public Path fileLog() {
        return dirWork.resolve(STR_LOG_FILE);
    }


    /**
     * Every `-c` configuration file this process was launched with, in order.
     *
     * Declared here rather than on each launcher because a caller holding a
     * {@link CantonProcess} has no other way to ask what configuration it was
     * given, and that question is the same one whichever launcher answered it.
     * All three subclasses already answered it before this declaration existed.
     *
     * @return those files, earliest first; a later one wins where two set the
     *         same key
     */
    public abstract List<Path> lstFileConf();


    /**
     * The lines in Canton's log that look like a problem, oldest first.
     *
     * A plain tail of that file is useless on a failed start, and this is the
     * second time it has cost a run. When a node fails, Canton logs the reason
     * and then dumps every thread in the JVM - hundreds of lines - INTO THE
     * SAME FILE. The last two hundred lines are therefore all dump, and the
     * reason has already scrolled past.
     *
     * So the dump is removed rather than tailed around: stack frames and
     * thread headers are dropped, what remains is filtered to log levels and
     * exception lines, and the FIRST of those are what get reported. First,
     * because on a failing start-up everything after the first error is the
     * shutdown it caused.
     *
     * @param cntFirst how many problem lines to take from the start
     * @param cntLast how many to take from the end
     * @return those lines, with a marker where the middle was elided
     */
    public List<String> logProblems(int cntFirst, int cntLast) {
        List<String> lstSevere = new ArrayList<>();
        List<String> lstWarn = new ArrayList<>();
        for (String strLine : readLog()) {
            if (isStackFrame(strLine))
                continue;
            if (isSevere(strLine))
                lstSevere.add(strLine);
            else if (strLine.contains("WARN"))
                lstWarn.add(strLine);
        }

        // Warnings only when there is nothing worse. A Canton start-up produces
        // plenty of them - a retrying database connection is a WARN - and
        // mixing them in by position pushes the one ERROR out of the window
        // that this method exists to keep.
        List<String> lstProblem = lstSevere.isEmpty() ? lstWarn : lstSevere;
        if (lstProblem.isEmpty())
            return List.of("(no ERROR, WARN or exception lines in " + fileLog() + ")");
        if (lstProblem.size() <= cntFirst + cntLast)
            return List.copyOf(lstProblem);

        List<String> lstOut = new ArrayList<>(lstProblem.subList(0, cntFirst));
        lstOut.add("... " + (lstProblem.size() - cntFirst - cntLast) + " further problem lines ...");
        lstOut.addAll(lstProblem.subList(lstProblem.size() - cntLast, lstProblem.size()));
        return List.copyOf(lstOut);
    }


    /**
     * @param cntLine how many lines to take from the end
     * @return the last lines of Canton's log, oldest first
     */
    public List<String> logTail(int cntLine) {
        List<String> lstLine = readLog();
        if (lstLine.isEmpty())
            return List.of("(no Canton log at " + fileLog() + ")");

        int idxFrom = Math.max(0, lstLine.size() - cntLine);
        return List.copyOf(lstLine.subList(idxFrom, lstLine.size()));
    }


    /**
     * A thread-dump entry: a header, or an indented frame with the
     * parenthesised source location a stack trace always carries.
     */
    protected static boolean isStackFrame(String strLine) {
        String strTrimmed = strLine.strip();
        if (strTrimmed.startsWith("Thread[") || strTrimmed.startsWith("at "))
            return true;
        if (!strLine.startsWith(" ") && !strLine.startsWith("\t"))
            return false;
        return strTrimmed.endsWith(")") && strTrimmed.contains("(");
    }


    protected static boolean isSevere(String strLine) {
        return strLine.contains("ERROR")
                || strLine.contains("Exception")
                || strLine.contains("Caused by");
    }


    protected static String absolute(Path path) {
        return JvmCommand.strAbsolute(path);
    }


    private List<String> readLog() {
        Path file = fileLog();
        if (!Files.isRegularFile(file))
            return List.of();

        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            return List.of("(could not read " + file + ": " + ex + ")");
        }
    }
}
