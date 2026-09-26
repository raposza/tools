// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.profile;

/**
 * One entry of the host catalogue. The first seven fields are exactly the
 * DevTools3 "ledger_hosts" columns so a file can be shared between the two
 * tools; mode and colour are appended and default when absent.
 *
 * @param nameDisplay display name, e.g. "Backend GGH/DEV"
 * @param strProtocol "http" or "https"; https implies TLS on gRPC
 * @param nameHost hostname
 * @param portLedger gRPC Ledger API port, -1 when not exposed
 * @param portJson HTTP JSON API port, -1 when not exposed
 * @param strScope default scope for a scope-based token, null for none
 * @param strAudience default audience for an audience-based token, null for none
 * @param mode whether submission is permitted
 * @param strColour hex colour of the status bar, e.g. "#2e7d32"
 *
 * Author Claude/bentzn
 */
public record HostProfile(String nameDisplay, String strProtocol, String nameHost,
        int portLedger, int portJson, String strScope, String strAudience,
        AccessMode mode, String strColour) {

    public boolean isTls() {
        return "https".equalsIgnoreCase(strProtocol);
    }


    public boolean canSubmit() {
        return mode == AccessMode.READ_WRITE;
    }


    public boolean hasLedgerPort() {
        return portLedger > 0;
    }


    public boolean hasJsonPort() {
        return portJson > 0;
    }

}
