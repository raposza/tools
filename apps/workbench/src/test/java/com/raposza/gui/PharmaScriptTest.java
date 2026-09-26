// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PartyInfo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The seeded Pharma script: one per ORGANIZATION, decided by what the
 * connected participant hosts, neither side naming a party it cannot act as,
 * and every line laid out to fit the pane.
 *
 * Author Claude/bentzn
 */
class PharmaScriptTest {

    private static final String STR_NS = "::1220" + "a".repeat(60) + "8b1936";

    private static final String ID_RECEIPT = "0059" + "e".repeat(130) + "4b70";

    private static final String ID_LOT = "0022" + "f".repeat(130) + "9d15";

    private static final String ID_INSPECTION = "00c1" + "b".repeat(130) + "dae4";

    /** Reads, FETCH, the two exercises and the administrative pair, on each side. */
    private static final int CNT_STATEMENT = 9;


    private static PartyInfo party(String strHint, boolean flagLocal) {
        return new PartyInfo(strHint + STR_NS, null, flagLocal);
    }


    /** The producer's four, hosted here; the supplier's four, merely visible. */
    private static List<PartyInfo> lstProducer() {
        List<PartyInfo> lstOut = new ArrayList<>();
        lstOut.add(party("Kestrel-Procurement", true));
        lstOut.add(party("Kestrel-QualityAssurance", true));
        lstOut.add(party("Kestrel-Receiving", true));
        lstOut.add(party("Kestrel-Manufacturing", true));
        lstOut.addAll(lstSupplierParty(false));
        return lstOut;
    }


    /** The supplier's participant: its own four hosted, the producer's seen. */
    private static List<PartyInfo> lstSupplier() {
        List<PartyInfo> lstOut = new ArrayList<>(lstSupplierParty(true));
        lstOut.add(party("Kestrel-Procurement", false));
        lstOut.add(party("Kestrel-QualityAssurance", false));
        lstOut.add(party("Kestrel-Receiving", false));
        lstOut.add(party("Kestrel-Manufacturing", false));
        return lstOut;
    }


    private static List<PartyInfo> lstSupplierParty(boolean flagLocal) {
        return List.of(party("Vinterberg-Sales", flagLocal),
                party("Vinterberg-Production", flagLocal),
                party("Vinterberg-QualityAssurance", flagLocal),
                party("Vinterberg-Shipping", flagLocal));
    }


    private static LedgerSnapshot.TemplateGroup group(String nameEntity,
            List<Contract> lstContract) {
        return new LedgerSnapshot.TemplateGroup(new DataId("pkg", "Main", nameEntity),
                "Main:" + nameEntity, lstContract);
    }


    private static Contract contract(String idContract, String nameEntity) {
        return new Contract(idContract, "1", new DataId("pkg", "Main", nameEntity),
                new DamlValue.Rec(null, List.of()), List.of(), List.of(), Optional.empty(),
                "", Optional.empty());
    }


    private static List<LedgerSnapshot.TemplateGroup> lstTemplate() {
        return List.of(group("MaterialSpecification", List.of(contract("0001",
                        "MaterialSpecification"))),
                group("ProductionLot", List.of(contract(ID_LOT, "ProductionLot"))),
                group("MaterialReceipt", List.of(contract(ID_RECEIPT, "MaterialReceipt"))),
                group("IncomingInspection", List.of(contract(ID_INSPECTION,
                        "IncomingInspection"))));
    }


    @Test
    void theProducerReadsAndWritesItsOwnSide() {
        String strOut = PharmaScript.strFor(lstProducer(), lstTemplate());

        assertTrue(strOut.contains("Kestrel - the producer's participant"), strOut);
        assertTrue(strOut.contains("LIST PARTIES"), strOut);
        // ONE QUERY, sieved on the field the write half sets. The clause sits
        // on its own line because the statement does not fit in one.
        assertEquals(1, strOut.split(" QUERY ", -1).length - 1, strOut);
        assertTrue(strOut.contains("QUERY Main:IncomingInspection\n"), strOut);
        assertTrue(strOut.contains("WHERE conforms = false;"), strOut);
        assertTrue(strOut.contains("receipt = AS Kestrel-Receiving"), strOut);
        // THE WRITES CHAIN: the quarantine targets the inspection's result.
        assertTrue(strOut.contains("inspection = AS Kestrel-QualityAssurance"), strOut);
        assertTrue(strOut.contains(" Inspect WITH {"), strOut);
        assertTrue(strOut.contains("\"inspectionNumber\": \"INS-CAQL\""), strOut);
        assertTrue(strOut.contains("\"permitted\": {"), strOut);
        assertTrue(strOut.contains("\"conforms\": false"), strOut);
        assertTrue(strOut.contains("quarantine = AS Kestrel-QualityAssurance"), strOut);
        assertTrue(strOut.contains("EXERCISE ON $inspection QuarantineLot"), strOut);
        assertTrue(strOut.contains("\"quarantineNumber\": \"QUA-CAQL\""), strOut);
        // EVERY STATEMENT SAYS WHO HAS TO RUN IT.
        assertTrue(strOut.contains("-- User: quality-assurance or superuser"), strOut);
        assertTrue(strOut.contains("-- User: receiving or superuser"), strOut);
        // AND IT ACTS AS NOTHING IT CANNOT ACT AS.
        assertTrue(strOut.indexOf("AS Vinterberg-") < 0, strOut);
        assertTrue(strOut.indexOf("Vinterberg-Sales") < 0, strOut);
        // THE ADMINISTRATIVE PAIR, as participant_admin alone - A-30, D-845.
        assertTrue(strOut.contains("-- User: participant_admin\n"), strOut);
        assertTrue(strOut.contains("trainee = ALLOCATE PARTY \"Kestrel-Trainee\";"), strOut);
        assertTrue(strOut.contains("CREATE USER \"kestrel-trainee\" WITH {"), strOut);
        assertTrue(strOut.contains("\"primaryParty\": \"$trainee\""), strOut);
        assertTrue(strOut.indexOf("participant_admin or superuser") < 0, strOut);
    }


    @Test
    void theSupplierReadsAndWritesItsOwnSide() {
        String strOut = PharmaScript.strFor(lstSupplier(), lstTemplate());

        assertTrue(strOut.contains("Vinterberg - the supplier's participant"), strOut);
        assertEquals(1, strOut.split(" QUERY ", -1).length - 1, strOut);
        assertTrue(strOut.contains("QUERY Main:CertificateOfAnalysis\n"), strOut);
        assertTrue(strOut.contains("WHERE issuedAt >= \"2026-10-01T00:00:00Z\";"), strOut);
        assertTrue(strOut.contains("lot = AS Vinterberg-Production"), strOut);
        assertTrue(strOut.contains("coa = AS Vinterberg-QualityAssurance"), strOut);
        assertTrue(strOut.contains(" IssueCoa WITH {"), strOut);
        assertTrue(strOut.contains("\"certificateNumber\": \"COA-CAQL\""), strOut);
        assertTrue(strOut.contains("shipment = AS Vinterberg-Shipping"), strOut);
        assertTrue(strOut.contains("EXERCISE ON $coa Ship"), strOut);
        assertTrue(strOut.contains("\"shipmentNumber\": \"SHP-CAQL\""), strOut);
        assertTrue(strOut.contains("-- User: shipping or superuser"), strOut);
        assertTrue(strOut.contains("-- User: production or superuser"), strOut);
        assertTrue(strOut.indexOf("AS Kestrel-") < 0, strOut);
        // THE QUARANTINE IS THE PRODUCER'S ALONE and is named nowhere here.
        assertTrue(strOut.indexOf("QuarantineLot") < 0, strOut);
        // ITS OWN TRAINEE, not the producer's.
        assertTrue(strOut.contains("trainee = ALLOCATE PARTY \"Vinterberg-Trainee\";"), strOut);
        assertTrue(strOut.contains("CREATE USER \"vinterberg-trainee\" WITH {"), strOut);
        assertTrue(strOut.indexOf("Kestrel-Trainee") < 0, strOut);
    }


    /** The write half exercises the contract it FETCHED, not another one. */
    @Test
    void eachSideActsOnTheContractItShows() {
        String strProducer = PharmaScript.strFor(lstProducer(), lstTemplate());
        assertTrue(strProducer.contains(ShortIds.text(ID_RECEIPT)), strProducer);
        assertTrue(strProducer.indexOf(ShortIds.text(ID_LOT)) < 0, strProducer);
        assertTrue(strProducer.indexOf(ShortIds.text(ID_INSPECTION)) < 0, strProducer);

        String strSupplier = PharmaScript.strFor(lstSupplier(), lstTemplate());
        assertTrue(strSupplier.contains(ShortIds.text(ID_LOT)), strSupplier);
        assertTrue(strSupplier.indexOf(ShortIds.text(ID_RECEIPT)) < 0, strSupplier);
    }


    /** The ids are the ledger's, and they are SHORT - the panes' own form. */
    @Test
    void thePartyIdsAreRealAndShortened() {
        String strOut = PharmaScript.strFor(lstProducer(), lstTemplate());

        assertTrue(strOut.contains("AS Kestrel-QualityAssurance::" + ShortIds.STR_CUT
                + "8b1936 QUERY"), strOut);
        assertTrue(strOut.indexOf(STR_NS) < 0, strOut);
    }


    /**
     * HIS INSTRUCTION, 2026-09-22: it has to fit the window. A statement that
     * runs past the pane is read by dragging a scrollbar, which is the thing
     * the starter exists not to require.
     */
    @Test
    void everyLineFitsThePane() {
        for (List<PartyInfo> lstParty : List.of(lstProducer(), lstSupplier())) {
            for (String strLine : PharmaScript.strFor(lstParty, lstTemplate()).split("\\n")) {
                assertTrue(strLine.length() <= PharmaScript.CNT_WIDTH,
                        strLine.length() + ": " + strLine);
            }
        }
    }


    /**
     * Every statement ends with `;` - D-742 - and a statement laid out over
     * several lines ends on its last one, so the count of lines ending in `;`
     * is the count of statements.
     */
    @Test
    void everyStatementIsTerminated() {
        for (List<PartyInfo> lstParty : List.of(lstProducer(), lstSupplier())) {
            int cntEnd = 0;
            for (String strLine : PharmaScript.strFor(lstParty, lstTemplate()).split("\\n")) {
                if (strLine.endsWith(";"))
                    cntEnd++;
            }
            assertEquals(CNT_STATEMENT, cntEnd);
        }
    }


    @Test
    void aLedgerWithoutTheAnchorStillGetsItsReads() {
        List<LedgerSnapshot.TemplateGroup> lstNoReceipt = new ArrayList<>(lstTemplate());
        lstNoReceipt.removeIf(group -> "MaterialReceipt".equals(group.idTemplate()
                .nameEntity()));

        String strOut = PharmaScript.strFor(lstProducer(), lstNoReceipt);

        assertTrue(strOut.contains("LIST PARTIES"), strOut);
        assertTrue(strOut.contains("QUERY Main:IncomingInspection"), strOut);
        assertTrue(strOut.indexOf(" FETCH ") < 0, strOut);
        assertTrue(strOut.indexOf("EXERCISE ") < 0, strOut);

        // The ROLES are the gate, so an empty read is still the fixture.
        String strEmpty = PharmaScript.strFor(lstSupplier(), List.of());
        assertTrue(strEmpty.contains("LIST PARTIES"), strEmpty);
        assertTrue(strEmpty.indexOf("EXERCISE ") < 0, strEmpty);
        // THE PAIR NEEDS NO CONTRACT and is there without the anchor.
        assertTrue(strOut.contains("ALLOCATE PARTY \"Kestrel-Trainee\""), strOut);
        assertTrue(strEmpty.contains("ALLOCATE PARTY \"Vinterberg-Trainee\""), strEmpty);
    }


    @Test
    void anotherLedgerIsNotSeeded() {
        assertNull(PharmaScript.strFor(List.of(), lstTemplate()));
        assertNull(PharmaScript.strFor(null, lstTemplate()));

        // THE SUPER VALIDATOR HOSTS NEITHER SET and is offered nothing, which
        // is the correct answer rather than a gap - `release_plan.md` 5.
        List<PartyInfo> lstSv = new ArrayList<>();
        lstSv.add(party("digital-asset-2", true));
        lstSv.addAll(lstSupplierParty(false));
        assertNull(PharmaScript.strFor(lstSv, lstTemplate()));

        // VISIBLE IS NOT HOSTED. A participant that can see all eight and
        // hosts none of them cannot act as any of them.
        List<PartyInfo> lstSeenOnly = new ArrayList<>();
        for (PartyInfo party : lstProducer()) {
            lstSeenOnly.add(new PartyInfo(party.idParty(), null, false));
        }
        assertNull(PharmaScript.strFor(lstSeenOnly, lstTemplate()));

        for (String strHint : List.of("Kestrel-Procurement", "Kestrel-QualityAssurance",
                "Kestrel-Receiving", "Kestrel-Manufacturing")) {
            List<PartyInfo> lstShort = new ArrayList<>(lstProducer());
            lstShort.removeIf(party -> party.idParty().startsWith(strHint));
            assertNull(PharmaScript.strFor(lstShort, lstTemplate()), strHint);
        }

        // A role hosted TWICE is not the fixture either.
        List<PartyInfo> lstTwice = new ArrayList<>(lstSupplier());
        lstTwice.add(party("Vinterberg-Shipping-2", true));
        assertNull(PharmaScript.strFor(lstTwice, lstTemplate()));
    }

}
