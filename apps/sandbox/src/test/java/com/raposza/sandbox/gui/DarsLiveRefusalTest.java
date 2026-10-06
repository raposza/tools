// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A refusal from the participant is worded for the dialog: the DAR by name,
 * the ids cut short, the one measured reason - a package in use by a
 * contract - with what to do about it, and anything else with its RPC and
 * code in front of Canton's own description.
 *
 * THE CONTROL CAN FAIL: the message below is the one his console showed on
 * 2026-10-04, verbatim, and its synchronizer ends in Canton's own ellipsis.
 *
 * Author Claude/bentzn
 */
class DarsLiveRefusalTest {

    private static final String STR_IN_USE =
            "com.digitalasset.canton.admin.participant.v30.PackageService/RemoveDar was"
            + " refused with FAILED_PRECONDITION: PACKAGE_OR_DAR_REMOVAL_ERROR(9,0): The DAR"
            + " DarDescription(dar-id = 17cbcb2b7c42..., name = pharma, version = 0.0.1,"
            + " description = uploaded-via-ledger-api) cannot be removed because its main"
            + " package 17cbcb2b7c423ca47a15a2403051929f0f0c5af595a0e12e68930a1e6e3546fd is"
            + " in-use by contract ContractId(002300cde484de69c7a4d9272f6afca37e3dc7c3eea22c5"
            + "319184672fc8aba542cca1212202febc25cde6a26497cce431eb6e884de4224b8608e12f8887bf5"
            + "8e8b243f714e) on synchronizer global-domain::1220f9603214....";


    @Test
    void aPackageInUseIsWordedWithItsIdsCutShort() {
        assertEquals("pharma 0.0.1 cannot be removed.\n\n"
                + "Its main package is still in use by a contract on the ledger:\n"
                + "    package      17cbcb2b7c423ca47a15a240...\n"
                + "    contract     002300cde484de69c7a4d927...\n"
                + "    synchronizer global-domain::1220f9603214\n\n"
                + "Archive the contract first, or leave the DAR in place.",
                DarsLivePane.strRefusal("pharma 0.0.1", STR_IN_USE));
    }


    @Test
    void anyOtherRefusalKeepsTheRpcTheCodeAndTheReason() {
        assertEquals("pharma 0.0.1: VetDar was refused (NOT_FOUND).\n\nDAR_NOT_FOUND(11,0): no such dar",
                DarsLivePane.strRefusal("pharma 0.0.1",
                        "com.digitalasset.canton.admin.participant.v30.PackageService/VetDar was"
                        + " refused with NOT_FOUND: DAR_NOT_FOUND(11,0): no such dar"));
        assertEquals("pharma 0.0.1: connection reset",
                DarsLivePane.strRefusal("pharma 0.0.1", "connection reset"));
    }

}
