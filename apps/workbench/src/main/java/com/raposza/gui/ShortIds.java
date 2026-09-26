// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

/**
 * Ids, shortened for reading, on their way to a pane.
 *
 * <h2>Two forms, because the two id kinds carry different information</h2>
 *
 * A CONTRACT ID carries no namespace and no participant - it is a version
 * byte, a discriminator and a suffix - so nothing is lost by keeping the ends
 * and dropping the middle. Head and tail are both kept: contract ids that
 * differ only in their last characters exist in this workspace, and a
 * head-only abbreviation renders those as one row, which is a wrong answer
 * rather than a terse one.
 *
 * A PARTY ID is `hint::fingerprint`, and the fingerprint is the only signal of
 * WHERE the party lives. So the hint is kept whole and the fingerprint keeps
 * its TAIL: every fingerprint starts with the same multihash header, so a
 * prefix of one is a prefix of all of them and says nothing.
 *
 * <h2>Why this reads the finished text rather than the model</h2>
 *
 * Every pane already produces its text through a renderer that must stay
 * exact - JsonRenderer's output is the thing that pastes back into a script.
 * Shortening here, on the way to the screen, leaves those renderers alone and
 * covers every pane at once, including ones not written yet. The failure mode
 * is that something id-shaped that was not an id gets shortened; it is never
 * that a renderer emits the wrong bytes.
 *
 * The marker is `[...]` and not an ellipsis on purpose: `[` is not a hex digit
 * and not legal in a party id, so anything receiving one of these can DETECT
 * that it was abbreviated instead of querying a participant with it.
 *
 * Author Claude/bentzn
 */
public final class ShortIds {

    /** What stands in for the dropped middle. */
    public static final String STR_CUT = "\u00a4\u00a4\u00a4";

    /**
     * How long a bare hex run must be before it is treated as an id.
     *
     * The same threshold ContractIdProbe accepts on, so what the resolver
     * calls an id and what this shortens are the same set.
     */
    static final int CNT_HEX_MIN = 40;

    /** How long the run after `::` must be before it is treated as a fingerprint. */
    static final int CNT_NAMESPACE_MIN = 16;

    private static final int CNT_HEAD = 4;

    private static final int CNT_TAIL = 4;

    /**
     * How many characters a shortened bare id occupies.
     *
     * Published because the navigator sizes its column from it. A pane that
     * guessed a width would go on guessing after CNT_HEAD changed, and the
     * symptom - an id clipped by four characters - looks like a layout bug
     * rather than a stale constant.
     */
    public static final int CNT_ID_SHOWN = CNT_HEAD + STR_CUT.length() + CNT_TAIL;

    /**
     * More tail on a fingerprint than on a contract id, because this one is
     * COMPARED: it is how an operator sees that two parties live in the same
     * place, or that one does not.
     */
    private static final int CNT_TAIL_NAMESPACE = 6;


    private ShortIds() {
    }


    /**
     * @param strText any rendered block, may be null
     * @return it with every id-shaped run shortened, everything else untouched
     */
    public static String text(String strText) {
        if (strText == null || strText.isEmpty())
            return strText;

        StringBuilder buf = new StringBuilder(strText.length());
        int idxAt = 0;
        while (idxAt < strText.length()) {
            if (!isHex(strText.charAt(idxAt))) {
                buf.append(strText.charAt(idxAt));
                idxAt++;
                continue;
            }

            int idxEnd = idxAt;
            while (idxEnd < strText.length() && isHex(strText.charAt(idxEnd))) {
                idxEnd++;
            }
            buf.append(strRun(strText.substring(idxAt, idxEnd), isAfterSeparator(strText, idxAt)));
            idxAt = idxEnd;
        }
        return buf.toString();
    }


    /**
     * @param strId one id on its own, may be null
     * @return it, shortened
     */
    public static String id(String strId) {
        return text(strId);
    }


    /**
     * @param strText the block
     * @param idxStart where the hex run begins
     * @return true when `::` sits immediately before it, which is what makes
     *         the run a namespace fingerprint rather than a bare id
     */
    private static boolean isAfterSeparator(String strText, int idxStart) {
        return idxStart >= 2 && strText.charAt(idxStart - 1) == ':'
                && strText.charAt(idxStart - 2) == ':';
    }


    /**
     * A run SHORTER than its threshold is returned whole. That is what keeps
     * ordinary words made of hex letters, and the eight-character salt inside
     * a party hint, out of this.
     *
     * @param strRun a maximal run of hex digits
     * @param flagNamespace whether it followed `::`
     * @return the run, shortened when it is long enough to be an id
     */
    private static String strRun(String strRun, boolean flagNamespace) {
        if (flagNamespace) {
            return strRun.length() < CNT_NAMESPACE_MIN ? strRun
                    : STR_CUT + strRun.substring(strRun.length() - CNT_TAIL_NAMESPACE);
        }
        if (strRun.length() < CNT_HEX_MIN)
            return strRun;
        return strRun.substring(0, CNT_HEAD) + STR_CUT
                + strRun.substring(strRun.length() - CNT_TAIL);
    }


    private static boolean isHex(char chAt) {
        return (chAt >= '0' && chAt <= '9') || (chAt >= 'a' && chAt <= 'f')
                || (chAt >= 'A' && chAt <= 'F');
    }

}
