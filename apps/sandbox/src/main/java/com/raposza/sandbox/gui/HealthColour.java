// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.lifecycle.StackService_i;

import java.awt.Color;

/**
 * What a health reads as, in one place.
 *
 * `LampBar` held this privately and it was right to until a second bar needed
 * the same answer. A colour that meant UP on one bar and something else on
 * another is the kind of disagreement nobody reports, because both bars look
 * deliberate.
 *
 * Author Claude/bentzn
 */
final class HealthColour {

    private HealthColour() {
    }


    /**
     * @param health what the component is doing
     * @return the colour its lamp takes
     */
    static Color of(StackService_i.Health health) {
        switch (health) {
            case UP:
                return GuiTheme.COL_OK;
            case STARTING:
                return GuiTheme.COL_BUSY;
            case PENDING:
                return GuiTheme.COL_PENDING;
            case DOWN:
                return GuiTheme.COL_BAD;
            case OFF:
            default:
                return GuiTheme.COL_LAMP_OFF;
        }
    }

}
