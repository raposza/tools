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
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building an upload request against BOTH shapes the vendor ships, with no
 * participant.
 *
 * The two are not variants of one message. 3.x takes a repeated
 * `{bytes, description}` beside two flags; 2.x takes one `bytes` and a
 * `filename`. Neither spelling is written into the client, so both are
 * fixtures here and the client is pointed at each in turn.
 *
 * Author Claude/bentzn
 */
class AdminUploadTest {

    private static final String STR_V30 = "com.digitalasset.canton.admin.participant.v30";

    private static final String STR_V0 = "com.digitalasset.canton.participant.admin.v0";

    private static final byte[] ARR_DAR = new byte[] {1, 2, 3, 4};


    private static FieldDescriptorProto.Builder field(String strName, int nNumber,
            FieldDescriptorProto.Type type) {
        return FieldDescriptorProto.newBuilder()
                .setName(strName)
                .setNumber(nNumber)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(type);
    }


    /**
     * @param strPackage the proto package
     * @param flagList whether the request takes a LIST of DARs, as 3.x does
     * @return one file declaring a PackageService with an upload method
     */
    private static FileDescriptorProto protoService(String strPackage, boolean flagList) {
        FileDescriptorProto.Builder file = FileDescriptorProto.newBuilder()
                .setName(strPackage.replace('.', '/') + "/package_service.proto")
                .setPackage(strPackage)
                .setSyntax("proto3");

        if (flagList) {
            file.addMessageType(DescriptorProto.newBuilder()
                    .setName("UploadDarData")
                    .addField(field("bytes", 1, FieldDescriptorProto.Type.TYPE_BYTES))
                    .addField(field("description", 2, FieldDescriptorProto.Type.TYPE_STRING)));
            file.addMessageType(DescriptorProto.newBuilder()
                    .setName("UploadDarRequest")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("dars")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName("." + strPackage + ".UploadDarData"))
                    .addField(field("vet_all_packages", 2,
                            FieldDescriptorProto.Type.TYPE_BOOL))
                    .addField(field("synchronize_vetting", 3,
                            FieldDescriptorProto.Type.TYPE_BOOL))
                    .addField(field("synchronizer_id", 4,
                            FieldDescriptorProto.Type.TYPE_STRING)));
        }
        else {
            file.addMessageType(DescriptorProto.newBuilder()
                    .setName("UploadDarRequest")
                    .addField(field("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
                    .addField(field("filename", 2, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("vet_all_packages", 3,
                            FieldDescriptorProto.Type.TYPE_BOOL))
                    .addField(field("synchronize_vetting", 4,
                            FieldDescriptorProto.Type.TYPE_BOOL)));
        }

        file.addMessageType(DescriptorProto.newBuilder()
                .setName("UploadDarResponse")
                .addField(field("dar_id", 1, FieldDescriptorProto.Type.TYPE_STRING)));
        file.addMessageType(DescriptorProto.newBuilder()
                .setName("ListDarsRequest"));
        file.addMessageType(DescriptorProto.newBuilder()
                .setName("ListDarsResponse"));

        ServiceDescriptorProto.Builder service = ServiceDescriptorProto.newBuilder()
                .setName("PackageService")
                .addMethod(MethodDescriptorProto.newBuilder()
                        .setName("ListDars")
                        .setInputType("." + strPackage + ".ListDarsRequest")
                        .setOutputType("." + strPackage + ".ListDarsResponse"))
                .addMethod(MethodDescriptorProto.newBuilder()
                        .setName("UploadDar")
                        .setInputType("." + strPackage + ".UploadDarRequest")
                        .setOutputType("." + strPackage + ".UploadDarResponse"));

        return file.addService(service).build();
    }


    private static DescriptorSet setOf(String strPackage, boolean flagList) {
        DescriptorSet set = new DescriptorSet();
        set.add(protoService(strPackage, flagList).toByteString());
        return set;
    }


    private static Descriptors.ServiceDescriptor serviceOf(DescriptorSet set,
            String strPackage) {
        return set.serviceFor(strPackage + ".PackageService");
    }


    @Test
    void theUploadMethodIsFoundOnTheThreeLine() {
        assertEquals("UploadDar", AdminPackages.strMethodUploadIn(
                serviceOf(setOf(STR_V30, true), STR_V30)));
    }


    @Test
    void theUploadMethodIsFoundOnTheTwoLine() {
        assertEquals("UploadDar", AdminPackages.strMethodUploadIn(
                serviceOf(setOf(STR_V0, false), STR_V0)));
    }


    @Test
    void theThreeLineRequestCarriesOneDarAndBothFlags() {
        DescriptorSet set = setOf(STR_V30, true);
        DynamicMessage msg = AdminPackages.msgUpload(set,
                STR_V30 + ".PackageService/UploadDar", ARR_DAR, "pet-shop.dar");

        Descriptors.Descriptor desc = msg.getDescriptorForType();
        Descriptors.FieldDescriptor fieldDars = desc.findFieldByName("dars");
        assertEquals(1, msg.getRepeatedFieldCount(fieldDars));

        Message msgItem = (Message) msg.getRepeatedField(fieldDars, 0);
        Descriptors.Descriptor descItem = msgItem.getDescriptorForType();
        assertEquals(ByteString.copyFrom(ARR_DAR),
                msgItem.getField(descItem.findFieldByName("bytes")));
        assertEquals("pet-shop.dar", msgItem.getField(descItem.findFieldByName("description")));

        assertEquals(Boolean.TRUE, msg.getField(desc.findFieldByName("vet_all_packages")));
        assertEquals(Boolean.TRUE, msg.getField(desc.findFieldByName("synchronize_vetting")));
        assertEquals("", msg.getField(desc.findFieldByName("synchronizer_id")));
    }


    @Test
    void theTwoLineRequestCarriesTheBytesAndTheFileName() {
        DescriptorSet set = setOf(STR_V0, false);
        DynamicMessage msg = AdminPackages.msgUpload(set,
                STR_V0 + ".PackageService/UploadDar", ARR_DAR, "pet-shop.dar");

        Descriptors.Descriptor desc = msg.getDescriptorForType();
        assertEquals(ByteString.copyFrom(ARR_DAR), msg.getField(desc.findFieldByName("data")));
        assertEquals("pet-shop.dar", msg.getField(desc.findFieldByName("filename")));
        assertEquals(Boolean.TRUE, msg.getField(desc.findFieldByName("vet_all_packages")));
        assertEquals(Boolean.TRUE, msg.getField(desc.findFieldByName("synchronize_vetting")));
    }


    @Test
    void aServiceWithNoDarPayloadIsRefusedByName() {
        DescriptorSet set = new DescriptorSet();
        FileDescriptorProto proto = FileDescriptorProto.newBuilder()
                .setName("x/package_service.proto")
                .setPackage("x")
                .setSyntax("proto3")
                .addMessageType(DescriptorProto.newBuilder().setName("Nothing"))
                .addService(ServiceDescriptorProto.newBuilder()
                        .setName("PackageService")
                        .addMethod(MethodDescriptorProto.newBuilder()
                                .setName("UploadDar")
                                .setInputType(".x.Nothing")
                                .setOutputType(".x.Nothing")))
                .build();
        set.add(proto.toByteString());

        WireException ex = assertThrows(WireException.class,
                () -> AdminPackages.strMethodUploadIn(serviceOf(set, "x")));
        assertTrue(ex.getMessage().contains("UploadDar"), ex.getMessage());
    }
}
