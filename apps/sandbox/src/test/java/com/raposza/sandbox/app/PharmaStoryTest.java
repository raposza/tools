// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Pharma story's commands, with no ledger.
 *
 * WHAT IS ASSERTED IS THE ENCODING, because that is what a participant refuses
 * and what a reading of the model cannot catch. `Int` and `Decimal` go as
 * QUOTED strings over the JSON Ledger API - measured by
 * `probes/localnet/AviationLoad.java`, which submits exactly those shapes
 * against a LocalNetND participant - and a number written bare is a
 * submission rejected at the far end of a start.
 *
 * Author Claude/bentzn
 */
class PharmaStoryTest {

    /**
     * THE EIGHT, as allocated party ids look.
     *
     * A PARTY PER ACTUAL USER - his instruction, 2026-09-22. There were two
     * here, named after the two companies, and a company is not a party that
     * signs anything.
     */
    private static final PharmaStory.Cast CAST = new PharmaStory.Cast(
            "Kestrel-Procurement::1220aa",
            "Kestrel-QualityAssurance::1220aa",
            "Kestrel-Receiving::1220aa",
            "Kestrel-Manufacturing::1220aa",
            "Vinterberg-Sales::1220bb",
            "Vinterberg-Production::1220bb",
            "Vinterberg-QualityAssurance::1220bb",
            "Vinterberg-Shipping::1220bb");

    private static final String STR_CID = "00abc";


    @Test
    void aCreateNamesThePackageByNameRatherThanByHash() {
        String strOut = PharmaStory.strOrder(CAST);

        assertTrue(strOut.contains("\"CreateCommand\""), strOut);
        assertTrue(strOut.contains("\"templateId\":\"#pharma:Main:PurchaseOrder\""), strOut);
    }


    @Test
    void anIntGoesAsAQuotedString() {
        // 12000 vials. Bare, the participant refuses the submission.
        assertTrue(PharmaStory.strOrder(CAST)
                .contains("\"quantity\":\"12000\""));
        assertFalse(PharmaStory.strOrder(CAST)
                .contains("\"quantity\":12000"));
    }


    @Test
    void aDecimalGoesAsAQuotedStringAndABoolDoesNot() {
        String strTest = PharmaStory.strTest("Identity", "FTIR", "99.40", "%",
                "98.00", "100.00", true);

        assertTrue(strTest.contains("\"measured\":\"99.40\""), strTest);
        assertTrue(strTest.contains("\"passed\":true"), strTest);
        assertFalse(strTest.contains("\"passed\":\"true\""), strTest);
    }


    @Test
    void theRangeIsARecordInsideTheTest() {
        String strTest = PharmaStory.strTest("Identity", "FTIR", "99.40", "%",
                "98.00", "100.00", true);

        assertTrue(strTest.contains("\"permitted\":{\"low\":\"98.00\",\"high\":\"100.00\"}"),
                strTest);
    }


    @Test
    void theCleanLotConformsOnEveryTest() {
        // STORY 1 IS THE CLEAN LOT. A `false` here would be Story 2, which is
        // not built - and a fixture that quietly diverged would be read as one
        // that does not work.
        assertFalse(PharmaStory.strIssueCoa(STR_CID).contains("\"passed\":false"));
        assertFalse(PharmaStory.strInspect(CAST, STR_CID).contains("\"passed\":false"));
        assertTrue(PharmaStory.strInspect(CAST, STR_CID).contains("\"conforms\":true"));
    }


    @Test
    void anExerciseCarriesTheContractAndTheChoice() {
        String strOut = PharmaStory.strAcceptOrder(STR_CID);

        assertTrue(strOut.contains("\"ExerciseCommand\""), strOut);
        assertTrue(strOut.contains("\"contractId\":\"" + STR_CID + "\""), strOut);
        assertTrue(strOut.contains("\"choice\":\"AcceptOrder\""), strOut);
        assertTrue(strOut.contains("\"templateId\":\"#pharma:Main:PurchaseOrder\""), strOut);
    }


    /**
     * EACH EXERCISE NAMES THE TEMPLATE IT IS EXERCISED ON, not the one it
     * creates. A chain that named the created template would be refused on
     * every step after the first, and the message would be about a contract id.
     */
    @Test
    void eachStepExercisesOnTheContractItWasGiven() {
        assertTrue(PharmaStory.strRecordLot(CAST, STR_CID)
                .contains(PharmaStory.strTemplateId(PharmaStory.STR_T_ACCEPT)));
        assertTrue(PharmaStory.strIssueCoa(STR_CID)
                .contains(PharmaStory.strTemplateId(PharmaStory.STR_T_LOT)));
        assertTrue(PharmaStory.strShip(STR_CID)
                .contains(PharmaStory.strTemplateId(PharmaStory.STR_T_COA)));
        assertTrue(PharmaStory.strReceive(STR_CID)
                .contains(PharmaStory.strTemplateId(PharmaStory.STR_T_SHIPMENT)));
        assertTrue(PharmaStory.strInspect(CAST, STR_CID)
                .contains(PharmaStory.strTemplateId(PharmaStory.STR_T_RECEIPT)));
        assertTrue(PharmaStory.strRelease(STR_CID)
                .contains(PharmaStory.strTemplateId(PharmaStory.STR_T_INSPECTION)));
    }


    /**
     * STORY 2's PRIVACY CLAIM, and it is the one thing here a reading of the
     * model is easy to get wrong: `Quarantine` must name NO supplier party. If
     * one ever appears in the command, the supplier's participant learns the
     * lot was held before the producer chose to say so, and the fixture stops
     * demonstrating the thing it exists for.
     */
    @Test
    void theQuarantineNamesNobodyFromTheSupplier() {
        String strOut = PharmaStory.strQuarantine(STR_CID);

        assertFalse(strOut.contains("Vinterberg"), strOut);
        assertFalse(strOut.contains("supplier"), strOut);
        assertTrue(strOut.contains(PharmaStory.STR_QUARANTINE), strOut);
    }


    /**
     * THE SUPPLIER IS NAMED WHEN THE DEVIATION IS RAISED, and not before - a
     * choice argument, because the quarantine could not carry it and stay
     * private.
     */
    @Test
    void theDeviationIsWhereTheSupplierFirstAppears() {
        String strOut = PharmaStory.strRaiseDeviation(CAST, STR_CID);

        assertTrue(strOut.contains(CAST.strQaSupplier()), strOut);
        assertTrue(strOut.contains(PharmaStory.STR_DEVIATION), strOut);
    }


    /**
     * The two organizations measured the same lot and got different answers:
     * the supplier's certificate conforms and the producer's incoming test
     * does not. That disagreement IS Story 2, so it is asserted rather than
     * left to a reading of two decimal literals.
     */
    @Test
    void theProblemLotIsCertifiedPassingAndReceivedFailing() {
        assertFalse(PharmaStory.strIssueCoaProblem(STR_CID).contains("\"passed\":false"),
                "the supplier certifies it as conforming");

        String strInspect = PharmaStory.strInspectProblem(CAST, STR_CID);
        assertTrue(strInspect.contains("\"passed\":false"), strInspect);
        assertTrue(strInspect.contains("\"conforms\":false"), strInspect);
    }


    /** The close names the disposition by CONTRACT ID, not by repeating it. */
    @Test
    void closingTheQuarantinePointsAtWhatResolvedIt() {
        String strOut = PharmaStory.strCloseQuarantine(STR_CID, "00feed");

        assertTrue(strOut.contains("\"disposition\":\"00feed\""), strOut);
        assertTrue(strOut.contains(PharmaStory.strTemplateId(PharmaStory.STR_T_QUARANTINE)),
                strOut);
    }


    /**
     * THE THREE ENCODINGS, EACH MEASURED BEFORE IT WAS USED -
     * `probes/pharma/types_probe.py`, 2026-09-22. They are asserted here
     * because each has exactly ONE accepted shape and the participant refuses
     * the others outright: a change of mind in a builder would show up as a
     * fixture that stops populating, three minutes into a start.
     */
    @Test
    void anEnumGoesAsTheBareConstructorName() {
        String strOut = PharmaStory.strDispose(CAST, STR_CID);

        assertTrue(strOut.contains("\"outcome\":\"ConditionallyAccepted\""), strOut);
        assertFalse(strOut.contains("\"outcome\":{"), strOut);
    }


    @Test
    void aVariantGoesAsTagAndValue() {
        String strOut = PharmaStory.strRespond(STR_CID);

        assertTrue(strOut.contains("\"evidence\":{\"tag\":\"RetainSample\",\"value\":{"),
                strOut);
        // The constructor-keyed shape the participant refuses.
        assertFalse(strOut.contains("\"evidence\":{\"RetainSample\""), strOut);

        // A constructor that takes nothing still carries a value.
        assertTrue(PharmaStory.strVariant("Absent", "{}").contains("\"value\":{}"),
                PharmaStory.strVariant("Absent", "{}"));
    }


    @Test
    void aMapGoesAsAnObject() {
        String strOut = PharmaStory.strRetest(STR_CID);

        assertTrue(strOut.contains("\"conditions\":{\"instrument\":"), strOut);
        assertFalse(strOut.contains("\"conditions\":[["), strOut);
    }


    @Test
    void aPartyWithQuotesInItCannotBreakOutOfTheJson() {
        String strOut = PharmaStory.strJson("a\"b");

        assertTrue(strOut.equals("\"a\\\"b\""), strOut);
    }

}
