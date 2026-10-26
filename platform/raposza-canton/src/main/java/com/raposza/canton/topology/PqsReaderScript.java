// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import java.util.regex.Pattern;

/**
 * The user scribe reads as, made on the participant if it is not there yet -
 * raposza/tools#4.
 *
 * <h2>Why scribe needs a user of its own</h2>
 *
 * `--pipeline-filter-parties=*` resolves against the parties the token's user
 * may read. `participant_admin` may administer the participant and read no
 * party, so an authenticated scribe logged `0 parties can actAs/readAs` and
 * retried for ever on `No parties found matching '*'` - measured at his
 * console 2026-10-26, and recorded as D-439 long before. `readAsAnyParty` is
 * the right that covers `*`, and it is a right on an ordinary user.
 *
 * <h2>Fetched, or created - and both calls are measured</h2>
 *
 * The same guard {@link Canton3xDaemonBootstrap} already renders for a named
 * user: `users.get(id = ...)` fails with `CommandFailure` when there is no such
 * user, and a second `users.create` of one id throws the same - both measured
 * on 3.5.11. A stack started on a snapshot or on
 * the founding snapshot already holds the user, and a start that created it
 * again would fail.
 *
 * WHAT IS CHECKED IS THE ID. The user is one only this application creates,
 * under a name of its own, so a user of that name is taken to be this one; its
 * rights are not read back.
 *
 * Author Claude/bentzn
 */
final class PqsReaderScript {

    /** What may go between the quotes of a Scala string literal unescaped. */
    private static final Pattern PAT_USER_ID = Pattern.compile("[A-Za-z0-9._@:-]{1,128}");


    private PqsReaderScript() {
    }


    /**
     * @param strUserId a candidate user id
     * @return it
     * @throws IllegalArgumentException when it is blank or carries anything a
     *         Scala literal would have to escape
     */
    static String strRequired(String strUserId) {
        if (strUserId == null || !PAT_USER_ID.matcher(strUserId).matches()) {
            throw new IllegalArgumentException("not a usable PQS user id: " + strUserId);
        }
        return strUserId;
    }


    /**
     * @param sb the script being rendered
     * @param strParticipant the participant node name
     * @param strUserId the user scribe's token names
     */
    static void append(StringBuilder sb, String strParticipant, String strUserId) {
        sb.append("val _pqsReader = try {\n");
        sb.append("  ").append(strParticipant).append(".ledger_api.users.get(id = \"")
                .append(strUserId).append("\")\n");
        sb.append("}\n");
        sb.append("catch {\n");
        sb.append("  case _: com.digitalasset.canton.console.CommandFailure =>\n");
        sb.append("    ").append(strParticipant).append(".ledger_api.users.create(\n");
        sb.append("      id = \"").append(strUserId).append("\",\n");
        sb.append("      actAs = Set.empty,\n");
        sb.append("      readAs = Set.empty,\n");
        sb.append("      readAsAnyParty = true)\n");
        sb.append("}\n");
    }
}
