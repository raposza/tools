// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which DPM SDK bundle ships which Canton component.
 *
 * <h2>`sdk-version` IS THE BUNDLE, NOT THE CANTON VERSION</h2>
 *
 * Bundle 3.5.5 ships Canton 3.5.12, measured off the manifests. A project built
 * for a Canton component therefore has to name the BUNDLE in its `daml.yaml`,
 * and the only honest source for that is the manifest on disk. Nothing here is
 * a hand-kept table, and nothing here derives one version
 * from the other by a rule.
 *
 * <h2>A PORT OF `probes/petshop/publish.py:mapBundleForCanton`</h2>
 *
 * Same two patterns, same rule for which `version:` is the bundle's own -
 * the LAST one in the file, because the component versions sit above it and are
 * matched separately. That function has produced the store for every install,
 * so it is copied rather than improved on.
 *
 * <h2>An absent or unreadable cache is an EMPTY MAP, never a guess</h2>
 *
 * A machine with no DPM has no bundles, which is a fact about it. The caller
 * decides what an empty map means; here it is not an error, and a manifest that
 * cannot be read is skipped rather than failing the whole reading.
 *
 * Author Claude/bentzn
 */
public final class DpmBundles {

    /** Under the DPM root, beside `components`. */
    public static final String STR_DIR_CACHE = "cache";

    /** Under the cache, one directory per channel, one manifest per bundle. */
    public static final String STR_DIR_SDK = "sdk";

    private static final String STR_GLOB_YAML = "*.yaml";

    /**
     * Any `version:` line. The LAST match in a manifest is the bundle's own -
     * see the type comment.
     */
    private static final Pattern PAT_BUNDLE =
            Pattern.compile("^\\s*version:\\s*([0-9][^\\s#]*)\\s*$", Pattern.MULTILINE);

    /**
     * The Canton component's version, which sits one level deeper than the
     * bundle's own and is the key of the map this class returns.
     */
    private static final Pattern PAT_CANTON =
            Pattern.compile("^\\s{4}canton-[a-z-]+:\\s*$\\s*^\\s*version:\\s*([0-9][^\\s#]*)\\s*$",
                    Pattern.MULTILINE);


    private DpmBundles() {
    }


    /**
     * @param roots what the environment describes; never null
     * @return `&lt;dpm root&gt;/cache/sdk`, whether or not it exists
     */
    public static Path dirSdk(ToolchainRoots roots) {
        if (roots == null)
            throw new IllegalArgumentException("roots are required");
        return roots.dirDpm().resolve(STR_DIR_CACHE).resolve(STR_DIR_SDK);
    }


    /**
     * @param dirSdk the manifest tree, `&lt;dpm root&gt;/cache/sdk`
     * @return canton version to bundle version, empty when there is no cache;
     *         never null
     */
    public static Map<String, String> mapBundle(Path dirSdk) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        if (dirSdk == null || !Files.isDirectory(dirSdk))
            return Collections.unmodifiableMap(mapOut);

        try (DirectoryStream<Path> streamChannel = Files.newDirectoryStream(dirSdk)) {
            for (Path dirChannel : streamChannel) {
                if (Files.isDirectory(dirChannel))
                    readChannel(dirChannel, mapOut::put);
            }
        }
        catch (IOException ex) {
            return Collections.unmodifiableMap(mapOut);
        }
        return Collections.unmodifiableMap(mapOut);
    }


    /**
     * The other direction: which Canton a bundle brings, for the SDK dialog -
     * `todo.md` T-3. Only a bundle on THIS machine has a manifest, so an
     * uninstalled one is absent and the caller says so. The registry holds the
     * manifest as well, but where dpm reads it from is computed in its code and
     * set per machine by `dpm-config.yaml`, so it is not read - D-834.
     *
     * @param dirSdk the manifest tree, `&lt;dpm root&gt;/cache/sdk`
     * @return bundle version to canton version, empty when there is no cache;
     *         never null
     */
    public static Map<String, String> mapCanton(Path dirSdk) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        if (dirSdk == null || !Files.isDirectory(dirSdk))
            return Collections.unmodifiableMap(mapOut);

        try (DirectoryStream<Path> streamChannel = Files.newDirectoryStream(dirSdk)) {
            for (Path dirChannel : streamChannel) {
                if (Files.isDirectory(dirChannel))
                    readChannel(dirChannel, (strCanton, strBundle) -> mapOut.put(strBundle, strCanton));
            }
        }
        catch (IOException ex) {
            return Collections.unmodifiableMap(mapOut);
        }
        return Collections.unmodifiableMap(mapOut);
    }


    /**
     * @param dirSdk the manifest tree
     * @param strCanton the Canton version a project is being built for
     * @return the bundle that ships it, or null when no manifest names it -
     *         which is a machine that has the component and not the compiler
     */
    public static String strBundleFor(Path dirSdk, String strCanton) {
        if (strCanton == null || strCanton.trim().isEmpty())
            return null;
        return mapBundle(dirSdk).get(strCanton.trim());
    }


    /**
     * @param dirChannel one channel directory, e.g. `open-source`
     * @param pairOut told canton version, then bundle version, per manifest
     */
    private static void readChannel(Path dirChannel, BiConsumer<String, String> pairOut) {
        try (DirectoryStream<Path> streamFile =
                Files.newDirectoryStream(dirChannel, STR_GLOB_YAML)) {
            for (Path fileYaml : streamFile) {
                if (!Files.isRegularFile(fileYaml))
                    continue;

                String strBody;
                try {
                    strBody = new String(Files.readAllBytes(fileYaml), StandardCharsets.UTF_8);
                }
                catch (IOException ex) {
                    // One unreadable manifest is one bundle nobody can build
                    // under. The other seventeen are still an answer.
                    continue;
                }

                Matcher matcherCanton = PAT_CANTON.matcher(strBody);
                if (!matcherCanton.find())
                    continue;

                String strBundle = strLastVersion(strBody);
                if (strBundle != null)
                    pairOut.accept(matcherCanton.group(1), strBundle);
            }
        }
        catch (IOException ex) {
            // An unreadable channel contributes nothing.
        }
    }


    /**
     * @param strBody one manifest
     * @return the last `version:` in it, which is the bundle's own, or null
     */
    private static String strLastVersion(String strBody) {
        Matcher matcher = PAT_BUNDLE.matcher(strBody);
        String strLast = null;
        while (matcher.find()) {
            strLast = matcher.group(1);
        }
        return strLast;
    }

}
