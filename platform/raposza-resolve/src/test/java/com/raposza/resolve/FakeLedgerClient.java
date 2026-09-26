// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A ledger that answers exactly what a test told it to.
 *
 * Hand-written rather than mocked: the chain's behaviour depends on WHICH calls
 * are made and in what order, and the counters below make that assertable. A
 * mocking framework would hide the thing under test.
 *
 * Every method a test has not armed either returns empty or throws. Nothing
 * returns a plausible default, because a probe that quietly succeeds against a
 * default is the failure this whole module exists to prevent.
 *
 * Author Claude/bentzn
 */
final class FakeLedgerClient implements LedgerClient_i {

    private final List<PartyInfo> lstParty = new ArrayList<>();
    private final List<Contract> lstContract = new ArrayList<>();
    private final List<TxTree> lstTree = new ArrayList<>();

    private LedgerException exParties;
    private LedgerException exContract;
    private LedgerException exTree;

    int cntCallParties;
    int cntCallContract;
    int cntCallTree;


    FakeLedgerClient withParty(String idParty) {
        lstParty.add(new PartyInfo(idParty, "", true));
        return this;
    }


    FakeLedgerClient withContract(String idContract) {
        lstContract.add(new Contract(idContract, "#ev:" + idContract,
                new DataId("pkg", "Main", "Account"), new DamlValue.Rec(null, List.of()),
                List.of("bank"), List.of(), Optional.empty(), "", Optional.empty()));
        return this;
    }


    FakeLedgerClient withTree(String idUpdate) {
        lstTree.add(new TxTree(idUpdate, Optional.empty(), Optional.empty(), "0000001",
                Instant.EPOCH, List.of()));
        return this;
    }


    FakeLedgerClient failingParties(LedgerException ex) {
        this.exParties = ex;
        return this;
    }


    FakeLedgerClient failingContract(LedgerException ex) {
        this.exContract = ex;
        return this;
    }


    FakeLedgerClient failingTree(LedgerException ex) {
        this.exTree = ex;
        return this;
    }


    @Override
    public List<PartyInfo> parties() {
        cntCallParties++;
        if (exParties != null)
            throw exParties;
        return List.copyOf(lstParty);
    }


    @Override
    public Optional<Contract> contract(String idContract, List<String> lstPartyRead) {
        cntCallContract++;
        if (exContract != null)
            throw exContract;
        return lstContract.stream().filter(c -> c.idContract().equals(idContract)).findFirst();
    }


    @Override
    public Optional<TxTree> tree(String idUpdate, List<String> lstPartyRead) {
        cntCallTree++;
        if (exTree != null)
            throw exTree;
        return lstTree.stream().filter(t -> t.idUpdate().equals(idUpdate)).findFirst();
    }


    /**
     * Armed from the same list as tree(), keyed on the event id the fake hands
     * out with each contract. A probe reaching for a transaction by event id
     * must find the same transaction it would have found by update id.
     */
    @Override
    public Optional<TxTree> treeByEvent(String idEvent, List<String> lstPartyRead) {
        cntCallTree++;
        if (exTree != null)
            throw exTree;
        return lstTree.stream().filter(t -> idEvent.endsWith(t.idUpdate())).findFirst();
    }


    @Override
    public LedgerInfo info() {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public List<UserInfo> users() {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public List<String> packageIds() {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public Optional<byte[]> archive(String idPackage) {
        throw new UnsupportedOperationException("not armed");
    }


    @Override
    public List<Contract> activeContracts(ContractQuery query) {
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
    }

}
