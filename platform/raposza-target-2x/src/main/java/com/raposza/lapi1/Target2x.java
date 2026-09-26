// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import com.raposza.api.TokenSource_i;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.profile.HostProfile;
import com.raposza.spi.Capabilities;
import com.raposza.spi.Capability;
import com.raposza.spi.LedgerClient_i;
import com.raposza.spi.Target_i;

import java.time.LocalDate;

/**
 * Registers the Ledger API v1 implementation as a translation target.
 *
 * The declaration below is the one artefact in this module that is expected to
 * be EDITED BY MEASUREMENT rather than by design. Every SUPPORTED line names
 * the version it was measured against and the day it was measured; when a
 * check goes green against 2.10.2, that is a change here and nowhere else.
 *
 * Explore is recorded as supported on 2.9.6, and the live suite ran the
 * submission path green against the same participant - a create that commits,
 * a rejection that keeps the Canton error code, and an outcome-unknown
 * provoked with a short deadline. COMMAND_SUBMIT is therefore a measurement
 * and not an aspiration.
 *
 * The two administrative writes were measured by the same live suite against
 * the same participant, and were available before they were declared here:
 * support is declared from a report rather than from a recollection.
 *
 * Author Claude/bentzn
 */
public final class Target2x implements Target_i {

    /** The version every claim below was measured against. */
    private static final String STR_VERSION = "2.9.6";

    /** Recorded 2026-08-07. */
    private static final LocalDate DATE_EXPLORE = LocalDate.of(2026, 8, 7);

    /** The day the refusals below were written into this module. */
    private static final LocalDate DATE_REFUSAL = LocalDate.of(2026, 8, 3);

    /** P4a, submission green against a live 2.9.6 participant. */
    private static final LocalDate DATE_ACT = LocalDate.of(2026, 8, 8);

    /** Lapi1AdminLiveTest green, 5 of 5, against a live 2.9.6 participant. */
    private static final LocalDate DATE_ADMIN = LocalDate.of(2026, 8, 11);

    /** The day the bounded update scan landed on 3.x and not here. */
    private static final LocalDate DATE_STREAM = LocalDate.of(2026, 8, 25);

    /** Implemented against the captured v1 protos; no participant has answered yet. */
    private static final String STR_NOTE_WIRED = "implemented against the v1 protos captured"
            + " from the 2.9.6 canton.jar; not yet driven against a participant";

    private static final Capabilities CAPABILITIES = Capabilities.of("canton-2x")
            .supported(Capability.LEDGER_IDENTITY, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.PARTY_LIST, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.USER_LIST, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.PACKAGE_LIST, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.PACKAGE_FETCH, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.CONTRACT_ACTIVE, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.CONTRACT_BY_ID, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.CONTRACT_KEY, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.UPDATE_BY_ID, STR_VERSION, DATE_EXPLORE)
            .supported(Capability.UPDATE_BY_EVENT_ID, STR_VERSION, DATE_EXPLORE)

            .unsupported(Capability.INTERFACE_FILTER, STR_VERSION, DATE_REFUSAL,
                    "retrieval mechanics are an open design question and silently"
                            + " dropping the filter would return the wrong contracts")
            .unsupported(Capability.PACKAGE_NAME_IDENTITY, STR_VERSION, DATE_REFUSAL,
                    "packages are addressed by content hash on 2.x; there is no"
                            + " package name or version to resolve against")
            .unsupported(Capability.OFFSET_NUMERIC, STR_VERSION, DATE_REFUSAL,
                    "v1 offsets are opaque strings and are not orderable as integers")
            .unsupported(Capability.UPDATE_STREAM, STR_VERSION, DATE_STREAM,
                    "GetTransactionTrees is not wired here; the v1 request shape has not been"
                            + " read off a descriptor and will not be written from memory")

            .supported(Capability.COMMAND_SUBMIT, STR_VERSION, DATE_ACT)

            .supported(Capability.PARTY_ALLOCATE, STR_VERSION, DATE_ADMIN)
            .supported(Capability.USER_CREATE, STR_VERSION, DATE_ADMIN)

            // WIRED, NOT MEASURED. The request shapes were read off the v1
            // protos shipped in the 2.9.6 canton.jar rather than written from
            // memory, so the field names are facts - but nothing here has been
            // driven against a participant, and this file's whole discipline is
            // that SUPPORTED names the version and the day it was measured.
            // These turn green from a suite report, not from a proto file.
            .unmeasured(Capability.USER_BY_ID, STR_NOTE_WIRED)
            .unmeasured(Capability.USER_DELETE, STR_NOTE_WIRED)
            .unmeasured(Capability.USER_RIGHTS_UPDATE, STR_NOTE_WIRED)
            .unmeasured(Capability.LEDGER_END, STR_NOTE_WIRED)
            .unmeasured(Capability.PARTICIPANT_PRUNE, STR_NOTE_WIRED)
            .build();


    @Override
    public String id() {
        return "canton-2x";
    }


    @Override
    public String nameDisplay() {
        return "Canton 2.x (Ledger API v1)";
    }


    @Override
    public ApiGeneration generation() {
        return ApiGeneration.V1;
    }


    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }


    @Override
    public LedgerClient_i connect(HostProfile profile, TokenSource_i source) {
        return new Lapi1Client(profile, source);
    }

}
