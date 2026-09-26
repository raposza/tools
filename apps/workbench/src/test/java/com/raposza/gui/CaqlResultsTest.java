// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.PrimKind;
import com.raposza.caql.Binding;
import com.raposza.caql.Entry;
import com.raposza.caql.RunStatus;
import com.raposza.caql.Transcript;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim under test is mostly the LAYOUT, which is unusual and is the point:
 * {@link LinkText} finds an identifier by shape and only in a position where
 * nothing follows it on the line. A rendering that put a contract id inside a
 * sentence would still read correctly and would silently stop being clickable,
 * which is the whole of section 8 of the design.
 *
 * The statement line is the operator's own source, echoed. The tests name what
 * was written rather than what the runner called it.
 *
 * Author Claude/bentzn
 */
class CaqlResultsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ID_CT = "00cda4cdbc5d57ff41b8b0ef6f9e2ccc04c07fceeff7ceb5ae98c1"
            + "aa85ca981cc8ca031220e922ec9e84d3462bafb5ec2674699ccce4266f39b483d79f9bd96d619cd7b655";


    @Test
    void theStatementIsEchoedAndACreatedContractIsPrintedBare() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.put("templateId", "Main:Pet");
        resolved.set("argument", MAPPER.createObjectNode());
        resolved.put("contractId", ID_CT);

        String strText = CaqlResults.text(transcript(entry(3, RunStatus.COMMITTED,
                "pet = AS $bank CREATE Main:Pet WITH {}", resolved)));

        assertTrue(strText.contains(">> pet = AS $bank CREATE Main:Pet WITH {}"), strText);
        assertTrue(strText.contains("contract " + ID_CT + "\n"), strText);
        assertTrue(strText.contains("Completed successfully."), strText);
    }


    /** The status was a per-statement field and the closing line carries it. */
    @Test
    void aStatementThatWorkedDoesNotRestateItsStatus() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.put("templateId", "Main:Pet");
        resolved.set("argument", MAPPER.createObjectNode());

        String strText = CaqlResults.text(transcript(entry(3, RunStatus.COMMITTED,
                "AS $bank CREATE Main:Pet WITH {}", resolved)));

        assertFalse(strText.contains("committed"), strText);
        assertFalse(strText.contains("create Main:Pet"), strText);
    }


    @Test
    void aRunThatStoppedSaysWhereAndDoesNotClaimSuccess() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.put("templateId", "Main:Pet");
        resolved.set("argument", MAPPER.createObjectNode());

        Entry entry = new Entry("id", 12, "CREATE Main:Pet", RunStatus.FAILED_LOCALLY,
                Optional.empty(), resolved, Optional.empty(), Optional.empty(),
                Optional.of("no such field 'nmae'"));

        String strText = CaqlResults.text(transcript(entry));

        assertTrue(strText.contains("STOPPED at line 12"), strText);
        assertTrue(strText.contains("nmae"), strText);
        assertFalse(strText.contains("Completed successfully."), strText);
    }


    @Test
    void aValidateRunSaysNothingWasSent() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.put("templateId", "Main:Pet");
        resolved.set("argument", MAPPER.createObjectNode());

        Transcript transcript = new Transcript("script", Instant.EPOCH, true,
                List.of(entry(1, RunStatus.VALIDATED, "AS $a CREATE Main:Pet WITH {}", resolved)),
                List.of());

        assertTrue(CaqlResults.text(transcript).contains("Nothing was sent"));
    }


    @Test
    void aListPrintsWhatItListed() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.putArray("items").add("alice::22").add("bank::22");
        resolved.put("count", 2);

        String strText = CaqlResults.text(
                transcript(entry(1, RunStatus.COMMITTED, "LIST PARTIES", resolved)));

        assertTrue(strText.contains(">> LIST PARTIES"), strText);
        assertTrue(strText.contains("2 items"), strText);
        assertTrue(strText.contains("alice::22\n"), strText);
        assertTrue(strText.contains("bank::22\n"), strText);
    }


    @Test
    void aUserStatementStillPrintsItsFields() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.put("userId", "inspector");
        resolved.putArray("readAs").add("alice::22");

        String strText = CaqlResults.text(transcript(
                entry(1, RunStatus.COMMITTED, "GET USER \"inspector\"", resolved)));

        assertTrue(strText.contains(">> GET USER \"inspector\""), strText);
        assertTrue(strText.contains("readAs   alice::22\n"), strText);
    }


    /** The Parameters tab holds them, and it outlives the run. */
    @Test
    void boundParametersAreNotPrinted() {
        ObjectNode resolved = MAPPER.createObjectNode();
        resolved.put("partyIdHint", "Alice");

        Binding binding = new Binding("alice", new DamlValue.Party("Alice::1220ab"),
                new DamlType.Prim(PrimKind.PARTY), 1, Optional.empty());

        Transcript transcript = new Transcript("script", Instant.EPOCH, false,
                List.of(entry(1, RunStatus.COMMITTED, "alice = ALLOCATE PARTY \"Alice\"",
                        resolved)),
                List.of(binding));

        String strText = CaqlResults.text(transcript);

        assertTrue(strText.contains(">> alice = ALLOCATE PARTY"), strText);
        assertFalse(strText.contains("$alice"), strText);
        assertFalse(strText.contains("bound"), strText);
    }


    private static Entry entry(int numLine, RunStatus status, String strSource,
            ObjectNode resolved) {
        return new Entry("id", numLine, strSource, status, Optional.empty(), resolved,
                Optional.empty(), Optional.empty(), Optional.empty());
    }


    private static Transcript transcript(Entry entry) {
        return new Transcript("script", Instant.EPOCH, false, List.of(entry), List.of());
    }

}
