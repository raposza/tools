// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * The result of reading a bounded stretch of the update stream.
 *
 * A bare list would not do. The window and the cap are part of the ANSWER, not
 * of the request: a scan that stopped at its cap and a scan that reached the
 * end of its window both return transactions, and only one of them may be read
 * as "this is everything that happened". A caller holding only the list cannot
 * tell them apart, and the pane above this has to say which one it is showing.
 *
 * offsetFrom and offsetTo are what was ACTUALLY scanned, after any open end was
 * resolved against the ledger. Echoing back what the caller asked for would
 * report a window nobody read.
 *
 * @param lstTree the transactions, in offset order
 * @param flagCapped true when the cap stopped the read rather than the window
 * @param offsetFrom the first offset in the window, inclusive
 * @param offsetTo the last offset in the window, inclusive
 *
 * Author Claude/bentzn
 */
public record UpdateScan(List<TxTree> lstTree, boolean flagCapped, String offsetFrom,
        String offsetTo) {

    /** @return an empty scan over an empty window */
    public static UpdateScan empty(String offsetFrom, String offsetTo) {
        return new UpdateScan(List.of(), false, offsetFrom, offsetTo);
    }

}
