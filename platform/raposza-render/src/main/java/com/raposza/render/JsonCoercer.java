// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import com.raposza.api.Substitution_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JsonRenderer run backwards: a JSON document plus a declared type becomes a
 * DamlValue.
 *
 * IT LIVES BESIDE THE RENDERER ON PURPOSE. The fixture language takes
 * "the same JSON the tool already renders contract payloads in", so an operator
 * copies a payload out of the detail pane, edits it and submits it. That makes
 * renderer and coercer two halves of ONE encoding specification, and two
 * implementations of one specification in two modules drift. The drift would
 * surface as a fixture that will not submit a payload the tool itself printed.
 *
 * The type is what makes this possible at all. JSON carries no type identity:
 * a party, a contract id, a timestamp and a piece of text are all strings, and
 * an Int64 and a Numeric are both strings too. Every one of those is recovered
 * from the DECLARED type rather than guessed from the text, which is why this
 * class needs a registry and the renderer does not.
 *
 * <h2>What is deliberately strict</h2>
 *
 * A missing field is an error UNLESS its type is Optional, in which case it is
 * None - that is the Daml JSON API's convention and the one an operator will
 * have in their fingers. An UNKNOWN key is always an error, naming the key: a
 * silently ignored key is a typo that submits the wrong contract.
 *
 * <h2>What is deliberately refused</h2>
 *
 * A nested Optional. The renderer encodes None as null and Some as the bare
 * value, which cannot express Some None, and it says so in its own comment. The
 * ecosystem's answer is an array form; implementing half of it here would make
 * this class disagree with the renderer, which is the one thing it exists not
 * to do. Refused by name so the gap is visible rather than silently wrong.
 *
 * Author Claude/bentzn
 */
public final class JsonCoercer {

    private final TypeRegistry_i registry;
    private final Substitution_i subst;


    /**
     * @param registry resolves DamlType.Ref; required, because a payload of any
     *                 interest contains at least one
     */
    public JsonCoercer(TypeRegistry_i registry) {
        this(registry, null);
    }


    /**
     * With substitution. A whole string of the form {@code $name} is replaced
     * by what that name is bound to, and the binding's type must equal the type
     * declared HERE - which is the whole reason substitution happens inside
     * coercion rather than before it. See Substitution_i.
     *
     * @param registry resolves DamlType.Ref
     * @param subst where a name is looked up, or null for no substitution, in
     *              which case '$' is an ordinary character
     */
    public JsonCoercer(TypeRegistry_i registry, Substitution_i subst) {
        if (registry == null)
            throw new IllegalArgumentException("type registry is required");

        this.registry = registry;
        this.subst = subst;
    }


    /**
     * @param node the JSON, as JsonRenderer would have written it
     * @param type the declared type, fully applied
     * @return the value
     * @throws CoercionException when the document does not fit the type
     */
    public DamlValue coerce(JsonNode node, DamlType type) {
        return coerce(node, type, "$");
    }


    /**
     * The payload case, which is the one both surfaces start from.
     *
     * @param node the JSON object
     * @param idData the record type, normally a template id
     * @return the record
     * @throws CoercionException when the document does not fit the type
     */
    public DamlValue.Rec coerceRecord(JsonNode node, DataId idData) {
        DamlValue value = coerce(node, new DamlType.Ref(idData), "$");
        if (!(value instanceof DamlValue.Rec rec))
            throw new CoercionException("$", idData + " is not a record type");

        return rec;
    }


    private DamlValue coerce(JsonNode node, DamlType type, String strPath) {
        if (type == null)
            throw new CoercionException(strPath, "no declared type, so nothing can be read here");

        // Not for OptionalOf. coerceOptional recurses with the ELEMENT type, so
        // letting it through means "$alice" lands in an Optional Party field
        // against Party rather than against Optional Party - which is what an
        // operator writes and what the renderer emits. Checking here would
        // refuse it for a type mismatch that is an artefact of where the check
        // sits.
        JsonNode nodeUse = node;
        if (subst != null && !(type instanceof DamlType.OptionalOf) && nodeUse != null
                && nodeUse.isTextual()) {
            DamlValue valueSubst = substitute(nodeUse.textValue(), type, strPath);
            if (valueSubst != null)
                return valueSubst;
            nodeUse = unescape(nodeUse);
        }
        node = nodeUse;

        return switch (type) {
            case DamlType.Prim val -> coercePrim(node, val, strPath);
            case DamlType.Numeric val -> coerceNumeric(node, val, strPath);
            case DamlType.ListOf val -> coerceList(node, val.typeElem(), strPath);
            case DamlType.OptionalOf val -> coerceOptional(node, val.typeElem(), strPath);
            case DamlType.TextMapOf val -> coerceTextMap(node, val.typeValue(), strPath);
            case DamlType.GenMapOf val -> coerceGenMap(node, val.typeKey(), val.typeValue(),
                    strPath);
            case DamlType.Ref val -> coerceRef(node, val.idData(), List.of(), strPath);
            case DamlType.App val -> coerceApp(node, val, strPath);

            // A free type variable at a point where a value is being read means
            // substitution did not happen. That is a defect in whatever built
            // the type, not in the operator's JSON, and saying so is more use
            // than "expected something".
            case DamlType.Var val -> throw new CoercionException(strPath,
                    "unsubstituted type variable '" + val.nameVar()
                            + "'; the declared type was not fully applied");
        };
    }


    private DamlValue coercePrim(JsonNode node, DamlType.Prim type, String strPath) {
        switch (type.kind()) {
            case UNIT: {
                // The renderer writes unit as an empty object. An empty record
                // renders as [], so the two stay distinguishable.
                if (!node.isObject() || node.size() != 0)
                    throw wrong(strPath, "Unit, written as {}", node);
                return new DamlValue.Unit();
            }

            case BOOL: {
                if (!node.isBoolean())
                    throw wrong(strPath, "a JSON boolean", node);
                return new DamlValue.Bool(node.booleanValue());
            }

            case INT64: {
                // A string is what the renderer writes, because a JSON number
                // is a double to most readers and Int64 outruns one past 2^53.
                // A number is accepted too, since an operator writing a fixture
                // by hand will type one - but only an integral one in range.
                if (node.isTextual()) {
                    try {
                        return new DamlValue.Int64(Long.parseLong(node.textValue().trim()));
                    }
                    catch (NumberFormatException ex) {
                        throw new CoercionException(strPath,
                                "'" + node.textValue() + "' is not an Int64");
                    }
                }
                if (node.isIntegralNumber() && node.canConvertToLong())
                    return new DamlValue.Int64(node.longValue());
                throw wrong(strPath, "an Int64, as a quoted string", node);
            }

            case TEXT: {
                if (!node.isTextual())
                    throw wrong(strPath, "a JSON string", node);
                return new DamlValue.Text(node.textValue());
            }

            case TIMESTAMP: {
                if (!node.isTextual())
                    throw wrong(strPath, "a timestamp as an ISO-8601 string", node);
                try {
                    return new DamlValue.TimeVal(Instant.parse(node.textValue()));
                }
                catch (DateTimeParseException ex) {
                    throw new CoercionException(strPath,
                            "'" + node.textValue() + "' is not an ISO-8601 instant");
                }
            }

            case DATE: {
                if (!node.isTextual())
                    throw wrong(strPath, "a date as YYYY-MM-DD", node);
                try {
                    return new DamlValue.DateVal(LocalDate.parse(node.textValue()));
                }
                catch (DateTimeParseException ex) {
                    throw new CoercionException(strPath,
                            "'" + node.textValue() + "' is not a date");
                }
            }

            case PARTY: {
                if (!node.isTextual())
                    throw wrong(strPath, "a party id as a string", node);
                // No format check. Only <something>::<fingerprint> holds, and a
                // tool that rejects a party the participant accepted is worse
                // than one that lets the participant answer.
                return new DamlValue.Party(node.textValue());
            }

            case CONTRACT_ID: {
                if (!node.isTextual())
                    throw wrong(strPath, "a contract id as a string", node);
                return new DamlValue.ContractRef(node.textValue());
            }

            default:
                throw new CoercionException(strPath, "unhandled primitive " + type.kind());
        }
    }


    /**
     * The scale is NOT imposed. The ledger sends a Numeric at its declared
     * scale and the renderer prints it verbatim, so a round trip is exact; an
     * operator writing fewer digits by hand is writing a legal value and having
     * it silently padded would change what the transcript says was submitted.
     *
     * A scale GREATER than the declared one is refused, because that value
     * cannot exist at this type and the participant would refuse it anyway -
     * later, with less information about where it came from.
     */
    private DamlValue coerceNumeric(JsonNode node, DamlType.Numeric type, String strPath) {
        BigDecimal num;
        if (node.isTextual()) {
            try {
                num = new BigDecimal(node.textValue().trim());
            }
            catch (NumberFormatException ex) {
                throw new CoercionException(strPath,
                        "'" + node.textValue() + "' is not a number");
            }
        }
        else if (node.isNumber()) {
            num = node.decimalValue();
        }
        else {
            throw wrong(strPath, "a number, as a quoted string", node);
        }

        if (num.scale() > type.cntScale()) {
            throw new CoercionException(strPath, "scale " + num.scale()
                    + " exceeds the declared Numeric " + type.cntScale());
        }
        return new DamlValue.Decimal(num);
    }


    private DamlValue coerceList(JsonNode node, DamlType typeElem, String strPath) {
        if (!node.isArray())
            throw wrong(strPath, "a JSON array", node);

        List<DamlValue> lstElem = new ArrayList<>(node.size());
        for (int cntLoop = 0; cntLoop < node.size(); cntLoop++) {
            lstElem.add(coerce(node.get(cntLoop), typeElem, strPath + "[" + cntLoop + "]"));
        }
        return new DamlValue.Lst(List.copyOf(lstElem));
    }


    private DamlValue coerceOptional(JsonNode node, DamlType typeElem, String strPath) {
        if (typeElem instanceof DamlType.OptionalOf) {
            throw new CoercionException(strPath, "nested Optional is not supported by this"
                    + " encoding - null cannot distinguish None from Some None, and the renderer"
                    + " has the same gap");
        }

        if (node == null || node.isNull() || node.isMissingNode())
            return new DamlValue.Opt(null);

        return new DamlValue.Opt(coerce(node, typeElem, strPath));
    }


    private DamlValue coerceTextMap(JsonNode node, DamlType typeValue, String strPath) {
        if (!node.isObject())
            throw wrong(strPath, "a JSON object keyed by text", node);

        List<DamlValue.TextMap.Entry> lstEntry = new ArrayList<>(node.size());
        Iterator<String> itName = node.fieldNames();
        while (itName.hasNext()) {
            String strKey = itName.next();
            lstEntry.add(new DamlValue.TextMap.Entry(strKey,
                    coerce(node.get(strKey), typeValue, strPath + "." + strKey)));
        }
        return new DamlValue.TextMap(List.copyOf(lstEntry));
    }


    private DamlValue coerceGenMap(JsonNode node, DamlType typeKey, DamlType typeValue,
            String strPath) {
        if (!node.isArray())
            throw wrong(strPath, "an array of [key, value] pairs", node);

        List<DamlValue.GenMap.Entry> lstEntry = new ArrayList<>(node.size());
        for (int cntLoop = 0; cntLoop < node.size(); cntLoop++) {
            JsonNode nodePair = node.get(cntLoop);
            String strAt = strPath + "[" + cntLoop + "]";
            if (!nodePair.isArray() || nodePair.size() != 2)
                throw wrong(strAt, "a two-element [key, value] array", nodePair);

            lstEntry.add(new DamlValue.GenMap.Entry(
                    coerce(nodePair.get(0), typeKey, strAt + "[0]"),
                    coerce(nodePair.get(1), typeValue, strAt + "[1]")));
        }
        return new DamlValue.GenMap(List.copyOf(lstEntry));
    }


    private DamlValue coerceApp(JsonNode node, DamlType.App type, String strPath) {
        if (!(type.typeFun() instanceof DamlType.Ref ref)) {
            throw new CoercionException(strPath,
                    "an applied type whose head is not a data reference cannot be read");
        }
        return coerceRef(node, ref.idData(), type.lstArg(), strPath);
    }


    private DamlValue coerceRef(JsonNode node, DataId idData, List<DamlType> lstArg,
            String strPath) {
        Optional<DataShape> optShape = registry.shape(idData);
        if (optShape.isEmpty()) {
            throw new CoercionException(strPath, idData + " is not in the type registry;"
                    + " its package has not been read from the participant");
        }

        DataShape shape = optShape.get();
        Map<String, DamlType> mapEnv = bind(shape.lstParam(), lstArg, idData, strPath);

        return switch (shape) {
            case DataShape.Rec rec -> coerceRecord(node, rec, mapEnv, strPath);
            case DataShape.Variant var -> coerceVariant(node, var, mapEnv, strPath);
            case DataShape.EnumShape enm -> coerceEnum(node, enm, strPath);
        };
    }


    private static Map<String, DamlType> bind(List<String> lstParam, List<DamlType> lstArg,
            DataId idData, String strPath) {
        if (lstParam.isEmpty() && lstArg.isEmpty())
            return Map.of();

        if (lstParam.size() != lstArg.size()) {
            throw new CoercionException(strPath, idData + " declares " + lstParam.size()
                    + " type parameter(s) but was applied to " + lstArg.size());
        }

        Map<String, DamlType> mapEnv = new HashMap<>();
        for (int cntLoop = 0; cntLoop < lstParam.size(); cntLoop++) {
            mapEnv.put(lstParam.get(cntLoop), lstArg.get(cntLoop));
        }
        return mapEnv;
    }


    /**
     * The labelled form is an object; the POSITIONAL form is an array, which is
     * what the renderer emits for a record the participant returned without
     * labels. Both are accepted, because the round trip has to survive a
     * non-verbose read even though this tool always asks for a verbose one.
     */
    private DamlValue coerceRecord(JsonNode node, DataShape.Rec shape,
            Map<String, DamlType> mapEnv, String strPath) {
        List<FieldInfo> lstDeclared = shape.lstField();

        if (node.isArray()) {
            if (node.size() != lstDeclared.size()) {
                throw new CoercionException(strPath, shape.idData() + " has "
                        + lstDeclared.size() + " field(s) but the array holds " + node.size());
            }

            List<DamlValue.Rec.Field> lstField = new ArrayList<>(lstDeclared.size());
            for (int cntLoop = 0; cntLoop < lstDeclared.size(); cntLoop++) {
                FieldInfo info = lstDeclared.get(cntLoop);
                lstField.add(new DamlValue.Rec.Field(info.nameField(),
                        coerce(node.get(cntLoop), subst(info.type(), mapEnv),
                                strPath + "[" + cntLoop + "]")));
            }
            return new DamlValue.Rec(shape.idData(), List.copyOf(lstField));
        }

        if (!node.isObject())
            throw wrong(strPath, "a JSON object for " + shape.idData(), node);

        Set<String> setSeen = new LinkedHashSet<>();
        List<DamlValue.Rec.Field> lstField = new ArrayList<>(lstDeclared.size());
        for (FieldInfo info : lstDeclared) {
            String strAt = strPath + "." + info.nameField();
            DamlType typeField = subst(info.type(), mapEnv);
            JsonNode nodeField = node.get(info.nameField());
            setSeen.add(info.nameField());

            if (nodeField == null) {
                // Absent means None, and only means None. Anything else absent
                // is a fixture that will submit something the operator did not
                // write.
                if (!(typeField instanceof DamlType.OptionalOf)) {
                    throw new CoercionException(strAt,
                            "required field is missing from the object");
                }
                lstField.add(new DamlValue.Rec.Field(info.nameField(), new DamlValue.Opt(null)));
                continue;
            }

            lstField.add(new DamlValue.Rec.Field(info.nameField(),
                    coerce(nodeField, typeField, strAt)));
        }

        List<String> lstExtra = new ArrayList<>();
        Iterator<String> itName = node.fieldNames();
        while (itName.hasNext()) {
            String strName = itName.next();
            if (!setSeen.contains(strName))
                lstExtra.add(strName);
        }
        if (!lstExtra.isEmpty()) {
            throw new CoercionException(strPath, shape.idData() + " has no field(s) named "
                    + String.join(", ", lstExtra));
        }

        return new DamlValue.Rec(shape.idData(), List.copyOf(lstField));
    }


    private DamlValue coerceVariant(JsonNode node, DataShape.Variant shape,
            Map<String, DamlType> mapEnv, String strPath) {
        if (!node.isObject() || !node.has("tag") || !node.has("value"))
            throw wrong(strPath, "a variant as {\"tag\": ..., \"value\": ...}", node);

        JsonNode nodeTag = node.get("tag");
        if (!nodeTag.isTextual())
            throw wrong(strPath + ".tag", "the constructor name as a string", nodeTag);

        String nameCtor = nodeTag.textValue();
        for (FieldInfo info : shape.lstCtor()) {
            if (info.nameField().equals(nameCtor)) {
                return new DamlValue.Variant(shape.idData(), nameCtor,
                        coerce(node.get("value"), subst(info.type(), mapEnv),
                                strPath + ".value"));
            }
        }

        throw new CoercionException(strPath + ".tag", "'" + nameCtor + "' is not a constructor of "
                + shape.idData() + "; it has " + names(shape));
    }


    private DamlValue coerceEnum(JsonNode node, DataShape.EnumShape shape, String strPath) {
        if (!node.isTextual())
            throw wrong(strPath, "an enum constructor name as a string", node);

        String nameCtor = node.textValue();
        if (!shape.lstCtor().contains(nameCtor)) {
            throw new CoercionException(strPath, "'" + nameCtor + "' is not a constructor of "
                    + shape.idData() + "; it has " + String.join(", ", shape.lstCtor()));
        }
        return new DamlValue.EnumVal(shape.idData(), nameCtor);
    }


    /**
     * Replaces type variables by the arguments the enclosing application bound
     * them to. A field of a parameterised record carries Var; the value being
     * read is at a fully applied occurrence, so the variable has to go before
     * anything can be read against it.
     *
     * @param type the declared type, possibly containing variables
     * @param mapEnv the bindings, empty for a ground type
     * @return the type with every bound variable replaced
     */
    private static DamlType subst(DamlType type, Map<String, DamlType> mapEnv) {
        if (mapEnv.isEmpty())
            return type;

        return switch (type) {
            case DamlType.Var val -> {
                DamlType typeBound = mapEnv.get(val.nameVar());
                yield typeBound == null ? val : typeBound;
            }
            case DamlType.ListOf val -> new DamlType.ListOf(subst(val.typeElem(), mapEnv));
            case DamlType.OptionalOf val -> new DamlType.OptionalOf(subst(val.typeElem(), mapEnv));
            case DamlType.TextMapOf val -> new DamlType.TextMapOf(subst(val.typeValue(), mapEnv));
            case DamlType.GenMapOf val -> new DamlType.GenMapOf(subst(val.typeKey(), mapEnv),
                    subst(val.typeValue(), mapEnv));
            case DamlType.App val -> {
                List<DamlType> lstArg = new ArrayList<>(val.lstArg().size());
                for (DamlType typeArg : val.lstArg()) {
                    lstArg.add(subst(typeArg, mapEnv));
                }
                yield new DamlType.App(subst(val.typeFun(), mapEnv), List.copyOf(lstArg));
            }
            case DamlType.Prim val -> val;
            case DamlType.Numeric val -> val;
            case DamlType.Ref val -> val;
        };
    }


    /**
     * A whole string of the form $name, and only a whole string - sec. 6 of the
     * language design. {@code "owner-$alice"} is literal text, and fixing that
     * now rather than later is deliberate: a retrofitted interpolation escape
     * would break every script already saved.
     *
     * @return the bound value, or null when the string is not a reference at all
     * @throws CoercionException when it IS a reference and cannot be honoured
     */
    private DamlValue substitute(String strText, DamlType type, String strPath) {
        if (strText == null || strText.length() < 2 || strText.charAt(0) != '$')
            return null;

        // $$ is the escape for a literal leading $, so it is never a reference.
        if (strText.charAt(1) == '$')
            return null;

        String name = strText.substring(1);
        if (!isName(name)) {
            // A '$' followed by something that is not a name is an ordinary
            // string. Refusing it would make a currency label a syntax error.
            return null;
        }

        Optional<Substitution_i.Bound> optBound = subst.lookup(name);
        if (optBound.isEmpty()) {
            throw new CoercionException(strPath, "'" + strText + "' is not bound; write $$"
                    + name + " for a literal");
        }

        Substitution_i.Bound bound = optBound.get();
        if (!type.equals(bound.type())) {
            throw new CoercionException(strPath, "'" + strText + "' holds "
                    + describe(bound.type()) + " and this field is " + describe(type));
        }
        return bound.value();
    }


    /**
     * Strips ONE leading dollar, so $$alice reads as the literal $alice.
     *
     * The language says this is resolved before JSON string escapes are
     * considered. It is resolved AFTER them here, because Jackson has already
     * decoded the document by this point and re-deriving the raw text would
     * mean parsing the JSON twice. The two differ only when a dollar is written
     * as \u0024 - and there the result is still the literal the author was
     * reaching for. Recorded rather than hidden.
     */
    private static JsonNode unescape(JsonNode node) {
        String str = node.textValue();
        if (str != null && str.startsWith("$$"))
            return TextNode.valueOf(str.substring(1));
        return node;
    }


    /**
     * A name, or a dotted projection of one.
     *
     * <h2>The dot is admitted here and resolved by the SUBSTITUTION</h2>
     *
     * This class asks {@link Substitution_i} for a name and gets a typed value
     * back; whether {@code r.owner} means anything is the substitution's
     * question, not this one. Admitting the dot only widens what counts as a
     * reference rather than as ordinary text.
     *
     * <h2>What this deliberately does NOT change</h2>
     *
     * A dollar followed by something that is not a name is still ordinary text
     * - {@code "$1,000"} and {@code "$"} are unaffected. So is a TRAILING or
     * DOUBLED dot: {@code "$rate."} and {@code "$a..b"} stay literal rather
     * than becoming a reference that fails, because a label ending in a period
     * is far likelier than a projection that was typed wrong here. The CaQL
     * lexer refuses those in a value slot, where a dollar is unambiguously a
     * reference; inside a JSON string it is not.
     */
    private static boolean isName(String str) {
        if (str.isEmpty() || !Character.isLetter(str.charAt(0)))
            return false;

        boolean flagDot = false;
        for (int cntLoop = 1; cntLoop < str.length(); cntLoop++) {
            char ch = str.charAt(cntLoop);
            if (ch == '.') {
                // A dot must separate two name characters. Nothing may start
                // with one, end with one, or carry two in a row.
                if (flagDot || cntLoop == str.length() - 1)
                    return false;
                flagDot = true;
                continue;
            }
            if (!Character.isLetterOrDigit(ch) && ch != '_')
                return false;

            if (flagDot && !Character.isLetter(ch))
                return false;
            flagDot = false;
        }
        return true;
    }


    /** Short enough for a message, specific enough to act on. */
    private static String describe(DamlType type) {
        return switch (type) {
            case DamlType.Prim val -> val.kind().name();
            case DamlType.Numeric val -> "Numeric " + val.cntScale();
            case DamlType.ListOf val -> "a list of " + describe(val.typeElem());
            case DamlType.OptionalOf val -> "an optional " + describe(val.typeElem());
            case DamlType.TextMapOf val -> "a text map of " + describe(val.typeValue());
            case DamlType.GenMapOf val -> "a map of " + describe(val.typeKey()) + " to "
                    + describe(val.typeValue());
            case DamlType.Ref val -> val.idData().shortName();
            case DamlType.Var val -> "type variable " + val.nameVar();
            case DamlType.App val -> describe(val.typeFun());
        };
    }


    private static String names(DataShape.Variant shape) {
        List<String> lstName = new ArrayList<>(shape.lstCtor().size());
        for (FieldInfo info : shape.lstCtor()) {
            lstName.add(info.nameField());
        }
        return String.join(", ", lstName);
    }


    /**
     * The message names what was expected AND what was found, because "expected
     * a string" over a document the operator is looking at is a riddle.
     */
    private static CoercionException wrong(String strPath, String strExpected, JsonNode node) {
        String strFound = node == null || node.isMissingNode() ? "nothing"
                : node.getNodeType().toString().toLowerCase();
        return new CoercionException(strPath, "expected " + strExpected + ", found " + strFound);
    }

}
