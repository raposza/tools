// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.Command;
import com.raposza.api.model.Contract;
import com.raposza.api.model.ContractQuery;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.PrimKind;
import com.raposza.api.model.SubmitContext;
import com.raposza.api.model.SubmitResult;
import com.raposza.api.model.TemplateInfo;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UserInfo;
import com.raposza.render.JsonCoercer;
import com.raposza.resolve.TemplateRef;
import com.raposza.spi.LedgerClient_i;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a parsed script against a participant. Sec. 8, 9 and 10.
 *
 * <h2>The registry is SNAPSHOTTED, which is the whole of sec. 9</h2>
 *
 * The instance handed in is used unchanged for the entire run. A package
 * uploaded midway must not make statement 12 resolve differently from statement
 * 3, and a fixture that quietly targeted a different package version invalidates
 * whatever it was set up to test.
 *
 * <h2>Preparation and submission are separated on purpose</h2>
 *
 * Every statement is resolved and coerced BEFORE anything is sent, so a bad
 * type, a stale binding or an ambiguous template is FAILED_LOCALLY - the one
 * failure state that certainly changed nothing. It is also what makes VALIDATE
 * a run mode rather than a second code path: it simply stops after preparation.
 *
 * <h2>The run stops on the first statement it cannot continue past</h2>
 *
 * <h2>The statement id does NOT carry the script hash</h2>
 *
 * Sec. 8 asks for the normalised source hashed WITH the script hash, and also
 * for an id that survives an edit because line numbers move. Those cannot both
 * hold: the script hash is over the whole file, so reindenting one statement
 * would change every id, and editing statement 5 would move statements 1 to 4.
 * The script identity is carried once, on the transcript. Flagged for amendment
 * rather than diverged from silently.
 *
 * Sec. 8. There is no rollback - a script is a sequence of submissions, not a
 * transaction - so carrying on after a failure produces a ledger state nobody
 * asked for. OUTCOME_UNKNOWN stops it hardest: the run claims neither the
 * pre-command nor the post-command state.
 *
 * <h2>What this class does not do</h2>
 *
 * It does not write files. The transcript is returned and the caller decides
 * where it goes, because a transcript written somewhere the operator did not
 * choose is the shape the audit rules object to. The audit log is built from
 * {@link Entry#audit} rather than from a second assembly.
 *
 * Author Claude/bentzn
 */
public final class Runner {

    private static final int CNT_ID_HEX = 6;
    private static final int CNT_QUERY_MAX = 1000;
    private static final DamlType TYPE_CID = new DamlType.Prim(PrimKind.CONTRACT_ID);
    private static final DamlType TYPE_PARTY = new DamlType.Prim(PrimKind.PARTY);
    private static final DamlType TYPE_TEXT = new DamlType.Prim(PrimKind.TEXT);

    /** Obvious in a transcript, and impossible to mistake for a real id. */
    /**
     * Where the transcript records WHICH STATEMENT an entry was.
     *
     * A reader cannot recover it from the other fields: `CREATE USER`,
     * `DELETE USER`, `GET USER` and a rights change all write `userId` and
     * nothing else, so a pane guessing from the shape called all four a
     * create. It cannot come off the SOURCE either - that is what was
     * written, which is the one thing here that may be wrong about what was
     * sent.
     */
    public static final String STR_KEY_KIND = "kind";

    private static final String STR_PLACEHOLDER = "<validate>";

    /** How much of the run nonce reaches the command id. */
    private static final int CNT_RUN_HEX = 8;

    private final LedgerClient_i client;
    private final TypeRegistry_i registry;
    private final RunConfig config;
    private final AuditLog audit;
    private final String nameProfile;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Env env = new Env();

    /**
     * What makes this RUN's commands distinct from the last run of the same
     * script.
     *
     * The statement id is the hash of the normalised source, deliberately,
     * so it stays stable across edits and two transcripts can be compared.
     * The command id was built from it alone, and Canton deduplicates on the
     * command id: so a statement whose text does not vary between runs -
     * an EXERCISE naming a binding rather than a literal - was refused
     * DUPLICATE_COMMAND on the second run inside the deduplication window,
     * while the CREATE above it, carrying a name the operator had changed,
     * went through. Measured on 3.5.12, 2026-08-25.
     *
     * Two identifiers were being served by one value. This is the other one:
     * stable WITHIN a run, different for every run, and it appears in no
     * transcript field that anything compares.
     */
    private final String idRun = UUID.randomUUID().toString().replace("-", "")
            .substring(0, CNT_RUN_HEX);

    private int cntStatement;

    /** Told about each statement as it runs, or null - see {@link RunProgress_i}. */
    private RunProgress_i progress;


    /**
     * @param client the participant
     * @param registry the registry SNAPSHOT for this run
     * @param config what the script does not say
     */
    public Runner(LedgerClient_i client, TypeRegistry_i registry, RunConfig config) {
        this(client, registry, config, null, "unnamed");
    }


    /**
     * @param client the participant
     * @param registry the registry SNAPSHOT for this run
     * @param config what the script does not say
     * @param audit where audit records go, or null for none. Null is for TESTS
     *              and for nothing else: a real run that changes a participant
     *              and writes no record breaks the audit invariant
     * @param nameProfile the participant's display name, recorded so a log
     *                    accumulating across participants can be read
     */
    public Runner(LedgerClient_i client, TypeRegistry_i registry, RunConfig config,
            AuditLog audit, String nameProfile) {
        if (client == null || registry == null || config == null)
            throw new IllegalArgumentException("client, registry and config are required");

        this.client = client;
        this.registry = registry;
        this.config = config;
        this.audit = audit;
        this.nameProfile = nameProfile == null ? "unnamed" : nameProfile;
    }


    /**
     * Seeds the run from the register.
     *
     * Called BEFORE `run`, and it is what makes a statement runnable one line
     * at a time: the party allocated by the last line is still bound when the
     * next one asks for it.
     *
     * @param lstBinding what earlier runs left, ignored when null
     */
    public void restore(List<Binding> lstBinding) {
        if (lstBinding == null)
            return;

        for (Binding binding : lstBinding) {
            env.restore(binding);
        }
    }


    /**
     * @param progressNew told about each statement as it starts and ends, or
     *        null for nobody - his instruction, 2026-10-04
     */
    public void useProgress(RunProgress_i progressNew) {
        this.progress = progressNew;
    }


    /** @return the bindings this run made, for inspection after it ends */
    public Env env() {
        return env;
    }


    /**
     * @param strScript the script text, for the statement ids
     * @param lstStmt its statements
     * @return the transcript, one entry per statement EXECUTED
     */
    public Transcript run(String strScript, List<Stmt> lstStmt) {
        String idScript = hash(strScript == null ? "" : strScript);
        Instant instStart = Instant.now();
        List<Entry> lstEntry = new ArrayList<>();

        int cntStmt = lstStmt.size();
        int numStmt = 0;
        for (Stmt stmt : lstStmt) {
            numStmt++;
            if (progress != null)
                progress.started(numStmt, cntStmt, stmt);
            Entry entry = execute(stmt);
            lstEntry.add(entry);
            if (progress != null)
                progress.finished(numStmt, cntStmt, entry);

            // BEFORE the stop check, so the statement that ended the run is in
            // the log. An outcome-unknown is precisely the record most worth
            // having, and it is also the one that stops everything.
            if (audit != null && AuditLog.flagAudited(stmt, entry.status()))
                audit.append(entry, nameProfile, Instant.now());

            if (!entry.status().flagContinue())
                break;
        }

        return new Transcript(idScript, instStart, config.flagValidateOnly(),
                List.copyOf(lstEntry), bindings());
    }


    /**
     * Every binding the run made, with its type and whether it went stale.
     *
     * A transcript records what each STATEMENT did; this is the state the run
     * ended in, which is a different question and the one asked first when a
     * fixture misbehaves. It costs nothing - the environment already holds all
     * of it - and it removes most of the reason to want a print statement.
     */
    private List<Binding> bindings() {
        return env.all();
    }


    private Entry execute(Stmt stmt) {
        cntStatement++;
        String idStatement = hash(normalise(stmt.strSource())).substring(0, CNT_ID_HEX);
        ObjectNode resolved = mapper.createObjectNode();

        try {
            return switch (stmt) {
                case Stmt.Allocate val -> allocate(val, idStatement, resolved);
                case Stmt.CreateUser val -> createUser(val, idStatement, resolved);
                case Stmt.Create val -> create(val, idStatement, resolved);
                case Stmt.Exercise val -> exercise(val, idStatement, resolved);
                case Stmt.ExerciseByKey val -> exerciseByKey(val, idStatement, resolved);
                case Stmt.Fetch val -> fetch(val, idStatement, resolved);
                case Stmt.FetchSingle val -> fetchSingle(val, idStatement, resolved);
                case Stmt.Query val -> query(val, idStatement, resolved);
                case Stmt.DeleteUser val -> deleteUser(val, idStatement, resolved);
                case Stmt.Rights val -> rights(val, idStatement, resolved);
                case Stmt.ListOf val -> listOf(val, idStatement, resolved);
                case Stmt.GetUser val -> getUser(val, idStatement, resolved);
                case Stmt.GetLedgerEnd val -> ledgerEnd(val, idStatement, resolved);
                case Stmt.Prune val -> prune(val, idStatement, resolved);
                case Stmt.Assert val -> assertion(val, idStatement);
                case Stmt.Expect val -> expectation(val, idStatement);
            };
        }
        catch (RuntimeException ex) {
            // Anything reaching here was refused BEFORE submission - every path
            // that sends returns its own entry. A submission failure cannot
            // arrive as an exception without also being a defect, and calling
            // it FAILED_LOCALLY would then be the lie sec. 8 refuses; so the
            // submitting paths never throw.
            return failed(stmt, idStatement, resolved, ex);
        }
    }


    // ------------------------------------------------------------ commands

    private Entry allocate(Stmt.Allocate stmt, String idStatement, ObjectNode resolved) {
        resolved.put("partyIdHint", stmt.hintParty());

        if (config.flagValidateOnly()) {
            // A placeholder of the RIGHT TYPE. Without it every later $binding
            // is unbound and a validate run reports failures the real run would
            // never have - which is validating nothing.
            bind(stmt.nameBind(), new DamlValue.Party(STR_PLACEHOLDER + stmt.hintParty()),
                    TYPE_PARTY, stmt);
            return entry(stmt, idStatement, RunStatus.VALIDATED, stmt.nameBind(), resolved);
        }

        PartyInfo party = client.allocateParty(stmt.hintParty(), null);
        resolved.put("party", party.idParty());

        bind(stmt.nameBind(), new DamlValue.Party(party.idParty()), TYPE_PARTY, stmt);
        return entry(stmt, idStatement, RunStatus.COMMITTED, stmt.nameBind(), resolved);
    }


    private Entry createUser(Stmt.CreateUser stmt, String idStatement, ObjectNode resolved) {
        UserSpec spec = UserSpec.of(stmt, env);
        resolved.put("userId", stmt.idUser());
        resolved.put("primaryParty", spec.idPartyPrimary());
        resolved.set("actAs", texts(spec.lstPartyAct()));
        resolved.set("readAs", texts(spec.lstPartyRead()));

        if (config.flagValidateOnly())
            return entry(stmt, idStatement, RunStatus.VALIDATED, Optional.empty(), resolved);

        UserInfo user = client.createUser(stmt.idUser(), spec.idPartyPrimary(),
                spec.lstPartyAct(), spec.lstPartyRead());
        resolved.put("userId", user.idUser());
        return entry(stmt, idStatement, RunStatus.COMMITTED, Optional.empty(), resolved);
    }


    private Entry create(Stmt.Create stmt, String idStatement, ObjectNode resolved) {
        TemplateInfo template = template(stmt.strTemplate(), stmt);
        List<String> lstParty = parties(stmt.lstParty(), stmt);

        DamlValue.Rec argument = coercer(stmt).coerceRecord(stmt.nodeWith(),
                template.idTemplate());

        describe(resolved, template.idTemplate(), lstParty, null, null);
        resolved.set("argument", rendered(argument));

        if (config.flagValidateOnly()) {
            bind(stmt.nameBind(), new DamlValue.ContractRef(STR_PLACEHOLDER + cntStatement),
                    TYPE_CID, stmt);
            return entry(stmt, idStatement, RunStatus.VALIDATED, stmt.nameBind(), resolved);
        }

        return submit(stmt, idStatement, resolved, stmt.nameBind(), lstParty,
                new Command.Create(template.idTemplate(), argument),
                (tree, result) -> bindCreated(tree, stmt, resolved));
    }


    private Entry exercise(Stmt.Exercise stmt, String idStatement, ObjectNode resolved) {
        List<String> lstParty = parties(stmt.lstParty(), stmt);
        String idContract = contractRef(stmt.refContract(), stmt);

        // The grammar names no template on EXERCISE - sec. 9: the participant
        // reports it, so nothing has to be named. This read is also what makes
        // "no such contract" a local failure instead of a rejection.
        Contract contract = client.contract(idContract, lstParty)
                .orElseThrow(() -> new CaqlException(stmt.numLine(), stmt.strSource(),
                        "contract " + idContract + " is not visible to " + lstParty
                                + ", or does not exist"));

        TemplateInfo template = registry.template(contract.idTemplate())
                .orElseThrow(() -> new CaqlException(stmt.numLine(), stmt.strSource(),
                        "the registry does not hold " + contract.idTemplate()
                                + "; its package has not been read from the participant"));

        Target target = target(template, stmt);
        ChoiceInfo choice = target.choice();
        JsonNode nodeArg = stmt.nodeWith().orElse(mapper.createObjectNode());
        DamlValue argument = coercer(stmt).coerce(nodeArg, choice.typeArg());

        describe(resolved, contract.idTemplate(), lstParty, choice.nameChoice(), idContract);
        if (!target.idTarget().equals(contract.idTemplate()))
            resolved.put("interfaceId", target.idTarget().toString());
        resolved.set("argument", rendered(argument));
        resolved.put("consuming", choice.flagConsuming());

        if (config.flagValidateOnly()) {
            // Staleness is tracked in validate too, so a script that uses an
            // archived binding is caught without a participant being changed.
            if (choice.flagConsuming())
                env.consume(idContract);

            // The VALUE of a choice result is unknowable without submitting;
            // its TYPE is not, and the type is the whole of what a later
            // substitution is checked against.
            bind(stmt.nameBind(), new DamlValue.Unit(), choice.typeReturn(), stmt);
            return entry(stmt, idStatement, RunStatus.VALIDATED, stmt.nameBind(), resolved);
        }

        return submit(stmt, idStatement, resolved, stmt.nameBind(), lstParty,
                new Command.Exercise(target.idTarget(), idContract, choice.nameChoice(),
                        argument),
                (tree, result) -> {
                    // Staleness BEFORE binding, so a result holding the id that
                    // was just archived is stale from the moment it exists.
                    if (choice.flagConsuming())
                        env.consume(idContract);

                    // Sec. 6 says a structured result is "recorded in the
                    // transcript, usable nowhere". It was usable nowhere and
                    // recorded nowhere either, which made the inertness a
                    // silence rather than a decision.
                    DamlValue valueResult = result.valueResult()
                            .orElse(new DamlValue.Unit());
                    resolved.set("result", rendered(valueResult));

                    bind(stmt.nameBind(), valueResult, choice.typeReturn(), stmt);
                });
    }


    private Entry fetch(Stmt.Fetch stmt, String idStatement, ObjectNode resolved) {
        List<String> lstParty = parties(stmt.lstParty(), stmt);
        String idContract = contractRef(stmt.refContract(), stmt);
        resolved.put("contractId", idContract);
        resolved.set("readAs", texts(lstParty));

        // Sec. 6: FETCH is strict. Everything that is not an active, visible
        // contract fails the run. A nullable FETCH would introduce conditional
        // behaviour indirectly and move the failure somewhere less informative.
        Contract contract = client.contract(idContract, lstParty)
                .orElseThrow(() -> new CaqlException(stmt.numLine(), stmt.strSource(),
                        "contract " + idContract + " is not visible to " + lstParty
                                + ", or does not exist"));
        if (!contract.isActive()) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(),
                    "contract " + idContract + " has been archived");
        }

        resolved.put("templateId", contract.idTemplate().toString());
        bind(stmt.nameBind(), new DamlValue.ContractRef(idContract), TYPE_CID, stmt);

        // A read: it commits nothing and writes no audit record - sec. 10 - so
        // it runs under VALIDATE as well. The status still differs, because a
        // validate run must not report that anything was done.
        return entry(stmt, idStatement,
                config.flagValidateOnly() ? RunStatus.VALIDATED : RunStatus.COMMITTED,
                stmt.nameBind(), resolved);
    }


    /**
     * QUERY, with its WHERE applied HERE, over what the read returned.
     *
     * Neither Ledger API generation filters an active-contract read by payload
     * content, so there is no wire predicate to send. Three things follow and
     * each is visible in the transcript rather than assumed: the parties still
     * decide what is visible and the sieve only narrows it; the read is capped
     * at {@link #CNT_QUERY_MAX} BEFORE the sieve sees it; and so both counts
     * are recorded - `countRead` before, `count` after - with `truncated`
     * present when the read hit its cap, or three matches out of a truncated
     * thousand would read the same as three out of everything.
     *
     * The clause is resolved BEFORE the read, so a structural fault - an
     * undeclared field, an ordering on a party - is refused with nothing
     * asked of the participant, and refused even when the read would have
     * returned nothing.
     */
    private Entry query(Stmt.Query stmt, String idStatement, ObjectNode resolved) {
        TemplateInfo template = template(stmt.strTemplate(), stmt);
        List<String> lstParty = parties(stmt.lstParty(), stmt);

        resolved.put("templateId", template.idTemplate().toString());
        resolved.set("readAs", texts(lstParty));

        Sieve sieve = null;
        if (stmt.clauseWhere().isPresent()) {
            resolved.put("where", stmt.clauseWhere().get().str());
            sieve = new Sieve(stmt.clauseWhere().get(), template, registry, coercer(stmt), stmt);
        }

        DataId idFilter = idFilter(template.idTemplate());
        if (!idFilter.equals(template.idTemplate()))
            resolved.put("filterTemplateId", idFilter.toString());

        List<Contract> lstContract = client.activeContracts(new ContractQuery(lstParty,
                List.of(idFilter), List.of(), "", CNT_QUERY_MAX, Optional.empty()));

        if (lstContract.size() >= CNT_QUERY_MAX)
            resolved.put("truncated", true);

        if (sieve != null) {
            resolved.put("countRead", lstContract.size());
            List<Contract> lstKept = new ArrayList<>();
            for (Contract contract : lstContract) {
                if (sieve.matches(contract.payload()))
                    lstKept.add(contract);
            }
            lstContract = lstKept;
        }

        resolved.put("count", lstContract.size());
        ArrayNode arr = resolved.putArray("contractIds");
        for (Contract contract : lstContract) {
            arr.add(contract.idContract());
        }

        // Sec. 6: QUERY binds nothing. It is the debugging command, and it is
        // a READ - so it runs under VALIDATE too, which changes nothing.
        return entry(stmt, idStatement,
                config.flagValidateOnly() ? RunStatus.VALIDATED : RunStatus.COMMITTED,
                Optional.empty(), resolved);
    }


    /**
     * EXERCISE ON KEY. The template is named, so the key is coerced against its
     * DECLARED key type - a template with no key fails here rather than on the
     * wire, which is the difference between a message naming the template and a
     * Canton error naming a hash.
     */
    private Entry exerciseByKey(Stmt.ExerciseByKey stmt, String idStatement,
            ObjectNode resolved) {

        TemplateInfo template = template(stmt.strTemplate(), stmt);
        List<String> lstParty = parties(stmt.lstParty(), stmt);

        DamlType typeKey = template.typeKey()
                .orElseThrow(() -> new CaqlException(stmt.numLine(), stmt.strSource(),
                        template.idTemplate().shortName() + " declares no key, so it cannot be"
                                + " exercised ON KEY"));

        DamlValue valueKey = coercer(stmt).coerce(stmt.nodeKey(), typeKey);
        ChoiceInfo choice = choice(template, stmt.nameChoice(), stmt);
        JsonNode nodeArg = stmt.nodeWith().orElse(mapper.createObjectNode());
        DamlValue argument = coercer(stmt).coerce(nodeArg, choice.typeArg());

        describe(resolved, template.idTemplate(), lstParty, choice.nameChoice(), null);
        resolved.set("key", rendered(valueKey));
        resolved.set("argument", rendered(argument));
        resolved.put("consuming", choice.flagConsuming());

        if (config.flagValidateOnly()) {
            bind(stmt.nameBind(), new DamlValue.Unit(), choice.typeReturn(), stmt);
            return entry(stmt, idStatement, RunStatus.VALIDATED, stmt.nameBind(), resolved);
        }

        // NOTHING IS STALED HERE even for a consuming choice. The contract the
        // key resolved to is chosen by the participant and its id is never in
        // this run's hands, so there is no binding to stale and pretending
        // otherwise would stale the wrong one.
        return submit(stmt, idStatement, resolved, stmt.nameBind(), lstParty,
                new Command.ExerciseByKey(template.idTemplate(), valueKey, choice.nameChoice(),
                        argument),
                (tree, result) -> {
                    DamlValue valueResult = result.valueResult().orElse(new DamlValue.Unit());
                    resolved.set("result", rendered(valueResult));
                    bind(stmt.nameBind(), valueResult, choice.typeReturn(), stmt);
                });
    }


    /**
     * A strict single-result read. Exactly one match binds.
     *
     * The cap is asked for as two rather than as one: a read limited to one
     * cannot tell "exactly one" from "the first of several", and reporting the
     * second case as success is the nondeterminism this statement exists to
     * refuse.
     */
    private Entry fetchSingle(Stmt.FetchSingle stmt, String idStatement, ObjectNode resolved) {
        TemplateInfo template = template(stmt.strTemplate(), stmt);
        List<String> lstParty = parties(stmt.lstParty(), stmt);

        resolved.put("templateId", template.idTemplate().toString());
        resolved.set("readAs", texts(lstParty));

        // THE SIEVE IS RESOLVED BEFORE THE READ, as on a QUERY, so a structural
        // fault is refused with nothing asked of the participant.
        Sieve sieve = null;
        if (stmt.clauseWhere().isPresent()) {
            resolved.put("where", stmt.clauseWhere().get().str());
            sieve = new Sieve(stmt.clauseWhere().get(), template, registry, coercer(stmt), stmt);
        }

        DataId idFilter = idFilter(template.idTemplate());
        if (!idFilter.equals(template.idTemplate()))
            resolved.put("filterTemplateId", idFilter.toString());

        // Without a WHERE two are asked for, which is enough to tell one from
        // several. With one the QUERY's cap applies, because the match may be
        // anywhere in what the parties see.
        int cntAsk = sieve == null ? 2 : CNT_QUERY_MAX;
        List<Contract> lstContract = client.activeContracts(new ContractQuery(lstParty,
                List.of(idFilter), List.of(), "", cntAsk, Optional.empty()));

        boolean flagTruncated = sieve != null && lstContract.size() >= CNT_QUERY_MAX;
        if (sieve != null) {
            resolved.put("countRead", lstContract.size());
            if (flagTruncated)
                resolved.put("truncated", true);
            List<Contract> lstKept = new ArrayList<>();
            for (Contract contract : lstContract) {
                if (sieve.matches(contract.payload()))
                    lstKept.add(contract);
            }
            lstContract = lstKept;
        }

        String strWhat = template.idTemplate().shortName()
                + (sieve == null ? "" : " WHERE " + stmt.clauseWhere().get().str());
        resolved.put("count", lstContract.size());
        if (lstContract.isEmpty()) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "no active " + strWhat
                    + " is visible to " + lstParty
                    + (flagTruncated ? "; the read stopped at " + CNT_QUERY_MAX
                            + " contracts, so one may lie beyond it" : ""));
        }
        if (lstContract.size() > 1) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "more than one active "
                    + strWhat + " is visible to " + lstParty
                    + "; SINGLE binds exactly one, and the active contract set has no order"
                    + " that would make any of them the right one");
        }
        // ONE MATCH IN A TRUNCATED READ PROVES NOTHING about the contracts the
        // read never reached, and binding it would be the nondeterminism SINGLE
        // exists to refuse.
        if (flagTruncated) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "one " + strWhat
                    + " matched, but the read stopped at " + CNT_QUERY_MAX + " contracts, so"
                    + " another may lie beyond it; narrow the parties");
        }

        String idContract = lstContract.get(0).idContract();
        resolved.put("contractId", idContract);
        bind(stmt.nameBind(), new DamlValue.ContractRef(idContract), TYPE_CID, stmt);

        return entry(stmt, idStatement,
                config.flagValidateOnly() ? RunStatus.VALIDATED : RunStatus.COMMITTED,
                stmt.nameBind(), resolved);
    }


    // ------------------------------------------------------------ admin

    private Entry deleteUser(Stmt.DeleteUser stmt, String idStatement, ObjectNode resolved) {
        resolved.put("userId", stmt.idUser());

        if (config.flagValidateOnly())
            return entry(stmt, idStatement, RunStatus.VALIDATED, Optional.empty(), resolved);

        client.deleteUser(stmt.idUser());
        return entry(stmt, idStatement, RunStatus.COMMITTED, Optional.empty(), resolved);
    }


    /**
     * GRANT and REVOKE.
     *
     * The user is READ BACK by the client and its rights recorded, rather than
     * the request being echoed. A right the participant already held or quietly
     * declined would otherwise be reported as granted, which is the one claim
     * that must never be made about a permission on the strength of a request.
     */
    private Entry rights(Stmt.Rights stmt, String idStatement, ObjectNode resolved) {
        String idParty = partyRef(stmt.refParty(), stmt);
        resolved.put("userId", stmt.idUser());
        resolved.put("right", stmt.flagActAs() ? "canActAs" : "canReadAs");
        resolved.put("party", idParty);
        resolved.put("grant", stmt.flagGrant());

        if (config.flagValidateOnly())
            return entry(stmt, idStatement, RunStatus.VALIDATED, Optional.empty(), resolved);

        List<String> lstAct = stmt.flagActAs() ? List.of(idParty) : List.of();
        List<String> lstRead = stmt.flagActAs() ? List.of() : List.of(idParty);

        UserInfo user = stmt.flagGrant()
                ? client.grantUserRights(stmt.idUser(), lstAct, lstRead)
                : client.revokeUserRights(stmt.idUser(), lstAct, lstRead);

        resolved.set("actAs", texts(user.lstPartyAct()));
        resolved.set("readAs", texts(user.lstPartyRead()));
        return entry(stmt, idStatement, RunStatus.COMMITTED, Optional.empty(), resolved);
    }


    /**
     * LIST. A read: it binds nothing, submits nothing and writes no audit
     * record, so it runs unchanged under VALIDATE.
     */
    private Entry listOf(Stmt.ListOf stmt, String idStatement, ObjectNode resolved) {
        ArrayNode arr = resolved.putArray("items");

        switch (stmt.kind()) {
            case PARTIES -> {
                for (PartyInfo party : client.parties()) {
                    arr.add(party.idParty());
                }
            }
            case USERS -> {
                for (UserInfo user : client.users()) {
                    arr.add(user.idUser());
                }
            }
            case PACKAGES -> {
                for (String idPackage : client.packageIds()) {
                    arr.add(idPackage);
                }
            }
        }

        resolved.put("count", arr.size());
        return entry(stmt, idStatement,
                config.flagValidateOnly() ? RunStatus.VALIDATED : RunStatus.COMMITTED,
                Optional.empty(), resolved);
    }


    private Entry getUser(Stmt.GetUser stmt, String idStatement, ObjectNode resolved) {
        UserInfo user = client.user(stmt.idUser());

        resolved.put("userId", user.idUser());
        resolved.put("primaryParty", user.idPartyPrimary());
        resolved.put("admin", user.flagAdmin());
        resolved.set("actAs", texts(user.lstPartyAct()));
        resolved.set("readAs", texts(user.lstPartyRead()));

        return entry(stmt, idStatement,
                config.flagValidateOnly() ? RunStatus.VALIDATED : RunStatus.COMMITTED,
                Optional.empty(), resolved);
    }


    /**
     * GET LEDGER END - the one read that binds, because it is the one that
     * answers with a scalar.
     *
     * It binds as Text on both generations: v1 reports an opaque string and v2
     * an int64, and Text is the shape both fit. The offset is what makes a
     * transcript reconcilable after an OUTCOME_UNKNOWN, which is the whole
     * reason the statement exists.
     */
    private Entry ledgerEnd(Stmt.GetLedgerEnd stmt, String idStatement, ObjectNode resolved) {
        if (config.flagValidateOnly()) {
            bind(stmt.nameBind(), new DamlValue.Text(STR_PLACEHOLDER + "offset"), TYPE_TEXT,
                    stmt);
            return entry(stmt, idStatement, RunStatus.VALIDATED, stmt.nameBind(), resolved);
        }

        String strOffset = client.ledgerEnd();
        resolved.put("offset", strOffset);
        bind(stmt.nameBind(), new DamlValue.Text(strOffset), TYPE_TEXT, stmt);

        return entry(stmt, idStatement, RunStatus.COMMITTED, stmt.nameBind(), resolved);
    }


    /** PRUNE. Irreversible, and it takes no divulged contracts without being asked. */
    private Entry prune(Stmt.Prune stmt, String idStatement, ObjectNode resolved) {
        String strOffset = offsetRef(stmt.refOffset(), stmt);
        resolved.put("offset", strOffset);

        if (config.flagValidateOnly())
            return entry(stmt, idStatement, RunStatus.VALIDATED, Optional.empty(), resolved);

        client.prune(strOffset, false);
        return entry(stmt, idStatement, RunStatus.COMMITTED, Optional.empty(), resolved);
    }


    // ------------------------------------------------------------ assertions

    /**
     * ASSERT. The read runs first and its own entry is discarded in favour of
     * one carrying the verdict - the count is already in `resolved`, so the
     * assertion adds what was expected rather than restating what was found.
     *
     * A failed assertion is FAILED_LOCALLY, which is exact: the read committed
     * nothing, so nothing about the participant changed and the run stops in
     * the one state that is safe to assume changed nothing.
     */
    private Entry assertion(Stmt.Assert stmt, String idStatement) {
        Entry entryRead = execute(stmt.stmtRead());
        ObjectNode resolved = entryRead.resolved();
        resolved.put("expectedCount", stmt.cntExpected());

        if (!entryRead.status().flagContinue())
            return entryRead;

        int cntFound = resolved.path("count").asInt(-1);
        if (cntFound != stmt.cntExpected()) {
            return new Entry(idStatement, stmt.numLine(), stmt.strSource(),
                    RunStatus.FAILED_LOCALLY, Optional.empty(), resolved, Optional.empty(),
                    Optional.empty(), Optional.of("expected " + stmt.cntExpected()
                            + " and found " + cntFound));
        }

        return new Entry(idStatement, stmt.numLine(), stmt.strSource(), entryRead.status(),
                Optional.empty(), resolved, entryRead.idCommand(), entryRead.idUpdate(),
                Optional.empty());
    }


    /**
     * EXPECT. The inner statement runs and its outcome is matched against the
     * SIX-STATE table.
     *
     * A match becomes COMMITTED so the run continues - the script said this was
     * the intended outcome, and continuing past an intended outcome is not
     * continuing past a failure. A MISS keeps the inner status, so a run that
     * expected a rejection and got an outcome-unknown stops exactly as hard as
     * it would have without the EXPECT. Sec. 8: the two are different claims
     * and this is where that matters most.
     *
     * The audit record is the INNER statement's, because the inner statement is
     * what reached the participant.
     */
    private Entry expectation(Stmt.Expect stmt, String idStatement) {
        Entry entryInner = execute(stmt.stmtInner());
        ObjectNode resolved = entryInner.resolved();
        resolved.put("expectedOutcome", stmt.status().strJson());
        resolved.put("actualOutcome", entryInner.status().strJson());

        if (entryInner.status() == stmt.status()) {
            return new Entry(idStatement, stmt.numLine(), stmt.strSource(), RunStatus.COMMITTED,
                    Optional.empty(), resolved, entryInner.idCommand(), entryInner.idUpdate(),
                    Optional.empty());
        }

        return new Entry(idStatement, stmt.numLine(), stmt.strSource(), entryInner.status(),
                Optional.empty(), resolved, entryInner.idCommand(), entryInner.idUpdate(),
                Optional.of("expected " + stmt.status().strJson() + " and got "
                        + entryInner.status().strJson()
                        + entryInner.strError().map(str -> ": " + str).orElse("")));
    }


    // ------------------------------------------------------------ submission

    /** What a committed submission does with its result, beyond being recorded. */
    private interface OnCommit {

        void accept(TxTree tree, SubmitResult result);

    }


    private Entry submit(Stmt stmt, String idStatement, ObjectNode resolved,
            Optional<String> nameBind, List<String> lstParty, Command cmd, OnCommit onCommit) {
        String idCommand = "caql-" + idRun + "-" + idStatement + "-" + cntStatement;
        resolved.put("commandId", idCommand);

        SubmitContext ctx = new SubmitContext(lstParty, List.of(), idCommand,
                config.idApplication(), config.timeout());

        SubmitResult result;
        try {
            result = client.submit(cmd, ctx);
        }
        catch (RuntimeException ex) {
            // The client throws only on transport failure, and a transport
            // failure at this point cannot say whether the command arrived.
            return new Entry(idStatement, stmt.numLine(), stmt.strSource(),
                    RunStatus.OUTCOME_UNKNOWN, Optional.empty(), resolved,
                    Optional.of(idCommand), Optional.empty(), Optional.of(ex.toString()));
        }

        switch (result.outcome()) {
            case COMMITTED: {
                TxTree tree = result.tree().orElseThrow(() -> new CaqlException(stmt.numLine(),
                        stmt.strSource(), "the participant reported a commit with no tree"));
                onCommit.accept(tree, result);

                return new Entry(idStatement, stmt.numLine(), stmt.strSource(),
                        RunStatus.COMMITTED, nameBind, resolved, Optional.of(idCommand),
                        Optional.of(tree.idUpdate()), Optional.empty());
            }

            case REJECTED:
                return new Entry(idStatement, stmt.numLine(), stmt.strSource(),
                        RunStatus.REJECTED, Optional.empty(), resolved, Optional.of(idCommand),
                        Optional.empty(), Optional.of(result.codeError().orElse("REJECTED")
                                + " " + result.strError().orElse("")));

            default:
                return new Entry(idStatement, stmt.numLine(), stmt.strSource(),
                        RunStatus.OUTCOME_UNKNOWN, Optional.empty(), resolved,
                        Optional.of(idCommand), Optional.empty(),
                        Optional.of(result.strError().orElse("no completion was observed")));
        }
    }


    private void bindCreated(TxTree tree, Stmt.Create stmt, ObjectNode resolved) {
        for (TxNode node : tree.lstRoot()) {
            if (node instanceof TxNode.Created created) {
                // WHAT THE STATEMENT PRODUCED, not only what it sent. A create
                // whose transcript does not say which contract appeared is the
                // one line a reader most wants and the only one that was
                // missing.
                resolved.put("contractId", created.idContract());

                bind(stmt.nameBind(), new DamlValue.ContractRef(created.idContract()), TYPE_CID,
                        stmt);
                return;
            }
        }
        throw new CaqlException(stmt.numLine(), stmt.strSource(),
                "the create committed but the transaction carries no created contract");
    }


    // ------------------------------------------------------------ helpers

    private JsonCoercer coercer(Stmt stmt) {
        // The registry goes in so a $r.field inside a WITH can be given the
        // field's DECLARED type. Without it the substitution would have to
        // guess, and a value checked against a guessed type is checked against
        // the wrong thing exactly where it matters.
        return new JsonCoercer(registry, new EnvSubstitution(env, registry, stmt.numLine(),
                stmt.strSource()));
    }



    /**
     * The identifier to FILTER by, which is not always the one that was
     * resolved.
     *
     * <h2>THE REGISTRY ANSWERS ONE HALF AND THE TARGET THE OTHER</h2>
     *
     * Whether the package HAS a name is a question about the package, and the
     * registry here is what knows. Whether the participant will ACCEPT that
     * name in a filter is a question about the participant, and only the target
     * knows - so it is asked rather than assumed.
     *
     * This used to decide both here, on the rule "use the package name when the
     * package has one". The two questions coincide on 3.x and come apart on
     * 2.x, where a participant answers `PACKAGE_NAMES_NOT_FOUND` for a name
     * the package genuinely carries. The name existed; sending it was still
     * wrong.
     *
     * <h2>The transcript records both</h2>
     *
     * `templateId` stays the resolved identifier and `filterTemplateId` appears
     * only when the two differ - which is why the conversion is asked for here
     * rather than done inside the client where nothing could record it.
     *
     * @param idTemplate the resolved template identifier
     * @return the identifier to put in the filter
     */
    private DataId idFilter(DataId idTemplate) {
        return client.idFilter(idTemplate, registry.namePackage(idTemplate.idPackage()));
    }


    private TemplateInfo template(String strRef, Stmt stmt) {
        TemplateRef ref = TemplateRef.resolve(registry, strRef);
        if (ref instanceof TemplateRef.Found found)
            return found.template();

        throw new CaqlException(stmt.numLine(), stmt.strSource(), ref.strProblem());
    }


    /**
     * What an EXERCISE is sent to: the choice, and the id the command names.
     *
     * @param choice the choice, as the template or the interface declares it
     * @param idTarget the template for its own choice, the INTERFACE for an
     *                 inherited one - the Ledger API's `template_id` takes
     *                 either, and an interface choice is addressed by its
     *                 interface
     */
    private record Target(ChoiceInfo choice, DataId idTarget) {}


    /**
     * The template's own choice wins - ChoiceUnion's rule. An inherited choice
     * is sent to its interface, and one that MORE THAN ONE implemented
     * interface declares is refused unless VIA names the interface:
     * TransferOffer implements TransferInstructionV1 and V2, both declare
     * TransferInstruction_Accept with different arguments, and ChoiceUnion's
     * name de-duplication keeps one of the two without saying which.
     *
     * A registry that answers no interface choices - the default - falls back
     * to the union's own provenance, which is all a fake registry can offer.
     */
    private Target target(TemplateInfo template, Stmt.Exercise stmt) {
        String nameChoice = stmt.nameChoice();

        if (stmt.strInterface().isPresent()) {
            DataId idInterface = namedInterface(template, stmt.strInterface().get(), stmt);
            List<ChoiceInfo> lstOn = registry.interfaceChoices(idInterface);
            List<String> lstName = new ArrayList<>();
            for (ChoiceInfo info : lstOn) {
                if (info.nameChoice().equals(nameChoice))
                    return new Target(info, idInterface);
                lstName.add(info.nameChoice());
            }
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + nameChoice
                    + "' is not a choice on " + idInterface.shortName() + "; it offers "
                    + (lstName.isEmpty() ? "nothing the registry holds" : String.join(", ", lstName)));
        }

        for (ChoiceInfo info : template.lstChoice()) {
            if (info.nameChoice().equals(nameChoice) && !info.flagInherited())
                return new Target(info, template.idTemplate());
        }

        List<DataId> lstDeclaring = new ArrayList<>();
        ChoiceInfo choiceOnly = null;
        for (DataId idInterface : template.lstInterface()) {
            for (ChoiceInfo info : registry.interfaceChoices(idInterface)) {
                if (info.nameChoice().equals(nameChoice)) {
                    lstDeclaring.add(idInterface);
                    choiceOnly = info;
                }
            }
        }
        if (lstDeclaring.size() > 1) {
            List<String> lstShort = new ArrayList<>();
            for (DataId idInterface : lstDeclaring) {
                lstShort.add(idInterface.shortName());
            }
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + nameChoice
                    + "' is declared by " + lstDeclaring.size() + " interfaces "
                    + template.idTemplate().shortName() + " implements - "
                    + String.join(", ", lstShort) + "; name one with VIA <interface>");
        }
        if (lstDeclaring.size() == 1)
            return new Target(choiceOnly, lstDeclaring.get(0));

        ChoiceInfo choice = choice(template, nameChoice, stmt);
        return new Target(choice, choice.idInterface().orElse(template.idTemplate()));
    }


    /**
     * VIA's reference, resolved against what the TARGET implements rather than
     * against every interface on the ledger: module:entity, or the entity alone
     * when only one implemented interface carries that name.
     */
    private static DataId namedInterface(TemplateInfo template, String strRef, Stmt stmt) {
        List<DataId> lstHit = new ArrayList<>();
        List<String> lstShort = new ArrayList<>();
        for (DataId idInterface : template.lstInterface()) {
            lstShort.add(idInterface.shortName());
            if (strRef.equals(idInterface.toString()) || strRef.equals(idInterface.shortName()))
                return idInterface;
            if (strRef.equals(idInterface.nameEntity()))
                lstHit.add(idInterface);
        }
        if (lstHit.size() == 1)
            return lstHit.get(0);
        if (lstHit.size() > 1) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + strRef + "' names "
                    + lstHit.size() + " interfaces " + template.idTemplate().shortName()
                    + " implements; write module:entity - " + String.join(", ", lstShort));
        }
        throw new CaqlException(stmt.numLine(), stmt.strSource(), template.idTemplate().shortName()
                + " does not implement '" + strRef + "'; it implements "
                + (lstShort.isEmpty() ? "no interface" : String.join(", ", lstShort)));
    }


    private static ChoiceInfo choice(TemplateInfo template, String nameChoice, Stmt stmt) {
        List<String> lstName = new ArrayList<>();
        for (ChoiceInfo info : template.lstChoice()) {
            if (info.nameChoice().equals(nameChoice))
                return info;
            lstName.add(info.nameChoice());
        }

        throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + nameChoice
                + "' is not a choice on " + template.idTemplate().shortName() + "; it offers "
                + String.join(", ", lstName));
    }


    private List<String> parties(List<CaqlRef> lstRef, Stmt stmt) {
        List<String> lstParty = new ArrayList<>(lstRef.size());
        for (CaqlRef ref : lstRef) {
            lstParty.add(partyRef(ref, stmt));
        }
        return List.copyOf(lstParty);
    }


    /**
     * The value a reference denotes, projection included.
     *
     * Staleness is asked of the ROOT binding, not of the projected field. Sec.
     * 7 stales a binding that holds an archived contract id anywhere inside it,
     * so reaching into such a record for some other field is refused too - the
     * statement that produced it no longer describes the ledger.
     */
    private DamlValue valueRef(CaqlRef ref, Stmt stmt) {
        if (ref instanceof CaqlRef.Literal literal)
            return new DamlValue.Text(literal.strValue());

        CaqlRef.Var var = (CaqlRef.Var) ref;
        Binding binding = env.require(var.name(), stmt.numLine(), stmt.strSource());
        if (var.lstField().isEmpty())
            return binding.value();

        return FieldPath.project(binding, var.lstField(), registry, ref.str(), stmt.numLine(),
                stmt.strSource()).value();
    }


    private String partyRef(CaqlRef ref, Stmt stmt) {
        if (ref instanceof CaqlRef.Literal literal)
            return literal.strValue();

        DamlValue value = valueRef(ref, stmt);
        if (!(value instanceof DamlValue.Party party)) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + ref.str()
                    + "' is not a party");
        }
        return party.idParty();
    }


    private String contractRef(CaqlRef ref, Stmt stmt) {
        if (ref instanceof CaqlRef.Literal literal)
            return literal.strValue();

        DamlValue value = valueRef(ref, stmt);
        if (!(value instanceof DamlValue.ContractRef cid)) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + ref.str()
                    + "' is not a contract id");
        }
        return cid.idContract();
    }


    /**
     * An offset, which is TEXT on both generations by SPI contract.
     */
    private String offsetRef(CaqlRef ref, Stmt stmt) {
        if (ref instanceof CaqlRef.Literal literal)
            return literal.strValue();

        DamlValue value = valueRef(ref, stmt);
        if (value instanceof DamlValue.Text text)
            return text.str();

        throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + ref.str()
                + "' is not an offset; GET LEDGER END binds one");
    }


    private void bind(Optional<String> nameBind, DamlValue value, DamlType type, Stmt stmt) {
        if (nameBind.isEmpty())
            return;

        env.bind(Env.of(nameBind.get(), value, type, stmt.numLine(), null), stmt.strSource());
    }


    private void describe(ObjectNode resolved, DataId idTemplate, List<String> lstParty,
            String nameChoice, String idContract) {
        resolved.put("packageId", idTemplate.idPackage());
        resolved.put("templateId", idTemplate.toString());
        if (nameChoice != null)
            resolved.put("choice", nameChoice);
        resolved.set("actAs", texts(lstParty));
        if (idContract != null)
            resolved.put("contractId", idContract);
    }


    /**
     * The canonical typed form, which is the point of recording it: what was
     * written and what was sent differ once a variable or a bare template name
     * is involved.
     */
    private JsonNode rendered(DamlValue value) {
        try {
            return mapper.readTree(new com.raposza.render.JsonRenderer(false)
                    .value(value, Optional.empty()));
        }
        catch (Exception ex) {
            return mapper.createObjectNode();
        }
    }


    private ArrayNode texts(List<String> lstStr) {
        ArrayNode arr = mapper.createArrayNode();
        for (String str : lstStr) {
            arr.add(str);
        }
        return arr;
    }


    private Entry entry(Stmt stmt, String idStatement, RunStatus status,
            Optional<String> nameBind, ObjectNode resolved) {
        resolved.put(STR_KEY_KIND, strKindOf(stmt));
        return new Entry(idStatement, stmt.numLine(), stmt.strSource(), status, nameBind,
                resolved, Optional.empty(), Optional.empty(), Optional.empty());
    }


    /**
     * `ASSERT` and `EXPECT` do not pass through here or through
     * {@link #entry}: they carry the inner statement's node, so they keep
     * the inner statement's kind - which is what actually reached the
     * participant.
     *
     * @param stmt the statement
     * @return its name, in the words the language uses
     */
    private static String strKindOf(Stmt stmt) {
        return switch (stmt) {
            case Stmt.Allocate ignored -> "allocate party";
            case Stmt.CreateUser ignored -> "create user";
            case Stmt.DeleteUser ignored -> "delete user";
            case Stmt.GetUser ignored -> "get user";
            case Stmt.GetLedgerEnd ignored -> "get ledger end";
            case Stmt.Create ignored -> "create";
            case Stmt.Exercise ignored -> "exercise";
            case Stmt.ExerciseByKey ignored -> "exercise on key";
            case Stmt.Fetch ignored -> "fetch";
            case Stmt.FetchSingle ignored -> "fetch single";
            case Stmt.Query ignored -> "query";
            case Stmt.Assert ignored -> "assert";
            case Stmt.Expect ignored -> "expect";
            case Stmt.Prune ignored -> "prune";

            case Stmt.Rights val -> (val.flagGrant() ? "grant " : "revoke ")
                    + (val.flagActAs() ? "canActAs" : "canReadAs");

            case Stmt.ListOf val -> switch (val.kind()) {
                case PARTIES -> "list parties";
                case USERS -> "list users";
                case PACKAGES -> "list packages";
            };
        };
    }


    private Entry failed(Stmt stmt, String idStatement, ObjectNode resolved,
            RuntimeException ex) {
        resolved.put(STR_KEY_KIND, strKindOf(stmt));
        String strWhy = ex instanceof CaqlException ? ex.getMessage() : ex.toString();
        return new Entry(idStatement, stmt.numLine(), stmt.strSource(), RunStatus.FAILED_LOCALLY,
                Optional.empty(), resolved, Optional.empty(), Optional.empty(),
                Optional.of(strWhy));
    }


    /**
     * Whitespace collapsed, so reindenting a script does not change its
     * statement ids. Sec. 8 wants an id that survives an edit; an id that moved
     * when the formatting did would survive nothing.
     */
    private static String normalise(String strSource) {
        return strSource == null ? "" : strSource.trim().replaceAll("\\s+", " ");
    }


    private static String hash(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(str.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }


    /**
     * `CREATE USER ... WITH` is NOT type-directed by the registry - sec. 5.1. A
     * user is not a Daml value, so its payload is validated against this fixed
     * schema and the errors differ in character from every other WITH.
     */
    private record UserSpec(String idPartyPrimary, List<String> lstPartyAct,
            List<String> lstPartyRead) {

        static UserSpec of(Stmt.CreateUser stmt, Env env) {
            JsonNode node = stmt.nodeWith();
            String idPartyPrimary = null;

            Iterator<String> itName = node.fieldNames();
            while (itName.hasNext()) {
                String strName = itName.next();
                if (!"primaryParty".equals(strName) && !"rights".equals(strName)) {
                    throw new CaqlException(stmt.numLine(), stmt.strSource(),
                            "a user payload has no field '" + strName
                                    + "'; it takes primaryParty and rights");
                }
            }

            if (node.has("primaryParty") && !node.get("primaryParty").isNull())
                idPartyPrimary = party(node.get("primaryParty"), stmt, env, "primaryParty");

            List<String> lstAct = new ArrayList<>();
            List<String> lstRead = new ArrayList<>();
            JsonNode nodeRights = node.get("rights");

            if (nodeRights != null && !nodeRights.isNull()) {
                if (!nodeRights.isArray()) {
                    throw new CaqlException(stmt.numLine(), stmt.strSource(),
                            "rights takes an array");
                }
                for (int cntLoop = 0; cntLoop < nodeRights.size(); cntLoop++) {
                    right(nodeRights.get(cntLoop), stmt, env, lstAct, lstRead, cntLoop);
                }
            }

            return new UserSpec(idPartyPrimary, List.copyOf(lstAct), List.copyOf(lstRead));
        }


        private static void right(JsonNode node, Stmt.CreateUser stmt, Env env,
                List<String> lstAct, List<String> lstRead, int cntIndex) {
            String strAt = "rights[" + cntIndex + "]";
            if (!node.isObject() || node.size() != 1) {
                throw new CaqlException(stmt.numLine(), stmt.strSource(),
                        strAt + " takes exactly one of canActAs or canReadAs");
            }

            String strKind = node.fieldNames().next();
            switch (strKind) {
                case "canActAs":
                    lstAct.add(party(node.get(strKind), stmt, env, strAt));
                    return;

                case "canReadAs":
                    lstRead.add(party(node.get(strKind), stmt, env, strAt));
                    return;

                default:
                    // participantAdmin is a real Ledger API right and is
                    // deliberately not reachable from here. A fixture
                    // stands a ledger up; it does not mint an administrator,
                    // and a language that could do both would do the second by
                    // accident.
                    throw new CaqlException(stmt.numLine(), stmt.strSource(), strAt
                            + ": '" + strKind + "' is not a right this tool grants;"
                            + " it accepts canActAs and canReadAs");
            }
        }


        /**
         * An ordinary whole-string substitution - sec. 5.1 - which must resolve
         * to a binding of party type. Not the registry-driven coercer: there is
         * no Daml type here to direct it.
         */
        private static String party(JsonNode node, Stmt.CreateUser stmt, Env env, String strAt) {
            if (!node.isTextual()) {
                throw new CaqlException(stmt.numLine(), stmt.strSource(),
                        strAt + " takes a party id or a binding");
            }

            String str = node.textValue();
            if (str.startsWith("$$"))
                return str.substring(1);
            if (!str.startsWith("$"))
                return str;

            Binding binding = env.require(str.substring(1), stmt.numLine(), stmt.strSource());
            if (!(binding.value() instanceof DamlValue.Party party)) {
                throw new CaqlException(stmt.numLine(), stmt.strSource(),
                        strAt + ": '" + str + "' is not a party");
            }
            return party.idParty();
        }

    }

}
