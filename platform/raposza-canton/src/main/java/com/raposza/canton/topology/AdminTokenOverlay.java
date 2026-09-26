// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import java.time.Duration;

/**
 * The overlay that pins the participant's admin token and says what it may do.
 *
 * <h2>Why pin it at all</h2>
 *
 * `fixedAdminToken` is an `Option`, so Canton MINTS one at startup when nothing
 * sets it, and `adminTokenDuration` defaults to **5 minutes** - measured with
 * jshell on 3.5.11, 2026-08-12. A generated token that rotates every five
 * minutes is not something a second process can be handed, and a stack that is
 * debugged over a longer session gets a different token each time it is looked
 * at. Pinning makes the admin channel deterministic and independent of whether
 * the JWKS material is right, which is exactly what is wanted while the JWKS
 * material is what is being debugged.
 *
 * <h2>Both claims default to FALSE, and that is the trap</h2>
 *
 * `adminClaim` and `actAsAnyPartyClaim` are both false by default - measured,
 * same session. So Canton's own admin token does NOT carry an admin claim on
 * the Ledger API, and a console script that allocates a party through the admin
 * API may succeed while a `users.create` beside it is refused, because the
 * second goes through `UserManagementService` on the Ledger API and the first
 * does not. That failure arrives late and reads nothing like an auth problem.
 *
 * {@link #ofAdmin} therefore sets `admin-claim` explicitly rather than relying
 * on a default that points the other way.
 *
 * <h2>act-as-any-party is a shortcut this project declines by default</h2>
 *
 * With it, an admin token alone satisfies scribe's `--pipeline-filter-parties=*`
 * - no user, no JWKS, no token endpoint. It is available here because it is
 * real and because it is the fallback if the JWKS path stalls, and it is not
 * the default because it is a shared bearer secret on a command line and
 * diverges from what production does.
 *
 * <h2>The block name is measured; ONE of the four is confirmed, three are not</h2>
 *
 * The block is `admin-token-config`, from the accessor `adminTokenConfig()` on
 * `LedgerApiServerConfig`. The first attempt used `admin-token`, taking the
 * type name `AdminTokenConfig` as evidence for a field called `adminToken`, and
 * Canton rejected it by path, file and line:
 *
 * <pre>
 * at 'canton.participants.sandbox.ledger-api.admin-token':
 *   (admin-token.conf: 3) Unknown key.
 * </pre>
 *
 * **A field's type name is not its field name**, and the accessor is the thing
 * to read. The four keys inside the block still follow the camel-to-kebab rule
 * that was measured for `UnsafeJwtHmac256` -> `unsafe-jwt-hmac-256`; Canton
 * refuses an unknown key one at a time, so a stack that starts confirms them
 * and a stack that does not names the next one.
 *
 * **One of the four is no longer resting on that argument.** Canton 3.5.9's
 * release notes print `act-as-any-party-claim` at its full path, and
 * `non-standard-config` beside it, as the pair an operator sets to run local
 * traffic enforcement with auth enabled - the vendor spelling both keys this
 * class renders. That is documentary confirmation rather than inference, and it
 * is a different KIND of evidence from a stack that started: a start says every
 * key parsed, and cannot say which spelling of a key Canton would also have
 * accepted. `fixed-admin-token`, `admin-token-duration` and `admin-claim` are
 * still confirmed only by the start.
 *
 * Author Claude/bentzn
 */
public final class AdminTokenOverlay {

    /**
     * The block on `ledger-api`, beside `auth-services`.
     *
     * Measured, after `admin-token` was rejected: the accessor on
     * `LedgerApiServerConfig` is `adminTokenConfig()`, on 3.4.11 and 3.5.11
     * alike, so the key carries the `Config` suffix that the field's TYPE name
     * also carries. Reading `AdminTokenConfig` as the type and assuming the
     * field was `adminToken` is what produced the first spelling.
     */
    public static final String STR_KEY_BLOCK = "admin-token-config";

    public static final String STR_KEY_FIXED = "fixed-admin-token";

    public static final String STR_KEY_DURATION = "admin-token-duration";

    public static final String STR_KEY_ADMIN_CLAIM = "admin-claim";

    /**
     * The only one of the four inside the block confirmed by something other
     * than a stack that started: Canton 3.5.9's release notes spell it at its
     * full path.
     */
    public static final String STR_KEY_ACT_AS_ANY = "act-as-any-party-claim";

    /**
     * Canton REFUSES to start with this block unless the escape hatch is open,
     * and it says so exactly:
     *
     * <pre>
     * CONFIG_VALIDATION_ERROR(8,0): Modifying ledger-api.admin-token-config.*
     * for participant sandbox requires you to explicitly set
     * canton.parameters.non-standard-config = yes
     * </pre>
     *
     * Measured on 3.5.11. It is rendered from HERE rather than from
     * the storage overlay, so that removing this one file removes both halves
     * of what it needs - the diagnosis rule the three files exist for.
     *
     * The cost is not zero and is not local: the flag is GLOBAL, so it also
     * lets other non-standard settings through silently anywhere in the merged
     * configuration. Acceptable in a development sandbox that is generated
     * whole on every run, and a reason not to reach for this overlay when
     * Canton's own generated token would do.
     */
    public static final String STR_KEY_NON_STANDARD = "canton.parameters.non-standard-config";

    /** Measured default. Stated so a caller can see what it is overriding. */
    public static final Duration DURATION_DEFAULT = Duration.ofMinutes(5);

    private final String strToken;
    private final Duration duration;
    private final boolean flagAdminClaim;
    private final boolean flagActAsAnyParty;


    private AdminTokenOverlay(String strToken, Duration duration, boolean flagAdminClaim,
            boolean flagActAsAnyParty) {
        this.strToken = strToken;
        this.duration = duration;
        this.flagAdminClaim = flagAdminClaim;
        this.flagActAsAnyParty = flagActAsAnyParty;
    }


    /**
     * A pinned token that carries the admin claim, which is what a console
     * needs for Ledger API administration.
     *
     * @param strToken the token, which every holder can administer with
     * @return the overlay
     */
    public static AdminTokenOverlay ofAdmin(String strToken) {
        require(strToken);
        return new AdminTokenOverlay(strToken, null, true, false);
    }


    /**
     * The same, plus act-as-any-party. Named separately rather than given as a
     * flag, so that choosing it is visible at the call site.
     *
     * @param strToken the token
     * @return the overlay
     */
    public static AdminTokenOverlay ofAdminActingAsAnyParty(String strToken) {
        require(strToken);
        return new AdminTokenOverlay(strToken, null, true, true);
    }


    /**
     * @param durationNew how long a token Canton mints itself stays valid; has
     *        no effect on a pinned one
     * @return a copy carrying it
     */
    public AdminTokenOverlay withDuration(Duration durationNew) {
        if (durationNew == null || durationNew.isZero() || durationNew.isNegative())
            throw new IllegalArgumentException("the duration must be positive");
        return new AdminTokenOverlay(strToken, durationNew, flagAdminClaim, flagActAsAnyParty);
    }


    public String strToken() {
        return strToken;
    }


    public boolean flagAdminClaim() {
        return flagAdminClaim;
    }


    public boolean flagActAsAnyParty() {
        return flagActAsAnyParty;
    }


    public String render() {
        return render(StorageOverlay.STR_NODE_PARTICIPANT);
    }


    /**
     * @param strParticipant the participant node the overlay applies to
     * @return the HOCON to hand to Canton with -c
     */
    public String render(String strParticipant) {
        if (strParticipant == null || strParticipant.isBlank())
            throw new IllegalArgumentException("a participant name is required");

        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Generated by raposza-canton: the admin token block, and the\n");
        sb.append("// flag Canton requires before it will accept one.\n");
        sb.append(STR_KEY_NON_STANDARD).append(" = yes\n");
        sb.append("canton.participants.").append(strParticipant).append(".ledger-api.")
                .append(STR_KEY_BLOCK).append(" {\n");
        sb.append("  ").append(STR_KEY_FIXED).append(" = \"").append(escape(strToken))
                .append("\"\n");
        if (duration != null) {
            sb.append("  ").append(STR_KEY_DURATION).append(" = ").append(duration.toSeconds())
                    .append("s\n");
        }
        sb.append("  ").append(STR_KEY_ADMIN_CLAIM).append(" = ").append(flagAdminClaim)
                .append("\n");
        sb.append("  ").append(STR_KEY_ACT_AS_ANY).append(" = ").append(flagActAsAnyParty)
                .append("\n");
        sb.append("}\n");
        return sb.toString();
    }


    /** Safe for a log line: what the token may do, never the token. */
    public String describe() {
        return "pinned admin token (not shown), admin-claim " + flagAdminClaim
                + ", act-as-any-party " + flagActAsAnyParty;
    }


    @Override
    public String toString() {
        return "admin token overlay: " + describe();
    }


    private static String escape(String strValue) {
        return strValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    private static void require(String strToken) {
        if (strToken == null || strToken.isBlank())
            throw new IllegalArgumentException("a token is required");
    }
}
