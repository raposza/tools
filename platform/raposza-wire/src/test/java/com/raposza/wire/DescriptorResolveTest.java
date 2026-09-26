// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning a captured descriptor set into something callable, with no server.
 *
 * The fixture is a service shaped like the one this exists for - a removal
 * method taking a message with an id and a flag, in a package that imports
 * another file - but it is BUILT HERE. A blob recorded off a participant would
 * make these assertions statements about that participant's Canton version
 * rather than about the resolution.
 *
 * Author Claude/bentzn
 */
class DescriptorResolveTest {

    private static final String STR_FILE_DEP = "vendor/common.proto";

    private static final String STR_FILE_MAIN = "vendor/admin/package_service.proto";

    private static final String STR_SERVICE = "vendor.admin.PackageService";


    private static ByteString bsDependency() {
        return FileDescriptorProto.newBuilder()
                .setName(STR_FILE_DEP)
                .setPackage("vendor")
                .setSyntax("proto3")
                .addMessageType(DescriptorProto.newBuilder()
                        .setName("DarDescription")
                        .addField(FieldDescriptorProto.newBuilder()
                                .setName("main")
                                .setNumber(1)
                                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                .setType(FieldDescriptorProto.Type.TYPE_STRING)))
                .build()
                .toByteString();
    }


    private static ByteString bsMain() {
        DescriptorProto msgRequest = DescriptorProto.newBuilder()
                .setName("RemoveDarRequest")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dar_id")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING))
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("synchronize")
                        .setNumber(2)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_BOOL))
                .build();

        DescriptorProto msgResponse = DescriptorProto.newBuilder()
                .setName("ListDarsResponse")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dars")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".vendor.DarDescription"))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName(STR_FILE_MAIN)
                .setPackage("vendor.admin")
                .setSyntax("proto3")
                .addDependency(STR_FILE_DEP)
                .addMessageType(msgRequest)
                .addMessageType(msgResponse)
                .addService(ServiceDescriptorProto.newBuilder()
                        .setName("PackageService")
                        .addMethod(MethodDescriptorProto.newBuilder()
                                .setName("RemoveDar")
                                .setInputType(".vendor.admin.RemoveDarRequest")
                                .setOutputType(".vendor.admin.ListDarsResponse")))
                .build()
                .toByteString();
    }


    private static DescriptorSet setBoth() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsDependency());
        set.add(bsMain());
        return set;
    }


    @Test
    void aFileResolvesWithItsImportWiredUnderneath() {
        Descriptors.FileDescriptor file = setBoth().fileFor(STR_FILE_MAIN);

        assertEquals(1, file.getDependencies().size());
        assertEquals(STR_FILE_DEP, file.getDependencies().get(0).getName());
    }


    @Test
    void aServiceIsFoundByItsProtoPackageAndNotByItsPath() {
        Descriptors.ServiceDescriptor service = setBoth().serviceFor(STR_SERVICE);

        assertEquals(STR_SERVICE, service.getFullName());
        assertNotNull(service.findMethodByName("RemoveDar"));
    }


    @Test
    void aMethodResolvesToItsRequestAndResponseTypes() {
        Descriptors.MethodDescriptor method = setBoth().methodFor(STR_SERVICE + "/RemoveDar");

        assertEquals("vendor.admin.RemoveDarRequest", method.getInputType().getFullName());
        assertEquals("vendor.admin.ListDarsResponse", method.getOutputType().getFullName());
    }


    @Test
    void anUncapturedImportIsRefusedByName() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsMain());

        WireException ex = assertThrows(WireException.class,
                () -> set.fileFor(STR_FILE_MAIN));
        assertTrue(ex.getMessage().contains(STR_FILE_DEP), ex.getMessage());
    }


    @Test
    void anUnknownMethodNamesTheOnesThatExist() {
        DescriptorSet set = setBoth();

        WireException ex = assertThrows(WireException.class,
                () -> set.methodFor(STR_SERVICE + "/UnvetDar"));
        assertTrue(ex.getMessage().contains("RemoveDar"), ex.getMessage());
    }


    @Test
    void aRequestIsBuiltByFieldName() {
        Descriptors.MethodDescriptor method = setBoth().methodFor(STR_SERVICE + "/RemoveDar");
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(method.getInputType());

        DynamicCall.set(builder, "dar_id", "abc123");
        DynamicCall.set(builder, "synchronize", Boolean.TRUE);
        DynamicMessage msg = builder.build();

        assertEquals("abc123", DynamicCall.strField(msg, "dar_id"));
        assertEquals("true", DynamicCall.strField(msg, "synchronize"));
    }


    @Test
    void anUnknownFieldNamesTheOnesThatExist() {
        Descriptors.MethodDescriptor method = setBoth().methodFor(STR_SERVICE + "/RemoveDar");
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(method.getInputType());

        WireException ex = assertThrows(WireException.class,
                () -> DynamicCall.set(builder, "darId", "abc123"));
        assertTrue(ex.getMessage().contains("dar_id"), ex.getMessage());
    }


    @Test
    void aRepeatedMessageFieldReadsBackAsAList() {
        DescriptorSet set = setBoth();
        Descriptors.Descriptor descResponse =
                set.methodFor(STR_SERVICE + "/RemoveDar").getOutputType();
        Descriptors.Descriptor descDar = set.fileFor(STR_FILE_DEP)
                .findMessageTypeByName("DarDescription");

        DynamicMessage.Builder builderDar = DynamicMessage.newBuilder(descDar);
        DynamicCall.set(builderDar, "main", "deadbeef");

        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descResponse);
        DynamicCall.set(builder, "dars", java.util.List.of(builderDar.build()));

        assertEquals(1, DynamicCall.lstMessage(builder.build(), "dars").size());
        assertEquals("deadbeef",
                DynamicCall.strField(DynamicCall.lstMessage(builder.build(), "dars").get(0),
                        "main"));
    }
}
