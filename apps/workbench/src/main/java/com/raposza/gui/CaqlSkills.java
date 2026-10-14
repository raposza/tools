// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.caql.CaqlReference;

/**
 * The whole of CaQL on one page.
 *
 * <h2>Why it is in the application rather than in a file beside it</h2>
 *
 * A reference that ships separately is a reference that goes stale silently:
 * the language moves, the page does not, and nobody finds out until a
 * statement written from it is refused. Here it is compiled into the same jar
 * as the parser it describes.
 *
 * <h2>Written to be pasted</h2>
 *
 * Plain text, no markup, one topic per block, every form shown as it is
 * typed. That is what a person skims and what is pasted elsewhere; two pages
 * would disagree.
 *
 * <h2>The page itself is in caql-core since 2026-10-07</h2>
 *
 * {@link CaqlReference} - his decision, so the page sits beside the parser it
 * describes. This stays as the Workbench's name for it.
 *
 * Author Claude/bentzn
 */
public final class CaqlSkills {

    private CaqlSkills() {
    }


    /** @return the reference, ready for a text area or a clipboard */
    public static String text() {
        return CaqlReference.text();
    }

}
