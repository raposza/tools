// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a script into statements. Grammar sec. 4.
 *
 * <h2>What it checks, and what it refuses to check</h2>
 *
 * Everything decidable from the text alone: shape, keywords, binding names,
 * duplicate bindings, well-formed JSON. Nothing that needs a participant or a
 * registry - a template reference stays a string, a binding is not checked
 * against what it will hold, and a contract id is not validated. Those are the
 * runner's, and a parser that reached for them could not be tested without a
 * ledger.
 *
 * <h2>Rebinding is refused HERE</h2>
 *
 * Sec. 6 says binding a name twice is far more often a mistake than an
 * intention, and the parser is where the whole script is in view - a runner
 * would only notice on reaching the second one, after the first has already
 * changed the participant. Refusing before anything runs is the difference
 * between a typo and a half-built fixture.
 *
 * <h2>A party may be written without quotes</h2>
 *
 * {@code AS Plant-1::1220ab CREATE ...} reads as mostly punctuation when the
 * id is quoted, and the quotes carry nothing: a party id is the only value
 * this grammar accepts that contains {@code ::}, so the separator is the whole
 * test. It is narrow ON PURPOSE - a bare word accepted outright would read
 * {@code AS QUERY M:T} as a party called QUERY, because keywords are WORDs
 * too.
 *
 * A CONTRACT ID IS DELIBERATELY NOT INCLUDED. A bare hex run and an
 * unqualified template reference are both WORDs and FETCH takes either, so
 * dropping its quotes would cost the one rule that tells the two FETCH forms
 * apart. The payload stays strict JSON for the same reason it always was: one
 * notation, so what a detail pane renders pastes back.
 *
 * <h2>Keywords are reserved</h2>
 *
 * So {@code as = ALLOCATE PARTY "x"} is fine and {@code AS = ...} is not.
 * Without this a script could bind a name that later reads as syntax, and the
 * failure would arrive at the line that USED it.
 *
 * Author Claude/bentzn
 */
public final class CaqlParser {

    /**
     * Reserved, and matched CASE-SENSITIVELY against these spellings. So a
     * script binding {@code key} or {@code count} is unaffected and only one
     * written in capitals collides - which is why growing this set by eighteen
     * words breaks almost nothing that already exists.
     */
    private static final Set<String> SET_KEYWORD = Set.of("AS", "QUERY", "FETCH", "CREATE",
            "EXERCISE", "ALLOCATE", "PARTY", "USER", "ON", "WITH", "KEY", "SINGLE",
            "DELETE", "GRANT", "REVOKE", "TO", "FROM", "LIST", "GET", "PARTIES", "USERS",
            "PACKAGES", "LEDGER", "END", "ASSERT", "COUNT", "EXPECT", "PRUNE", "WHERE", "AND",
            "OR", "NOT", "VIA");

    /**
     * The words that can START a statement.
     *
     * Used for ONE thing: when a statement has tokens left over, deciding
     * whether the leftover is junk or the beginning of the NEXT statement -
     * which is what a forgotten ';' looks like from in here. A newline stopped
     * being a terminator on 2026-09-14, so two statements written on two lines
     * with no ';' between them arrive as one, and "unexpected 'AS'" would send
     * the reader looking for a typo that is not there.
     */
    private static final Set<String> SET_START = Set.of("AS", "ALLOCATE", "CREATE", "DELETE",
            "GRANT", "REVOKE", "LIST", "GET", "ASSERT", "EXPECT", "PRUNE");

    /** The two rights the language grants. Spelled as the CREATE USER payload spells them. */
    private static final String STR_ACT_AS = "canActAs";

    private static final String STR_READ_AS = "canReadAs";


    /**
     * Published so an editor colours exactly what the parser reserves. A list
     * kept anywhere else would agree with this one until the day it did not,
     * and the symptom - a keyword that stops being a keyword on screen while
     * the parser still refuses it as a binding name - reads as a bug in the
     * language rather than in a colour table.
     *
     * @return the reserved words, immutable
     */
    public static Set<String> setKeyword() {
        return SET_KEYWORD;
    }


    /**
     * The span of the statement the caret is in, for an editor that runs one.
     *
     * @param strScript the whole script
     * @param idxCaret the caret offset
     * @return {from, toExclusive} character offsets, or null when the caret is
     *         in no statement
     */
    public static int[] spanAt(String strScript, int idxCaret) {
        return Splitter.spanAt(strScript, idxCaret);
    }


    private CaqlParser() {
    }


    /**
     * @param strScript the script
     * @return its statements, in order
     * @throws CaqlException on the first thing that is wrong
     */
    public static List<Stmt> parse(String strScript) {
        List<Stmt> lstStmt = new ArrayList<>();
        Set<String> setBound = new LinkedHashSet<>();

        for (Splitter.Logical logical : Splitter.split(strScript)) {
            Stmt stmt = statement(logical, Lexer.lex(logical), setBound);
            lstStmt.add(stmt);
        }
        return List.copyOf(lstStmt);
    }


    /**
     * @param logical the statement, for line and source on every failure
     * @param lstToken its tokens, or what is left of them after a prefix
     * @param setBound names bound so far in the whole script
     * @return the statement
     */
    private static Stmt statement(Splitter.Logical logical, List<Token> lstToken,
            Set<String> setBound) {
        Cursor cur = new Cursor(logical, lstToken);

        Optional<String> nameBind = Optional.empty();
        if (cur.size() >= 2 && cur.at(0).kind() == Token.Kind.WORD
                && cur.at(1).kind() == Token.Kind.EQ) {
            nameBind = Optional.of(bindingName(cur, cur.at(0).str(), setBound));
            cur.skip(2);
        }

        String strFirst = cur.word("a statement must start with one of AS, ALLOCATE, CREATE,"
                + " DELETE, GRANT, REVOKE, LIST, GET, ASSERT, EXPECT or PRUNE");

        switch (strFirst) {
            case "AS":
                return withParties(cur, nameBind);

            case "DELETE": {
                unbound(cur, nameBind, "DELETE USER");
                cur.expect("USER", "DELETE must be followed by USER");
                String idUser = cur.text("DELETE USER needs a user id in quotes");
                cur.end();
                return new Stmt.DeleteUser(cur.numLine(), cur.strSource(), idUser);
            }

            case "GRANT":
            case "REVOKE": {
                unbound(cur, nameBind, strFirst);
                return rights(cur, "GRANT".equals(strFirst));
            }

            case "LIST": {
                // A LIST holds a collection and the language has no way to read
                // into one, so binding it would bind something unusable. A
                // syntax error, not a discarded binding - sec. 12.
                unbound(cur, nameBind, "LIST");
                String strWhat = cur.word("LIST must be followed by PARTIES, USERS or PACKAGES");
                Stmt.ListKind kind = switch (strWhat) {
                    case "PARTIES" -> Stmt.ListKind.PARTIES;
                    case "USERS" -> Stmt.ListKind.USERS;
                    case "PACKAGES" -> Stmt.ListKind.PACKAGES;
                    default -> throw cur.fail("'" + strWhat + "' is not something to list;"
                            + " expected PARTIES, USERS or PACKAGES");
                };
                cur.end();
                return new Stmt.ListOf(cur.numLine(), cur.strSource(), kind);
            }

            case "GET": {
                String strWhat = cur.word("GET must be followed by USER or LEDGER END");
                if ("USER".equals(strWhat)) {
                    unbound(cur, nameBind, "GET USER");
                    String idUser = cur.text("GET USER needs a user id in quotes");
                    cur.end();
                    return new Stmt.GetUser(cur.numLine(), cur.strSource(), idUser);
                }
                if ("LEDGER".equals(strWhat)) {
                    cur.expect("END", "GET LEDGER must be followed by END");
                    cur.end();
                    return new Stmt.GetLedgerEnd(cur.numLine(), cur.strSource(), nameBind);
                }
                throw cur.fail("'" + strWhat + "' cannot be got; expected USER or LEDGER END");
            }

            case "ASSERT": {
                unbound(cur, nameBind, "ASSERT");
                return assertion(cur, logical, setBound);
            }

            case "EXPECT": {
                unbound(cur, nameBind, "EXPECT");
                return expectation(cur, logical, setBound);
            }

            case "PRUNE": {
                unbound(cur, nameBind, "PRUNE");
                cur.expect("TO", "PRUNE must be followed by TO and an offset");
                CaqlRef refOffset = cur.valueRef("PRUNE TO needs an offset or a binding;"
                        + " there is no prune-everything form");
                cur.end();
                return new Stmt.Prune(cur.numLine(), cur.strSource(), refOffset);
            }

            case "CREATE": {
                // CREATE without AS is CREATE USER and nothing else. A create
                // needs a submitter, so the missing AS is the actual error and
                // saying "USER expected" would send the reader the wrong way.
                if (nameBind.isPresent())
                    throw cur.fail("CREATE USER does not bind: the user id is already in the"
                            + " statement, so a binding would restate a literal");
                cur.expect("USER", "CREATE outside an AS clause must be CREATE USER;"
                        + " a CREATE of a contract needs 'AS <party>' in front of it");

                String idUser = cur.text("CREATE USER needs a user id in quotes");
                cur.expect("WITH", "CREATE USER needs a WITH payload");
                JsonNode nodeWith = cur.json("CREATE USER needs a JSON object after WITH");
                cur.end();
                return new Stmt.CreateUser(cur.numLine(), cur.strSource(), idUser, nodeWith);
            }

            case "ALLOCATE": {
                cur.expect("PARTY", "ALLOCATE must be followed by PARTY");
                String hintParty = cur.text("ALLOCATE PARTY needs a hint in quotes");
                cur.end();
                return new Stmt.Allocate(cur.numLine(), cur.strSource(), nameBind, hintParty);
            }

            default:
                throw cur.fail("'" + strFirst + "' does not start a statement; expected one of"
                        + " AS, ALLOCATE, CREATE, DELETE, GRANT, REVOKE, LIST, GET, ASSERT,"
                        + " EXPECT or PRUNE");
        }
    }


    /**
     * GRANT canActAs "$p" TO USER "u" / REVOKE canReadAs "$p" FROM USER "u".
     *
     * The two right words are the ones a CREATE USER payload already uses. One
     * vocabulary for rights across the whole language means an editor's
     * completion list after GRANT is the list it uses inside the payload, and
     * it means an operator never has to remember which spelling this position
     * wants.
     */
    private static Stmt rights(Cursor cur, boolean flagGrant) {
        String strWord = flagGrant ? "GRANT" : "REVOKE";
        String strRight = cur.word(strWord + " must name " + STR_ACT_AS + " or " + STR_READ_AS);

        boolean flagActAs;
        if (STR_ACT_AS.equals(strRight)) {
            flagActAs = true;
        }
        else if (STR_READ_AS.equals(strRight)) {
            flagActAs = false;
        }
        else {
            // participantAdmin is a real Ledger API right and is deliberately
            // unreachable, exactly as it is from a CREATE USER payload. A
            // fixture stands a ledger up; it does not mint an administrator.
            throw cur.fail("'" + strRight + "' is not a right this tool " + strWord.toLowerCase()
                    + "s; it accepts " + STR_ACT_AS + " and " + STR_READ_AS);
        }

        CaqlRef refParty = cur.valueRef(strWord + " needs a party or a binding");
        cur.expect(flagGrant ? "TO" : "FROM",
                strWord + " needs " + (flagGrant ? "TO" : "FROM") + " followed by USER");
        cur.expect("USER", (flagGrant ? "TO" : "FROM") + " must be followed by USER");
        String idUser = cur.text(strWord + " needs a user id in quotes");
        cur.end();

        return new Stmt.Rights(cur.numLine(), cur.strSource(), flagGrant, flagActAs, refParty,
                idUser);
    }


    /**
     * ASSERT &lt;read&gt; COUNT &lt;n&gt;.
     *
     * Only a read is assertable. Asserting over a write would mean submitting
     * in order to check a number, and a fixture that changes the ledger as a
     * side effect of an assertion is the thing sec. 8 stops the run to avoid.
     */
    private static Stmt assertion(Cursor cur, Splitter.Logical logical, Set<String> setBound) {
        cur.expect("AS", "ASSERT takes a read: AS <party> QUERY <template> ... COUNT <n>");

        List<CaqlRef> lstParty = partyList(cur);
        String strVerb = cur.word("ASSERT takes QUERY after the parties");
        if (!"QUERY".equals(strVerb))
            throw cur.fail("ASSERT takes QUERY, not '" + strVerb + "'");

        String strTemplate = cur.reference("QUERY needs a template reference");
        Optional<Clause> clauseWhere = where(cur);

        cur.expect("COUNT", "ASSERT needs COUNT followed by a whole number");
        int cntExpected = cur.count();
        cur.end();

        Stmt stmtRead = new Stmt.Query(cur.numLine(), cur.strSource(), lstParty, strTemplate,
                clauseWhere);
        return new Stmt.Assert(cur.numLine(), cur.strSource(), stmtRead, cntExpected);
    }


    /**
     * EXPECT &lt;status&gt; &lt;statement&gt;.
     *
     * A PREFIX, so the editor - and the reader - knows from the first token
     * that this statement's failure is not fatal. As a suffix it would be
     * knowable only after the statement had been read.
     *
     * The status is one of the six, not a boolean "it failed". Sec. 8 exists
     * because rejected and outcome-unknown are different claims, and an EXPECT
     * that passed on a timeout when a rejection was meant would be worse than
     * no negative testing.
     */
    private static Stmt expectation(Cursor cur, Splitter.Logical logical, Set<String> setBound) {
        String strStatus = cur.word("EXPECT needs an outcome: "
                + String.join(", ", statusNames()));

        RunStatus status = null;
        for (RunStatus val : RunStatus.values()) {
            if (val.strJson().equalsIgnoreCase(strStatus) || val.name().equalsIgnoreCase(strStatus))
                status = val;
        }
        if (status == null) {
            throw cur.fail("'" + strStatus + "' is not an outcome; expected one of "
                    + String.join(", ", statusNames()));
        }
        if (status == RunStatus.COMMITTED) {
            throw cur.fail("EXPECT committed says nothing: a statement that commits is already"
                    + " the case that continues");
        }

        // The inner statement is parsed from what is LEFT, against the same
        // bound-name set, so a binding it would make is still refused if the
        // name is taken. It cannot bind anything itself - see below.
        Stmt stmtInner = statement(logical, cur.rest(), setBound);
        if (bindOf(stmtInner).isPresent()) {
            throw cur.fail("an EXPECT statement cannot bind: the outcome it expects is one where"
                    + " nothing was produced to bind");
        }

        return new Stmt.Expect(cur.numLine(), cur.strSource(), status, stmtInner);
    }


    private static List<String> statusNames() {
        List<String> lstName = new ArrayList<>();
        for (RunStatus val : RunStatus.values()) {
            if (val != RunStatus.COMMITTED)
                lstName.add(val.strJson());
        }
        return lstName;
    }


    /** @return the name a statement binds, empty when it binds nothing */
    static Optional<String> bindOf(Stmt stmt) {
        return switch (stmt) {
            case Stmt.Fetch val -> val.nameBind();
            case Stmt.FetchSingle val -> val.nameBind();
            case Stmt.Create val -> val.nameBind();
            case Stmt.Exercise val -> val.nameBind();
            case Stmt.ExerciseByKey val -> val.nameBind();
            case Stmt.Allocate val -> val.nameBind();
            case Stmt.GetLedgerEnd val -> val.nameBind();
            default -> Optional.empty();
        };
    }


    private static void unbound(Cursor cur, Optional<String> nameBind, String strWhat) {
        if (nameBind.isPresent()) {
            throw cur.fail(strWhat + " does not bind: it produces nothing the language can hold,"
                    + " and a discarded binding would read as one that worked");
        }
    }


    private static Stmt withParties(Cursor cur, Optional<String> nameBind) {
        List<CaqlRef> lstParty = partyList(cur);
        String strVerb = cur.word("expected QUERY, FETCH, CREATE or EXERCISE after the parties");

        switch (strVerb) {
            case "QUERY": {
                if (nameBind.isPresent()) {
                    throw cur.fail("QUERY does not bind: it returns a set of contracts and the"
                            + " language has no way to read into one");
                }
                String strTemplate = cur.reference("QUERY needs a template reference");
                Optional<Clause> clauseWhere = where(cur);
                cur.end();
                return new Stmt.Query(cur.numLine(), cur.strSource(), lstParty, strTemplate,
                        clauseWhere);
            }

            case "FETCH": {
                // Two forms, told apart by TOKEN KIND and nothing subtler: a
                // contract id is TEXT or VAR, a template reference is a WORD.
                if (cur.peek(Token.Kind.WORD)) {
                    String strTemplate = cur.reference("FETCH needs a template reference");
                    // WITH IS REFUSED BY NAME - his decision on `todo.md` P-1,
                    // 2026-09-25. It was parsed and never applied, so a script
                    // that carried one narrowed nothing and read as if it had.
                    if (cur.peekWord("WITH")) {
                        throw cur.fail("WITH on FETCH ... SINGLE narrows nothing and is refused;"
                                + " to narrow, write FETCH <template> WHERE <clause> SINGLE");
                    }
                    // WHERE NARROWS THE READ - 2026-10-03. The QUERY's clause,
                    // so the QUERY's rules decide what parses.
                    Optional<Clause> clauseWhere = where(cur);
                    cur.expect("SINGLE", "a FETCH by predicate must end in SINGLE; there is no"
                            + " form that takes the first of several, because the active"
                            + " contract set has no order to take the first of");
                    cur.end();
                    return new Stmt.FetchSingle(cur.numLine(), cur.strSource(), nameBind,
                            lstParty, strTemplate, clauseWhere);
                }

                CaqlRef refContract = cur.valueRef("FETCH needs a contract id, a binding, or a"
                        + " template reference followed by SINGLE");
                cur.end();
                return new Stmt.Fetch(cur.numLine(), cur.strSource(), nameBind, lstParty,
                        refContract);
            }

            case "CREATE": {
                String strTemplate = cur.reference("CREATE needs a template reference");
                cur.expect("WITH", "CREATE needs a WITH payload; a template's fields are not"
                        + " optional");
                JsonNode nodeWith = cur.json("CREATE needs a JSON object after WITH");
                cur.end();
                return new Stmt.Create(cur.numLine(), cur.strSource(), nameBind, lstParty,
                        strTemplate, nodeWith);
            }

            // EXERCISE ON <target> <choice>, in that order, so the whole
            // statement resolves LEFT TO RIGHT: the contract gives the
            // template, the template gives the choice set, the choice gives the
            // type of the WITH object. Naming the choice first - as this did
            // until 2026-08-25 - means the choice must be offered before
            // anything knows which template it belongs to, and no amount of
            // editor cleverness recovers that. It also matches Daml's own
            // `exercise cid Choice arg`.
            case "EXERCISE": {
                cur.expect("ON", "EXERCISE is followed by ON, then the target, then the choice");

                if (cur.peekWord("KEY")) {
                    cur.skip(1);
                    String strTemplate = cur.reference("ON KEY needs a template reference");
                    JsonNode nodeKey = cur.json("ON KEY needs the key as a JSON object after the"
                            + " template");
                    String nameChoice = cur.reference("EXERCISE needs a choice name after the"
                            + " key");

                    Optional<JsonNode> nodeWith = Optional.empty();
                    if (cur.peekWord("WITH")) {
                        cur.skip(1);
                        nodeWith = Optional.of(cur.json("WITH needs a JSON object"));
                    }
                    cur.end();
                    return new Stmt.ExerciseByKey(cur.numLine(), cur.strSource(), nameBind,
                            lstParty, strTemplate, nodeKey, nameChoice, nodeWith);
                }

                CaqlRef refContract = cur.valueRef("ON needs a contract id, a binding, or KEY");
                String nameChoice = cur.reference("EXERCISE needs a choice name after the"
                        + " contract");

                // VIA NAMES THE INTERFACE - 2026-10-03, his choice of form. After
                // the choice, so completion still offers choices once the target
                // is known - D-558.
                Optional<String> strInterface = Optional.empty();
                if (cur.peekWord("VIA")) {
                    cur.skip(1);
                    strInterface = Optional.of(cur.reference("VIA needs an interface reference,"
                            + " module:entity"));
                }

                Optional<JsonNode> nodeWith = Optional.empty();
                if (cur.peekWord("WITH")) {
                    cur.skip(1);
                    nodeWith = Optional.of(cur.json("WITH needs a JSON object"));
                }
                cur.end();
                return new Stmt.Exercise(cur.numLine(), cur.strSource(), nameBind, lstParty,
                        nameChoice, refContract, strInterface, nodeWith);
            }

            default:
                throw cur.fail("'" + strVerb + "' is not a command; expected QUERY, FETCH,"
                        + " CREATE or EXERCISE");
        }
    }


    /**
     * The optional WHERE of a QUERY, including the QUERY inside an ASSERT.
     *
     * WITH is refused HERE, by name. It was parsed and discarded on a QUERY for
     * long enough that a script may still carry one, and "unexpected 'WITH'"
     * would send its author looking for a typo. The refusal says what to
     * write instead.
     */
    private static Optional<Clause> where(Cursor cur) {
        if (cur.peekWord("WITH")) {
            throw cur.fail("WITH on a QUERY narrows nothing and is refused; to filter on"
                    + " payload contents write WHERE <field> <op> <value>");
        }
        if (!cur.peekWord("WHERE"))
            return Optional.empty();

        cur.skip(1);
        if (cur.size() == 0)
            throw cur.fail("WHERE needs a clause after it");
        return Optional.of(orClause(cur));
    }


    /**
     * Precedence: NOT binds tightest, then AND, then OR - so
     * {@code a AND b OR NOT c} reads as {@code (a AND b) OR (NOT c)}.
     * Parentheses regroup. One method per level, each consuming its own
     * operator and handing the tighter level everything else.
     */
    private static Clause orClause(Cursor cur) {
        Clause clause = andClause(cur);
        while (cur.peekWord("OR")) {
            cur.skip(1);
            clause = new Clause.Or(clause, andClause(cur));
        }
        return clause;
    }


    private static Clause andClause(Cursor cur) {
        Clause clause = notClause(cur);
        while (cur.peekWord("AND")) {
            cur.skip(1);
            clause = new Clause.And(clause, notClause(cur));
        }
        return clause;
    }


    private static Clause notClause(Cursor cur) {
        if (cur.peekWord("NOT")) {
            cur.skip(1);
            return new Clause.Not(notClause(cur));
        }
        if (cur.peek(Token.Kind.LPAREN)) {
            cur.skip(1);
            Clause clause = orClause(cur);
            if (!cur.peek(Token.Kind.RPAREN))
                throw cur.fail("a '(' in the WHERE clause is not closed");
            cur.skip(1);
            return clause;
        }
        return comparison(cur);
    }


    /**
     * {@code <field path> <op> <value>}.
     *
     * The field path is a WORD - the lexer keeps holder.name whole - and is
     * split on its dots here, where the question of what a dot means belongs.
     * It reaches into nested RECORDS only, the same restriction a binding
     * projection has; the runner refuses anything else with the type it hit.
     */
    private static Clause comparison(Cursor cur) {
        if (!cur.peek(Token.Kind.WORD)) {
            throw cur.fail("the WHERE clause needs a field name here: a comparison is"
                    + " <field> <op> <value>, and the field is on the left");
        }
        String strPath = cur.at(0).str();
        if (SET_KEYWORD.contains(strPath))
            throw cur.fail("'" + strPath + "' is a reserved word, and a comparison needs a field");
        if (strPath.indexOf(':') >= 0 || strPath.indexOf('-') >= 0
                || strPath.startsWith(".") || strPath.endsWith(".") || strPath.contains("..")) {
            throw cur.fail("'" + strPath + "' is not a field path; write a field name, dotted"
                    + " for a nested record - holder.name");
        }
        cur.skip(1);

        Clause.Op op = switch (cur.size() == 0 ? Token.Kind.WORD : cur.at(0).kind()) {
            case EQ -> Clause.Op.EQ;
            case LT -> Clause.Op.LT;
            case LE -> Clause.Op.LE;
            case GT -> Clause.Op.GT;
            case GE -> Clause.Op.GE;
            default -> throw cur.fail("'" + strPath + "' must be followed by one of = < <= > >=;"
                    + " there is no !=, write NOT " + strPath + " = <value> instead");
        };
        cur.skip(1);

        if (cur.size() == 0)
            throw cur.fail("'" + strPath + " " + op.strSymbol() + "' needs a value after it");

        Token tok = cur.at(0);
        JsonNode nodeValue;
        String strValue;
        switch (tok.kind()) {
            case VAR -> {
                // The same text a WITH payload would carry, so the same
                // coercer substitutes it, against the same declared type.
                nodeValue = TextNode.valueOf("$" + tok.str());
                strValue = "$" + tok.str();
            }
            case TEXT -> {
                nodeValue = TextNode.valueOf(tok.str());
                strValue = nodeValue.toString();
            }
            case WORD -> {
                nodeValue = scalar(cur, tok.str(), strPath);
                strValue = tok.str();
            }
            default -> throw cur.fail("'" + strPath + " " + op.strSymbol() + "' needs a value:"
                    + " quoted text, a number, true, false, null or a $binding");
        }
        cur.skip(1);

        return new Clause.Cmp(List.of(strPath.split("\\.")), op, nodeValue, strValue);
    }


    /**
     * A bare word in value position: a number, true, false or null. A name
     * is refused with the reason - the right side is never a field, because
     * comparing two fields is the first step towards an expression grammar.
     */
    private static JsonNode scalar(Cursor cur, String strWord, String strPath) {
        switch (strWord) {
            case "true":
                return BooleanNode.TRUE;
            case "false":
                return BooleanNode.FALSE;
            case "null":
                return NullNode.getInstance();
            default:
                break;
        }

        if (strWord.matches("-?[0-9]+")) {
            try {
                return LongNode.valueOf(Long.parseLong(strWord));
            }
            catch (NumberFormatException ex) {
                throw cur.fail("'" + strWord + "' is outside what an Int64 holds");
            }
        }
        if (strWord.matches("-?[0-9]+\\.[0-9]+"))
            return DecimalNode.valueOf(new BigDecimal(strWord));

        throw cur.fail("'" + strWord + "' is not a value; '" + strPath + "' can be compared"
                + " with quoted text, a number, true, false, null or a $binding, not with"
                + " another field");
    }


    private static List<CaqlRef> partyList(Cursor cur) {
        List<CaqlRef> lstParty = new ArrayList<>();
        lstParty.add(cur.valueRef("AS needs at least one party"));

        while (cur.peek(Token.Kind.COMMA)) {
            cur.skip(1);
            lstParty.add(cur.valueRef("a comma must be followed by another party"));
        }
        return List.copyOf(lstParty);
    }


    private static String bindingName(Cursor cur, String strName, Set<String> setBound) {
        if (SET_KEYWORD.contains(strName)) {
            throw cur.fail("'" + strName + "' is a reserved word and cannot be a binding name");
        }
        if (!Character.isLetter(strName.charAt(0)))
            throw cur.fail("a binding name must start with a letter, not '" + strName + "'");

        for (int cntLoop = 0; cntLoop < strName.length(); cntLoop++) {
            char ch = strName.charAt(cntLoop);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                throw cur.fail("'" + strName + "' is not a binding name: letters, digits and"
                        + " underscore only");
            }
        }

        // Sec. 6: refused, not shadowed, and refused with the whole script in
        // view rather than on reaching the second one at run time.
        if (!setBound.add(strName))
            throw cur.fail("'" + strName + "' is already bound; rebinding is refused");

        return strName;
    }


    /**
     * A cursor over one statement's tokens. Every failure it raises carries the
     * statement's line and source, so no call site has to remember to attach
     * them - which is the kind of thing that gets forgotten on the one path
     * nobody tested.
     */
    private static final class Cursor {

        private final Splitter.Logical logical;
        private final List<Token> lstToken;
        private int numPos;


        Cursor(Splitter.Logical logical, List<Token> lstToken) {
            this.logical = logical;
            this.lstToken = lstToken;
        }


        int numLine() {
            return logical.numLine();
        }


        String strSource() {
            return logical.strSource();
        }


        int size() {
            return lstToken.size() - numPos;
        }


        Token at(int numOffset) {
            return lstToken.get(numPos + numOffset);
        }


        void skip(int cntToken) {
            numPos += cntToken;
        }


        boolean peek(Token.Kind kind) {
            return size() > 0 && at(0).kind() == kind;
        }


        boolean peekWord(String strWord) {
            return size() > 0 && at(0).kind() == Token.Kind.WORD && strWord.equals(at(0).str());
        }


        String word(String strWhy) {
            if (!peek(Token.Kind.WORD))
                throw fail(strWhy);
            String str = at(0).str();
            skip(1);
            return str;
        }


        void expect(String strWord, String strWhy) {
            if (!peekWord(strWord))
                throw fail(strWhy);
            skip(1);
        }


        String text(String strWhy) {
            if (!peek(Token.Kind.TEXT))
                throw fail(strWhy);
            String str = at(0).str();
            skip(1);
            return str;
        }


        JsonNode json(String strWhy) {
            if (!peek(Token.Kind.JSON))
                throw fail(strWhy);
            JsonNode node = at(0).node();
            skip(1);
            return node;
        }


        /** A word used as a name: a template reference or a choice. */
        String reference(String strWhy) {
            return word(strWhy);
        }


        /** @return the tokens not yet consumed, for a prefix that wraps a statement */
        List<Token> rest() {
            return List.copyOf(lstToken.subList(numPos, lstToken.size()));
        }


        /**
         * A whole number. Digits lex as WORD, so this is a WORD that happens to
         * be all digits - and one that is not says so rather than parsing to
         * something surprising.
         */
        int count() {
            String str = word("COUNT needs a whole number");
            try {
                int cnt = Integer.parseInt(str);
                if (cnt < 0)
                    throw fail("a count cannot be negative");
                return cnt;
            }
            catch (NumberFormatException ex) {
                throw fail("'" + str + "' is not a whole number");
            }
        }


        /**
         * A binding or a literal, which is what a party and a contract id both
         * are. A party may also arrive BARE - see the class comment; a
         * contract id may not.
         */
        CaqlRef valueRef(String strWhy) {
            if (peek(Token.Kind.VAR)) {
                // The lexer hands over r.owner.party as one token; the split
                // is a question about bindings and belongs here.
                String str = at(0).str();
                skip(1);
                int numDot = str.indexOf('.');
                if (numDot < 0)
                    return new CaqlRef.Var(str);
                return new CaqlRef.Var(str.substring(0, numDot),
                        List.of(str.substring(numDot + 1).split("\\.")));
            }
            if (peek(Token.Kind.TEXT)) {
                String str = at(0).str();
                skip(1);
                return new CaqlRef.Literal(str);
            }

            // A bare word is a party when it carries `::`, and only then.
            // `Lexer.isWordPart` already keeps a whole party id in one token,
            // hyphens and colons included, so nothing in the lexer moves.
            if (peek(Token.Kind.WORD) && at(0).str().contains("::")) {
                String str = at(0).str();
                skip(1);
                return new CaqlRef.Literal(str);
            }

            throw fail(strWhy);
        }


        void end() {
            if (size() == 0)
                return;

            // A leftover that could begin a statement is a missing ';', not
            // junk, and saying so is the difference between a one-character
            // fix and a hunt.
            if (at(0).kind() == Token.Kind.WORD
                    && (SET_START.contains(at(0).str())
                            || (size() > 1 && at(1).kind() == Token.Kind.EQ))) {
                // The ';' belongs at the end of the last line of the statement
                // BEFORE this token: back over the joining whitespace to its
                // last character and ask the splitter which line that was.
                int idx = at(0).numOffset() - 1;
                while (idx > 0 && Character.isWhitespace(strSource().charAt(idx))) {
                    idx--;
                }
                throw new CaqlException(numLine(), strSource(), "'" + at(0).str()
                        + "' begins another statement, so the one before it has no ';' after"
                        + " it; every CaQL statement ends with one", logical.lineAt(idx));
            }
            throw fail("unexpected '" + at(0).str() + "' after the end of the statement");
        }


        CaqlException fail(String strWhy) {
            return new CaqlException(logical.numLine(), logical.strSource(), strWhy);
        }

    }

}
