// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The gRPC server reflection client - what a participant says about its own API,
 * asked over the wire rather than read out of a vendor jar.
 *
 * This is what makes an offline descriptor of the Ledger API possible without a
 * vendor protobuf dependency: the participant hands back serialised
 * {@code FileDescriptorProto}s, and {@link DescriptorSet} assembles them.
 *
 * <h2>Two dialects, and why both are tried</h2>
 *
 * Reflection exists twice. {@code grpc.reflection.v1alpha} is the original and
 * carries {@code option deprecated = true}; {@code grpc.reflection.v1} is its
 * straight promotion. A server registers one or the other, and which one is a
 * fact about the server's grpc-java vintage rather than about its API.
 *
 * Canton 3.5.11 ships BOTH generated packages and
 * registers v1 ONLY. A v1alpha call returns UNIMPLEMENTED with "Method not
 * found", which reads like a broken participant and is not one. This is also
 * why {@code grpcurl list} has always worked against the sandbox - it tries v1
 * first and falls back, which is exactly what this class now does.
 *
 * <h2>One set of message types serves both</h2>
 *
 * The two {@code reflection.proto} files were diffed out of the Canton jar and
 * differ ONLY in comments, blank lines and file-level options - no field
 * number, type or name changes. The messages are therefore identical on the
 * wire, so the v1alpha marshallers decode a v1 response correctly and the v1
 * method descriptor is the generated one with its full method name retargeted.
 * That is a measurement, not an assumption, and it is the whole reason this
 * needs no second dependency.
 *
 * <h2>Bidirectional, so no blocking stub</h2>
 *
 * {@code ServerReflectionInfo} is bidi streaming and grpc-java generates no
 * blocking method for it. The call is issued through {@link ClientCalls}
 * directly rather than through the generated stub, because the stub is bound to
 * the v1alpha method name and the point here is to choose.
 *
 * <h2>What it does NOT do</h2>
 *
 * No authentication. Credentials belong on the {@link Channel} the caller
 * supplies, which is also why no channel is built here - a transport choice
 * made in a translation module is one the caller cannot override.
 *
 * Author Claude/bentzn
 */
public final class ServerReflection {

    /** The promoted service, which is what a current grpc-java registers. */
    public static final String STR_SERVICE_V1 = "grpc.reflection.v1.ServerReflection";

    /** The original, deprecated in its own proto and still served by older stacks. */
    public static final String STR_SERVICE_V1ALPHA = "grpc.reflection.v1alpha.ServerReflection";

    /** Prefix of every file the reflection mechanism itself is described by. */
    public static final String STR_PREFIX_PROTO = "grpc/reflection/";

    private static final MethodDescriptor<ServerReflectionRequest, ServerReflectionResponse>
            METHOD_V1ALPHA = ServerReflectionGrpc.getServerReflectionInfoMethod();

    /**
     * The v1 method, built from the generated v1alpha one.
     *
     * Only the full method name changes: {@code toBuilder()} keeps the
     * marshallers, and the messages are identical on the wire.
     */
    private static final MethodDescriptor<ServerReflectionRequest, ServerReflectionResponse>
            METHOD_V1 = METHOD_V1ALPHA.toBuilder()
                    .setFullMethodName(STR_SERVICE_V1 + "/ServerReflectionInfo")
                    .build();

    private static final int N_CLOSURE_ROUNDS_MAX = 16;

    private final Channel channel;
    private final Duration timeout;
    private String strDialect;


    /**
     * @param channel an open channel to the participant; not closed here, since
     *        the caller built it and may reuse it
     * @param timeout how long any one stream may take
     */
    public ServerReflection(Channel channel, Duration timeout) {
        if (channel == null)
            throw new IllegalArgumentException("channel is required");
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("a positive timeout is required");

        this.channel = channel;
        this.timeout = timeout;
    }


    public static ServerReflection of(Channel channel) {
        return new ServerReflection(channel, Duration.ofSeconds(30));
    }


    /**
     * Which dialect this participant answered on, or null before the first call.
     *
     * Worth recording per version rather than discarding: it is a property of
     * the server's grpc-java vintage, it differs across the lines this project
     * targets, and a capture that does not say which service produced it cannot
     * be compared with one taken from a different stack.
     *
     * @return {@link #STR_SERVICE_V1}, {@link #STR_SERVICE_V1ALPHA}, or null
     */
    public String strDialect() {
        return strDialect;
    }


    /**
     * @param strService a fully qualified service name
     * @return true when it is the reflection service in either dialect
     */
    public static boolean isReflectionService(String strService) {
        return STR_SERVICE_V1.equals(strService) || STR_SERVICE_V1ALPHA.equals(strService);
    }


    /**
     * @return every service the participant advertises, in the order it gave
     *         them, including the reflection service itself
     * @throws WireException when the participant errors or does not answer
     */
    public List<String> lstServiceName() {
        ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
                .setListServices("")
                .build();

        List<ServerReflectionResponse> lstResponse = exchange(List.of(request));
        List<String> lstName = new ArrayList<>();
        for (ServerReflectionResponse response : lstResponse) {
            if (!response.hasListServicesResponse())
                continue;
            for (ServiceResponse service : response.getListServicesResponse().getServiceList()) {
                lstName.add(service.getName());
            }
        }

        if (lstName.isEmpty())
            throw new WireException("the participant advertised no services at all");
        return lstName;
    }


    /**
     * The whole descriptor set for every service the participant advertises,
     * closed over its own imports.
     *
     * The reflection service is excluded in BOTH dialects: it describes the
     * mechanism used to ask, not the API being described.
     *
     * @return the set, dependency-closed
     */
    public DescriptorSet descriptorSet() {
        List<String> lstService = new ArrayList<>();
        for (String strName : lstServiceName()) {
            if (!isReflectionService(strName))
                lstService.add(strName);
        }
        return descriptorSetFor(lstService);
    }


    /**
     * @param lstService the services to describe, by fully qualified name
     * @return the set, dependency-closed
     * @throws WireException when a symbol is unknown, or the closure does not
     *         terminate
     */
    public DescriptorSet descriptorSetFor(List<String> lstService) {
        if (lstService == null || lstService.isEmpty())
            throw new IllegalArgumentException("at least one service is required");

        List<ServerReflectionRequest> lstRequest = new ArrayList<>();
        for (String strService : lstService) {
            lstRequest.add(ServerReflectionRequest.newBuilder()
                    .setFileContainingSymbol(strService)
                    .build());
        }

        DescriptorSet set = new DescriptorSet();
        collectInto(set, lstRequest);

        // A server MAY return transitive dependencies with the file that names
        // them and MAY return only the file. Closing the set explicitly is
        // correct under both, and is the only way to be sure the result
        // resolves offline - which is the whole point of capturing it.
        int cntRound = 0;
        Set<String> setMissing = set.setMissingDependency();
        while (!setMissing.isEmpty()) {
            cntRound++;
            if (cntRound > N_CLOSURE_ROUNDS_MAX) {
                throw new WireException("the descriptor set did not close after "
                        + N_CLOSURE_ROUNDS_MAX + " rounds; still missing " + setMissing);
            }

            List<ServerReflectionRequest> lstFetch = new ArrayList<>();
            for (String strFile : setMissing) {
                lstFetch.add(ServerReflectionRequest.newBuilder()
                        .setFileByFilename(strFile)
                        .build());
            }
            collectInto(set, lstFetch);

            Set<String> setStill = set.setMissingDependency();
            if (setStill.equals(setMissing)) {
                throw new WireException("the participant does not serve these imported files: "
                        + setStill);
            }
            setMissing = setStill;
        }

        return set;
    }


    private void collectInto(DescriptorSet set, List<ServerReflectionRequest> lstRequest) {
        Set<com.google.protobuf.ByteString> setBs = new LinkedHashSet<>();
        for (ServerReflectionResponse response : exchange(lstRequest)) {
            if (response.hasErrorResponse()) {
                throw new WireException("reflection error "
                        + response.getErrorResponse().getErrorCode() + ": "
                        + response.getErrorResponse().getErrorMessage());
            }
            if (response.hasFileDescriptorResponse())
                setBs.addAll(response.getFileDescriptorResponse().getFileDescriptorProtoList());
        }
        set.addAll(setBs);
    }


    /**
     * Send every request on one stream and collect what comes back, choosing
     * the dialect on the first call and keeping it afterwards.
     *
     * UNIMPLEMENTED is the ONLY status that triggers the fallback. Any other
     * failure is a real one and must not be retried under a second name, or a
     * refused credential would be reported as a missing service.
     *
     * @param lstRequest what to ask
     * @return the responses, in arrival order
     */
    private List<ServerReflectionResponse> exchange(List<ServerReflectionRequest> lstRequest) {
        if (STR_SERVICE_V1ALPHA.equals(strDialect))
            return exchangeOn(METHOD_V1ALPHA, lstRequest);
        if (STR_SERVICE_V1.equals(strDialect))
            return exchangeOn(METHOD_V1, lstRequest);

        try {
            List<ServerReflectionResponse> lstOut = exchangeOn(METHOD_V1, lstRequest);
            strDialect = STR_SERVICE_V1;
            return lstOut;
        }
        catch (WireException ex) {
            if (!isUnimplemented(ex))
                throw ex;
        }

        List<ServerReflectionResponse> lstOut = exchangeOn(METHOD_V1ALPHA, lstRequest);
        strDialect = STR_SERVICE_V1ALPHA;
        return lstOut;
    }


    private static boolean isUnimplemented(Throwable thrError) {
        Throwable thrWalk = thrError;
        while (thrWalk != null) {
            if (Status.fromThrowable(thrWalk).getCode() == Status.Code.UNIMPLEMENTED)
                return true;
            thrWalk = thrWalk.getCause();
        }
        return false;
    }


    private List<ServerReflectionResponse> exchangeOn(
            MethodDescriptor<ServerReflectionRequest, ServerReflectionResponse> method,
            List<ServerReflectionRequest> lstRequest) {

        List<ServerReflectionResponse> lstResponse = new ArrayList<>();
        AtomicReference<Throwable> refError = new AtomicReference<>();
        CountDownLatch latchDone = new CountDownLatch(1);

        StreamObserver<ServerReflectionResponse> observerIn =
                new StreamObserver<ServerReflectionResponse>() {

            @Override
            public void onNext(ServerReflectionResponse response) {
                synchronized (lstResponse) {
                    lstResponse.add(response);
                }
            }


            @Override
            public void onError(Throwable thrError) {
                refError.set(thrError);
                latchDone.countDown();
            }


            @Override
            public void onCompleted() {
                latchDone.countDown();
            }
        };

        StreamObserver<ServerReflectionRequest> observerOut = ClientCalls.asyncBidiStreamingCall(
                channel.newCall(method, CallOptions.DEFAULT), observerIn);
        try {
            for (ServerReflectionRequest request : lstRequest) {
                observerOut.onNext(request);
            }
            observerOut.onCompleted();
        }
        catch (RuntimeException ex) {
            observerOut.onError(ex);
            throw new WireException("the reflection stream failed while sending on "
                    + method.getFullMethodName() + ": " + ex, ex);
        }

        boolean flagDone;
        try {
            flagDone = latchDone.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WireException("interrupted while awaiting the reflection stream", ex);
        }

        if (!flagDone) {
            throw new WireException("the participant did not finish answering within "
                    + timeout.toSeconds() + " s");
        }
        if (refError.get() != null) {
            throw new WireException("the reflection stream failed on "
                    + method.getFullMethodName() + ": " + refError.get(), refError.get());
        }

        synchronized (lstResponse) {
            return List.copyOf(lstResponse);
        }
    }
}
