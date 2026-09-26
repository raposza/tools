// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi2;

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
import com.raposza.api.model.UpdateScan;
import com.raposza.api.model.UserInfo;
import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;
import com.raposza.spi.Capability;
import com.raposza.spi.LedgerClient_i;
import com.raposza.spi.UnsupportedCapability;
import com.raposza.wire.CantonError;
import com.raposza.wire.DescriptorSet;
import com.raposza.wire.DynamicCall;
import com.raposza.wire.ProtoValues;
import com.raposza.wire.ServerReflection;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The Canton 3.x client, over Ledger API v2, spoken through DESCRIPTORS.
 *
 * <h2>No vendor jar, and that is the whole design</h2>
 *
 * The v2 classes ship inside the Canton jar and collide with the v1 bindings on
 * 306 fully qualified names, so an application that held both would build and
 * then fail to load either reliably. This client holds NEITHER: on connect it
 * asks the participant to describe its own Ledger API over
 * {@code grpc.reflection.v1}, and every request is a {@link DynamicMessage}
 * built against what came back.
 *
 * That reflection is available was MEASURED rather than assumed - on an
 * authenticated 3.5.11 participant, the service list is identical with and
 * without a token, so the descriptors can be fetched before any credential
 * question is settled.
 *
 * <h2>Fields resolve by NAME, and the 3.x line is not one API</h2>
 *
 * Across the installed 3.x range the read path's messages differ only by
 * ADDED fields with fresh numbers - {@code create_arguments} is 6 and
 * {@code contract_key} is 5 at both ends, and the {@code Value} oneof does not
 * differ at all. So name resolution holds unchanged and {@link
 * com.raposza.wire.ProtoValues} needs no generation branch.
 *
 * What DOES differ is which methods exist. {@code ContractService} arrives at
 * 3.4.11 and is absent from every earlier 3.4; the paged reads arrive at 3.5.
 * Each is used only where the descriptor set reports it, and the floor - 3.4.4 -
 * has a working path for everything {@link LedgerClient_i} declares.
 *
 * <h2>The transaction tree is RECONSTRUCTED</h2>
 *
 * v1 handed over a tree with root event ids and child event ids. v2 hands over
 * a FLAT event list in which an exercised node declares
 * {@code last_descendant_node_id}, and the nesting is the interval that implies.
 * {@link #treeOf} rebuilds it. This is the one place where the two generations
 * needed different code rather than different field names.
 *
 * Author Claude/bentzn
 */
public final class Lapi2Client implements LedgerClient_i {

    private static final Logger LOG = LoggerFactory.getLogger(Lapi2Client.class);

    private static final String PKG = "com.daml.ledger.api.v2.";

    /**
     * What marks a package-name reference rather than a package id.
     *
     * A hash and a name are both text in the same field, so the participant
     * cannot tell them apart by shape and does not try: the prefix is the
     * declaration.
     */
    private static final String STR_PREFIX_NAME = "#";

    private static final String PKG_ADMIN = PKG + "admin.";

    private static final String M_VERSION = PKG + "VersionService/GetLedgerApiVersion";

    private static final String M_LEDGER_END = PKG + "StateService/GetLedgerEnd";

    private static final String M_ACS = PKG + "StateService/GetActiveContracts";

    private static final String M_UPDATE_BY_ID = PKG + "UpdateService/GetUpdateById";

    private static final String M_UPDATES = PKG + "UpdateService/GetUpdates";

    private static final String M_EVENTS_BY_CID = PKG + "EventQueryService/GetEventsByContractId";

    private static final String M_GET_CONTRACT = PKG + "ContractService/GetContract";

    private static final String M_LIST_PACKAGES = PKG + "PackageService/ListPackages";

    private static final String M_GET_PACKAGE = PKG + "PackageService/GetPackage";

    private static final String M_SUBMIT_TX = PKG + "CommandService/SubmitAndWaitForTransaction";

    private static final String M_PARTICIPANT_ID = PKG_ADMIN
            + "PartyManagementService/GetParticipantId";

    private static final String M_LIST_PARTIES = PKG_ADMIN
            + "PartyManagementService/ListKnownParties";

    private static final String M_ALLOCATE_PARTY = PKG_ADMIN
            + "PartyManagementService/AllocateParty";

    private static final String M_LIST_USERS = PKG_ADMIN + "UserManagementService/ListUsers";

    private static final String M_LIST_USER_RIGHTS = PKG_ADMIN
            + "UserManagementService/ListUserRights";

    private static final String M_GET_USER = PKG_ADMIN + "UserManagementService/GetUser";

    private static final String M_DELETE_USER = PKG_ADMIN + "UserManagementService/DeleteUser";

    private static final String M_GRANT_RIGHTS = PKG_ADMIN
            + "UserManagementService/GrantUserRights";

    private static final String M_REVOKE_RIGHTS = PKG_ADMIN
            + "UserManagementService/RevokeUserRights";

    private static final String M_PRUNE = PKG_ADMIN + "ParticipantPruningService/Prune";

    /** LEDGER_EFFECTS is what carries exercised nodes; ACS_DELTA carries creates only. */
    private static final String STR_SHAPE_EFFECTS = "TRANSACTION_SHAPE_LEDGER_EFFECTS";

    private static final Duration TIMEOUT_CALL = Duration.ofSeconds(60);

    private static final Duration TIMEOUT_REFLECT = Duration.ofSeconds(60);

    /** Guards a drained stream. An unbounded active contract set is the ledger. */
    private static final int CNT_ACS_MAX = 100_000;

    /** The same guard for an update scan, where the stream is the ledger's history. */
    private static final int CNT_UPDATE_MAX = 10_000;

    private final Lapi2Channel channel;

    private final DescriptorSet set;

    private final DynamicCall call;

    private final HostProfile profile;

    private final String idParticipant;

    private final String versionApi;


    /**
     * @param profile where to connect and with what rights
     * @param source token supply, or null for an unauthenticated participant
     * @throws LedgerException when the participant does not describe itself
     */
    public Lapi2Client(HostProfile profile, TokenSource_i source) {
        this.profile = profile;
        this.channel = new Lapi2Channel(profile, source);

        try {
            ServerReflection reflection =
                    new ServerReflection(channel.authenticated(), TIMEOUT_REFLECT);
            this.set = reflection.descriptorSet();
        }
        catch (RuntimeException ex) {
            channel.close();
            throw new LedgerException("'" + profile.nameDisplay() + "' did not describe its"
                    + " Ledger API over gRPC reflection, so nothing can be called on it: " + ex,
                    ex);
        }

        if (!hasService(PKG + "VersionService")) {
            channel.close();
            throw new LedgerException("'" + profile.nameDisplay() + "' advertises no "
                    + PKG + "VersionService, so it is not a Canton 3.x participant");
        }

        this.call = new DynamicCall(channel.authenticated(), set, TIMEOUT_CALL);
        this.versionApi = strOf(call.call(M_VERSION), "version");
        this.idParticipant = readParticipantId();
    }


    /**
     * @return the participant id, or empty when the token cannot ask for it -
     *         admin rights and read rights are separate and one credential
     *         rarely holds both, so this must not fail a connection
     */
    private String readParticipantId() {
        try {
            return strOf(call.call(M_PARTICIPANT_ID), "participant_id");
        }
        catch (RuntimeException ex) {
            LOG.debug("the participant id is not readable with this credential: {}",
                    ex.getMessage());
            return "";
        }
    }


    @Override
    public LedgerInfo info() {
        // v2 HAS NO LEDGER ID. It is not empty-because-unread, it does not
        // exist as a concept, and reporting the participant id in its place
        // would be inventing an identifier the participant never issued.
        return new LedgerInfo("", idParticipant, versionApi, ApiGeneration.V2);
    }


    @Override
    public List<PartyInfo> parties() {
        List<PartyInfo> lstOut = new ArrayList<>();
        String strPage = "";

        do {
            DynamicMessage.Builder bld = call.builderFor(M_LIST_PARTIES);
            if (!strPage.isEmpty())
                DynamicCall.set(bld, "page_token", strPage);

            DynamicMessage rsp = call.call(M_LIST_PARTIES, bld.build());
            for (Message msg : DynamicCall.lstMessage(rsp, "party_details")) {
                lstOut.add(toParty(msg));
            }
            strPage = strOf(rsp, "next_page_token");
        }
        while (!strPage.isEmpty());

        return List.copyOf(lstOut);
    }


    /**
     * On 3.x an allocation hint lands in the party ID rather than in a display
     * name, and there is no display name field at all. Reporting the id as the
     * name would make {@code PartyInfo.label()} claim a display name the
     * participant never gave, so it is left empty and the label falls back on
     * its own.
     */
    private static PartyInfo toParty(Message msg) {
        return new PartyInfo(strOf(msg, "party"), "", boolOf(msg, "is_local"));
    }


    @Override
    public List<UserInfo> users() {
        List<UserInfo> lstOut = new ArrayList<>();
        String strPage = "";

        do {
            DynamicMessage.Builder bld = call.builderFor(M_LIST_USERS);
            if (!strPage.isEmpty())
                DynamicCall.set(bld, "page_token", strPage);

            DynamicMessage rsp = call.call(M_LIST_USERS, bld.build());
            for (Message msg : DynamicCall.lstMessage(rsp, "users")) {
                lstOut.add(toUser(strOf(msg, "id"), strOf(msg, "primary_party")));
            }
            strPage = strOf(rsp, "next_page_token");
        }
        while (!strPage.isEmpty());

        return List.copyOf(lstOut);
    }


    /**
     * The rights are a SECOND call per user and there is no bulk form. A user
     * whose rights cannot be read is reported with the rights unknown rather
     * than dropped: the id and primary party were read successfully and
     * discarding them would hide a user that exists.
     */
    private UserInfo toUser(String idUser, String idPartyPrimary) {
        List<String> lstAct = new ArrayList<>();
        List<String> lstRead = new ArrayList<>();
        boolean flagAdmin = false;

        try {
            DynamicMessage.Builder bld = call.builderFor(M_LIST_USER_RIGHTS);
            DynamicCall.set(bld, "user_id", idUser);
            DynamicMessage rsp = call.call(M_LIST_USER_RIGHTS, bld.build());

            for (Message msgRight : DynamicCall.lstMessage(rsp, "rights")) {
                for (Map.Entry<Descriptors.FieldDescriptor, Object> entry
                        : msgRight.getAllFields().entrySet()) {

                    String nameKind = entry.getKey().getName();
                    if ("participant_admin".equals(nameKind))
                        flagAdmin = true;
                    else if ("can_act_as".equals(nameKind))
                        lstAct.add(strOf((Message) entry.getValue(), "party"));
                    else if ("can_read_as".equals(nameKind))
                        lstRead.add(strOf((Message) entry.getValue(), "party"));
                }
            }
        }
        catch (RuntimeException ex) {
            LOG.debug("rights for user {} are not readable: {}", idUser, ex.getMessage());
        }

        return new UserInfo(idUser, idPartyPrimary, flagAdmin, List.copyOf(lstAct),
                List.copyOf(lstRead));
    }


    @Override
    public List<String> packageIds() {
        DynamicMessage rsp = call.call(M_LIST_PACKAGES);
        Descriptors.FieldDescriptor field =
                rsp.getDescriptorForType().findFieldByName("package_ids");

        List<String> lstOut = new ArrayList<>();
        int cntItem = rsp.getRepeatedFieldCount(field);
        for (int idxItem = 0; idxItem < cntItem; idxItem++) {
            lstOut.add((String) rsp.getRepeatedField(field, idxItem));
        }
        return List.copyOf(lstOut);
    }


    @Override
    public Optional<byte[]> archive(String idPackage) {
        DynamicMessage.Builder bld = call.builderFor(M_GET_PACKAGE);
        DynamicCall.set(bld, "package_id", idPackage);

        try {
            DynamicMessage rsp = call.call(M_GET_PACKAGE, bld.build());
            ByteString bs = (ByteString) rsp.getField(
                    rsp.getDescriptorForType().findFieldByName("archive_payload"));
            return Optional.of(bs.toByteArray());
        }
        catch (RuntimeException ex) {
            if (isNotFound(ex))
                return Optional.empty();
            throw ex;
        }
    }


    /**
     * A 3.x participant REFUSES a package hash in an active-contract template
     * filter - `Invalid field packageId: Received an identifier with package ID
     * &lt;hash&gt;, but expected a package name`, measured on 3.5.12,
     * 2026-08-25 - and it refuses the bare name too, with the name in the same
     * message. The field is read as a package-name REFERENCE, which is `#`
     * followed by the name, and anything else is a package id whatever it looks
     * like.
     *
     * A package with no name is left as a hash: it predates Smart Contract
     * Upgrades, and there is nothing else to send.
     */
    @Override
    public DataId idFilter(DataId idTemplate, Optional<String> nameOptPackage) {
        if (idTemplate == null)
            throw new LedgerException("no template identifier supplied");
        if (nameOptPackage == null || nameOptPackage.isEmpty())
            return idTemplate;

        return new DataId(STR_PREFIX_NAME + nameOptPackage.get(), idTemplate.nameModule(),
                idTemplate.nameEntity());
    }


    @Override
    public List<Contract> activeContracts(ContractQuery query) {
        if (query == null)
            throw new LedgerException("no query supplied");
        if (!query.lstInterface().isEmpty()) {
            // v2 CAN filter by interface. It is refused here anyway because
            // nothing has measured what a participant returns for one, and a
            // silently empty result is the failure this refusal exists to
            // prevent. Lifting it is a measurement, not a code change.
            throw new UnsupportedCapability(Capability.INTERFACE_FILTER, "canton-3x",
                    "interface-filtered retrieval is unmeasured on this target");
        }

        // v2 REQUIRES an offset to read at, and it must be one the participant
        // still holds. Empty means "now", which is the ledger end.
        long nOffset = query.offsetAt().isPresent()
                ? Long.parseLong(query.offsetAt().get())
                : longOf(call.call(M_LEDGER_END), "offset");

        DynamicMessage.Builder bld = call.builderFor(M_ACS);
        DynamicCall.set(bld, "active_at_offset", nOffset);
        DynamicCall.set(bld, "event_format", eventFormat(query.lstPartyRead(),
                query.lstTemplate(), fieldOfType(bld, "event_format")));

        int cntWanted = query.cntLimit() > 0 ? query.cntLimit() : CNT_ACS_MAX;
        DynamicCall.StreamResult result = call.callStream(M_ACS, bld.build(), cntWanted);

        List<Contract> lstOut = new ArrayList<>();
        for (DynamicMessage rsp : result.lstMessage()) {
            Message msgActive = msgOf(rsp, "active_contract");
            if (msgActive == null)
                continue;

            Message msgCreated = msgOf(msgActive, "created_event");
            if (msgCreated != null)
                lstOut.add(toContract(msgCreated));
        }

        if (result.flagCapped()) {
            LOG.info("the active contract set on '{}' was read up to the cap of {}",
                    profile.nameDisplay(), cntWanted);
        }
        return List.copyOf(lstOut);
    }


    @Override
    public Optional<Contract> contract(String idContract, List<String> lstPartyRead) {
        if (idContract == null || idContract.isBlank())
            throw new LedgerException("no contract id supplied");

        // ContractService arrives at 3.4.11 and is absent from every earlier
        // 3.4. Where it is missing the event query answers the same question
        // from the creating event, so the floor keeps a working path.
        if (hasService(PKG + "ContractService"))
            return contractFromService(idContract, lstPartyRead);
        return contractFromEvents(idContract, lstPartyRead);
    }


    private Optional<Contract> contractFromService(String idContract, List<String> lstPartyRead) {
        DynamicMessage.Builder bld = call.builderFor(M_GET_CONTRACT);
        DynamicCall.set(bld, "contract_id", idContract);
        DynamicCall.set(bld, "querying_parties", lstPartyRead == null ? List.of() : lstPartyRead);

        try {
            DynamicMessage rsp = call.call(M_GET_CONTRACT, bld.build());
            Message msgCreated = msgOf(rsp, "created_event");
            return msgCreated == null ? Optional.empty() : Optional.of(toContract(msgCreated));
        }
        catch (RuntimeException ex) {
            if (isNotFound(ex))
                return Optional.empty();
            throw ex;
        }
    }


    private Optional<Contract> contractFromEvents(String idContract, List<String> lstPartyRead) {
        DynamicMessage.Builder bld = call.builderFor(M_EVENTS_BY_CID);
        DynamicCall.set(bld, "contract_id", idContract);
        DynamicCall.set(bld, "event_format", eventFormat(lstPartyRead, List.of(),
                fieldOfType(bld, "event_format")));

        try {
            DynamicMessage rsp = call.call(M_EVENTS_BY_CID, bld.build());
            Message msgCreated = msgOf(rsp, "created");
            if (msgCreated == null)
                return Optional.empty();

            Message msgEvent = msgOf(msgCreated, "created_event");
            if (msgEvent == null)
                return Optional.empty();

            Contract contract = toContract(msgEvent);
            Message msgArchived = msgOf(rsp, "archived");
            if (msgArchived == null)
                return Optional.of(contract);

            Message msgArchivedEvent = msgOf(msgArchived, "archived_event");
            String offsetArchived = msgArchivedEvent == null
                    ? null
                    : String.valueOf(longOf(msgArchivedEvent, "offset"));
            return Optional.of(new Contract(contract.idContract(), contract.idEvent(),
                    contract.idTemplate(), contract.payload(), contract.lstSignatory(),
                    contract.lstObserver(), contract.key(), contract.offsetCreated(),
                    Optional.ofNullable(offsetArchived)));
        }
        catch (RuntimeException ex) {
            if (isNotFound(ex))
                return Optional.empty();
            throw ex;
        }
    }


    /**
     * The event id is the NODE ID rendered as text. v2 has no event id; a node
     * is addressed by its offset and its position within the transaction, and
     * the node id is the half that identifies it inside the tree.
     */
    private static Contract toContract(Message msgCreated) {
        Message msgKey = msgOf(msgCreated, "contract_key");
        return new Contract(strOf(msgCreated, "contract_id"),
                String.valueOf(intOf(msgCreated, "node_id")),
                ProtoValues.toDataId(msgOf(msgCreated, "template_id")),
                ProtoValues.toRecord(msgOf(msgCreated, "create_arguments")),
                lstStr(msgCreated, "signatories"), lstStr(msgCreated, "observers"),
                msgKey == null ? Optional.empty()
                        : Optional.of(ProtoValues.toValue(msgKey)),
                String.valueOf(longOf(msgCreated, "offset")), Optional.empty());
    }


    @Override
    public Optional<TxTree> tree(String idUpdate, List<String> lstPartyRead) {
        if (idUpdate == null || idUpdate.isBlank())
            throw new LedgerException("no update id supplied");

        DynamicMessage.Builder bld = call.builderFor(M_UPDATE_BY_ID);
        DynamicCall.set(bld, "update_id", idUpdate);
        DynamicCall.set(bld, "update_format", updateFormat(lstPartyRead,
                fieldOfType(bld, "update_format")));

        try {
            DynamicMessage rsp = call.call(M_UPDATE_BY_ID, bld.build());
            Message msgTx = msgOf(rsp, "transaction");
            return msgTx == null ? Optional.empty() : Optional.of(treeOf(msgTx));
        }
        catch (RuntimeException ex) {
            if (isNotFound(ex))
                return Optional.empty();
            throw ex;
        }
    }


    /**
     * Reads a bounded stretch of the update stream.
     *
     * <h2>Why the end is resolved before the call</h2>
     *
     * end_inclusive is OPTIONAL on the wire and omitting it means "follow the
     * ledger". That is a stream that never completes, and a drained stream that
     * never completes is a pane that hangs until the deadline. So an open end
     * is turned into the current ledger end here, and the window that was
     * actually read is reported back rather than the one that was asked for.
     *
     * <h2>Inclusive in, exclusive on the wire</h2>
     *
     * begin_exclusive is the v2 spelling; the SPI promises the caller an
     * inclusive start, so one is subtracted. Offsets start at 1 and 0 is the
     * beginning of the ledger, which is exactly what a start of 1 decrements
     * to - no special case is needed for the first transaction on a ledger.
     *
     * The shape is LEDGER_EFFECTS by way of updateFormat, which is what carries
     * exercised nodes. ACS_DELTA would return the creates and archives and
     * silently drop every choice, which is most of what activity IS.
     */
    @Override
    public UpdateScan updates(String offsetFromInclusive, String offsetToInclusive,
            List<String> lstPartyRead, int cntLimit) {

        if (lstPartyRead == null || lstPartyRead.isEmpty())
            throw new LedgerException("an update scan must name at least one party to read as");

        long nEnd = offsetToInclusive == null || offsetToInclusive.isBlank()
                ? longOf(call.call(M_LEDGER_END), "offset")
                : parseOffset(offsetToInclusive);
        long nFrom = offsetFromInclusive == null || offsetFromInclusive.isBlank()
                ? 1L
                : parseOffset(offsetFromInclusive);

        if (nEnd < nFrom)
            return UpdateScan.empty(String.valueOf(nFrom), String.valueOf(nEnd));

        DynamicMessage.Builder bld = call.builderFor(M_UPDATES);
        DynamicCall.set(bld, "begin_exclusive", Math.max(0L, nFrom - 1L));
        DynamicCall.set(bld, "end_inclusive", nEnd);
        DynamicCall.set(bld, "update_format", updateFormat(lstPartyRead,
                fieldOfType(bld, "update_format")));

        int cntWanted = cntLimit > 0 ? cntLimit : CNT_UPDATE_MAX;
        DynamicCall.StreamResult result = call.callStream(M_UPDATES, bld.build(), cntWanted);

        List<TxTree> lstOut = new ArrayList<>();
        for (DynamicMessage rsp : result.lstMessage()) {
            // Offset checkpoints and topology events arrive on the same stream
            // and are not transactions. Skipped rather than refused: they are a
            // normal part of the stream, not a surprise in it.
            Message msgTx = msgOf(rsp, "transaction");
            if (msgTx != null)
                lstOut.add(treeOf(msgTx));
        }

        if (result.flagCapped()) {
            LOG.info("the update scan of {}..{} on '{}' stopped at the cap of {}", nFrom, nEnd,
                    profile.nameDisplay(), cntWanted);
        }
        return new UpdateScan(List.copyOf(lstOut), result.flagCapped(), String.valueOf(nFrom),
                String.valueOf(nEnd));
    }


    /**
     * @param strOffset a v2 offset, which is an integer
     * @return it as a number
     * @throws LedgerException when it is not one, rather than a
     *         NumberFormatException nobody upstream would recognise
     */
    private static long parseOffset(String strOffset) {
        try {
            return Long.parseLong(strOffset.trim());
        }
        catch (NumberFormatException ex) {
            throw new LedgerException("'" + strOffset + "' is not a Ledger API v2 offset, which"
                    + " is an integer", ex);
        }
    }


    /**
     * v2 addresses a node by offset and node id and has no event id, so there
     * is nothing here to look one up by. Refused rather than answered with the
     * wrong thing: returning empty would be indistinguishable from a
     * transaction that does not exist.
     */
    @Override
    public Optional<TxTree> treeByEvent(String idEvent, List<String> lstPartyRead) {
        throw new UnsupportedCapability(Capability.UPDATE_BY_EVENT_ID, "canton-3x",
                "Ledger API v2 has no event id; a node is addressed by offset and node id");
    }


    /**
     * Rebuilds the tree from the flat event list.
     *
     * Every event carries a node id, and an exercised event carries the id of
     * its LAST DESCENDANT. So node B is a child of exercised node A exactly
     * when B's id falls in A's interval and no nearer exercised node claims it -
     * which is what the stack below decides. A create has no interval and
     * therefore no children.
     */
    private static TxTree treeOf(Message msgTx) {
        Map<Integer, Message> mapEvent = new TreeMap<>();
        for (Message msgWrap : DynamicCall.lstMessage(msgTx, "events")) {
            Message msgEvent = anyOneOf(msgWrap);
            if (msgEvent != null)
                mapEvent.put(intOf(msgEvent, "node_id"), msgEvent);
        }

        List<TxNode> lstRoot = new ArrayList<>();
        Map<Integer, List<TxNode>> mapChild = new LinkedHashMap<>();
        List<int[]> lstOpen = new ArrayList<>();

        List<Integer> lstNodeId = new ArrayList<>(mapEvent.keySet());
        lstNodeId.sort(Comparator.naturalOrder());

        // Walk in node order. An exercised node opens an interval; a node whose
        // id has passed the top interval's end closes it. What remains on the
        // stack when a node arrives is its ancestry, and the top of it is its
        // parent.
        for (Integer nId : lstNodeId) {
            while (!lstOpen.isEmpty() && nId > lstOpen.get(lstOpen.size() - 1)[1]) {
                closeTop(lstOpen, mapChild, mapEvent, lstRoot);
            }

            Message msgEvent = mapEvent.get(nId);
            boolean flagExercised = has(msgEvent, "choice");
            if (flagExercised) {
                lstOpen.add(new int[] {nId, intOf(msgEvent, "last_descendant_node_id")});
                mapChild.put(nId, new ArrayList<>());
            }
            else {
                place(toCreated(msgEvent), nId, lstOpen, mapChild, lstRoot);
            }
        }

        while (!lstOpen.isEmpty()) {
            closeTop(lstOpen, mapChild, mapEvent, lstRoot);
        }

        return new TxTree(strOf(msgTx, "update_id"), blankToEmpty(strOf(msgTx, "command_id")),
                blankToEmpty(strOf(msgTx, "workflow_id")),
                String.valueOf(longOf(msgTx, "offset")), instantOf(msgTx, "effective_at"),
                List.copyOf(lstRoot));
    }


    private static void closeTop(List<int[]> lstOpen, Map<Integer, List<TxNode>> mapChild,
            Map<Integer, Message> mapEvent, List<TxNode> lstRoot) {

        int[] arrTop = lstOpen.remove(lstOpen.size() - 1);
        int nId = arrTop[0];
        TxNode node = toExercised(mapEvent.get(nId), mapChild.getOrDefault(nId, List.of()));
        place(node, nId, lstOpen, mapChild, lstRoot);
    }


    private static void place(TxNode node, int nId, List<int[]> lstOpen,
            Map<Integer, List<TxNode>> mapChild, List<TxNode> lstRoot) {

        if (lstOpen.isEmpty()) {
            lstRoot.add(node);
            return;
        }
        mapChild.get(lstOpen.get(lstOpen.size() - 1)[0]).add(node);
    }


    private static TxNode toCreated(Message msgEvent) {
        return new TxNode.Created(String.valueOf(intOf(msgEvent, "node_id")),
                strOf(msgEvent, "contract_id"),
                ProtoValues.toDataId(msgOf(msgEvent, "template_id")),
                ProtoValues.toRecord(msgOf(msgEvent, "create_arguments")),
                lstStr(msgEvent, "signatories"), lstStr(msgEvent, "observers"));
    }


    private static TxNode toExercised(Message msgEvent, List<TxNode> lstChild) {
        Message msgArg = msgOf(msgEvent, "choice_argument");
        Message msgResult = msgOf(msgEvent, "exercise_result");
        return new TxNode.Exercised(String.valueOf(intOf(msgEvent, "node_id")),
                strOf(msgEvent, "contract_id"),
                ProtoValues.toDataId(msgOf(msgEvent, "template_id")),
                strOf(msgEvent, "choice"), boolOf(msgEvent, "consuming"),
                msgArg == null ? null : ProtoValues.toValue(msgArg),
                msgResult == null ? null : ProtoValues.toValue(msgResult),
                lstStr(msgEvent, "acting_parties"), List.copyOf(lstChild));
    }


    @Override
    public SubmitResult submit(Command cmd, SubmitContext ctx) {
        refuseIfReadOnly("change the participant");
        if (cmd == null)
            throw new LedgerException("no command supplied");
        if (ctx == null || ctx.lstPartyAct() == null || ctx.lstPartyAct().isEmpty())
            throw new LedgerException("a submission must name at least one acting party");
        if (ctx.idCommand() == null || ctx.idCommand().isBlank()) {
            throw new LedgerException("a submission must carry a command id so its result can be"
                    + " found again");
        }

        DynamicMessage.Builder bldReq = call.builderFor(M_SUBMIT_TX);
        Descriptors.Descriptor descCommands = fieldOfType(bldReq, "commands");

        DynamicMessage.Builder bldCommands = DynamicMessage.newBuilder(descCommands);
        // v2 renamed applicationId to user_id and dropped ledger_id entirely.
        DynamicCall.set(bldCommands, "user_id",
                ctx.idApplication() == null ? "workbench" : ctx.idApplication());
        DynamicCall.set(bldCommands, "command_id", ctx.idCommand());
        DynamicCall.set(bldCommands, "act_as", ctx.lstPartyAct());
        DynamicCall.set(bldCommands, "read_as",
                ctx.lstPartyRead() == null ? List.of() : ctx.lstPartyRead());
        DynamicCall.set(bldCommands, "commands",
                List.of(toCommand(cmd, fieldOfType(bldCommands, "commands"))));

        DynamicCall.set(bldReq, "commands", bldCommands.build());
        DynamicCall.set(bldReq, "transaction_format",
                transactionFormat(ctx.lstPartyAct(), fieldOfType(bldReq, "transaction_format")));

        Duration timeout = ctx.timeout() == null ? Duration.ofSeconds(30) : ctx.timeout();
        DynamicCall callHere = new DynamicCall(channel.authenticated(), set, timeout);

        try {
            DynamicMessage rsp = callHere.call(M_SUBMIT_TX, bldReq.build());
            Message msgTx = msgOf(rsp, "transaction");
            if (msgTx == null) {
                return SubmitResult.unknown("NO_TRANSACTION", "the participant accepted command '"
                        + ctx.idCommand() + "' and returned no transaction");
            }

            TxTree tree = treeOf(msgTx);
            return SubmitResult.committed(tree.idUpdate(), tree, resultOf(tree, cmd));
        }
        catch (RuntimeException ex) {
            return outcome(ex, ctx);
        }
    }


    /**
     * A failure with no completion behind it is UNKNOWN, and everything else is
     * a rejection the participant reported. The unknown set is deliberately
     * small and is not "anything that looks like a network problem": each of
     * these means the CALL ended without the participant saying what happened,
     * which is a different claim from the participant saying no.
     */
    private SubmitResult outcome(RuntimeException ex, SubmitContext ctx) {
        StatusRuntimeException exStatus = statusOf(ex);
        if (exStatus == null)
            throw ex;

        Status.Code code = exStatus.getStatus().getCode();
        if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.CANCELLED
                || code == Status.Code.UNAVAILABLE) {

            LOG.warn("submission {} on '{}' ended as {} - the outcome was NOT observed",
                    ctx.idCommand(), profile.nameDisplay(), code.name());
            return SubmitResult.unknown(code.name(), "no completion was observed for command '"
                    + ctx.idCommand() + "' on '" + profile.nameDisplay()
                    + "'. It may or may not have committed; look before submitting again");
        }

        CantonError err = CantonError.of(exStatus);
        return SubmitResult.rejected(err.codeError(), err.strDetail());
    }


    private static DamlValue resultOf(TxTree tree, Command cmd) {
        if (!(cmd instanceof Command.Exercise) && !(cmd instanceof Command.ExerciseByKey))
            return null;

        for (TxNode node : tree.lstRoot()) {
            if (node instanceof TxNode.Exercised exercised)
                return exercised.valueResult();
        }
        return null;
    }


    private static Message toCommand(Command cmd, Descriptors.Descriptor descCommand) {
        DynamicMessage.Builder bld = DynamicMessage.newBuilder(descCommand);

        switch (cmd) {
            case Command.Create create -> {
                Descriptors.Descriptor descCreate = typeOf(descCommand, "create");
                DynamicMessage.Builder bldCreate = DynamicMessage.newBuilder(descCreate);
                DynamicCall.set(bldCreate, "template_id", ProtoValues.fromDataId(
                        create.idTemplate(),
                        DynamicMessage.newBuilder(typeOf(descCreate, "template_id"))));
                DynamicCall.set(bldCreate, "create_arguments",
                        ProtoValues.fromRecord(create.argument(),
                                DynamicMessage.newBuilder(typeOf(descCreate, "create_arguments"))));
                DynamicCall.set(bld, "create", bldCreate.build());
            }
            case Command.Exercise exercise -> {
                Descriptors.Descriptor descEx = typeOf(descCommand, "exercise");
                DynamicMessage.Builder bldEx = DynamicMessage.newBuilder(descEx);
                DynamicCall.set(bldEx, "template_id", ProtoValues.fromDataId(
                        exercise.idTemplate(),
                        DynamicMessage.newBuilder(typeOf(descEx, "template_id"))));
                DynamicCall.set(bldEx, "contract_id", exercise.idContract());
                DynamicCall.set(bldEx, "choice", exercise.nameChoice());
                DynamicCall.set(bldEx, "choice_argument",
                        ProtoValues.fromValue(exercise.argument(),
                                DynamicMessage.newBuilder(typeOf(descEx, "choice_argument"))));
                DynamicCall.set(bld, "exercise", bldEx.build());
            }

            // exercise_by_key on v2, where v1 spells the same oneof field
            // exerciseByKey. This client resolves the name off the descriptor,
            // so the difference is load-bearing here and invisible on 2.x.
            case Command.ExerciseByKey byKey -> {
                Descriptors.Descriptor descKey = typeOf(descCommand, "exercise_by_key");
                DynamicMessage.Builder bldKey = DynamicMessage.newBuilder(descKey);
                DynamicCall.set(bldKey, "template_id", ProtoValues.fromDataId(
                        byKey.idTemplate(),
                        DynamicMessage.newBuilder(typeOf(descKey, "template_id"))));
                DynamicCall.set(bldKey, "contract_key",
                        ProtoValues.fromValue(byKey.valueKey(),
                                DynamicMessage.newBuilder(typeOf(descKey, "contract_key"))));
                DynamicCall.set(bldKey, "choice", byKey.nameChoice());
                DynamicCall.set(bldKey, "choice_argument",
                        ProtoValues.fromValue(byKey.argument(),
                                DynamicMessage.newBuilder(typeOf(descKey, "choice_argument"))));
                DynamicCall.set(bld, "exercise_by_key", bldKey.build());
            }
        }

        return bld.build();
    }


    @Override
    public PartyInfo allocateParty(String hintParty, String nameDisplay) {
        refuseIfReadOnly("allocate a party");

        DynamicMessage.Builder bld = call.builderFor(M_ALLOCATE_PARTY);
        if (hintParty != null && !hintParty.isBlank())
            DynamicCall.set(bld, "party_id_hint", hintParty);

        // nameDisplay is ACCEPTED AND DROPPED. v2 removed the display name from
        // PartyDetails, so a value stored here could never be read back, and a
        // write nobody can observe is worse than an honest omission.
        if (nameDisplay != null && !nameDisplay.isBlank()) {
            LOG.info("a display name was supplied for party hint '{}' and Ledger API v2 has no"
                    + " field for one; it is not sent", hintParty);
        }

        DynamicMessage rsp = call.call(M_ALLOCATE_PARTY, bld.build());
        Message msgDetails = msgOf(rsp, "party_details");
        if (msgDetails == null)
            throw new LedgerException("the participant allocated a party and described none");
        return toParty(msgDetails);
    }


    @Override
    public UserInfo createUser(String idUser, String idPartyPrimary, List<String> lstPartyAct,
            List<String> lstPartyRead) {

        refuseIfReadOnly("create a ledger user");
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user must have an id");

        String strMethod = PKG_ADMIN + "UserManagementService/CreateUser";
        DynamicMessage.Builder bldReq = call.builderFor(strMethod);

        Descriptors.Descriptor descUser = fieldOfType(bldReq, "user");
        DynamicMessage.Builder bldUser = DynamicMessage.newBuilder(descUser);
        DynamicCall.set(bldUser, "id", idUser);
        if (idPartyPrimary != null && !idPartyPrimary.isBlank())
            DynamicCall.set(bldUser, "primary_party", idPartyPrimary);
        DynamicCall.set(bldReq, "user", bldUser.build());

        Descriptors.Descriptor descRight = fieldOfType(bldReq, "rights");
        List<Message> lstRight = new ArrayList<>();
        for (String idParty : lstPartyAct == null ? List.<String>of() : lstPartyAct) {
            lstRight.add(right(descRight, "can_act_as", idParty));
        }
        for (String idParty : lstPartyRead == null ? List.<String>of() : lstPartyRead) {
            lstRight.add(right(descRight, "can_read_as", idParty));
        }
        if (!lstRight.isEmpty())
            DynamicCall.set(bldReq, "rights", lstRight);

        DynamicMessage rsp = call.call(strMethod, bldReq.build());
        Message msgUser = msgOf(rsp, "user");
        if (msgUser == null)
            throw new LedgerException("the participant created a user and described none");

        return new UserInfo(strOf(msgUser, "id"), strOf(msgUser, "primary_party"), false,
                lstPartyAct == null ? List.of() : List.copyOf(lstPartyAct),
                lstPartyRead == null ? List.of() : List.copyOf(lstPartyRead));
    }


    /**
     * One user by id. A READ, so no READ_ONLY refusal.
     *
     * toUser makes the rights call itself, so this reads the User message and
     * hands the id on rather than mapping rights twice.
     */
    @Override
    public UserInfo user(String idUser) {
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user id is required");

        DynamicMessage.Builder bld = call.builderFor(M_GET_USER);
        DynamicCall.set(bld, "user_id", idUser);

        DynamicMessage rsp = call.call(M_GET_USER, bld.build());
        Message msgUser = msgOf(rsp, "user");
        if (msgUser == null)
            throw new LedgerException("the participant answered GetUser and described no user");

        return toUser(strOf(msgUser, "id"), strOf(msgUser, "primary_party"));
    }


    /**
     * Deletes a ledger user. The parties it could act as are not touched: a
     * user is a credential and the parties outlive it.
     */
    @Override
    public void deleteUser(String idUser) {
        refuseIfReadOnly("delete a ledger user");
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user id is required");

        DynamicMessage.Builder bld = call.builderFor(M_DELETE_USER);
        DynamicCall.set(bld, "user_id", idUser);
        call.call(M_DELETE_USER, bld.build());
    }


    @Override
    public UserInfo grantUserRights(String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        refuseIfReadOnly("grant rights to a ledger user");
        return rights(M_GRANT_RIGHTS, idUser, lstPartyAct, lstPartyRead);
    }


    @Override
    public UserInfo revokeUserRights(String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {
        refuseIfReadOnly("revoke rights from a ledger user");
        return rights(M_REVOKE_RIGHTS, idUser, lstPartyAct, lstPartyRead);
    }


    /**
     * Grant and revoke, which differ only in the method name.
     *
     * The response carries newly_granted_rights / newly_revoked_rights, which
     * is NOT what was asked for - a right already held comes back absent, and
     * so does one declined. It is therefore ignored and the user is read back,
     * because reporting a permission on the strength of having requested it is
     * the one claim a caller must never be given.
     *
     * v2 Right has seven kinds where v1 has four. Only can_act_as and
     * can_read_as are constructed here, so the extra kinds are unreachable and
     * the grammar above stays generation-neutral - S-2.
     */
    private UserInfo rights(String strMethod, String idUser, List<String> lstPartyAct,
            List<String> lstPartyRead) {

        if (idUser == null || idUser.isBlank())
            throw new LedgerException("a user id is required");

        DynamicMessage.Builder bldReq = call.builderFor(strMethod);
        DynamicCall.set(bldReq, "user_id", idUser);

        Descriptors.Descriptor descRight = fieldOfType(bldReq, "rights");
        List<Message> lstRight = new ArrayList<>();
        for (String idParty : lstPartyAct == null ? List.<String>of() : lstPartyAct) {
            lstRight.add(right(descRight, "can_act_as", idParty));
        }
        for (String idParty : lstPartyRead == null ? List.<String>of() : lstPartyRead) {
            lstRight.add(right(descRight, "can_read_as", idParty));
        }
        if (lstRight.isEmpty())
            throw new LedgerException("a rights change must name at least one right");

        DynamicCall.set(bldReq, "rights", lstRight);
        call.call(strMethod, bldReq.build());

        return user(idUser);
    }


    /**
     * The current ledger end, rendered as a decimal string.
     *
     * v2 reports an int64 where v1 reports an opaque string, and the SPI is
     * Text so that one shape serves both. Rendering rather than widening the
     * SPI keeps {@link com.raposza.spi.Capability#OFFSET_NUMERIC} the only
     * place that claims the string is orderable as a number.
     */
    @Override
    public String ledgerEnd() {
        return Long.toString(longOf(call.call(M_LEDGER_END), "offset"));
    }


    /**
     * Prunes the participant. IRREVERSIBLE.
     *
     * The offset arrives as text because v1 has no other form, and is PARSED
     * here rather than coerced: a string that is not a v2 offset fails the call
     * instead of pruning whatever Long.parseLong would have made of it.
     */
    @Override
    public void prune(String offsetUpToInclusive, boolean flagDivulged) {
        refuseIfReadOnly("prune the participant");
        if (offsetUpToInclusive == null || offsetUpToInclusive.isBlank())
            throw new LedgerException("pruning needs an offset; there is no prune-everything form");

        long nUpTo;
        try {
            nUpTo = Long.parseLong(offsetUpToInclusive.trim());
        }
        catch (NumberFormatException ex) {
            throw new LedgerException("'" + offsetUpToInclusive + "' is not a Ledger API v2"
                    + " offset; v2 offsets are integers", ex);
        }

        DynamicMessage.Builder bld = call.builderFor(M_PRUNE);
        DynamicCall.set(bld, "prune_up_to", nUpTo);
        DynamicCall.set(bld, "prune_all_divulged_contracts", flagDivulged);
        call.call(M_PRUNE, bld.build());
    }


    private static Message right(Descriptors.Descriptor descRight, String nameKind,
            String idParty) {

        Descriptors.Descriptor descKind = typeOf(descRight, nameKind);
        DynamicMessage.Builder bldKind = DynamicMessage.newBuilder(descKind);
        DynamicCall.set(bldKind, "party", idParty);

        DynamicMessage.Builder bld = DynamicMessage.newBuilder(descRight);
        DynamicCall.set(bld, nameKind, bldKind.build());
        return bld.build();
    }


    /**
     * @param strFullService a fully qualified service name
     * @return whether the participant declared it - which across the 3.x line
     *         is a real question and not a formality
     */
    private boolean hasService(String strFullService) {
        for (Descriptors.ServiceDescriptor service : set.lstService()) {
            if (strFullService.equals(service.getFullName()))
                return true;
        }
        return false;
    }


    @Override
    public void close() {
        channel.close();
    }


    private void refuseIfReadOnly(String strWhat) {
        if (profile.mode() != AccessMode.READ_WRITE) {
            throw new LedgerException("profile '" + profile.nameDisplay() + "' is "
                    + profile.mode() + " and may not " + strWhat);
        }
    }


    /**
     * The event format: which parties see what, verbose ALWAYS.
     *
     * Without verbose the participant omits record field labels and record
     * identifiers at every depth, and the omission is silent - the payload
     * still decodes, into an unlabelled shape nothing can render. It is not an
     * option here for that reason.
     */
    private static Message eventFormat(List<String> lstPartyRead, List<DataId> lstTemplate,
            Descriptors.Descriptor descFormat) {

        DynamicMessage.Builder bld = DynamicMessage.newBuilder(descFormat);
        DynamicCall.set(bld, "verbose", true);

        Descriptors.Descriptor descFilters = typeOf(descFormat, "filters_for_any_party");
        Message msgFilters = filters(lstTemplate, descFilters);

        if (lstPartyRead == null || lstPartyRead.isEmpty()) {
            DynamicCall.set(bld, "filters_for_any_party", msgFilters);
            return bld.build();
        }

        // filters_by_party is a proto MAP, which on the wire is a repeated
        // entry message rather than a field that takes a java Map.
        Descriptors.FieldDescriptor fieldMap = descFormat.findFieldByName("filters_by_party");
        Descriptors.Descriptor descEntry = fieldMap.getMessageType();
        for (String idParty : lstPartyRead) {
            DynamicMessage.Builder bldEntry = DynamicMessage.newBuilder(descEntry);
            bldEntry.setField(descEntry.findFieldByName("key"), idParty);
            bldEntry.setField(descEntry.findFieldByName("value"), msgFilters);
            bld.addRepeatedField(fieldMap, bldEntry.build());
        }
        return bld.build();
    }


    private static Message filters(List<DataId> lstTemplate, Descriptors.Descriptor descFilters) {
        DynamicMessage.Builder bld = DynamicMessage.newBuilder(descFilters);
        Descriptors.FieldDescriptor fieldCumulative = descFilters.findFieldByName("cumulative");
        Descriptors.Descriptor descCumulative = fieldCumulative.getMessageType();

        if (lstTemplate == null || lstTemplate.isEmpty()) {
            DynamicMessage.Builder bldCum = DynamicMessage.newBuilder(descCumulative);
            DynamicCall.set(bldCum, "wildcard_filter", DynamicMessage.newBuilder(
                    typeOf(descCumulative, "wildcard_filter")).build());
            bld.addRepeatedField(fieldCumulative, bldCum.build());
            return bld.build();
        }

        for (DataId idTemplate : lstTemplate) {
            Descriptors.Descriptor descTemplate = typeOf(descCumulative, "template_filter");
            DynamicMessage.Builder bldTemplate = DynamicMessage.newBuilder(descTemplate);
            DynamicCall.set(bldTemplate, "template_id",
                    ProtoValues.fromDataId(idTemplate,
                            DynamicMessage.newBuilder(typeOf(descTemplate, "template_id"))));

            DynamicMessage.Builder bldCum = DynamicMessage.newBuilder(descCumulative);
            DynamicCall.set(bldCum, "template_filter", bldTemplate.build());
            bld.addRepeatedField(fieldCumulative, bldCum.build());
        }
        return bld.build();
    }


    private static Message transactionFormat(List<String> lstParty,
            Descriptors.Descriptor descFormat) {

        DynamicMessage.Builder bld = DynamicMessage.newBuilder(descFormat);
        DynamicCall.set(bld, "event_format",
                eventFormat(lstParty, List.of(), typeOf(descFormat, "event_format")));

        Descriptors.FieldDescriptor fieldShape = descFormat.findFieldByName("transaction_shape");
        bld.setField(fieldShape, fieldShape.getEnumType().findValueByName(STR_SHAPE_EFFECTS));
        return bld.build();
    }


    private static Message updateFormat(List<String> lstParty,
            Descriptors.Descriptor descFormat) {

        DynamicMessage.Builder bld = DynamicMessage.newBuilder(descFormat);
        DynamicCall.set(bld, "include_transactions",
                transactionFormat(lstParty, typeOf(descFormat, "include_transactions")));
        return bld.build();
    }


    private static Descriptors.Descriptor fieldOfType(DynamicMessage.Builder bld,
            String strField) {
        return typeOf(bld.getDescriptorForType(), strField);
    }


    private static Descriptors.Descriptor typeOf(Descriptors.Descriptor descType,
            String strField) {

        Descriptors.FieldDescriptor field = descType.findFieldByName(strField);
        if (field == null) {
            throw new LedgerException(descType.getFullName() + " has no field " + strField
                    + "; it has " + DynamicCall.lstFieldName(descType));
        }
        return field.getMessageType();
    }


    private static boolean has(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        return field != null;
    }


    private static String strOf(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        return field == null ? "" : String.valueOf(msg.getField(field));
    }


    private static long longOf(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        return field == null ? 0L : ((Number) msg.getField(field)).longValue();
    }


    private static int intOf(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        return field == null ? 0 : ((Number) msg.getField(field)).intValue();
    }


    private static boolean boolOf(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        return field != null && Boolean.TRUE.equals(msg.getField(field));
    }


    private static List<String> lstStr(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        if (field == null)
            return List.of();

        List<String> lstOut = new ArrayList<>();
        int cntItem = msg.getRepeatedFieldCount(field);
        for (int idxItem = 0; idxItem < cntItem; idxItem++) {
            lstOut.add((String) msg.getRepeatedField(field, idxItem));
        }
        return List.copyOf(lstOut);
    }


    /**
     * @return the message on a singular message field, or null when it is not
     *         SET - which for a proto3 message field is a real distinction and
     *         not the same as an empty one
     */
    private static Message msgOf(Message msg, String strField) {
        Descriptors.FieldDescriptor field =
                msg.getDescriptorForType().findFieldByName(strField);
        if (field == null || !msg.hasField(field))
            return null;
        return (Message) msg.getField(field);
    }



    /** @return whichever member of the message's single oneof is set */
    private static Message anyOneOf(Message msg) {
        for (Map.Entry<Descriptors.FieldDescriptor, Object> entry
                : msg.getAllFields().entrySet()) {

            if (entry.getKey().getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE)
                return (Message) entry.getValue();
        }
        return null;
    }


    private static Optional<String> blankToEmpty(String str) {
        return str == null || str.isBlank() ? Optional.empty() : Optional.of(str);
    }


    private static Instant instantOf(Message msg, String strField) {
        Message msgStamp = msgOf(msg, strField);
        if (msgStamp == null)
            return Instant.EPOCH;
        return Instant.ofEpochSecond(longOf(msgStamp, "seconds"), longOf(msgStamp, "nanos"));
    }


    private static StatusRuntimeException statusOf(Throwable thrError) {
        Throwable thrWalk = thrError;
        while (thrWalk != null) {
            if (thrWalk instanceof StatusRuntimeException exStatus)
                return exStatus;
            thrWalk = thrWalk.getCause();
        }
        return null;
    }


    private static boolean isNotFound(RuntimeException ex) {
        StatusRuntimeException exStatus = statusOf(ex);
        return exStatus != null && exStatus.getStatus().getCode() == Status.Code.NOT_FOUND;
    }

}
