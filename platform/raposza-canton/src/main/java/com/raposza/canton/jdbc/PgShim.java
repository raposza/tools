// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.jdbc;

import com.raposza.canton.install.CantonRuntimeJar;
import com.raposza.canton.install.HostPlatform;
import com.raposza.runtime.process.JvmCommand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Where {@link WinPgDataSource} comes from, and what has to be true before
 * Canton is told to use it.
 *
 * <h2>A directory, not this application's jar</h2>
 *
 * The class has to be visible to the Canton JVM, which is a child process
 * running someone else's jar. The obvious way to arrange that - putting this
 * application's own jar on that classpath - would also put its gRPC, protobuf
 * and Netty in front of Canton's, and a stack that fails on a version skew is
 * a worse failure than the one being fixed.
 *
 * So exactly two class files are copied out to a directory of their own, that
 * directory goes on the classpath ahead of the Canton jar, and nothing else
 * follows them. {@link WinPgDataSource} depends on the PostgreSQL driver and on
 * the JDK, and Canton's own jar carries the driver.
 *
 * <h2>The decision is taken once and read twice</h2>
 *
 * The storage overlay NAMES the class and the launcher MAKES IT REACHABLE, and
 * the two must agree: a configuration naming a class Canton cannot load fails
 * harder than the defect it answers. {@link #extract(Path)} is called before
 * the overlay is written, and {@link #flagActive(Path, Path)} is what each
 * launcher asks afterwards - the extracted directory is the evidence, so a
 * platform that never extracted it launches exactly as before.
 *
 * Author Claude/bentzn
 */
public final class PgShim {

    /** The stock driver's own DataSource, and the default everywhere. */
    public static final String STR_CLASS_STOCK = "org.postgresql.ds.PGSimpleDataSource";

    /** Under the work directory, beside the configuration overlays. */
    public static final String STR_DIR = "pgshim";

    /** The package, as a path, because a classpath directory is laid out flat. */
    private static final String STR_PACKAGE_PATH = "com/raposza/canton/jdbc";

    /**
     * BOTH of them. The handler is an inner class and is loaded the first time
     * a connection is wrapped, which is long after the launch that would have
     * been the place to notice it was missing.
     */
    private static final String[] ARR_RESOURCE = { "WinPgDataSource.class",
            "WinPgDataSource$HandlerCall.class" };


    private PgShim() {
    }


    /**
     * @param platform the platform the stack will run on; never null
     * @return whether the shim is needed there at all
     */
    public static boolean flagNeeded(HostPlatform platform) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");
        return platform.flagWindows();
    }


    /**
     * @param platform the platform the stack will run on
     * @return the class every storage block should name there
     */
    public static String strDataSourceClass(HostPlatform platform) {
        return flagNeeded(platform) ? WinPgDataSource.class.getName() : STR_CLASS_STOCK;
    }


    /**
     * @param dirWork the stack's work directory
     * @return where the class files go, absolute
     */
    public static Path dirShim(Path dirWork) {
        if (dirWork == null)
            throw new IllegalArgumentException("a work directory is required");
        return dirWork.toAbsolutePath().normalize().resolve(STR_DIR);
    }


    /**
     * Copies the class files out of whatever this application is running from -
     * a jar or a classes directory, both work - into a directory of their own.
     *
     * @param dirWork the stack's work directory
     * @return the directory to put on Canton's classpath
     * @throws IOException when a class file cannot be found or cannot be
     *         written; a start on Windows must fail here rather than name a
     *         class Canton will not find
     */
    public static Path extract(Path dirWork) throws IOException {
        Path dirOut = dirShim(dirWork);
        Path dirPackage = dirOut.resolve(STR_PACKAGE_PATH);
        Files.createDirectories(dirPackage);
        for (String strResource : ARR_RESOURCE) {
            try (InputStream in = PgShim.class.getResourceAsStream(strResource)) {
                if (in == null)
                    throw new IOException(strResource + " is not on this application's classpath");
                Files.copy(in, dirPackage.resolve(strResource),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return dirOut;
    }


    /**
     * @param fileRuntime the Canton jar
     * @return its manifest main class, or null when it declares none - in
     *         which case the `-cp` form cannot be built and the shim cannot be
     *         used
     */
    public static String strMainClass(Path fileRuntime) {
        return CantonRuntimeJar.strMainClass(fileRuntime);
    }


    /**
     * Whether this launch should use the `-cp` form and the shim class.
     *
     * @param dirWork the stack's work directory
     * @param fileRuntime the Canton jar
     * @return true only on Windows, with the class files already extracted and
     *         a main class to name
     */
    public static boolean flagActive(Path dirWork, Path fileRuntime) {
        return flagNeeded(HostPlatform.ofDefaults())
                && strMainClass(fileRuntime) != null
                && Files.isDirectory(dirShim(dirWork));
    }


    /**
     * @param dirWork the stack's work directory
     * @param fileRuntime the Canton jar
     * @return the classpath, shim first so its class is the one that answers
     */
    public static String strClasspath(Path dirWork, Path fileRuntime) {
        return JvmCommand.strClasspath(List.of(dirShim(dirWork), fileRuntime));
    }
}
