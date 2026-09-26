// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import com.raposza.api.LedgerException;
import com.raposza.api.model.Command;
import com.raposza.api.model.Contract;
import com.raposza.api.model.ContractQuery;
import com.raposza.api.model.DataId;
import com.raposza.api.model.LedgerInfo;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.SubmitContext;
import com.raposza.api.model.SubmitResult;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UpdateScan;
import com.raposza.api.model.UserInfo;

import java.util.List;
import java.util.Optional;

/**
 * Everything the two panes need from a participant, expressed without any
 * Ledger API type. raposza-target-2x implements this over Ledger API v1;
 * raposza-target-3x implements it over v2 without this file changing.
 *
 * It lives here rather than in raposza-model. It is a contract a
 * translation target IMPLEMENTS, not a type the domain speaks, and leaving
 * it beside the model would make "what does a new Canton version cost" a
 * question about a module that must never change.
 *
 * Implementations are not required to be thread safe. The GUI owns one
 * instance per open connection and calls it from a worker thread.
 *
 * Author Claude/bentzn
 */
public interface LedgerClient_i extends AutoCloseable {

    /**
     * @return identity and version of the connected participant
     */
    LedgerInfo info();


    /**
     * @return all parties known to the participant
     */
    List<PartyInfo> parties();


    /**
     * @return all users on the participant; requires an admin token
     */
    List<UserInfo> users();


    /**
     * Package ids the participant holds.
     *
     * The BYTES are what a client offers; decoding them is somewhere else
     * entirely. A client module knows a Ledger API generation and nothing about
     * Daml-LF, so returning a decoded registry from here would make decoding a
     * property of the transport and oblige the second generation to carry its
     * own copy of a decision that has nothing to do with the wire.
     *
     * @return every package id, in the order the participant reports them
     * @throws LedgerException when the participant refuses to list them, which
     *         is a different claim from a participant holding none
     */
    List<String> packageIds();


    /**
     * One archive, exactly as the package service returns it.
     *
     * These bytes are an ArchivePayload, NOT a DAR and NOT a DamlLf.Archive,
     * and the package id is their SHA-256 - so the fetch verifies itself and a
     * caller can check what it received against what it asked for.
     *
     * @param idPackage the package id
     * @return the raw archive, empty when the participant does not hold it
     * @throws LedgerException on transport failure
     */
    Optional<byte[]> archive(String idPackage);



    /**
     * @param query the filter
     * @return matching active contracts
     */
    List<Contract> activeContracts(ContractQuery query);


    /**
     * The identifier to put in a template FILTER, which is not always the one
     * that was resolved.
     *
     * <h2>THE TARGET DECIDES, because the wire format is the target's</h2>
     *
     * Whether a participant accepts a package-NAME reference in a filter is a
     * property of the participant, not of the package. Those two questions
     * coincide on 3.x and come apart on 2.x, and reading the package was
     * wrong: a 2.x participant answers `PACKAGE_NAMES_NOT_FOUND` for a name
     * the package genuinely carries. The name exists; the participant will
     * not take it.
     *
     * So the caller resolves the name - it owns the registry - and the target
     * says what to do with it. {@link Capability#PACKAGE_NAME_IDENTITY} is the
     * declaration of the same fact, and this is the behaviour that follows from
     * it.
     *
     * <h2>The DEFAULT is the hash, and that is the safe direction</h2>
     *
     * A content hash is what a participant accepts unless it says otherwise. A
     * target that needs the name form overrides this and says so; one that
     * forgets sends a hash, which is refused loudly on 3.x rather than
     * returning the wrong contracts.
     *
     * @param idTemplate the resolved template identifier
     * @param nameOptPackage the package's name, empty when it has none
     * @return the identifier to filter by; never null
     */
    default DataId idFilter(DataId idTemplate, Optional<String> nameOptPackage) {
        return idTemplate;
    }


    /**
     * @param idContract the contract id
     * @param lstPartyRead parties to read as
     * @return the contract, empty when not visible or unknown
     */
    Optional<Contract> contract(String idContract, List<String> lstPartyRead);


    /**
     * @param idUpdate the update (transaction) id
     * @param lstPartyRead parties to read as
     * @return the transaction tree, empty when not visible or unknown
     */
    Optional<TxTree> tree(String idUpdate, List<String> lstPartyRead);


    /**
     * The transaction that produced a given event, which is how a contract
     * reaches the transaction that created it.
     *
     * An event id is treated as an opaque handle. It is passed back to the
     * participant, never parsed: the id carries a transaction id inside it on
     * the generations sampled so far, and splitting the string would be an
     * assumption about a format nobody has committed to.
     *
     * @param idEvent the event id, as the participant reported it
     * @param lstPartyRead parties to read as
     * @return the transaction tree, empty when not visible or unknown
     */
    Optional<TxTree> treeByEvent(String idEvent, List<String> lstPartyRead);


    /**
     * Every transaction in a bounded offset window, as trees.
     *
     * This is what a contract's activity is read from. The window is CLOSED at
     * both ends on purpose: an open-ended read against a live participant does
     * not end, it follows the ledger, and a pane waiting for a stream that will
     * never complete is indistinguishable from one that hung.
     *
     * Offsets are INCLUSIVE at both ends here, whatever the wire underneath
     * calls them. A caller asking from a contract's create offset expects the
     * create, and making that caller subtract one would be this interface
     * leaking a generation's convention.
     *
     * The read is capped and the cap is REPORTED rather than swallowed. A
     * truncated scan that does not say so turns "this contract was never
     * exercised" into a lie.
     *
     * @param offsetFromInclusive where to start, blank or null for the
     *        beginning of the ledger
     * @param offsetToInclusive where to stop, blank or null for the current
     *        ledger end
     * @param lstPartyRead parties to read as; the trees carry only what they
     *        witnessed, so this is not a formality
     * @param cntLimit the most responses to take, must be positive
     * @return the transactions in offset order, with the window that was
     *         actually scanned and whether the cap stopped the read
     * @throws UnsupportedCapability when the target implements no bounded scan
     * @throws LedgerException on transport failure
     */
    default UpdateScan updates(String offsetFromInclusive, String offsetToInclusive,
            List<String> lstPartyRead, int cntLimit) {
        throw new UnsupportedCapability(Capability.UPDATE_STREAM, getClass().getSimpleName(),
                "no bounded update scan is implemented on this target");
    }


    /**
     * The Act pane. Submits and waits for the transaction so the result can be
     * shown in the same window.
     *
     * A ledger rejection is a RESULT, not an exception. So is a submission
     * whose completion never arrived - SubmitResult.Outcome has three values
     * and UNKNOWN is one of them, because this interface cannot honestly claim
     * either state after a timeout. Only a transport or protocol failure
     * throws.
     *
     * @param cmd the command
     * @param ctx submitting identity
     * @return acceptance with the resulting tree, or rejection with the error code
     * @throws LedgerException on transport failure
     * @throws IllegalStateException when the profile is read only
     */
    SubmitResult submit(Command cmd, SubmitContext ctx);


    /**
     * Allocates a party.
     *
     * This is PartyManagementService on the Ledger API, not the Canton admin
     * port, so it takes no dependency on admin-port access. It is nonetheless
     * a privileged operation and a WRITE for profile purposes: READ_ONLY
     * refuses it, because that mode is a promise not to change the participant
     * and allocating a party changes it.
     *
     * @param hintParty the party id hint, or null to let the participant choose
     * @param nameDisplay display name, or null for none
     * @return the allocated party as the participant reports it
     * @throws LedgerException on refusal or transport failure
     * @throws IllegalStateException when the profile is read only
     */
    PartyInfo allocateParty(String hintParty, String nameDisplay);


    /**
     * Creates a ledger user with the given rights.
     *
     * UserManagementService, and privileged for the same reason and with the
     * same READ_ONLY refusal as allocateParty. A user created WITH RIGHTS is
     * emphatically an administrative act.
     *
     * @param idUser the user id
     * @param idPartyPrimary primary party, or null for none
     * @param lstPartyAct parties the user may act as, may be empty
     * @param lstPartyRead parties the user may read as, may be empty
     * @return the user as the participant reports it
     * @throws LedgerException on refusal or transport failure
     * @throws IllegalStateException when the profile is read only
     */
    UserInfo createUser(String idUser, String idPartyPrimary, List<String> lstPartyAct,
            List<String> lstPartyRead);


    /**
     * One user by id.
     *
     * A READ, so READ_ONLY does not refuse it - but it is still
     * UserManagementService and still needs an admin token.
     *
     * @param idUser the user id
     * @return the user, empty when the participant does not hold one by that id
     * @throws UnsupportedCapability when the target does not implement it
     * @throws LedgerException on transport failure
     */
    default UserInfo user(String idUser) {
        throw new UnsupportedCapability(Capability.USER_BY_ID, getClass().getSimpleName(),
                "no single-user read is implemented on this target");
    }


    /**
     * Deletes a ledger user.
     *
     * A WRITE, refused by READ_ONLY for the same reason as createUser. It does
     * NOT touch the parties the user could act as: a user is a credential and
     * the parties outlive it.
     *
     * @param idUser the user id
     * @throws UnsupportedCapability when the target does not implement it
     * @throws LedgerException on refusal or transport failure
     * @throws IllegalStateException when the profile is read only
     */
    default void deleteUser(String idUser) {
        throw new UnsupportedCapability(Capability.USER_DELETE, getClass().getSimpleName(),
                "no user delete is implemented on this target");
    }


    /**
     * Grants act-as and read-as rights on an existing user.
     *
     * The participant reports what was NEWLY granted, which is not the same as
     * what was asked for - a right the user already held comes back absent. The
     * distinction is preserved rather than flattened, because a caller cannot
     * otherwise tell "granted" from "already had it".
     *
     * @param idUser the user id
     * @param lstPartyAct parties to add as actAs, may be empty
     * @param lstPartyRead parties to add as readAs, may be empty
     * @return the rights the participant reports as newly granted
     * @throws UnsupportedCapability when the target does not implement it
     * @throws LedgerException on refusal or transport failure
     * @throws IllegalStateException when the profile is read only
     */
    default UserInfo grantUserRights(String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        throw new UnsupportedCapability(Capability.USER_RIGHTS_UPDATE,
                getClass().getSimpleName(), "no rights grant is implemented on this target");
    }


    /**
     * Revokes act-as and read-as rights from an existing user.
     *
     * @param idUser the user id
     * @param lstPartyAct parties to remove from actAs, may be empty
     * @param lstPartyRead parties to remove from readAs, may be empty
     * @return the rights the participant reports as newly revoked
     * @throws UnsupportedCapability when the target does not implement it
     * @throws LedgerException on refusal or transport failure
     * @throws IllegalStateException when the profile is read only
     */
    default UserInfo revokeUserRights(String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        throw new UnsupportedCapability(Capability.USER_RIGHTS_UPDATE,
                getClass().getSimpleName(), "no rights revoke is implemented on this target");
    }


    /**
     * The current ledger end, as a STRING on both generations.
     *
     * MEASURED, and the reason the return type is not a long: v1
     * GetLedgerEndResponse carries a LedgerOffset whose absolute form is a
     * string, and v2 carries an int64. Text is the one shape both fit, it
     * round-trips through prune() on 2.x untouched, and it parses on 3.x.
     * {@link Capability#OFFSET_NUMERIC} is where a caller asks whether the
     * string is orderable as a number; this method does not pretend to answer
     * that.
     *
     * @return the offset, as the participant reports it
     * @throws UnsupportedCapability when the target does not implement it
     * @throws LedgerException on transport failure
     */
    default String ledgerEnd() {
        throw new UnsupportedCapability(Capability.LEDGER_END, getClass().getSimpleName(),
                "no ledger end read is implemented on this target");
    }


    /**
     * Prunes the participant up to and including an offset.
     *
     * IRREVERSIBLE. A WRITE in the strongest sense the profile knows, so
     * READ_ONLY refuses it.
     *
     * The offset is the string form ledgerEnd() returns, for the reason given
     * there. A target over a numeric wire parses it and fails the call rather
     * than pruning a coerced value.
     *
     * @param offsetUpToInclusive the offset to prune up to, inclusive
     * @param flagDivulged whether to prune divulged contracts too
     * @throws UnsupportedCapability when the target does not implement it
     * @throws LedgerException on refusal or transport failure
     * @throws IllegalStateException when the profile is read only
     */
    default void prune(String offsetUpToInclusive, boolean flagDivulged) {
        throw new UnsupportedCapability(Capability.PARTICIPANT_PRUNE,
                getClass().getSimpleName(), "no pruning is implemented on this target");
    }


    @Override
    void close();

}
