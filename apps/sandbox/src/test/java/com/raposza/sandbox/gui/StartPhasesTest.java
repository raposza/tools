// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.StackComponent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every line quoted here is a VERBATIM line from a log taken on 2026-08-22 -
 * two participant runs, 3.5.12 and 3.4.6, and one embedded PostgreSQL. The
 * patterns match a vendor's prose, so the only defence against a wording
 * change is that the lines these were written from are kept where a failure
 * names them.
 *
 * Author Claude/bentzn
 */
class StartPhasesTest {

    private static final String PAR = StackComponent.STR_PARTICIPANT;

    private static final String PG = StackComponent.STR_POSTGRES;

    private static final String PQS = StackComponent.STR_PQS;


    @Test
    void theCantonStartReadsItsWayThrough() {
        assertEquals("version 3.5.12", StartPhases.strOf(PAR,
                "2026-08-22 08:06:39,053 [main] INFO  c.d.canton.CantonCommunityApp$"
                        + " - Starting Canton version 3.5.12"));
        assertEquals("resolving the configuration", StartPhases.strOf(PAR,
                "2026-08-22 08:06:40,084 [main] INFO  c.d.canton.CantonCommunityApp$"
                        + " - Starting up with resolved config"));
        assertEquals("starting the nodes", StartPhases.strOf(PAR,
                "2026-08-22 08:06:40,629 [main] INFO  c.d.c.environment.CantonEnvironment"
                        + " - Automatically starting all instances"));
        assertEquals("node sequencer1", StartPhases.strOf(PAR,
                "2026-08-22 08:06:40,655 [main] INFO  c.d.c.environment.SequencerNodes"
                        + " - Starting node sequencer1"));
        assertEquals("schemas for mediator1", StartPhases.strOf(PAR,
                "2026-08-22 08:06:44,866 [main] INFO  c.d.c.environment.MediatorNodes"
                        + " - Setting up database schemas for mediator1"));
    }


    @Test
    void flywayNamesTheMigrationItIsOn() {
        assertEquals("migrating to 998 - blocks", StartPhases.strOf(PAR,
                "2026-08-22 08:05:49,689 [x] INFO  o.f.core.internal.command.DbMigrate"
                        + " - Migrating schema \"public\" to version \"998 - blocks\""));
        assertEquals("migrating to 2.1 - lapi 3.0 views", StartPhases.strOf(PAR,
                "2026-08-22 08:23:28,295 [x] INFO  o.f.core.internal.command.DbMigrate"
                        + " - Migrating schema \"public\" to version \"2.1 - lapi 3.0 views\""));
    }


    @Test
    void theApiLinesCarryTheirPort() {
        assertEquals("ledger API on 25475", StartPhases.strOf(PAR,
                "2026-08-22 08:06:52,292 [x] INFO  c.d.c.p.a.ApiServiceOwner$:participant=sandbox"
                        + " - Initialized API server listening to port = 25475 without tls."));
        assertEquals("JSON API on 25474", StartPhases.strOf(PAR,
                "2026-08-22 08:06:54,919 [x] INFO  c.d.c.h.HttpApiServer$:participant=sandbox"
                        + " - HTTP JSON API Server started with (address=127.0.0.1, port=25474,"
                        + " portFile=None)"));
    }


    @Test
    void theLastThingACantonStartDoesIsTheScript() {
        assertEquals("every node is up", StartPhases.strOf(PAR,
                "2026-08-22 08:06:56,624 [main] INFO  c.d.c.environment.CantonEnvironment"
                        + " - Successfully started all nodes"));
        assertEquals("the bootstrap script", StartPhases.strOf(PAR,
                "2026-08-22 08:06:56,655 [main] INFO  c.digitalasset.canton.ServerRunner"
                        + " - Running script Some(/home/x/work/bootstrap.canton)"));
        assertEquals("the bootstrap script is done", StartPhases.strOf(PAR,
                "2026-08-22 08:07:01,303 [main] INFO  c.digitalasset.canton.ServerRunner"
                        + " - Bootstrap script successfully executed."));
    }


    @Test
    void initdbIsNamedStepByStep() {
        assertEquals("unpacking the binaries", StartPhases.strOf(PG,
                "[raposza-pg-start] INFO io.zonky.test.db.postgres.embedded.EmbeddedPostgres"
                        + " - Postgres binaries at /tmp/embedded-pg/PG-3f56119e"));
        assertEquals("initdb - subdirectories", StartPhases.strOf(PG,
                "[initdb:pid(1862598)] INFO io.zonky.test.db.postgres.embedded.EmbeddedPostgres"
                        + " - creating subdirectories ... ok"));
        assertEquals("initdb - syncing to disk", StartPhases.strOf(PG,
                "[initdb:pid(1862598)] INFO io.zonky.test.db.postgres.embedded.EmbeddedPostgres"
                        + " - syncing data to disk ... ok"));
        assertEquals("PostgreSQL 14.22", StartPhases.strOf(PG,
                "[postgres:pid(1862673)] INFO x - waiting for server to start....2026-08-22"
                        + " 08:35:37.358 WEST [1862677] LOG:  starting PostgreSQL 14.22 on"
                        + " x86_64-pc-linux-gnu"));
        assertEquals("accepting connections", StartPhases.strOf(PG,
                "[postgres:pid(1862673)] INFO x - 2026-08-22 08:35:37.370 WEST [1862677] LOG: "
                        + " database system is ready to accept connections"));
        assertEquals("database sequencer_driver", StartPhases.strOf(PG,
                "[sandbox-gui-start] INFO com.raposza.runtime.db.SandboxPostgres"
                        + " - created database sequencer_driver"));
    }


    @Test
    void anythingElseSaysNothing() {
        assertNull(StartPhases.strOf(PAR, "2026-08-22 08:06:46,135 [x] INFO"
                + "  c.d.c.r.DbLockedConnection:mediator=mediator1/connId=pool-21"
                + " - Successfully rebuilt connection"));
        assertNull(StartPhases.strOf(PG, ""));
        assertNull(StartPhases.strOf(PAR, null));
        assertNull(StartPhases.strOf(null, "Starting Canton version 3.5.12"));
        // PQS HAS ITS OWN TABLE AND MUST NOT BORROW THE PARTICIPANT'S. This
        // assertion used to read `PQS has no table`; it now proves the
        // stronger thing, which is that adding one did not make the two
        // components interchangeable.
        assertNull(StartPhases.strOf(StackComponent.STR_PQS, "Starting Canton version 3.5.12"));
        assertNull(StartPhases.strOf(PAR, "07:53:41.105 I [zio-fiber-126861014]"
                + " com.digitalasset.auth.TokenService:219 Successful auth token response"));
    }


    @Test
    void aPhaseIsShortEnoughForTheFooter() {
        String strLong = StartPhases.strOf(PAR, "Migrating schema \"public\" to version \""
                + "1.0 - " + "x".repeat(200) + "\"");
        assertTrue(strLong.length() <= StartPhases.N_PHASE_MAX, strLong);
    }

    @Test
    void theTwoTenStartReadsItsWayThroughToo() {
        // VERBATIM from the 2.10.2 log of 2026-08-22. The 2.x line starts no
        // named nodes, so the schema line is what says which one is coming up.
        assertEquals("version 2.10.2", StartPhases.strOf(PAR,
                "2026-08-22 08:19:33,009 [main] INFO  c.d.canton.CantonCommunityApp$"
                        + " - Starting Canton version 2.10.2"));
        assertEquals("schemas for mydomain", StartPhases.strOf(PAR,
                "2026-08-22 08:19:34,127 [x] INFO  c.d.canton.environment.DomainNodes"
                        + " - Setting up database schemas for mydomain"));
        assertEquals("the index schema - 99 migrations", StartPhases.strOf(PAR,
                "2026-08-22 08:19:37,394 [x] INFO  c.d.c.p.s.FlywayMigrations:participant=sandbox"
                        + " - Running Flyway migration on empty database with 99 migrations"
                        + " pending..."));
        // TRUNCATED, because Flyway migration names run long and the footer
        // does not. The prefix is what a reader is watching.
        String strMigration = StartPhases.strOf(PAR, "2026-08-22 08:19:38,515 [x] INFO "
                + " o.f.core.internal.command.DbMigrate - Migrating schema \"ledger_api\" to"
                + " version \"114 - activate string interning for parties and templates\"");
        assertTrue(strMigration.startsWith("migrating to 114 - activate"), strMigration);
        assertTrue(strMigration.length() <= StartPhases.N_PHASE_MAX, strMigration);
        assertEquals("the index schema is current", StartPhases.strOf(PAR,
                "2026-08-22 08:21:02,884 [x] INFO  c.d.c.p.s.FlywayMigrations:participant=sandbox"
                        + " - No pending migrations with 99 migrations applied."));
    }


    @Test
    void theTwoTenApiLineIsWordedDifferentlyAndCarriesTheSamePort() {
        assertEquals("ledger API on 25315", StartPhases.strOf(PAR,
                "2026-08-22 08:19:44,617 [x] INFO  c.d.c.p.a.ApiServiceOwner$:participant=sandbox"
                        + " - Initialized API server version {component version not found on"
                        + " classpath} with ledger-id = sandbox, port = 25315."));
        assertEquals("LF 1.14, 1.15, 1.17", StartPhases.strOf(PAR,
                "2026-08-22 08:19:44,373 [x] INFO  c.d.c.p.a.ApiServices$Owner:participant=sandbox"
                        + " - Daml-LF Engine supports LF versions: 1.14, 1.15, 1.17"));
    }


    @Test
    void theTwoTenDomainIsNamedAsADomain() {
        assertEquals("the node identity", StartPhases.strOf(PAR,
                "2026-08-22 08:21:01,715 [x] INFO  c.d.c.p.ParticipantNodeBootstrap"
                        + ":participant=sandbox - Resuming as existing instance with"
                        + " uid=NodeId(sandbox::1220fe6dcbab...)"));
        assertEquals("first-time initialisation", StartPhases.strOf(PAR,
                "2026-08-22 08:19:35,961 [x] INFO  c.d.c.d.DomainNodeBootstrap:domain=mydomain"
                        + " - Node is not initialized yet. Performing automated default"
                        + " initialization."));
        assertEquals("the sequencer is up", StartPhases.strOf(PAR,
                "2026-08-22 08:19:36,913 [x] INFO  c.d.c.d.s.SequencerRuntime:domain=mydomain"
                        + " - Sequencer is healthy"));
        assertEquals("registering the domain", StartPhases.strOf(PAR,
                "2026-08-22 08:19:49,093 [x] INFO  c.d.c.p.a.DomainConnectivityService"
                        + ":participant=sandbox - Registering mydomain with"
                        + " DomainConnectionConfig("));
        assertEquals("connecting to the domain", StartPhases.strOf(PAR,
                "2026-08-22 08:21:05,153 [x] INFO  c.d.c.p.s.CantonSyncService:participant=sandbox"
                        + " - Reconnecting to domains List(mydomain). Already connected: Set()"));
        assertEquals("the domain is connected", StartPhases.strOf(PAR,
                "2026-08-22 08:21:05,632 [x] INFO  c.d.c.p.s.CantonSyncService:participant=sandbox"
                        + " - Successfully re-connected to domains List(Domain 'mydomain')"));
    }


    /**
     * VERBATIM from the scribe v3.5.7 log of 2026-08-24, against a 3.5.12
     * participant, first start on an empty `pqs` schema. The ANSI colour codes
     * that sit between scribe's fields are dropped here: nothing matches on
     * them, and a test carrying escape sequences is one nobody can read.
     */
    @Test
    void thePqsStartReadsItsWayThroughToo() {
        assertEquals("resolving scribe", StartPhases.strOf(PQS,
                "[sandbox-gui-start] INFO com.raposza.canton.pqs.ScribeProcess"
                        + " - PQS: real scribe from /home/x/.pqs/line/3.5/scribe.jar"));
        assertEquals("the diagnostics server", StartPhases.strOf(PQS,
                "[diagnostics] Initialising diagnostics server with configuration:"));
        assertEquals("reading the configuration", StartPhases.strOf(PQS,
                "07:53:40.063 I [zio-fiber-493907118]"
                        + " com.digitalasset.scribe.configuration.package:52"
                        + " Applied configuration:"));
        assertEquals("scribe v3.5.7", StartPhases.strOf(PQS,
                "07:53:40.120 I [zio-fiber-493907118]"
                        + " com.digitalasset.scribe.appversion.package:17"
                        + " scribe, version: v3.5.7 (daml-sdk.version: 3.5.2,"
                        + " postgres-document.schema: 041)"));
        assertEquals("the target database answered", StartPhases.strOf(PQS,
                "07:53:40.551 I [zio-fiber-1973649863] zio.jdbc.shims.postgres:139"
                        + " Database probe (select 1) successful"));
        assertEquals("acquiring the auth token", StartPhases.strOf(PQS,
                "07:53:40.830 I [zio-fiber-126861014] com.digitalasset.auth.TokenService:202"
                        + " Acquiring auth token"));
        assertEquals("the auth token is in", StartPhases.strOf(PQS,
                "07:53:41.117 I [zio-fiber-126861014] com.digitalasset.auth.TokenService:231"
                        + " Token acquired, expires in PT24H."));
        assertEquals("the ledger API answered", StartPhases.strOf(PQS,
                "07:53:41.865 I [zio-fiber-284255723] com.digitalasset.zio.daml.Channel:49"
                        + " Keep-alive (get ledger version) successful"));
        assertEquals("30 packages on the ledger", StartPhases.strOf(PQS,
                "07:53:42.318 I [zio-fiber-168430143]"
                        + " com.digitalasset.zio.daml.ledgerapi.PackageService:27"
                        + " Listed 30 packages"));
        assertEquals("applying the schema", StartPhases.strOf(PQS,
                "07:53:42.690 I [zio-fiber-340051601]"
                        + " com.digitalasset.scribe.postgres.document.DocumentPostgres:46"
                        + " Applying schema"));
        assertEquals("42 migrations validated", StartPhases.strOf(PQS,
                "07:53:43.625 I [zio-fiber-1409336404]"
                        + " org.flywaydb.core.internal.command.DbValidate:"
                        + " Successfully validated 42 migrations (execution time 00:00.291s)"));
        assertEquals("migrating to 001 - Create initial schema", StartPhases.strOf(PQS,
                "07:53:43.740 I [zio-fiber-1903518457]"
                        + " org.flywaydb.core.internal.command.DbMigrate:"
                        + " Migrating schema \"pqs\" to version \"001 - Create initial schema\""));
        assertEquals("migrations applied", StartPhases.strOf(PQS,
                "07:53:44.499 I [zio-fiber-485682839]"
                        + " org.flywaydb.core.internal.command.DbMigrate: Successfully applied"
                        + " 42 migrations to schema \"pqs\", now at version v041"));
        assertEquals("the schema is current", StartPhases.strOf(PQS,
                "07:53:46.243 I [zio-fiber-1100631116]"
                        + " org.flywaydb.core.internal.command.DbMigrate:"
                        + " Schema \"pqs\" is up to date. No migration necessary."));
        assertEquals("the schema is done", StartPhases.strOf(PQS,
                "07:53:44.658 I [zio-fiber-340051601]"
                        + " com.digitalasset.scribe.postgres.document.DocumentPostgres:99"
                        + " Schema and mappings applied"));
        assertEquals("18 exercise types", StartPhases.strOf(PQS,
                "07:53:44.684 I [zio-fiber-340051601]"
                        + " com.digitalasset.scribe.postgres.document.DocumentPostgres:137"
                        + " Initialised 18 exercise types"));
        assertEquals("reading the user rights", StartPhases.strOf(PQS,
                "07:53:44.771 I [zio-fiber-493907118]"
                        + " com.digitalasset.zio.daml.ledgerapi.PartiesService:67"
                        + " Retrieved 1 user rights"));
    }


    /**
     * THE ONE THAT MATTERS. A first PQS start against a ledger with no party
     * on it retries forever, and until these two branches existed the footer
     * said `Starting PQS` throughout and a reader could not tell it from a
     * hang.
     */
    @Test
    void theWaitForAPartyIsNamed() {
        assertEquals("waiting for a party on the ledger", StartPhases.strOf(PQS,
                "Suppressed: io.grpc.StatusException: UNAVAILABLE:"
                        + " No parties found matching `*`."));
        assertEquals("retrying - attempt 7", StartPhases.strOf(PQS,
                "07:54:50.721 I [zio-fiber-493907118]"
                        + " com.digitalasset.scribe.pipeline.Retry.retryRecoverable:62"
                        + " Recoverable GRPC exception. Attempt 7, unstable for 1 minute"
                        + " 5 seconds."));
    }


    @Test
    void theSandboxOwnLinesAreNamedToo() {
        assertEquals("writing the topology", StartPhases.strOf(PAR,
                "[sandbox-gui-start] INFO com.raposza.sandbox.Sandbox2xStack"
                        + " - topology written to /home/x/work/canton.conf"));
        assertEquals("writing the bootstrap script", StartPhases.strOf(PAR,
                "[sandbox-gui-start] INFO com.raposza.sandbox.Sandbox2xStack"
                        + " - bootstrap written to /home/x/work/bootstrap.canton"));
    }
}
