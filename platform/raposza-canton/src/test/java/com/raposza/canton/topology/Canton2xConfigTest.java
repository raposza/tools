// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import com.raposza.runtime.db.PostgresCoordinates;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class Canton2xConfigTest {

    private static PostgresCoordinates pg() {
        return new PostgresCoordinates("localhost", 33321, "raposza2x_participant", "postgres",
                "postgres");
    }


    private static Canton2xConfig config() {
        return Canton2xConfig.of(pg(), Sandbox2xPorts.ofDefaults());
    }


    @Test
    void theConfCarriesBothNodesAndTheirPorts() {
        String strConf = config().renderConf();

        assertTrue(strConf.contains("participants {"));
        assertTrue(strConf.contains(Canton2xConfig.STR_NODE_PARTICIPANT + " {"));
        assertTrue(strConf.contains("domains {"));
        assertTrue(strConf.contains(Canton2xConfig.STR_NODE_DOMAIN + " {"));
        assertTrue(strConf.contains("port = " + Sandbox2xPorts.N_DEFAULT_LEDGER_API));
        assertTrue(strConf.contains("port = " + Sandbox2xPorts.N_DEFAULT_ADMIN_API));
        assertTrue(strConf.contains("public-api { port = "
                + Sandbox2xPorts.N_DEFAULT_DOMAIN_PUBLIC + " }"));
        assertTrue(strConf.contains("admin-api { port = "
                + Sandbox2xPorts.N_DEFAULT_DOMAIN_ADMIN + " }"));
    }


    @Test
    void theParticipantIsOnPostgresAndTheDomainIsOnMemory() {
        String strConf = config().renderConf();

        assertTrue(strConf.contains("type = postgres"));
        assertTrue(strConf.contains("databaseName = \"raposza2x_participant\""));
        assertTrue(strConf.contains("type = memory"));
        assertTrue(strConf.contains("init.domain-parameters.protocol-version = "
                + Canton2xConfig.N_PROTOCOL_VERSION_DEFAULT));
    }


    /**
     * The dev switches are off by default and arrive together. Canton refuses
     * dev-version-support without non-standard-config, so one without the other
     * is a stack that will not start.
     */
    @Test
    void theDevSwitchesAreOffByDefaultAndArriveTogether() {
        String strPlain = config().renderConf();
        assertFalse(strPlain.contains("non-standard-config"));
        assertFalse(strPlain.contains("dev-version-support"));

        String strDev = new Canton2xConfig(Canton2xConfig.STR_NODE_PARTICIPANT,
                Canton2xConfig.STR_NODE_DOMAIN, pg(), Sandbox2xPorts.ofDefaults(), 5, true)
                        .renderConf();
        assertTrue(strDev.contains("non-standard-config = yes"));
        assertTrue(strDev.contains("dev-version-support = yes"));
        assertTrue(strDev.contains("enable-testing-commands = yes"));
    }


    /**
     * The bootstrap's order IS the topology, and the sentinel has to be last:
     * printed before the uploads it would report a stack ready that carries no
     * packages.
     */
    @Test
    void theBootstrapOrdersDomainThenParticipantThenDarsThenSentinel() {
        String strScript = config().renderBootstrap(List.of(Path.of("/tmp/model.dar")),
                Path.of("/tmp/work/participant-id.txt"));

        int idxDomain = strScript.indexOf(Canton2xConfig.STR_NODE_DOMAIN + ".start()");
        int idxParticipant = strScript.indexOf(Canton2xConfig.STR_NODE_PARTICIPANT + ".start()");
        int idxConnect = strScript.indexOf("domains.connect_local");
        int idxDar = strScript.indexOf("dars.upload");
        int idxReady = strScript.indexOf(Canton2xConfig.STR_READY);

        assertTrue(idxDomain >= 0 && idxParticipant > idxDomain);
        assertTrue(idxConnect > idxParticipant);
        assertTrue(idxDar > idxConnect);
        assertTrue(idxReady > idxDar);
        assertTrue(strScript.contains("/tmp/model.dar"));
    }


    /**
     * THE DOMAIN'S STORAGE IS THE SNAPSHOT'S PROBLEM. On memory it is not in
     * the cluster a snapshot copies, so a restored participant faces a domain
     * that was created empty a moment ago. Both shapes are rendered
     * here, because the memory one is still what a caller that never
     * snapshots gets.
     */
    @Test
    void theDomainIsOnPostgresWhenItIsGivenADatabase() {
        PostgresCoordinates pgParticipant =
                new PostgresCoordinates("localhost", 33321, "participant", "u", "p");
        PostgresCoordinates pgDomain =
                new PostgresCoordinates("localhost", 33321, "domain", "u", "p");

        String strMemory = new Canton2xConfig(Canton2xConfig.STR_NODE_PARTICIPANT,
                Canton2xConfig.STR_NODE_DOMAIN, pgParticipant, Sandbox2xPorts.ofDefaults(),
                Canton2xConfig.N_PROTOCOL_VERSION_DEFAULT, false).renderConf();
        assertTrue(strMemory.contains("type = memory"));

        String strPostgres = new Canton2xConfig(Canton2xConfig.STR_NODE_PARTICIPANT,
                Canton2xConfig.STR_NODE_DOMAIN, pgParticipant, pgDomain, Sandbox2xPorts.ofDefaults(),
                Canton2xConfig.N_PROTOCOL_VERSION_DEFAULT, false).renderConf();
        assertFalse(strPostgres.contains("type = memory"));
        assertTrue(strPostgres.contains("databaseName = \"domain\""));
        assertTrue(strPostgres.contains("databaseName = \"participant\""));
    }


    /**
     * A BLANK PREFIX IS NOT AN ERROR. `Canton2xConfig.databaseName` stopped
     * refusing one when the stacks changed to default to no prefix at all -
     * its own comment says so - and a blank has to mean the plain node name
     * rather than a rejection. This test went on asserting the rejection.
     *
     * It is asserted here in BOTH directions on purpose: the empty case is
     * now the DEFAULT path, which makes it the one worth pinning.
     */
    @Test
    void theDatabaseNameIsDerivedFromThePrefix() {
        assertEquals("raposza2x_participant", Canton2xConfig.databaseName("raposza2x"));
        assertEquals("raposza2x_domain", Canton2xConfig.databaseNameDomain("raposza2x"));
        assertEquals("domain", Canton2xConfig.databaseNameDomain(""));
        assertEquals("domain", Canton2xConfig.databaseNameDomain(null));
        assertEquals(Canton2xConfig.STR_DATABASE_PARTICIPANT,
                Canton2xConfig.databaseName(" "));
        assertEquals(Canton2xConfig.STR_DATABASE_PARTICIPANT,
                Canton2xConfig.databaseName(""));
        assertEquals(Canton2xConfig.STR_DATABASE_PARTICIPANT,
                Canton2xConfig.databaseName(null));
    }


    @Test
    void aProtocolVersionBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Canton2xConfig(Canton2xConfig.STR_NODE_PARTICIPANT,
                        Canton2xConfig.STR_NODE_DOMAIN, pg(), Sandbox2xPorts.ofDefaults(), 0,
                        false));
    }
}
