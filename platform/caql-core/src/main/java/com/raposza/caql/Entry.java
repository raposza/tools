// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

/**
 * One statement's line in the transcript. Sec. 10.
 *
 * <h2>source is not enough, which is why resolved exists</h2>
 *
 * With variables and bare template names, what was WRITTEN and what was SENT
 * differ - and when a fixture misbehaves the question is always the second one.
 * `resolved` answers it: concrete package id, concrete parties, concrete
 * contract id, canonical typed argument.
 *
 * <h2>The audit record is this object minus two fields</h2>
 *
 * `source` and `argument` are the only things here that may carry
 * business data, and they are the only two the audit log must never hold. That
 * is why they are separable rather than woven through - see {@link #audit()}.
 *
 * @param idStatement stable across edits: the hash of the normalised source
 *                    with the script's hash. Line numbers move when a script is
 *                    edited and this does not, which is what makes two runs
 *                    comparable
 * @param numLine where the statement starts
 * @param strSource the statement as written
 * @param status where it got to
 * @param nameBind the name it bound, empty when it bound nothing
 * @param resolved what was actually sent; shape varies by command
 * @param idCommand the command id, empty when nothing was submitted
 * @param idUpdate the resulting update, empty unless committed
 * @param strError why it failed, empty when it did not
 *
 * Author Claude/bentzn
 */
public record Entry(String idStatement, int numLine, String strSource, RunStatus status,
        Optional<String> nameBind, ObjectNode resolved, Optional<String> idCommand,
        Optional<String> idUpdate, Optional<String> strError) {

    /**
     * @param mapper a factory for the node
     * @return the full transcript form, source and argument included
     */
    public ObjectNode json(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("id", idStatement);
        obj.put("line", numLine);
        obj.put("source", strSource);
        obj.put("status", status.strJson());
        obj.put("bind", nameBind.orElse(null));
        obj.set("resolved", resolved == null ? mapper.createObjectNode() : resolved.deepCopy());
        obj.put("commandId", idCommand.orElse(null));
        obj.put("updateId", idUpdate.orElse(null));
        if (strError.isPresent())
            obj.put("error", strError.get());
        return obj;
    }


    /**
     * The same object with `source` removed and `argument` removed from
     * `resolved`. Nothing else changes: identity, ids, outcome and timestamp
     * are exactly what the audit log is for.
     *
     * Built by SUBTRACTION rather than by assembling a second object, so a
     * field added to the transcript cannot quietly fail to reach the audit log
     * - and, more to the point, so a field carrying a payload has to be removed
     * here deliberately rather than forgotten there.
     *
     * @param mapper a factory for the node
     * @return the audit form
     */
    public ObjectNode audit(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        ObjectNode obj = json(mapper);
        obj.remove("source");

        JsonNode nodeResolved = obj.get("resolved");
        if (nodeResolved instanceof ObjectNode objResolved)
            objResolved.remove("argument");

        return obj;
    }

}
