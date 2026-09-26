// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.render.LineRenderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The record of ONE run. Sec. 10.
 *
 * Party ids differ on every run, so the SCRIPT is the reproducible artefact and
 * this is the record of what happened once. It is not replayable and does not
 * try to be.
 *
 * @param idScript hash of the script, so two transcripts can be told apart or
 *                 recognised as the same fixture
 * @param instStart when the run began
 * @param flagValidateOnly true when nothing was sent
 * @param lstEntry one per statement EXECUTED - a run stops on the first
 *                 failure, so a transcript shorter than the script is the
 *                 normal shape of a failed run rather than a truncated file
 * @param lstBinding the state the run ENDED in. The entries say what each
 *                   statement did; this says what was left, which is the
 *                   question asked first when a fixture misbehaves and the one
 *                   the entries answer only by being read end to end
 *
 * Author Claude/bentzn
 */
public record Transcript(String idScript, Instant instStart, boolean flagValidateOnly,
        List<Entry> lstEntry, List<Binding> lstBinding) {

    /** @return the status of the last statement, empty when none ran */
    public Optional<RunStatus> statusLast() {
        if (lstEntry.isEmpty())
            return Optional.empty();
        return Optional.of(lstEntry.get(lstEntry.size() - 1).status());
    }


    /** @return true when every statement reached a state the run could continue past */
    public boolean flagOk() {
        for (Entry entry : lstEntry) {
            if (!entry.status().flagContinue())
                return false;
        }
        return true;
    }


    /**
     * @param mapper a factory for the nodes
     * @return the transcript as JSON
     */
    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("script", idScript);
        obj.put("startedAt", instStart.toString());
        obj.put("mode", flagValidateOnly ? "validate" : "run");
        obj.put("ok", flagOk());

        ArrayNode arr = obj.putArray("statements");
        for (Entry entry : lstEntry) {
            arr.add(entry.json(mapper));
        }

        // Values, not just names. A binding holding a contract id is the thing
        // a reader wants to paste into the next command, and a name alone
        // sends them hunting back up the statement list for it.
        ArrayNode arrBind = obj.putArray("bindings");
        for (Binding binding : lstBinding) {
            ObjectNode objBind = arrBind.addObject();
            objBind.put("name", binding.name());
            objBind.put("line", binding.numLine());
            objBind.put("type", binding.type() == null ? null : binding.type().toString());
            objBind.put("value", LineRenderer.line(binding.value()));
            objBind.put("updateId", binding.idUpdate().orElse(null));
        }
        return obj;
    }

}
