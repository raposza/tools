// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Reading a transaction back, and the port arithmetic - both without a ledger.
 *
 * THE DEFECT THIS EXISTS FOR IS REAL AND WAS MINE. `probes/pharma`'s RouteProbe
 * takes the FIRST `contractId` in a response and calls it the created contract.
 * On a consuming exercise that is the ARCHIVED node's id, so the probe printed
 * the Order's id and labelled it the Acceptance - 2026-09-22. A fixture that
 * chained from it would exercise the next choice on a contract that no longer
 * exists.
 *
 * Author Claude/bentzn
 */
class PharmaLedgerTest {

    /**
     * An archived node FIRST and the created one after it, which is the order
     * that made the probe wrong.
     */
    private static final String STR_BODY = "{\"transaction\":{\"events\":["
            + "{\"ArchivedEvent\":{\"contractId\":\"00old\","
            + "\"templateId\":\"abc:Main:Shipment\"}},"
            + "{\"CreatedEvent\":{\"contractId\":\"00new\","
            + "\"templateId\":\"abc:Main:MaterialReceipt\"}}]}}";


    @Test
    void theCreatedContractIsReadAndNotTheArchivedOne() throws IOException {
        assertEquals("00new", PharmaLedger.strCreated(STR_BODY, PharmaStory.STR_T_RECEIPT));
    }


    @Test
    void aTemplateThatWasNotCreatedIsAnError() {
        // NOT null, and not the first id it can find: a chain that carried on
        // would exercise on a contract that is not the one it thinks.
        assertThrows(IOException.class,
                () -> PharmaLedger.strCreated(STR_BODY, PharmaStory.STR_T_COA));
    }


    @Test
    void theRightOneIsPickedWhenTwoWereCreated() throws IOException {
        String strBody = "{\"events\":["
                + "{\"CreatedEvent\":{\"contractId\":\"00a\","
                + "\"templateId\":\"abc:Main:ProductionLot\"}},"
                + "{\"CreatedEvent\":{\"contractId\":\"00b\","
                + "\"templateId\":\"abc:Main:CertificateOfAnalysis\"}}]}";

        assertEquals("00a", PharmaLedger.strCreated(strBody, PharmaStory.STR_T_LOT));
        assertEquals("00b", PharmaLedger.strCreated(strBody, PharmaStory.STR_T_COA));
    }


    /**
     * The block is `LocalNetPorts`': six slots per role, ten apart, and the
     * JSON API is the third slot. app-provider's is 30022 on the default block
     * and app-user's is 30032 - the two the probe reached.
     */
    @Test
    void theJsonPortsAreTheOnesTheProbeMeasured() {
        assertEquals(30022, PharmaLedger.nPortJson(30010, PharmaLedger.N_OFFSET_PRODUCER));
        assertEquals(30032, PharmaLedger.nPortJson(30010, PharmaLedger.N_OFFSET_SUPPLIER));
    }


    @Test
    void aMovedBlockMovesBothPorts() {
        assertEquals(23022, PharmaLedger.nPortJson(23010, PharmaLedger.N_OFFSET_PRODUCER));
        assertEquals(23032, PharmaLedger.nPortJson(23010, PharmaLedger.N_OFFSET_SUPPLIER));
    }


    /**
     * A wildcard participant has no user to default to, so the body names one -
     * the account the fixture was granted its parties to, and the one its
     * token is minted for when auth is on.
     */
    @Test
    void aSubmissionNamesItsUser() {
        String strBody = PharmaLedger.strBodySubmit("pharma-1", "Alice::1220",
                "{\"CreateCommand\":{}}");

        assertEquals("{\"commands\":{\"commandId\":\"pharma-1\",\"userId\":\""
                + PharmaLedger.STR_USER + "\",\"actAs\":[\"Alice::1220\"],\"commands\":["
                + "{\"CreateCommand\":{}}]}}", strBody);
        assertEquals("participant_admin", PharmaLedger.STR_USER);
    }

}
