// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ids in here are REAL, taken off this workspace, not invented. The pair in
 * shortensBothEndsOfAContractId differs only in its last four characters, which
 * is the case a head-only abbreviation renders as one row.
 *
 * Author Claude/bentzn
 */
class ShortIdsTest {

    private static final String ID_PARTY =
            "PetShop-d4d95138::12200dec65748d980875cd6414d94e88f1e0abfd042969fd75a370a7a21cb9b8ac40";

    private static final String ID_CT_A = "00cda4cdbc5d57ff41b8b0ef6f9e2ccc04c07fceeff7ceb5ae98c1"
            + "aa85ca981cc8ca031220e922ec9e84d3462bafb5ec2674699ccce4266f39b483d79f9bd96d619cd70000";

    private static final String ID_CT_B = "00cda4cdbc5d57ff41b8b0ef6f9e2ccc04c07fceeff7ceb5ae98c1"
            + "aa85ca981cc8ca031220e922ec9e84d3462bafb5ec2674699ccce4266f39b483d79f9bd96d619cd7b655";


    @Test
    void keepsTheHintAndTheTailOfAPartyId() {
        assertEquals("PetShop-d4d95138::\u00a4\u00a4\u00a4b8ac40", ShortIds.id(ID_PARTY));
    }


    @Test
    void shortensBothEndsOfAContractId() {
        assertEquals("00cd\u00a4\u00a4\u00a40000", ShortIds.id(ID_CT_A));
        assertEquals("00cd\u00a4\u00a4\u00a4b655", ShortIds.id(ID_CT_B));
    }


    @Test
    void twoContractsDifferingOnlyInTheTailStayDistinct() {
        assertEquals(false, ShortIds.id(ID_CT_A).equals(ShortIds.id(ID_CT_B)));
    }


    @Test
    void shortensInsideARenderedBlock() {
        String strIn = "  \"owner\" : \"" + ID_PARTY + "\",\n  \"contractId\" : \"" + ID_CT_A + "\"";
        assertEquals("  \"owner\" : \"PetShop-d4d95138::\u00a4\u00a4\u00a4b8ac40\",\n"
                + "  \"contractId\" : \"00cd\u00a4\u00a4\u00a40000\"", ShortIds.text(strIn));
    }


    @Test
    void shortensAPackageIdAndLeavesTheQualifiedNameAlone() {
        assertEquals("266c\u00a4\u00a4\u00a47d7c:Main:Pet", ShortIds.text(
                "266c34996c4eb8bb20cce9aa1f3847e36b3788b62f861445d229929e2c0f7d7c:Main:Pet"));
    }


    @Test
    void leavesShortHexAlone() {
        assertEquals("Alice-9b3970be", ShortIds.text("Alice-9b3970be"));
        assertEquals("offset 3", ShortIds.text("offset 3"));
        assertEquals("sandbox::1220f5d8", ShortIds.text("sandbox::1220f5d8"));
    }


    @Test
    void leavesOrdinaryWordsAlone() {
        assertEquals("the decade faded, effaced", ShortIds.text("the decade faded, effaced"));
    }


    @Test
    void survivesNullAndEmpty() {
        assertEquals(null, ShortIds.text(null));
        assertEquals("", ShortIds.text(""));
    }

}
