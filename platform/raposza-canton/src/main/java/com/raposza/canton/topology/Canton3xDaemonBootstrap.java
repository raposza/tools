// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import java.nio.file.Path;
import java.util.List;

/**
 * The bootstrap the daemon launcher runs, and it is SHORTER than the vendor's.
 *
 * <h2>What the vendor's script does, and which line breaks a restart</h2>
 *
 * The `sandbox` subcommand generates and runs a script - captured off disk on
 * 3.5.11 - whose working part is three calls per synchronizer:
 *
 * <pre>
 * val synchId = bootstrap.synchronizer(name, Seq(sequencer), Seq(mediator), owners,
 *                                      PositiveInt.one, staticSynchronizerParameters)
 * p.synchronizers.connect_local(sequencer, name)
 * p.topology.synchronizer_trust_certificates.propose(p, synchId.logical,
 *                                                    featureFlags = featureFlag)
 * </pre>
 *
 * The third is what fails a second start with TOPOLOGY_MAPPING_ALREADY_EXISTS,
 * and its only job is to set `EnableMultiSynchronizer` - a capability for one
 * participant against several synchronizers, which this sandbox does not use.
 * `connect_local` is what connects the participant. So the line is not
 * repaired here, it is ABSENT, and so is the vendor's `sidebox` filter beside
 * it, which is scaffolding for their own multi-sync fixture.
 *
 * <h2>The synchronizer guard</h2>
 *
 * `is_connected(SynchronizerAlias): Boolean` is measured from the console's own
 * `synchronizers.help("is_connected")` on 3.5.11, alongside two overloads
 * taking ids. `connect_local`'s first two parameters are `sequencer` and
 * `alias`, from `help("connect_local")`. Both are named rather than positional
 * here for the reason {@link Canton3xBootstrap} gives about `users.create`.
 *
 * A STRING is passed where both take a `SynchronizerAlias`, and that is not an
 * assumption: the vendor's generated script passes `syncDef.name`, a String,
 * to `connect_local`. So the implicit conversion is in the console's scope,
 * measured from a script Canton itself wrote and ran.
 *
 * `StaticSynchronizerParameters.defaults` is called with the vendor's own
 * three arguments including the named `topologyChangeDelay`, rather than the
 * two-argument form this class would otherwise have guessed at.
 *
 * `bootstrap.synchronizer` is NOT guarded, and that is no longer a gamble: a
 * second start ran straight through it, so the private
 * `check_synchronizer_bootstrap_status` beside `run_bootstrap` does what its
 * name suggested and the guard held in reserve is not needed.
 *
 * <h2>The provisioning guards, and why the two are shaped differently</h2>
 *
 * Both a repeat `parties.allocate` and a repeat `users.create` throw, measured
 * on 3.5.11 - and both throw the SAME thing:
 *
 * <pre>
 * com.digitalasset.canton.console.CommandFailure: Command execution failed.
 * </pre>
 *
 * That message is why neither guard is a try/catch around the call itself. A
 * catch on `CommandFailure` cannot tell "this party is already here" from "the
 * participant is not connected to a synchronizer", so a stack with a real
 * fault would come up looking provisioned and fail later, somewhere else. The
 * guards therefore ASK FIRST, and the two ask differently because the console
 * offers different questions.
 *
 * <h2>Parties: list and match a prefix, because the hint is not the id</h2>
 *
 * `parties.get(parties: Seq[PartyId], identityProviderId: String,
 * failOnNotFound: Boolean)` takes a `PartyId`, and a PartyId is exactly what a
 * caller holding a HINT does not have: `allocate(party = "alice")` returns
 * `alice::12201f4b7a22...`, where the namespace is Canton's to decide. So the
 * question is asked of `list` instead, whose measured signature is
 * `list(identityProviderId: String, filterParty: String): Seq[PartyDetails]`.
 *
 * **`filterParty` is not used, and that is deliberate.** Its help says
 * "Filter party by name" and does not say whether "name" means the hint
 * segment or the whole id. If it were the latter, a filtered list would come
 * back empty on a restart, the guard would never fire, and the allocate behind
 * it would throw - the exact failure this class exists to remove, restored by
 * an optimisation. An unfiltered list over a sandbox's handful of parties
 * costs nothing and depends on nothing unmeasured.
 *
 * The match is `startsWith(hint + "::")` rather than equality with the hint,
 * which would never fire, and rather than `contains(hint)`, which would match
 * a different party whose hint merely began the same way. The separator is
 * what makes it exact on the segment.
 *
 * <h2>Users: get, and catch its one documented failure</h2>
 *
 * `users.get(id: String, identityProviderId: String): User` takes the id
 * directly - a user id IS its own identifier, with no namespace appended - and
 * its help states that it fails when there is no such user. So the absence is
 * reported as a throw and there is nothing to list.
 *
 * The catch is around `get` ALONE and never around `create`. If the
 * participant is genuinely broken, `get` throws, the catch runs `create`, and
 * `create`'s own failure propagates and fails the script - which is the loud
 * failure a catch-all would have swallowed.
 *
 * <h2>The ping is unguarded, and needs no guard</h2>
 *
 * A ping creates and exercises a contract on Canton's own admin workflow and
 * leaves no topology behind, so running it on every start is what it is for.
 *
 * <h2>The DAR uploads are unguarded, and that is a MEASUREMENT</h2>
 *
 * Every other repeat in this script had to be asked about first, so the plain
 * `dars.upload(path)` here looks like the one that was not thought about. It
 * is the opposite: it is the only one where the question was put to a running
 * 3.5.11 and came back saying no guard is possible to need.
 *
 * <pre>
 * upload #1  152c70796e4d684ff7c1026c10bb50351c904a3a4acd32b00ab1c7d54a7a06f8
 * upload #2  152c70796e4d684ff7c1026c10bb50351c904a3a4acd32b00ab1c7d54a7a06f8
 * </pre>
 *
 * The second call succeeded and returned the SAME string, and the reason it
 * differs from `parties.allocate` is worth carrying next to the
 * call: an allocate mints an identifier and a second one would collide, while
 * a DAR's identity is a hash of its own bytes, so a repeat has nothing to
 * conflict with. A guard modelled on the party one would therefore be dead
 * code that still has to be read and maintained.
 *
 * <h2>What upload does about vetting, and why nothing here configures it</h2>
 *
 * From `dars.help("upload")` on 3.5.11: with `synchronizerId` unset the
 * packages are vetted when the participant is connected to exactly one
 * synchronizer, and `synchronizeVetting` defaults to blocking until the
 * vetting transaction is registered. This script connects exactly one
 * synchronizer, above, and does so before the uploads - so the one-argument
 * call is not the minimal call, it is the CORRECT one, and every parameter
 * left out has a default that matches what this stack needs.
 *
 * The uploads sit after the participant-id sentinel and before provisioning,
 * which is the order {@link Canton2xConfig#renderBootstrap} already uses: a
 * package is what a party is going to be asked to use.
 *
 * <h2>This script does NOT end the process it runs in</h2>
 *
 * It is handed to `--bootstrap` and interpreted inside the daemon's own JVM,
 * so anything here that ends a JVM ends the stack. The comment where the exit
 * constant used to be, beside {@link #STR_DONE}, carries the measurement.
 *
 * Author Claude/bentzn
 */
public final class Canton3xDaemonBootstrap {

    /** The vendor's own name for the first synchronizer, kept so ids match. */
    public static final String STR_SYNCHRONIZER = "synchronizer-1";

    public static final String STR_MARKER_FILE = "daemon-bootstrap-ran.txt";

    public static final String STR_MARKER = "raposza-daemon-bootstrap-ran";

    public static final String STR_PARTICIPANT_ID_FILE = "participant-id.txt";

    /** Written only when a party and a user were asked for. */
    public static final String STR_PARTY_ID_FILE = "party-id.txt";

    /** Written beside the party, and the same names Canton3xBootstrap uses. */
    public static final String STR_USER_ID_FILE = "user-id.txt";

    /** Written last, only when a ping was asked for. */
    public static final String STR_PING_FILE = "ping.txt";

    /**
     * The main package id per DAR, one per line, in the order they were
     * uploaded. Written only when DARs were asked for.
     *
     * It carries the ids rather than the paths because the id is what the
     * participant knows: `dars.list()` reports a `DarDescription` whose first
     * field is that id, and a reader holding a path has nothing to compare.
     */
    public static final String STR_DAR_FILE = "dar-package-ids.txt";

    /**
     * Written after every other statement, and the ONLY file that means the
     * script finished.
     *
     * `participant-id.txt` used to carry that meaning by accident, because it
     * happened to be the last thing written. It is not a readiness signal: it
     * is a diagnostic sentinel that says a console call succeeded, and the
     * moment a script grew work after it, a reader waiting on it was waiting
     * for the middle.
     */
    public static final String STR_READY_FILE = "daemon-ready.txt";

    public static final String STR_READY = "raposza-daemon-script-finished";

    /** Printed last, and what the process waits for. */
    public static final String STR_DONE = "raposza daemon is ready";

    /*
     * THERE IS NO EXIT LINE, and its absence is deliberate.
     *
     * Until 2026-08-19 this script ended with `sys.exit(0)`, copied from
     * Canton3xBootstrap where it is correct - that script drives a SEPARATE
     * console process which is meant to end. Here it was `System.exit(0)` on
     * the daemon's own JVM: every node started, this script ran to its last
     * line, and the daemon then took itself down through its own shutdown
     * hook with exit code 0. Orderly, silent and indistinguishable from a
     * clean stop, which is why four other causes were blamed first.
     *
     * MEASURED: the ready line, then `Shutting down...` on Thread-0 308 ms
     * later, then `Shutdown complete.` and exit code 0. Reproduced in five
     * consecutive runs, with PQS on and off, and unaffected by removing
     * `--no-tty`.
     *
     * A daemon is supposed to outlive its bootstrap. Nothing may be appended
     * to this script that ends the process.
     */

    /** What separates a party's hint from the namespace Canton appends. */
    private static final String STR_NAMESPACE_SEPARATOR = "::";

    private final String strParticipant;
    private final String strSequencer;
    private final String strMediator;
    private final String strSynchronizer;
    private final String strPartyHint;
    private final String strUserId;
    private final boolean flagPing;
    private final List<Path> lstFileDar;


    public Canton3xDaemonBootstrap() {
        this(StorageOverlay.STR_NODE_PARTICIPANT, StorageOverlay.STR_NODE_SEQUENCER,
                StorageOverlay.STR_NODE_MEDIATOR, STR_SYNCHRONIZER);
    }


    /**
     * @param strParticipant the participant node name in the configuration
     * @param strSequencer the sequencer node name
     * @param strMediator the mediator node name
     * @param strSynchronizer the synchronizer alias
     */
    public Canton3xDaemonBootstrap(String strParticipant, String strSequencer, String strMediator,
            String strSynchronizer) {
        this(strParticipant, strSequencer, strMediator, strSynchronizer, null, null, false,
                List.of());
    }


    private Canton3xDaemonBootstrap(String strParticipant, String strSequencer, String strMediator,
            String strSynchronizer, String strPartyHint, String strUserId, boolean flagPing,
            List<Path> lstFileDarNew) {
        if (strParticipant == null || strParticipant.isBlank())
            throw new IllegalArgumentException("a participant name is required");
        if (strSequencer == null || strSequencer.isBlank())
            throw new IllegalArgumentException("a sequencer name is required");
        if (strMediator == null || strMediator.isBlank())
            throw new IllegalArgumentException("a mediator name is required");
        if (strSynchronizer == null || strSynchronizer.isBlank())
            throw new IllegalArgumentException("a synchronizer alias is required");

        this.strParticipant = strParticipant;
        this.strSequencer = strSequencer;
        this.strMediator = strMediator;
        this.strSynchronizer = strSynchronizer;
        this.strPartyHint = strPartyHint;
        this.strUserId = strUserId;
        this.flagPing = flagPing;
        this.lstFileDar = lstFileDarNew == null ? List.of() : List.copyOf(lstFileDarNew);
    }


    /**
     * Allocates a party and creates a user, both only if they are not already
     * there - which is the difference between this and
     * {@link Canton3xBootstrap#withPartyAndUser(String, String)}, and the whole
     * reason a restartable stack can also be a usable one.
     *
     * A hint that already carries the namespace separator is rejected: the
     * guard appends it to build the prefix, and a hint containing it would
     * produce a match that could never fire.
     *
     * @param strPartyHintNew a HINT for the party identifier; Canton appends a
     *        namespace, so the allocated id is not this string
     * @param strUserIdNew the user id, which is what a token's `sub` carries
     * @return a bootstrap that also does that work
     */
    public Canton3xDaemonBootstrap withPartyAndUser(String strPartyHintNew, String strUserIdNew) {
        if (strPartyHintNew == null || strPartyHintNew.isBlank())
            throw new IllegalArgumentException("a party hint is required");
        if (strPartyHintNew.contains(STR_NAMESPACE_SEPARATOR))
            throw new IllegalArgumentException(
                    "a party HINT carries no namespace, so it cannot contain "
                            + STR_NAMESPACE_SEPARATOR + ": " + strPartyHintNew);
        if (strUserIdNew == null || strUserIdNew.isBlank())
            throw new IllegalArgumentException("a user id is required");

        return new Canton3xDaemonBootstrap(strParticipant, strSequencer, strMediator,
                strSynchronizer, strPartyHintNew, strUserIdNew, flagPing, lstFileDar);
    }


    /**
     * @return a bootstrap that also pings the participant from itself
     */
    public Canton3xDaemonBootstrap withPing() {
        return new Canton3xDaemonBootstrap(strParticipant, strSequencer, strMediator,
                strSynchronizer, strPartyHint, strUserId, true, lstFileDar);
    }


    /**
     * Uploads DARs, in the order given, on every start.
     *
     * There is no "already uploaded" branch and none is possible to need: a
     * repeat upload of the same DAR was measured on 3.5.11 returning the same
     * main package id, twice in one run.
     *
     * The paths are not checked here. This class renders a script and touches
     * no disk; a missing file is refused by `SandboxStack` before anything is
     * started, which is both earlier and a better message than a
     * `CommandFailure` from inside a node JVM. The link is not a javadoc one
     * on purpose: that class is in the parent package and this one does not
     * import it.
     *
     * @param lstFileDarNew DARs to upload, in order; null and empty both mean
     *        none
     * @return a bootstrap that also uploads them
     */
    public Canton3xDaemonBootstrap withDars(List<Path> lstFileDarNew) {
        return new Canton3xDaemonBootstrap(strParticipant, strSequencer, strMediator,
                strSynchronizer, strPartyHint, strUserId, flagPing, lstFileDarNew);
    }


    public String strParticipant() {
        return strParticipant;
    }


    public String strSequencer() {
        return strSequencer;
    }


    public String strMediator() {
        return strMediator;
    }


    public String strSynchronizer() {
        return strSynchronizer;
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


    /**
     * @return the DARs this script uploads, in order; never null
     */
    public List<Path> lstFileDar() {
        return lstFileDar;
    }


    /**
     * @return whether this script uploads any DAR
     */
    public boolean flagUploadsDars() {
        return !lstFileDar.isEmpty();
    }


    /**
     * @param dirWork where the sentinels go
     * @return the script
     */
    public String render(Path dirWork) {
        if (dirWork == null)
            throw new IllegalArgumentException("dirWork is required");

        StringBuilder sb = new StringBuilder();
        sb.append("import com.digitalasset.canton.config.RequireTypes.PositiveInt\n");
        sb.append("import com.digitalasset.canton.version.ProtocolVersion\n");

        // First, before any call that could fail: the marker says the script
        // was reached at all, which is a different fact from the stack being
        // up. Canton3xBootstrap makes the same distinction.
        appendWrite(sb, dirWork.resolve(STR_MARKER_FILE), "\"" + STR_MARKER + "\"");

        // The synchronizer. Argument ORDER is the vendor's own generated
        // script; the two trailing parameters carry defaults.
        sb.append("val _params = StaticSynchronizerParameters.defaults(\n");
        sb.append("  ").append(strSequencer).append(".config.crypto,\n");
        sb.append("  ProtocolVersion.forSynchronizer,\n");
        sb.append("  topologyChangeDelay = NonNegativeFiniteDuration.Zero)\n");
        sb.append("val _sync = bootstrap.synchronizer(\n");
        sb.append("  \"").append(strSynchronizer).append("\",\n");
        sb.append("  Seq(").append(strSequencer).append("),\n");
        sb.append("  Seq(").append(strMediator).append("),\n");
        sb.append("  Seq(").append(strSequencer).append(", ").append(strMediator).append("),\n");
        sb.append("  PositiveInt.one,\n");
        sb.append("  _params)\n");

        // THE GUARD, and the reason this class exists. The vendor connects
        // unconditionally and then proposes a trust certificate that already
        // exists; this asks first and proposes nothing.
        sb.append("if (!").append(strParticipant).append(".synchronizers.is_connected(\"")
                .append(strSynchronizer).append("\")) {\n");
        sb.append("  ").append(strParticipant).append(".synchronizers.connect_local(\n");
        sb.append("    sequencer = ").append(strSequencer).append(",\n");
        sb.append("    alias = \"").append(strSynchronizer).append("\")\n");
        sb.append("}\n");

        appendWrite(sb, dirWork.resolve(STR_PARTICIPANT_ID_FILE),
                strParticipant + ".id.toProtoPrimitive");

        // Before provisioning: a package is what a party is going to be asked
        // to use, and this is the order the 2.x script already renders.
        if (flagUploadsDars())
            appendDars(sb, dirWork);

        if (flagProvisions())
            appendProvisioning(sb, dirWork);

        if (flagPing)
            appendPing(sb, dirWork);

        // LAST, and after everything that can fail. A reader that waits on any
        // earlier sentinel is waiting for the middle of the script.
        appendWrite(sb, dirWork.resolve(STR_READY_FILE), "\"" + STR_READY + "\"");

        sb.append("println(\"").append(STR_DONE).append("\")\n");
        return sb.toString();
    }


    /**
     * The party is found by prefix or allocated; the user is fetched or
     * created. Neither branch writes a sentinel of its own - the writes below
     * are unconditional, so `party-id.txt` says what the party IS rather than
     * whether this particular start created it.
     *
     * The lambda parameter is named rather than a `_` placeholder. Both the
     * placeholder and the leading-underscore vals this script uses are legal
     * Scala together, but a reader has to stop and check that they are, and a
     * bootstrap script is read when something has already gone wrong.
     */
    private void appendProvisioning(StringBuilder sb, Path dirWork) {
        sb.append("val _prefix = \"").append(strPartyHint).append(STR_NAMESPACE_SEPARATOR)
                .append("\"\n");
        sb.append("val _found = ").append(strParticipant)
                .append(".ledger_api.parties.list().filter(\n");
        sb.append("  detail => detail.party.toProtoPrimitive.startsWith(_prefix))\n");
        sb.append("val _party = if (_found.isEmpty) {\n");
        sb.append("  ").append(strParticipant).append(".ledger_api.parties.allocate(party = \"")
                .append(strPartyHint).append("\")\n");
        sb.append("} else {\n");
        sb.append("  _found.head\n");
        sb.append("}\n");
        appendWrite(sb, dirWork.resolve(STR_PARTY_ID_FILE), "_party.party.toProtoPrimitive");

        sb.append("val _user = try {\n");
        sb.append("  ").append(strParticipant).append(".ledger_api.users.get(id = \"")
                .append(strUserId).append("\")\n");
        sb.append("}\n");
        sb.append("catch {\n");
        sb.append("  case _: com.digitalasset.canton.console.CommandFailure =>\n");
        sb.append("    ").append(strParticipant).append(".ledger_api.users.create(\n");
        sb.append("      id = \"").append(strUserId).append("\",\n");
        sb.append("      actAs = Set.empty,\n");
        sb.append("      readAs = Set.empty,\n");
        sb.append("      readAsAnyParty = true)\n");
        sb.append("}\n");
        appendWrite(sb, dirWork.resolve(STR_USER_ID_FILE), "_user.id");
    }


    /**
     * One `Seq` rather than a statement per DAR, so the sentinel is written
     * from the uploads' own return values instead of from a list this class
     * would otherwise have to keep in parallel with them.
     *
     * The separator is `System.lineSeparator()` and not a `"\n"` literal: a
     * backslash in this script is an escape a long way from its cause, and
     * `backslashesDoNotReachTheScript` asserts there are none.
     */
    private void appendDars(StringBuilder sb, Path dirWork) {
        sb.append("val _dars = Seq(\n");
        for (int cntDar = 0; cntDar < lstFileDar.size(); cntDar++) {
            sb.append("  ").append(strParticipant).append(".dars.upload(\"")
                    .append(forScala(lstFileDar.get(cntDar))).append("\")");
            sb.append(cntDar == lstFileDar.size() - 1 ? ")\n" : ",\n");
        }
        appendWrite(sb, dirWork.resolve(STR_DAR_FILE),
                "_dars.mkString(java.lang.System.lineSeparator())");
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
        return "canton 3.x daemon bootstrap: " + strParticipant + " -> " + strSynchronizer
                + (flagProvisions() ? ", party " + strPartyHint + ", user " + strUserId : "")
                + (flagUploadsDars() ? ", " + lstFileDar.size() + " DAR(s)" : "");
    }
}
