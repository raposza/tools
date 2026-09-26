// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

import com.raposza.api.model.Resolved;

import java.util.List;

/**
 * The Explore pane. Turns whatever the operator pasted into something the UI
 * can show.
 *
 * Implemented as an ordered chain of RefProbe_i. This is a subsystem rather
 * than a method on the client: probe ordering is a correctness concern, since
 * contract ids and update ids are both LedgerStrings and can look alike.
 *
 * Author Claude/bentzn
 */
public interface Resolver_i {

    /**
     * @param strRef the pasted identifier, trimmed
     * @param lstPartyRead parties to read as
     * @return what the identifier turned out to be, or Resolved.None
     */
    Resolved resolve(String strRef, List<String> lstPartyRead);

}
