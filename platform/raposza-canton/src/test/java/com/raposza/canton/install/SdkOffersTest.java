// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class SdkOffersTest {

    /** The 3.4 patches are published both ways and the DPM row is the one kept. */
    @Test
    void dpmWinsAVersionPublishedBothWays() {
        List<SdkOffer> lstMerged = SdkOffers.lstMerged(
                List.of(new SdkOffer(VersionId.of(3, 4, 11), SdkChannel.ASSISTANT, false)),
                List.of(new SdkOffer(VersionId.of(3, 4, 11), SdkChannel.DPM, true)));

        assertEquals(1, lstMerged.size());
        assertEquals(SdkChannel.DPM, lstMerged.get(0).channel());
        assertTrue(lstMerged.get(0).flagInstalled());
    }


    @Test
    void theMergedListIsOldestFirst() {
        List<SdkOffer> lstMerged = SdkOffers.lstMerged(
                List.of(new SdkOffer(VersionId.of(2, 10, 6), SdkChannel.ASSISTANT, false),
                        new SdkOffer(VersionId.of(2, 8, 12), SdkChannel.ASSISTANT, true)),
                List.of(new SdkOffer(VersionId.of(3, 5, 7), SdkChannel.DPM, false)));

        assertEquals(List.of(VersionId.of(2, 8, 12), VersionId.of(2, 10, 6),
                        VersionId.of(3, 5, 7)),
                lstMerged.stream().map(SdkOffer::version).toList());
    }


    /** With no DPM the assistant's whole set survives, 3.4 included. */
    @Test
    void anEmptyDpmSideKeepsEverythingTheAssistantHas() {
        List<SdkOffer> lstAssistant = SdkOffers.lstAssistant(HostPlatform.LINUX_X64,
                Set.of(VersionId.of(2, 10, 4)));

        List<SdkOffer> lstMerged = SdkOffers.lstMerged(lstAssistant, List.of());

        assertEquals(lstAssistant.size(), lstMerged.size());
        assertTrue(lstMerged.contains(
                new SdkOffer(VersionId.of(3, 4, 11), SdkChannel.ASSISTANT, false)));
        assertTrue(lstMerged.contains(
                new SdkOffer(VersionId.of(2, 10, 4), SdkChannel.ASSISTANT, true)));
    }


    /** An installed set is read against the assistant's own tree only. */
    @Test
    void theAssistantSideCarriesWhatIsUnderItsRoot() {
        List<SdkOffer> lstAssistant = SdkOffers.lstAssistant(HostPlatform.LINUX_X64,
                Set.of(VersionId.of(2, 9, 6)));

        assertTrue(lstAssistant.stream().anyMatch(offer ->
                offer.version().equals(VersionId.of(2, 9, 6)) && offer.flagInstalled()));
        assertFalse(lstAssistant.stream().anyMatch(offer ->
                offer.version().equals(VersionId.of(2, 9, 7)) && offer.flagInstalled()));
    }


    /** An ARM machine has no 2.x assets and still has the 3.4 ones. */
    @Test
    void armKeepsTheThreeLineAndLosesTheTwo() {
        List<SdkOffer> lstAssistant = SdkOffers.lstAssistant(HostPlatform.LINUX_ARM64,
                Set.of());

        assertFalse(lstAssistant.isEmpty());
        for (SdkOffer offer : lstAssistant) {
            assertEquals(3, offer.version().major());
        }
    }

}
