// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.port.PortClass;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.ToolTipManager;
import javax.swing.SwingUtilities;

/**
 * What every labelled field in this window means, in two lengths.
 *
 * <h2>Why this exists instead of a manual</h2>
 *
 * A form of twenty-two fields spread over five tabs cannot be explained by a
 * document nobody opens. The short text is a tooltip on the label, which costs
 * a reader nothing; the long text is a dialog raised by RIGHT-CLICKING the
 * label, which costs one click and is where the range, the default, whether
 * the field is required and what a wrong value does actually live. There is no
 * third place, and there is no long-form documentation for these fields
 * anywhere else - this IS it.
 *
 * <h2>The links, and the half of the form that has none</h2>
 *
 * Roughly half of these fields are THIS APPLICATION'S and not Canton's - the
 * first port, the ready timeout, everything on the Settings tab, the Scripts
 * rows, the DARs directory. No vendor documents them because no vendor has
 * them. Those entries carry no link, and the dialog shows no link section at
 * all rather than pointing a reader at somebody else's page about something
 * else.
 *
 * Where a vendor page does exist, the URL is PER GENERATION and HARD-CODED to
 * the newest documentation line of that generation at the time of writing -
 * 3.5 on {@code docs.digitalasset.com}, and the unversioned {@code
 * docs.daml.com} alias for 2.x. It is deliberately not derived from the
 * selected Canton: a 3.5.16 participant is documented by the 3.5 line, and
 * building a URL per patch version would produce forty links of which none
 * had been opened. Every URL below was fetched and read on 2026-08-24. When a
 * line moves, the constants at the top of this class are the whole edit.
 *
 * Author Claude/bentzn
 */
public final class FieldHelp {

    // ---- the documentation lines, one per generation -------------------
    //
    // ONLY PAGES CONFIRMED FROM THIS MACHINE WITH curl. Two earlier revisions
    // carried URLs taken from a search index; every one of them answered 404,
    // because Digital Asset retired the whole Canton section of
    // docs.digitalasset.com - that domain is now a product portfolio, and the
    // 3.x documentation moved to docs.canton.network, unversioned.
    //
    // THE 3.x PAGES ARE FETCHABLE AS MARKDOWN. docs.canton.network is a
    // Mintlify site and serves the same page at <url>.md as text/markdown,
    // about 16 kB each. HelpDialog reads that directly rather than opening a
    // browser: see FMT_MD there. The URL stored here is the HUMAN one, without
    // the suffix, because it is also what is shown when the fetch fails.
    //
    // THE 2.x PAGES ARE SPHINX AND HAVE NO MARKDOWN TWIN, so they stay browser
    // links. That line is stable, and anybody still on 2.x is unlikely to be
    // reading a reference for the first time.

    /** Canton 3.x: token formats, claims, and what each right permits. */
    private static final String URL_3X_AUTHZ =
            "https://docs.canton.network/appdev/deep-dives/authorization";

    /** Canton 3.x: the sandbox, including the ports it reserves. */
    private static final String URL_3X_SANDBOX =
            "https://docs.canton.network/sdks-tools/development-tools/sandbox";

    /** Canton 3.x: PQS. */
    private static final String URL_3X_PQS =
            "https://docs.canton.network/sdks-tools/development-tools/pqs";

    /** Canton 2.x: the API manual, which carries auth-services. */
    private static final String URL_2X_APIS =
            "https://docs.daml.com/canton/usermanual/apis.html";

    /** Canton 2.x: what a token carries and what each right permits. */
    private static final String URL_2X_AUTHZ =
            "https://docs.daml.com/app-dev/authorization.html";

    /** Canton 2.x: the sandbox. */
    private static final String URL_2X_SANDBOX = "https://docs.daml.com/tools/sandbox.html";

    /** Canton 2.x: PQS. */
    private static final String URL_2X_PQS = "https://docs.daml.com/query/pqs-user-guide.html";

    // ---- the keys ------------------------------------------------------

    public static final String KEY_CANTON = "canton";

    public static final String KEY_PQS = "pqs";

    public static final String KEY_PORT_FIRST = "port.first";

    public static final String KEY_PORT_POSTGRES = "port.postgres";

    public static final String KEY_TIMEOUT_READY = "timeout.ready";

    public static final String KEY_AUTH = "auth";

    public static final String KEY_TOKEN_SHAPE = "token.shape";

    public static final String KEY_AUTH_TARGET = "auth.target";

    public static final String KEY_SECRET = "secret";

    public static final String KEY_CERTIFICATE = "certificate";

    public static final String KEY_JWKS = "jwks";

    public static final String KEY_SCRIPT_PROJECT = "script.project";

    public static final String KEY_SCRIPT_DAR = "script.dar";

    public static final String KEY_SCRIPT_NAME = "script.name";

    public static final String KEY_DARS_DIR = "dars.directory";

    public static final String KEY_JWT_USER = "jwt.user";

    /**
     * The Settings tab keys ARE the settings keys.
     *
     * `SettingsPane` already holds the settings key at the point it builds a
     * row, so reusing it means there is no second name to keep in step with
     * the first. They are spelt out here rather than referenced from
     * `RaposzaSettings` so that this class stays a plain table with no
     * dependency on the runtime module.
     */
    public static final String KEY_SET_DIR_HOME = "dir.home";

    public static final String KEY_SET_PORT_MINT = "port.mint";

    /** Settings tab. This application's own; no vendor documents it. */
    public static final String KEY_SET_URL_OIDC = "oidc.url";

    public static final String KEY_SET_PORT_DISCOVERY = "port.discovery";

    public static final String KEY_SET_PORT_FIRST = "default.port.first";

    /** Settings tab. This application's own, and no vendor documents it. */
    public static final String KEY_SET_OFFER_AVIATION = "fixture.aviation.offer";

    public static final String KEY_SET_PORT_POSTGRES = "default.port.postgres";

    /** Settings tab. LocalNetND's web UI block - `todo.md` A-42. */
    public static final String KEY_SET_PORT_UI_FIRST = "default.port.ui.first";

    public static final String KEY_SET_SECONDS_READY = "default.timeout.ready.seconds";

    /**
     * What is appended to every tooltip.
     *
     * The dialog is worth having only if a reader finds out it is there, and
     * a right-click has no affordance of its own. The hand cursor says the
     * label is live; this says what it does.
     */
    private static final String STR_HINT = "  (click for detail)";

    /**
     * How long a label is hovered before its tooltip appears, in
     * milliseconds.
     *
     * The Swing default is 750 ms, which on a form of twenty-two rows means a
     * reader scanning down the column waits nearly a second per row and gives
     * up before the second one. GLOBAL, because `ToolTipManager` is a
     * singleton and a form whose rows answered at two different speeds would
     * read as one of them being broken.
     */
    private static final int N_MS_TOOLTIP = 500;

    /**
     * Which Canton generation the links should point at.
     *
     * STATIC, and set by the form when the installation box changes. One
     * window per JVM, so this is a field on the application rather than
     * shared state between two of anything. Zero means "not yet known", in
     * which case the dialog shows whichever link exists and names its
     * generation.
     */
    private static volatile int nMajor;

    private static final Map<String, Help> MAP_HELP = new HashMap<>();

    static {
        put(KEY_CANTON,
                "Which installed SDK, and so which Canton, this stack runs.",
                "Each row names the SDK first - the number your daml.yaml calls"
                + " sdk-version - and then the Canton that SDK runs."
                + " On the 3.5 line they differ: SDK 3.5.5 runs Canton 3.5.12. A row"
                + " whose SDK reads \"-\" is a Canton no SDK on this machine ships.\n\n"
                + "The box lists every Canton this machine has, oldest first, and the"
                + " selection becomes an exact version rather than a line. An entry"
                + " with no runtime jar is shown DISABLED rather than hidden, so"
                + " \"my 2.10 is missing\" is answered by looking at it greyed out.\n\n"
                + "Required. There is no default: the terminal picks the newest of"
                + " 3.5 because a default that moved with what a machine happens to"
                + " have would make one command mean two things, but a window can"
                + " show what is there and so it asks.\n\n"
                + "Changing this is not a small edit to one row. The selection owns"
                + " the settings: the outgoing version's profile is written, the"
                + " incoming one is read into the form, and any row whose feature"
                + " the new version does not have is taken away. An unmeasured"
                + " version keeps every row - hiding a control from the one person"
                + " who could find out whether it works is worse than showing one"
                + " that turns out to do nothing.",
                URL_3X_SANDBOX, URL_2X_SANDBOX);

        put(KEY_PQS,
                "Whether to run PQS against this stack.",
                "PQS - scribe - streams the ledger into PostgreSQL so it can be"
                + " queried in SQL. OFF or ON.\n\n"
                + "Starts OFF EVERY TIME, and is deliberately not part of the saved"
                + " profile: it is a decision about this run rather than a property"
                + " of the version.\n\n"
                + "What actually runs depends on what is staged: the vendor scribe"
                + " where one was found for the Canton line, a stand-in on the 2.10"
                + " line, and an in-process mock otherwise. The PQS tab names which"
                + " one it got and the command it was given.\n\n"
                + "With auth on, PQS needs a token, and it is minted before Canton"
                + " starts and re-minted from the mint's OAuth endpoint as it"
                + " expires. On a first start against an empty ledger scribe will"
                + " sit in \"No parties found matching *\" and retry on a doubling"
                + " backoff until a party exists. That is a wait, not a hang; the"
                + " footer says so.",
                URL_3X_PQS, URL_2X_PQS);

        put(KEY_PORT_FIRST,
                "The lowest port this stack takes; the rest follow it.",
                "One number decides the whole stack. The ledger API, admin API,"
                + " JSON API, PQS health and the rest are allocated from it in a"
                + " fixed order, so two stacks side by side need only two numbers"
                + " that are far enough apart.\n\n"
                + "Required, " + PortClass.NODE.nLow() + "-"
                + LocalNetPorts.N_PORT_FIRST_MAX + ". The node ports are the 30xxx"
                + " thousand, and the cap keeps the whole block inside it - PQS"
                + " health, its last slot, sits 42 above the first port. The web"
                + " UIs have a block of their own in 31xxx, and PostgreSQL, the"
                + " mint and discovery are single ports in 32xxx, so the thousand"
                + " says what a port is.\n\n"
                + "All three stay below 32768. Linux hands out ephemeral ports"
                + " from there upwards, and a stack parked in that range can lose"
                + " a port to an unrelated outbound connection between one start"
                + " and the next - a collision measured the expensive way.\n\n"
                + "A port already held produces \"Failed to bind to address\" from"
                + " Canton. `lsof -n -i` names the holder.",
                URL_3X_SANDBOX, URL_2X_SANDBOX);

        put(KEY_PORT_POSTGRES,
                "The port the embedded PostgreSQL listens on.",
                "The stack runs its own PostgreSQL - the Zonky embedded server -"
                + " and this is where it listens. Canton's four databases and the"
                + " PQS database are all created inside it.\n\n"
                + "Required, " + PortClass.ADMIN.nLow() + "-"
                + PortClass.ADMIN.nHigh() + " - an administrative port, below"
                + " the ephemeral range.\n\n"
                + "It is separate from the stack's own block so that a stack can be"
                + " restarted on a different port range without moving its data, and"
                + " so that psql can be pointed at a known number.",
                null, null);

        put(KEY_TIMEOUT_READY,
                "How long to wait for a component to report ready.",
                "Applies to each component of the start in turn, not to the start"
                + " as a whole. A component that has not said it is ready inside"
                + " this window fails the start and takes the rest of the stack"
                + " down with it.\n\n"
                + "Required, in whole seconds.\n\n"
                + "The figure that matters is the participant, and it varies more"
                + " than anything else here: about forty-five seconds on a fast"
                + " workstation, nearer three minutes on a modest machine, and"
                + " longer again on a first 2.x start, whose index schema is"
                + " ninety-nine Flyway migrations. Raise it before concluding a"
                + " start is broken - the phase in the footer says what the"
                + " participant is doing while it waits.",
                null, null);

        put(KEY_AUTH,
                "What the participant checks on the Ledger API.",
                "NONE leaves the participant wide open: it checks nothing, so no"
                + " token is needed and PQS presents none.\n\n"
                + "The other modes configure an auth-service on the participant and"
                + " are what the JWT tab mints against:\n"
                + "  UNSAFE_HMAC_256 - a shared secret, HS256. Test only; the mint"
                + " is sent the same secret so the token verifies.\n"
                + "  RS256/ES256/ES512 by certificate - the public key is loaded"
                + " from an X.509 file, PEM or DER.\n"
                + "  JWKS - the participant fetches the key set from a URL. This is"
                + " what the built-in mint serves, and it is the measured path.\n\n"
                + "Which of the rows below apply is decided here; the ones that do"
                + " not are taken away rather than left to be filled in and"
                + " discarded.\n\n"
                + "With anything other than NONE the start waits for the JWT"
                + " service before Canton comes up, because the participant reads"
                + " the JWKS while it starts.",
                URL_3X_AUTHZ, URL_2X_APIS);

        put(KEY_TOKEN_SHAPE,
                "Whether tokens are checked by audience or by scope.",
                "A user access token is a JWT in one of two encodings, and the"
                + " participant is configured for one of them:\n\n"
                + "  AUDIENCE - the `aud` claim names this participant. Canton's own"
                + " default is https://daml.com/jwt/aud/participant/<participantId>,"
                + " and a different value can be configured as target-audience.\n"
                + "  SCOPE - the `scope` claim names a purpose. The default is"
                + " daml_ledger_api; other values go in target-scope.\n\n"
                + "The two are mutually exclusive on the participant - one or the"
                + " other may be configured, never both. Audience is the safer"
                + " default: it is compatible with more identity providers, and it"
                + " ties the token to one participant, so a token cannot be replayed"
                + " against a different one.\n\n"
                + "This decides whether the row below asks for an Audience or a"
                + " Scope, and the mint is told the same thing, so what is issued"
                + " and what is checked cannot drift apart.",
                URL_3X_AUTHZ, URL_2X_AUTHZ);

        put(KEY_AUTH_TARGET,
                "The audience or scope the token must carry.",
                "The row is an AUDIENCE or a SCOPE depending on the token shape"
                + " above, and the value goes into the participant's configuration"
                + " as target-audience or target-scope AND into what the mint puts"
                + " in the token. They cannot disagree.\n\n"
                + "Leave it EMPTY to take the vendor default -"
                + " https://daml.com/jwt/aud/participant/<node> for audience,"
                + " daml_ledger_api for scope. Filling it in is for reproducing an"
                + " identity provider that rewrites the claim.\n\n"
                + "A scope may hold letters, digits, hyphens, slashes, colons and"
                + " underscores, and is case-sensitive.\n\n"
                + "A mismatch here does not fail the start. It fails the first call"
                + " - PERMISSION_DENIED or INVALID_TOKEN on a token whose signature"
                + " and lifetime the participant has just accepted.",
                URL_3X_AUTHZ, URL_2X_APIS);

        put(KEY_SECRET,
                "The shared secret for HS256, test only.",
                "With UNSAFE_HMAC_256 the participant expects every token signed"
                + " with HMAC-SHA256 using this plaintext secret, and the same"
                + " string is sent to the mint so that what it issues verifies.\n\n"
                + "Required in that mode. There is a minimum length, enforced by"
                + " the form.\n\n"
                + "The name is the vendor's and it is not decoration: anyone"
                + " holding the secret can mint a token for any user on this"
                + " participant. It exists so a symmetric column can be exercised"
                + " without a key pair. Do not carry a value from here to anything"
                + " that is not a sandbox.",
                null, URL_2X_APIS);

        put(KEY_CERTIFICATE,
                "X.509 file holding the public key that verifies tokens.",
                "For the certificate modes the participant loads a public key from"
                + " this file and expects every token signed with the matching"
                + " private key - RS256, ES256 or ES512 according to the mode"
                + " selected above.\n\n"
                + "Required in those modes. PEM (a text file beginning"
                + " -----BEGIN CERTIFICATE-----) and DER (binary) are both"
                + " accepted. Browse picks the file; the path is stored in the"
                + " version's profile.\n\n"
                + "This is the CERTIFICATE, never the private key and never a"
                + " token. A self-signed pair is enough for a sandbox:\n"
                + "  openssl req -nodes -new -x509 -keyout s.key -out s.crt\n\n"
                + "Unlike JWKS this is read once, at start. Rotating the key means"
                + " restarting the stack.",
                null, URL_2X_APIS);

        put(KEY_JWKS,
                "URL the participant fetches the verification key set from.",
                "In JWKS mode the participant fetches a JSON Web Key Set over HTTP"
                + " and accepts tokens signed with RS256, ES256 or ES512 by any key"
                + " in it.\n\n"
                + "Required in that mode. Leave it pointing at the built-in mint -"
                + " which serves /oauth2/jwks on the JWT mint port - unless you are"
                + " deliberately reproducing an external identity provider.\n\n"
                + "This is the measured path and the one the rest of the window is"
                + " built around: the mint signs, the participant fetches, and the"
                + " key can rotate without restarting the stack. Canton caches the"
                + " key set for five minutes by default, so a rotation is not"
                + " necessarily visible immediately.\n\n"
                + "A URL the participant cannot reach fails the START, not the"
                + " first call.",
                null, URL_2X_APIS);

        put(KEY_SCRIPT_PROJECT,
                "The Daml project directory the script is built from.",
                "The directory holding daml.yaml. It is what is built to produce"
                + " the DAR, and the script name below is resolved inside the"
                + " packages that build yields.\n\n"
                + "Required to build; not required if a DAR is named directly.\n\n"
                + "The resolved path is shown under the row, because a relative"
                + " path here is relative to this window's working directory and"
                + " not to wherever the terminal was when the window opened.",
                null, null);

        put(KEY_SCRIPT_DAR,
                "The built DAR the script is run from.",
                "A .dar file. Naming one skips the build entirely and runs against"
                + " what is in the file, which is the faster loop when the Daml has"
                + " not changed.\n\n"
                + "One of this and the project is required.\n\n"
                + "A DAR carries its dependencies, so the one named here is the"
                + " whole package set the script sees. If the script is not found,"
                + " the usual cause is a DAR built before the script was added"
                + " rather than a wrong name.",
                null, null);

        put(KEY_SCRIPT_NAME,
                "Fully qualified name of the script to run.",
                "Module:entity - for example Main:setup. The module path uses dots"
                + " and the entity is separated by a colon.\n\n"
                + "Required.\n\n"
                + "It has to name a Daml Script, not any function: it must be a"
                + " top-level definition of Script type in a package the DAR"
                + " carries. A name that does not resolve fails immediately with a"
                + " list of what the DAR does contain, which is usually enough to"
                + " see the typo.",
                null, null);

        put(KEY_DARS_DIR,
                "Where this run looks for DARs to upload.",
                "One directory under the run directory. Everything ticked in the"
                + " list is uploaded to the participant as part of the start,"
                + " before the stack is reported ready.\n\n"
                + "Not required - a stack with no DARs starts perfectly well.\n\n"
                + "The directory is prepared at every start, so a file dropped in"
                + " while the window is open is picked up by Refresh without"
                + " restarting. Removing, vetting and unvetting a package on a"
                + " RUNNING participant is the DARs tab's live half and goes over"
                + " the admin API rather than through this directory.",
                null, null);

        put(KEY_JWT_USER,
                "The ledger user a minted token speaks for.",
                "Goes into the token's `sub` claim. The participant resolves it to"
                + " a user and takes that user's current rights - so the token"
                + " carries an identity, not a permission set, and rights changed"
                + " through user management take effect without minting again.\n\n"
                + "Required to mint. The box is filled from the participant's own"
                + " user list; Refresh re-reads it.\n\n"
                + "participant_admin exists on a bare start without any bootstrap"
                + " step, which is why it is the fallback and what PQS presents"
                + " by default. A `sub` naming a user that does not exist"
                + " authenticates and then authorises nothing: the call fails with"
                + " PERMISSION_DENIED and UserNotFound, on a token the participant"
                + " has just accepted as valid.",
                URL_3X_AUTHZ, URL_2X_AUTHZ);

        // ---- the Settings tab. Keyed by their own settings keys, which are
        // already distinct strings and are what the pane has in hand at the
        // point it builds each row - so no second name has to be invented and
        // then kept in step with the first. None of these is a Canton
        // setting, so none of them carries a link.

        put(KEY_SET_DIR_HOME,
                "Where this application keeps its own state.",
                "One directory holding everything this application owns rather"
                + " than borrows: the mint's keys, the fixtures, the package"
                + " cache, the saved ledger hosts and the audit log.\n\n"
                + "Required. It is created if it is not there.\n\n"
                + "Nothing Canton writes lives here - a stack's databases and"
                + " work files go under its own run directory, so this can be"
                + " moved or backed up without touching any stack.\n\n"
                + "Moving it does NOT move what is already there. The mint will"
                + " generate a fresh key pair at the new location, which means"
                + " every token issued before the move stops verifying.",
                null, null);

        put(KEY_SET_PORT_DISCOVERY,
                "Port for the discovery endpoint.",
                "The discovery endpoint serves one document describing what this"
                + " machine is running - which stack, on which ports, with which"
                + " auth - so a tool does not have to be told by hand.\n\n"
                + "Required, " + PortClass.ADMIN.nLow() + "-"
                + PortClass.ADMIN.nHigh() + " - an administrative port. It"
                + " belongs to the application rather than to any one stack: it"
                + " stays put while stacks come and go.",
                null, null);

        put(KEY_SET_URL_OIDC,
                "An OpenID Provider to use instead of starting one.",
                "LEAVE IT EMPTY and this window starts its own provider - Raposza"
                + " OIDC, on the JWT mint port above, on the loopback address - and"
                + " stops it again when the window closes. That is the default and"
                + " it needs nothing running beforehand.\n\n"
                + "PUT A BASE URL HERE and nothing is started. The provider's"
                + " discovery document is read, the participant is configured with"
                + " the jwks_uri it publishes, and the Sandbox's own discovery"
                + " document reports the provider as external so a consumer knows"
                + " it outlives this window.\n\n"
                + "IT NEED NOT BE A RAPOSZA OIDC. A stack needs one thing from a"
                + " provider - a key set the participant can fetch - so any"
                + " conforming OpenID Provider serves. Two controls on the OIDC tab"
                + " go off against a foreign one, Mint JWT and Refresh users,"
                + " because both mint through /mint.txt, which is this project's own"
                + " endpoint and not part of OpenID Connect.\n\n"
                + "ONE CONSTRAINT IS NOT AN ENDPOINT. Below Canton 3.5.6 the"
                + " participant applies its own short ceiling to a token lifetime"
                + " and cannot be told otherwise, so a provider that cannot be"
                + " configured to issue short tokens will have every token refused"
                + " on that column.\n\n"
                + "http:// or https://, no trailing slash needed. A change takes"
                + " effect on the next start.",
                null, null);

        put(KEY_SET_PORT_MINT,
                "Port the JWT mint service listens on.",
                "The mint is an ordinary process this application starts, and this"
                + " is where it listens. It serves the JWKS the participant"
                + " fetches, the OAuth token endpoint PQS re-mints from, and the"
                + " mint form on the JWT tab.\n\n"
                + "Required, " + PortClass.ADMIN.nLow() + "-"
                + PortClass.ADMIN.nHigh() + " - an administrative port.\n\n"
                + "CHANGING IT CHANGES THE JWKS URL, which is what a participant"
                + " is configured with at start. A stack already running was"
                + " configured with the old number and will keep asking for it, so"
                + " a change here takes effect on the next start rather than"
                + " immediately.",
                null, null);

        put(KEY_SET_PORT_POSTGRES,
                "Default PostgreSQL port for a new stack; LocalNetND's PostgreSQL port.",
                "What the Sandbox form's PostgreSQL port row is pre-filled with"
                + " when a version has no saved profile yet. On the Sandbox it is"
                + " a DEFAULT, not a setting the stack reads - a profile that"
                + " already has a number keeps it.\n\n"
                + "LocalNetND HAS NO PROFILE, so on that topology this IS the port"
                + " its PostgreSQL listens on, from the next start.\n\n"
                + "Required, " + PortClass.ADMIN.nLow() + "-"
                + PortClass.ADMIN.nHigh() + " - an administrative port.",
                null, null);

        put(KEY_SET_OFFER_AVIATION,
                "Whether a start may offer to build the Aviation fixture.",
                "A freshly bootstrapped ledger holds almost nothing, so a browser"
                + " that renders contracts wrongly cannot be told from one that"
                + " renders an empty ledger correctly. The Aviation fixture is a"
                + " small, known, non-trivial state to look at instead: an"
                + " aircraft operator's maintenance ledger, seven roles, three"
                + " airframes, one of them with a defect under investigation, and"
                + " an engine on its way through a shop.\n\n"
                + "With this on, a start where the fixture has not been built for"
                + " the selected Canton offers to build it, upload it and run it."
                + " Answering `Never` in that dialog is what switches this off,"
                + " and this row is where it goes back on.\n\n"
                + "It needs a Daml SDK that can build for the selected Canton. A"
                + " machine with the Canton component and no matching SDK bundle"
                + " is never asked, because there would be nothing to run the"
                + " build with.\n\n"
                + "On by default. It changes nothing about a stack that is"
                + " already running.",
                null, null);

        put(KEY_SET_PORT_FIRST,
                "Default first stack port for a new stack; LocalNetND's first node port.",
                "What the Sandbox form's first port row is pre-filled with when a"
                + " version has no saved profile yet. The rest of a stack's ports"
                + " are allocated upwards from whatever that row ends up holding.\n\n"
                + "LocalNetND HAS NO PROFILE, so on that topology this IS the"
                + " first port of its node block - sv, app-provider and app-user"
                + " ten apart, then the sequencers, mediators, scan and the sv"
                + " app, then PQS health, " + (LocalNetPorts.N_SPAN_NODE + 1)
                + " ports in all. Moving it moves the founding snapshot key, so"
                + " the first start on a new number founds again.\n\n"
                + "Required, " + PortClass.NODE.nLow() + "-"
                + LocalNetPorts.N_PORT_FIRST_MAX + ", so the block the row opens stays"
                + " inside the 30xxx node ports.",
                null, null);

        put(KEY_SET_PORT_UI_FIRST,
                "First port of LocalNetND's web UIs.",
                "The sv, app-provider and app-user web UIs listen on this port and"
                + " the two " + LocalNetPorts.N_STRIDE_UI + " and "
                + (2 * LocalNetPorts.N_STRIDE_UI) + " above it. When the window"
                + " runs its own OpenID Provider, each start registers those"
                + " origins there, so a moved block signs in without anything"
                + " done by hand.\n\n"
                + "LocalNetND only. The Sandbox serves no web UIs.\n\n"
                + "Required, " + PortClass.UI.nLow() + "-"
                + LocalNetPorts.N_PORT_UI_FIRST_MAX + ", so the block stays inside"
                + " the 31xxx web UI ports.",
                null, null);

        put(KEY_SET_SECONDS_READY,
                "Default ready timeout for a new stack.",
                "What the Sandbox form's ready timeout row is pre-filled with when"
                + " a version has no saved profile yet. A profile that already has"
                + " a number keeps it.\n\n"
                + "Required, whole seconds, at least 1.\n\n"
                + "Set it for the SLOWEST machine and start this profile will meet,"
                + " which is a first 2.x start on a modest box: its index schema is"
                + " ninety-nine Flyway migrations before anything else happens.",
                null, null);
    }


    private FieldHelp() {
        throw new AssertionError("no instances");
    }


    /**
     * Tells the dialog which generation's documentation to offer. Called by
     * the form when the installation selection changes.
     *
     * @param nMajorNew the selected Canton's major version, or 0 for unknown
     */
    public static void useGeneration(int nMajorNew) {
        nMajor = nMajorNew;
    }


    /**
     * Puts the short text on the label as a tooltip and makes a right-click
     * raise the long one. A key with no entry leaves the label untouched, so
     * a row added without help is plain rather than broken.
     *
     * @param lbl the label; never null
     * @param strKey one of the KEY_ constants
     */
    public static void install(JLabel lbl, String strKey) {
        Help help = MAP_HELP.get(strKey);
        if (lbl == null || help == null)
            return;

        ToolTipManager.sharedInstance().setInitialDelay(N_MS_TOOLTIP);
        lbl.setToolTipText(help.strShort() + STR_HINT);
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.addMouseListener(new MouseAdapter() {

            /**
             * LEFT BUTTON, ON THE CLICK. A label is not a control and a
             * left-click on one does nothing else, so there is nothing to
             * collide with; and a right-click, which this used to want, has
             * no affordance a reader would find without being told. The hand
             * cursor and the tooltip now say the same thing as the gesture.
             *
             * `mouseClicked` rather than pressed or released, so that a drag
             * beginning on a label does not raise the dialog.
             *
             * @param evt the mouse event
             */
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt))
                    HelpDialog.show(lbl, lbl.getText(), help, nMajor);
            }
        });
    }


    /**
     * @param strKey one of the KEY_ constants
     * @return the entry, or null when there is none
     */
    public static Help of(String strKey) {
        return MAP_HELP.get(strKey);
    }


    private static void put(String strKey, String strShort, String strLong, String strUrl3x,
            String strUrl2x) {
        MAP_HELP.put(strKey, new Help(strShort, strLong, strUrl3x, strUrl2x));
    }


    /**
     * One field's help.
     *
     * @param strShort one line for the tooltip
     * @param strLong the dialog text; blank-line separated paragraphs
     * @param strUrl3x the Canton 3.x page, or null when there is none
     * @param strUrl2x the Canton 2.x page, or null when there is none
     */
    public record Help(String strShort, String strLong, String strUrl3x, String strUrl2x) {

        /**
         * @param nMajorHere the selected Canton's major version, or 0
         * @return the URL to offer, or null when this field has none
         */
        public String strUrlFor(int nMajorHere) {
            // NO CROSS-GENERATION FALLBACK. A 3.x reader offered the 2.x page
            // because 3.x has none is worse served than one offered nothing:
            // the 2.x wording differs in exactly the places that matter, and
            // the button would be telling them it was their documentation.
            if (nMajorHere == 2)
                return strUrl2x;
            if (nMajorHere == 3)
                return strUrl3x;
            return strUrl3x != null ? strUrl3x : strUrl2x;
        }


        /**
         * @param nMajorHere the selected Canton's major version, or 0
         * @return what to call the link, naming the generation it documents
         *         so a reader on the other one is not misled
         */
        public String strLinkFor(int nMajorHere) {
            String strUrl = strUrlFor(nMajorHere);
            if (strUrl == null)
                return null;
            return strUrl.equals(strUrl2x) ? "Official documentation - Canton 2.x"
                    : "Official documentation - Canton 3.x";
        }
    }

}
