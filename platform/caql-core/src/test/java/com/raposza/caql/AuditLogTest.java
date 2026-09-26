// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The audit log: what it records, what it must never record, and which
 * statements owe a record at all.
 *
 * Author Claude/bentzn
 */
class AuditLogTest {

    private final ObjectMapper mapper = new ObjectMapper();


    private Entry entry(RunStatus status) {
        ObjectNode resolved = mapper.createObjectNode();
        resolved.put("templateId", "aaaa:Main:Account");
        resolved.putArray("actAs").add("Bank::1220ab");
        resolved.set("argument", mapper.createObjectNode().put("balance", "1250.0"));

        return new Entry("a1f3c9", 14, "AS $bank CREATE Main:Account WITH {\"balance\":\"1250.0\"}",
                status, Optional.of("acct"), resolved, Optional.of("caql-a1f3c9-1"),
                Optional.of("1220f0"), Optional.empty());
    }


    private static Stmt stmtCreate() {
        return CaqlParser.parse("AS $b CREATE Main:Account WITH {};").get(0);
    }


    // ------------------------------------------------------- what it carries

    @Test
    void aRecordCarriesTheIdsAndTheOutcomeAndNoPayload(@TempDir Path dirTmp) throws Exception {
        Path fileLog = dirTmp.resolve("audit.jsonl");
        new AuditLog(fileLog).append(entry(RunStatus.COMMITTED), "IT sandbox", Instant.EPOCH);

        JsonNode node = mapper.readTree(Files.readString(fileLog).trim());

        assertEquals("a1f3c9", node.get("id").asText());
        assertEquals("committed", node.get("status").asText());
        assertEquals("caql-a1f3c9-1", node.get("commandId").asText());
        assertEquals("1220f0", node.get("updateId").asText());
        assertEquals("IT sandbox", node.get("participant").asText());
        assertEquals(Instant.EPOCH.toString(), node.get("at").asText());
        assertEquals("aaaa:Main:Account", node.get("resolved").get("templateId").asText());
        assertEquals("Bank::1220ab", node.get("resolved").get("actAs").get(0).asText());

        // The two the log must never hold.
        assertFalse(node.has("source"), "the audit log carried the operator's source line");
        assertFalse(node.get("resolved").has("argument"),
                "the audit log carried a payload");
    }


    /** Accumulates: one object per line, appended, never rewritten. */
    @Test
    void recordsAccumulateOnePerLine(@TempDir Path dirTmp) throws Exception {
        Path fileLog = dirTmp.resolve("audit.jsonl");
        AuditLog log = new AuditLog(fileLog);

        log.append(entry(RunStatus.COMMITTED), "p", Instant.EPOCH);
        log.append(entry(RunStatus.REJECTED), "p", Instant.EPOCH);
        log.append(entry(RunStatus.OUTCOME_UNKNOWN), "p", Instant.EPOCH);

        List<String> lstLine = Files.readAllLines(fileLog);
        assertEquals(3, lstLine.size());
        for (String strLine : lstLine) {
            mapper.readTree(strLine);
        }
        assertEquals("outcome-unknown", mapper.readTree(lstLine.get(2)).get("status").asText());
    }


    /** The directory is made on first write; nothing has to exist beforehand. */
    @Test
    void theDirectoryIsCreatedOnFirstWrite(@TempDir Path dirTmp) throws Exception {
        Path fileLog = dirTmp.resolve("nested").resolve("deeper").resolve("audit.jsonl");
        new AuditLog(fileLog).append(entry(RunStatus.COMMITTED), "p", Instant.EPOCH);

        assertTrue(Files.isRegularFile(fileLog));
    }


    /**
     * A log that silently fails to record a submission is worse than no log,
     * because it will be believed.
     */
    @Test
    void anUnwritableLogThrowsRatherThanLosingTheRecord(@TempDir Path dirTmp) throws Exception {
        Path fileBlocking = dirTmp.resolve("blocked");
        Files.writeString(fileBlocking, "not a directory");

        AuditLog log = new AuditLog(fileBlocking.resolve("audit.jsonl"));
        assertThrows(UncheckedIOException.class,
                () -> log.append(entry(RunStatus.COMMITTED), "p", Instant.EPOCH));
    }


    // ------------------------------------------------------- who owes a record

    /** The unit is the OPERATION: changed the participant AND reached it. */
    @Test
    void onlyMutatingStatementsThatReachedTheParticipantOweARecord() {
        Stmt create = stmtCreate();
        assertTrue(AuditLog.flagAudited(create, RunStatus.COMMITTED));
        assertTrue(AuditLog.flagAudited(create, RunStatus.REJECTED));
        assertTrue(AuditLog.flagAudited(create, RunStatus.OUTCOME_UNKNOWN),
                "an unobserved outcome is the record most worth having");

        // Never left this machine.
        assertFalse(AuditLog.flagAudited(create, RunStatus.FAILED_LOCALLY));
        // Nothing was sent.
        assertFalse(AuditLog.flagAudited(create, RunStatus.VALIDATED));
    }


    /** A read changes nothing, so it writes nothing - sec. 10. */
    @Test
    void readsOweNoRecord() {
        Stmt query = CaqlParser.parse("AS $b QUERY Main:Account;").get(0);
        Stmt fetch = CaqlParser.parse("x = AS $b FETCH \"00ab\";").get(0);

        assertFalse(AuditLog.flagAudited(query, RunStatus.COMMITTED));
        assertFalse(AuditLog.flagAudited(fetch, RunStatus.COMMITTED));
    }


    /** Allocation and user creation change the participant, so they do. */
    @Test
    void administrativeWritesOweARecord() {
        Stmt allocate = CaqlParser.parse("p = ALLOCATE PARTY \"A\";").get(0);
        Stmt user = CaqlParser.parse("CREATE USER \"u\" WITH {};").get(0);

        assertTrue(AuditLog.flagAudited(allocate, RunStatus.COMMITTED));
        assertTrue(AuditLog.flagAudited(user, RunStatus.COMMITTED));
    }

}
