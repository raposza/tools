// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.SandboxService;

/**
 * Which controls a lifecycle state allows.
 *
 * WHAT THIS FIXES. The window had seven places that turned buttons on and off
 * - one per transition - and between them they did not agree. The enabled state
 * of the snapshot list and of Save was PATH-DEPENDENT: it was whatever the
 * previous transition happened to leave behind, because no transition set every
 * control. That is the defect a state machine spread across seven methods
 * always has, and the only reliable repair is to make the answer a function of
 * the state rather than of the route taken to it.
 *
 * TWO CONTROLS NARROWED as a consequence, both deliberately. The snapshot list
 * is now DISABLED while a start runs: the selection is read when Start is
 * pressed, so changing it mid-start already did nothing and the enabled control
 * said otherwise. And Save is DISABLED while a stop runs: a save stops the
 * stack first, so pressing it during a stop asked for a second one.
 *
 * NO SWING HERE. The window applies these; this decides them, and that is what
 * makes the table testable without a display.
 *
 * @param flagStart whether Start is pressable
 * @param flagStop whether Stop is pressable
 * @param flagReset whether Reset is pressable
 * @param flagSave whether Save snapshot is pressable
 * @param flagSnapshots whether the snapshot list may be clicked
 * @param flagInput whether the form and the DAR store accept edits
 *
 * Author Claude/bentzn
 */
record LifecycleControls(boolean flagStart, boolean flagStop, boolean flagReset,
        boolean flagSave, boolean flagSnapshots, boolean flagInput) {

    /**
     * THE FORM IS DISABLED WHENEVER ANYTHING IS UP OR COMING UP. A field that
     * can be edited under a running participant is a field that disagrees with
     * what is listening, and the table beside it would be the only thing
     * saying so.
     *
     * STOP IS LIVE WHILE STARTING. A start is 20 to 180 seconds and the only
     * way out of it used to be closing the window, which takes the stack with
     * it in a way nothing narrates.
     *
     * RESET IS LIVE ONLY WHILE RUNNING. It is a stop and a start, so there has
     * to be something to stop.
     *
     * FAILED LEAVES THE SNAPSHOT LIST OPEN. A failed start is where somebody
     * reaches for a different snapshot, and a state nobody can act from is a
     * state they have to close the window to leave.
     *
     * @param state the state the window has reached
     * @return what its controls should be
     */
    static LifecycleControls of(SandboxService.State state) {
        if (state == null)
            throw new IllegalArgumentException("a state is required");

        switch (state) {
            case STARTING:
                return new LifecycleControls(false, true, false, false, false, false);
            case RUNNING:
                return new LifecycleControls(false, true, true, true, false, false);
            case STOPPING:
                return new LifecycleControls(false, false, false, false, false, false);
            case FAILED:
                return new LifecycleControls(true, false, false, false, true, true);
            case STOPPED:
            default:
                return new LifecycleControls(true, false, false, false, true, true);
        }
    }

}
