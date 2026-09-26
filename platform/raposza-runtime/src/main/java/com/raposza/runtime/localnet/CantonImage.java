// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import com.raposza.runtime.settings.RaposzaSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where stock Canton lives, and the configuration files that ship only inside
 * the Docker image.
 *
 * WHY THIS EXISTS AT ALL. LocalNet runs TWO jars, not one. The splice namespace
 * runs on `splice-node.jar` out of the Splice bundle; the canton namespace runs
 * on `canton-open-source-<v>.jar`, which is NOT in the bundle. Measured
 * 2026-08-26: `splice-node.jar` accepts the canton namespace's configuration,
 * parses it, logs `Canton started` and instantiates no participant, sequencer or
 * mediator at all. That silence is what cost the previous sessions.
 *
 * `storage.conf`, `parameters.conf` and `additional-config.conf` live only
 * inside `docker/canton:<v>` - the bundle does not carry them - so they are
 * banked next to the jar and staged into the namespace's own /app root, which is
 * what the container's six absolute includes resolve against.
 *
 * `monitoring.conf` is deliberately NOT one of them. It starts a Prometheus
 * exporter on a fixed port, and a second JVM asked for the same port dies with a
 * BindException before any node starts.
 *
 * Author Claude/bentzn
 */
public final class CantonImage {

    /** Overrides the default location of the banked image artefacts. */
    public static final String STR_ENV_DIR = "RAPOSZA_CANTON_IMAGE";

    /**
     * A NAME, not a path. This used to be composed from the home directory
     * here, which made a second definition of the state root - and the two
     * disagree the moment the root follows the platform.
     */
    private static final String STR_DIR_DEFAULT = "canton-image";

    private static final String STR_JAR_PREFIX = "canton-open-source-";

    /**
     * Image-only files staged into the canton /app root. Order is irrelevant;
     * they are referenced by path, not merged in sequence.
     */
    private static final String[] ARR_CONF =
            { "storage.conf", "parameters.conf", "additional-config.conf" };

    private final Path dirImage;
    private final Path fileJarPinned;

    public CantonImage(Path dirImage) {
        this(dirImage, null);
    }


    /**
     * @param dirImage where the image-only conf lives
     * @param fileJarPinned an explicit Canton jar, or null to take the newest in
     *        the image directory
     */
    public CantonImage(Path dirImage, Path fileJarPinned) {
        this.dirImage = dirImage.toAbsolutePath().normalize();
        this.fileJarPinned = fileJarPinned == null
                ? null
                : fileJarPinned.toAbsolutePath().normalize();
    }


    /**
     * THE BINARY AND THE CONF ARE SEPARATE QUESTIONS. A jar from the DPM cache
     * runs against the image's conf perfectly well - measured - so pinning
     * one does not move the other.
     *
     * @param fileJar the Canton jar to run, never null
     * @return the same image with that jar pinned
     */
    public CantonImage withJar(Path fileJar) {
        return new CantonImage(dirImage, fileJar);
    }


    /**
     * @return the image directory named by RAPOSZA_CANTON_IMAGE, or
     *         `canton-image` under the state root
     */
    public static CantonImage fromEnvironment() {
        String strDir = System.getenv(STR_ENV_DIR);
        if (strDir != null && !strDir.isBlank())
            return new CantonImage(Path.of(strDir));
        return new CantonImage(RaposzaSettings.dirHomeDefault()
                .resolve(STR_DIR_DEFAULT));
    }


    public Path dir() {
        return dirImage;
    }


    /**
     * The jar carries its own Main-Class - com.digitalasset.canton.CantonCommunityApp -
     * so it is run with `java -jar` and no classpath is assembled.
     *
     * @return the newest canton-open-source jar in the image directory, or null
     *         when there is none
     */
    public Path fileJar() {
        if (fileJarPinned != null)
            return Files.isRegularFile(fileJarPinned) ? fileJarPinned : null;
        if (!Files.isDirectory(dirImage))
            return null;
        try (Stream<Path> strm = Files.list(dirImage)) {
            return strm.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(STR_JAR_PREFIX))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .max(Comparator.comparing((Path path) -> path.getFileName().toString()))
                    .orElse(null);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("could not list " + dirImage, ex);
        }
    }


    /**
     * @return every image-only configuration file present, which may be fewer
     *         than expected; {@link #lstMissing()} names the rest
     */
    public List<Path> lstConf() {
        List<Path> lstConf = new ArrayList<>();
        for (String strName : ARR_CONF) {
            Path fileConf = dirImage.resolve(strName);
            if (Files.isRegularFile(fileConf))
                lstConf.add(fileConf);
        }
        return lstConf;
    }


    /**
     * @return the image-only files that are not there; an include pointing at
     *         one of them will fail the start
     */
    public List<String> lstMissing() {
        List<String> lstMissing = new ArrayList<>();
        for (String strName : ARR_CONF) {
            if (!Files.isRegularFile(dirImage.resolve(strName)))
                lstMissing.add(strName);
        }
        return lstMissing;
    }


    /**
     * @param dirApp the namespace's staging root
     * @param strName an image-only file name
     * @return it inside the staging root, once staged
     */
    public static Path fileStaged(Path dirApp, String strName) {
        return dirApp.resolve(strName);
    }
}
