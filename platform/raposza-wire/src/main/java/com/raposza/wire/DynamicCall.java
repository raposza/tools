// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calls a gRPC method the caller has a DESCRIPTOR for and no generated stub.
 *
 * <h2>Why this rather than vendor stubs</h2>
 *
 * The admin API is where DAR removal lives, and it moved between the two
 * generations this project targets - the same service under a different proto
 * package, with methods added on the way. A client built from generated stubs
 * would exist twice, would have to be regenerated per Canton version, and would
 * bring a vendor jar into a module that must not have one. No codegen, either:
 * that is settled.
 *
 * So the descriptor comes off the participant through {@link ServerReflection},
 * the request is a {@link DynamicMessage} built against it, and the wire body
 * is raw bytes. A method that gained a field is picked up on the next
 * connection with nothing rebuilt.
 *
 * <h2>Fields are set BY NAME, and an absent one names what is there</h2>
 *
 * {@link #set} refuses an unknown field with the list of fields the message
 * actually has. That is the whole defence against version drift: a rename shows
 * up as one legible failure at the call site rather than as a request the
 * server silently reads as empty.
 *
 * <h2>Unary and server-streaming</h2>
 *
 * {@link #call} is unary and refuses anything else. {@link #callStream} is
 * server-streaming and refuses anything else, because the two return different
 * things and a method silently served by the wrong one is a defect that reads
 * as an empty result.
 *
 * Client-streaming and bidirectional methods are still REFUSED by name. Nothing
 * in the Ledger API's read or write path is either, and half-serving a shape
 * with no caller is how an untested branch ships.
 *
 * <h2>A stream is drained under a CAP, never followed</h2>
 *
 * {@code GetUpdates} without an end offset never completes, and an active
 * contract set is as large as the ledger. So {@link #callStream} takes a
 * maximum, cancels the call when it reaches it, and SAYS SO in its result: a
 * truncated read that looks like a complete one is the failure this whole
 * module is arranged to avoid.
 *
 * <h2>No credentials here</h2>
 *
 * The admin token belongs on the {@link Channel} the caller supplies, for the
 * same reason {@link ServerReflection} takes one: a transport or credential
 * choice made in this module is one the caller cannot override.
 *
 * Author Claude/bentzn
 */
public final class DynamicCall {

    private final Channel channel;

    private final DescriptorSet set;

    private final Duration timeout;


    /**
     * @param channel an open channel, carrying whatever credentials the server
     *        requires; not closed here
     * @param set the descriptors, closed - see
     *        {@link ServerReflection#descriptorSet}
     * @param timeout the deadline put on any one call
     */
    public DynamicCall(Channel channel, DescriptorSet set, Duration timeout) {
        if (channel == null)
            throw new IllegalArgumentException("channel is required");
        if (set == null)
            throw new IllegalArgumentException("a descriptor set is required");
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("a positive timeout is required");

        this.channel = channel;
        this.set = set;
        this.timeout = timeout;
    }


    public static DynamicCall of(Channel channel, DescriptorSet set) {
        return new DynamicCall(channel, set, Duration.ofSeconds(60));
    }


    /**
     * @param strFullMethod the gRPC spelling, {@code some.pkg.Service/Method}
     * @return a builder for that method's request message
     */
    public DynamicMessage.Builder builderFor(String strFullMethod) {
        return DynamicMessage.newBuilder(set.methodFor(strFullMethod).getInputType());
    }


    /**
     * Sets one field by name, repeated fields included.
     *
     * @param builder what is being built
     * @param strField the proto field name, as the descriptor spells it
     * @param objValue the value; an {@link Iterable} for a repeated field
     * @return the builder, so calls chain
     * @throws WireException when the message has no such field
     */
    public static DynamicMessage.Builder set(DynamicMessage.Builder builder, String strField,
            Object objValue) {
        if (builder == null)
            throw new IllegalArgumentException("builder is required");

        Descriptors.Descriptor descType = builder.getDescriptorForType();
        Descriptors.FieldDescriptor field = descType.findFieldByName(strField);
        if (field == null) {
            throw new WireException(descType.getFullName() + " has no field " + strField
                    + "; it has " + lstFieldName(descType));
        }

        if (!field.isRepeated()) {
            builder.setField(field, objValue);
            return builder;
        }

        builder.clearField(field);
        if (objValue instanceof Iterable) {
            for (Object objHere : (Iterable<?>) objValue) {
                builder.addRepeatedField(field, objHere);
            }
        }
        else {
            builder.addRepeatedField(field, objValue);
        }
        return builder;
    }


    /**
     * @param descType a message type
     * @return its field names, for a failure that says what WAS available
     */
    public static List<String> lstFieldName(Descriptors.Descriptor descType) {
        List<String> lstOut = new ArrayList<>();
        for (Descriptors.FieldDescriptor field : descType.getFields()) {
            lstOut.add(field.getName());
        }
        return lstOut;
    }


    /**
     * @param strFullMethod the method to call
     * @return the response, with an empty request
     */
    public DynamicMessage call(String strFullMethod) {
        return call(strFullMethod, null);
    }


    /**
     * @param strFullMethod the method to call
     * @param msgRequest the request, or null for an empty one
     * @return the response
     * @throws WireException when the call is refused, when the method streams,
     *         or when the response does not parse as its declared type
     */
    public DynamicMessage call(String strFullMethod, Message msgRequest) {
        Descriptors.MethodDescriptor method = set.methodFor(strFullMethod);
        if (method.toProto().getClientStreaming() || method.toProto().getServerStreaming()) {
            throw new WireException(strFullMethod + " streams, and only unary methods"
                    + " are called here");
        }

        byte[] arrResponse;
        try {
            arrResponse = ClientCalls.blockingUnaryCall(channel,
                    MethodProbe.methodFor(strFullMethod),
                    CallOptions.DEFAULT.withDeadlineAfter(timeout.toMillis(),
                            TimeUnit.MILLISECONDS),
                    msgRequest == null ? MethodProbe.ARR_EMPTY : msgRequest.toByteArray());
        }
        catch (StatusRuntimeException ex) {
            throw new WireException(strFullMethod + " was refused with "
                    + ex.getStatus().getCode() + ": " + ex.getStatus().getDescription(), ex);
        }

        try {
            return DynamicMessage.parseFrom(method.getOutputType(), arrResponse);
        }
        catch (InvalidProtocolBufferException ex) {
            throw new WireException(strFullMethod + " answered " + arrResponse.length
                    + " bytes that are not a " + method.getOutputType().getFullName()
                    + ": " + ex, ex);
        }
    }


    /**
     * What a drained server stream came back as.
     *
     * The flag is the point. A caller that reads {@code lstMessage} alone
     * cannot tell a ledger holding exactly {@code cntMax} contracts from one
     * holding ten times that, and rendering the second as though it were the
     * first is worse than refusing to answer.
     *
     * @param lstMessage the responses, in the order the server sent them
     * @param flagCapped true when the cap stopped the read before the server
     *        had finished
     */
    public record StreamResult(List<DynamicMessage> lstMessage, boolean flagCapped) {

        public StreamResult {
            lstMessage = List.copyOf(lstMessage);
        }


        public int cntMessage() {
            return lstMessage.size();
        }
    }


    /**
     * Calls a server-streaming method and drains it under a cap.
     *
     * @param strFullMethod the gRPC spelling, {@code some.pkg.Service/Method}
     * @param msgRequest the request, or null for an empty one
     * @param cntMax the most responses to take; the call is cancelled on
     *        reaching it
     * @return the responses and whether the cap stopped the read
     * @throws WireException when the method does not server-stream, when the
     *         call is refused, when the deadline passes with the stream still
     *         open, or when a response does not parse as its declared type
     */
    public StreamResult callStream(String strFullMethod, Message msgRequest, int cntMax) {
        if (cntMax <= 0)
            throw new IllegalArgumentException("a positive cap is required: " + cntMax);

        Descriptors.MethodDescriptor method = set.methodFor(strFullMethod);
        if (method.toProto().getClientStreaming()) {
            throw new WireException(strFullMethod + " streams from the CLIENT, and only"
                    + " server-streaming methods are drained here");
        }
        if (!method.toProto().getServerStreaming()) {
            throw new WireException(strFullMethod + " is unary; call it with call() rather"
                    + " than callStream()");
        }

        List<DynamicMessage> lstMessage = new ArrayList<>();
        AtomicBoolean flagCapped = new AtomicBoolean(false);
        AtomicReference<Throwable> refError = new AtomicReference<>();
        CountDownLatch latchDone = new CountDownLatch(1);

        ClientCall<byte[], byte[]> call = channel.newCall(
                MethodProbe.methodFor(strFullMethod, MethodDescriptor.MethodType.SERVER_STREAMING),
                CallOptions.DEFAULT.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS));

        StreamObserver<byte[]> observerIn = new StreamObserver<byte[]>() {

            @Override
            public void onNext(byte[] arrResponse) {
                synchronized (lstMessage) {
                    if (flagCapped.get())
                        return;

                    try {
                        lstMessage.add(DynamicMessage.parseFrom(method.getOutputType(),
                                arrResponse));
                    }
                    catch (InvalidProtocolBufferException ex) {
                        refError.set(new WireException(strFullMethod + " answered "
                                + arrResponse.length + " bytes that are not a "
                                + method.getOutputType().getFullName() + ": " + ex, ex));
                        latchDone.countDown();
                        return;
                    }

                    // Cancelling from inside onNext is what stops a stream that
                    // would otherwise run to the deadline, and the flag is set
                    // FIRST so the CANCELLED that follows is read as ours.
                    if (lstMessage.size() >= cntMax) {
                        flagCapped.set(true);
                        call.cancel("the caller's cap of " + cntMax + " was reached", null);
                        latchDone.countDown();
                    }
                }
            }


            @Override
            public void onError(Throwable thrError) {
                if (!flagCapped.get())
                    refError.set(thrError);
                latchDone.countDown();
            }


            @Override
            public void onCompleted() {
                latchDone.countDown();
            }
        };

        ClientCalls.asyncServerStreamingCall(call,
                msgRequest == null ? MethodProbe.ARR_EMPTY : msgRequest.toByteArray(),
                observerIn);

        boolean flagInTime;
        try {
            flagInTime = latchDone.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            call.cancel("the calling thread was interrupted", ex);
            throw new WireException(strFullMethod + " was interrupted", ex);
        }

        if (!flagInTime) {
            call.cancel("the caller's timeout of " + timeout + " passed", null);
            throw new WireException(strFullMethod + " did not finish within " + timeout
                    + "; " + lstMessage.size() + " responses had arrived");
        }

        Throwable thrError = refError.get();
        if (thrError instanceof WireException)
            throw (WireException) thrError;
        if (thrError != null) {
            throw new WireException(strFullMethod + " was refused with "
                    + Status.fromThrowable(thrError).getCode() + ": "
                    + Status.fromThrowable(thrError).getDescription(), thrError);
        }

        synchronized (lstMessage) {
            return new StreamResult(lstMessage, flagCapped.get());
        }
    }


    /**
     * @param msg any message
     * @param strField a singular field name
     * @return its value as text, or "" when it is not set
     * @throws WireException when the message has no such field
     */
    public static String strField(Message msg, String strField) {
        Descriptors.FieldDescriptor field = fieldOf(msg, strField);
        Object objValue = msg.getField(field);
        return objValue == null ? "" : String.valueOf(objValue);
    }


    /**
     * @param msg any message
     * @param strField a repeated message field
     * @return its elements
     * @throws WireException when the message has no such field
     */
    public static List<Message> lstMessage(Message msg, String strField) {
        Descriptors.FieldDescriptor field = fieldOf(msg, strField);
        List<Message> lstOut = new ArrayList<>();
        int cntItem = msg.getRepeatedFieldCount(field);
        for (int idxItem = 0; idxItem < cntItem; idxItem++) {
            lstOut.add((Message) msg.getRepeatedField(field, idxItem));
        }
        return lstOut;
    }


    private static Descriptors.FieldDescriptor fieldOf(Message msg, String strField) {
        Descriptors.Descriptor descType = msg.getDescriptorForType();
        Descriptors.FieldDescriptor field = descType.findFieldByName(strField);
        if (field == null) {
            throw new WireException(descType.getFullName() + " has no field " + strField
                    + "; it has " + lstFieldName(descType));
        }
        return field;
    }
}
