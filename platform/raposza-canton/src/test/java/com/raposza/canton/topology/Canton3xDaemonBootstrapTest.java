// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The script that is shorter than the vendor's, the two lines it must not grow
 * back, and the two guards it must not lose.
 *
 * Author Claude/bentzn
 */
class Canton3xDaemonBootstrapTest {

    private static final Path DIR_WORK = Paths.get("/tmp/raposza-daemon-test");

    private static final String STR_HINT = "alice";

    private static final String STR_USER = "alice-user";

    /** Two, because order is a property this script has to keep. */
    private static final List<Path> LST_DAR =
            List.of(Paths.get("/tmp/one.dar"), Paths.get("/tmp/two.dar"));


    private static Canton3xDaemonBootstrap provisioning() {
        return new Canton3xDaemonBootstrap().withPartyAndUser(STR_HINT, STR_USER);
    }


    @Test
    void theConnectIsGuardedAndTheProposeIsAbsent() {
        String strScript = new Canton3xDaemonBootstrap().render(DIR_WORK);

        assertTrue(strScript.contains("is_connected"),
                "the connect is unguarded, which is what fails a second start");
        assertTrue(strScript.contains("connect_local"), strScript);

        // The line whose absence is the whole restart. It sets a
        // multi-synchronizer feature flag this sandbox does not use.
        assertFalse(strScript.contains("synchronizer_trust_certificates"),
                "the propose is back, and a second start will fail on it");
        assertFalse(strScript.contains("EnableMultiSynchronizer"), strScript);

        // The vendor's own multi-sync fixture scaffolding.
        assertFalse(strScript.contains("sidebox"), strScript);
    }


    @Test
    void theConnectPassesItsArgumentsByName() {
        String strScript = new Canton3xDaemonBootstrap().render(DIR_WORK);

        assertTrue(strScript.contains("sequencer = "), strScript);
        assertTrue(strScript.contains("alias = "), strScript);
    }


    @Test
    void theMarkerIsWrittenBeforeAnythingThatCanFail() {
        String strScript = new Canton3xDaemonBootstrap().render(DIR_WORK);

        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_MARKER), strScript);
        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_PARTICIPANT_ID_FILE), strScript);
        assertTrue(strScript.indexOf(Canton3xDaemonBootstrap.STR_MARKER_FILE)
                < strScript.indexOf("bootstrap.synchronizer"),
                "the marker is written after a call that can fail, so it can no longer"
                        + " distinguish 'the script never ran' from 'the script failed'");
    }


    /**
     * The default is still bare. Provisioning is opt-in because the caller
     * that wants a restartable stack and the caller that wants a usable one
     * are not always the same caller.
     */
    @Test
    void nothingIsProvisionedByDefault() {
        Canton3xDaemonBootstrap bootstrap = new Canton3xDaemonBootstrap();
        String strScript = bootstrap.render(DIR_WORK);

        assertFalse(bootstrap.flagProvisions());
        assertFalse(bootstrap.flagPings());
        assertFalse(strScript.contains("parties.allocate"), strScript);
        assertFalse(strScript.contains("users.create"), strScript);
        assertFalse(strScript.contains("health.ping"), strScript);
    }


    /**
     * The allocate is reached only when a prefix match found nothing.
     *
     * A repeat allocate throws `CommandFailure: Command execution failed.` -
     * measured on 3.5.11 - and so does every other console failure, so the
     * guard cannot be a catch around the call.
     */
    @Test
    void theAllocateIsGuardedByAPrefixMatch() {
        String strScript = provisioning().render(DIR_WORK);

        assertTrue(strScript.contains("ledger_api.parties.list()"), strScript);
        assertTrue(strScript.contains("startsWith(_prefix)"), strScript);
        assertTrue(strScript.contains("_found.isEmpty"), strScript);
        assertTrue(strScript.contains("ledger_api.parties.allocate"), strScript);

        // The hint is not the id: allocate(party = "alice") returns
        // alice::1220..., so a guard comparing with the hint never fires and
        // the separator is what makes the match exact on the segment.
        assertTrue(strScript.contains("\"" + STR_HINT + "::\""),
                "the prefix does not carry the namespace separator, so it would match a"
                        + " different party whose hint merely begins the same way");
    }


    /**
     * `list`'s `filterParty` help says "Filter party by name" and does not say
     * whether "name" is the hint segment or the whole id. If it were the
     * latter the filtered list would come back empty on a restart, the guard
     * would never fire, and the allocate behind it would throw.
     */
    @Test
    void theListFilterIsNotUsed() {
        String strScript = provisioning().render(DIR_WORK);

        assertFalse(strScript.contains("filterParty"), strScript);
    }


    /**
     * The catch wraps `get` and NOT `create`. A participant that is genuinely
     * broken fails `get`, reaches `create`, and fails there loudly - which is
     * what a catch around both would have swallowed.
     */
    @Test
    void theUserGuardCatchesOnlyTheGet() {
        String strScript = provisioning().render(DIR_WORK);

        assertTrue(strScript.contains("ledger_api.users.get(id = "), strScript);
        assertTrue(strScript.contains("com.digitalasset.canton.console.CommandFailure"),
                strScript);

        int nGet = strScript.indexOf("ledger_api.users.get");
        int nCatch = strScript.indexOf("catch {");
        int nCreate = strScript.indexOf("ledger_api.users.create");
        assertTrue(nGet < nCatch && nCatch < nCreate,
                "the create is not inside the catch, so a failure that is not"
                        + " 'no such user' can be reported as one:\n" + strScript);
    }


    /**
     * Both sentinels are written outside their branches, so `party-id.txt`
     * says what the party IS rather than whether this start created it. A
     * restart that found an existing party still writes it.
     */
    @Test
    void theProvisioningSentinelsAreWrittenUnconditionally() {
        String strScript = provisioning().render(DIR_WORK);

        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_PARTY_ID_FILE), strScript);
        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_USER_ID_FILE), strScript);
        assertTrue(strScript.indexOf("_found.head") < strScript
                .indexOf(Canton3xDaemonBootstrap.STR_PARTY_ID_FILE),
                "the party sentinel is written inside a branch:\n" + strScript);
    }


    /**
     * The guard builds its prefix by appending the separator, so a hint that
     * already carries one produces a match that can never fire.
     */
    @Test
    void aHintCarryingTheNamespaceSeparatorIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap().withPartyAndUser("alice::1220ab", "u"));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap().withPartyAndUser("", "u"));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap().withPartyAndUser("alice", " "));
    }


    @Test
    void theProvisioningIsCarriedAcrossWithPing() {
        Canton3xDaemonBootstrap bootstrap = provisioning().withPing();

        assertTrue(bootstrap.flagProvisions());
        assertTrue(bootstrap.flagPings());
        assertEquals(STR_HINT, bootstrap.strPartyHint());
        assertEquals(STR_USER, bootstrap.strUserId());

        String strScript = bootstrap.render(DIR_WORK);
        assertTrue(strScript.contains("health.ping"), strScript);
        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_PING_FILE), strScript);
    }


    /**
     * The readiness sentinel is written after every other statement.
     *
     * This is the assertion that would have caught it before a live run:
     * the participant id is written early, so anything that treats it as
     * readiness is waiting for the middle of the script.
     */
    @Test
    void theReadySentinelIsWrittenAfterEverythingElse() {
        String strScript = provisioning().withPing().render(DIR_WORK);

        int nReady = strScript.indexOf(Canton3xDaemonBootstrap.STR_READY_FILE);
        assertTrue(nReady > 0, strScript);
        assertTrue(nReady > strScript.indexOf(Canton3xDaemonBootstrap.STR_PARTICIPANT_ID_FILE),
                strScript);
        assertTrue(nReady > strScript.indexOf(Canton3xDaemonBootstrap.STR_PARTY_ID_FILE),
                strScript);
        assertTrue(nReady > strScript.indexOf(Canton3xDaemonBootstrap.STR_USER_ID_FILE),
                strScript);
        assertTrue(nReady > strScript.indexOf(Canton3xDaemonBootstrap.STR_PING_FILE), strScript);
        assertTrue(nReady < strScript.indexOf(Canton3xDaemonBootstrap.STR_DONE), strScript);
    }


    /**
     * The last line says the stack is up. It does NOT end the JVM.
     *
     * This script is interpreted inside the daemon's own process, so the
     * `sys.exit(0)` that used to sit here was `System.exit(0)` on the stack:
     * the daemon started every node, ran the script and shut itself down with
     * exit code 0. The console script keeps its exit - it is a separate
     * process and is meant to end.
     */
    @Test
    void theScriptEndsBySayingSoAndDoesNotEndTheJvm() {
        String strScript = provisioning().withPing().render(DIR_WORK);

        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_DONE), strScript);
        assertTrue(strScript.trim().endsWith("println(\"" + Canton3xDaemonBootstrap.STR_DONE
                + "\")"), strScript);
        assertFalse(strScript.contains("sys.exit"),
                "this script runs INSIDE the daemon JVM; sys.exit ends the daemon:\n"
                        + strScript);
        assertFalse(strScript.contains("System.exit"), strScript);
    }


    @Test
    void theNodeNamesDefaultToTheVendorTopologys() {
        Canton3xDaemonBootstrap bootstrap = new Canton3xDaemonBootstrap();

        assertTrue(StorageOverlay.STR_NODE_PARTICIPANT.equals(bootstrap.strParticipant()));
        assertTrue(StorageOverlay.STR_NODE_SEQUENCER.equals(bootstrap.strSequencer()));
        assertTrue(StorageOverlay.STR_NODE_MEDIATOR.equals(bootstrap.strMediator()));
    }


    @Test
    void everyNameIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap("", "s", "m", "sync"));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap("p", null, "m", "sync"));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap().render(null));
    }


    /**
     * A Windows path written verbatim into a Scala string literal is a parse
     * error a long way from its cause.
     *
     * The DARs are in this one deliberately. They are the only part of the
     * script that renders a path the caller supplied, so they are the most
     * likely place for a backslash to arrive - and `mkString` takes
     * `System.lineSeparator()` rather than a `"\n"` literal for the same
     * reason this assertion exists.
     */
    @Test
    void backslashesDoNotReachTheScript() {
        String strScript = provisioning().withPing().withDars(LST_DAR).render(DIR_WORK);

        assertFalse(strScript.contains("\\"), strScript);
    }


    /**
     * The uploads are plain calls, and the ABSENCE of a guard is the assertion.
     *
     * Every other repeat in this script is asked about first, so a reader
     * meeting `dars.upload` bare will reasonably suspect it was forgotten.
     * It was measured: on 3.5.11 the second upload of one DAR succeeded and
     * returned the same main package id as the first.
     */
    @Test
    void theDarUploadsAreUnguarded() {
        String strScript = new Canton3xDaemonBootstrap().withDars(LST_DAR).render(DIR_WORK);

        // The FILENAME, not the absolute path: the path is absolutised at
        // render time, so asserting `/tmp/one.dar` passes here and fails on a
        // Windows run for a reason that has nothing to do with this test.
        assertTrue(strScript.contains(".dars.upload(\""), strScript);
        assertTrue(strScript.contains("one.dar\")"), strScript);
        assertTrue(strScript.contains("two.dar\")"), strScript);

        // The shapes a guard would have. None of them belongs here.
        assertFalse(strScript.contains("dars.list"), strScript);
        assertFalse(strScript.contains("dars.validate"), strScript);
        assertFalse(strScript.contains("get_contents"), strScript);
    }


    /**
     * In the order given, because a DAR can depend on one uploaded before it.
     */
    @Test
    void theDarsAreUploadedInOrder() {
        String strScript = new Canton3xDaemonBootstrap().withDars(LST_DAR).render(DIR_WORK);

        assertTrue(strScript.indexOf("one.dar") < strScript.indexOf("two.dar"), strScript);
    }


    /**
     * The ids come from the uploads' own return values, so the sentinel cannot
     * drift from what the participant actually took.
     */
    @Test
    void theDarSentinelIsWrittenFromTheReturnValues() {
        String strScript = new Canton3xDaemonBootstrap().withDars(LST_DAR).render(DIR_WORK);

        assertTrue(strScript.contains("val _dars = Seq("), strScript);
        assertTrue(strScript.contains(Canton3xDaemonBootstrap.STR_DAR_FILE), strScript);
        assertTrue(strScript.contains("_dars.mkString("), strScript);
        assertTrue(strScript.indexOf("two.dar")
                < strScript.indexOf(Canton3xDaemonBootstrap.STR_DAR_FILE),
                "the sentinel is written before the uploads it reports:\n" + strScript);
    }


    /**
     * A package is what a party is going to be asked to use, and the 2.x
     * script already renders that order.
     */
    @Test
    void theDarsGoUpBeforeTheProvisioning() {
        String strScript = provisioning().withDars(LST_DAR).render(DIR_WORK);

        assertTrue(strScript.indexOf("dars.upload") < strScript.indexOf("parties.list()"),
                strScript);
    }


    /**
     * The readiness sentinel still comes last, with the DARs in front of it.
     * The defect it guards was a sentinel that reported the middle of a script.
     */
    @Test
    void theReadySentinelStillFollowsTheDars() {
        String strScript = provisioning().withPing().withDars(LST_DAR).render(DIR_WORK);

        assertTrue(strScript.indexOf(Canton3xDaemonBootstrap.STR_READY_FILE)
                > strScript.indexOf(Canton3xDaemonBootstrap.STR_DAR_FILE), strScript);
    }


    @Test
    void noDarsAreUploadedByDefault() {
        Canton3xDaemonBootstrap bootstrap = new Canton3xDaemonBootstrap();

        assertFalse(bootstrap.flagUploadsDars());
        assertTrue(bootstrap.lstFileDar().isEmpty());
        assertFalse(bootstrap.render(DIR_WORK).contains("dars.upload"));

        // Null and empty both mean none, and neither throws: a caller passing
        // `spec.lstFileDar()` straight through should not have to check first.
        assertFalse(new Canton3xDaemonBootstrap().withDars(null).flagUploadsDars());
        assertFalse(new Canton3xDaemonBootstrap().withDars(List.of()).flagUploadsDars());
    }


    @Test
    void theDarsAreCarriedAcrossTheOtherBuilders() {
        Canton3xDaemonBootstrap bootstrap = new Canton3xDaemonBootstrap().withDars(LST_DAR)
                .withPartyAndUser(STR_HINT, STR_USER).withPing();

        assertEquals(LST_DAR, bootstrap.lstFileDar());
        assertTrue(bootstrap.flagProvisions());
        assertTrue(bootstrap.flagPings());
    }
}
