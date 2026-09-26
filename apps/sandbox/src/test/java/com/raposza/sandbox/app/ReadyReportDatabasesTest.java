// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.topology.SandboxPorts;
import com.raposza.runtime.db.PostgresCoordinates;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The report has to be enough to CONNECT with. B-8.
 *
 * A consumer handed this walks `db.names` and finds a url, a role and a
 * password for each. That it can do so without knowing whether it is looking at
 * a 2.x domain or a 3.x sequencer is the point of the list existing at all.
 *
 * Author Claude/bentzn
 */
class ReadyReportDatabasesTest {

    private static final String STR_HOST = "127.0.0.1";

    private static final int N_PORT_PG = 33321;


    @Test
    void every_node_database_is_walkable_from_the_list() {
        ReadyReport report = report3x()
                .withDatabase("participant", coord("participant"))
                .withDatabase("sequencer", coord("sequencer"))
                .withDatabase("sequencer_driver", coord("sequencer_driver"))
                .withDatabase("mediator", coord("mediator"));

        List<String> lstDb = report.lstDatabase();
        assertEquals(List.of("participant", "sequencer", "sequencer_driver", "mediator"),
                lstDb);
        Map<String, String> map = report.map();
        for (String strNode : lstDb) {
            assertTrue(map.containsKey(ReadyReport.STR_PREFIX_JDBC_URL + strNode), strNode);
            assertTrue(map.containsKey(ReadyReport.STR_PREFIX_JDBC_USER + strNode), strNode);
            assertTrue(map.containsKey(ReadyReport.STR_PREFIX_JDBC_PASSWORD + strNode),
                    strNode);
        }
    }


    /**
     * THE KEY IS THE NODE AND THE URL NAMES THE DATABASE. A prefixed cluster
     * must not move the key a consumer asks for.
     */
    @Test
    void a_prefix_moves_the_database_and_not_the_key() {
        ReadyReport report = report3x()
                .withDatabase("sequencer", coord("foo_sequencer"));

        Map<String, String> map = report.map();
        assertTrue(map.get(ReadyReport.STR_PREFIX_JDBC_URL + "sequencer")
                .endsWith("/foo_sequencer"), map.get(ReadyReport.STR_PREFIX_JDBC_URL
                        + "sequencer"));
    }


    /** The host was always the loopback and was never written down. */
    @Test
    void the_host_is_stated() {
        assertEquals(STR_HOST, report3x().value(ReadyReport.KEY_HOST));
    }


    /** Recording the same node twice must not lengthen the list. */
    @Test
    void a_repeated_node_is_recorded_once() {
        ReadyReport report = report3x()
                .withDatabase("participant", coord("participant"))
                .withDatabase("participant", coord("participant"));

        assertEquals(1, report.lstDatabase().size());
    }


    /** Nothing recorded is an empty list rather than a null to test for. */
    @Test
    void no_database_is_an_empty_list() {
        ReadyReport report = report3x();

        assertTrue(report.lstDatabase().isEmpty());
        assertNull(report.value(ReadyReport.KEY_DB_NAMES));
    }


    private static PostgresCoordinates coord(String strDatabase) {
        return new PostgresCoordinates(STR_HOST, N_PORT_PG, strDatabase, "raposza",
                "raposza");
    }


    private static ReadyReport report3x() {
        SandboxPorts ports = new SandboxPorts(22211, 22212, 22213, 22214, 22215, 22216);
        return new ReadyReport("3.5.12", "OPEN_SOURCE", ports, N_PORT_PG, coord("participant"),
                Path.of("/run/work"), Path.of("/run/data"));
    }
}
