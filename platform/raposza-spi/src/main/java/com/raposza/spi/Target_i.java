// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import com.raposza.api.TokenSource_i;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.profile.HostProfile;

/**
 * One translation implementation, as a registered thing rather than a class
 * name an application has to know.
 *
 * This is the SPI's reason to exist. Before it, an application chose a
 * generation by writing `new Lapi1Client(...)`, which put a version target in
 * an application's import block and made "support 3.6" mean "edit every
 * application". A target now registers itself through java.util.ServiceLoader
 * and an application names none.
 *
 * Adding Canton 3.6 or 4.x is a new module implementing this, plus one line in
 * META-INF/services. It is never a change to raposza-model, and never a change
 * here.
 *
 * Author Claude/bentzn
 */
public interface Target_i {

    /**
     * Stable identifier, e.g. "canton-2x". Goes into reports and log lines, so
     * it does not change when the class does.
     *
     * @return the target id
     */
    String id();


    /**
     * @return what to show an operator, e.g. "Canton 2.x (Ledger API v1)"
     */
    String nameDisplay();


    /**
     * @return which Ledger API generation this target speaks
     */
    ApiGeneration generation();


    /**
     * What this target claims it can do, with the evidence.
     *
     * Available WITHOUT connecting, deliberately: the commonest question is
     * "will this work against that participant" and answering it should not
     * require reaching the participant first.
     *
     * @return the declaration; complete, never null
     */
    Capabilities capabilities();


    /**
     * Open a client.
     *
     * @param profile the connection profile
     * @param source token supply, or null for an unauthenticated ledger
     * @return a live client the caller owns and must close
     */
    LedgerClient_i connect(HostProfile profile, TokenSource_i source);

}
