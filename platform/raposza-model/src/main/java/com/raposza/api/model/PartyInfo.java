// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * @param idParty the party id
 * @param nameDisplay display name, may be null
 * @param flagLocal true when hosted on the connected participant
 *
 * Author Claude/bentzn
 */
public record PartyInfo(String idParty, String nameDisplay, boolean flagLocal) {

    /** Display name when there is one, party id otherwise. */
    public String label() {
        if (nameDisplay == null || nameDisplay.isBlank())
            return idParty;
        return nameDisplay;
    }

}
