// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import com.raposza.api.LedgerException;
import com.raposza.api.TokenSource_i;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.model.Command;
import com.raposza.api.model.Contract;
import com.raposza.api.model.ContractQuery;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.LedgerInfo;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.SubmitContext;
import com.raposza.api.model.SubmitResult;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UserInfo;
import com.raposza.api.profile.HostProfile;

import com.raposza.spi.LedgerClient_i;
import com.raposza.wire.CantonError;
import com.raposza.wire.ProtoValues;
import com.raposza.wire.ValueText;

import com.daml.ledger.api.v1.ActiveContractsServiceGrpc;
import com.daml.ledger.api.v1.ActiveContractsServiceOuterClass;
import com.daml.ledger.api.v1.CommandServiceGrpc;
import com.daml.ledger.api.v1.CommandServiceOuterClass;
import com.daml.ledger.api.v1.CommandsOuterClass;
import com.daml.ledger.api.v1.EventOuterClass;
import com.daml.ledger.api.v1.LedgerIdentityServiceGrpc;
import com.daml.ledger.api.v1.LedgerIdentityServiceOuterClass;
import com.daml.ledger.api.v1.PackageServiceGrpc;
import com.daml.ledger.api.v1.PackageServiceOuterClass;
import com.daml.ledger.api.v1.TransactionFilterOuterClass;
import com.daml.ledger.api.v1.TransactionOuterClass;
import com.daml.ledger.api.v1.TransactionServiceGrpc;
import com.daml.ledger.api.v1.TransactionServiceOuterClass;
import com.daml.ledger.api.v1.ValueOuterClass;
import com.daml.ledger.api.v1.VersionServiceGrpc;
import com.daml.ledger.api.v1.VersionServiceOuterClass;
import com.daml.ledger.api.v1.LedgerOffsetOuterClass;
import com.daml.ledger.api.v1.admin.ParticipantPruningServiceGrpc;
import com.daml.ledger.api.v1.admin.ParticipantPruningServiceOuterClass;
import com.daml.ledger.api.v1.admin.PartyManagementServiceGrpc;
import com.daml.ledger.api.v1.admin.PartyManagementServiceOuterClass;
import com.daml.ledger.api.v1.admin.UserManagementServiceGrpc;
import com.daml.ledger.api.v1.admin.UserManagementServiceOuterClass;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Ledger API v1 client, for Canton 2.x.
 *
 * Implements the identity and party reads, and the active contract reads. The
 * identity reads came first deliberately: they exercise the channel, TLS and
 * the token source end to end against messages with no dynamic values in them,
 * so an authentication problem cannot be mistaken for a decoding problem.
 *
 * Value conversion is NOT done here. It lives in raposza-wire, which works
 * off protobuf descriptors so that the same code serves Ledger API v2 at P5 -
 * the two binding jars cannot share a classpath, so a converter written against
 * generated types would have to exist twice.
 *
 * Everything else throws until its increment lands, rather than returning empty
 * - an empty result is indistinguishable from a ledger with nothing on it.
 *
 * Author Claude/bentzn
 */
public final class Lapi1Client implements LedgerClient_i {

    private static final Logger LOG = LoggerFactory.getLogger(Lapi1Client.class);

    /** Users requested per page. The participant may return fewer. */
    private static final int CNT_PAGE_USER = 1000;

    /** Refuse to follow more pages than this; a runaway token is a bug, not a big ledger. */
    private static final int CNT_PAGE_MAX = 1000;

    private final LedgerChannel channel;
    private final String idLedger;


    /**
     * @param profile the connection profile
     * @param source token supply, or null for an unauthenticated ledger
     * @throws LedgerException when the participant cannot be reached
     */
    public Lapi1Client(HostProfile profile, TokenSource_i source) {
        this.channel = new LedgerChannel(profile, source);
        this.idLedger = fetchLedgerId();
    }


    /**
     * The ledger id is fetched once and cached: several v1 requests carry it,
     * and it does not change for the life of a participant.
     *
     * LedgerIdentityService is deprecated in later 2.x. A participant that has
     * removed it yields an empty id rather than a failure, because the reads
     * that need it will say so more usefully than a failure at connect time.
     */
    private String fetchLedgerId() {
        try {
            LedgerIdentityServiceOuterClass.GetLedgerIdentityResponse rsp =
                    LedgerIdentityServiceGrpc.newBlockingStub(channel.authenticated())
                        .getLedgerIdentity(LedgerIdentityServiceOuterClass
                                .GetLedgerIdentityRequest.getDefaultInstance());
            return rsp.getLedgerId();
        }
        catch (StatusRuntimeException ex) {
            return "";
        }
    }


    @Override
    public LedgerInfo info() {
        try {
            VersionServiceOuterClass.GetLedgerApiVersionResponse rsp =
                    VersionServiceGrpc.newBlockingStub(channel.authenticated())
                        .getLedgerApiVersion(VersionServiceOuterClass
                                .GetLedgerApiVersionRequest.newBuilder()
                                .setLedgerId(idLedger)
                                .build());

            return new LedgerInfo(idLedger, participantId(), rsp.getVersion(),
                    ApiGeneration.V1);
        }
        catch (StatusRuntimeException ex) {
            throw failed("read the ledger API version", ex);
        }
    }


    private String participantId() {
        try {
            PartyManagementServiceOuterClass.GetParticipantIdResponse rsp =
                    PartyManagementServiceGrpc.newBlockingStub(channel.authenticated())
                        .getParticipantId(PartyManagementServiceOuterClass
                                .GetParticipantIdRequest.getDefaultInstance());
            return rsp.getParticipantId();
        }
        catch (StatusRuntimeException ex) {
            // Needs an admin token. A read-only operator should still see a
            // version and a party list, so this degrades rather than fails.
            return "";
        }
    }


    /**
     * On 2.x an allocation hint lands in displayName and the party id itself is
     * opaque; on 3.x it is the other way round. PartyInfo carries both and
     * label() picks, so nothing above here needs to know which generation it is
     * talking to.
     */
    @Override
    public List<PartyInfo> parties() {
        try {
            PartyManagementServiceOuterClass.ListKnownPartiesResponse rsp =
                    PartyManagementServiceGrpc.newBlockingStub(channel.authenticated())
                        .listKnownParties(PartyManagementServiceOuterClass
                                .ListKnownPartiesRequest.getDefaultInstance());

            List<PartyInfo> lstParty = new ArrayList<>();
            for (PartyManagementServiceOuterClass.PartyDetails details : rsp.getPartyDetailsList()) {
                lstParty.add(new PartyInfo(details.getParty(), details.getDisplayName(),
                        details.getIsLocal()));
            }
            return List.copyOf(lstParty);
        }
        catch (StatusRuntimeException ex) {
            throw failed("list known parties", ex);
        }
    }


    /**
     * Lists the participant's users with the rights each one holds.
     *
     * Two calls per user, not one: ListUsers returns the user record, and the
     * rights come only from ListUserRights. That is N+1 round trips and it is
     * the API's shape, not a choice made here - a participant with hundreds of
     * users will feel it, and paging the GUI is the answer if it ever matters.
     *
     * @return every user, in the order the participant reports them
     * @throws LedgerException when the service is absent, the token lacks admin
     *         rights, or any page fails. An empty list means a participant with
     *         no users, which is a different claim and must stay one
     */
    @Override
    public List<UserInfo> users() {
        UserManagementServiceGrpc.UserManagementServiceBlockingStub stub =
                UserManagementServiceGrpc.newBlockingStub(channel.authenticated());

        List<UserManagementServiceOuterClass.User> lstUser = new ArrayList<>();
        String tokenPage = "";
        int cntPage = 0;

        try {
            do {
                UserManagementServiceOuterClass.ListUsersResponse rsp = stub.listUsers(
                        UserManagementServiceOuterClass.ListUsersRequest.newBuilder()
                            .setPageToken(tokenPage).setPageSize(CNT_PAGE_USER).build());

                lstUser.addAll(rsp.getUsersList());

                String tokenNext = rsp.getNextPageToken();
                // A participant that keeps handing back the same token would
                // otherwise loop until the heap gives out. Bounded rather than
                // trusted.
                if (!tokenNext.isEmpty() && tokenNext.equals(tokenPage)) {
                    throw new LedgerException("user listing on '" + channel.profile().nameDisplay()
                            + "' repeated page token '" + tokenPage + "'; pagination is not"
                            + " advancing");
                }
                tokenPage = tokenNext;

                if (++cntPage > CNT_PAGE_MAX) {
                    throw new LedgerException("user listing on '" + channel.profile().nameDisplay()
                            + "' exceeded " + CNT_PAGE_MAX + " pages; refusing to keep reading");
                }
            }
            while (!tokenPage.isEmpty());

            List<UserInfo> lstInfo = new ArrayList<>(lstUser.size());
            for (UserManagementServiceOuterClass.User user : lstUser) {
                lstInfo.add(toUser(user, stub));
            }
            return List.copyOf(lstInfo);
        }
        catch (StatusRuntimeException ex) {
            throw failed("list users", ex);
        }
    }


    /**
     * Rights arrive as a oneof per grant. An unrecognised kind is IGNORED here
     * rather than fatal - unlike a Value variant, a right this build does not
     * know does not corrupt what it does know, and a participant that grows a
     * right kind should not stop the user list from rendering. The trade is
     * deliberate and goes the other way from ProtoValues.
     */
    private static UserInfo toUser(UserManagementServiceOuterClass.User user,
            UserManagementServiceGrpc.UserManagementServiceBlockingStub stub) {
        UserManagementServiceOuterClass.ListUserRightsResponse rsp = stub.listUserRights(
                UserManagementServiceOuterClass.ListUserRightsRequest.newBuilder()
                    .setUserId(user.getId()).build());

        return toUser(user, rsp.getRightsList());
    }


    /**
     * Split out from the call so the mapping is testable without a participant.
     */
    private static UserInfo toUser(UserManagementServiceOuterClass.User user,
            List<UserManagementServiceOuterClass.Right> lstRight) {
        List<String> lstPartyAct = new ArrayList<>();
        List<String> lstPartyRead = new ArrayList<>();
        boolean flagAdmin = false;

        for (UserManagementServiceOuterClass.Right right : lstRight) {
            switch (right.getKindCase()) {
                case CAN_ACT_AS:
                    lstPartyAct.add(right.getCanActAs().getParty());
                    break;

                case CAN_READ_AS:
                    lstPartyRead.add(right.getCanReadAs().getParty());
                    break;

                case PARTICIPANT_ADMIN:
                    flagAdmin = true;
                    break;

                default:
                    break;
            }
        }

        // Protobuf yields "" for an unset primary party; the model says null.
        String idPartyPrimary = user.getPrimaryParty().isEmpty() ? null : user.getPrimaryParty();

        return new UserInfo(user.getId(), idPartyPrimary, flagAdmin, List.copyOf(lstPartyAct),
                List.copyOf(lstPartyRead));
    }


    /**
     * ListPackages returns package ids and NOTHING else - no name, no LF
     * version, no size. Anything more comes from decoding the archive, which is
     * not this module's business.
     */
    @Override
    public List<String> packageIds() {
        try {
            PackageServiceOuterClass.ListPackagesResponse rsp =
                    PackageServiceGrpc.newBlockingStub(channel.authenticated())
                        .listPackages(PackageServiceOuterClass.ListPackagesRequest.newBuilder()
                                .setLedgerId(idLedger)
                                .build());
            return List.copyOf(rsp.getPackageIdsList());
        }
        catch (StatusRuntimeException ex) {
            throw failed("list packages", ex);
        }
    }


    /**
     * A package id the participant does not hold answers NOT_FOUND, which is an
     * absence rather than a failure and comes back empty. Everything else is a
     * failure and is reported as one.
     */
    @Override
    public Optional<byte[]> archive(String idPackage) {
        if (idPackage == null || idPackage.isBlank())
            return Optional.empty();

        try {
            PackageServiceOuterClass.GetPackageResponse rsp =
                    PackageServiceGrpc.newBlockingStub(channel.authenticated())
                        .getPackage(PackageServiceOuterClass.GetPackageRequest.newBuilder()
                                .setLedgerId(idLedger)
                                .setPackageId(idPackage)
                                .build());
            return Optional.of(rsp.getArchivePayload().toByteArray());
        }
        catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND)
                return Optional.empty();

            throw failed("fetch package " + idPackage, ex);
        }
    }


    /**
     * Reads the active contract set as of the current ledger end.
     *
     * @param query parties, template filter, text filter and cap
     * @return contracts, at most query.cntLimit of them
     * @throws LedgerException on transport failure, never to signal emptiness
     */
    @Override
    public List<Contract> activeContracts(ContractQuery query) {
        if (query.lstPartyRead().isEmpty())
            throw new LedgerException("a contract query must name at least one party to read as");
        if (query.cntLimit() <= 0)
            throw new LedgerException("a contract query needs a positive limit, got " + query.cntLimit());

        List<Contract> lstContract = new ArrayList<>();
        readAcs(query, contract -> {
            lstContract.add(contract);
            // False stops the stream. The alternative - buffer the whole ACS
            // and sublist it - reads a production participant dry to show ten
            // rows.
            return lstContract.size() < query.cntLimit();
        });
        return List.copyOf(lstContract);
    }


    /**
     * Ledger API v1 has no point lookup by contract id, so this scans the ACS
     * and stops at the match.
     *
     * @param idContract the contract id
     * @param lstPartyRead parties to read as
     * @return the contract, or empty when it is not visible to these parties,
     *         is archived, or does not exist - v1 does not distinguish those
     *         three, so nothing here pretends to
     */
    @Override
    public Optional<Contract> contract(String idContract, List<String> lstPartyRead) {
        if (lstPartyRead.isEmpty())
            throw new LedgerException("a contract lookup must name at least one party to read as");

        ContractQuery query = new ContractQuery(lstPartyRead, List.of(), List.of(), "",
                Integer.MAX_VALUE, Optional.empty());

        List<Contract> lstHit = new ArrayList<>(1);
        readAcs(query, contract -> {
            if (!contract.idContract().equals(idContract))
                return true;
            lstHit.add(contract);
            return false;
        });

        if (lstHit.isEmpty()) {
            // Worth a line: "not yours" and "not there" are different problems
            // for whoever is holding the identifier, and the return type cannot
            // carry the difference. EventQueryService would distinguish them on
            // 2.9+; wire it in when the resolver needs the distinction.
            LOG.debug("contract {} not in the active set for {} on '{}' - not visible,"
                    + " archived and non-existent are indistinguishable on Ledger API v1",
                    idContract, lstPartyRead, channel.profile().nameDisplay());
            return Optional.empty();
        }
        return Optional.of(lstHit.get(0));
    }


    /**
     * Streams the ACS, handing each matching contract to the consumer until it
     * returns false or the stream ends.
     *
     * The RPC is opened inside a cancellable context: a blocking-stub iterator
     * abandoned mid-stream leaves the call running on the participant, which on
     * a large ACS is a leak that only shows up under load.
     */
    private void readAcs(ContractQuery query, Predicate<Contract> onContract) {
        ActiveContractsServiceOuterClass.GetActiveContractsRequest req = acsRequest(query);

        Context.CancellableContext ctx = Context.current().withCancellation();
        Context ctxPrev = ctx.attach();
        try {
            Iterator<ActiveContractsServiceOuterClass.GetActiveContractsResponse> itRsp =
                    ActiveContractsServiceGrpc.newBlockingStub(channel.authenticated())
                        .getActiveContracts(req);

            while (itRsp.hasNext()) {
                // The final message carries the offset and no contracts, so it
                // falls out of this loop without a special case.
                for (EventOuterClass.CreatedEvent ev : itRsp.next().getActiveContractsList()) {
                    Contract contract = toContract(ev);
                    if (!ValueText.contains(contract.payload(), query.strFilter()))
                        continue;
                    if (!onContract.test(contract))
                        return;
                }
            }
        }
        catch (StatusRuntimeException ex) {
            throw failed("read the active contract set", ex);
        }
        finally {
            ctx.detach(ctxPrev);
            ctx.cancel(null);
        }
    }


    /**
     * verbose is set unconditionally. Without it the participant returns
     * neither field labels nor record identifiers, and the payload cannot be
     * rendered at all until the LF decoder exists. That is measured behaviour
     * on 2.9.6, 2.10.2 and 3.4.10, not a precaution - see the OQ3 evidence.
     */
    private ActiveContractsServiceOuterClass.GetActiveContractsRequest acsRequest(
            ContractQuery query) {
        if (!query.lstInterface().isEmpty()) {
            throw new UnsupportedOperationException("interface filters are not implemented;"
                    + " retrieval mechanics are an open design question and silently dropping"
                    + " the filter would return the wrong contracts");
        }

        TransactionFilterOuterClass.Filters.Builder bldFilters =
                TransactionFilterOuterClass.Filters.newBuilder();

        // An empty Filters means every template visible to the party. That
        // holds on v1 and does NOT hold on v2, which needs an explicit
        // wildcard_filter inside cumulative.
        if (!query.lstTemplate().isEmpty()) {
            TransactionFilterOuterClass.InclusiveFilters.Builder bldInc =
                    TransactionFilterOuterClass.InclusiveFilters.newBuilder();
            for (com.raposza.api.model.DataId idTemplate : query.lstTemplate()) {
                bldInc.addTemplateIds(ValueOuterClass.Identifier.newBuilder()
                        .setPackageId(idTemplate.idPackage())
                        .setModuleName(idTemplate.nameModule())
                        .setEntityName(idTemplate.nameEntity()));
            }
            bldFilters.setInclusive(bldInc);
        }

        TransactionFilterOuterClass.TransactionFilter.Builder bldFilter =
                TransactionFilterOuterClass.TransactionFilter.newBuilder();
        for (String idParty : query.lstPartyRead()) {
            bldFilter.putFiltersByParty(idParty, bldFilters.build());
        }

        ActiveContractsServiceOuterClass.GetActiveContractsRequest.Builder bldReq =
                ActiveContractsServiceOuterClass.GetActiveContractsRequest.newBuilder()
                    .setLedgerId(idLedger)
                    .setFilter(bldFilter.build())
                    .setVerbose(true);
        query.offsetAt().ifPresent(bldReq::setActiveAtOffset);
        return bldReq.build();
    }


    /**
     * offsetCreated is empty: the ACS stream reports one offset for the whole
     * snapshot, which is the ledger end it was taken at and NOT the offset of
     * any individual create. Putting that value on every contract would look
     * like data. The transaction tree read supplies the real one.
     *
     * The event id IS carried, and it is per contract. It is the handle
     * treeByEvent uses to reach the create's transaction; a participant that
     * reports none yields an empty string and a contract that cannot be traced,
     * which the caller can see rather than having to discover.
     */
    private static Contract toContract(EventOuterClass.CreatedEvent ev) {
        Optional<DamlValue> key = ev.hasContractKey()
                ? Optional.of(ProtoValues.toValue(ev.getContractKey()))
                : Optional.empty();

        return new Contract(ev.getContractId(), ev.getEventId(),
                ProtoValues.toDataId(ev.getTemplateId()),
                ProtoValues.toRecord(ev.getCreateArguments()), List.copyOf(ev.getSignatoriesList()),
                List.copyOf(ev.getObserversList()), key, "", Optional.empty());
    }


    /**
     * Reads one transaction as a tree.
     *
     * Ledger API v1 calls this a transaction id; v2 calls the same thing an
     * update id. The model uses the v2 word throughout so that the name does
     * not change when P5 lands - only this method knows the difference.
     *
     * @param idUpdate the transaction id
     * @param lstPartyRead parties to read as; the tree shows only what they
     *        witnessed, so this is not a formality
     * @return the tree, or empty when the transaction is unknown to these
     *         parties. NOT_FOUND covers both "no such transaction" and "not
     *         yours", which the participant deliberately does not distinguish
     * @throws LedgerException on any other transport or protocol failure
     */
    @Override
    public Optional<TxTree> tree(String idUpdate, List<String> lstPartyRead) {
        if (lstPartyRead.isEmpty())
            throw new LedgerException("a transaction read must name at least one party to read as");
        if (idUpdate == null || idUpdate.isBlank())
            throw new LedgerException("no transaction id supplied");

        try {
            TransactionServiceOuterClass.GetTransactionResponse rsp =
                    TransactionServiceGrpc.newBlockingStub(channel.authenticated())
                        .getTransactionById(TransactionServiceOuterClass.GetTransactionByIdRequest
                                .newBuilder().setLedgerId(idLedger).setTransactionId(idUpdate)
                                .addAllRequestingParties(lstPartyRead).build());

            return Optional.of(toTree(rsp.getTransaction()));
        }
        catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                LOG.debug("transaction {} not visible to {} on '{}'", idUpdate, lstPartyRead,
                        channel.profile().nameDisplay());
                return Optional.empty();
            }
            throw failed("read transaction " + idUpdate, ex);
        }
    }


    /**
     * Reads the transaction that produced one event.
     *
     * This is what turns a contract into the transaction that created it: the
     * active contract set reports an event id per contract, and v1 will look a
     * transaction up by it. The id is passed back exactly as received. It looks
     * like a transaction id with a node index appended, and splitting it here
     * would be faster and would be an assumption about a format nobody has
     * committed to - the same reason ContractIdProbe refuses to assert what a
     * contract id looks like.
     *
     * @param idEvent the event id
     * @param lstPartyRead parties to read as
     * @return the tree, or empty when the event is unknown to these parties.
     *         NOT_FOUND covers both "no such event" and "not yours", which the
     *         participant deliberately does not distinguish
     * @throws LedgerException on any other transport or protocol failure
     */
    @Override
    public Optional<TxTree> treeByEvent(String idEvent, List<String> lstPartyRead) {
        if (lstPartyRead.isEmpty())
            throw new LedgerException("a transaction read must name at least one party to read as");
        if (idEvent == null || idEvent.isBlank())
            throw new LedgerException("no event id supplied");

        try {
            TransactionServiceOuterClass.GetTransactionResponse rsp =
                    TransactionServiceGrpc.newBlockingStub(channel.authenticated())
                        .getTransactionByEventId(TransactionServiceOuterClass
                                .GetTransactionByEventIdRequest.newBuilder().setLedgerId(idLedger)
                                .setEventId(idEvent).addAllRequestingParties(lstPartyRead).build());

            return Optional.of(toTree(rsp.getTransaction()));
        }
        catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                LOG.debug("event {} not visible to {} on '{}'", idEvent, lstPartyRead,
                        channel.profile().nameDisplay());
                return Optional.empty();
            }
            throw failed("read transaction for event " + idEvent, ex);
        }
    }


    /**
     * The wire form is a flat map of events plus a list of root ids; the tree
     * shape lives in the child id lists. Rebuilding it here rather than in the
     * renderer keeps the flat form from leaking upwards.
     */
    private static TxTree toTree(TransactionOuterClass.TransactionTree tree) {
        Map<String, TransactionOuterClass.TreeEvent> mapEvent = tree.getEventsByIdMap();

        List<TxNode> lstRoot = new ArrayList<>();
        for (String idEvent : tree.getRootEventIdsList()) {
            lstRoot.add(toNode(idEvent, mapEvent, new HashSet<>()));
        }

        return new TxTree(tree.getTransactionId(), blankToEmpty(tree.getCommandId()),
                blankToEmpty(tree.getWorkflowId()), tree.getOffset(),
                toInstant(tree.getEffectiveAt()), List.copyOf(lstRoot));
    }


    /**
     * A child id with no event behind it, or a cycle, means the tree cannot be
     * rebuilt faithfully. Both fail rather than yielding a partial tree: a
     * transaction rendered with a branch missing looks exactly like one that
     * never had it, which is the wrong answer to "what did this do".
     */
    private static TxNode toNode(String idEvent, Map<String, TransactionOuterClass.TreeEvent> mapEvent,
            Set<String> setSeen) {
        if (!setSeen.add(idEvent)) {
            throw new LedgerException("event " + idEvent + " appears inside its own subtree;"
                    + " the transaction tree is not a tree");
        }

        TransactionOuterClass.TreeEvent ev = mapEvent.get(idEvent);
        if (ev == null) {
            throw new LedgerException("transaction refers to event " + idEvent
                    + " which it does not carry; the tree cannot be rebuilt");
        }

        switch (ev.getKindCase()) {
            case CREATED: {
                EventOuterClass.CreatedEvent created = ev.getCreated();
                return new TxNode.Created(created.getEventId(), created.getContractId(),
                        ProtoValues.toDataId(created.getTemplateId()),
                        ProtoValues.toRecord(created.getCreateArguments()),
                        List.copyOf(created.getSignatoriesList()),
                        List.copyOf(created.getObserversList()));
            }

            case EXERCISED: {
                EventOuterClass.ExercisedEvent exercised = ev.getExercised();
                List<TxNode> lstChild = new ArrayList<>();
                for (String idChild : exercised.getChildEventIdsList()) {
                    lstChild.add(toNode(idChild, mapEvent, setSeen));
                }

                // exercise_result is unset on a choice returning unit, and an
                // unset Value has no variant - which ProtoValues rejects, quite
                // rightly. Absence is modelled as null here rather than as a
                // fabricated Unit, which would be indistinguishable from a
                // choice that really returned ().
                DamlValue valueResult = exercised.hasExerciseResult()
                        ? ProtoValues.toValue(exercised.getExerciseResult())
                        : null;

                return new TxNode.Exercised(exercised.getEventId(), exercised.getContractId(),
                        ProtoValues.toDataId(exercised.getTemplateId()), exercised.getChoice(),
                        exercised.getConsuming(),
                        ProtoValues.toValue(exercised.getChoiceArgument()), valueResult,
                        List.copyOf(exercised.getActingPartiesList()), List.copyOf(lstChild));
            }

            default:
                throw new LedgerException("event " + idEvent + " is neither a create nor an"
                        + " exercise but " + ev.getKindCase()
                        + "; the Ledger API has grown a node kind this build does not know");
        }
    }


    /**
     * Protobuf yields "" for an unset string, which is not the same claim as
     * "the reader may not see it". Optional carries that distinction upwards.
     */
    private static Optional<String> blankToEmpty(String str) {
        return str == null || str.isEmpty() ? Optional.empty() : Optional.of(str);
    }


    private static Instant toInstant(com.google.protobuf.Timestamp stamp) {
        return Instant.ofEpochSecond(stamp.getSeconds(), stamp.getNanos());
    }


    /**
     * Submits and waits for the transaction tree.
     *
     * <h2>Which service, and why not the other one</h2>
     *
     * CommandService, not CommandSubmissionService plus a completion stream.
     * A rejection on this path carries a full google.rpc.ErrorInfo with the
     * Canton code in it, so the asynchronous pair would buy nothing and cost
     * an offset-tracking loop. The same rejection comes back SYNCHRONOUSLY
     * from CommandSubmissionService.Submit, so a completion never arrives for
     * it either.
     *
     * <h2>act_as, not party</h2>
     *
     * The deprecated single-party field is left unset. The acting identity is
     * a list so that a create with two signatories can be submitted at all,
     * and writing the
     * first acting party into a field a participant might read INSTEAD of the
     * list would restore the single-party assumption invisibly.
     *
     * <h2>Three outcomes</h2>
     *
     * A completion that arrived is an observed outcome, accepted or rejected.
     * UNKNOWN is for the cases where none arrived - a deadline, a cancelled
     * call, an unreachable participant. That is not a theoretical branch: a
     * 0.05 s deadline against a local sandbox returned DEADLINE_EXCEEDED and
     * the contract was on the ledger anyway.
     *
     * definite_answer is deliberately NOT consulted. It came back false on a
     * plain CONTRACT_NOT_FOUND, which committed nothing; Canton sets it
     * conservatively, and mapping it to UNKNOWN would tell the operator to go
     * and look every time a contract id was mistyped. It is carried in the
     * detail text instead.
     */
    @Override
    public SubmitResult submit(Command cmd, SubmitContext ctx) {
        refuseIfReadOnly("change the participant");
        if (cmd == null)
            throw new LedgerException("no command supplied");
        if (ctx == null || ctx.lstPartyAct() == null || ctx.lstPartyAct().isEmpty())
            throw new LedgerException("a submission must name at least one acting party");
        if (ctx.idCommand() == null || ctx.idCommand().isBlank())
            throw new LedgerException("a submission must carry a command id so its result can be"
                    + " found again");

        CommandsOuterClass.Commands commands = CommandsOuterClass.Commands.newBuilder()
                .setLedgerId(idLedger)
                .setApplicationId(ctx.idApplication() == null ? "workbench"
                        : ctx.idApplication())
                .setCommandId(ctx.idCommand())
                .addAllActAs(ctx.lstPartyAct())
                .addAllReadAs(ctx.lstPartyRead() == null ? List.of() : ctx.lstPartyRead())
                .addCommands(toCommand(cmd))
                .build();

        CommandServiceOuterClass.SubmitAndWaitRequest req =
                CommandServiceOuterClass.SubmitAndWaitRequest.newBuilder()
                    .setCommands(commands).build();

        long msTimeout = ctx.timeout() == null ? 30_000L : ctx.timeout().toMillis();

        try {
            CommandServiceOuterClass.SubmitAndWaitForTransactionTreeResponse rsp =
                    CommandServiceGrpc.newBlockingStub(channel.authenticated())
                        .withDeadlineAfter(msTimeout, TimeUnit.MILLISECONDS)
                        .submitAndWaitForTransactionTree(req);

            TxTree tree = toTree(rsp.getTransaction());
            return SubmitResult.committed(tree.idUpdate(), tree, resultOf(tree, cmd));
        }
        catch (StatusRuntimeException ex) {
            return outcome(ex, ctx);
        }
    }


    /**
     * A failure with no completion behind it is UNKNOWN, and everything else is
     * a rejection the participant reported.
     *
     * The unknown set is deliberately small and deliberately not "anything that
     * looks like a network problem": each of these means the CALL ended without
     * the participant telling this client what happened, which is a different
     * claim from the participant saying no.
     */
    private SubmitResult outcome(StatusRuntimeException ex, SubmitContext ctx) {
        Status.Code code = ex.getStatus().getCode();

        if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.CANCELLED
                || code == Status.Code.UNAVAILABLE) {
            LOG.warn("submission {} on '{}' ended as {} - the outcome was NOT observed",
                    ctx.idCommand(), channel.profile().nameDisplay(), code.name());
            return SubmitResult.unknown(code.name(), "no completion was observed for command '"
                    + ctx.idCommand() + "' on '" + channel.profile().nameDisplay()
                    + "'. It may or may not have committed; look before submitting again");
        }

        CantonError err = CantonError.of(ex);
        return SubmitResult.rejected(err.codeError(), err.strDetail());
    }


    /**
     * The exercise return value, which lives on the ROOT exercised node. A
     * create has none, and saying so is not the same as a choice that returned
     * unit - null carries that distinction the whole way up, as TxNode already
     * does.
     */
    private static DamlValue resultOf(TxTree tree, Command cmd) {
        if (!(cmd instanceof Command.Exercise) && !(cmd instanceof Command.ExerciseByKey))
            return null;

        for (TxNode node : tree.lstRoot()) {
            if (node instanceof TxNode.Exercised exercised)
                return exercised.valueResult();
        }
        return null;
    }


    private static CommandsOuterClass.Command toCommand(Command cmd) {
        return switch (cmd) {
            case Command.Create val -> CommandsOuterClass.Command.newBuilder()
                    .setCreate(CommandsOuterClass.CreateCommand.newBuilder()
                            .setTemplateId(identifier(val.idTemplate()))
                            .setCreateArguments(record(val.argument()))
                            .build())
                    .build();

            case Command.Exercise val -> {
                if (val.idContract() == null || val.idContract().isBlank())
                    throw new LedgerException("an exercise must name a contract");
                if (val.nameChoice() == null || val.nameChoice().isBlank())
                    throw new LedgerException("an exercise must name a choice");

                yield CommandsOuterClass.Command.newBuilder()
                        .setExercise(CommandsOuterClass.ExerciseCommand.newBuilder()
                                .setTemplateId(identifier(val.idTemplate()))
                                .setContractId(val.idContract())
                                .setChoice(val.nameChoice())
                                .setChoiceArgument(value(val.argument()))
                                .build())
                        .build();
            }

            // The v1 oneof field is exerciseByKey, camelCase, where v2 spells it
            // exercise_by_key. The generated setter hides that here; Lapi2Client
            // resolves the name off a descriptor and cannot.
            case Command.ExerciseByKey val -> {
                if (val.valueKey() == null)
                    throw new LedgerException("an exercise by key must carry a key");
                if (val.nameChoice() == null || val.nameChoice().isBlank())
                    throw new LedgerException("an exercise must name a choice");

                yield CommandsOuterClass.Command.newBuilder()
                        .setExerciseByKey(CommandsOuterClass.ExerciseByKeyCommand.newBuilder()
                                .setTemplateId(identifier(val.idTemplate()))
                                .setContractKey(value(val.valueKey()))
                                .setChoice(val.nameChoice())
                                .setChoiceArgument(value(val.argument()))
                                .build())
                        .build();
            }
        };
    }


    // The three casts below are the ONLY places a generated type meets the
    // descriptor-driven converter. ProtoValues returns Message because it must
    // serve both generations and knows neither; this module knows exactly one,
    // which is what makes the cast safe here and nowhere else.

    private static ValueOuterClass.Identifier identifier(DataId idData) {
        return (ValueOuterClass.Identifier) ProtoValues.fromDataId(idData,
                ValueOuterClass.Identifier.newBuilder());
    }


    private static ValueOuterClass.Record record(DamlValue.Rec rec) {
        return (ValueOuterClass.Record) ProtoValues.fromRecord(rec,
                ValueOuterClass.Record.newBuilder());
    }


    private static ValueOuterClass.Value value(DamlValue val) {
        return (ValueOuterClass.Value) ProtoValues.fromValue(val,
                ValueOuterClass.Value.newBuilder());
    }


    /**
     * Allocates a party.
     *
     * The hint is a HINT. A participant is free to return
     * party-&lt;uuid&gt;::&lt;fingerprint&gt; instead of honouring it, measured on 2.x,
     * so nothing here or above may assume the requested string
     * appears in the result. The returned id is the answer; the hint was a
     * request.
     *
     * Both arguments are optional. An empty proto3 string is not serialised, so
     * leaving them unset is the same statement on the wire as omitting them,
     * and the participant chooses.
     */
    @Override
    public PartyInfo allocateParty(String hintParty, String nameDisplay) {
        refuseIfReadOnly("allocate a party");

        PartyManagementServiceOuterClass.AllocatePartyRequest.Builder bld =
                PartyManagementServiceOuterClass.AllocatePartyRequest.newBuilder();
        if (hintParty != null && !hintParty.isBlank())
            bld.setPartyIdHint(hintParty);
        if (nameDisplay != null && !nameDisplay.isBlank())
            bld.setDisplayName(nameDisplay);

        try {
            PartyManagementServiceOuterClass.AllocatePartyResponse rsp =
                    PartyManagementServiceGrpc.newBlockingStub(channel.authenticated())
                        .allocateParty(bld.build());

            PartyManagementServiceOuterClass.PartyDetails details = rsp.getPartyDetails();
            return new PartyInfo(details.getParty(), details.getDisplayName(),
                    details.getIsLocal());
        }
        catch (StatusRuntimeException ex) {
            throw failed("allocate party", ex);
        }
    }


    /**
     * Creates a ledger user with act-as and read-as rights.
     *
     * ParticipantAdmin is NOT grantable through this interface. A fixture needs
     * to stand a ledger up, not to mint an administrator, and a method that
     * could do both would eventually be used for the second by accident.
     *
     * The rights come back from the participant rather than being echoed from
     * the request. A grant the participant silently declined would otherwise be
     * reported as held, which is the one thing a caller must not be told about
     * a permission.
     */
    @Override
    public UserInfo createUser(String idUser, String idPartyPrimary, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        refuseIfReadOnly("create a user");
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user must have an id");

        UserManagementServiceOuterClass.User.Builder bldUser =
                UserManagementServiceOuterClass.User.newBuilder().setId(idUser);
        if (idPartyPrimary != null && !idPartyPrimary.isBlank())
            bldUser.setPrimaryParty(idPartyPrimary);

        List<UserManagementServiceOuterClass.Right> lstRight = new ArrayList<>();
        if (lstPartyAct != null) {
            for (String idParty : lstPartyAct) {
                lstRight.add(UserManagementServiceOuterClass.Right.newBuilder()
                        .setCanActAs(UserManagementServiceOuterClass.Right.CanActAs.newBuilder()
                                .setParty(idParty).build())
                        .build());
            }
        }
        if (lstPartyRead != null) {
            for (String idParty : lstPartyRead) {
                lstRight.add(UserManagementServiceOuterClass.Right.newBuilder()
                        .setCanReadAs(UserManagementServiceOuterClass.Right.CanReadAs.newBuilder()
                                .setParty(idParty).build())
                        .build());
            }
        }

        UserManagementServiceGrpc.UserManagementServiceBlockingStub stub =
                UserManagementServiceGrpc.newBlockingStub(channel.authenticated());

        try {
            UserManagementServiceOuterClass.CreateUserResponse rsp = stub.createUser(
                    UserManagementServiceOuterClass.CreateUserRequest.newBuilder()
                        .setUser(bldUser.build()).addAllRights(lstRight).build());

            return toUser(rsp.getUser(), stub);
        }
        catch (StatusRuntimeException ex) {
            throw failed("create user '" + idUser + "'", ex);
        }
    }


    /**
     * One user by id.
     *
     * A READ, so no READ_ONLY refusal - but the rights are a SECOND call, as
     * they are for users(). GetUser answers with the User message alone and the
     * rights live on ListUserRights, so a single-call implementation would
     * report every user as holding nothing.
     */
    @Override
    public UserInfo user(String idUser) {
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user id is required");

        UserManagementServiceGrpc.UserManagementServiceBlockingStub stub =
                UserManagementServiceGrpc.newBlockingStub(channel.authenticated());
        try {
            UserManagementServiceOuterClass.GetUserResponse rsp = stub.getUser(
                    UserManagementServiceOuterClass.GetUserRequest.newBuilder()
                        .setUserId(idUser).build());

            return toUser(rsp.getUser(), stub);
        }
        catch (StatusRuntimeException ex) {
            throw failed("get user '" + idUser + "'", ex);
        }
    }


    /**
     * Deletes a ledger user.
     *
     * The parties the user could act as are NOT touched. A user is a
     * credential; the parties outlive it, and the participant refuses a
     * repeated party hint anyway - so a delete that also removed parties would
     * make a script re-runnable in a way the language deliberately is not.
     */
    @Override
    public void deleteUser(String idUser) {
        refuseIfReadOnly("delete a user");
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user id is required");

        try {
            UserManagementServiceGrpc.newBlockingStub(channel.authenticated()).deleteUser(
                    UserManagementServiceOuterClass.DeleteUserRequest.newBuilder()
                        .setUserId(idUser).build());
        }
        catch (StatusRuntimeException ex) {
            throw failed("delete user '" + idUser + "'", ex);
        }
    }


    @Override
    public UserInfo grantUserRights(String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        refuseIfReadOnly("grant rights to a user");
        return rights(idUser, lstPartyAct, lstPartyRead, true);
    }


    @Override
    public UserInfo revokeUserRights(String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        refuseIfReadOnly("revoke rights from a user");
        return rights(idUser, lstPartyAct, lstPartyRead, false);
    }


    /**
     * Grant and revoke, which differ only in the RPC.
     *
     * WHAT COMES BACK IS NOT WHAT WAS ASKED FOR. The participant answers with
     * newly_granted_rights - a right the user already held is absent from it,
     * and so is one silently declined. Echoing the request would report a
     * permission as held on the strength of having asked, which is the one
     * claim a caller must never be given about a permission. So the user is
     * READ BACK afterwards and that is what is returned.
     *
     * ParticipantAdmin is unreachable here for the same reason as in
     * createUser: a fixture stands a ledger up, it does not mint an
     * administrator.
     */
    private UserInfo rights(String idUser, List<String> lstPartyAct, List<String> lstPartyRead,
            boolean flagGrant) {
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user id is required");

        List<UserManagementServiceOuterClass.Right> lstRight = new ArrayList<>();
        if (lstPartyAct != null) {
            for (String idParty : lstPartyAct) {
                lstRight.add(UserManagementServiceOuterClass.Right.newBuilder()
                        .setCanActAs(UserManagementServiceOuterClass.Right.CanActAs.newBuilder()
                                .setParty(idParty).build())
                        .build());
            }
        }
        if (lstPartyRead != null) {
            for (String idParty : lstPartyRead) {
                lstRight.add(UserManagementServiceOuterClass.Right.newBuilder()
                        .setCanReadAs(UserManagementServiceOuterClass.Right.CanReadAs.newBuilder()
                                .setParty(idParty).build())
                        .build());
            }
        }
        if (lstRight.isEmpty()) {
            throw new LedgerException("a " + (flagGrant ? "grant" : "revoke")
                    + " must name at least one right");
        }

        UserManagementServiceGrpc.UserManagementServiceBlockingStub stub =
                UserManagementServiceGrpc.newBlockingStub(channel.authenticated());

        try {
            if (flagGrant) {
                stub.grantUserRights(
                        UserManagementServiceOuterClass.GrantUserRightsRequest.newBuilder()
                            .setUserId(idUser).addAllRights(lstRight).build());
            }
            else {
                stub.revokeUserRights(
                        UserManagementServiceOuterClass.RevokeUserRightsRequest.newBuilder()
                            .setUserId(idUser).addAllRights(lstRight).build());
            }

            UserManagementServiceOuterClass.GetUserResponse rsp = stub.getUser(
                    UserManagementServiceOuterClass.GetUserRequest.newBuilder()
                        .setUserId(idUser).build());
            return toUser(rsp.getUser(), stub);
        }
        catch (StatusRuntimeException ex) {
            throw failed((flagGrant ? "grant rights to" : "revoke rights from")
                    + " user \'" + idUser + "\'", ex);
        }
    }


    /**
     * The current ledger end, as the opaque string v1 reports.
     *
     * A v1 offset is a LedgerOffset with two forms and only the ABSOLUTE one is
     * a value: a boundary is a request word, not a position. A participant
     * answering GetLedgerEnd with a boundary would be answering "the end is the
     * end", so that is refused rather than rendered as an enum name that would
     * then be handed to prune().
     */
    @Override
    public String ledgerEnd() {
        try {
            TransactionServiceOuterClass.GetLedgerEndResponse rsp =
                    TransactionServiceGrpc.newBlockingStub(channel.authenticated())
                        .getLedgerEnd(TransactionServiceOuterClass.GetLedgerEndRequest
                            .newBuilder().setLedgerId(idLedger).build());

            LedgerOffsetOuterClass.LedgerOffset offset = rsp.getOffset();
            if (offset.getValueCase()
                    != LedgerOffsetOuterClass.LedgerOffset.ValueCase.ABSOLUTE) {
                throw new LedgerException("the participant reported the ledger end as "
                        + offset.getValueCase() + " rather than an absolute offset");
            }
            return offset.getAbsolute();
        }
        catch (StatusRuntimeException ex) {
            throw failed("read the ledger end", ex);
        }
    }


    /**
     * Prunes the participant. IRREVERSIBLE.
     *
     * v1 takes the offset as a STRING and so does this, which is why nothing is
     * parsed on the way through: whatever ledgerEnd() reported goes back
     * unaltered, and a participant that dislikes it says so itself rather than
     * being second-guessed here.
     */
    @Override
    public void prune(String offsetUpToInclusive, boolean flagDivulged) {
        refuseIfReadOnly("prune the participant");
        if (offsetUpToInclusive == null || offsetUpToInclusive.isBlank())
            throw new LedgerException("pruning needs an offset; there is no prune-everything form");

        try {
            ParticipantPruningServiceGrpc.newBlockingStub(channel.authenticated()).prune(
                    ParticipantPruningServiceOuterClass.PruneRequest.newBuilder()
                        .setPruneUpTo(offsetUpToInclusive)
                        .setPruneAllDivulgedContracts(flagDivulged)
                        .build());
        }
        catch (StatusRuntimeException ex) {
            throw failed("prune up to " + offsetUpToInclusive, ex);
        }
    }


    /**
     * READ_ONLY is a promise not to change the participant, so the refusal
     * happens HERE and not at the participant - a call that was refused
     * remotely would already have been made. Design sec. 13.
     */
    private void refuseIfReadOnly(String strWhat) {
        if (!channel.profile().canSubmit()) {
            throw new IllegalStateException("profile '" + channel.profile().nameDisplay()
                    + "' is READ_ONLY and this would " + strWhat + " on the participant;"
                    + " nothing was sent");
        }
    }


    @Override
    public void close() {
        channel.close();
    }


    /**
     * Keeps the Canton error code, which is usually the whole answer, and names
     * the profile so a failure against the wrong participant is obvious.
     */
    private LedgerException failed(String strWhat, StatusRuntimeException ex) {
        // The CANTON code, not the gRPC one. Reads reported NOT_FOUND
        // here while submission reported CONTRACT_NOT_FOUND from the same
        // participant, and only one of those is the answer to "why".
        CantonError err = CantonError.of(ex);
        return new LedgerException("could not " + strWhat + " on '"
                + channel.profile().nameDisplay() + "': " + err.codeError()
                + " " + err.strDetail(), err.codeError(), ex);
    }

}
