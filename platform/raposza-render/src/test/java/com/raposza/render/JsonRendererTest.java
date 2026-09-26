// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Rendering, checked by parsing the output back rather than by comparing
 * strings. A string comparison would break on whitespace and pass on a value
 * silently changed to a double, which is the wrong way round.
 *
 * Author Claude/bentzn
 */
class JsonRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DataId ID_ACCOUNT = new DataId("pkg1", "Main", "Account");

    private final JsonRenderer renderer = new JsonRenderer();


    private JsonNode parse(String strJson) throws Exception {
        return MAPPER.readTree(strJson);
    }


    private JsonNode render(DamlValue value) throws Exception {
        return parse(renderer.value(value, Optional.empty()));
    }


    private static DamlValue.Rec rec(DamlValue.Rec.Field... arrField) {
        return new DamlValue.Rec(ID_ACCOUNT, List.of(arrField));
    }


    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }


    /**
     * The one that matters most: a balance must survive rendering. As a JSON
     * number it would be read back as a double and lose its tail.
     */
    @Test
    void numericIsAStringAndKeepsItsScale() throws Exception {
        JsonNode node = render(new DamlValue.Decimal(new BigDecimal("1250.0000000000")));
        assertTrue(node.isTextual(), "Numeric rendered as a JSON number: " + node);
        assertEquals("1250.0000000000", node.asText());
    }


    @Test
    void largeNumericIsNotRoundedThroughADouble() throws Exception {
        String strNum = "12345678901234567890.1234567890123456";
        assertEquals(strNum, render(new DamlValue.Decimal(new BigDecimal(strNum))).asText());
    }


    @Test
    void int64IsAStringBecauseJsonNumbersAreDoubles() throws Exception {
        JsonNode node = render(new DamlValue.Int64(9007199254740993L));
        assertTrue(node.isTextual());
        assertEquals("9007199254740993", node.asText());
    }


    @Test
    void scalarsRender() throws Exception {
        assertTrue(render(new DamlValue.Bool(true)).asBoolean());
        assertEquals("primary", render(new DamlValue.Text("primary")).asText());
        assertEquals("alice::1220ab", render(new DamlValue.Party("alice::1220ab")).asText());
        assertEquals("00cafe", render(new DamlValue.ContractRef("00cafe")).asText());
        assertEquals("2026-07-31", render(new DamlValue.DateVal(LocalDate.of(2026, 7, 31)))
                .asText());
        assertTrue(render(new DamlValue.Unit()).isObject());
        assertEquals(0, render(new DamlValue.Unit()).size());
    }


    @Test
    void timestampKeepsMicrosecondPrecisionInUtc() throws Exception {
        Instant inst = Instant.ofEpochSecond(1_785_486_666L, 581_794_000L);
        assertEquals("2026-07-31T08:31:06.581794Z", render(new DamlValue.TimeVal(inst)).asText());
    }


    @Test
    void labelledRecordBecomesAnObject() throws Exception {
        JsonNode node = render(rec(fld("label", new DamlValue.Text("primary")),
                fld("balance", new DamlValue.Decimal(new BigDecimal("10.50")))));

        assertTrue(node.isObject());
        assertEquals("primary", node.get("label").asText());
        assertEquals("10.50", node.get("balance").asText());
    }


    /**
     * A read without verbose returns no labels. Inventing keys would present
     * positional data as named data, so it renders as an array instead - the
     * shape says plainly that the names are not known.
     */
    @Test
    void unlabelledRecordBecomesAnArrayNotAnObjectWithInventedKeys() throws Exception {
        JsonNode node = render(new DamlValue.Rec(null,
                List.of(fld("", new DamlValue.Text("a")), fld("", new DamlValue.Text("b")))));

        assertTrue(node.isArray(), "unlabelled record rendered as " + node.getNodeType());
        assertEquals(2, node.size());
        assertEquals("b", node.get(1).asText());
    }


    @Test
    void nestedRecordNests() throws Exception {
        JsonNode node = render(rec(fld("address",
                new DamlValue.Rec(new DataId("pkg1", "Main", "Address"),
                        List.of(fld("city", new DamlValue.Text("Lisboa")))))));

        assertEquals("Lisboa", node.get("address").get("city").asText());
    }


    @Test
    void noneIsNullAndSomeIsTheBareValue() throws Exception {
        assertTrue(render(new DamlValue.Opt(null)).isNull());
        assertEquals("day-to-day", render(new DamlValue.Opt(new DamlValue.Text("day-to-day")))
                .asText());
    }


    @Test
    void listBecomesAnArray() throws Exception {
        JsonNode node = render(new DamlValue.Lst(
                List.of(new DamlValue.Text("eur"), new DamlValue.Text("retail"))));
        assertEquals(2, node.size());
        assertEquals("eur", node.get(0).asText());
    }


    @Test
    void enumIsItsConstructorName() throws Exception {
        assertEquals("Gold", render(new DamlValue.EnumVal(ID_ACCOUNT, "Gold")).asText());
    }


    @Test
    void variantCarriesTagAndValue() throws Exception {
        JsonNode node = render(
                new DamlValue.Variant(ID_ACCOUNT, "Circle", new DamlValue.Text("r")));
        assertEquals("Circle", node.get("tag").asText());
        assertEquals("r", node.get("value").asText());
    }


    @Test
    void textMapBecomesAnObject() throws Exception {
        JsonNode node = render(new DamlValue.TextMap(
                List.of(new DamlValue.TextMap.Entry("eur", new DamlValue.Text("euro")))));
        assertEquals("euro", node.get("eur").asText());
    }


    /**
     * Generic map keys are values, not strings, so an object cannot hold them
     * without stringifying a key and losing its type.
     */
    @Test
    void genMapBecomesPairsNotAnObject() throws Exception {
        JsonNode node = render(new DamlValue.GenMap(List.of(
                new DamlValue.GenMap.Entry(new DamlValue.Int64(1), new DamlValue.Text("one")))));

        assertTrue(node.isArray());
        assertEquals("1", node.get(0).get(0).asText());
        assertEquals("one", node.get(0).get(1).asText());
    }


    @Test
    void textIsEscapedRatherThanBreakingTheDocument() throws Exception {
        String strAwkward = "quote \" backslash \\ newline \n tab \t emoji \uD83D\uDE00";
        assertEquals(strAwkward, render(new DamlValue.Text(strAwkward)).asText());
    }


    @Test
    void contractRenders() throws Exception {
        Contract contract = new Contract("00abc", "#tx-9:0", ID_ACCOUNT,
                rec(fld("label", new DamlValue.Text("primary"))), List.of("bank"),
                List.of("alice"), Optional.empty(), "", Optional.empty());

        JsonNode node = parse(renderer.contract(contract));

        assertEquals("00abc", node.get("contractId").asText());
        assertEquals("#tx-9:0", node.get("eventId").asText());
        assertEquals("pkg1:Main:Account", node.get("templateId").asText());
        assertEquals("primary", node.get("payload").get("label").asText());
        assertEquals("bank", node.get("signatories").get(0).asText());
        assertTrue(node.get("key").isNull());
        assertTrue(node.get("active").asBoolean());
    }


    /**
     * Consequences nest inside the exercise that caused them, so an archive and
     * its cause stay adjacent - design sec. 11.2.
     */
    @Test
    void treeNestsConsequencesInsideTheirExercise() throws Exception {
        TxNode.Created created = new TxNode.Created("#tx:1", "00new", ID_ACCOUNT,
                rec(fld("label", new DamlValue.Text("after"))), List.of("bank"), List.of());

        TxNode.Exercised exercised = new TxNode.Exercised("#tx:0", "00old", ID_ACCOUNT, "Deposit",
                true, rec(fld("amount", new DamlValue.Decimal(new BigDecimal("100.00")))), null,
                List.of("alice"), List.of(created));

        TxTree tree = new TxTree("tx-1", Optional.of("cmd-1"), Optional.empty(), "0000007",
                Instant.ofEpochSecond(1_785_486_666L, 581_794_000L), List.of(exercised));

        JsonNode node = parse(renderer.tree(tree));

        assertEquals("tx-1", node.get("updateId").asText());
        assertEquals("cmd-1", node.get("commandId").asText());
        assertTrue(node.get("workflowId").isNull());
        assertEquals("2026-07-31T08:31:06.581794Z", node.get("effectiveAt").asText());

        JsonNode root = node.get("roots").get(0);
        assertEquals("exercised", root.get("kind").asText());
        assertEquals("Deposit", root.get("choice").asText());
        assertTrue(root.get("consuming").asBoolean());
        assertEquals("100.00", root.get("argument").get("amount").asText());

        // Absent result stays absent. Rendering it as {} would claim the choice
        // returned unit, which is a different fact.
        assertTrue(root.get("result").isNull());

        JsonNode child = root.get("children").get(0);
        assertEquals("created", child.get("kind").asText());
        assertEquals("00new", child.get("contractId").asText());
        assertEquals("after", child.get("payload").get("label").asText());
    }


    @Test
    void compactModeEmitsOneLine() {
        String strJson = new JsonRenderer(false)
                .value(new DamlValue.Text("primary"), Optional.empty());
        assertFalse(strJson.contains("\n"));
    }

}
