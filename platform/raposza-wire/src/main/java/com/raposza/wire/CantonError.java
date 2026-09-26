// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.ErrorInfo;

import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

import java.util.Map;

/**
 * The Canton error code, pulled out of a gRPC failure.
 *
 * <h2>Why this class exists</h2>
 *
 * The gRPC status code is NOT the answer. A missing contract arrives as gRPC
 * NOT_FOUND, which is true and useless: dozens of Canton codes share it. The
 * code an operator needs - CONTRACT_NOT_FOUND - is
 * {@code ErrorInfo.reason}, in a {@code google.rpc.ErrorInfo} inside the
 * status details. Measured against a live 2.9.6 participant, not read off
 * a schema.
 *
 * The same measurement produced the metadata carried below: category,
 * definite_answer, the participant name and a correlation id.
 *
 * <h2>definite_answer is NOT an outcome</h2>
 *
 * It came back false on a plain CONTRACT_NOT_FOUND, which committed nothing.
 * Canton sets it conservatively, so treating false as "outcome unknown" would
 * make almost every rejection unknown and would tell the operator to go and
 * look every time a contract id was mistyped. It is carried in the detail text
 * because it is worth seeing, and it decides nothing - an unknown outcome is
 * "no completion was observed", which is a different event.
 *
 * <h2>The fallback</h2>
 *
 * Canton also prefixes the message with {@code CODE(category,correlation):}.
 * That is a documented format but it is a human string, so it is the second
 * source rather than the first. Having it means a participant that omits the
 * structured detail still yields a code rather than a shrug.
 *
 * Lives in this module rather than in raposza-wire, which depends on
 * protobuf and deliberately not on gRPC. It is generation-neutral and P5 will
 * want it too; moving it then is a smaller act than widening that module's
 * dependencies now for a caller that does not exist.
 *
 * Author Claude/bentzn
 */
public final class CantonError {

    private static final String KEY_DEFINITE = "definite_answer";
    private static final String KEY_CATEGORY = "category";

    private final String codeError;
    private final String strDetail;


    private CantonError(String codeError, String strDetail) {
        this.codeError = codeError;
        this.strDetail = strDetail;
    }


    /**
     * @param ex the failure as gRPC delivered it
     * @return the Canton code and a detail line, never null; the code falls
     *         back to the gRPC code name when the participant supplied neither
     *         a structured detail nor a recognisable message prefix
     */
    public static CantonError of(StatusRuntimeException ex) {
        String strMessage = ex.getStatus().getDescription() == null
                ? ""
                : ex.getStatus().getDescription();

        ErrorInfo info = errorInfo(ex);
        if (info != null && !info.getReason().isEmpty())
            return new CantonError(info.getReason(), detail(strMessage, info.getMetadataMap()));

        String codePrefix = codeFromPrefix(strMessage);
        if (codePrefix != null)
            return new CantonError(codePrefix, strMessage);

        // Not a Canton-shaped failure at all - a TLS or routing problem, say.
        // The gRPC name is then the most specific true thing available.
        return new CantonError(ex.getStatus().getCode().name(), strMessage);
    }


    /** @return the Canton error code, e.g. "CONTRACT_NOT_FOUND" */
    public String codeError() {
        return codeError;
    }


    /** @return the message, with the metadata worth seeing appended */
    public String strDetail() {
        return strDetail;
    }


    private static ErrorInfo errorInfo(StatusRuntimeException ex) {
        com.google.rpc.Status status = StatusProto.fromThrowable(ex);
        if (status == null)
            return null;

        for (Any any : status.getDetailsList()) {
            if (!any.is(ErrorInfo.class))
                continue;

            try {
                return any.unpack(ErrorInfo.class);
            }
            catch (InvalidProtocolBufferException exUnpack) {
                // A detail that claims to be an ErrorInfo and is not tells us
                // nothing, and must not cost the caller the message it came
                // with. Fall through to the prefix.
                return null;
            }
        }
        return null;
    }


    /**
     * The documented Canton message prefix is CODE(category,correlation): and
     * the code is upper snake case. Anchored at the start, so a code appearing
     * inside a sentence is not mistaken for the prefix.
     */
    private static String codeFromPrefix(String strMessage) {
        int numOpen = strMessage.indexOf('(');
        if (numOpen <= 0)
            return null;

        String strHead = strMessage.substring(0, numOpen);
        for (int cntLoop = 0; cntLoop < strHead.length(); cntLoop++) {
            char ch = strHead.charAt(cntLoop);
            boolean flagOk = (ch >= 'A' && ch <= 'Z') || ch == '_'
                    || (ch >= '0' && ch <= '9' && cntLoop > 0);
            if (!flagOk)
                return null;
        }
        return strHead.isEmpty() ? null : strHead;
    }


    private static String detail(String strMessage, Map<String, String> mapMeta) {
        StringBuilder bld = new StringBuilder(strMessage);

        String strCategory = mapMeta.get(KEY_CATEGORY);
        if (strCategory != null && !strCategory.isEmpty())
            bld.append("  [category ").append(strCategory).append("]");

        // Reported because it is worth seeing and because a reader who knows
        // what it means will want it. It changes no decision here.
        String strDefinite = mapMeta.get(KEY_DEFINITE);
        if (strDefinite != null && !strDefinite.isEmpty())
            bld.append("  [definite_answer ").append(strDefinite).append("]");

        return bld.toString();
    }

}
