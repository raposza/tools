// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.sandbox.app.SandboxService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The control table, with no display.
 *
 * This is what the extraction bought. Before it, "is Stop pressable while a
 * start is running" could only be answered by opening a window and pressing
 * Start.
 *
 * Author Claude/bentzn
 */
class LifecycleControlsTest {

    @ParameterizedTest
    @EnumSource(SandboxService.State.class)
    void everyStateHasAnAnswer(SandboxService.State state) {
        LifecycleControls.of(state);
    }


    @Test
    void aNullStateIsRefusedRatherThanDefaulted() {
        assertThrows(IllegalArgumentException.class, () -> LifecycleControls.of(null));
    }


    /**
     * A start is 20 to 180 seconds; the only way out of one used to be closing
     * the window.
     */
    @Test
    void stopIsLiveWhileStarting() {
        assertTrue(LifecycleControls.of(SandboxService.State.STARTING).flagStop());
    }


    @Test
    void startIsRefusedUnlessNothingIsUp() {
        assertFalse(LifecycleControls.of(SandboxService.State.STARTING).flagStart());
        assertFalse(LifecycleControls.of(SandboxService.State.RUNNING).flagStart());
        assertFalse(LifecycleControls.of(SandboxService.State.STOPPING).flagStart());
        assertTrue(LifecycleControls.of(SandboxService.State.STOPPED).flagStart());
        assertTrue(LifecycleControls.of(SandboxService.State.FAILED).flagStart());
    }


    /** Reset is a stop and a start, so there has to be something to stop. */
    @Test
    void resetIsLiveOnlyWhileRunning() {
        for (SandboxService.State state : SandboxService.State.values()) {
            if (state == SandboxService.State.RUNNING)
                assertTrue(LifecycleControls.of(state).flagReset());
            else
                assertFalse(LifecycleControls.of(state).flagReset(), state.name());
        }
    }


    /** A save stops the stack to take it, so there has to be one up. */
    @Test
    void saveIsOfferedOnlyWhileRunning() {
        for (SandboxService.State state : SandboxService.State.values()) {
            if (state == SandboxService.State.RUNNING)
                assertTrue(LifecycleControls.of(state).flagSave());
            else
                assertFalse(LifecycleControls.of(state).flagSave(), state.name());
        }
    }


    /**
     * The selection is read when Start is pressed, so a list that could be
     * clicked mid-start was saying something untrue.
     */
    @Test
    void theSnapshotListIsClosedOnceAStartHasBegun() {
        assertFalse(LifecycleControls.of(SandboxService.State.STARTING).flagSnapshots());
        assertFalse(LifecycleControls.of(SandboxService.State.RUNNING).flagSnapshots());
        assertFalse(LifecycleControls.of(SandboxService.State.STOPPING).flagSnapshots());
    }


    /**
     * A failed start is where somebody reaches for a different snapshot.
     */
    @Test
    void aFailedStartLeavesTheSnapshotListOpen() {
        assertTrue(LifecycleControls.of(SandboxService.State.FAILED).flagSnapshots());
        assertTrue(LifecycleControls.of(SandboxService.State.STOPPED).flagSnapshots());
    }


    /**
     * A field editable under a running participant disagrees with what is
     * listening.
     */
    @Test
    void theFormIsClosedWheneverAnythingIsUpOrComingUp() {
        assertFalse(LifecycleControls.of(SandboxService.State.STARTING).flagInput());
        assertFalse(LifecycleControls.of(SandboxService.State.RUNNING).flagInput());
        assertFalse(LifecycleControls.of(SandboxService.State.STOPPING).flagInput());
        assertTrue(LifecycleControls.of(SandboxService.State.STOPPED).flagInput());
        assertTrue(LifecycleControls.of(SandboxService.State.FAILED).flagInput());
    }


    /** Nothing is pressable while a stop is in flight. */
    @Test
    void stoppingOffersNothing() {
        LifecycleControls controls = LifecycleControls.of(SandboxService.State.STOPPING);

        assertFalse(controls.flagStart());
        assertFalse(controls.flagStop());
        assertFalse(controls.flagReset());
        assertFalse(controls.flagSave());
        assertFalse(controls.flagSnapshots());
        assertFalse(controls.flagInput());
    }

}
