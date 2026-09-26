// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * One parsed statement. Grammar sec. 4.
 *
 * <h2>Every statement carries its own source</h2>
 *
 * Not for error messages alone: the transcript records what
 * the operator WROTE alongside what it resolved to, and a statement continues
 * across lines while a JSON object is open, so the source cannot be recovered
 * from a line number afterwards. Captured once, here.
 *
 * <h2>Binding is on the four statements that can bind, and nowhere else</h2>
 *
 * QUERY and CREATE USER have no binding field at all rather than an empty one.
 * A record with a field that is always absent invites a caller to check it, and
 * the check would be dead. The grammar makes both a syntax error - sec. 4 - and
 * this is the same statement in Java.
 *
 * A template reference stays a STRING here. Resolving it needs a registry
 * snapshot, which exists at run time and not at parse time, and a parser that
 * needed one could not be tested without one.
 *
 * Author Claude/bentzn
 */
public sealed interface Stmt {

    /** @return one-based line the statement starts on */
    int numLine();


    /** @return the statement as written, comments stripped, continuations joined */
    String strSource();


    /**
     * A read that answers with a set, filtered in the Workbench.
     *
     * NEITHER LEDGER API GENERATION FILTERS AN ACTIVE-CONTRACT READ BY PAYLOAD
     * CONTENT, so the WHERE clause is a sieve over what the read returned. It
     * narrows and never widens: the parties still decide what is visible, and
     * the read is capped before the sieve sees it - which is why the runner
     * records the count before the sieve as well as after.
     *
     * WITH is REFUSED here. It used to be parsed and discarded, and two
     * spellings of a filter where only one narrows anything is worse than
     * the silence it replaced.
     *
     * @param numLine line
     * @param strSource source
     * @param lstParty readAs parties
     * @param strTemplate the template reference, unresolved
     * @param clauseWhere the filter, empty when the statement had no WHERE
     */
    record Query(int numLine, String strSource, List<CaqlRef> lstParty, String strTemplate,
            Optional<Clause> clauseWhere) implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     * @param lstParty readAs parties
     * @param refContract the contract id
     */
    record Fetch(int numLine, String strSource, Optional<String> nameBind, List<CaqlRef> lstParty,
            CaqlRef refContract) implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     * @param lstParty actAs parties
     * @param strTemplate the template reference, unresolved
     * @param nodeWith the create argument; required, so not optional
     */
    record Create(int numLine, String strSource, Optional<String> nameBind, List<CaqlRef> lstParty,
            String strTemplate, JsonNode nodeWith) implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param idUser the user id, chosen by the script
     * @param nodeWith the user payload, validated against a fixed schema in the
     *                 runner rather than against the registry - sec. 5.1
     */
    record CreateUser(int numLine, String strSource, String idUser, JsonNode nodeWith)
            implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     * @param lstParty actAs parties
     * @param nameChoice the choice
     * @param refContract the target contract id
     * @param nodeWith the choice argument, empty when the statement had no WITH
     */
    record Exercise(int numLine, String strSource, Optional<String> nameBind,
            List<CaqlRef> lstParty, String nameChoice, CaqlRef refContract,
            Optional<JsonNode> nodeWith) implements Stmt {}


    /**
     * Exercise a choice on the contract a key resolves to.
     *
     * The template is NAMED here where Exercise has the participant report it,
     * because a key without a template is not a lookup - the same key value
     * means different contracts under different templates.
     *
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     * @param lstParty actAs parties
     * @param strTemplate the template reference, unresolved
     * @param nodeKey the key, coerced against the template's declared key type
     * @param nameChoice the choice
     * @param nodeWith the choice argument, empty when the statement had no WITH
     */
    record ExerciseByKey(int numLine, String strSource, Optional<String> nameBind,
            List<CaqlRef> lstParty, String strTemplate, JsonNode nodeKey, String nameChoice,
            Optional<JsonNode> nodeWith) implements Stmt {}


    /**
     * A strict single-result read that binds the contract id it found.
     *
     * NOT a QUERY that binds. The active contract set has no defined order, so
     * "the first" is a nondeterministic row and a fixture built on one passes
     * on Tuesday. Exactly one match binds; zero or many fails the run, which is
     * the same strictness sec. 6 already gives FETCH.
     *
     * IT TAKES NO FILTER - his decision on `todo.md` P-1, 2026-09-25. A `WITH`
     * here was parsed and never applied, so a SINGLE only ever succeeded where
     * the template has exactly one visible contract and a filter written to
     * narrow it changed nothing. The parser now refuses it by name, as D-751
     * refused `WITH` on a `QUERY`, and names `QUERY ... WHERE` instead.
     *
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     * @param lstParty readAs parties
     * @param strTemplate the template reference, unresolved
     */
    record FetchSingle(int numLine, String strSource, Optional<String> nameBind,
            List<CaqlRef> lstParty, String strTemplate) implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param idUser the user id
     */
    record DeleteUser(int numLine, String strSource, String idUser) implements Stmt {}


    /**
     * Grant or revoke one right on an existing user.
     *
     * ONE record for both directions with a flag, not two records. They differ
     * in a single bit at every level - grammar, runner, SPI and wire - and two
     * records would mean two of everything to keep in step.
     *
     * @param numLine line
     * @param strSource source
     * @param flagGrant true for GRANT, false for REVOKE
     * @param flagActAs true for canActAs, false for canReadAs
     * @param refParty the party the right is about
     * @param idUser the user id
     */
    record Rights(int numLine, String strSource, boolean flagGrant, boolean flagActAs,
            CaqlRef refParty, String idUser) implements Stmt {}


    /** What a LIST enumerates. */
    enum ListKind {

        PARTIES,
        USERS,
        PACKAGES

    }


    /**
     * @param numLine line
     * @param strSource source
     * @param kind what to enumerate
     */
    record ListOf(int numLine, String strSource, ListKind kind) implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param idUser the user id
     */
    record GetUser(int numLine, String strSource, String idUser) implements Stmt {}


    /**
     * The current ledger end.
     *
     * THE ONLY READ THAT BINDS, because it is the only one that answers with a
     * scalar. A LIST holds a collection, a collection needs indexing, indexing
     * is an expression, and sec. 12 is gone - so those are a syntax error to
     * bind and this is not.
     *
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     */
    record GetLedgerEnd(int numLine, String strSource, Optional<String> nameBind)
            implements Stmt {}


    /**
     * A count assertion over a read.
     *
     * The comparison is against a LITERAL and there is no operator. Neither
     * operand is computed and nothing is arithmetic, which is the whole
     * guardrail: without it the next request is COUNT &gt; $n and the one after
     * that is COUNT &gt; $n + 1.
     *
     * @param numLine line
     * @param strSource source
     * @param stmtRead the read being asserted over
     * @param cntExpected the count it must have
     */
    record Assert(int numLine, String strSource, Stmt stmtRead, int cntExpected)
            implements Stmt {}


    /**
     * A statement whose expected outcome is stated, so a failure is a pass.
     *
     * The status is matched against the SIX-STATE table rather than against
     * "it failed". Sec. 8 exists to refuse that conflation, and an EXPECT that
     * passed on OUTCOME_UNKNOWN when REJECTED was meant would be worse than no
     * negative testing at all.
     *
     * @param numLine line
     * @param strSource source
     * @param status the outcome that counts as a pass
     * @param stmtInner the statement to run
     */
    record Expect(int numLine, String strSource, RunStatus status, Stmt stmtInner)
            implements Stmt {}


    /**
     * Prune the participant up to an offset. IRREVERSIBLE.
     *
     * The offset is REQUIRED - there is no prune-everything form on either
     * generation's wire and there is not one here.
     *
     * @param numLine line
     * @param strSource source
     * @param refOffset the offset, a literal or a binding
     */
    record Prune(int numLine, String strSource, CaqlRef refOffset) implements Stmt {}


    /**
     * @param numLine line
     * @param strSource source
     * @param nameBind binding name, empty when unbound
     * @param hintParty the party id hint. A HINT: the participant is free to
     *                  return something else, measured on 2.x
     */
    record Allocate(int numLine, String strSource, Optional<String> nameBind, String hintParty)
            implements Stmt {}

}
