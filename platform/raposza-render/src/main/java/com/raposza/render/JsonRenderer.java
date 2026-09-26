// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;
import com.raposza.api.render.ValueRenderer_i;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/**
 * Renders the semantic model as JSON.
 *
 * The encoding follows the Daml JSON API's conventions rather than inventing
 * one: Int64 and Numeric as STRINGS, Optional as null or the bare value, a
 * variant as {"tag","value"}, an enum as its constructor name. Those choices
 * exist because JSON numbers are doubles in most readers and a contract balance
 * that loses its last digits on the way to the screen is worse than useless in
 * a diagnostic tool. Following the ecosystem's convention also means what this
 * prints can be pasted back into things that expect it.
 *
 * Values are rendered EXACTLY as the ledger sent them - a Numeric keeps its
 * trailing zeros, a Timestamp keeps microsecond precision in UTC. A trimmed
 * value is one the operator cannot paste back.
 *
 * The type argument is ignored. Verbose reads carry field labels, so a payload
 * renders correctly with no type metadata at all; the parameter is honoured in
 * the signature so that P3 can improve the output without changing callers.
 *
 * Escaping and encoding are Jackson's, not hand-rolled: quoting and unicode in
 * ledger text is commodity work with a long tail, and this module already
 * depends on Jackson.
 *
 * Author Claude/bentzn
 */
public final class JsonRenderer implements ValueRenderer_i<String> {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean flagPretty;


    /** Pretty-printed. */
    public JsonRenderer() {
        this(true);
    }


    /** @param flagPretty true to indent, false for one line */
    public JsonRenderer(boolean flagPretty) {
        this.flagPretty = flagPretty;
    }


    @Override
    public String value(DamlValue value, Optional<DamlType> type) {
        return write(node(value));
    }


    @Override
    public String contract(Contract contract) {
        return write(nodeContract(contract));
    }


    @Override
    public String tree(TxTree tree) {
        ObjectNode obj = FACTORY.objectNode();
        obj.put("updateId", tree.idUpdate());
        obj.put("commandId", tree.idCommand().orElse(null));
        obj.put("workflowId", tree.idWorkflow().orElse(null));
        obj.put("offset", tree.offset());
        obj.put("effectiveAt", tree.instEffective().toString());

        ArrayNode arr = obj.putArray("roots");
        for (TxNode node : tree.lstRoot()) {
            arr.add(nodeTx(node));
        }
        return write(obj);
    }


    /**
     * @param value the value
     * @return the value as a JSON tree, for callers embedding it in their own
     */
    public JsonNode node(DamlValue value) {
        if (value == null)
            return FACTORY.nullNode();

        return switch (value) {
            // The Daml JSON API encodes unit as an empty object; nothing else
            // distinguishes "() was here" from "nothing was here".
            case DamlValue.Unit ignored -> FACTORY.objectNode();

            case DamlValue.Bool val -> FACTORY.booleanNode(val.flag());

            // String, not number. A JSON number is a double to most readers and
            // Int64 outruns one past 2^53.
            case DamlValue.Int64 val -> FACTORY.textNode(Long.toString(val.num()));

            // toPlainString, not toString: scientific notation is not what the
            // ledger sent and not what pastes back.
            case DamlValue.Decimal val -> FACTORY.textNode(val.num().toPlainString());

            case DamlValue.Text val -> FACTORY.textNode(val.str());
            case DamlValue.TimeVal val -> FACTORY.textNode(val.inst().toString());
            case DamlValue.DateVal val -> FACTORY.textNode(val.date().toString());
            case DamlValue.Party val -> FACTORY.textNode(val.idParty());
            case DamlValue.ContractRef val -> FACTORY.textNode(val.idContract());

            case DamlValue.Rec val -> nodeRecord(val);

            case DamlValue.Variant val -> {
                ObjectNode obj = FACTORY.objectNode();
                obj.put("tag", val.nameCtor());
                obj.set("value", node(val.value()));
                yield obj;
            }

            case DamlValue.EnumVal val -> FACTORY.textNode(val.nameCtor());

            case DamlValue.Lst val -> {
                ArrayNode arr = FACTORY.arrayNode();
                for (DamlValue elem : val.lstElem()) {
                    arr.add(node(elem));
                }
                yield arr;
            }

            // None is null, Some is the bare value. Ambiguous for a nested
            // Optional, which the Daml JSON API handles with an array form;
            // not implemented here because nothing in P1 produces one and a
            // half-done encoding is worse than an absent one.
            case DamlValue.Opt val -> val.value() == null ? FACTORY.nullNode() : node(val.value());

            case DamlValue.TextMap val -> {
                ObjectNode obj = FACTORY.objectNode();
                for (DamlValue.TextMap.Entry entry : val.lstEntry()) {
                    obj.set(entry.strKey(), node(entry.value()));
                }
                yield obj;
            }

            // Keys are values, not strings, so this cannot be an object.
            case DamlValue.GenMap val -> {
                ArrayNode arr = FACTORY.arrayNode();
                for (DamlValue.GenMap.Entry entry : val.lstEntry()) {
                    ArrayNode pair = FACTORY.arrayNode();
                    pair.add(node(entry.key()));
                    pair.add(node(entry.value()));
                    arr.add(pair);
                }
                yield arr;
            }
        };
    }


    /**
     * A record with labels becomes an object. Without them - a read that did
     * not set verbose - it becomes an ARRAY rather than an object with invented
     * keys, because positional data dressed as named data is a lie about what
     * the ledger returned.
     */
    private JsonNode nodeRecord(DamlValue.Rec rec) {
        boolean flagLabelled = !rec.lstField().isEmpty();
        for (DamlValue.Rec.Field fld : rec.lstField()) {
            if (fld.nameField() == null || fld.nameField().isEmpty()) {
                flagLabelled = false;
                break;
            }
        }

        if (!flagLabelled) {
            ArrayNode arr = FACTORY.arrayNode();
            for (DamlValue.Rec.Field fld : rec.lstField()) {
                arr.add(node(fld.value()));
            }
            return arr;
        }

        ObjectNode obj = FACTORY.objectNode();
        for (DamlValue.Rec.Field fld : rec.lstField()) {
            obj.set(fld.nameField(), node(fld.value()));
        }
        return obj;
    }


    private ObjectNode nodeContract(Contract contract) {
        ObjectNode obj = FACTORY.objectNode();
        obj.put("contractId", contract.idContract());
        // Empty rather than absent when the participant reported none: a
        // missing key reads as "this renderer forgot", an empty one as "the
        // ledger did not say".
        obj.put("eventId", contract.idEvent() == null ? "" : contract.idEvent());
        obj.put("templateId", str(contract.idTemplate()));
        obj.set("payload", node(contract.payload()));
        obj.set("signatories", arrText(contract.lstSignatory()));
        obj.set("observers", arrText(contract.lstObserver()));
        obj.set("key", contract.key().map(this::node).orElse(FACTORY.nullNode()));
        obj.put("active", contract.isActive());
        return obj;
    }


    /**
     * Consequences nest inside their exercise rather than sitting in a flat
     * list, so an archive stays adjacent to the choice that caused it - design
     * sec. 11.2. That is the whole reason the tree is rebuilt from the wire's
     * flat map.
     */
    private ObjectNode nodeTx(TxNode node) {
        ObjectNode obj = FACTORY.objectNode();

        switch (node) {
            case TxNode.Created val -> {
                obj.put("kind", "created");
                obj.put("eventId", val.idEvent());
                obj.put("contractId", val.idContract());
                obj.put("templateId", str(val.idTemplate()));
                obj.set("payload", node(val.payload()));
                obj.set("signatories", arrText(val.lstSignatory()));
                obj.set("observers", arrText(val.lstObserver()));
            }

            case TxNode.Exercised val -> {
                obj.put("kind", "exercised");
                obj.put("eventId", val.idEvent());
                obj.put("contractId", val.idContract());
                obj.put("templateId", str(val.idTemplate()));
                obj.put("choice", val.nameChoice());
                obj.put("consuming", val.flagConsuming());
                obj.set("actingParties", arrText(val.lstActor()));
                obj.set("argument", node(val.argument()));
                // null means the choice returned nothing the API reported, NOT
                // that it returned unit. Kept distinct all the way out.
                obj.set("result", node(val.valueResult()));

                ArrayNode arr = obj.putArray("children");
                for (TxNode child : val.lstChild()) {
                    arr.add(nodeTx(child));
                }
            }
        }
        return obj;
    }


    private static ArrayNode arrText(List<String> lstStr) {
        ArrayNode arr = FACTORY.arrayNode();
        for (String str : lstStr) {
            arr.add(str);
        }
        return arr;
    }


    private static String str(DataId idData) {
        return idData == null ? null : idData.toString();
    }


    private String write(JsonNode node) {
        try {
            return flagPretty ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                    : mapper.writeValueAsString(node);
        }
        catch (JsonProcessingException ex) {
            // Jackson serialising a tree it was handed should not fail. If it
            // does, the caller gets a real failure rather than a partial
            // document that looks like data.
            throw new IllegalStateException("could not render JSON", ex);
        }
    }

}
