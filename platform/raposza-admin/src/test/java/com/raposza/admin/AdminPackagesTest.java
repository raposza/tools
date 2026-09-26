// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.admin;

import com.raposza.wire.DescriptorSet;
import com.raposza.wire.WireException;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DynamicMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding the admin package service among everything a participant advertises,
 * with no participant.
 *
 * The fixture carries BOTH `PackageService` types a real participant does - the
 * Ledger API one, which removes nothing, and the Canton admin one, which does -
 * under two different proto packages, because telling those apart is the whole
 * job.
 *
 * Author Claude/bentzn
 */
class AdminPackagesTest {

    private static final String STR_ADMIN_V30 = "com.digitalasset.canton.admin.participant.v30";

    private static final String STR_ADMIN_V0 = "com.digitalasset.canton.participant.admin.v0";

    private static final String STR_PKG_LEDGER = "com.daml.ledger.api.v2";


    private static FieldDescriptorProto.Builder fieldString(String strName, int nNumber) {
        return FieldDescriptorProto.newBuilder()
                .setName(strName)
                .setNumber(nNumber)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_STRING);
    }


    /**
     * @param strPackage the proto package
     * @param flagRemoval whether the service carries the removal verbs
     * @return one file declaring a PackageService in that package
     */
    private static ByteString bsPackageService(String strPackage, boolean flagRemoval) {
        DescriptorProto msgListRequest = DescriptorProto.newBuilder()
                .setName("ListDarsRequest")
                .build();
        DescriptorProto msgDar = DescriptorProto.newBuilder()
                .setName("DarDescription")
                .addField(fieldString("main", 1))
                .addField(fieldString("name", 2))
                .build();
        DescriptorProto msgListResponse = DescriptorProto.newBuilder()
                .setName("ListDarsResponse")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dars")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName("." + strPackage + ".DarDescription"))
                .build();
        DescriptorProto msgRemoveRequest = DescriptorProto.newBuilder()
                .setName("RemoveDarRequest")
                .addField(fieldString("dar_id", 1))
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("synchronize")
                        .setNumber(2)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_BOOL))
                .build();
        DescriptorProto msgRemoveResponse = DescriptorProto.newBuilder()
                .setName("RemoveDarResponse")
                .build();
        // THE SHAPE THAT BROKE THE FIRST RULE, on 3.5.12: the subject first,
        // a qualifier after it.
        DescriptorProto msgUnvetRequest = DescriptorProto.newBuilder()
                .setName("UnvetDarRequest")
                .addField(fieldString("main_package_id", 1))
                .addField(fieldString("synchronizer_id", 2))
                .build();

        ServiceDescriptorProto.Builder service = ServiceDescriptorProto.newBuilder()
                .setName("PackageService")
                .addMethod(MethodDescriptorProto.newBuilder()
                        .setName("ListDars")
                        .setInputType("." + strPackage + ".ListDarsRequest")
                        .setOutputType("." + strPackage + ".ListDarsResponse"));
        if (flagRemoval) {
            service.addMethod(MethodDescriptorProto.newBuilder()
                    .setName("RemoveDar")
                    .setInputType("." + strPackage + ".RemoveDarRequest")
                    .setOutputType("." + strPackage + ".RemoveDarResponse"));
            service.addMethod(MethodDescriptorProto.newBuilder()
                    .setName("UnvetDar")
                    .setInputType("." + strPackage + ".UnvetDarRequest")
                    .setOutputType("." + strPackage + ".RemoveDarResponse"));
        }

        return FileDescriptorProto.newBuilder()
                .setName(strPackage.replace('.', '/') + "/package_service.proto")
                .setPackage(strPackage)
                .setSyntax("proto3")
                .addMessageType(msgListRequest)
                .addMessageType(msgDar)
                .addMessageType(msgListResponse)
                .addMessageType(msgRemoveRequest)
                .addMessageType(msgRemoveResponse)
                .addMessageType(msgUnvetRequest)
                .addService(service)
                .build()
                .toByteString();
    }


    private static DescriptorSet setOf(String strAdminPackage) {
        DescriptorSet set = new DescriptorSet();
        set.add(bsPackageService(STR_PKG_LEDGER, false));
        set.add(bsPackageService(strAdminPackage, true));
        return set;
    }


    @Test
    void theAdminServiceIsFoundOnTheThreeLine() {
        assertEquals(STR_ADMIN_V30 + ".PackageService",
                AdminPackages.strServiceIn(setOf(STR_ADMIN_V30)));
    }


    @Test
    void theSameShapeIsFoundOnTheTwoLine() {
        assertEquals(STR_ADMIN_V0 + ".PackageService",
                AdminPackages.strServiceIn(setOf(STR_ADMIN_V0)));
    }


    @Test
    void aLedgerApiPackageServiceAloneIsNotTakenForIt() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsPackageService(STR_PKG_LEDGER, false));

        WireException ex = assertThrows(WireException.class,
                () -> AdminPackages.strServiceIn(set));
        assertTrue(ex.getMessage().contains(STR_PKG_LEDGER), ex.getMessage());
    }


    @Test
    void theIdGoesInTheOneStringFieldWhateverItIsCalled() {
        DescriptorSet set = setOf(STR_ADMIN_V30);
        DynamicMessage msg = AdminPackages.msgFor(set,
                STR_ADMIN_V30 + ".PackageService/RemoveDar", "1220abcd");

        assertEquals("1220abcd", msg.getField(msg.getDescriptorForType()
                .findFieldByName("dar_id")));
    }


    @Test
    void theFirstStringFieldWinsWhenAQualifierFollowsIt() {
        DescriptorSet set = setOf(STR_ADMIN_V30);
        DynamicMessage msg = AdminPackages.msgFor(set,
                STR_ADMIN_V30 + ".PackageService/UnvetDar", "1220abcd");

        assertEquals("1220abcd", msg.getField(msg.getDescriptorForType()
                .findFieldByName("main_package_id")));
        assertEquals("", msg.getField(msg.getDescriptorForType()
                .findFieldByName("synchronizer_id")));
    }


    @Test
    void aRequestWithNoStringFieldIsRefusedByName() {
        DescriptorSet set = setOf(STR_ADMIN_V30);

        WireException ex = assertThrows(WireException.class,
                () -> AdminPackages.msgFor(set,
                        STR_ADMIN_V30 + ".PackageService/ListDars", "1220abcd"));
        assertTrue(ex.getMessage().contains("ListDarsRequest"), ex.getMessage());
    }


    @Test
    void aDarRowCarriesEveryFieldTheVersionHas() {
        DescriptorSet set = setOf(STR_ADMIN_V30);
        DynamicMessage msgDar = DynamicMessage.newBuilder(
                set.fileFor(STR_ADMIN_V30.replace('.', '/') + "/package_service.proto")
                        .findMessageTypeByName("DarDescription"))
                .build();

        assertEquals(java.util.List.of("main", "name"),
                java.util.List.copyOf(AdminPackages.mapOf(msgDar).keySet()));
    }
}
