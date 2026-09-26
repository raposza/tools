// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.process;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The `java [-Xmx<n>m] -jar <jar> <arguments>` shape, in one place.
 *
 * Five processes in this project launch an executable jar and every one of them
 * assembled the same first four elements by hand. The shape is not interesting
 * and getting it wrong is expensive: a heap flag after `-jar` is an APPLICATION
 * argument, silently, and the JVM runs with the default while the application
 * receives a token it does not recognise.
 *
 * WHAT THIS DOES NOT DO. There is no working directory and no environment here,
 * because {@link ManagedProcess} already owns both and a second place to set
 * them would be a second answer.
 *
 * THERE IS A CLASSPATH FORM, and it carries its own reason: `-jar` IGNORES
 * `-cp`, so a JVM that has to see a class of this project's BESIDE someone
 * else's executable jar cannot be launched with `-jar` at all. That form names
 * the jar's own main class instead, and is otherwise identical.
 *
 * PATHS ARE MADE ABSOLUTE AND NORMALISED as they go in. A relative path in a
 * command line resolves against the child's working directory, which is not the
 * one that built the command.
 *
 * Author Claude/bentzn
 */
public final class JvmCommand {

    private final Path fileJar;
    private final List<Path> lstEntry;
    private final String strMainClass;
    private final List<String> lstOption = new ArrayList<>();
    private final List<String> lstArg = new ArrayList<>();
    private int nHeapMb;

    private JvmCommand(Path fileJar, List<Path> lstEntry, String strMainClass) {
        this.fileJar = fileJar;
        this.lstEntry = lstEntry;
        this.strMainClass = strMainClass;
    }


    /**
     * @param fileJar the executable jar
     * @return a builder for it
     */
    public static JvmCommand ofJar(Path fileJar) {
        if (fileJar == null)
            throw new IllegalArgumentException("a jar is required");
        return new JvmCommand(fileJar, null, null);
    }


    /**
     * The `-cp` form. The main class is NAMED rather than looked up, because
     * the caller is the one that knows which jar carries it.
     *
     * @param lstEntryNew the classpath in order; the first entry that declares
     *        a class is the one that answers for it
     * @param strMainClassNew the class to run
     * @return a builder for it
     */
    public static JvmCommand ofClasspath(List<Path> lstEntryNew, String strMainClassNew) {
        if (lstEntryNew == null || lstEntryNew.isEmpty())
            throw new IllegalArgumentException("a classpath with at least one entry is required");
        if (strMainClassNew == null || strMainClassNew.isBlank())
            throw new IllegalArgumentException("a main class is required");
        return new JvmCommand(null, List.copyOf(lstEntryNew), strMainClassNew);
    }


    /**
     * @param nHeapMbNew the maximum heap, or 0 to leave the JVM default alone
     * @return this
     * @throws IllegalArgumentException when it is negative
     */
    public JvmCommand heapMb(int nHeapMbNew) {
        if (nHeapMbNew < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMbNew);
        this.nHeapMb = nHeapMbNew;
        return this;
    }


    /**
     * @param strOption a JVM option, placed before `-jar`
     * @return this
     */
    public JvmCommand jvmOption(String strOption) {
        lstOption.add(strOption);
        return this;
    }


    /**
     * @param strArg one application argument
     * @return this
     */
    public JvmCommand arg(String strArg) {
        lstArg.add(strArg);
        return this;
    }


    /**
     * @param strFlag the flag
     * @param strValue its value, as a separate element
     * @return this
     */
    public JvmCommand arg(String strFlag, String strValue) {
        lstArg.add(strFlag);
        lstArg.add(strValue);
        return this;
    }


    /**
     * @param strFlag the flag
     * @param nValue its value
     * @return this
     */
    public JvmCommand arg(String strFlag, int nValue) {
        return arg(strFlag, String.valueOf(nValue));
    }


    /**
     * @param strFlag the flag
     * @param file its value, made absolute
     * @return this
     */
    public JvmCommand arg(String strFlag, Path file) {
        return arg(strFlag, strAbsolute(file));
    }


    /**
     * @param file an application argument that is a path, made absolute
     * @return this
     */
    public JvmCommand argPath(Path file) {
        return arg(strAbsolute(file));
    }


    /**
     * @return the command, ready for a ProcessBuilder
     */
    public List<String> build() {
        List<String> lstOut = new ArrayList<>();
        lstOut.add("java");
        if (nHeapMb > 0)
            lstOut.add("-Xmx" + nHeapMb + "m");
        lstOut.addAll(lstOption);
        if (fileJar != null) {
            lstOut.add("-jar");
            lstOut.add(strAbsolute(fileJar));
        }
        else {
            lstOut.add("-cp");
            lstOut.add(strClasspath(lstEntry));
            lstOut.add(strMainClass);
        }
        lstOut.addAll(lstArg);
        return lstOut;
    }


    /**
     * @param lstEntryHere the classpath entries, in order
     * @return them absolute, joined with this platform's separator
     */
    public static String strClasspath(List<Path> lstEntryHere) {
        StringBuilder sb = new StringBuilder();
        for (Path file : lstEntryHere) {
            if (sb.length() > 0)
                sb.append(File.pathSeparator);
            sb.append(strAbsolute(file));
        }
        return sb.toString();
    }


    /**
     * @param path any path
     * @return it, absolute and normalised, as a string
     */
    public static String strAbsolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

}
