// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Author Claude/bentzn
 */
class ScribeBannerTest {

    private static final String STR_BANNER_357 = """
            scribe, version: v3.5.7
            daml-sdk.version: 3.5.2
            postgres-document.schema: 041
            """;

    private static final String STR_BANNER_341 = """
            scribe, version: v3.4.1
            daml-sdk.version: 3.4.9
            postgres-document.schema: 034
            """;


    @Test
    void readsAllThreeNumbers() {
        ScribeBanner banner = ScribeBanner.parse(STR_BANNER_357);
        assertEquals(VersionId.of(3, 5, 7), banner.version());
        assertEquals("041", banner.strSchemaRevision());
        assertEquals("3.5.2", banner.strDamlSdkVersion());
        assertEquals("3.5", banner.cantonLine());
    }


    @Test
    void keepsTheSchemaRevisionAsWritten() {
        // Leading zero preserved: this is a database identity, not a quantity.
        assertEquals("034", ScribeBanner.parse(STR_BANNER_341).strSchemaRevision());
    }


    @Test
    void theSdkVersionIsItsOwnNumber() {
        // 3.4.1 reports daml-sdk 3.4.9 and arrived from SDK bundle 3.4.11.
        // Three numbers, none derivable from another.
        ScribeBanner banner = ScribeBanner.parse(STR_BANNER_341);
        assertEquals("3.4.9", banner.strDamlSdkVersion());
        assertEquals(VersionId.of(3, 4, 1), banner.version());
    }


    @Test
    void toleratesAMissingOptionalLine() {
        ScribeBanner banner = ScribeBanner.parse("scribe, version: v3.4.3\n");
        assertEquals(VersionId.of(3, 4, 3), banner.version());
        assertNull(banner.strSchemaRevision());
        assertNull(banner.strDamlSdkVersion());
    }


    @Test
    void refusesOutputWithNoVersionLine() {
        assertThrows(InstallException.class, () -> ScribeBanner.parse("Usage: scribe COMMAND\n"));
        assertThrows(InstallException.class, () -> ScribeBanner.parse(null));
    }
}
