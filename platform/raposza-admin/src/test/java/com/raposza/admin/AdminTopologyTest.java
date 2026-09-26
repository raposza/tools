// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.admin;

import com.raposza.wire.DescriptorSet;
import com.raposza.wire.WireException;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asking the topology manager which packages are vetted, against both shapes,
 * with no participant.
 *
 * The 3.x fixture carries a `StoreId` message with an `authorized` variant and
 * a repeated `VettedPackage` holding `package_id`. The 2.x one carries a
 * `filter_store` string and a repeated `package_ids`. Neither is written into
 * the client.
 *
 * Author Claude/bentzn
 */
class AdminTopologyTest {

    private static final String STR_V30 = "com.digitalasset.canton.topology.admin.v30";

    private static final String STR_V0 = "com.digitalasset.canton.topology.admin.v0";


    private static FieldDescriptorProto.Builder field(String strName, int nNumber,
            FieldDescriptorProto.Type type) {
        return FieldDescriptorProto.newBuilder()
                .setName(strName)
                .setNumber(nNumber)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(type);
    }


    private static FieldDescriptorProto.Builder fieldMessage(String strName, int nNumber,
            String strType, boolean flagRepeated) {
        return FieldDescriptorProto.newBuilder()
                .setName(strName)
                .setNumber(nNumber)
                .setLabel(flagRepeated ? FieldDescriptorProto.Label.LABEL_REPEATED
                        : FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(strType);
    }


    /**
     * @param strPackage the proto package
     * @param flagStoreMessage whether the store is a message, as 3.x has it,
     *        rather than a filter string as 2.x has it
     * @return one file declaring a TopologyManagerReadService
     */
    private static FileDescriptorProto proto(String strPackage, boolean flagStoreMessage) {
        FileDescriptorProto.Builder file = FileDescriptorProto.newBuilder()
                .setName(strPackage.replace('.', '/') + "/topology_manager_read_service.proto")
                .setPackage(strPackage)
                .setSyntax("proto3");

        file.addMessageType(DescriptorProto.newBuilder().setName("HeadState"));
        file.addMessageType(DescriptorProto.newBuilder().setName("Authorized"));
        file.addMessageType(DescriptorProto.newBuilder()
                .setName("StoreId")
                .addField(fieldMessage("authorized", 1, "." + strPackage + ".Authorized", false))
                .addField(field("synchronizer", 2, FieldDescriptorProto.Type.TYPE_STRING)));

        DescriptorProto.Builder msgQuery = DescriptorProto.newBuilder()
                .setName("BaseQuery")
                .addField(field("proposals", 3, FieldDescriptorProto.Type.TYPE_BOOL))
                .addField(fieldMessage("head_state", 6, "." + strPackage + ".HeadState", false));
        if (flagStoreMessage)
            msgQuery.addField(fieldMessage("store", 1, "." + strPackage + ".StoreId", false));
        else
            msgQuery.addField(field("filter_store", 1, FieldDescriptorProto.Type.TYPE_STRING));
        file.addMessageType(msgQuery);

        file.addMessageType(DescriptorProto.newBuilder()
                .setName("ListVettedPackagesRequest")
                .addField(fieldMessage("base_query", 1, "." + strPackage + ".BaseQuery", false))
                .addField(field("filter_participant", 2,
                        FieldDescriptorProto.Type.TYPE_STRING)));

        DescriptorProto.Builder msgMapping = DescriptorProto.newBuilder()
                .setName("VettedPackages")
                .addField(field("participant", 1, FieldDescriptorProto.Type.TYPE_STRING));
        if (flagStoreMessage) {
            file.addMessageType(DescriptorProto.newBuilder()
                    .setName("VettedPackage")
                    .addField(field("package_id", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("valid_from", 2, FieldDescriptorProto.Type.TYPE_STRING)));
            msgMapping.addField(fieldMessage("packages", 2,
                    "." + strPackage + ".VettedPackage", true));
        }
        else {
            msgMapping.addField(FieldDescriptorProto.newBuilder()
                    .setName("package_ids")
                    .setNumber(2)
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING));
        }
        file.addMessageType(msgMapping);

        file.addMessageType(DescriptorProto.newBuilder()
                .setName("Result")
                .addField(field("store", 1, FieldDescriptorProto.Type.TYPE_STRING))
                .addField(fieldMessage("item", 2, "." + strPackage + ".VettedPackages", false)));
        file.addMessageType(DescriptorProto.newBuilder()
                .setName("ListVettedPackagesResponse")
                .addField(fieldMessage("results", 1, "." + strPackage + ".Result", true)));

        return file.addService(ServiceDescriptorProto.newBuilder()
                .setName("TopologyManagerReadService")
                .addMethod(MethodDescriptorProto.newBuilder()
                        .setName("ListVettedPackages")
                        .setInputType("." + strPackage + ".ListVettedPackagesRequest")
                        .setOutputType("." + strPackage + ".ListVettedPackagesResponse")))
                .build();
    }


    private static DescriptorSet setOf(String strPackage, boolean flagStoreMessage) {
        DescriptorSet set = new DescriptorSet();
        set.add(proto(strPackage, flagStoreMessage).toByteString());
        return set;
    }


    private static Descriptors.Descriptor descIn(DescriptorSet set, String strPackage,
            String strMessage) {
        return set.fileFor(strPackage.replace('.', '/')
                + "/topology_manager_read_service.proto").findMessageTypeByName(strMessage);
    }


    @Test
    void theServiceIsFoundOnTheThreeLine() {
        assertEquals(STR_V30 + ".TopologyManagerReadService",
                AdminTopology.strServiceIn(setOf(STR_V30, true)));
    }


    @Test
    void theServiceIsFoundOnTheTwoLine() {
        assertEquals(STR_V0 + ".TopologyManagerReadService",
                AdminTopology.strServiceIn(setOf(STR_V0, false)));
    }


    @Test
    void aTopologyServiceWithoutTheListIsRefused() {
        DescriptorSet set = new DescriptorSet();
        set.add(FileDescriptorProto.newBuilder()
                .setName("x/t.proto")
                .setPackage("x")
                .setSyntax("proto3")
                .addMessageType(DescriptorProto.newBuilder().setName("Nothing"))
                .addService(ServiceDescriptorProto.newBuilder()
                        .setName("TopologyManagerReadService")
                        .addMethod(MethodDescriptorProto.newBuilder()
                                .setName("ListAll")
                                .setInputType(".x.Nothing")
                                .setOutputType(".x.Nothing")))
                .build()
                .toByteString());

        WireException ex = assertThrows(WireException.class,
                () -> AdminTopology.strServiceIn(set));
        assertTrue(ex.getMessage().contains("ListVettedPackages"), ex.getMessage());
    }


    @Test
    void theThreeLineQueryTakesTheStoreItIsGiven() {
        DescriptorSet set = setOf(STR_V30, true);
        Descriptors.Descriptor descStore = descIn(set, STR_V30, "StoreId");
        DynamicMessage msgSynchronizer = DynamicMessage.newBuilder(descStore)
                .setField(descStore.findFieldByName("synchronizer"), "synchronizer-1::1220")
                .build();
        DynamicMessage msg = AdminTopology.msgQuery(set,
                STR_V30 + ".TopologyManagerReadService/ListVettedPackages",
                msgSynchronizer);

        Descriptors.FieldDescriptor fieldQuery =
                msg.getDescriptorForType().findFieldByName("base_query");
        assertTrue(msg.hasField(fieldQuery));

        DynamicMessage msgQuery = (DynamicMessage) msg.getField(fieldQuery);
        Descriptors.Descriptor descQuery = msgQuery.getDescriptorForType();
        assertTrue(msgQuery.hasField(descQuery.findFieldByName("head_state")));

        DynamicMessage msgStore =
                (DynamicMessage) msgQuery.getField(descQuery.findFieldByName("store"));
        assertEquals("synchronizer-1::1220",
                msgStore.getField(descStore.findFieldByName("synchronizer")));
    }


    @Test
    void theThreeLineQueryFallsBackToTheAuthorizedStore() {
        DescriptorSet set = setOf(STR_V30, true);
        DynamicMessage msg = AdminTopology.msgQuery(set,
                STR_V30 + ".TopologyManagerReadService/ListVettedPackages", "Authorized");

        DynamicMessage msgQuery = (DynamicMessage) msg.getField(
                msg.getDescriptorForType().findFieldByName("base_query"));
        DynamicMessage msgStore = (DynamicMessage) msgQuery.getField(
                msgQuery.getDescriptorForType().findFieldByName("store"));
        assertTrue(msgStore.hasField(
                msgStore.getDescriptorForType().findFieldByName("authorized")));
    }


    @Test
    void theTwoLineQueryNamesTheStoreAsAString() {
        DescriptorSet set = setOf(STR_V0, false);
        DynamicMessage msg = AdminTopology.msgQuery(set,
                STR_V0 + ".TopologyManagerReadService/ListVettedPackages",
                "synchronizer-1::1220");

        DynamicMessage msgQuery = (DynamicMessage) msg.getField(
                msg.getDescriptorForType().findFieldByName("base_query"));
        assertEquals("synchronizer-1::1220", msgQuery.getField(
                msgQuery.getDescriptorForType().findFieldByName("filter_store")));
    }


    @Test
    void aNestedPackageIdIsCollected() {
        DescriptorSet set = setOf(STR_V30, true);
        DynamicMessage msgPackage = DynamicMessage.newBuilder(
                descIn(set, STR_V30, "VettedPackage"))
                .setField(descIn(set, STR_V30, "VettedPackage").findFieldByName("package_id"),
                        "cafe01")
                .build();
        Descriptors.Descriptor descMapping = descIn(set, STR_V30, "VettedPackages");
        DynamicMessage msgMapping = DynamicMessage.newBuilder(descMapping)
                .addRepeatedField(descMapping.findFieldByName("packages"), msgPackage)
                .build();
        Descriptors.Descriptor descResult = descIn(set, STR_V30, "Result");
        DynamicMessage msgResult = DynamicMessage.newBuilder(descResult)
                .setField(descResult.findFieldByName("item"), msgMapping)
                .build();
        Descriptors.Descriptor descResponse = descIn(set, STR_V30,
                "ListVettedPackagesResponse");
        DynamicMessage msgResponse = DynamicMessage.newBuilder(descResponse)
                .addRepeatedField(descResponse.findFieldByName("results"), msgResult)
                .build();

        Set<String> setOut = new LinkedHashSet<>();
        AdminTopology.collectPackageId(msgResponse, setOut);
        assertEquals(Set.of("cafe01"), setOut);
    }


    @Test
    void aRepeatedStringOfPackageIdsIsCollected() {
        DescriptorSet set = setOf(STR_V0, false);
        Descriptors.Descriptor descMapping = descIn(set, STR_V0, "VettedPackages");
        DynamicMessage msgMapping = DynamicMessage.newBuilder(descMapping)
                .addRepeatedField(descMapping.findFieldByName("package_ids"), "cafe01")
                .addRepeatedField(descMapping.findFieldByName("package_ids"), "cafe02")
                .build();
        Descriptors.Descriptor descResult = descIn(set, STR_V0, "Result");
        DynamicMessage msgResult = DynamicMessage.newBuilder(descResult)
                .setField(descResult.findFieldByName("item"), msgMapping)
                .build();
        Descriptors.Descriptor descResponse = descIn(set, STR_V0, "ListVettedPackagesResponse");
        DynamicMessage msgResponse = DynamicMessage.newBuilder(descResponse)
                .addRepeatedField(descResponse.findFieldByName("results"), msgResult)
                .build();

        Set<String> setOut = new LinkedHashSet<>();
        AdminTopology.collectPackageId(msgResponse, setOut);
        assertEquals(Set.of("cafe01", "cafe02"), setOut);
    }


    @Test
    void aParticipantNameIsNotMistakenForAPackageId() {
        assertTrue(AdminTopology.flagPackageId("package_id"));
        assertTrue(AdminTopology.flagPackageId("package_ids"));
        assertTrue(AdminTopology.flagPackageId("main_package_id"));
        assertEquals(false, AdminTopology.flagPackageId("participant"));
        assertEquals(false, AdminTopology.flagPackageId("package_name"));
    }
}
