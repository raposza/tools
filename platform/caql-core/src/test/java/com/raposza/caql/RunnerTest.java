// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.ChoiceControllers;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.Command;
import com.raposza.api.model.Contract;
import com.raposza.api.model.ContractQuery;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;
import com.raposza.api.model.LedgerInfo;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.PrimKind;
import com.raposza.api.model.SubmitContext;
import com.raposza.api.model.SubmitResult;
import com.raposza.api.model.TemplateInfo;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UserInfo;
import com.raposza.spi.LedgerClient_i;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The runner, against a participant that answers exactly what a test told it
 * to. No ledger; the live exit criterion is a separate suite.
 *
 * Author Claude/bentzn
 */
class RunnerTest {

    private static final String PKG = "aaaa1111";
    private static final DataId ID_ACCOUNT = new DataId(PKG, "Main", "Account");
    private static final DataId ID_DEPOSIT_ARG = new DataId(PKG, "Main", "Deposit");
    private static final DataId ID_ARCHIVE_ARG = new DataId(PKG, "Main", "Archive");
    private static final DataId ID_OFFER = new DataId(PKG, "Main", "Offer");
    private static final DataId ID_HELD = new DataId(PKG, "Main", "Held");
    private static final DataId ID_IFACE_V1 = new DataId("cccc3333", "Api.V1", "Instruction");
    private static final DataId ID_IFACE_V2 = new DataId("dddd4444", "Api.V2", "Instruction");

    private final ObjectMapper mapper = new ObjectMapper();


    private static Transcript run(String strScript, FakeClient client, TypeRegistry_i registry,
            RunConfig config) {
        return new Runner(client, registry, config).run(strScript, CaqlParser.parse(strScript));
    }


    // ------------------------------------------------------------ happy path

    @Test
    void aWholeFixtureRunsAndTheTranscriptSaysWhatWasSent() {
        FakeClient client = new FakeClient();
        String strScript = """
                alice = ALLOCATE PARTY "Alice";
                bank  = ALLOCATE PARTY "Bank";
                CREATE USER "alice-app" WITH { "primaryParty": "$alice",
                    "rights": [ { "canActAs": "$alice" }, { "canReadAs": "$bank" } ] };
                acct = AS $bank CREATE Main:Account WITH { "owner": "$alice", "balance": "0.0" };
                AS $bank EXERCISE ON $acct Deposit WITH { "amount": "10.0" };
                AS $alice QUERY Main:Account;
                """;

        Transcript transcript = run(strScript, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        assertEquals(6, transcript.lstEntry().size());

        // The party the participant chose, not the hint the script wrote.
        Entry entryAlloc = transcript.lstEntry().get(0);
        assertEquals("Alice::fake", entryAlloc.resolved().get("party").asText());
        assertEquals("alice", entryAlloc.nameBind().orElseThrow());

        // The user payload's $alice resolved to the allocated id.
        assertEquals("Alice::fake",
                transcript.lstEntry().get(2).resolved().get("primaryParty").asText());
        assertEquals("Bank::fake",
                transcript.lstEntry().get(2).resolved().get("readAs").get(0).asText());

        // The create: concrete package, concrete party, canonical argument.
        Entry entryCreate = transcript.lstEntry().get(3);
        assertEquals(PKG, entryCreate.resolved().get("packageId").asText());
        assertEquals("Bank::fake", entryCreate.resolved().get("actAs").get(0).asText());
        assertEquals("Alice::fake",
                entryCreate.resolved().get("argument").get("owner").asText());
        assertTrue(entryCreate.idUpdate().isPresent());

        Entry entryExercise = transcript.lstEntry().get(4);
        assertEquals("Deposit", entryExercise.resolved().get("choice").asText());
        assertEquals("00cid1", entryExercise.resolved().get("contractId").asText());

        assertEquals(1, transcript.lstEntry().get(5).resolved().get("count").asInt());
        assertEquals(2, client.cntSubmit, "a create and an exercise, one submission each");
    }


    /** Sec. 8: one submission per command, so the ids differ per statement. */
    @Test
    void everySubmissionCarriesItsOwnCommandId() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                b = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "1.0" };
                """, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk());
        assertFalse(transcript.lstEntry().get(1).idCommand().orElseThrow()
                .equals(transcript.lstEntry().get(2).idCommand().orElseThrow()));
    }


    // ------------------------------------------------------------ failures

    /** Sec. 9: ambiguity FAILS, listing the candidates. Nothing is sent. */
    /**
     * Progress - his instruction, 2026-10-04. Each statement is reported as it
     * starts and as it ends, in order, with the entry the transcript carries.
     *
     * THE CONTROLS CAN FAIL: the second statement stops the run, so a sink told
     * about the third - a loop that reported before deciding to stop - would
     * record it; and a sink told only at the end would record the starts after
     * the finishes.
     */
    @Test
    void progressIsToldOfEachStatementRunAndOfNoneAfterTheStop() {
        List<String> lstEvent = new ArrayList<>();
        Runner runner = new Runner(new FakeClient(), registryTwoVersions(), RunConfig.ofRun());
        runner.useProgress(new RunProgress_i() {

            @Override
            public void started(int numStmt, int cntStmt, Stmt stmt) {
                lstEvent.add("start " + numStmt + "/" + cntStmt + " line " + stmt.numLine());
            }


            @Override
            public void finished(int numStmt, int cntStmt, Entry entry) {
                lstEvent.add("end " + numStmt + "/" + cntStmt + " " + entry.status());
            }
        });
        String strScript = """
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                other = ALLOCATE PARTY "Other";
                """;
        Transcript transcript = runner.run(strScript, CaqlParser.parse(strScript));

        assertEquals(2, transcript.lstEntry().size());
        assertEquals(List.of("start 1/3 line 1", "end 1/3 COMMITTED",
                "start 2/3 line 2", "end 2/3 FAILED_LOCALLY"), lstEvent);
    }


    @Test
    void anAmbiguousTemplateFailsLocallyAndNamesBothPackages() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                """, client, registryTwoVersions(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("bbbb2222"), entry.strError().get());
        assertEquals(0, client.cntSubmit, "an ambiguous reference must not reach the ledger");
    }


    /** Sec. 7: the consuming flag comes from the registry, so this is local. */
    @Test
    void aStaleBindingFailsLocallyAfterAConsumingChoice() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                AS $bank EXERCISE ON $a Archive;
                AS $bank EXERCISE ON $a Deposit WITH { "amount": "1.0" };
                """, client, registry(), RunConfig.ofRun());

        assertEquals(4, transcript.lstEntry().size());
        Entry entry = transcript.lstEntry().get(3);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("stale"), entry.strError().get());
    }


    @Test
    void anUnknownChoiceNamesWhatTheTemplateOffers() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                AS $bank EXERCISE ON $a Withdraw;
                """, client, registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(2);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("Deposit"), entry.strError().get());
    }


    /** A rejection is definitive, so the run stops and the rest is not attempted. */
    @Test
    void aRejectionStopsTheRun() {
        FakeClient client = new FakeClient();
        client.outcome = SubmitResult.rejected("CONTRACT_NOT_FOUND", "no such contract");

        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                b = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "1.0" };
                """, client, registry(), RunConfig.ofRun());

        assertEquals(2, transcript.lstEntry().size(), "the third statement must not run");
        assertEquals(RunStatus.REJECTED, transcript.lstEntry().get(1).status());
        assertTrue(transcript.lstEntry().get(1).strError().orElseThrow()
                .contains("CONTRACT_NOT_FOUND"));
        assertFalse(transcript.flagOk());
    }


    /**
     * The case sec. 8 exists for: the run stops and claims neither the
     * pre-command nor the post-command state.
     */
    @Test
    void anUnobservedOutcomeStopsTheRunAndSaysSo() {
        FakeClient client = new FakeClient();
        client.outcome = SubmitResult.unknown("DEADLINE_EXCEEDED", "no completion observed");

        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                b = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "1.0" };
                """, client, registry(), RunConfig.ofRun());

        assertEquals(2, transcript.lstEntry().size());
        assertEquals(RunStatus.OUTCOME_UNKNOWN, transcript.lstEntry().get(1).status());
        assertEquals(1, client.cntSubmit, "nothing may be sent after an unknown outcome");
    }


    /** A transport failure cannot say whether the command arrived. */
    @Test
    void aTransportFailureIsUnknownRatherThanLocal() {
        FakeClient client = new FakeClient();
        client.exSubmit = new IllegalStateException("connection reset");

        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                """, client, registry(), RunConfig.ofRun());

        assertEquals(RunStatus.OUTCOME_UNKNOWN, transcript.lstEntry().get(1).status());
    }


    /** Sec. 5.1: a user payload is validated against a fixed schema, not the registry. */
    @Test
    void anUnknownRightIsRefusedNamingTheSupportedSet() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                a = ALLOCATE PARTY "A";
                CREATE USER "u" WITH { "rights": [ { "participantAdmin": true } ] };
                """, client, registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("canActAs"), entry.strError().get());
    }


    // ------------------------------------------------------------ validate

    @Test
    void validateResolvesEverythingAndSendsNothing() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                """, client, registry(), RunConfig.ofValidate());

        assertTrue(transcript.flagOk());
        assertTrue(transcript.flagValidateOnly());
        for (Entry entry : transcript.lstEntry()) {
            assertEquals(RunStatus.VALIDATED, entry.status());
        }
        assertEquals(0, client.cntSubmit);
        assertEquals(0, client.cntAllocate, "VALIDATE must not allocate either");

        // The placeholder is bound with the right TYPE, which is what makes the
        // payload check below meaningful rather than vacuous.
        assertTrue(transcript.lstEntry().get(0).nameBind().isPresent());
    }


    /**
     * A bad payload is caught by VALIDATE, which is the point of the mode.
     *
     * The payload is otherwise COMPLETE on purpose. An earlier version omitted
     * balance as well, so the coercer reported the missing required field - it
     * reports the first problem it finds - and the test asserted on the second
     * one. Two faults in one fixture make the assertion a guess about ordering.
     */
    @Test
    void validateStillCatchesAnUnknownField() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0",
                    "nope": "x" };
                """, client, registry(), RunConfig.ofValidate());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("nope"), entry.strError().get());
    }


    /** The other half, stated separately so neither can hide the other. */
    @Test
    void validateStillCatchesAMissingRequiredField() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank" };
                """, client, registry(), RunConfig.ofValidate());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("balance"), entry.strError().get());
    }


    // ------------------------------------------------------------ artefacts

    /** The audit form is the transcript form minus source and argument. */
    @Test
    void theAuditFormCarriesNoPayload() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                """, client, registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        ObjectNode objFull = entry.json(mapper);
        assertTrue(objFull.has("source"));
        assertTrue(objFull.get("resolved").has("argument"));

        ObjectNode objAudit = entry.audit(mapper);
        assertFalse(objAudit.has("source"), "the audit log must never carry the source line");
        assertFalse(objAudit.get("resolved").has("argument"),
                "the audit log must never carry an argument");

        // Everything the log exists to answer is still there.
        assertTrue(objAudit.has("id"));
        assertTrue(objAudit.get("resolved").has("templateId"));
        assertTrue(objAudit.get("resolved").has("actAs"));
        assertEquals("committed", objAudit.get("status").asText());
        assertFalse(objAudit.get("updateId").isNull());
    }


    /** Building it by subtraction is what keeps the full form intact. */
    @Test
    void takingTheAuditFormDoesNotDamageTheTranscript() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                """, client, registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        entry.audit(mapper);
        assertTrue(entry.json(mapper).get("resolved").has("argument"));
    }


    /**
     * Sec. 8 wants an id that survives an edit. That is why it does NOT carry
     * the script hash: reindenting one statement would otherwise change every
     * id in the file, and editing statement 5 would move statements 1 to 4.
     */
    @Test
    void theStatementIdSurvivesReindentingButNotRewording() {
        FakeClient client = new FakeClient();
        String strA = "a = ALLOCATE PARTY \"A\";\n";
        String strB = "a    =   ALLOCATE PARTY \"A\";\n";
        String strC = "a = ALLOCATE PARTY \"B\";\n";

        String idA = run(strA, client, registry(), RunConfig.ofValidate()).lstEntry().get(0)
                .idStatement();
        String idB = run(strB, client, registry(), RunConfig.ofValidate()).lstEntry().get(0)
                .idStatement();
        String idC = run(strC, client, registry(), RunConfig.ofValidate()).lstEntry().get(0)
                .idStatement();

        assertEquals(idA, idB, "whitespace must not change the id");
        assertFalse(idA.equals(idC), "a different statement must not share an id");
    }


    /** The property the script hash would have destroyed. */
    @Test
    void editingOneStatementDoesNotMoveTheOthersIds() {
        FakeClient client = new FakeClient();
        String strOne = "a = ALLOCATE PARTY \"A\";\nb = ALLOCATE PARTY \"B\";\n";
        String strTwo = "a = ALLOCATE PARTY \"A\";\nb = ALLOCATE PARTY \"CHANGED\";\n";

        Transcript one = run(strOne, client, registry(), RunConfig.ofValidate());
        Transcript two = run(strTwo, client, registry(), RunConfig.ofValidate());

        assertEquals(one.lstEntry().get(0).idStatement(), two.lstEntry().get(0).idStatement(),
                "editing the second statement must not move the first one's id");
        assertFalse(one.lstEntry().get(1).idStatement()
                .equals(two.lstEntry().get(1).idStatement()));
    }


    /**
     * Sec. 6 says a structured result is "recorded in the transcript, usable
     * nowhere". It was usable nowhere and recorded nowhere either, which made
     * the inertness a silence rather than a decision.
     */
    @Test
    void theTranscriptRecordsWhatEachStatementPRODUCED() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                AS $bank EXERCISE ON $a Deposit WITH { "amount": "1.0" };
                """, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk());
        assertEquals("00cid1", transcript.lstEntry().get(1).resolved().get("contractId").asText(),
                "a create must say which contract appeared");
        assertTrue(transcript.lstEntry().get(2).resolved().has("result"),
                "an exercise must record its result");
    }


    /** The state the run ENDED in, which the entries answer only by being read end to end. */
    @Test
    void theTranscriptCarriesTheBindingsWithTheirValues() {
        FakeClient client = new FakeClient();
        Transcript transcript = run("""
                bank = ALLOCATE PARTY "Bank";
                a = AS $bank CREATE Main:Account WITH { "owner": "$bank", "balance": "0.0" };
                """, client, registry(), RunConfig.ofRun());

        assertEquals(2, transcript.lstBinding().size());

        JsonNode arr = transcript.json(mapper).get("bindings");
        assertEquals("bank", arr.get(0).get("name").asText());
        assertEquals("Bank::fake", arr.get(0).get("value").asText());
        assertEquals("a", arr.get(1).get("name").asText());
        assertEquals("00cid1", arr.get(1).get("value").asText());
    }


    // ------------------------------------------------------------ WHERE

    /** The sieve is applied over what the read returned, and both counts are recorded. */
    @Test
    void whereSievesTheReadAndRecordsBothCounts() {
        FakeClient client = new FakeClient();
        client.lstActive = List.of(account("00small", "Alice::x", "5.0"),
                account("00large", "Bob::x", "20.0"));

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                AS $bob QUERY Main:Account WHERE balance > 10.0;
                """, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        JsonNode resolved = transcript.lstEntry().get(1).resolved();
        assertEquals("balance > 10.0", resolved.get("where").asText());
        assertEquals(2, resolved.get("countRead").asInt());
        assertEquals(1, resolved.get("count").asInt());
        assertEquals(1, resolved.get("contractIds").size());
        assertEquals("00large", resolved.get("contractIds").get(0).asText());
        assertFalse(resolved.has("truncated"));
    }


    /** A binding on the right is substituted and type-checked by the same coercer a WITH uses. */
    @Test
    void whereComparesAgainstABindingAndRefusesOneOfTheWrongType() {
        FakeClient client = new FakeClient();
        client.lstActive = List.of(account("00alice", "Alice::fake", "5.0"),
                account("00bob", "Bob::fake", "5.0"));

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                AS $bob QUERY Main:Account WHERE owner = $bob;
                """, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        assertEquals("00bob",
                transcript.lstEntry().get(1).resolved().get("contractIds").get(0).asText());

        Transcript wrong = run("""
                bob = ALLOCATE PARTY "Bob";
                a = AS $bob CREATE Main:Account WITH { "owner": "$bob", "balance": "0.0" };
                AS $bob QUERY Main:Account WHERE owner = $a;
                """, client, registry(), RunConfig.ofRun());

        Entry entry = wrong.lstEntry().get(2);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("CONTRACT_ID"),
                entry.strError().orElseThrow());
    }


    /** A structural fault is decided before the read and names what the template offers. */
    @Test
    void whereRefusesAnUndeclaredFieldNamingTheOnesItOffers() {
        FakeClient client = new FakeClient();
        client.lstActive = List.of();

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                AS $bob QUERY Main:Account WHERE weight > 1;
                """, client, registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("owner, balance"),
                entry.strError().orElseThrow());
    }


    @Test
    void whereRefusesAnOrderingOnAParty() {
        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                AS $bob QUERY Main:Account WHERE owner > "a";
                """, new FakeClient(), registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("no ordering"),
                entry.strError().orElseThrow());
    }


    /** Missing data does not fail - it does not match. The fake's default payload has no fields. */
    @Test
    void whereTreatsAMissingFieldAsANonMatch() {
        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                AS $bob QUERY Main:Account WHERE balance = 5.0;
                """, new FakeClient(), registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        JsonNode resolved = transcript.lstEntry().get(1).resolved();
        assertEquals(1, resolved.get("countRead").asInt());
        assertEquals(0, resolved.get("count").asInt());
    }


    /** ASSERT COUNT counts what the filter left. */
    @Test
    void anAssertOverAFilteredQueryCountsAfterTheSieve() {
        FakeClient client = new FakeClient();
        client.lstActive = List.of(account("00a", "Alice::x", "5.0"),
                account("00b", "Bob::x", "20.0"), account("00c", "Bob::x", "30.0"));

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                ASSERT AS $bob QUERY Main:Account WHERE NOT balance < 10.0 COUNT 2;
                ASSERT AS $bob QUERY Main:Account WHERE balance < 10.0 OR balance >= 30.0 COUNT 3;
                """, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.lstEntry().get(1).status().flagContinue(),
                transcript.json(mapper).toString());
        Entry entryLast = transcript.lstEntry().get(2);
        assertEquals(RunStatus.FAILED_LOCALLY, entryLast.status());
        assertTrue(entryLast.strError().orElseThrow().contains("expected 3 and found 2"),
                entryLast.strError().orElseThrow());
    }


    /**
     * '= null' on an Optional of a RECORD is a None test, not a comparison of
     * the record - the Registry Utility's Holding, 'lock = null'. Refused before
     * 2026-10-04 because the element type was asked first whether it compares.
     */
    @Test
    void whereNullTestsAnOptionalRecordForNone() {
        DataId idLock = new DataId(PKG, "Main", "Lock");
        TemplateInfo held = new TemplateInfo(ID_HELD,
                List.of(new FieldInfo("owner", new DamlType.Prim(PrimKind.PARTY)),
                        new FieldInfo("lock", new DamlType.OptionalOf(new DamlType.Ref(idLock)))),
                List.of(), List.of(), Optional.empty());
        DamlValue.Rec lock = new DamlValue.Rec(idLock, List.of(
                new DamlValue.Rec.Field("context", new DamlValue.Text("offer"))));
        FakeClient client = new FakeClient();
        client.idTemplateContract = ID_HELD;
        client.lstActive = List.of(held("00free", new DamlValue.Opt(null)),
                held("00locked", new DamlValue.Opt(lock)));

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                free = AS $bob FETCH Main:Held WHERE lock = null SINGLE;
                """, client, new FakeRegistry(List.of(held)), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        assertEquals("00free",
                transcript.lstEntry().get(1).resolved().get("contractId").asText());
    }


    /** A comparison with a VALUE on an Optional record is still refused, naming the type. */
    @Test
    void whereStillRefusesComparingAnOptionalRecordWithAValue() {
        DataId idLock = new DataId(PKG, "Main", "Lock");
        TemplateInfo held = new TemplateInfo(ID_HELD,
                List.of(new FieldInfo("lock", new DamlType.OptionalOf(new DamlType.Ref(idLock)))),
                List.of(), List.of(), Optional.empty());

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                AS $bob QUERY Main:Held WHERE lock = "x";
                """, new FakeClient(), new FakeRegistry(List.of(held)), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("cannot be compared"),
                entry.strError().orElseThrow());
    }


    /** A Held contract: an owner and an optional lock. */
    private static Contract held(String idContract, DamlValue lock) {
        DamlValue.Rec payload = new DamlValue.Rec(ID_HELD, List.of(
                new DamlValue.Rec.Field("owner", new DamlValue.Party("Bob::x")),
                new DamlValue.Rec.Field("lock", lock)));
        return new Contract(idContract, "ev", ID_HELD, payload, List.of(), List.of(),
                Optional.empty(), "0", Optional.empty());
    }


    // ------------------------------------------------------------ FETCH ... WHERE ... SINGLE

    /** The one contract the sieve leaves is bound - a holding among several. */
    @Test
    void fetchSingleWithWhereBindsTheOneSurvivor() {
        FakeClient client = new FakeClient();
        client.lstActive = List.of(account("00small", "Alice::x", "5.0"),
                account("00large", "Bob::x", "20.0"));

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                big = AS $bob FETCH Main:Account WHERE balance > 10.0 SINGLE;
                """, client, registry(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        JsonNode resolved = transcript.lstEntry().get(1).resolved();
        assertEquals("00large", resolved.get("contractId").asText());
        assertEquals(2, resolved.get("countRead").asInt());
        assertEquals(1, resolved.get("count").asInt());
        assertEquals("balance > 10.0", resolved.get("where").asText());
    }


    /** Two survivors are refused: the active contract set has no order to pick by. */
    @Test
    void fetchSingleWithWhereRefusesTwoSurvivors() {
        FakeClient client = new FakeClient();
        client.lstActive = List.of(account("00small", "Alice::x", "5.0"),
                account("00large", "Bob::x", "20.0"));

        Transcript transcript = run("""
                bob = ALLOCATE PARTY "Bob";
                any = AS $bob FETCH Main:Account WHERE balance > 1.0 SINGLE;
                """, client, registry(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("more than one"), entry.strError().get());
    }


    // ------------------------------------------------------------ inherited choices

    /** Two interfaces declare Accept: refused, naming both and VIA, with nothing sent. */
    @Test
    void anInheritedChoiceTwoInterfacesDeclareIsRefusedNamingThem() {
        FakeClient client = new FakeClient();
        client.idTemplateContract = ID_OFFER;

        Transcript transcript = run("""
                p = ALLOCATE PARTY "P";
                AS $p EXERCISE ON "00off" Accept;
                """, client, registryOffer(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        String strError = entry.strError().orElseThrow();
        assertTrue(strError.contains("Api.V1:Instruction") && strError.contains("Api.V2:Instruction"), strError);
        assertTrue(strError.contains("VIA"), strError);
        assertEquals(0, client.cntSubmit);
    }


    /** VIA picks the interface, and the command is addressed to it, not to the template. */
    @Test
    void viaSendsTheChoiceToTheNamedInterface() {
        FakeClient client = new FakeClient();
        client.idTemplateContract = ID_OFFER;

        Transcript transcript = run("""
                p = ALLOCATE PARTY "P";
                AS $p EXERCISE ON "00off" Accept VIA Api.V2:Instruction;
                """, client, registryOffer(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        assertEquals(ID_IFACE_V2, ((Command.Exercise) client.cmdLast).idTemplate());
        assertEquals(ID_IFACE_V2.toString(),
                transcript.lstEntry().get(1).resolved().get("interfaceId").asText());
    }


    /** A choice only one interface declares needs no VIA and still goes to that interface. */
    @Test
    void anInheritedChoiceOneInterfaceDeclaresGoesToThatInterface() {
        FakeClient client = new FakeClient();
        client.idTemplateContract = ID_OFFER;

        Transcript transcript = run("""
                p = ALLOCATE PARTY "P";
                AS $p EXERCISE ON "00off" Describe;
                """, client, registryOffer(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        assertEquals(ID_IFACE_V1, ((Command.Exercise) client.cmdLast).idTemplate());
    }


    /** The template's own choice keeps going to the template. */
    @Test
    void aTemplatesOwnChoiceIsStillSentToTheTemplate() {
        FakeClient client = new FakeClient();
        client.idTemplateContract = ID_OFFER;

        Transcript transcript = run("""
                p = ALLOCATE PARTY "P";
                AS $p EXERCISE ON "00off" Archive;
                """, client, registryOffer(), RunConfig.ofRun());

        assertTrue(transcript.flagOk(), transcript.json(mapper).toString());
        assertEquals(ID_OFFER, ((Command.Exercise) client.cmdLast).idTemplate());
        assertFalse(transcript.lstEntry().get(1).resolved().has("interfaceId"));
    }


    /** VIA naming an interface the template does not implement is refused, listing what it does. */
    @Test
    void viaNamingAnInterfaceTheTemplateLacksIsRefused() {
        FakeClient client = new FakeClient();
        client.idTemplateContract = ID_OFFER;

        Transcript transcript = run("""
                p = ALLOCATE PARTY "P";
                AS $p EXERCISE ON "00off" Accept VIA Api.V3:Instruction;
                """, client, registryOffer(), RunConfig.ofRun());

        Entry entry = transcript.lstEntry().get(1);
        assertEquals(RunStatus.FAILED_LOCALLY, entry.status());
        assertTrue(entry.strError().orElseThrow().contains("Api.V1:Instruction"), entry.strError().get());
        assertEquals(0, client.cntSubmit);
    }


    @Test
    void anEmptyScriptRunsAndProducesAnEmptyTranscript() {
        Transcript transcript = run("-- nothing here\n", new FakeClient(), registry(),
                RunConfig.ofRun());

        assertTrue(transcript.lstEntry().isEmpty());
        assertTrue(transcript.flagOk());
        assertNull(transcript.json(mapper).get("statements").get(0));
    }


    // ------------------------------------------------------------ fixtures

    private static TemplateInfo account(DataId idTemplate) {
        ChoiceInfo deposit = new ChoiceInfo("Deposit", false, new DamlType.Ref(ID_DEPOSIT_ARG),
                new DamlType.Prim(PrimKind.CONTRACT_ID), new ChoiceControllers.Unresolved("test fixture"),
                Optional.empty());
        ChoiceInfo archive = new ChoiceInfo("Archive", true, new DamlType.Ref(ID_ARCHIVE_ARG),
                new DamlType.Prim(PrimKind.UNIT), new ChoiceControllers.Unresolved("test fixture"), Optional.empty());

        return new TemplateInfo(idTemplate,
                List.of(new FieldInfo("owner", new DamlType.Prim(PrimKind.PARTY)),
                        new FieldInfo("balance", new DamlType.Numeric(1))),
                List.of(deposit, archive), List.of(), Optional.empty());
    }


    private static TypeRegistry_i registry() {
        return new FakeRegistry(List.of(account(ID_ACCOUNT)));
    }


    /** An active Account contract with a payload the sieve can read. */
    private static Contract account(String idContract, String idOwner, String strBalance) {
        DamlValue.Rec payload = new DamlValue.Rec(ID_ACCOUNT, List.of(
                new DamlValue.Rec.Field("owner", new DamlValue.Party(idOwner)),
                new DamlValue.Rec.Field("balance",
                        new DamlValue.Decimal(new java.math.BigDecimal(strBalance)))));
        return new Contract(idContract, "ev", ID_ACCOUNT, payload, List.of(), List.of(),
                Optional.empty(), "0", Optional.empty());
    }


    private static ChoiceInfo choiceOn(String nameChoice, Optional<DataId> idInterface) {
        return new ChoiceInfo(nameChoice, true, new DamlType.Ref(ID_ARCHIVE_ARG),
                new DamlType.Prim(PrimKind.UNIT), new ChoiceControllers.Unresolved("test fixture"),
                idInterface);
    }


    /**
     * An Offer implementing two interfaces that BOTH declare Accept - the
     * shape of TransferOffer under TransferInstructionV1 and V2 - and one,
     * V1, that also declares Describe. Its choice list is the union as
     * ChoiceUnion builds it: Accept once, stamped with whichever came first.
     */
    private static TypeRegistry_i registryOffer() {
        TemplateInfo offer = new TemplateInfo(ID_OFFER, List.of(),
                List.of(choiceOn("Archive", Optional.empty()),
                        choiceOn("Accept", Optional.of(ID_IFACE_V1)),
                        choiceOn("Describe", Optional.of(ID_IFACE_V1))),
                List.of(ID_IFACE_V1, ID_IFACE_V2), Optional.empty());
        return new FakeRegistry(List.of(account(ID_ACCOUNT), offer), Map.of(
                ID_IFACE_V1, List.of(choiceOn("Accept", Optional.empty()), choiceOn("Describe", Optional.empty())),
                ID_IFACE_V2, List.of(choiceOn("Accept", Optional.empty()))));
    }


    private static TypeRegistry_i registryTwoVersions() {
        return new FakeRegistry(List.of(account(ID_ACCOUNT),
                account(new DataId("bbbb2222", "Main", "Account"))));
    }


    /** Answers what the test told it to; anything unarmed throws. */
    private static final class FakeClient implements LedgerClient_i {

        private SubmitResult outcome;
        private RuntimeException exSubmit;
        private int cntSubmit;
        private int cntAllocate;
        private int cntCid;

        /** What an active-contract read answers; null for the one-contract default. */
        private List<Contract> lstActive;

        /** The template a contract read reports. */
        private DataId idTemplateContract = ID_ACCOUNT;

        /** The last command submitted. */
        private Command cmdLast;


        @Override
        public PartyInfo allocateParty(String hintParty, String nameDisplay) {
            cntAllocate++;
            return new PartyInfo(hintParty + "::fake", hintParty, true);
        }


        @Override
        public UserInfo createUser(String idUser, String idPartyPrimary,
                List<String> lstPartyAct, List<String> lstPartyRead) {
            return new UserInfo(idUser, idPartyPrimary, false, lstPartyAct, lstPartyRead);
        }


        @Override
        public SubmitResult submit(Command cmd, SubmitContext ctx) {
            cntSubmit++;
            cmdLast = cmd;
            if (exSubmit != null)
                throw exSubmit;
            if (outcome != null)
                return outcome;

            if (cmd instanceof Command.Create create) {
                cntCid++;
                TxNode.Created created = new TxNode.Created("ev" + cntCid, "00cid" + cntCid,
                        create.idTemplate(), create.argument(), List.of(), List.of());
                return SubmitResult.committed("upd" + cntCid, tree("upd" + cntCid, created),
                        null);
            }

            Command.Exercise ex = (Command.Exercise) cmd;
            TxNode.Exercised exercised = new TxNode.Exercised("ev-x", ex.idContract(),
                    ex.idTemplate(), ex.nameChoice(), false, ex.argument(), null, List.of(),
                    List.of());
            return SubmitResult.committed("upd-x", tree("upd-x", exercised), new DamlValue.Unit());
        }


        private static TxTree tree(String idUpdate, TxNode node) {
            return new TxTree(idUpdate, Optional.empty(), Optional.empty(), "0",
                    Instant.EPOCH, List.of(node));
        }


        @Override
        public Optional<Contract> contract(String idContract, List<String> lstPartyRead) {
            return Optional.of(new Contract(idContract, "ev", idTemplateContract,
                    new DamlValue.Rec(idTemplateContract, List.of()), List.of(), List.of(),
                    Optional.empty(), "0", Optional.empty()));
        }


        @Override
        public List<Contract> activeContracts(ContractQuery query) {
            if (lstActive != null)
                return lstActive;
            return List.of(new Contract("00cid1", "ev", ID_ACCOUNT,
                    new DamlValue.Rec(ID_ACCOUNT, List.of()), List.of(), List.of(),
                    Optional.empty(), "0", Optional.empty()));
        }


        @Override
        public LedgerInfo info() {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public List<PartyInfo> parties() {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public List<UserInfo> users() {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public List<String> packageIds() {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public Optional<byte[]> archive(String idPackage) {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public Optional<TxTree> tree(String idUpdate, List<String> lstPartyRead) {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public Optional<TxTree> treeByEvent(String idEvent, List<String> lstPartyRead) {
            throw new UnsupportedOperationException("not armed");
        }


        @Override
        public void close() {
        }

    }


    private record FakeRegistry(List<TemplateInfo> lstTemplate,
            Map<DataId, List<ChoiceInfo>> mapInterface) implements TypeRegistry_i {

        FakeRegistry(List<TemplateInfo> lstTemplate) {
            this(lstTemplate, Map.of());
        }


        @Override
        public List<ChoiceInfo> interfaceChoices(DataId idInterface) {
            return mapInterface.getOrDefault(idInterface, List.of());
        }


        @Override
        public List<TemplateInfo> templates() {
            return lstTemplate;
        }


        @Override
        public Optional<TemplateInfo> template(DataId idTemplate) {
            return lstTemplate.stream().filter(t -> t.idTemplate().equals(idTemplate))
                    .findFirst();
        }


        @Override
        public List<TemplateInfo> templatesByName(String nameShort) {
            List<TemplateInfo> lstOut = new ArrayList<>();
            for (TemplateInfo info : lstTemplate) {
                if (info.idTemplate().shortName().equals(nameShort)
                        || info.idTemplate().nameEntity().equals(nameShort))
                    lstOut.add(info);
            }
            return List.copyOf(lstOut);
        }


        @Override
        public Optional<DataShape> shape(DataId idData) {
            Map<DataId, DataShape> mapShape = new LinkedHashMap<>();
            for (TemplateInfo info : lstTemplate) {
                mapShape.put(info.idTemplate(),
                        new DataShape.Rec(info.idTemplate(), List.of(), info.lstField()));
            }
            mapShape.put(ID_DEPOSIT_ARG, new DataShape.Rec(ID_DEPOSIT_ARG, List.of(),
                    List.of(new FieldInfo("amount", new DamlType.Numeric(1)))));
            // Archive's argument is a record with no fields, so EXERCISE Archive
            // needs no WITH. Giving it Deposit's argument was a fixture error and
            // it made a passing runner look broken.
            mapShape.put(ID_ARCHIVE_ARG,
                    new DataShape.Rec(ID_ARCHIVE_ARG, List.of(), List.of()));
            return Optional.ofNullable(mapShape.get(idData));
        }


        @Override
        public void refresh() {
        }

    }

}
