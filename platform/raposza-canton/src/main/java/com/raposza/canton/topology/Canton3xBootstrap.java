// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import java.nio.file.Path;

/**
 * The console script the 3.x sandbox runs with `--bootstrap`.
 *
 * <h2>Why there is one at all</h2>
 *
 * The `sandbox` subcommand starts the participant, the sequencer and the
 * mediator and bootstraps the synchronizer by itself, so unlike
 * {@link Canton2xConfig#renderBootstrap} this script has no topology work to
 * do. What it is for is everything the subcommand does NOT do: parties, users,
 * and a stable namespace key. Those are Canton console operations, and the
 * console is the only client this module has - `raposza-sandbox` holds no Ledger
 * API client, and reaching for the JSON API instead would put an HTTP client
 * and a JSON shape into a module whose whole point is process orchestration.
 *
 * <h2>Who runs it, and it is not the sandbox subcommand</h2>
 *
 * `sandbox` REFUSES `--bootstrap` - "bootstrap script cannot be defined
 * together with the 'sandbox' command", measured on 3.5.11. The script is run
 * by `sandbox-console`, a separate process that connects to the already-running
 * nodes.
 *
 * The console is INTERACTIVE, so the script has to end by exiting. Without
 * that last line the process sits at a prompt reading standard input, and in a
 * harness that is a process which never returns rather than one that fails.
 *
 * <h2>Two sentinels, on purpose</h2>
 *
 * The script writes a fixed marker FIRST and the participant id SECOND, and
 * the difference between them is diagnostic. If neither appears, the option was
 * not honoured. If the marker appears and the id does not, the option was
 * honoured and a console call failed - which on 3.x means either the console
 * API differs from 2.x's `id.toProtoPrimitive` or an auth check refused it.
 * One file cannot distinguish those, and they have opposite fixes.
 *
 * Author Claude/bentzn
 */
public final class Canton3xBootstrap {

    /**
     * What the CONSOLE calls the participant, which is not what the participant
     * calls itself.
     *
     * The script runs in a remote console, and the console binds the node under
     * the name in its own configuration. That name cannot be `sandbox`, because
     * `sandbox-console` generates an entry of that name from its port flag
     * defaults and it shadows the configured one.
     */
    public static final String STR_NODE_PARTICIPANT = RemoteConsoleOverlay.STR_NODE_DEFAULT;

    /** Written first, before any console call, so it isolates the option. */
    public static final String STR_MARKER_FILE = "bootstrap-ran.txt";

    /** Written second, by a console call that can fail on its own. */
    public static final String STR_PARTICIPANT_ID_FILE = "participant-id.txt";

    /** Written third, only when a party and a user were asked for. */
    public static final String STR_USER_ID_FILE = "user-id.txt";

    /** Written third as well, beside the user: the allocated party. */
    public static final String STR_PARTY_ID_FILE = "party-id.txt";

    /** Written last, only when a ping was asked for: the round-trip duration. */
    public static final String STR_PING_FILE = "ping.txt";

    public static final String STR_MARKER = "raposza-bootstrap-ran";

    /** Printed last, and what the console process waits for. */
    public static final String STR_DONE = "raposza bootstrap complete";

    /**
     * The console does not leave on its own. Emitted as the final line, after
     * the completion marker, so a reader sees the work finish before the exit.
     */
    public static final String STR_EXIT = "sys.exit(0)";

    private final String strParticipant;
    private final String strPartyHint;
    private final String strUserId;
    private final boolean flagPing;


    public Canton3xBootstrap() {
        this(STR_NODE_PARTICIPANT);
    }


    /**
     * Allocates a party and creates a user that may read as ANY party.
     *
     * `readAsAnyParty` is what scribe's `--pipeline-filter-parties=*` needs,
     * and it is a right on an ordinary user rather than a claim on the admin
     * token - measured from the console's own help on 3.5.11. That difference
     * matters: the alternative, `act-as-any-party-claim` on a pinned admin
     * token, is a shared bearer secret and diverges from the production shape,
     * which is an audience token whose `sub` names a user.
     *
     * @param strPartyHintNew a HINT for the party identifier; Canton appends a
     *        namespace, so the allocated id is not this string
     * @param strUserIdNew the user id, which is what a token's `sub` carries
     * @return a bootstrap that also does that work
     */
    public Canton3xBootstrap withPartyAndUser(String strPartyHintNew, String strUserIdNew) {
        if (strPartyHintNew == null || strPartyHintNew.isBlank())
            throw new IllegalArgumentException("a party hint is required");
        if (strUserIdNew == null || strUserIdNew.isBlank())
            throw new IllegalArgumentException("a user id is required");
        return new Canton3xBootstrap(strParticipant, strPartyHintNew, strUserIdNew, flagPing);
    }


    /**
     * Pings the participant from itself, which is the cheapest thing that puts
     * a transaction on the ledger.
     *
     * A ping is a create and a choice on Canton's own admin workflow, so it
     * needs no DAR built, staged or committed and no template name known to
     * this project. That makes it the instrument for the ingest question -
     * whether anything reaches a scribe table - PROVIDED admin-workflow
     * contracts are visible to the Ledger API stream PQS reads. Whether they
     * are has not been measured; if they turn out not to be, the fallback is
     * `model-tests.dar` out of the Canton jar, which
     * {@link com.raposza.canton.dar.CantonBuiltinDars} extracts.
     *
     * The signature is measured on 3.5.11:
     * `ping(ParticipantId, NonNegativeDuration, Option[SynchronizerId], String)`
     * with defaults on parameters two through four, so the participant's own id
     * is the only argument. It is NOT a no-argument call, which is what a 2.x
     * recollection would have produced.
     *
     * A failed ping FAILS THE SCRIPT, and that is deliberate: a stack whose
     * participant cannot reach its own synchronizer is not a stack that came
     * up, and finding out at the first assertion is worse than at start-up.
     *
     * @return a bootstrap that also pings
     */
    public Canton3xBootstrap withPing() {
        return new Canton3xBootstrap(strParticipant, strPartyHint, strUserId, true);
    }


    /**
     * @param strParticipant the participant node name in the configuration the
     *        subcommand ends up with; a mismatch here is a script that fails on
     *        an unknown identifier rather than one that quietly does nothing
     */
    public Canton3xBootstrap(String strParticipant) {
        this(strParticipant, null, null, false);
    }


    private Canton3xBootstrap(String strParticipant, String strPartyHint, String strUserId,
            boolean flagPing) {
        if (strParticipant == null || strParticipant.isBlank())
            throw new IllegalArgumentException("a participant name is required");
        this.strParticipant = strParticipant;
        this.strPartyHint = strPartyHint;
        this.strUserId = strUserId;
        this.flagPing = flagPing;
    }


    public String strPartyHint() {
        return strPartyHint;
    }


    public String strUserId() {
        return strUserId;
    }


    /**
     * @return whether this script allocates a party and creates a user
     */
    public boolean flagProvisions() {
        return strUserId != null;
    }


    /**
     * @return whether this script pings the participant from itself
     */
    public boolean flagPings() {
        return flagPing;
    }


    public String strParticipant() {
        return strParticipant;
    }


    /**
     * @param dirWork where the two sentinels go
     * @return the script
     */
    public String render(Path dirWork) {
        if (dirWork == null)
            throw new IllegalArgumentException("dirWork is required");

        Path fileMarker = dirWork.resolve(STR_MARKER_FILE);
        Path fileId = dirWork.resolve(STR_PARTICIPANT_ID_FILE);

        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Generated by raposza-canton. Canton console, Scala.\n");
        sb.append("// The subcommand has already started every node and bootstrapped the\n");
        sb.append("// synchronizer, so nothing here starts or connects anything.\n");

        // First, and deliberately before any console call: this file answers
        // "was --bootstrap honoured", which is a different question from
        // "did the console work".
        appendWrite(sb, fileMarker, "\"" + STR_MARKER + "\"");

        sb.append("val _strPid = ").append(strParticipant).append(".id.toProtoPrimitive\n");
        appendWrite(sb, fileId, "_strPid");

        if (flagProvisions())
            appendProvisioning(sb, dirWork);

        if (flagPing)
            appendPing(sb, dirWork);

        sb.append("println(\"").append(STR_DONE).append("\")\n");
        sb.append(STR_EXIT).append("\n");
        return sb.toString();
    }


    /**
     * Named arguments, not positional. `users.create` takes THIRTEEN
     * parameters, and its order is not something a hand-written script should
     * depend on; the names below are measured from the console's own
     * `users.help("create")` on 3.5.11.
     *
     * `parties.allocate` takes a HINT and returns `PartyDetails`, so the party
     * id is Canton's to decide. The user does not carry the party in its sets:
     * with `readAsAnyParty` it does not need to.
     *
     * <h2>The sentinel names a field, and it has to</h2>
     *
     * `_party.toString` is NOT the party id. On 3.5.11 `parties.allocate`
     * returns
     * `com.digitalasset.canton.admin.api.client.data.parties.PartyDetails`
     * - measured with javap, and identified from the four members the printed
     * value showed: `(PartyId, Boolean, Map[String,String], String)`. Its
     * `toString` is FINAL and routed through Canton's `Pretty`, so what reached
     * the file was `PartyDetails(alice::1220241e8b69...,true,Map(),)`: a whole
     * record, with Canton's own ellipsis in the middle of the id. Truncated as
     * well as wrong, so nothing could recover it by parsing.
     *
     * `PartyId` declares a public no-argument `toProtoPrimitive()`, which is
     * the same call the participant sentinel already makes through
     * `<node>.id`. `toLf` and `filterString` are beside it on that class and
     * are NOT interchangeable with it here; the canonical wire form is what a
     * script consuming `sandbox.properties` needs.
     */
    private void appendProvisioning(StringBuilder sb, Path dirWork) {
        sb.append("val _party = ").append(strParticipant)
                .append(".ledger_api.parties.allocate(party = \"").append(strPartyHint)
                .append("\")\n");
        appendWrite(sb, dirWork.resolve(STR_PARTY_ID_FILE), "_party.party.toProtoPrimitive");

        sb.append("val _user = ").append(strParticipant)
                .append(".ledger_api.users.create(\n");
        sb.append("  id = \"").append(strUserId).append("\",\n");
        sb.append("  actAs = Set.empty,\n");
        sb.append("  readAs = Set.empty,\n");
        sb.append("  readAsAnyParty = true)\n");
        appendWrite(sb, dirWork.resolve(STR_USER_ID_FILE), "_user.id");
    }


    /**
     * `health.ping` takes a ParticipantId, not a name and not nothing.
     * `<node>.id` is that type - the same expression the participant-id
     * sentinel already calls `toProtoPrimitive` on.
     */
    private void appendPing(StringBuilder sb, Path dirWork) {
        sb.append("val _dur = ").append(strParticipant).append(".health.ping(")
                .append(strParticipant).append(".id)\n");
        appendWrite(sb, dirWork.resolve(STR_PING_FILE), "_dur.toString");
    }


    private static void appendWrite(StringBuilder sb, Path file, String strExpression) {
        ConsoleScript.appendWrite(sb, file, strExpression);
    }


    private static String forScala(Path path) {
        return ConsoleScript.forScala(path);
    }

    @Override
    public String toString() {
        return "canton 3.x bootstrap for participant " + strParticipant;
    }
}
