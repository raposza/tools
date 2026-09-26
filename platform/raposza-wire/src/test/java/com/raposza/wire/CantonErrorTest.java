// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;

import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

import org.junit.jupiter.api.Test;

/**
 * The error extraction, against statuses built here rather than provoked from a
 * participant.
 *
 * The shapes are not invented: they were recorded off a live 2.9.6 sandbox,
 * including the metadata keys and the message prefix. A test written against
 * a guessed shape would pass and prove nothing, which is why the shapes were
 * measured first.
 *
 * Author Claude/bentzn
 */
class CantonErrorTest {

    private static final String MSG = "CONTRACT_NOT_FOUND(11,fda07e9d): Contract could not be"
            + " found with id 005a2147";


    private static StatusRuntimeException withInfo(int numCode, String strMessage,
            ErrorInfo info) {
        com.google.rpc.Status status = com.google.rpc.Status.newBuilder().setCode(numCode)
                .setMessage(strMessage).addDetails(Any.pack(info)).build();
        return StatusProto.toStatusRuntimeException(status);
    }


    private static StatusRuntimeException bare(int numCode, String strMessage) {
        com.google.rpc.Status status = com.google.rpc.Status.newBuilder().setCode(numCode)
                .setMessage(strMessage).build();
        return StatusProto.toStatusRuntimeException(status);
    }


    /** The whole point: the code is the reason, not the gRPC status name. */
    @Test
    void theCodeComesFromErrorInfoRatherThanFromGrpc() {
        ErrorInfo info = ErrorInfo.newBuilder().setReason("CONTRACT_NOT_FOUND")
                .putMetadata("category", "11").putMetadata("definite_answer", "false")
                .putMetadata("participant", "'sandbox'").build();

        CantonError err = CantonError.of(withInfo(5, MSG, info));

        assertEquals("CONTRACT_NOT_FOUND", err.codeError());
        assertTrue(err.strDetail().contains("category 11"), err.strDetail());
        assertTrue(err.strDetail().contains("definite_answer false"), err.strDetail());
    }


    /** A participant that omits the structured detail still yields a code. */
    @Test
    void theMessagePrefixIsTheFallback() {
        assertEquals("CONTRACT_NOT_FOUND", CantonError.of(bare(5, MSG)).codeError());
    }


    /**
     * A failure that is not Canton-shaped at all - TLS, routing, a proxy - must
     * not have a code invented for it. The gRPC name is the most specific true
     * thing left.
     */
    @Test
    void aNonCantonFailureFallsBackToTheGrpcName() {
        assertEquals("UNAVAILABLE",
                CantonError.of(bare(14, "io exception")).codeError());
    }


    /**
     * A code appearing inside a sentence is not a prefix. Without the anchor
     * this would report whatever preceded the first bracket.
     */
    @Test
    void aBracketLaterInTheSentenceIsNotAPrefix() {
        assertEquals("INTERNAL",
                CantonError.of(bare(13, "something failed (see the log)")).codeError());
    }


    @Test
    void anEmptyDescriptionDoesNotThrow() {
        assertEquals("ABORTED", CantonError.of(bare(10, "")).codeError());
    }

}
