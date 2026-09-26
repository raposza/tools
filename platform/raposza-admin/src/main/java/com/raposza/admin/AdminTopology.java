// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.admin;

import com.raposza.wire.DescriptorSet;
import com.raposza.wire.DynamicCall;
import com.raposza.wire.ServerReflection;
import com.raposza.wire.WireException;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import io.grpc.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which packages this participant has VETTED, off the topology admin API.
 *
 * <h2>Why the topology service and not the package one</h2>
 *
 * `ListDars` describes DARs; vetting is a property of PACKAGES in the topology
 * state, so the DAR table cannot show it and Vet and Unvet would be
 * invisible. The Ledger API grew a `ListVettedPackages` of its own, but that
 * is a
 * different port and a token whenever authentication is on, and it does not
 * exist on the 2.x line at all. The topology manager's read service carries
 * the same state on the SAME plaintext admin channel every other call here
 * uses, under `...topology.admin.v0` and `...topology.admin.v30`.
 *
 * <h2>EVERY STORE IS ASKED, because the vetting is not in the authorized
 * one</h2>
 *
 * Measured on 3.5.12. Asking the
 * authorized store gets an empty answer and reports every DAR as unvetted.
 * The participant's log says why: the `VettedPackages` transactions are
 * written by the SynchronizerTopologyManager and land in the SYNCHRONIZER
 * store, while the authorized store holds the node's own identity - its
 * namespace delegation, its key mapping and its trust certificate.
 *
 * Rather than name a store, the service is asked which stores it has and each
 * one is queried in turn; the answer is their union. That needs no synchronizer
 * id, survives a participant on several, and costs one extra call. A store that
 * refuses is skipped rather than losing the whole answer.
 *
 * <h2>The query is built from the descriptor, and asks for as little as it
 * can</h2>
 *
 * A `BaseQuery` carries a store, a time query and several filters. Only two
 * are set: the head state, and the store - and each only when the version
 * declares it, in whichever of the two shapes it declares. Everything else is
 * left at its default, which is the widest answer the service gives.
 *
 * <h2>Package ids are collected BY FIELD NAME, at any depth</h2>
 *
 * The mapping is a repeated string of package ids on one line and a repeated
 * message with a `package_id` inside it on the other. Rather than encode
 * either, the response is walked and every string field called `package_id` or
 * `package_ids` - or ending in one of those - is collected. A version that
 * nests it one level deeper is read anyway.
 *
 * Author Claude/bentzn
 */
public final class AdminTopology {

    private static final Logger LOG = LoggerFactory.getLogger(AdminTopology.class);

    /** The simple name both generations give the service. */
    public static final String STR_SERVICE = "TopologyManagerReadService";

    public static final String STR_LIST_VETTED = "ListVettedPackages";

    public static final String STR_LIST_STORES = "ListAvailableStores";

    /** The store asked for when the service will not say which it has. */
    private static final String STR_STORE_AUTHORIZED = "Authorized";

    private static final String STR_FIELD_QUERY = "base_query";

    private static final String STR_FIELD_HEAD = "head_state";

    private static final String STR_FIELD_STORE = "store";

    private static final String STR_FIELD_STORE_2X = "filter_store";

    private static final String STR_FIELD_AUTHORIZED = "authorized";

    private final DescriptorSet set;

    private final DynamicCall call;

    private final String strService;


    /**
     * @param channel an open channel to the participant's ADMIN api
     * @param setNew the descriptors that channel advertised
     */
    public AdminTopology(Channel channel, DescriptorSet setNew) {
        this.set = setNew;
        this.call = DynamicCall.of(channel, setNew);
        this.strService = strServiceIn(setNew);
    }


    /**
     * @param channel an open channel to the participant's ADMIN api
     * @return a facade over whatever that participant advertises
     */
    public static AdminTopology of(Channel channel) {
        return new AdminTopology(channel, ServerReflection.of(channel).descriptorSet());
    }


    /**
     * @return the fully qualified service this participant carries
     */
    public String strService() {
        return strService;
    }


    /**
     * @param setHere the descriptors a participant advertised
     * @return the topology read service's full name
     * @throws WireException when no advertised service has the shape
     */
    public static String strServiceIn(DescriptorSet setHere) {
        List<String> lstSeen = new ArrayList<>();
        for (Descriptors.ServiceDescriptor service : setHere.lstService()) {
            if (!STR_SERVICE.equals(service.getName()))
                continue;
            lstSeen.add(service.getFullName());
            if (service.findMethodByName(STR_LIST_VETTED) != null)
                return service.getFullName();
        }
        throw new WireException("no advertised " + STR_SERVICE + " declares "
                + STR_LIST_VETTED + "; saw " + lstSeen);
    }


    /**
     * @return every package id this participant reports as vetted, in any of
     *         its stores
     */
    public Set<String> setPackageIdVetted() {
        String strFullMethod = strService + "/" + STR_LIST_VETTED;
        Set<String> setOut = new LinkedHashSet<>();

        for (Object objStore : lstStore()) {
            try {
                Message msgResponse = call.call(strFullMethod,
                        msgQuery(set, strFullMethod, objStore));
                collectPackageId(msgResponse, setOut);
            }
            catch (RuntimeException ex) {
                LOG.info("store {} answered no vetting state: {}", objStore, ex.getMessage());
            }
        }
        LOG.info("vetted packages: {}", setOut.size());
        return setOut;
    }


    /**
     * The stores to ask, as the service itself names them.
     *
     * @return one entry per store - a string on the 2.x line, a `StoreId`
     *         message on the 3.x one - or the authorized store alone when the
     *         service will not say
     */
    public List<Object> lstStore() {
        List<Object> lstOut = new ArrayList<>();
        Descriptors.ServiceDescriptor service = set.serviceFor(strService);
        if (service.findMethodByName(STR_LIST_STORES) == null) {
            lstOut.add(STR_STORE_AUTHORIZED);
            return lstOut;
        }

        try {
            Message msgResponse = call.call(strService + "/" + STR_LIST_STORES, null);
            for (Descriptors.FieldDescriptor field
                    : msgResponse.getDescriptorForType().getFields()) {
                if (!field.isRepeated())
                    continue;
                int cntItem = msgResponse.getRepeatedFieldCount(field);
                for (int idxItem = 0; idxItem < cntItem; idxItem++) {
                    lstOut.add(msgResponse.getRepeatedField(field, idxItem));
                }
            }
        }
        catch (RuntimeException ex) {
            LOG.info("{} was refused: {}", STR_LIST_STORES, ex.getMessage());
        }

        if (lstOut.isEmpty())
            lstOut.add(STR_STORE_AUTHORIZED);
        LOG.info("topology stores: {}", lstOut.size());
        return lstOut;
    }


    /**
     * @param setHere the descriptors
     * @param strFullMethod the list method
     * @param objStore the store to ask, as {@link #lstStore} reported it
     * @return its request
     */
    public static DynamicMessage msgQuery(DescriptorSet setHere, String strFullMethod,
            Object objStore) {
        Descriptors.Descriptor descRequest = setHere.methodFor(strFullMethod).getInputType();
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descRequest);

        Descriptors.FieldDescriptor fieldQuery = fieldBaseQuery(descRequest);
        if (fieldQuery != null)
            builder.setField(fieldQuery, msgBaseQuery(fieldQuery.getMessageType(), objStore));
        return builder.build();
    }


    /**
     * @param descRequest a list request
     * @return its query field, by name where there is one and by shape
     *         otherwise, or null when it carries no query at all
     */
    public static Descriptors.FieldDescriptor fieldBaseQuery(
            Descriptors.Descriptor descRequest) {
        Descriptors.FieldDescriptor fieldNamed = descRequest.findFieldByName(STR_FIELD_QUERY);
        if (fieldNamed != null && !fieldNamed.isRepeated()
                && fieldNamed.getType() == Descriptors.FieldDescriptor.Type.MESSAGE)
            return fieldNamed;

        for (Descriptors.FieldDescriptor field : descRequest.getFields()) {
            if (!field.isRepeated()
                    && field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE)
                return field;
        }
        return null;
    }


    /**
     * @param descQuery the query type
     * @param objStore the store to ask for
     * @return a query for that store's head state, carrying only the fields
     *         this version actually declares
     */
    public static DynamicMessage msgBaseQuery(Descriptors.Descriptor descQuery,
            Object objStore) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descQuery);

        Descriptors.FieldDescriptor fieldHead = descQuery.findFieldByName(STR_FIELD_HEAD);
        if (fieldHead != null && !fieldHead.isRepeated()
                && fieldHead.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
            builder.setField(fieldHead,
                    DynamicMessage.getDefaultInstance(fieldHead.getMessageType()));
        }

        Descriptors.FieldDescriptor fieldStore = descQuery.findFieldByName(STR_FIELD_STORE);
        if (fieldStore == null)
            fieldStore = descQuery.findFieldByName(STR_FIELD_STORE_2X);
        if (fieldStore == null || fieldStore.isRepeated())
            return builder.build();

        if (fieldStore.getType() == Descriptors.FieldDescriptor.Type.STRING) {
            builder.setField(fieldStore,
                    objStore instanceof String ? objStore : STR_STORE_AUTHORIZED);
            return builder.build();
        }
        if (fieldStore.getType() != Descriptors.FieldDescriptor.Type.MESSAGE)
            return builder.build();

        Descriptors.Descriptor descStore = fieldStore.getMessageType();
        if (objStore instanceof Message
                && descStore.equals(((Message) objStore).getDescriptorForType())) {
            builder.setField(fieldStore, objStore);
            return builder.build();
        }

        Descriptors.FieldDescriptor fieldAuth = descStore.findFieldByName(STR_FIELD_AUTHORIZED);
        if (fieldAuth == null || fieldAuth.isRepeated()
                || fieldAuth.getType() != Descriptors.FieldDescriptor.Type.MESSAGE)
            return builder.build();

        DynamicMessage msgStore = DynamicMessage.newBuilder(descStore)
                .setField(fieldAuth, DynamicMessage.getDefaultInstance(fieldAuth.getMessageType()))
                .build();
        builder.setField(fieldStore, msgStore);
        return builder.build();
    }


    /**
     * Walks a response and takes every package id out of it.
     *
     * A singular message field is only descended into when it is SET. An unset
     * one answers with its default instance, and a type that holds itself
     * would then be walked for ever.
     *
     * @param msg the message to walk
     * @param setOut where the ids land
     */
    public static void collectPackageId(Message msg, Set<String> setOut) {
        for (Descriptors.FieldDescriptor field : msg.getDescriptorForType().getFields()) {
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                if (field.isRepeated()) {
                    int cntItem = msg.getRepeatedFieldCount(field);
                    for (int idxItem = 0; idxItem < cntItem; idxItem++) {
                        collectPackageId((Message) msg.getRepeatedField(field, idxItem), setOut);
                    }
                }
                else if (msg.hasField(field)) {
                    collectPackageId((Message) msg.getField(field), setOut);
                }
                continue;
            }

            if (field.getType() != Descriptors.FieldDescriptor.Type.STRING
                    || !flagPackageId(field.getName()))
                continue;

            if (field.isRepeated()) {
                int cntItem = msg.getRepeatedFieldCount(field);
                for (int idxItem = 0; idxItem < cntItem; idxItem++) {
                    addId(setOut, String.valueOf(msg.getRepeatedField(field, idxItem)));
                }
            }
            else {
                addId(setOut, String.valueOf(msg.getField(field)));
            }
        }
    }


    /**
     * @param strField a field name
     * @return whether it holds a package id
     */
    public static boolean flagPackageId(String strField) {
        return "package_id".equals(strField) || "package_ids".equals(strField)
                || strField.endsWith("_package_id") || strField.endsWith("_package_ids");
    }


    private static void addId(Set<String> setOut, String strId) {
        if (strId != null && !strId.isBlank())
            setOut.add(strId);
    }
}
