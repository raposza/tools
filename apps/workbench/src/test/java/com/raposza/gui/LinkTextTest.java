// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lengths in here are MEASURED off this workspace: a contract id is 138 hex
 * characters, an update id 68, a package id 64. The last one is why a bare
 * 64-character run is not a link.
 *
 * Author Claude/bentzn
 */
class LinkTextTest {

    private static final String ID_CT = "00cda4cdbc5d57ff41b8b0ef6f9e2ccc04c07fceeff7ceb5ae98c1"
            + "aa85ca981cc8ca031220e922ec9e84d3462bafb5ec2674699ccce4266f39b483d79f9bd96d619cd7b655";

    private static final String ID_PKG =
            "266c34996c4eb8bb20cce9aa1f3847e36b3788b62f861445d229929e2c0f7d7c";

    private static final String ID_PARTY =
            "PetShop-d4d95138::12200dec65748d980875cd6414d94e88f1e0abfd042969fd75a370a7a21cb9b8ac40";


    @Test
    void aContractIdIsALinkShownShortAndTargetedWhole() {
        String strHtml = LinkText.html("\"contractId\" : \"" + ID_CT + "\"", true);

        assertTrue(strHtml.contains("href=\"ref:" + ID_CT + "\""));
        assertTrue(strHtml.contains(">00cd\u00a4\u00a4\u00a4b655</a>"));
    }


    @Test
    void aPartyIdIsMatchedWholeAndKeepsItsHint() {
        String strHtml = LinkText.html("  \"owner\" : \"" + ID_PARTY + "\"", true);

        assertTrue(strHtml.contains("href=\"party:" + ID_PARTY + "\""));
        assertTrue(strHtml.contains(">PetShop-d4d95138::\u00a4\u00a4\u00a4b8ac40</a>"));
    }


    @Test
    void theFullFormIsTheLinkTextWhenShorteningIsOff() {
        String strHtml = LinkText.html(ID_CT, false);

        assertTrue(strHtml.contains("href=\"ref:" + ID_CT + "\""));
        assertTrue(strHtml.contains(">" + ID_CT + "</a>"));
    }


    @Test
    void aTemplateIdIsShortenedAndLinked() {
        String strHtml = LinkText.html(ID_PKG + ":Main:Pet", true);

        assertTrue(strHtml.contains("href=\"template:" + ID_PKG + ":Main:Pet\""));
        assertTrue(strHtml.contains(">266c\u00a4\u00a4\u00a47d7c:Main:Pet</a>"));
    }


    @Test
    void aBarePackageIdIsNotLinkedEither() {
        String strHtml = LinkText.html("package   " + ID_PKG, true);

        assertFalse(strHtml.contains("<a "));
    }


    @Test
    void ordinaryTextIsEscapedAndNotLinked() {
        String strHtml = LinkText.html("a < b & c > d, offset 33, 2026-08-25T06:49:05", true);

        assertTrue(strHtml.contains("a &lt; b &amp; c &gt; d"));
        assertFalse(strHtml.contains("<a "));
    }


    @Test
    void theTreeConnectorsSurvive() {
        assertTrue(LinkText.html("\u251c\u2500 Created", true).contains("\u251c\u2500 Created"));
    }

}
