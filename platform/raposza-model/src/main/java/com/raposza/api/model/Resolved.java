// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * Outcome of pasting an arbitrary identifier into the single search box.
 * The client tries each plausible interpretation and returns what it found;
 * an unrecognised string yields None rather than an exception.
 *
 * Author Claude/bentzn
 */
public sealed interface Resolved {

    record None(String strRef) implements Resolved {}

    /** @param contract the contract, active or archived */
    record AsContract(Contract contract) implements Resolved {}

    /** @param tree the transaction */
    record AsUpdate(TxTree tree) implements Resolved {}

    /**
     * A command id can fan out to more than one transaction.
     *
     * @param idCommand the command id
     * @param lstTree transactions produced by it
     */
    record AsCommand(String idCommand, List<TxTree> lstTree) implements Resolved {}

    /** @param party the party */
    record AsParty(PartyInfo party) implements Resolved {}

    /**
     * @param info the template
     * @param cntActive number of active contracts visible to the reader
     */
    record AsTemplate(TemplateInfo info, int cntActive) implements Resolved {}

    /** @param offset the ledger offset */
    record AsOffset(String offset) implements Resolved {}

    /**
     * More than one interpretation survived, none of them confirmed against
     * the ledger. The UI asks rather than choosing: picking silently is how a
     * pasted string gets presented as the wrong object with total confidence.
     *
     * Never carries a confirmed result - a confirmed one ends the chain.
     *
     * @param strRef what was pasted
     * @param lstCandidate the surviving interpretations, best guess first
     */
    record Ambiguous(String strRef, List<Resolved> lstCandidate) implements Resolved {}

}
