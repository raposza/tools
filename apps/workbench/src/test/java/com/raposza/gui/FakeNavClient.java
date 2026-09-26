// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.model.Command;
import com.raposza.api.model.Contract;
import com.raposza.api.model.ContractQuery;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.LedgerInfo;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.SubmitContext;
import com.raposza.api.model.SubmitResult;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UserInfo;
import com.raposza.spi.LedgerClient_i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A participant that answers exactly what a test told it to.
 *
 * Hand written rather than mocked, and it records the query it was given: the
 * snapshot's contract is that it passes the caller's cap and read-as parties
 * through untouched, and a mock would let that be asserted only by rewriting
 * the assertion whenever the call changed.
 *
 * Anything a test has not armed throws. Nothing returns a plausible default,
 * because a navigator populated from a default is the failure this whole class
 * of test exists to prevent.
 *
 * Author Claude/bentzn
 */
final class FakeNavClient implements LedgerClient_i {

    private final List<PartyInfo> lstParty = new ArrayList<>();
    private final List<UserInfo> lstUser = new ArrayList<>();
    private final List<Contract> lstContract = new ArrayList<>();

    private RuntimeException exParties;
    private RuntimeException exUsers;
    private RuntimeException exContracts;

    ContractQuery queryLast;
    int cntCallContracts;
    int cntCallUsers;


    FakeNavClient withParty(String idParty, String nameDisplay) {
        lstParty.add(new PartyInfo(idParty, nameDisplay, true));
        return this;
    }


    FakeNavClient withUser(String idUser, boolean flagAdmin) {
        lstUser.add(new UserInfo(idUser, null, flagAdmin, List.of(), List.of()));
        return this;
    }


    FakeNavClient withContract(String idContract, String idPackage, String nameEntity,
            String nameField) {
        DamlValue.Rec payload = new DamlValue.Rec(null,
                List.of(new DamlValue.Rec.Field(nameField, new DamlValue.Text("v"))));
        lstContract.add(new Contract(idContract, "#ev:" + idContract,
                new DataId(idPackage, "Main", nameEntity), payload, List.of("bank"), List.of(),
                Optional.empty(), "0000001", Optional.empty()));
        return this;
    }


    FakeNavClient failingParties(RuntimeException ex) {
        this.exParties = ex;
        return this;
    }


    FakeNavClient failingUsers(RuntimeException ex) {
        this.exUsers = ex;
        return this;
    }


    FakeNavClient failingContracts(RuntimeException ex) {
        this.exContracts = ex;
        return this;
    }


    @Override
    public LedgerInfo info() {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public List<PartyInfo> parties() {
        if (exParties != null)
            throw exParties;
        return List.copyOf(lstParty);
    }


    @Override
    public List<UserInfo> users() {
        cntCallUsers++;
        if (exUsers != null)
            throw exUsers;
        return List.copyOf(lstUser);
    }


    private final java.util.Map<String, byte[]> mapArchive = new java.util.LinkedHashMap<>();

    private RuntimeException exPackages;


    /**
     * @param idPackage the package id the participant will report
     * @param arrArchive the bytes it will hand back for it
     */
    void armPackage(String idPackage, byte[] arrArchive) {
        mapArchive.put(idPackage, arrArchive);
    }


    /**
     * @param ex what packageIds() throws instead of answering. A refusal is not
     *           an empty ledger and a caller has to be able to tell them apart
     */
    void armPackagesFailure(RuntimeException ex) {
        this.exPackages = ex;
    }


    @Override
    public List<String> packageIds() {
        if (exPackages != null)
            throw exPackages;

        return List.copyOf(mapArchive.keySet());
    }


    @Override
    public Optional<byte[]> archive(String idPackage) {
        return Optional.ofNullable(mapArchive.get(idPackage));
    }


    @Override
    public List<Contract> activeContracts(ContractQuery query) {
        cntCallContracts++;
        queryLast = query;
        if (exContracts != null)
            throw exContracts;

        List<Contract> lstOut = new ArrayList<>();
        for (Contract contract : lstContract) {
            if (lstOut.size() >= query.cntLimit())
                break;
            lstOut.add(contract);
        }
        return List.copyOf(lstOut);
    }


    @Override
    public Optional<Contract> contract(String idContract, List<String> lstPartyRead) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public Optional<TxTree> tree(String idUpdate, List<String> lstPartyRead) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public Optional<TxTree> treeByEvent(String idEvent, List<String> lstPartyRead) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public SubmitResult submit(Command cmd, SubmitContext ctx) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public PartyInfo allocateParty(String hintParty, String nameDisplay) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public UserInfo createUser(String idUser, String idPartyPrimary, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public void close() {
        // Nothing to close.
    }


    static LedgerException refused(String strMessage) {
        return new LedgerException(strMessage, "PERMISSION_DENIED", null);
    }

}
