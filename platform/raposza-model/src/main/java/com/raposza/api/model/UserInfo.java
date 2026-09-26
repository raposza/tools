// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * A ledger user and the parties it may act or read as.
 *
 * @param idUser the user id
 * @param idPartyPrimary primary party, may be null
 * @param flagAdmin true when the user holds ParticipantAdmin
 * @param lstPartyAct parties the user can act as
 * @param lstPartyRead parties the user can read as
 *
 * Author Claude/bentzn
 */
public record UserInfo(String idUser, String idPartyPrimary, boolean flagAdmin,
        List<String> lstPartyAct, List<String> lstPartyRead) {}
