// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixture is a trimmed copy of a real listing, noise included. Every tag
 * shape asserted here occurs in one.
 *
 * Author Claude/bentzn
 */
class DpmCatalogueTest {

    private static final String STR_LISTING = String.join("\n",
            "3.4",
            "3.4.11",
            "3.4.11.generic",
            "3.5",
            "3.5.1-rc1",
            "3.5.1-rc1.generic",
            "3.5.14",
            "3.5.14.generic",
            "3.5.14-ad-hoc.20260815.19194.0.v5cdf407c",
            "3.5.14-snapshot.20260819.19183.0.va7a6d3ae",
            "3.5.16",
            "3.5.16.generic",
            "3.6",
            "3.6.0-snapshot.20260907.20233.0.ve241688b",
            "3.7",
            "3.7.0-snapshot.20260907.20269.0.v7ba1a545",
            "devnet",
            "mainnet",
            "testnet");


    @Test
    void onlyStablePatchesSurviveTheFilter() {
        List<VersionId> lstVersion = DpmCatalogue.lstStable(STR_LISTING);

        assertEquals(List.of(VersionId.of(3, 5, 16), VersionId.of(3, 5, 14),
                VersionId.of(3, 4, 11)), lstVersion);
    }


    /**
     * A floating line tag would resolve to whatever is newest under it, and
     * both 3.6 and 3.7 are published as lines with no stable patch under
     * either - so offering one would offer a snapshot under a stable-looking
     * name.
     */
    @Test
    void aLineWithNoStablePatchOffersNothing() {
        List<VersionId> lstVersion = DpmCatalogue.lstStable(STR_LISTING);

        for (VersionId version : lstVersion) {
            assertTrue(version.major() == 3 && version.minor() <= 5,
                    "a 3.6 or 3.7 tag survived: " + version);
        }
    }


    @Test
    void anEmptyOrAbsentListingIsAnEmptyListRatherThanAFailure() {
        assertTrue(DpmCatalogue.lstStable(null).isEmpty());
        assertTrue(DpmCatalogue.lstStable("").isEmpty());
        assertTrue(DpmCatalogue.lstStable("devnet\nmainnet\n").isEmpty());
    }


    @Test
    void carriageReturnsAndBlanksAreTolerated() {
        assertEquals(List.of(VersionId.of(3, 5, 14)),
                DpmCatalogue.lstStable("\r\n  3.5.14  \r\n\r\n3.5.14.generic\r\n"));
    }


    @Test
    void theComponentSegmentIsInTheReference() {
        assertEquals("oci://europe-docker.pkg.dev/da-images/public-all/components/"
                + "canton-open-source", DpmCatalogue.strUri(DpmCatalogue.STR_COMPONENT_OPEN_SOURCE));
        assertEquals("oci://europe-docker.pkg.dev/da-images/public-all/components/"
                        + "canton-open-source:3.5.16",
                DpmCatalogue.strUriAt(DpmCatalogue.STR_COMPONENT_OPEN_SOURCE,
                        VersionId.of(3, 5, 16)));
    }

}
