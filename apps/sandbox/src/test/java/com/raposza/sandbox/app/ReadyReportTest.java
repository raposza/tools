// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.topology.SandboxPorts;
import com.raposza.runtime.db.PostgresCoordinates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One set of facts, two renderings, and they have to agree.
 *
 * Author Claude/bentzn
 */
class ReadyReportTest {

    private static final PostgresCoordinates PG =
            new PostgresCoordinates("localhost", 33321, "raposza_participant", "postgres",
                    "postgres");


    @Test
    void thePrintedBlockAndTheFileCarryTheSameNumbers(@TempDir Path dirWork) throws Exception {
        SandboxPorts ports = SandboxPorts.ofDefaults();
        ReadyReport report = new ReadyReport("3.5.11", "ENTERPRISE", ports, 33321, PG, dirWork,
                null);

        List<String> lstLine = report.lstLines();
        String strBlock = String.join("\n", lstLine);
        assertTrue(strBlock.contains(String.valueOf(ports.nPortLedgerApi())));
        assertTrue(strBlock.contains(PG.jdbcUrl()));

        Path file = report.writeTo(dirWork);
        String strFile = Files.readString(file);
        assertTrue(strFile.contains(ReadyReport.KEY_LEDGER_API + "="
                + ports.nPortLedgerApi()));
        assertTrue(strFile.contains(ReadyReport.KEY_JDBC_PARTICIPANT + "=" + PG.jdbcUrl()));

        // The pid is in there because a status file that cannot be tied to a
        // process is a file that outlives one.
        assertEquals(String.valueOf(ProcessHandle.current().pid()),
                report.value(ReadyReport.KEY_PID));
    }


    @Test
    void whatWasNotAskedForIsAbsentRatherThanEmpty(@TempDir Path dirWork) {
        ReadyReport report = new ReadyReport("3.5.11", "OPEN_SOURCE", SandboxPorts.ofDefaults(),
                33321, PG, dirWork, null);

        assertNull(report.value(ReadyReport.KEY_DATA_DIR),
                "a temporary cluster has no data directory, and an empty value would read as one");
        assertNull(report.value(ReadyReport.KEY_PQS));
        assertFalse(report.map().containsKey(ReadyReport.KEY_PARTY_ID));

        report.with(ReadyReport.KEY_PARTY_ID, null);
        assertFalse(report.map().containsKey(ReadyReport.KEY_PARTY_ID),
                "a null still records nothing, so a bootstrap that allocated no party leaves no"
                        + " key rather than the string null");
    }


    @Test
    void theFileIsRemovedWhenTheStackStops(@TempDir Path dirWork) throws Exception {
        ReadyReport report = new ReadyReport("3.5.11", "UNKNOWN", SandboxPorts.ofDefaults(), 33321,
                PG, dirWork, dirWork.resolve("data"));
        Path file = report.writeTo(dirWork);
        assertTrue(Files.isRegularFile(file));

        ReadyReport.removeFrom(dirWork);
        assertFalse(Files.exists(file),
                "a status file left behind says a stack is up when it is not");

        // And again, because a hook can run after a failed start that never
        // wrote one.
        ReadyReport.removeFrom(dirWork);
    }
}
