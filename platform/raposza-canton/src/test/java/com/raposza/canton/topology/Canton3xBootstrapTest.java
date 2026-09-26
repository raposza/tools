// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the script must and must not contain.
 *
 * The must-nots carry the weight. The 2.x bootstrap starts nodes and connects a
 * domain because on that line nothing else does; the 3.x subcommand has already
 * done both by the time this script runs, and a `start()` or a `connect_local`
 * copied across would fail against a node that is already up - late, and
 * reading like a topology fault rather than a duplicated instruction.
 *
 * Author Claude/bentzn
 */
class Canton3xBootstrapTest {

    private static final Path DIR_WORK = Path.of("/tmp", "raposza-3x");


    @Test
    void theMarkerIsWrittenBeforeAnyConsoleCall() {
        String strScript = new Canton3xBootstrap().render(DIR_WORK);

        int idxMarker = strScript.indexOf(Canton3xBootstrap.STR_MARKER);
        int idxConsole = strScript.indexOf(".id.toProtoPrimitive");

        assertTrue(idxMarker > 0, "the script writes no marker");
        assertTrue(idxConsole > 0, "the script makes no console call");
        assertTrue(idxMarker < idxConsole,
                "the marker is written after a console call, so a console failure would make it"
                        + " impossible to tell whether --bootstrap was honoured");
    }


    @Test
    void bothSentinelsLandInTheWorkDirectory() {
        String strScript = new Canton3xBootstrap().render(DIR_WORK);

        assertTrue(strScript.contains(DIR_WORK.resolve(Canton3xBootstrap.STR_MARKER_FILE)
                .toAbsolutePath().normalize().toString().replace("\\", "/")));
        assertTrue(strScript.contains(DIR_WORK.resolve(Canton3xBootstrap.STR_PARTICIPANT_ID_FILE)
                .toAbsolutePath().normalize().toString().replace("\\", "/")));
    }


    /**
     * The subcommand starts every node and bootstraps the synchronizer. Doing
     * either again is not harmless.
     */
    @Test
    void nothingIsStartedOrConnected() {
        String strScript = new Canton3xBootstrap().render(DIR_WORK);

        assertFalse(strScript.contains(".start()"), "the 3.x subcommand has already started"
                + " the nodes; this is the 2.x script's work, not this one's");
        assertFalse(strScript.contains("connect_local"), "the 3.x subcommand has already"
                + " bootstrapped the synchronizer");
    }


    /**
     * The script runs in a REMOTE console and refers to the node by whatever
     * that console binds it as - which is deliberately not `sandbox`, because
     * `sandbox-console` generates an entry of that name from its port flag
     * defaults and it shadows the configured one.
     */
    @Test
    void theScriptNamesTheNodeTheConsoleBinds() {
        assertEquals(RemoteConsoleOverlay.STR_NODE_DEFAULT,
                new Canton3xBootstrap().strParticipant());
        assertTrue(new Canton3xBootstrap().render(DIR_WORK)
                .contains(RemoteConsoleOverlay.STR_NODE_DEFAULT + ".id"));
        assertNotEquals(StorageOverlay.STR_NODE_PARTICIPANT,
                Canton3xBootstrap.STR_NODE_PARTICIPANT);
    }


    @Test
    void aNamedParticipantReachesTheScript() {
        assertTrue(new Canton3xBootstrap("other").render(DIR_WORK).contains("other.id"));
    }


    /**
     * A backslash is an escape inside a Scala string literal, so a Windows path
     * written verbatim produces a parse error a long way from its cause.
     */
    @Test
    void windowsSeparatorsAreNotLeftInScalaLiterals() {
        String strScript = new Canton3xBootstrap().render(Path.of("C:\\work\\sandbox"));

        assertFalse(strScript.contains("\\"), "a backslash survived into the script");
    }


    /**
     * The console is interactive. A script that ends without exiting leaves the
     * process at a prompt reading standard input, which in a harness is a run
     * that hangs rather than one that fails - and the exit has to come last, so
     * the completion marker is printed before the process leaves.
     */
    @Test
    void theScriptEndsByExiting() {
        String strScript = new Canton3xBootstrap().render(DIR_WORK);

        assertTrue(strScript.contains(Canton3xBootstrap.STR_EXIT),
                "the script never exits, so the console would sit at a prompt");
        assertTrue(strScript.indexOf(Canton3xBootstrap.STR_DONE)
                < strScript.indexOf(Canton3xBootstrap.STR_EXIT),
                "the script exits before it reports completion");
        assertTrue(strScript.trim().endsWith(Canton3xBootstrap.STR_EXIT));
    }


    /**
     * Thirteen parameters on `users.create`, so the script uses NAMED
     * arguments. A positional call would depend on an order no hand-written
     * script should rely on, and the names are measured from the console's own
     * help on 3.5.11.
     */
    @Test
    void theUserIsCreatedWithNamedArguments() {
        String strScript = new Canton3xBootstrap().withPartyAndUser("raposza", "raposza-pqs")
                .render(DIR_WORK);

        assertTrue(strScript.contains("users.create("));
        assertTrue(strScript.contains("id = \"raposza-pqs\""));
        assertTrue(strScript.contains("readAsAnyParty = true"),
                "without readAsAnyParty the user cannot serve scribe's --pipeline-filter-parties=*");
        assertTrue(strScript.contains("parties.allocate(party = \"raposza\")"));
    }


    /**
     * The party id is Canton's to decide - `allocate` takes a hint - and with
     * readAsAnyParty the user never needs it in its sets.
     */
    @Test
    void theUserDoesNotEnumerateParties() {
        String strScript = new Canton3xBootstrap().withPartyAndUser("raposza", "raposza-pqs")
                .render(DIR_WORK);

        assertTrue(strScript.contains("actAs = Set.empty"));
        assertTrue(strScript.contains("readAs = Set.empty"));
    }


    /**
     * The sentinel writes the ID, not the record that carries it.
     *
     * `_party.toString` rendered the whole `PartyDetails` through Canton's
     * `Pretty`, ellipsis included, and shipped that as `party.id` in
     * `sandbox.properties`. The accessor and the call are measured off the
     * 3.5.11 jar; this test is what stops a future edit reaching for `toString`
     * again because it is shorter.
     */
    @Test
    void thePartySentinelWritesTheIdRatherThanTheRecord() {
        String strScript = new Canton3xBootstrap().withPartyAndUser("raposza", "raposza-pqs")
                .render(DIR_WORK);

        assertTrue(strScript.contains("_party.party.toProtoPrimitive"),
                "the party sentinel does not reach the id through PartyId");
        assertFalse(strScript.contains("_party.toString"),
                "PartyDetails.toString is Canton's Pretty rendering of the whole record, with an"
                        + " ellipsis inside the id");
    }


    /** Provisioning is opt-in; the plain script stays what step 1 measured. */
    @Test
    void provisioningIsAbsentUnlessAskedFor() {
        Canton3xBootstrap plain = new Canton3xBootstrap();

        assertFalse(plain.flagProvisions());
        assertFalse(plain.render(DIR_WORK).contains("users.create"));
        assertFalse(plain.render(DIR_WORK).contains("parties.allocate"));
        assertTrue(new Canton3xBootstrap().withPartyAndUser("p", "u").flagProvisions());
    }


    /** Provisioning goes after the id and before the completion line. */
    @Test
    void provisioningSitsBetweenTheIdAndTheDone() {
        String strScript = new Canton3xBootstrap().withPartyAndUser("raposza", "raposza-pqs")
                .render(DIR_WORK);

        assertTrue(strScript.indexOf(".id.toProtoPrimitive")
                < strScript.indexOf("parties.allocate"));
        assertTrue(strScript.indexOf("users.create")
                < strScript.indexOf(Canton3xBootstrap.STR_DONE));
    }


    @Test
    void theArgumentsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Canton3xBootstrap(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xBootstrap().withPartyAndUser(" ", "u"));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xBootstrap().withPartyAndUser("p", " "));
        assertThrows(IllegalArgumentException.class, () -> new Canton3xBootstrap().render(null));
    }

    @Test
    void aPingIsOptionalAndOffByDefault() {
        assertFalse(new Canton3xBootstrap().flagPings());
        assertFalse(new Canton3xBootstrap().render(Path.of("/w")).contains("health.ping"));
        assertTrue(new Canton3xBootstrap().withPing().flagPings());
    }


    @Test
    void thePingTakesTheParticipantsOwnId() {
        // Measured on 3.5.11: ping(ParticipantId, ...) with defaults on two
        // through four. NOT a no-argument call, which is the 2.x recollection.
        String strScript = new Canton3xBootstrap("sandbox").withPing().render(Path.of("/w"));

        assertTrue(strScript.contains("sandbox.health.ping(sandbox.id)"), strScript);
        assertTrue(strScript.contains(Canton3xBootstrap.STR_PING_FILE), strScript);
    }


    @Test
    void thePingComesAfterProvisioningAndBeforeTheDoneMarker() {
        String strScript = new Canton3xBootstrap()
                .withPartyAndUser("alice", "alice-user")
                .withPing()
                .render(Path.of("/w"));

        int nUser = strScript.indexOf("users.create");
        int nPing = strScript.indexOf("health.ping");
        int nDone = strScript.indexOf(Canton3xBootstrap.STR_DONE);
        assertTrue(nUser > 0 && nPing > nUser && nDone > nPing, strScript);
    }


    @Test
    void withPingAndWithPartyAndUserComposeInEitherOrder() {
        Canton3xBootstrap a = new Canton3xBootstrap().withPing()
                .withPartyAndUser("alice", "alice-user");
        Canton3xBootstrap b = new Canton3xBootstrap()
                .withPartyAndUser("alice", "alice-user").withPing();

        assertTrue(a.flagPings());
        assertTrue(a.flagProvisions());
        assertEquals(b.render(Path.of("/w")), a.render(Path.of("/w")));
    }

}
