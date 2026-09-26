// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

/**
 * The heading every section of the result pane wears.
 *
 * A rule above and below, because the pane is one scrolling column and a bare
 * word is not a boundary: with a contract, its activity and whatever was read
 * before it stacked in the same text area, the eye needs something that is
 * plainly not content to find where one answer ends and the next begins.
 *
 * ONE WIDTH, and it is fixed rather than fitted to the label. A rule as long as
 * its heading is a heading with a squiggle under it; a rule of constant length
 * is a horizon, and the pane reads as a stack of sections rather than as a page
 * of text with words in capitals scattered through it.
 *
 * Author Claude/bentzn
 */
public final class Banner {

    /** How wide the rule is, in characters. */
    public static final int CNT_WIDE = 49;

    private static final String STR_RULE = "-".repeat(CNT_WIDE);


    private Banner() {
    }


    /**
     * @param strLabel what the section is
     * @return the heading, ending in a newline so the body follows directly
     */
    public static String head(String strLabel) {
        return STR_RULE + '\n' + strLabel + '\n' + STR_RULE + '\n';
    }

}
