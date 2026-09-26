// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi2;

import com.raposza.api.TokenSource_i;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.profile.HostProfile;
import com.raposza.spi.Capabilities;
import com.raposza.spi.Capability;
import com.raposza.spi.LedgerClient_i;
import com.raposza.spi.Target_i;

import java.time.LocalDate;

/**
 * The Canton 3.x target, over Ledger API v2.
 *
 * <h2>What is claimed, and on what evidence</h2>
 *
 * The capability declarations below are UNMEASURED, deliberately, with two
 * exceptions that are refusals rather than claims. A measured claim cannot be
 * constructed without the version and the date behind it, and nothing here has
 * been driven against a participant yet - so saying SUPPORTED would be a
 * declaration written from reading the code, which is the thing the three-value
 * scheme exists to prevent.
 *
 * The two refusals are real. Ledger API v2 has no event id at all, so an update
 * cannot be found by one. Interface-filtered retrieval EXISTS on v2 and is
 * refused anyway, because nothing has measured what a participant returns for
 * one and a silently empty answer is worse than an honest no.
 *
 * Author Claude/bentzn
 */
public final class Target3x implements Target_i {

    private static final String STR_ID = "canton-3x";

    private static final Capabilities CAPABILITIES = Capabilities.of(STR_ID)
            .unsupported(Capability.UPDATE_BY_EVENT_ID, "3.4.4 - 3.5.12", LocalDate.of(2026, 8, 24),
                    "Ledger API v2 has no event id; a node is addressed by offset and node id")
            .unsupported(Capability.INTERFACE_FILTER, "3.4.4 - 3.5.12", LocalDate.of(2026, 8, 24),
                    "the wire supports it and nothing has measured what comes back")
            .unmeasuredRest("built against descriptors read off a participant; no capability has"
                    + " been driven against one yet")
            .build();


    @Override
    public String id() {
        return STR_ID;
    }


    @Override
    public String nameDisplay() {
        return "Canton 3.x (Ledger API v2)";
    }


    @Override
    public ApiGeneration generation() {
        return ApiGeneration.V2;
    }


    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }


    @Override
    public LedgerClient_i connect(HostProfile profile, TokenSource_i source) {
        return new Lapi2Client(profile, source);
    }

}
