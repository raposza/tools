// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Contract;
import com.raposza.api.model.ContractQuery;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.UserInfo;
import com.raposza.spi.LedgerClient_i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One read of everything the navigator lists: users, parties, the templates
 * seen on the ledger and the contracts under them.
 *
 * Three things about this are deliberate and will otherwise look like defects.
 *
 * TEMPLATES COME FROM THE CONTRACTS, not from a type registry. There is no
 * decoder yet, so the only templates this tool can honestly name are the ones
 * it just saw instances of. A participant hosting a template with no active
 * contracts will not show it, and that is the truth about what was read rather
 * than a gap in the list. Everything downstream says "seen" rather than "known"
 * for the same reason.
 *
 * A FAILING SECTION DOES NOT FAIL THE SNAPSHOT, AND THAT NOW INCLUDES
 * parties(). users() and parties() are both ADMIN calls. A token minted to read
 * contracts as two parties is refused both of them, and a token minted for
 * administration is refused the contracts - so on a participant that demands
 * authentication, SOME section is expected to fail whichever token is
 * presented. The reason is carried in the snapshot and shown on the node; a
 * navigator that empties itself because one of four calls was refused is
 * useless exactly where it is most needed.
 *
 * parties() used to be the exception, and that was wrong: a read token produced
 * a window that could read everything and displayed nothing, because the
 * connection aborted on the one call that token was never going to be allowed
 * to make.
 *
 * THE CONTRACT READ IS CAPPED and says so when the cap bites. An uncapped ACS
 * scan against a production participant is not a listing, it is an outage.
 *
 * No Swing in here on purpose: this is the part with the logic in it and it is
 * tested without a display.
 *
 * @param lstUser users, sorted by id; empty when the call was refused
 * @param strProblemUser why the user list is empty, null when it is not
 * @param lstParty parties, sorted by display label; empty when the call was
 *        refused
 * @param strProblemParty why the party list is empty, null when it is not
 * @param lstTemplate templates seen in the contracts read, sorted by name
 * @param lstContract contracts in ledger order, at most cntLimit of them
 * @param strProblemContract why the contract list is empty, null when it is not
 * @param flagCapped true when the read stopped at the cap rather than the end
 * @param cntLimit the cap that was applied
 *
 * Author Claude/bentzn
 */
public record LedgerSnapshot(List<UserInfo> lstUser, String strProblemUser,
        List<PartyInfo> lstParty, String strProblemParty, List<TemplateGroup> lstTemplate,
        List<Contract> lstContract, String strProblemContract, boolean flagCapped,
        int cntLimit) {

    /**
     * A template and the contracts of it that were read.
     *
     * @param idTemplate the full identifier, package id included
     * @param strLabel what the tree shows: module and entity, qualified by a
     *        package id prefix ONLY when two packages on this ledger carry the
     *        same module and entity name. That happens as soon as a DAR is
     *        rebuilt and re-uploaded, and two identical labels in the tree
     *        would be worse than a long one
     * @param lstContract contracts of this template, in ledger order
     */
    public record TemplateGroup(DataId idTemplate, String strLabel, List<Contract> lstContract) {}

    /** Enough to be a listing, small enough not to be an outage. */
    public static final int CNT_LIMIT_DEFAULT = 500;


    /**
     * Reads the participant. Runs on a worker thread; it makes three or four
     * round trips and one of them streams the ACS.
     *
     * @param client the connected client
     * @param lstPartyRead parties to read contracts as; when empty the contract
     *        and template lists are skipped with a reason, because a ledger
     *        read is always made as somebody
     * @param cntLimit maximum contracts to read
     * @return the snapshot, never null. No call in here is allowed to fail it
     */
    public static LedgerSnapshot load(LedgerClient_i client, List<String> lstPartyRead,
            int cntLimit) {

        List<PartyInfo> lstParty = new ArrayList<>();
        String strProblemParty = null;
        try {
            lstParty.addAll(client.parties());
            lstParty.sort(Comparator.comparing(PartyInfo::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PartyInfo::idParty));
        }
        catch (RuntimeException ex) {
            strProblemParty = describe(ex);
        }

        List<UserInfo> lstUser = new ArrayList<>();
        String strProblemUser = null;
        try {
            lstUser.addAll(client.users());
            lstUser.sort(Comparator.comparing(UserInfo::idUser, String.CASE_INSENSITIVE_ORDER));
        }
        catch (RuntimeException ex) {
            strProblemUser = describe(ex);
        }

        if (lstPartyRead.isEmpty()) {
            return new LedgerSnapshot(List.copyOf(lstUser), strProblemUser, List.copyOf(lstParty),
                    strProblemParty, List.of(), List.of(), "no read-as parties selected", false,
                    cntLimit);
        }

        List<Contract> lstContract = new ArrayList<>();
        String strProblemContract = null;
        try {
            lstContract.addAll(client.activeContracts(ContractQuery.of(lstPartyRead, cntLimit)));
        }
        catch (RuntimeException ex) {
            strProblemContract = describe(ex);
        }

        return new LedgerSnapshot(List.copyOf(lstUser), strProblemUser, List.copyOf(lstParty),
                strProblemParty, group(lstContract), List.copyOf(lstContract), strProblemContract,
                lstContract.size() >= cntLimit, cntLimit);
    }


    /**
     * Groups contracts under their template, first-seen order preserved inside
     * a group and the groups themselves sorted by what the tree will show.
     *
     * @param lstContract contracts as read
     * @return one group per distinct template identifier
     */
    static List<TemplateGroup> group(List<Contract> lstContract) {
        Map<DataId, List<Contract>> mapGroup = new LinkedHashMap<>();
        for (Contract contract : lstContract) {
            mapGroup.computeIfAbsent(contract.idTemplate(), key -> new ArrayList<>())
                    .add(contract);
        }

        Map<String, Integer> mapCount = new HashMap<>();
        for (DataId idTemplate : mapGroup.keySet()) {
            mapCount.merge(idTemplate.shortName(), 1, Integer::sum);
        }

        Set<String> setAmbiguous = new HashSet<>();
        for (Map.Entry<String, Integer> entry : mapCount.entrySet()) {
            if (entry.getValue() > 1)
                setAmbiguous.add(entry.getKey());
        }

        List<TemplateGroup> lstGroup = new ArrayList<>();
        for (Map.Entry<DataId, List<Contract>> entry : mapGroup.entrySet()) {
            DataId idTemplate = entry.getKey();
            String strLabel = idTemplate.shortName();
            if (setAmbiguous.contains(strLabel))
                strLabel = strLabel + "  [" + shortPackage(idTemplate.idPackage()) + "]";
            lstGroup.add(new TemplateGroup(idTemplate, strLabel, List.copyOf(entry.getValue())));
        }

        lstGroup.sort(Comparator.comparing(TemplateGroup::strLabel, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(lstGroup);
    }


    /**
     * @param idPackage a package id or package name
     * @return enough of it to tell two packages apart without filling the tree
     */
    static String shortPackage(String idPackage) {
        if (idPackage == null || idPackage.length() <= 12)
            return idPackage == null ? "" : idPackage;
        return idPackage.substring(0, 12) + "\u2026";
    }


    /**
     * The message, not the stack trace. A refused call is a normal outcome here
     * and it is shown on a tree node, which has room for one line.
     *
     * @param ex what was thrown
     * @return something an operator can act on
     */
    static String describe(RuntimeException ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        String strMsg = ex.getMessage() == null ? cause.getMessage() : ex.getMessage();
        if (strMsg == null || strMsg.isBlank())
            return ex.getClass().getSimpleName();
        return strMsg;
    }


    /** @return contracts read, for the section header */
    public int cntContract() {
        return lstContract.size();
    }

}
