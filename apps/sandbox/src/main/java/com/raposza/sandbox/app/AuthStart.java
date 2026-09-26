// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.StorageOverlay;
import com.raposza.jwt.TokenShape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * `--auth <mode> [--token-shape <shape>]` on the headless Sandbox.
 *
 * <h2>Why the headless stack needs it</h2>
 *
 * Auth was the WINDOW'S, and the headless stack has never had any: nothing in
 * {@link SandboxOptions} names it, no mint is started, and the discovery
 * document publishes no `mint.token`. A consumer then gets ONE tokenless
 * connection for every user it names - `SandboxSession` falls back to the
 * document's own credential - so nothing that depends on WHO is asking can be
 * measured. The CaQL matrix lost twelve cases to that on 2026-09-15: ten
 * administrative statements need `participant_admin`, and two prove a token is
 * bound to the user it names.
 *
 * <h2>OFF UNLESS ASKED FOR - D-636</h2>
 *
 * No switch, no mint, no overlay, exactly as before. `--auth` is stripped
 * before {@link SandboxOptions} parses, the same shape `--fixture`,
 * `--discovery` and `--cli` have.
 *
 * <h2>The order is not negotiable</h2>
 *
 * The participant reads the JWKS WHILE IT STARTS, so the mint is up and
 * serving before {@link SandboxService#start} is called - which is why this
 * runs before the stack rather than beside it. The window learned the same
 * thing; `SandboxWindow` restarts its mint before Canton for this reason.
 *
 * Author Claude/bentzn
 */
public final class AuthStart {

    /** The switch, with an {@link AuthSettings.Mode} name as the next argument. */
    public static final String STR_ARG = "--auth";

    /** AUDIENCE or SCOPE, optional; the settings default applies without it. */
    public static final String STR_ARG_SHAPE = "--token-shape";

    /**
     * How long the mint is given. It is a Spring Boot start, so this is tens
     * of seconds in the ordinary case and the ceiling is for a cold machine.
     */
    public static final Duration TIMEOUT_MINT = Duration.ofMinutes(2);

    private AuthStart() {
    }


    /**
     * @param arrArg the command line
     * @return what the participant is to check, or null when `--auth` is absent
     * @throws IllegalArgumentException when the mode or the shape is not a name
     */
    public static AuthSettings authIn(String[] arrArg) {
        if (arrArg == null)
            return null;

        String strMode = strValueOf(arrArg, STR_ARG);
        if (strMode == null)
            return null;

        // `Mode.of` FALLS BACK TO JWKS rather than refusing, and that is its
        // rule and not one to second-guess here: the window reads a stored
        // profile through the same method, so a name this does not recognise
        // means the same thing on both paths.
        AuthSettings.Mode mode = AuthSettings.Mode.of(strMode);
        AuthSettings auth = AuthSettings.ofDefaults();
        String strShape = strValueOf(arrArg, STR_ARG_SHAPE);
        TokenShape shape = auth.shape();
        if (strShape != null) {
            try {
                shape = TokenShape.valueOf(strShape.trim().toUpperCase(java.util.Locale.ROOT));
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(STR_ARG_SHAPE + " does not name a shape: "
                        + strShape);
            }
        }
        return new AuthSettings(mode, shape, auth.strValue(), auth.strSecret(),
                auth.strFileCert(), auth.strUrlJwks());
    }


    /**
     * The line with both switches and their values taken out.
     *
     * @param arrArg the command line
     * @return what is left
     */
    public static String[] without(String[] arrArg) {
        if (arrArg == null)
            return new String[0];

        List<String> lstArg = new ArrayList<>();
        for (int idx = 0; idx < arrArg.length; idx++) {
            if (!STR_ARG.equals(arrArg[idx]) && !STR_ARG_SHAPE.equals(arrArg[idx])) {
                lstArg.add(arrArg[idx]);
                continue;
            }
            if (idx + 1 < arrArg.length)
                idx++;
        }
        return lstArg.toArray(new String[0]);
    }


    /**
     * Starts the mint and waits for its JWKS.
     *
     * A MINT THAT DOES NOT START IS FATAL HERE, and it is stated as the cause
     * rather than left to a timeout further down: a participant configured to
     * verify against a key set nobody serves does not come up, and the reason
     * it will never come up is known at this point.
     *
     * @param auth what the participant will check
     * @param outLine where the mint's own lines go
     * @return the running process, for the caller to stop
     * @throws IllegalStateException when it does not start or does not serve
     */
    public static JwtMintProcess mintServing(AuthSettings auth, Consumer<String> outLine) {
        JwtMintProcess mint = new JwtMintProcess(outLine);
        mint.start();
        if (JwtMintProcess.isExternal())
            outLine.accept("using the external OpenID Provider at "
                    + JwtMintProcess.strUrlBase());
        else
            outLine.accept("the JWT mint is starting from " + mint.fileJar());

        // THE KEY SET THE PARTICIPANT WILL ACTUALLY FETCH, which is the typed
        // override when there is one. Polling this process's own address
        // instead waited on a service the stack was never going to read.
        String strUrlJwks = auth.strUrlJwksEffective(JwtMintProcess.strUrlJwks());
        if (!JwtMintProcess.isJwksServing(strUrlJwks, TIMEOUT_MINT, null)) {
            mint.stop();
            throw new IllegalStateException("nothing served the JWKS at " + strUrlJwks
                    + " within " + TIMEOUT_MINT + ", so the participant would have no key"
                    + " to verify tokens against");
        }
        outLine.accept("the key set is served at " + strUrlJwks);
        return mint;
    }


    /**
     * The url a consumer fetches to get a bearer for one user - the document's
     * `mint.token`, and a GET rather than the OAuth POST.
     *
     * @param auth what the participant checks, or null
     * @param version the Canton running, which caps the lifetime
     * @param strUser the ledger user
     * @return the url, or null when nothing would verify a token anyway
     */
    public static String strUrlTokenFor(AuthSettings auth, VersionId version, String strUser) {
        if (auth == null || version == null || strUser == null || strUser.isBlank())
            return null;
        if (!auth.mode().flagTargets())
            return null;
        // NOT AGAINST A FOREIGN PROVIDER. `/mint.txt` is this project's own
        // endpoint; publishing it against a provider that does not serve it
        // hands a consumer a 404 dressed as a hand-over. The window refuses it
        // for the same reason - `JwtPane.strUrlTokenFor`.
        if (JwtMintProcess.isExternal())
            return null;
        return JwtMintProcess.strUrlBase() + JwtMintProcess.strMintQuery(auth, version,
                strUser.trim(), StorageOverlay.STR_NODE_PARTICIPANT);
    }


    /**
     * The line that turns the provider's endpoints into instructions.
     * Deliberately names the non-standard parameter, because that is the one a
     * conforming client will not send.
     */
    public static final String STR_HOW = "POST token_endpoint with"
            + " grant_type=client_credentials, client_id and the audience or scope given"
            + " here; the participant refuses a token that carries neither. The token"
            + " endpoint is POST-only. Where this node publishes a `token` url beside"
            + " this block, one GET on it returns the bare token as text; a provider this"
            + " application does not run publishes none.";


    /**
     * What an OIDC-aware client needs that NO discovery document carries.
     *
     * The participant checks an audience or a scope, and neither is in any
     * provider's document, so a conforming client doing plain
     * `client_credentials` gets a token that verifies against the key set and
     * is then refused by the participant - the hardest failure of the lot to
     * read. The value it has to send is here, beside a line saying so.
     *
     * PER NODE, not per document: on a multi-node topology each participant
     * checks its own audience.
     *
     * @param auth what the node checks, or null
     * @param strUser the ledger user to speak for, or null for the admin user
     *        every participant has
     * @param nameParticipant the node name, for the audience convention
     * @return never null; empty when the node checks nothing
     */
    public static Map<String, Object> mapOidcFor(AuthSettings auth, String strUser,
            String nameParticipant) {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        if (auth == null || !auth.mode().flagTargets())
            return mapOut;

        String strSub = (strUser == null || strUser.isBlank()) ? AviationRun.STR_USER_ADMIN
                : strUser.trim();
        mapOut.put("client_id", strSub);
        if (auth.shape() == TokenShape.SCOPE)
            mapOut.put("scope", auth.strScopeEffective());
        else
            mapOut.put("audience", auth.strAudienceFor(nameParticipant));
        mapOut.put("how", STR_HOW);
        return mapOut;
    }


    /**
     * A bearer for one user, in a file, for a Daml script to present.
     *
     * @param auth what the participant checks
     * @param version the Canton running
     * @param strUser the ledger user the token speaks for
     * @return the file holding it
     * @throws IOException when the mint never answered
     */
    public static Path fileTokenFor(AuthSettings auth, VersionId version, String strUser)
            throws IOException {
        String strToken = JwtMintProcess.strMintBlocking(auth, version, strUser,
                StorageOverlay.STR_NODE_PARTICIPANT, TIMEOUT_MINT);
        if (strToken == null) {
            throw new IOException("the JWT mint did not mint a token for " + strUser
                    + " within " + TIMEOUT_MINT);
        }

        Path fileOut = Files.createTempFile("aviation-token-" + strUser, ".txt");
        Files.write(fileOut, strToken.getBytes(StandardCharsets.UTF_8));
        return fileOut;
    }


    private static String strValueOf(String[] arrArg, String strSwitch) {
        for (int idx = 0; idx < arrArg.length - 1; idx++) {
            if (strSwitch.equals(arrArg[idx]))
                return arrArg[idx + 1];
        }
        return null;
    }
}
