// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.runtime.lifecycle.StackService_i;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * `todo.md` A-34 (d): a runner that has been stopped starts nothing.
 *
 * The window cancels a start by stopping the runner from another thread while
 * the start thread is still inside it. Before 2026-09-23 the start thread went
 * on to create PostgreSQL, Canton and Splice after the stop had run, and those
 * were held by nobody. Stopping BEFORE start() is the same race with the
 * timing removed, and it is what this drives.
 *
 * THE CONTROL CAN FAIL. The image directory holds no Canton jar, so a runner
 * that ignored its own stop would reach the jar name first and fail on that
 * instead - a different message, and a run directory the refusal below never
 * creates.
 *
 * Author Claude/bentzn
 */
class LocalNetRunnerStopTest {

    @Test
    void a_stopped_runner_starts_nothing(@TempDir Path dirTmp) {
        Path dirRun = dirTmp.resolve("run");
        LocalNetRunner runner = new LocalNetRunner(dirTmp.resolve("bundle"), dirRun,
                new CantonImage(dirTmp.resolve("image")), false, true, false);

        runner.stop();
        assertEquals(StackService_i.State.STOPPED, runner.state());

        StackService_i.StartException ex = assertThrows(StackService_i.StartException.class,
                runner::start);
        assertTrue(ex.getMessage().contains("stopped while it was starting"), ex.getMessage());
        assertFalse(Files.exists(dirRun), "the refused start created " + dirRun);
    }


    /** A second stop() leaves the runner STOPPED rather than stopping for good. */
    @Test
    void a_second_stop_leaves_the_state_stopped(@TempDir Path dirTmp) {
        LocalNetRunner runner = new LocalNetRunner(dirTmp.resolve("bundle"),
                dirTmp.resolve("run"), new CantonImage(dirTmp.resolve("image")), false, true,
                false);

        runner.stop();
        runner.stop();
        assertEquals(StackService_i.State.STOPPED, runner.state());
    }


    /**
     * A STOPPED RUNNER'S LAMPS ARE OFF - "Lamps are wrong", 2026-09-23. This
     * runner never started, so it asserts the rule rather than reproducing
     * his case, where the components had been created before the stop.
     */
    @Test
    void a_stopped_runner_reports_every_component_off(@TempDir Path dirTmp) {
        LocalNetRunner runner = new LocalNetRunner(dirTmp.resolve("bundle"),
                dirTmp.resolve("run"), new CantonImage(dirTmp.resolve("image")), false, true,
                false);
        runner.stop();
        for (String strComp : List.of(LocalNetRunner.STR_COMP_POSTGRES,
                LocalNetRunner.STR_NS_SPLICE, LocalNetRunner.STR_COMP_WEB)) {
            assertEquals(StackService_i.Health.OFF, runner.healthOf(strComp), strComp);
        }
    }


    /** The window reads the numbering off the runner while it is up. */
    @Test
    void the_runner_answers_for_the_numbering_it_was_given(@TempDir Path dirTmp) {
        LocalNetPorts ports = LocalNetPorts.ofFirst(30400, 31500, 32400);
        LocalNetRunner runner = new LocalNetRunner(dirTmp.resolve("bundle"),
                dirTmp.resolve("run"), new CantonImage(dirTmp.resolve("image")), false, true,
                false, ports);
        assertEquals(ports, runner.ports());
    }
}
