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
 * The seeded script: only for the fixture, with the ledger's own ids, and
 * every line laid out to fit the pane.
 *
 * Author Claude/bentzn
 */
class AviationScriptTest {

    private static final String STR_NS = "::1220" + "a".repeat(60) + "8b1936";

    private static final String ID_RELEASE = "0076" + "c".repeat(130) + "7c21";

    private static final String ID_DEFECT = "0041" + "d".repeat(130) + "1a09";

    /** Three reads, the FETCH, the three writes and the administrative pair. */
    private static final int CNT_STATEMENT = 10;


    private static PartyInfo party(String strHint) {
        return new PartyInfo(strHint + STR_NS, null, true);
    }


    private static List<PartyInfo> lstParty() {
        List<PartyInfo> lstOut = new ArrayList<>();
        lstOut.add(party("MaintenanceControl-d4d95138"));
        lstOut.add(party("Technician-9b3970be"));
        lstOut.add(party("Inspector-4f1df03a"));
        lstOut.add(party("Engineering-1df42503"));
        lstOut.add(party("Quality-c812a76d"));
        lstOut.add(party("ConfigurationControl-77e10b94"));
        lstOut.add(party("ReleaseAuthority-3e0a51c7"));
        return lstOut;
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
        return List.of(group("Aircraft", List.of(contract("00b3", "Aircraft"))),
                group("Defect", List.of(contract(ID_DEFECT, "Defect"))),
                group("Investigation", List.of()),
                group("Release", List.of(contract(ID_RELEASE, "Release"))));
    }


    @Test
    void itReadsAndItWrites() {
        String strOut = AviationScript.strFor(lstParty(), lstTemplate());

        assertTrue(strOut.contains("LIST PARTIES"), strOut);
        assertTrue(strOut.indexOf("GET LEDGER END") < 0, strOut);
        // ONE QUERY, on the template the write half creates, sieved on the
        // field the write half sets.
        assertEquals(1, strOut.split(" QUERY ", -1).length - 1, strOut);
        assertTrue(strOut.contains("QUERY Main:Defect WHERE grounded = true;"), strOut);
        assertTrue(strOut.contains("defect = AS "), strOut);
        assertTrue(strOut.contains("CREATE Main:Defect"), strOut);
        assertTrue(strOut.contains("\"defectId\": \"DEF-CAQL\""), strOut);
        assertTrue(strOut.contains("\"msn\": \"MSN-5822\""), strOut);
        assertTrue(strOut.contains("\"severity\": \"Major\""), strOut);
        assertTrue(strOut.contains("\"location\": {"), strOut);
        assertTrue(strOut.contains("\"measurements\": ["), strOut);
        // THE WRITES CHAIN THROUGH BINDINGS: each exercise targets the
        // previous result, never the contract it already consumed.
        assertTrue(strOut.contains("withEvidence = AS "), strOut);
        assertTrue(strOut.contains("EXERCISE ON $defect AddEvidence"), strOut);
        assertTrue(strOut.contains("assigned = AS "), strOut);
        assertTrue(strOut.contains("EXERCISE ON $withEvidence AdvanceDefect"), strOut);
        assertTrue(strOut.contains("\"stateNew\": \"UnderInvestigation\""), strOut);
        assertTrue(strOut.indexOf("EXERCISE ON $defect AdvanceDefect") < 0, strOut);
        // EVERY STATEMENT SAYS WHO HAS TO RUN IT.
        assertTrue(strOut.contains("-- User: maintenancecontrol or superuser"), strOut);
        assertTrue(strOut.contains("-- User: technician or superuser"), strOut);
        assertTrue(strOut.contains("-- User: releaseauthority or superuser"), strOut);
        // NO `*`. It is not a user and the picker no longer offers it.
        assertTrue(strOut.indexOf(" or *") < 0, strOut);
        assertTrue(strOut.contains("-- User: participant_admin\n"), strOut);
        assertTrue(strOut.contains("ALLOCATE PARTY"), strOut);
        assertTrue(strOut.contains("CREATE USER"), strOut);
        // NOTHING OF THE DAIRY SURVIVES.
        assertTrue(strOut.indexOf("HACCP") < 0, strOut);
        assertTrue(strOut.indexOf("RawMilkLot") < 0, strOut);
        assertTrue(strOut.indexOf("Batch") < 0, strOut);
    }


    /**
     * HIS INSTRUCTION, 2026-09-22: it has to fit the window. A statement that
     * runs past the pane is read by dragging a scrollbar, which is the thing
     * the starter exists not to require.
     *
     * The statements were one line each until that instruction, on the
     * reasoning that Ctrl-Enter is line-based. It is not: `CaqlPanel.runLine`
     * cuts the span with `CaqlParser.spanAt`, which scans to the first `;` at
     * brace depth zero.
     */
    @Test
    void everyLineFitsThePane() {
        for (String strLine : AviationScript.strFor(lstParty(), lstTemplate()).split("\\n")) {
            assertTrue(strLine.length() <= AviationScript.CNT_WIDTH,
                    strLine.length() + ": " + strLine);
        }
    }


    /**
     * Every statement ends with `;` - D-742 - and a statement laid out over
     * several lines ends on its last one, so the count of lines ending in `;`
     * is the count of statements.
     */
    @Test
    void everyStatementIsTerminated() {
        int cntEnd = 0;
        for (String strLine : AviationScript.strFor(lstParty(), lstTemplate()).split("\\n")) {
            if (strLine.endsWith(";"))
                cntEnd++;
        }
        assertEquals(CNT_STATEMENT, cntEnd);
    }


    @Test
    void theContractItFetchesIsOneTheLedgerHolds() {
        String strOut = AviationScript.strFor(lstParty(), lstTemplate());

        assertTrue(strOut.contains("release = AS "), strOut);
        assertTrue(strOut.contains(ShortIds.text(ID_RELEASE)), strOut);
        // AND NOT THE FIXTURE'S DEFECT. The write half raises its own, so
        // nothing the last read happened to show is named.
        assertTrue(strOut.indexOf(ShortIds.text(ID_DEFECT)) < 0, strOut);
    }


    /** The ids are the ledger's, and they are SHORT - the panes' own form. */
    @Test
    void thePartyIdsAreRealAndShortened() {
        String strOut = AviationScript.strFor(lstParty(), lstTemplate());

        assertTrue(strOut.contains("AS MaintenanceControl-d4d95138::" + ShortIds.STR_CUT
                + "8b1936 QUERY"), strOut);
        assertTrue(strOut.indexOf(STR_NS) < 0, strOut);
    }


    @Test
    void aLedgerWithNoReleaseStillGetsAScript() {
        // THE ROLES ARE THE GATE; a template is not. A ledger the fixture has
        // been run on but whose release is not visible to the selected user
        // still gets the script, without the one line it cannot support.
        List<LedgerSnapshot.TemplateGroup> lstNoRelease = new ArrayList<>(lstTemplate());
        lstNoRelease.removeIf(group -> "Release".equals(group.idTemplate().nameEntity()));

        String strOut = AviationScript.strFor(lstParty(), lstNoRelease);

        assertTrue(strOut.contains("LIST PARTIES"), strOut);
        assertTrue(strOut.contains("CREATE Main:Defect"), strOut);
        assertTrue(strOut.indexOf(" FETCH ") < 0, strOut);
        assertTrue(strOut.indexOf("release = ") < 0, strOut);

        // And an empty read - no templates at all - is still the fixture.
        String strEmpty = AviationScript.strFor(lstParty(), List.of());
        assertTrue(strEmpty.contains("EXERCISE ON $withEvidence AdvanceDefect"), strEmpty);
    }


    @Test
    void anotherLedgerIsNotSeeded() {
        // THE SEVEN ROLES ARE THE WHOLE GATE, so a ledger that is not the
        // fixture is one that does not host all of them.
        assertNull(AviationScript.strFor(List.of(), lstTemplate()));
        assertNull(AviationScript.strFor(List.of(party("MaintenanceControl-d4d95138")),
                lstTemplate()));
        assertNull(AviationScript.strFor(null, lstTemplate()));

        for (String strHint : List.of("MaintenanceControl-", "Technician-", "Inspector-",
                "Engineering-", "Quality-", "ConfigurationControl-", "ReleaseAuthority-")) {
            List<PartyInfo> lstShort = new ArrayList<>(lstParty());
            lstShort.removeIf(party -> party.idParty().startsWith(strHint));
            assertNull(AviationScript.strFor(lstShort, lstTemplate()), strHint);
        }

        // A role hosted TWICE is not the fixture either: the script could not
        // say which of the two to act as.
        List<PartyInfo> lstTwice = new ArrayList<>(lstParty());
        lstTwice.add(party("Technician-00000000"));
        assertNull(AviationScript.strFor(lstTwice, lstTemplate()));
    }

}
