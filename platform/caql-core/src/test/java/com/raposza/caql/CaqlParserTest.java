// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The parser, against the grammar of `dql-design.md` sec. 4.
 *
 * The first test is the example script from that document, verbatim. It is the
 * whole language in one page, so a parser that reads it and nothing else is
 * still a parser worth having - and a change that breaks it breaks the document
 * rather than a test someone wrote.
 *
 * Author Claude/bentzn
 */
class CaqlParserTest {

    /** Verbatim from dql-design.md sec. 4. */
    private static final String SCRIPT_EXAMPLE = """
            alice = ALLOCATE PARTY "Alice";
            bank  = ALLOCATE PARTY "Bank";

            CREATE USER "alice-app" WITH {
              "primaryParty": "$alice",
              "rights": [ { "canActAs": "$alice" },
                          { "canReadAs": "$bank"  } ]
            };

            acct = AS $bank CREATE Main:Account WITH {
              "owner": "$alice",
              "bank": "$bank",
              "balance": "0.0",
              "label": "fixture-1"
            };

            AS $bank EXERCISE ON $acct Deposit WITH { "amount": "10.0" };
            AS $alice QUERY Main:Account;
            """;


    @Test
    void theDocumentsOwnExampleParses() {
        List<Stmt> lstStmt = CaqlParser.parse(SCRIPT_EXAMPLE);
        assertEquals(6, lstStmt.size());

        Stmt.Allocate alloc = assertInstanceOf(Stmt.Allocate.class, lstStmt.get(0));
        assertEquals("alice", alloc.nameBind().orElseThrow());
        assertEquals("Alice", alloc.hintParty());
        assertEquals(1, alloc.numLine());

        Stmt.CreateUser user = assertInstanceOf(Stmt.CreateUser.class, lstStmt.get(2));
        assertEquals("alice-app", user.idUser());
        assertEquals("$alice", user.nodeWith().get("primaryParty").asText());
        assertEquals(2, user.nodeWith().get("rights").size());
        assertEquals(4, user.numLine());

        Stmt.Create create = assertInstanceOf(Stmt.Create.class, lstStmt.get(3));
        assertEquals("acct", create.nameBind().orElseThrow());
        assertEquals("Main:Account", create.strTemplate());
        assertEquals(List.of(new CaqlRef.Var("bank")), create.lstParty());
        assertEquals("fixture-1", create.nodeWith().get("label").asText());

        Stmt.Exercise ex = assertInstanceOf(Stmt.Exercise.class, lstStmt.get(4));
        assertTrue(ex.nameBind().isEmpty());
        assertEquals("Deposit", ex.nameChoice());
        assertEquals(new CaqlRef.Var("acct"), ex.refContract());
        assertEquals("10.0", ex.nodeWith().orElseThrow().get("amount").asText());

        Stmt.Query query = assertInstanceOf(Stmt.Query.class, lstStmt.get(5));
        assertEquals("Main:Account", query.strTemplate());
        assertTrue(query.clauseWhere().isEmpty());
    }


    /** The line reported is where the STATEMENT starts, not where it ended. */
    @Test
    void aMultiLineStatementReportsItsFirstLine() {
        List<Stmt> lstStmt = CaqlParser.parse(SCRIPT_EXAMPLE);
        assertEquals(10, lstStmt.get(3).numLine());
        assertEquals(17, lstStmt.get(4).numLine());
    }


    @Test
    void commentsAreStrippedAndDoNotShiftLineNumbers() {
        List<Stmt> lstStmt = CaqlParser.parse("""
                -- a comment
                a = ALLOCATE PARTY "A";   -- trailing
                -- another
                b = ALLOCATE PARTY "B";
                """);

        assertEquals(2, lstStmt.size());
        assertEquals(2, lstStmt.get(0).numLine());
        assertEquals(4, lstStmt.get(1).numLine());
        assertFalse(lstStmt.get(0).strSource().contains("trailing"));
    }


    /** The three ways a string literal hides syntax, in one place. */
    @Test
    void syntaxInsideAStringIsNotSyntax() {
        List<Stmt> lstStmt = CaqlParser.parse("""
                a = ALLOCATE PARTY "not -- a comment";
                AS $a CREATE M:T WITH { "brace": "}", "quote": "a\\"b" };
                """);

        assertEquals(2, lstStmt.size());
        assertEquals("not -- a comment",
                assertInstanceOf(Stmt.Allocate.class, lstStmt.get(0)).hintParty());

        Stmt.Create create = assertInstanceOf(Stmt.Create.class, lstStmt.get(1));
        assertEquals("}", create.nodeWith().get("brace").asText());
        assertEquals("a\"b", create.nodeWith().get("quote").asText());
    }


    /** Jackson decodes a text literal, so it means what a WITH string means. */
    @Test
    void aTextLiteralIsDecodedTheSameWayAJsonStringIs() {
        Stmt.Allocate alloc = assertInstanceOf(Stmt.Allocate.class,
                CaqlParser.parse("ALLOCATE PARTY \"a\\u00e7\\u4e2d\\ttab\";").get(0));

        assertEquals("a\u00e7\u4e2d\ttab", alloc.hintParty());
    }


    @Test
    void aPartyListTakesBindingsAndLiteralsTogether() {
        Stmt.Create create = assertInstanceOf(Stmt.Create.class, CaqlParser.parse(
                "AS $a, \"Bob::1220ab\", $c CREATE M:T WITH {};").get(0));

        assertEquals(List.of(new CaqlRef.Var("a"), new CaqlRef.Literal("Bob::1220ab"),
                new CaqlRef.Var("c")), create.lstParty());
    }


    @Test
    void aPartyMayBeWrittenWithoutQuotes() {
        Stmt.Create create = assertInstanceOf(Stmt.Create.class, CaqlParser.parse(
                "AS Bob-1::1220ab, $c CREATE M:T WITH {};").get(0));

        assertEquals(List.of(new CaqlRef.Literal("Bob-1::1220ab"), new CaqlRef.Var("c")),
                create.lstParty());
    }


    /** Without the separator a keyword would be read as a party. */
    @Test
    void aBareWordWithoutTheSeparatorIsNotAParty() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS QUERY M:T;"));
        assertTrue(ex.getMessage().contains("party"), ex.getMessage());
    }


    /** A contract id keeps its quotes: FETCH tells its two forms apart by kind. */
    @Test
    void aBareContractIdIsStillReadAsATemplateReference() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a FETCH 00b3cafe;"));
        assertTrue(ex.getMessage().contains("SINGLE"), ex.getMessage());
    }


    /**
     * P-1, his decision of 2026-09-25: WITH on FETCH ... SINGLE narrowed
     * nothing, so it is refused by name and the refusal names WHERE.
     */
    @Test
    void withOnFetchSingleIsRefusedByName() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a FETCH M:T WITH {\"x\": 1} SINGLE;"));
        assertTrue(ex.getMessage().contains("WITH on FETCH ... SINGLE"), ex.getMessage());
        assertTrue(ex.getMessage().contains("WHERE"), ex.getMessage());

        Stmt.FetchSingle stmt = assertInstanceOf(Stmt.FetchSingle.class,
                CaqlParser.parse("a = AS $p FETCH M:T SINGLE;").get(0));
        assertEquals("M:T", stmt.strTemplate());
    }


    @Test
    void aPackageQualifiedTemplateReferenceSurvivesWhole() {
        Stmt.Query query = assertInstanceOf(Stmt.Query.class,
                CaqlParser.parse("AS $a QUERY abc123:Iou.Main:Iou;").get(0));

        assertEquals("abc123:Iou.Main:Iou", query.strTemplate());
    }


    @Test
    void withIsOptionalOnExerciseAndRequiredOnCreate() {
        assertTrue(assertInstanceOf(Stmt.Exercise.class,
                CaqlParser.parse("AS $a EXERCISE ON $c Archive;").get(0)).nodeWith().isEmpty());

        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a CREATE M:T;"));
        assertTrue(ex.getMessage().contains("WITH"), ex.getMessage());
    }


    // ------------------------------------------------------------ WHERE

    /** WITH on a QUERY narrowed nothing for long enough that the refusal names WHERE. */
    @Test
    void withOnAQueryIsRefusedNamingWhere() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WITH { \"x\": 1 };"));
        assertTrue(ex.getMessage().contains("WHERE"), ex.getMessage());

        CaqlException exAssert = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("ASSERT AS $a QUERY M:T WITH { \"x\": 1 } COUNT 1;"));
        assertTrue(exAssert.getMessage().contains("WHERE"), exAssert.getMessage());
    }


    @Test
    void aComparisonKeepsItsValueAsJsonAndItsPathSplit() {
        Stmt.Query query = assertInstanceOf(Stmt.Query.class,
                CaqlParser.parse("AS $a QUERY M:T WHERE holder.name = \"acme\";").get(0));
        Clause.Cmp cmp = assertInstanceOf(Clause.Cmp.class, query.clauseWhere().orElseThrow());

        assertEquals(List.of("holder", "name"), cmp.lstField());
        assertEquals(Clause.Op.EQ, cmp.op());
        assertTrue(cmp.nodeValue().isTextual());
        assertEquals("acme", cmp.nodeValue().textValue());
        assertEquals("holder.name = \"acme\"", cmp.str());
    }


    @Test
    void everyValueFormParses() {
        assertEquals("weight > 10.0", where("weight > 10.0").str());
        assertEquals("cases >= 3", where("cases >= 3").str());
        assertEquals("delta < -2", where("delta < -2").str());
        assertEquals("open = true", where("open = true").str());
        assertEquals("note = null", where("note = null").str());
        assertEquals("owner = $plant", where("owner = $plant").str());
        assertEquals("made <= \"2026-01-01\"", where("made <= \"2026-01-01\"").str());

        Clause.Cmp cmpNum = assertInstanceOf(Clause.Cmp.class, where("weight > 10.0"));
        assertTrue(cmpNum.nodeValue().isNumber());
        assertEquals("10.0", cmpNum.nodeValue().decimalValue().toPlainString());

        Clause.Cmp cmpInt = assertInstanceOf(Clause.Cmp.class, where("cases >= 3"));
        assertTrue(cmpInt.nodeValue().isIntegralNumber());

        // A binding is carried as the text a WITH payload would carry, so the
        // same coercer substitutes it.
        Clause.Cmp cmpVar = assertInstanceOf(Clause.Cmp.class, where("owner = $plant"));
        assertEquals("$plant", cmpVar.nodeValue().textValue());
    }


    /** NOT binds tightest, then AND, then OR. Parentheses regroup. */
    @Test
    void precedenceIsNotThenAndThenOr() {
        Clause clause = where("a = 1 AND b = 2 OR NOT c = 3");
        Clause.Or or = assertInstanceOf(Clause.Or.class, clause);
        assertInstanceOf(Clause.And.class, or.left());
        assertInstanceOf(Clause.Not.class, or.right());
        assertEquals("a = 1 AND b = 2 OR NOT c = 3", clause.str());

        Clause grouped = where("NOT (open = true OR note = null)");
        Clause.Not not = assertInstanceOf(Clause.Not.class, grouped);
        assertInstanceOf(Clause.Or.class, not.inner());
        assertEquals("NOT (open = true OR note = null)", grouped.str());

        Clause regrouped = where("a = 1 AND (b = 2 OR c = 3)");
        Clause.And and = assertInstanceOf(Clause.And.class, regrouped);
        assertInstanceOf(Clause.Or.class, and.right());
        assertEquals("a = 1 AND (b = 2 OR c = 3)", regrouped.str());
    }


    @Test
    void whereOnAnAssertIsCarriedOnItsQuery() {
        Stmt.Assert assertion = assertInstanceOf(Stmt.Assert.class,
                CaqlParser.parse("ASSERT AS $a QUERY M:T WHERE cases >= 3 COUNT 2;").get(0));
        Stmt.Query query = assertInstanceOf(Stmt.Query.class, assertion.stmtRead());

        assertEquals("cases >= 3", query.clauseWhere().orElseThrow().str());
        assertEquals(2, assertion.cntExpected());
    }


    @Test
    void thereIsNoNotEqualsAndTheRefusalSaysWhatToWrite() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE x != 1;"));
        assertTrue(ex.getMessage().contains("no !=") && ex.getMessage().contains("NOT "),
                ex.getMessage());
    }


    @Test
    void theRightSideIsNeverAField() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE x = y;"));
        assertTrue(ex.getMessage().contains("another field"), ex.getMessage());
    }


    @Test
    void whereRefusesTheShapesItDoesNotHave() {
        assertTrue(assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE;")).getMessage()
                .contains("clause"));
        assertTrue(assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE (x = 1;")).getMessage()
                .contains("not closed"));
        assertTrue(assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE x =;")).getMessage()
                .contains("value"));
        assertTrue(assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE AND = 1;")).getMessage()
                .contains("reserved"));
        assertTrue(assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T WHERE M:T = 1;")).getMessage()
                .contains("field path"));
    }


    /** The reserved words are matched in capitals, so a field called `and` is a field. */
    @Test
    void aLowerCaseAndIsAField() {
        assertEquals("and = 1", where("and = 1").str());
    }


    private static Clause where(String strClause) {
        return assertInstanceOf(Stmt.Query.class,
                CaqlParser.parse("AS $a QUERY M:T WHERE " + strClause + ";").get(0))
                .clauseWhere().orElseThrow();
    }


    /** Sec. 4: both are syntax errors, and for different reasons. */
    @Test
    void queryAndCreateUserRefuseToBind() {
        CaqlException exQuery = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("x = AS $a QUERY M:T;"));
        assertTrue(exQuery.getMessage().contains("QUERY does not bind"), exQuery.getMessage());

        CaqlException exUser = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("x = CREATE USER \"u\" WITH {};"));
        assertTrue(exUser.getMessage().contains("does not bind"), exUser.getMessage());
    }


    /**
     * Sec. 6: refused, not shadowed - and refused before anything runs, which
     * is the difference between a typo and a half-built fixture.
     */
    @Test
    void rebindingIsRefusedWithTheWholeScriptInView() {
        CaqlException ex = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                a = ALLOCATE PARTY "A";
                a = ALLOCATE PARTY "B";
                """));

        assertEquals(2, ex.numLine());
        assertTrue(ex.getMessage().contains("already bound"), ex.getMessage());
    }


    @Test
    void aReservedWordCannotBeABindingName() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS = ALLOCATE PARTY \"A\";"));
        assertTrue(ex.getMessage().contains("reserved"), ex.getMessage());

        // Lower case is not reserved, so this is a legal binding.
        assertEquals(1, CaqlParser.parse("as = ALLOCATE PARTY \"A\";").size());
    }


    @Test
    void aBindingNameMustStartWithALetter() {
        assertThrows(CaqlException.class, () -> CaqlParser.parse("1a = ALLOCATE PARTY \"A\";"));
    }


    /**
     * A CREATE without AS has no submitter, so that is the error worth naming -
     * "USER expected" would send the reader looking for a typo they did not
     * make.
     */
    @Test
    void aCreateWithoutAsSaysWhatIsActuallyMissing() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("CREATE M:T WITH {};"));
        assertTrue(ex.getMessage().contains("AS"), ex.getMessage());
    }


    @Test
    void anUnterminatedTextLiteralFailsOnItsOwnLine() {
        CaqlException ex = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                a = ALLOCATE PARTY "A";
                b = ALLOCATE PARTY "unclosed
                """));
        assertEquals(2, ex.numLine());
    }


    @Test
    void anUnclosedJsonObjectFailsRatherThanSwallowingTheRest() {
        CaqlException ex = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                AS $a CREATE M:T WITH {
                  "x": "y"
                """));
        assertTrue(ex.getMessage().contains("not closed"), ex.getMessage());
    }


    @Test
    void malformedJsonFailsAtParseTimeWithItsLine() {
        CaqlException ex = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                a = ALLOCATE PARTY "A";
                AS $a CREATE M:T WITH { "x": };
                """));
        assertEquals(2, ex.numLine());
    }


    @Test
    void trailingJunkIsRefused() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T extra;"));
        assertTrue(ex.getMessage().contains("after the end"), ex.getMessage());
    }


    @Test
    void anUnknownCommandNamesWhatWasExpected() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a DELETE M:T;"));
        assertTrue(ex.getMessage().contains("QUERY"), ex.getMessage());
    }


    @Test
    void anEmptyScriptIsAnEmptyListRatherThanAFailure() {
        assertTrue(CaqlParser.parse("").isEmpty());
        assertTrue(CaqlParser.parse("\n\n-- only a comment\n\n").isEmpty());
    }


    // ------------------------------------------------- the terminator, 2026-09-14

    /** No end-of-file exemption. Operator decision: without exception. */
    @Test
    void theLastStatementNeedsItsOwnTerminatorToo() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY M:T"));
        assertTrue(ex.getMessage().contains("';'"), ex.getMessage());
    }


    /**
     * A newline is whitespace now, so the layout is free. The reported line is
     * still the first line the statement has content on.
     */
    @Test
    void aStatementMaySpanAsManyLinesAsItLikes() {
        List<Stmt> lstStmt = CaqlParser.parse("""
                AS $a
                    QUERY
                    M:T;
                """);

        assertEquals(1, lstStmt.size());
        assertEquals(1, lstStmt.get(0).numLine());
    }


    @Test
    void twoStatementsMayShareOneLine() {
        assertEquals(2, CaqlParser.parse("AS $a QUERY M:T; AS $b QUERY M:T;").size());
    }


    /**
     * The failure a missing terminator actually produces: the next statement
     * joins this one, and the message has to name the cause rather than the
     * symptom.
     */
    @Test
    void aForgottenTerminatorNamesItselfRatherThanTheNextKeyword() {
        CaqlException ex = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                AS $a QUERY M:T
                AS $b QUERY M:T;
                """));
        assertTrue(ex.getMessage().contains("no ';'"), ex.getMessage());
    }


    /** Inside a literal it is a character, not syntax. */
    @Test
    void aSemicolonInsideAValueIsNotATerminator() {
        List<Stmt> lstStmt = CaqlParser.parse("""
                ALLOCATE PARTY "a;b";
                AS $a CREATE M:T WITH { "label": "x;y" };
                """);

        assertEquals(2, lstStmt.size());
        assertEquals("a;b", assertInstanceOf(Stmt.Allocate.class, lstStmt.get(0)).hintParty());
    }


    /** A terminator with nothing in front of it is a typo, so it is refused. */
    @Test
    void aSemicolonWithNoStatementInFrontOfItIsRefused() {
        assertThrows(CaqlException.class, () -> CaqlParser.parse("AS $a QUERY M:T;;"));
    }


    /**
     * THE TERMINATOR IS CONSUMED. The statement id is the hash of the
     * normalised source, so a ';' that reached strSource would move every id
     * in the corpus at once.
     */
    @Test
    void theTerminatorDoesNotReachTheRecordedSource() {
        Stmt stmt = CaqlParser.parse("AS $a QUERY M:T;").get(0);
        assertEquals("AS $a QUERY M:T", stmt.strSource());
    }


    // ------------------------------------------------- spanAt, for Ctrl-Enter

    /** The caret anywhere inside a multi-line statement cuts the whole of it. */
    @Test
    void spanAtCutsAWholeMultiLineStatementFromAnyOfItsLines() {
        String strScript = """
                AS $a QUERY M:T;
                AS $b CREATE M:T WITH {
                  "x": "y"
                };
                AS $c QUERY M:T;
                """;
        String strWhole = "AS $b CREATE M:T WITH {\n  \"x\": \"y\"\n};";
        int idxMid = strScript.indexOf("\"x\"");

        int[] arrSpan = CaqlParser.spanAt(strScript, idxMid);
        assertEquals(strWhole, strScript.substring(arrSpan[0], arrSpan[1]));
    }


    /** The terminator is part of the span, so what is cut parses on its own. */
    @Test
    void spanAtCarriesTheTerminatorSoTheCutTextParses() {
        String strScript = "AS $a QUERY M:T;\n";
        int[] arrSpan = CaqlParser.spanAt(strScript, 3);

        assertEquals("AS $a QUERY M:T;", strScript.substring(arrSpan[0], arrSpan[1]));
        assertEquals(1, CaqlParser.parse(strScript.substring(arrSpan[0], arrSpan[1])).size());
    }


    @Test
    void spanAtAnswersNothingForACaretBetweenStatements() {
        String strScript = "AS $a QUERY M:T;\n\n-- a comment\nAS $b QUERY M:T;\n";
        assertNull(CaqlParser.spanAt(strScript, strScript.indexOf("a comment")));
    }


    /**
     * A missing ';' names the line its fix goes at the END of - the last line
     * of the statement that lacks it, not the first, and not the line of the
     * statement that follows.
     */
    @Test
    void aMissingTerminatorNamesTheLineItBelongsAtTheEndOf() {
        CaqlException exTwo = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                AS $a QUERY M:T
                AS $b QUERY M:T;
                """));
        assertEquals(1, exTwo.numLine());
        assertEquals(1, exTwo.numLineFix());

        CaqlException exTail = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                AS $a QUERY M:T;

                AS $b QUERY M:T
                """));
        assertEquals(3, exTail.numLine());
        assertEquals(3, exTail.numLineFix());

        CaqlException exJson = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                a = AS $p CREATE M:T WITH {
                  "x": 1,
                  "y": 2
                }
                -- a comment in between
                AS $b QUERY M:T;
                """));
        assertEquals(1, exJson.numLine());
        assertEquals(4, exJson.numLineFix());

        CaqlException exJsonTail = assertThrows(CaqlException.class, () -> CaqlParser.parse("""
                AS $b QUERY M:T;
                a = AS $p CREATE M:T WITH {
                  "x": 1
                }
                """));
        assertEquals(2, exJsonTail.numLine());
        assertEquals(4, exJsonTail.numLineFix());

        // Any other refusal has no fix line.
        CaqlException exOther = assertThrows(CaqlException.class,
                () -> CaqlParser.parse("AS $a QUERY;"));
        assertEquals(0, exOther.numLineFix());
    }


    /** An unterminated tail is still the caret's statement; the refusal names it. */
    @Test
    void spanAtCutsAnUnterminatedTailSoTheRefusalIsTheOneAboutTheTerminator() {
        String strScript = "AS $a QUERY M:T;\nAS $b QUERY M:T";
        int[] arrSpan = CaqlParser.spanAt(strScript, strScript.length());

        assertEquals("AS $b QUERY M:T", strScript.substring(arrSpan[0], arrSpan[1]));
    }


    /** The source is what the transcript records. */
    @Test
    void eachStatementCarriesItsOwnSource() {
        List<Stmt> lstStmt = CaqlParser.parse(SCRIPT_EXAMPLE);
        assertTrue(lstStmt.get(0).strSource().startsWith("alice = ALLOCATE PARTY"));
        assertTrue(lstStmt.get(3).strSource().contains("fixture-1"),
                lstStmt.get(3).strSource());
    }

}
