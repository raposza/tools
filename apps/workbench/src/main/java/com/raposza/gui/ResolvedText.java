// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Resolved;
import com.raposza.render.ContractBlock;
import com.raposza.render.JsonRenderer;

/**
 * Turns a resolution into what the result pane shows.
 *
 * Every outcome says WHAT it is before showing the payload, including the ones
 * that are not answers. A pane that prints an offset guess with the same
 * confidence as a confirmed contract undoes the entire point of the probe
 * chain, so the guesses are labelled as guesses here.
 *
 * Author Claude/bentzn
 */
public final class ResolvedText {

    private final JsonRenderer renderer = new JsonRenderer();


    /**
     * @param resolved what the resolver returned
     * @return text for the result pane
     */
    public String text(Resolved resolved) {
        return switch (resolved) {
            case Resolved.AsContract val ->
                    Banner.head("CONTRACT") + ContractBlock.block(val.contract());

            case Resolved.AsUpdate val ->
                    Banner.head("TRANSACTION") + renderer.tree(val.tree());

            case Resolved.AsParty val -> Banner.head("PARTY") + val.party().idParty()
                    + "\ndisplay name: "
                    + (val.party().nameDisplay() == null || val.party().nameDisplay().isBlank()
                            ? "(none)"
                            : val.party().nameDisplay())
                    + "\nhosted here: " + val.party().flagLocal();

            case Resolved.AsCommand val -> {
                StringBuilder buf = new StringBuilder(
                        Banner.head("COMMAND " + val.idCommand())
                        + val.lstTree().size() + " transaction(s)\n");
                for (com.raposza.api.model.TxTree tree : val.lstTree()) {
                    buf.append('\n').append(renderer.tree(tree)).append('\n');
                }
                yield buf.toString();
            }

            case Resolved.AsTemplate val -> Banner.head("TEMPLATE") + val.info()
                    + "\nactive contracts: "
                    + val.cntActive();

            // Said plainly. Nothing confirmed this; it is the shape of the
            // string and no more.
            case Resolved.AsOffset val ->
                    Banner.head("GUESS - looks like a ledger offset") + val.offset()
                    + "\n\nThe ledger did not confirm this as anything. It was not a known"
                    + "\ncontract, transaction or party for the selected read-as parties.";

            case Resolved.Ambiguous val -> {
                StringBuilder buf = new StringBuilder(Banner.head("AMBIGUOUS - "
                        + val.lstCandidate().size()
                        + " interpretations, none confirmed by the ledger")
                        + val.strRef()
                        + "\n");
                int numIdx = 1;
                for (Resolved candidate : val.lstCandidate()) {
                    buf.append("\n--- candidate ").append(numIdx++).append(" ---\n")
                        .append(text(candidate)).append('\n');
                }
                yield buf.toString();
            }

            // Names what this box accepts. The commonest way to see
            // this message is to type something that was never an
            // identifier - a template name, part of a payload - into
            // the one field that asks the participant.
            case Resolved.None val -> Banner.head("NOT FOUND") + val.strRef()
                    + "\n\nNo probe recognised this, or nothing matched for the selected read-as"
                    + "\nparties. Widening the party selection is the usual fix.\n\n"
                    + "This box resolves ONE IDENTIFIER against the participant: contract\n"
                    + "id, update id, command id, party id or offset. To narrow what is\n"
                    + "already loaded, filter the navigator or use the contract table.";
        };
    }

}
