// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

/**
 * One thing a translation target may or may not be able to do.
 *
 * The list is deliberately METHOD-SHAPED where a method exists on
 * LedgerClient_i, and difference-shaped where two Canton generations are known
 * to disagree. It is not a taxonomy of Canton; it is the set of claims this
 * project makes about its own targets.
 *
 * Adding a member here breaks every Capabilities.build() in the reactor until
 * each target says something about it. That is the intended cost: an
 * unmeasured capability is cheap to write down and expensive to discover.
 *
 * NOTHING above the translation layer may branch on a capability to choose an
 * algorithm. A red check is never fixed with a
 * version conditional. A capability exists so a refusal can be reported
 * honestly, not so raposza-render can grow an if.
 *
 * Author Claude/bentzn
 */
public enum Capability {

    /** Participant identity and version - LedgerClient_i.info(). */
    LEDGER_IDENTITY("ledger.identity"),

    /** Enumerate parties - LedgerClient_i.parties(). */
    PARTY_LIST("party.list"),

    /** Allocate a party - LedgerClient_i.allocateParty(). */
    PARTY_ALLOCATE("party.allocate"),

    /** Enumerate ledger users - LedgerClient_i.users(). */
    USER_LIST("user.list"),

    /** Create a ledger user with rights - LedgerClient_i.createUser(). */
    USER_CREATE("user.create"),

    /** Enumerate package ids - LedgerClient_i.packageIds(). */
    PACKAGE_LIST("package.list"),

    /** Fetch one archive - LedgerClient_i.archive(). */
    PACKAGE_FETCH("package.fetch"),

    /**
     * A package is addressable by name and version rather than only by content
     * hash. Canton 3.x smart contract upgrades; nothing on 2.x.
     */
    PACKAGE_NAME_IDENTITY("package.nameIdentity"),

    /** Active contract set - LedgerClient_i.activeContracts(). */
    CONTRACT_ACTIVE("contract.active"),

    /** Fetch one contract by id - LedgerClient_i.contract(). */
    CONTRACT_BY_ID("contract.byId"),

    /** Contract keys are reported on created events. */
    CONTRACT_KEY("contract.key"),

    /** ContractQuery may filter by interface id rather than template id. */
    INTERFACE_FILTER("filter.interface"),

    /** Transaction tree by update id - LedgerClient_i.tree(). */
    UPDATE_BY_ID("update.byId"),

    /** Transaction tree by event id - LedgerClient_i.treeByEvent(). */
    UPDATE_BY_EVENT_ID("update.byEventId"),

    /**
     * Every transaction in a bounded offset window - LedgerClient_i.updates().
     *
     * BOUNDED is the claim. An unbounded read back to genesis is a different
     * thing and this is not it: a target declaring this says it will scan
     * between two offsets under a cap and report whether the cap bit.
     */
    UPDATE_STREAM("update.stream"),

    /**
     * Offsets are numeric and orderable as integers rather than opaque
     * strings. The single sharpest 2.x/3.x difference, and the first real test
     * of whether this declaration shape carries its weight.
     */
    OFFSET_NUMERIC("offset.numeric"),

    /** Submit and await a command - LedgerClient_i.submit(). */
    COMMAND_SUBMIT("command.submit"),

    /** Read one user by id - LedgerClient_i.user(). */
    USER_BY_ID("user.byId"),

    /** Delete a ledger user - LedgerClient_i.deleteUser(). */
    USER_DELETE("user.delete"),

    /**
     * Grant and revoke rights on an existing user -
     * LedgerClient_i.grantUserRights() and revokeUserRights().
     *
     * ONE capability for both directions. A participant that serves
     * GrantUserRights and refuses RevokeUserRights has not been observed and
     * would be a defect rather than a generation difference; splitting it would
     * invite a target to declare half an answer.
     */
    USER_RIGHTS_UPDATE("user.rightsUpdate"),

    /** The current ledger end as an offset - LedgerClient_i.ledgerEnd(). */
    LEDGER_END("ledger.end"),

    /**
     * Prune the participant up to an offset - LedgerClient_i.prune().
     *
     * IRREVERSIBLE, and the only capability here that destroys history. A
     * target declaring it UNMEASURED is refusing, which is the safe direction.
     */
    PARTICIPANT_PRUNE("participant.prune");


    private final String strId;


    private Capability(String strId) {
        this.strId = strId;
    }


    /**
     * Stable external name. Enum constant names are Java's; this is what goes
     * into a compatibility matrix, a report file or a log line, and it does not
     * change when a constant is renamed.
     *
     * @return the wire-stable identifier
     */
    public String strId() {
        return strId;
    }

}
