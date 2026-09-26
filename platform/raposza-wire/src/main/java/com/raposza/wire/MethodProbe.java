// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Calls any gRPC method by name and reports only the STATUS.
 *
 * This exists because measuring authentication needs no generated stub and no
 * vendor protobuf. What a token does or does not get past is entirely visible
 * in the status code, and the request body is irrelevant to it: auth runs in an
 * interceptor, before the handler ever sees a message.
 *
 * <h2>The bytes marshaller</h2>
 *
 * Requests and responses are raw {@code byte[]}. An EMPTY request is what this
 * sends, which is a valid encoding of a protobuf message with no fields set -
 * so a call that gets past auth lands on the handler and answers OK, or
 * INVALID_ARGUMENT, or NOT_FOUND. Every one of those is a PASS for the question
 * being asked, and the distinction from UNAUTHENTICATED and PERMISSION_DENIED
 * is the whole measurement.
 *
 * <h2>Why not grpcurl</h2>
 *
 * grpcurl resolves a descriptor through reflection before it
 * invokes anything, and reflection is refused to a non-admin token with
 * PERMISSION_DENIED. So grpcurl cannot exercise a real method on an
 * authenticated stack at all, and no conclusion about auth may rest on it. A
 * Java caller builds the {@link MethodDescriptor} itself and never asks the
 * server what exists.
 *
 * <h2>What a refusal does NOT tell you</h2>
 *
 * A mis-signed token and a valid token of the wrong shape both come back
 * UNAUTHENTICATED with the same message. Only the participant log separates
 * them. A matrix built from these readings records THAT a call was refused and
 * can never record WHY.
 *
 * Author Claude/bentzn
 */
public final class MethodProbe {

    /** An empty protobuf message: no fields set, zero bytes. */
    public static final byte[] ARR_EMPTY = new byte[0];

    private static final MethodDescriptor.Marshaller<byte[]> MARSHALLER =
            new MethodDescriptor.Marshaller<byte[]>() {

        @Override
        public InputStream stream(byte[] arrValue) {
            return new ByteArrayInputStream(arrValue);
        }


        @Override
        public byte[] parse(InputStream streamIn) {
            try {
                return streamIn.readAllBytes();
            }
            catch (IOException ex) {
                throw new WireException("could not read a response body: " + ex, ex);
            }
        }
    };

    private final Channel channel;


    public MethodProbe(Channel channel) {
        if (channel == null)
            throw new IllegalArgumentException("channel is required");
        this.channel = channel;
    }


    /**
     * @param strFullMethod fully qualified, e.g.
     *        {@code com.daml.ledger.api.v2.StateService/GetLedgerEnd}
     * @return the descriptor for a unary call carrying raw bytes
     */
    public static MethodDescriptor<byte[], byte[]> methodFor(String strFullMethod) {
        return methodFor(strFullMethod, MethodDescriptor.MethodType.UNARY);
    }


    /**
     * The same raw-bytes descriptor for any method type.
     *
     * The type is not decoration. grpc-java carries it on the call and the
     * stub helpers branch on the shape they were handed, so a server-streaming
     * method invoked through a descriptor still declaring UNARY is a call whose
     * declared shape and actual shape disagree - and the failure surfaces
     * somewhere other than here.
     *
     * @param strFullMethod fully qualified, e.g.
     *        {@code com.daml.ledger.api.v2.StateService/GetActiveContracts}
     * @param type the method's shape, as its descriptor declares it
     * @return the descriptor, carrying raw bytes in both directions
     */
    public static MethodDescriptor<byte[], byte[]> methodFor(String strFullMethod,
            MethodDescriptor.MethodType type) {
        if (strFullMethod == null || !strFullMethod.contains("/"))
            throw new IllegalArgumentException("a full method name is required: " + strFullMethod);
        if (type == null)
            throw new IllegalArgumentException("a method type is required");

        return MethodDescriptor.<byte[], byte[]>newBuilder(MARSHALLER, MARSHALLER)
                .setType(type)
                .setFullMethodName(strFullMethod)
                .build();
    }


    /**
     * Call the method with an empty request and report what came back.
     *
     * @param strFullMethod the method to call
     * @return the status code, never null
     */
    public Status.Code codeFor(String strFullMethod) {
        try {
            ClientCalls.blockingUnaryCall(channel, methodFor(strFullMethod),
                    CallOptions.DEFAULT, ARR_EMPTY);
            return Status.Code.OK;
        }
        catch (StatusRuntimeException ex) {
            return ex.getStatus().getCode();
        }
    }


    /**
     * Whether a status means the caller got past authentication.
     *
     * UNAUTHENTICATED is a refusal at the door. PERMISSION_DENIED is NOT: the
     * caller was identified and then refused a right, which is the transition
     * that matters. Everything else - OK, INVALID_ARGUMENT,
     * NOT_FOUND, UNIMPLEMENTED - reached the handler.
     *
     * @param code what {@link #codeFor} returned
     * @return true when authentication succeeded
     */
    public static boolean isAuthenticated(Status.Code code) {
        return code != Status.Code.UNAUTHENTICATED;
    }
}
