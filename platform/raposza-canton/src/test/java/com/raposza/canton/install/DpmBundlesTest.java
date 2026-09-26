// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>The manifests are WRITTEN HERE, in the shape the real ones have</h2>
 *
 * Two bundles where the component version equals the bundle version, and one
 * where it does not - which is the case the whole class exists for. The bodies
 * below are cut down from `~/.dpm/cache/sdk/open-source/3.5.5.yaml`; the two
 * patterns see the indentation and nothing else, so the omitted keys change
 * no answer.
 *
 * Author Claude/bentzn
 */
class DpmBundlesTest {

    private static final String STR_DIVERGING = """
            spec:
              components:
                canton-open-source:
                  version: 3.5.12
                daml-script:
                  version: 3.5.5
              version: 3.5.5
            """;

    private static final String STR_MATCHING = """
            spec:
              components:
                canton-enterprise:
                  version: 3.4.11
              version: 3.4.11
            """;

    private static final String STR_NO_CANTON = """
            spec:
              components:
                daml-script:
                  version: 3.3.0
              version: 3.3.0
            """;


    @Test
    void readsTheBundleThatShipsEachCanton(@TempDir Path dirTemp) throws IOException {
        Path dirSdk = dirChannel(dirTemp, "open-source",
                Map.of("3.5.5.yaml", STR_DIVERGING, "3.4.11.yaml", STR_MATCHING));

        Map<String, String> mapBundle = DpmBundles.mapBundle(dirSdk);

        // THE POINT OF THE CLASS: the key is the Canton and the value is the
        // bundle, and on the 3.5 line they are different numbers.
        assertEquals("3.5.5", mapBundle.get("3.5.12"));
        assertEquals("3.4.11", mapBundle.get("3.4.11"));
        assertEquals(2, mapBundle.size());
    }


    @Test
    void skipsAManifestThatNamesNoCanton(@TempDir Path dirTemp) throws IOException {
        Path dirSdk = dirChannel(dirTemp, "open-source",
                Map.of("3.3.0.yaml", STR_NO_CANTON));

        assertTrue(DpmBundles.mapBundle(dirSdk).isEmpty());
    }


    @Test
    void readsEveryChannel(@TempDir Path dirTemp) throws IOException {
        dirChannel(dirTemp, "open-source", Map.of("3.5.5.yaml", STR_DIVERGING));
        Path dirSdk = dirChannel(dirTemp, "enterprise", Map.of("3.4.11.yaml", STR_MATCHING));

        assertEquals(2, DpmBundles.mapBundle(dirSdk).size());
    }


    @Test
    void aMissingCacheIsAnEmptyMapRatherThanAFailure(@TempDir Path dirTemp) {
        assertTrue(DpmBundles.mapBundle(dirTemp.resolve("absent")).isEmpty());
        assertTrue(DpmBundles.mapBundle(null).isEmpty());
    }


    @Test
    void aCantonNoManifestNamesHasNoBundle(@TempDir Path dirTemp) throws IOException {
        Path dirSdk = dirChannel(dirTemp, "open-source", Map.of("3.5.5.yaml", STR_DIVERGING));

        assertEquals("3.5.5", DpmBundles.strBundleFor(dirSdk, "3.5.12"));
        assertNull(DpmBundles.strBundleFor(dirSdk, "3.5.16"));
        assertNull(DpmBundles.strBundleFor(dirSdk, null));
    }


    /** T-3: the dialog's direction - which Canton each installed bundle brings. */
    @Test
    void readsTheCantonEachBundleBrings(@TempDir Path dirTemp) throws IOException {
        Path dirSdk = dirChannel(dirTemp, "open-source",
                Map.of("3.5.5.yaml", STR_DIVERGING, "3.4.11.yaml", STR_MATCHING,
                        "3.3.0.yaml", STR_NO_CANTON));

        Map<String, String> mapCanton = DpmBundles.mapCanton(dirSdk);

        assertEquals("3.5.12", mapCanton.get("3.5.5"));
        assertEquals("3.4.11", mapCanton.get("3.4.11"));
        assertEquals(2, mapCanton.size());
        assertTrue(DpmBundles.mapCanton(null).isEmpty());
    }


    @Test
    void theSdkDirectoryHangsUnderTheDpmRoot() {
        ToolchainRoots roots = new ToolchainRoots(Path.of("/home/x/.daml"),
                Path.of("/home/x/.dpm"), null, null);

        assertEquals(Path.of("/home/x/.dpm/cache/sdk"), DpmBundles.dirSdk(roots));
    }


    /**
     * @param dirTemp the test's own directory, which stands in for the cache
     * @param strChannel the channel directory to write under
     * @param mapFile file name to body
     * @return the sdk root the channel sits in
     */
    private static Path dirChannel(Path dirTemp, String strChannel, Map<String, String> mapFile)
            throws IOException {
        Path dirSdk = dirTemp.resolve("sdk");
        Path dirOut = dirSdk.resolve(strChannel);
        Files.createDirectories(dirOut);
        for (Map.Entry<String, String> entry : mapFile.entrySet()) {
            Files.write(dirOut.resolve(entry.getKey()),
                    entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return dirSdk;
    }

}
