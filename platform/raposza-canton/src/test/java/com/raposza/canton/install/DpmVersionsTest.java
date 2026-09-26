// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The document below is the head of what `dpm version --all -o json` printed on
 * a workstation, including the release candidate it opens with.
 *
 * Author Claude/bentzn
 */
class DpmVersionsTest {

    private static final String STR_JSON = """
            [
                {
                    "version": "3.4.0-rc2",
                    "remote": true
                },
                {
                    "version": "3.4.4",
                    "installed": true,
                    "remote": true
                },
                {
                    "version": "3.5.7",
                    "remote": true
                }
            ]
            """;


    @Test
    void theCandidateIsDroppedAndTheStablePatchesAreKept() {
        List<SdkOffer> lstOffer = DpmVersions.lstOffer(STR_JSON);

        assertEquals(2, lstOffer.size());
        assertEquals(VersionId.of(3, 4, 4), lstOffer.get(0).version());
        assertEquals(VersionId.of(3, 5, 7), lstOffer.get(1).version());
    }


    /** The command omits the key for a bundle it has not got. */
    @Test
    void aMissingInstalledKeyIsNotInstalled() {
        List<SdkOffer> lstOffer = DpmVersions.lstOffer(STR_JSON);

        assertTrue(lstOffer.get(0).flagInstalled());
        assertFalse(lstOffer.get(1).flagInstalled());
    }


    @Test
    void everyOfferCarriesTheChannelThatWouldInstallIt() {
        for (SdkOffer offer : DpmVersions.lstOffer(STR_JSON)) {
            assertEquals(SdkChannel.DPM, offer.channel());
        }
    }


    /** A machine with no DPM prints nothing, which is not an error. */
    @Test
    void nothingIsAnEmptyList() {
        assertTrue(DpmVersions.lstOffer(null).isEmpty());
        assertTrue(DpmVersions.lstOffer("").isEmpty());
        assertTrue(DpmVersions.lstOffer("[]").isEmpty());
    }


    /** THE ROW THIS CLOSES: a nested object hid its entry from the regex. */
    @Test
    void anEntryCarryingANestedObjectIsStillRead() {
        List<SdkOffer> lstOffer = DpmVersions.lstOffer("""
                [
                    {
                        "version": "3.5.8",
                        "installed": true,
                        "remote": true,
                        "channels": { "labels": ["latest"] }
                    }
                ]
                """);

        assertEquals(1, lstOffer.size());
        assertEquals(VersionId.of(3, 5, 8), lstOffer.get(0).version());
        assertTrue(lstOffer.get(0).flagInstalled());
    }


    /**
     * stderr arrives in the same stream. A line with a bracket in it before the
     * document is passed over, including one that parses as an array of numbers.
     */
    @Test
    void aLineBesideTheDocumentIsPassedOver() {
        List<SdkOffer> lstOffer = DpmVersions.lstOffer(
                "warning [x]: a newer dpm is available\nnote [1]\n" + STR_JSON);

        assertEquals(2, lstOffer.size());
        assertEquals(VersionId.of(3, 4, 4), lstOffer.get(0).version());
    }


    /** An answer with no document in it is not an empty catalogue. */
    @Test
    void outputWithNoArrayIsRefusedNotEmpty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DpmVersions.lstOffer("Error: unknown flag: -o"));

        assertTrue(ex.getMessage().contains("unknown flag"), ex.getMessage());
    }


    /** Only a JSON true is installed; the string is not. */
    @Test
    void installedIsABooleanOrNothing() {
        List<SdkOffer> lstOffer = DpmVersions.lstOffer(
                "[{\"version\": \"3.5.8\", \"installed\": \"true\"}]");

        assertFalse(lstOffer.get(0).flagInstalled());
    }

}
