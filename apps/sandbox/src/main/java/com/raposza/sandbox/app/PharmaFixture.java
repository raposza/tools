// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.runtime.settings.RaposzaSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Pharma fixture: where its source is, what builds it, and where the DAR
 * lands.
 *
 * <h2>The same store rule as Aviation, and a different ledger</h2>
 *
 * The source ships inside the application and is staged per
 * `&lt;version&gt;-&lt;edition&gt;`, because a DAR is compiled against one LF
 * version and the package id is a hash of what that compiler emitted. What
 * differs is everything past the build: this fixture is for the LocalNetND
 * topology, where two organizational participants hold the two organizations -
 * `fixture_pharma.md`, and his correction of 2026-09-21 that the Super Validator
 * is infrastructure rather than an organization.
 *
 * <h2>THE DAR DOES NOT GO THROUGH THE DARs TAB</h2>
 *
 * On that topology it cannot: {@link com.raposza.sandbox.gui.SandboxWindow}'s
 * admin port is read off the Sandbox's own service and answers 0 there, and the
 * LocalNet runner uploads nothing. The route this fixture uses is the JSON
 * Ledger API on each participant, MEASURED 2026-09-22 by `probes/pharma` -
 * `POST /v2/dars?vetAllPackages=true`, accepted by both participants in a second
 * each, same bytes and one package id because all three roles run inside one
 * Canton jar.
 *
 * <h2>NO daml-script</h2>
 *
 * The population is submitted over the same JSON route by {@link PharmaLedger},
 * one participant at a time. The script runner has never been driven against a
 * LocalNetND participant and a fixture built on an unmeasured mechanism is a
 * fixture that may have to be thrown away; the route in use is the one the probe
 * proved end to end. `daml-script` is therefore not a dependency of the project,
 * which also takes a network resolve out of the build.
 *
 * Author Claude/bentzn
 */
public final class PharmaFixture {

    /** What `daml.yaml.template` calls the package, and the store directory. */
    public static final String STR_NAME = "pharma";

    /** What the template pins the package version to. */
    public static final String STR_VERSION = "0.0.1";

    /** The source, as a resource of this module. */
    public static final String PATH_SOURCE = "/fixtures/pharma/daml/Main.daml";

    /** The project file, with one token left to substitute. */
    public static final String PATH_TEMPLATE = "/fixtures/pharma/daml.yaml.template";

    /** What the template carries where the SDK version goes. */
    public static final String STR_TOKEN_SDK = "SDK_VERSION";

    /** Where the compiler puts the DAR, under the project. */
    public static final String STR_DIR_DIST = ".daml";

    private static final String STR_DIR_DIST_INNER = "dist";

    private static final String STR_DIR_SOURCE = "daml";


    private PharmaFixture() {
    }


    /**
     * @return `~/.raposza/fixtures/pharma`, whether or not it exists
     */
    public static Path dirRootDefault() {
        return RaposzaSettings.current().dirFixture(STR_NAME);
    }


    /**
     * @param dirRoot the fixture store, `~/.raposza/fixtures/pharma`
     * @param strKey the `&lt;version&gt;-&lt;edition&gt;` string
     * @return that version's staging project, whether or not it exists
     */
    public static Path dirProject(Path dirRoot, String strKey) {
        if (dirRoot == null)
            throw new IllegalArgumentException("a store root is required");
        if (strKey == null || strKey.trim().isEmpty())
            throw new IllegalArgumentException("a version key is required");
        return dirRoot.resolve(strKey.trim());
    }


    /**
     * @param dirProject the staging project
     * @return where `build` will write the DAR
     */
    public static Path fileDar(Path dirProject) {
        if (dirProject == null)
            throw new IllegalArgumentException("a project directory is required");
        return dirProject.resolve(STR_DIR_DIST).resolve(STR_DIR_DIST_INNER)
                .resolve(STR_NAME + "-" + STR_VERSION + ".dar");
    }


    /**
     * THE TOOLCHAIN RULE IS AVIATION'S, and it is called rather than copied:
     * which SDK builds for which Canton is one fact and two spellings of it
     * would drift the day a line moves.
     *
     * @param strCanton the Canton version the DAR is for
     * @param mapBundle canton version to bundle version, off the DPM manifests
     * @return what `daml.yaml` should name, or null when this machine cannot
     *         build for that Canton
     */
    public static String strSdkFor(String strCanton, Map<String, String> mapBundle) {
        return AviationFixture.strSdkFor(strCanton, mapBundle);
    }


    /**
     * @param strSdk what `daml.yaml` names
     * @return the build command, to be run INSIDE the project; never null
     */
    public static List<String> lstBuild(String strSdk) {
        return AviationFixture.lstBuild(strSdk);
    }


    /**
     * Writes the project out of the jar, replacing whatever was there.
     *
     * REGENERATED EVERY TIME, for Aviation's reason: a fixture edited in place
     * to suit a later question stops being reproducible.
     *
     * @param dirProject where the project goes; created if absent
     * @param strSdk what `daml.yaml` names
     * @throws IOException when the resource is missing or the tree cannot be
     *         written
     */
    public static void stage(Path dirProject, String strSdk) throws IOException {
        if (dirProject == null)
            throw new IllegalArgumentException("a project directory is required");
        if (strSdk == null || strSdk.trim().isEmpty())
            throw new IllegalArgumentException("an sdk version is required");

        Path dirSource = dirProject.resolve(STR_DIR_SOURCE);
        Files.createDirectories(dirSource);

        Files.write(dirSource.resolve("Main.daml"),
                strResource(PATH_SOURCE).getBytes(StandardCharsets.UTF_8));

        String strYaml = strResource(PATH_TEMPLATE).replace(STR_TOKEN_SDK, strSdk.trim());
        Files.write(dirProject.resolve(DamlScriptSpec.STR_FILE_YAML),
                strYaml.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * @return the fixture source, for anything that wants to show it
     * @throws IOException when the jar does not carry it
     */
    public static String strSource() throws IOException {
        return strResource(PATH_SOURCE);
    }


    /**
     * @param strPath a resource of this module
     * @return its content
     * @throws IOException when it is not there, which means a jar built without
     *         the fixture rather than a machine missing something
     */
    private static String strResource(String strPath) throws IOException {
        try (InputStream stream = PharmaFixture.class.getResourceAsStream(strPath)) {
            if (stream == null)
                throw new IOException("this build carries no " + strPath);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    /**
     * Removes a staged project and everything under it.
     *
     * @param dirProject the staging project; an absent one is not an error
     * @throws IOException when part of the tree cannot be removed
     */
    public static void deleteProject(Path dirProject) throws IOException {
        if (dirProject == null || !Files.exists(dirProject))
            return;

        List<Path> lstPath;
        try (Stream<Path> streamWalk = Files.walk(dirProject)) {
            lstPath = streamWalk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path pathHere : lstPath) {
            Files.deleteIfExists(pathHere);
        }
    }

}
