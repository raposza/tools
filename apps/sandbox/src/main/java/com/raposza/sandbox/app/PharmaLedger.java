// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The JSON Ledger API, as much of it as the Pharma fixture needs.
 *
 * <h2>Why this route and not the admin API or daml-script</h2>
 *
 * MEASURED 2026-09-22, `probes/pharma`, against LocalNetND on a 0.8.1 bundle
 * and canton-open-source 3.5.13: a DAR is accepted by both organizational
 * participants in a second each, a party allocated on one is usable from the
 * other within a second, and a propose/accept commits across them. Nothing else
 * has been measured on that topology - the admin API is not reachable from the
 * window there and the script runner has never been driven against it.
 *
 * <h2>A PARTICIPANT THAT IS LISTENING IS NOT A PARTICIPANT THAT HAS JOINED</h2>
 *
 * Also measured the same day: both participants answered `/v2/version` forty
 * seconds into a start while refusing every call with
 * PACKAGE_SERVICE_CANNOT_AUTODETECT_SYNCHRONIZER and
 * PARTY_ALLOCATION_WITHOUT_CONNECTED_SYNCHRONIZER. So {@link #waitJoined} is a
 * step of its own and the caller runs it before anything else.
 *
 * <h2>No JSON library</h2>
 *
 * The three response fields this reads - the allocated party and the created
 * contract ids - are string values with distinctive keys, and the requests are
 * built by {@link PharmaStory}. Adding a parser to the released application for
 * one fixture is a dependency nothing else asks for.
 *
 * Author Claude/bentzn
 */
public final class PharmaLedger {

    /** The per-role JSON API slot, from `LocalNetPorts.LST_SLOT_ROLE`. */
    public static final int N_OFFSET_JSON = 2;

    /** app-provider is the second role block, ten above the first port. */
    public static final int N_OFFSET_PRODUCER = 10;

    /** app-user is the third. */
    public static final int N_OFFSET_SUPPLIER = 20;

    /** Canton's own user, present on every participant. */
    public static final String STR_USER = DamlScriptSpec.STR_USER_ID_DEFAULT;

    /** What a created event is called in a transaction. */
    public static final String STR_EVENT_CREATED = "CreatedEvent";

    private static final int N_SECONDS_UPLOAD = 300;

    private static final int N_SECONDS_CALL = 120;

    private static final long N_MS_RETRY = 2000L;

    private final transient HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final transient String strBase;

    private final transient String strToken;


    /**
     * @param strBaseNew the participant's JSON API, no trailing slash
     * @param strTokenNew the bearer, or null against a wildcard participant
     */
    public PharmaLedger(String strBaseNew, String strTokenNew) {
        if (strBaseNew == null || strBaseNew.trim().isEmpty())
            throw new IllegalArgumentException("a base url is required");
        this.strBase = strBaseNew.trim();
        this.strToken = strTokenNew;
    }


    /**
     * @param nPortFirst the lowest port of the block, `LocalNetPorts`
     * @param nOffsetRole {@link #N_OFFSET_PRODUCER} or
     *        {@link #N_OFFSET_SUPPLIER}
     * @return that role's JSON API port
     */
    public static int nPortJson(int nPortFirst, int nOffsetRole) {
        return nPortFirst + nOffsetRole + N_OFFSET_JSON;
    }


    /**
     * @param nPort the JSON API port
     * @return the base url
     */
    public static String strBaseOf(int nPort) {
        return "http://localhost:" + nPort;
    }


    /**
     * Keeps asking until the participant accepts a call that needs a
     * synchronizer, or the deadline passes.
     *
     * WHAT IS ASKED IS THE PARTY LIST, which is refused by exactly the
     * condition that matters and changes nothing when it is not.
     *
     * @param nSeconds how long to keep trying
     * @throws IOException the last refusal, at the deadline
     * @throws InterruptedException when the wait is interrupted
     */
    public void waitJoined(int nSeconds) throws IOException, InterruptedException {
        long nMsEnd = System.currentTimeMillis() + nSeconds * 1000L;
        IOException exLast = null;
        while (System.currentTimeMillis() < nMsEnd) {
            try {
                strGet("/v2/parties");
                return;
            }
            catch (IOException ex) {
                exLast = ex;
                Thread.sleep(N_MS_RETRY);
            }
        }
        throw exLast == null ? new IOException("no attempt was made") : exLast;
    }


    /**
     * @param arrDar the archive
     * @throws IOException when the participant refuses it
     * @throws InterruptedException when the call is interrupted
     */
    public void uploadDar(byte[] arrDar) throws IOException, InterruptedException {
        strPost("/v2/dars?vetAllPackages=true", arrDar, "application/octet-stream",
                N_SECONDS_UPLOAD);
    }


    /**
    /**
     * @param strHint the party id hint, which is what a reader sees
     * @return the allocated party id
     * @throws IOException when the participant refuses
     * @throws InterruptedException when the call is interrupted
     */
    public String strAllocate(String strHint) throws IOException, InterruptedException {
        return strAllocateFor(strHint, STR_USER);
    }


    /**
     * Allocates a party and gives it to a user in one call.
     *
     * THE USER MUST EXIST FIRST. Measured 2026-09-22,
     * `probes/pharma/user_probe.py`: naming a user that does not exist is
     * refused with `USER_NOT_FOUND` rather than creating it, so
     * {@link #createUser} comes before this for every role.
     *
     * AND THIS IS WHAT GRANTS `CanActAs`. Measured the same day,
     * `rights_probe.py` R1: the named user holds no rights before the
     * allocation and exactly one `CanActAs` over the new party after it. A
     * role user therefore needs no grant call of its own.
     *
     * @param strHint the party id hint, which is what a reader sees
     * @param strUser the ledger user the party is allocated to
     * @return the allocated party id
     * @throws IOException when the participant refuses
     * @throws InterruptedException when the call is interrupted
     */
    public String strAllocateFor(String strHint, String strUser)
            throws IOException, InterruptedException {
        String strBody = "{\"partyIdHint\":" + PharmaStory.strJson(strHint)
                + ",\"userId\":" + PharmaStory.strJson(strUser) + "}";
        String strOut = strPost("/v2/parties", strBody.getBytes(StandardCharsets.UTF_8),
                "application/json", N_SECONDS_CALL);
        return strValueAfter(strOut, "\"party\":\"", "no party in the allocation response");
    }


    /**
     * A ledger user with no rights at all.
     *
     * @param strUser the id
     * @throws IOException when the participant refuses
     * @throws InterruptedException when the call is interrupted
     */
    public void createUser(String strUser) throws IOException, InterruptedException {
        createUser(strUser, null, java.util.List.of(), java.util.List.of());
    }


    /**
     * A ledger user, with its rights in the SAME call.
     *
     * MEASURED 2026-09-22, `rights_probe.py` R2 and R4: the `rights` array on
     * `POST /v2/users` is honoured - both rights read back off
     * `/v2/users/&lt;id&gt;/rights`, which is where they are checked rather
     * than off the create response, since that echoes only the user. Several
     * named rights go in one call, which is how a superuser gets its
     * organization's four parties without four round trips.
     *
     * THE BODY NEEDS THE `user` WRAPPER. A flat one is refused with
     * `MISSING_FIELD` naming `user` - `user_probe.py` Q2, shape C.
     *
     * @param strUser the id
     * @param strPartyPrimary the primary party, or null for none
     * @param lstActAs the parties it may submit as
     * @param lstReadAs the parties it may read as
     * @throws IOException when the participant refuses
     * @throws InterruptedException when the call is interrupted
     */
    public void createUser(String strUser, String strPartyPrimary,
            java.util.List<String> lstActAs, java.util.List<String> lstReadAs)
            throws IOException, InterruptedException {
        StringBuilder bld = new StringBuilder("{\"user\":{\"id\":")
                .append(PharmaStory.strJson(strUser));
        if (strPartyPrimary != null && !strPartyPrimary.isEmpty()) {
            bld.append(",\"primaryParty\":").append(PharmaStory.strJson(strPartyPrimary));
        }
        bld.append("},\"rights\":[");
        boolean flagFirst = true;
        for (String strParty : lstActAs) {
            if (!flagFirst)
                bld.append(",");
            bld.append(strRight("CanActAs", strParty));
            flagFirst = false;
        }
        for (String strParty : lstReadAs) {
            if (!flagFirst)
                bld.append(",");
            bld.append(strRight("CanReadAs", strParty));
            flagFirst = false;
        }
        bld.append("]}");
        strPost("/v2/users", bld.toString().getBytes(StandardCharsets.UTF_8),
                "application/json", N_SECONDS_CALL);
    }


    /**
     * Grants one party to a user that already exists.
     *
     * THE USER ID GOES IN THE BODY AS WELL AS THE PATH. Measured 2026-09-22,
     * `rights_probe.py` R3: without it the participant answers
     * `INVALID_ARGUMENT ... does not match user in body`, and a `kind` given
     * as a bare name is refused by the decoder. The shape that answered is the
     * one below, and it came back naming what it had newly granted.
     *
     * @param strUser the user
     * @param strParty the party it may submit as
     * @throws IOException when the participant refuses
     * @throws InterruptedException when the call is interrupted
     */
    public void grantActAs(String strUser, String strParty)
            throws IOException, InterruptedException {
        String strBody = "{\"userId\":" + PharmaStory.strJson(strUser)
                + ",\"rights\":[" + strRight("CanActAs", strParty) + "]}";
        strPost("/v2/users/" + strUser + "/rights",
                strBody.getBytes(StandardCharsets.UTF_8), "application/json", N_SECONDS_CALL);
    }


    /**
     * @param strKind `CanActAs` or `CanReadAs`
     * @param strParty the party it is over
     * @return one right, as the API spells it
     */
    private static String strRight(String strKind, String strParty) {
        return "{\"kind\":{" + PharmaStory.strJson(strKind) + ":{\"value\":{\"party\":"
                + PharmaStory.strJson(strParty) + "}}}}";
    }


    /**
     * @param strActAs the submitting party
     * @param strCommand one command, already JSON
     * @return the response body
     * @throws IOException when the participant refuses
     * @throws InterruptedException when the call is interrupted
     */
    public String strSubmit(String strActAs, String strCommand)
            throws IOException, InterruptedException {
        String strId = "pharma-" + Long.toString(System.nanoTime(), 36);
        return strPost("/v2/commands/submit-and-wait-for-transaction",
                strBodySubmit(strId, strActAs, strCommand).getBytes(StandardCharsets.UTF_8),
                "application/json", N_SECONDS_CALL);
    }


    /**
     * The submission body, NAMING ITS USER.
     *
     * WITHOUT `userId` A WILDCARD PARTICIPANT REFUSES EVERY SUBMISSION -
     * `INVALID_TOKEN ... Cannot default user_id field because claims do not
     * specify an user-id`, measured 2026-09-26 by the app matrix on both Pharma
     * cells, LocalNetND with `LocalNetAuth.ofWildcard()`, which is what the
     * window builds for auth mode NONE. The same refusal is recorded for a
     * claimless token in `auth_inventory.md` section 4. With auth on the token
     * is minted for {@link #STR_USER}, so naming it changes nothing there.
     *
     * The field sits beside `commandId` and `actAs` in `JsCommands` - the
     * JSON Ledger API tutorial, 3.5.
     *
     * @param strId the command id
     * @param strActAs the submitting party
     * @param strCommand one command, already JSON
     * @return the request body
     */
    static String strBodySubmit(String strId, String strActAs, String strCommand) {
        return "{\"commands\":{\"commandId\":" + PharmaStory.strJson(strId)
                + ",\"userId\":" + PharmaStory.strJson(STR_USER)
                + ",\"actAs\":[" + PharmaStory.strJson(strActAs) + "],\"commands\":["
                + strCommand + "]}}";
    }


    /**
     * The contract id of the template that was CREATED, and not of the one that
     * was archived.
     *
     * THE FIRST `contractId` IN THE BODY IS THE WRONG ANSWER on a consuming
     * exercise - it is the exercised node's. The probe of 2026-09-22 printed it
     * and labelled it as the created contract, which is the defect this method
     * exists not to repeat.
     *
     * @param strBody a submit-and-wait-for-transaction response
     * @param strTemplate the template's simple name, as `Main:Name`
     * @return the created contract's id
     * @throws IOException when the body carries no created event for it
     */
    public static String strCreated(String strBody, String strTemplate) throws IOException {
        int nAt = 0;
        while (true) {
            int nFrom = strBody.indexOf(STR_EVENT_CREATED, nAt);
            if (nFrom < 0)
                break;
            int nTo = strBody.indexOf(STR_EVENT_CREATED, nFrom + STR_EVENT_CREATED.length());
            String strChunk = nTo < 0 ? strBody.substring(nFrom) : strBody.substring(nFrom, nTo);
            // `Main:PurchaseOrder` IS THE MATCH, and the module is part of it:
            // a bare template name would also match the field of another
            // template that happened to carry it.
            if (strChunk.contains(strTemplate)) {
                return strValueAfter(strChunk, "\"contractId\":\"",
                        "a created " + strTemplate + " with no contract id");
            }
            nAt = nFrom + STR_EVENT_CREATED.length();
        }
        throw new IOException("no created " + strTemplate + " in the transaction: "
                + strShort(strBody));
    }


    /**
     * @param strBody where to look
     * @param strKey the key, with its opening quote
     * @param strWhy what to say when it is absent
     * @return the value
     * @throws IOException when the key is not there
     */
    private static String strValueAfter(String strBody, String strKey, String strWhy)
            throws IOException {
        int nAt = strBody.indexOf(strKey);
        if (nAt < 0)
            throw new IOException(strWhy + ": " + strShort(strBody));

        int nFrom = nAt + strKey.length();
        int nTo = strBody.indexOf('"', nFrom);
        if (nTo < 0)
            throw new IOException(strWhy + ": " + strShort(strBody));
        return strBody.substring(nFrom, nTo);
    }


    /**
     * @param strValue what to shorten
     * @return at most 300 characters of it, on one line
     */
    public static String strShort(String strValue) {
        String strTrim = strValue == null ? "" : strValue.replace("\n", " ").trim();
        return strTrim.length() > 300 ? strTrim.substring(0, 300) : strTrim;
    }


    private String strGet(String strPath) throws IOException, InterruptedException {
        HttpRequest.Builder bld = HttpRequest.newBuilder(URI.create(strBase + strPath))
                .timeout(Duration.ofSeconds(N_SECONDS_CALL))
                .GET();
        return strSend(bld);
    }


    private String strPost(String strPath, byte[] arrBody, String strType, int nSeconds)
            throws IOException, InterruptedException {
        HttpRequest.Builder bld = HttpRequest.newBuilder(URI.create(strBase + strPath))
                .header("Content-Type", strType)
                .timeout(Duration.ofSeconds(nSeconds))
                .POST(HttpRequest.BodyPublishers.ofByteArray(arrBody));
        return strSend(bld);
    }


    /**
     * @param bld the request, without its credential
     * @return the body
     * @throws IOException on any status at or above 400
     * @throws InterruptedException when the call is interrupted
     */
    private String strSend(HttpRequest.Builder bld) throws IOException, InterruptedException {
        // NO HEADER AT ALL WITH AUTH OFF. A wildcard participant checks
        // nothing, and a `Bearer null` is a credential it would have to decide
        // what to do with.
        if (strToken != null && !strToken.isEmpty())
            bld.header("Authorization", "Bearer " + strToken);

        HttpResponse<String> resp = client.send(bld.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + " "
                    + strShort(resp.body()));
        }
        return resp.body();
    }

}
