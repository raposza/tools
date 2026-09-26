// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DynamicMessage;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape guards on a streamed call, proved with no server.
 *
 * Every assertion here is about a refusal that happens BEFORE the wire, which
 * is why the channel throws if it is touched: a test that quietly opened a
 * connection would pass for a reason it does not state, and these guards exist
 * precisely so a wrong-shaped call never reaches a participant.
 *
 * What a drained stream actually returns is not testable here and is not
 * pretended to be. That needs a server and belongs against a running
 * participant.
 *
 * Author Claude/bentzn
 */
class DynamicStreamTest {

    private static final String STR_FILE = "vendor/state_service.proto";

    private static final String STR_SERVICE = "vendor.StateService";

    /** Reached only if a guard failed to fire. */
    private static final Channel CHANNEL_REFUSING = new Channel() {

        @Override
        public <ReqT, RspT> ClientCall<ReqT, RspT> newCall(
                MethodDescriptor<ReqT, RspT> method, CallOptions options) {
            throw new AssertionError("the wire was touched by " + method.getFullMethodName());
        }


        @Override
        public String authority() {
            return "test";
        }
    };


    private static ByteString bsFile() {
        DescriptorProto msgRequest = DescriptorProto.newBuilder()
                .setName("GetActiveContractsRequest")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("active_at_offset")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_INT64))
                .build();

        DescriptorProto msgResponse = DescriptorProto.newBuilder()
                .setName("GetActiveContractsResponse")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("workflow_id")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName(STR_FILE)
                .setPackage("vendor")
                .setSyntax("proto3")
                .addMessageType(msgRequest)
                .addMessageType(msgResponse)
                .addService(ServiceDescriptorProto.newBuilder()
                        .setName("StateService")
                        .addMethod(MethodDescriptorProto.newBuilder()
                                .setName("GetActiveContracts")
                                .setInputType(".vendor.GetActiveContractsRequest")
                                .setOutputType(".vendor.GetActiveContractsResponse")
                                .setServerStreaming(true))
                        .addMethod(MethodDescriptorProto.newBuilder()
                                .setName("GetLedgerEnd")
                                .setInputType(".vendor.GetActiveContractsRequest")
                                .setOutputType(".vendor.GetActiveContractsResponse"))
                        .addMethod(MethodDescriptorProto.newBuilder()
                                .setName("PushThings")
                                .setInputType(".vendor.GetActiveContractsRequest")
                                .setOutputType(".vendor.GetActiveContractsResponse")
                                .setClientStreaming(true)))
                .build()
                .toByteString();
    }


    private static DynamicCall callHere() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsFile());
        return new DynamicCall(CHANNEL_REFUSING, set, Duration.ofSeconds(5));
    }


    @Test
    void aUnaryMethodIsRefusedByCallStreamAndSaysWhichToUse() {
        WireException ex = assertThrows(WireException.class,
                () -> callHere().callStream(STR_SERVICE + "/GetLedgerEnd", null, 10));

        assertTrue(ex.getMessage().contains("unary"), ex.getMessage());
        assertTrue(ex.getMessage().contains("call()"), ex.getMessage());
    }


    @Test
    void aServerStreamingMethodIsRefusedByCall() {
        WireException ex = assertThrows(WireException.class,
                () -> callHere().call(STR_SERVICE + "/GetActiveContracts"));

        assertTrue(ex.getMessage().contains("streams"), ex.getMessage());
    }


    @Test
    void aClientStreamingMethodIsRefusedByBoth() {
        WireException exStream = assertThrows(WireException.class,
                () -> callHere().callStream(STR_SERVICE + "/PushThings", null, 10));
        assertTrue(exStream.getMessage().contains("CLIENT"), exStream.getMessage());

        assertThrows(WireException.class, () -> callHere().call(STR_SERVICE + "/PushThings"));
    }


    @Test
    void aCapIsRequiredToBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> callHere().callStream(STR_SERVICE + "/GetActiveContracts", null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> callHere().callStream(STR_SERVICE + "/GetActiveContracts", null, -1));
    }


    /**
     * The descriptor's type is what tells grpc-java the shape of the call, so a
     * streamed method built as UNARY would disagree with itself on the wire.
     */
    @Test
    void aMethodDescriptorCarriesTheTypeItWasAskedFor() {
        assertEquals(MethodDescriptor.MethodType.UNARY,
                MethodProbe.methodFor("a.B/C").getType());
        assertEquals(MethodDescriptor.MethodType.SERVER_STREAMING,
                MethodProbe.methodFor("a.B/C", MethodDescriptor.MethodType.SERVER_STREAMING)
                        .getType());
    }


    @Test
    void aResultReportsItsCountAndItsCapAndCannotBeMutatedAfterwards() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsFile());
        DynamicMessage msg = DynamicMessage.newBuilder(
                set.methodFor(STR_SERVICE + "/GetActiveContracts").getOutputType()).build();

        DynamicCall.StreamResult result =
                new DynamicCall.StreamResult(List.of(msg, msg), false);

        assertEquals(2, result.cntMessage());
        assertFalse(result.flagCapped());
        assertThrows(UnsupportedOperationException.class, () -> result.lstMessage().add(msg));
    }

}
