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
 * The Aviation Maintenance fixture: where its source is, what builds it, and
 * where the DAR lands.
 *
 * <h2>The source ships INSIDE the application</h2>
 *
 * `/fixtures/aviation/daml/Main.daml` is a resource of this module and is the
 * ONLY copy. The fixture has to reach a machine that has nothing but the
 * released application, so it travels in the jar and is staged out of it on
 * demand.
 *
 * <h2>A staged project per version and edition</h2>
 *
 * `~/.raposza/fixtures/aviation/&lt;version&gt;-&lt;edition&gt;`, which is the key
 * the run directories, the profiles and the snapshots already use. One
 * DAR per Canton, because a DAR is compiled against one LF version and the
 * package id is a hash of what that compiler emitted.
 *
 * <h2>THE TOOLCHAIN IS CHOSEN BY LINE, and `sdk-version` is the BUNDLE</h2>
 *
 * 2.x builds with `daml build` under an `sdk-version` that IS the Canton
 * version. Every 3.x install builds with `dpm build` under the bundle that
 * ships that Canton, which {@link com.raposza.canton.install.DpmBundles} reads
 * off the manifests rather than deriving - bundle 3.5.5 ships Canton 3.5.12.
 * A version no manifest names cannot be built for, and that is answered as null
 * rather than as a plausible number.
 *
 * <h2>Nothing here starts a process</h2>
 *
 * It stages, it names a command line and it says where the output will be. The
 * running is {@link DamlScriptRun}'s and the window's, so a class that decides
 * paths can be tested without a compiler on the machine.
 *
 * Author Claude/bentzn
 */
public final class AviationFixture {

    /** What `daml.yaml.template` calls the package, and the store directory. */
    public static final String STR_NAME = "aviation";

    /** What the template pins the package version to. */
    public static final String STR_VERSION = "0.0.1";

    /** The source, as a resource of this module. */
    public static final String PATH_SOURCE = "/fixtures/aviation/daml/Main.daml";

    /** The project file, with one token left to substitute. */
    public static final String PATH_TEMPLATE = "/fixtures/aviation/daml.yaml.template";

    /** What the template carries where the SDK version goes. */
    public static final String STR_TOKEN_SDK = "SDK_VERSION";

    /** Phase one, under a `participant_admin` token, with `--output-file`. */
    public static final String STR_SCRIPT_PARTIES = "Main:setupParties";

    /** Phase two, under a `superuser` token, with `--input-file`. */
    public static final String STR_SCRIPT_LEDGER = "Main:setupLedger";

    /** Both halves, for a participant that checks nothing. */
    public static final String STR_SCRIPT_SETUP = "Main:setup";

    /** The user phase two submits as; created by phase one. */
    public static final String STR_USER_SUPER = "superuser";

    /**
     * The read-only user, for a browser opened on the fixture. NOT
     * `inspector`: the Inspector is one of the seven roles and acts.
     */
    public static final String STR_USER_AUDITOR = "auditor";

    /** Where the compiler puts the DAR, under the project. */
    public static final String STR_DIR_DIST = ".daml";

    private static final String STR_DIR_DIST_INNER = "dist";

    /** The 2.x toolchain. */
    public static final String STR_BIN_ASSISTANT = "daml";

    /** Every 3.x install, including the two with an assistant tree beside them. */
    public static final String STR_BIN_DPM = "dpm";

    private static final String STR_CMD_BUILD = "build";

    private static final String STR_DIR_SOURCE = "daml";


    private AviationFixture() {
    }


    /**
     * @return `~/.raposza/fixtures/aviation`, whether or not it exists
     */
    public static Path dirRootDefault() {
        return RaposzaSettings.current().dirFixture(STR_NAME);
    }


    /**
     * @param dirRoot the fixture store, `~/.raposza/fixtures/aviation`
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
     * @param strCanton the Canton version the DAR is for
     * @param mapBundle canton version to bundle version, as read off the DPM
     *        manifests
     * @return what `daml.yaml` should name, or null when this machine cannot
     *         build for that Canton
     */
    public static String strSdkFor(String strCanton, Map<String, String> mapBundle) {
        if (strCanton == null || strCanton.trim().isEmpty())
            return null;

        String strTrim = strCanton.trim();
        // THE 2.x LINE NAMES ITSELF. dpm does not go back that far, and the
        // assistant resolves an SDK whose version is the Canton version.
        if (strTrim.startsWith("2."))
            return strTrim;
        return mapBundle == null ? null : mapBundle.get(strTrim);
    }


    /**
     * @param strSdk what `daml.yaml` names
     * @return the executable that can drive it; never null
     */
    public static String strBin(String strSdk) {
        return strSdk != null && strSdk.startsWith("2.") ? STR_BIN_ASSISTANT : STR_BIN_DPM;
    }


    /**
     * @param strSdk what `daml.yaml` names
     * @return the build command, to be run INSIDE the project; never null
     */
    public static List<String> lstBuild(String strSdk) {
        return List.of(strBin(strSdk), STR_CMD_BUILD);
    }


    /**
     * Writes the project out of the jar, replacing whatever was there.
     *
     * REGENERATED EVERY TIME: a fixture edited in place to suit a later
     * question stops being reproducible, and a store whose DARs were built
     * from three revisions of the source is worse than no store because it
     * still looks like one.
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
        try (InputStream stream = AviationFixture.class.getResourceAsStream(strPath)) {
            if (stream == null)
                throw new IOException("this build carries no " + strPath);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    /**
     * @return the scripts this fixture publishes, in the order they run
     */
    /**
     * Removes a staged project and everything under it.
     *
     * THE FIXTURE DOES NOT OUTLIVE THE WINDOW THAT ASKED FOR IT. The source is
     * a resource of this jar, so nothing is lost by removing what was staged
     * from it - the next window stages it again. What the removal costs is the
     * build, which is a minute of compiler.
     *
     * @param dirProject the staging project; an absent one is not an error
     * @throws IOException when part of the tree cannot be removed
     */
    public static void deleteProject(Path dirProject) throws IOException {
        if (dirProject == null || !Files.exists(dirProject))
            return;

        // DEEPEST FIRST. A directory cannot be removed while it holds
        // anything, and `.daml` holds the whole of the compiler's output.
        List<Path> lstPath;
        try (Stream<Path> streamWalk = Files.walk(dirProject)) {
            lstPath = streamWalk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path pathHere : lstPath) {
            Files.deleteIfExists(pathHere);
        }
    }


    public static List<String> lstScript() {
        return List.of(STR_SCRIPT_PARTIES, STR_SCRIPT_LEDGER);
    }

}
