// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.lifecycle;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Starting and stopping ONE stack, whatever that stack is made of.
 *
 * <h2>Why this type exists</h2>
 *
 * The Sandbox window drives a single participant and LocalNetND drives three,
 * and the window must be able to hold either without knowing which. Everything
 * a lifecycle needs - the two verbs, the state, the two output sinks and the
 * per-component health - is the same on both sides; everything that is not is
 * deliberately absent, because a method a LocalNet stack cannot answer would
 * have to return something, and that something would look like an answer.
 *
 * <h2>Why the enums live HERE and not in the implementation</h2>
 *
 * They were nested in `SandboxService` and are moved here whole. A second copy
 * beside this one would drift on the first value either side gained, and the
 * drift would not show until a live run. Java inherits an interface's member
 * types, so `SandboxService.State` still resolves for every caller that
 * already spells it that way and nothing outside this file had to change.
 *
 * <h2>The report is NOT here</h2>
 *
 * `ReadyReport` describes one participant and one database, which a LocalNet
 * stack has no referent for. What generalises is the discovery document's
 * node list, and that producer still sits in `apps/sandbox`; until it moves
 * down into this module a report method here could only be the Sandbox's.
 *
 * <h2>Threading</h2>
 *
 * {@link #start()} and {@link #stop()} BLOCK and are not safe on an event
 * dispatch thread. Both sinks are called from whichever thread is inside them,
 * so a GUI caller hops to the EDT itself - the headless caller must not pay
 * for it.
 *
 * Author Claude/bentzn
 */
public interface StackService_i {

    /** What the stack is doing, as far as this process knows. */
    enum State {

        /** Nothing has been started, or everything has been stopped again. */
        STOPPED,

        /** {@link StackService_i#start()} is running. */
        STARTING,

        /** Serving. The status file is on disk. */
        RUNNING,

        /** {@link StackService_i#stop()} is running. */
        STOPPING,

        /** A start failed. Nothing is left running; see the last line printed. */
        FAILED
    }


    /** What one component of the stack is doing, as far as this knows. */
    enum Health {

        /** Not part of this stack at all, or nothing has been started yet. */
        OFF,

        /**
         * The stack is starting, this component IS expected to come up, and
         * its own start has not begun.
         *
         * A DIFFERENT STATEMENT FROM OFF, which is what this used to report:
         * OFF means nothing here will ever start the component, and a JSON API
         * waiting for the participant it fronts is not that.
         */
        PENDING,

        /** The stack is starting and this component is not up yet. */
        STARTING,

        /** Running. */
        UP,

        /** It is supposed to be running and it is not. */
        DOWN
    }


    /**
     * A refused or failed start, carrying the exit code the headless entry
     * point returns for it.
     */
    final class StartException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int nExit;


        /**
         * @param nExitNew the headless entry point's exit code for this failure
         * @param strMessage what to print, already a whole sentence
         */
        public StartException(int nExitNew, String strMessage) {
            super(strMessage);
            this.nExit = nExitNew;
        }


        /**
         * @return the process exit code for this failure
         */
        public int nExit() {
            return nExit;
        }
    }


    /**
     * Brings the stack up and returns once it serves. Blocks.
     *
     * @throws StartException when anything refuses or fails
     */
    void start();


    /**
     * Stops whatever is up. Safe to call when nothing was ever started, which
     * is what a shutdown hook and a window close both need.
     */
    void stop();


    /**
     * @return what the stack is doing
     */
    State state();


    /**
     * @return whether the stack is serving
     */
    boolean isRunning();


    /**
     * @param sinkNew called with a component name - one of the strings
     *        {@link #lstComponent()} returns - and one line, from a process
     *        pump thread
     */
    void useComponentSink(BiConsumer<String, String> sinkNew);


    /**
     * @param outLineNew where the milestones go, or null to discard them
     */
    void useMilestoneSink(Consumer<String> outLineNew);


    /**
     * The components this stack can have, in the order a lamp bar shows them.
     *
     * DECLARED BY THE STACK rather than by the window, which is the whole
     * point of the type: a Sandbox names four and a LocalNet stack names what
     * it has. A component that is listed but not part of THIS configuration
     * reports {@link Health#OFF} and is not removed from the list, so the row
     * does not move when the options change.
     *
     * @return the component names, never null and never empty
     */
    List<String> lstComponent();


    /**
     * @param strComponent one of the names {@link #lstComponent()} returns
     * @return that component's health, or {@link Health#OFF} for a name this
     *         stack does not know
     */
    Health healthOf(String strComponent);


    /**
     * One addressable node of the stack, as an outside consumer reaches it.
     *
     * THE LIST IS WHAT GENERALISES, and it is the shape this type's own
     * comment already named: a Sandbox answers ONE node and LocalNetND answers
     * three, so a surface that wants a Ledger API port asks the list rather
     * than a method that could only ever be the Sandbox's.
     *
     * A port the node does not serve is 0, which is not a port, rather than -1
     * or null - a caller testing `> 0` then runs the same test on every field.
     *
     * @param strRole what the node is called in its own topology - `participant`
     *        on a Sandbox, `sv`, `app-provider` or `app-user` on LocalNetND
     * @param strHost the host every port below is on
     * @param nPortLedger the gRPC Ledger API port, or 0
     * @param nPortAdmin the admin API port, or 0
     * @param nPortJson the JSON Ledger API port, or 0
     */
    record StackNode(String strRole, String strHost, int nPortLedger, int nPortAdmin,
            int nPortJson) {
    }


    /**
     * EVERYTHING NEEDED TO REACH THE STACK, as data rather than as print.
     *
     * The window renders these as the status table and a terminal prints them;
     * neither is a second source of truth, because both read this.
     *
     * @return key to value, in the order a reader wants them; empty before a
     *         start
     */
    Map<String, String> mapReach();


    /**
     * The same facts as {@link #mapReach()} as the block a terminal prints.
     *
     * @return one line per row, the first naming what is up; empty before a
     *         start
     */
    List<String> lstReachLine();


    /**
     * @return the properties file written while the stack runs and removed
     *         when it stops, or null when there is none on disk
     */
    Path fileStatus();


    /**
     * @param strComponent one of the names {@link #lstComponent()} returns
     * @return where that component writes its log, or null when it owns no
     *         file or nothing is up
     */
    Path fileLogOf(String strComponent);


    /**
     * WHAT IT WAS STARTED WITH, which is what a lamp click shows.
     *
     * @param strComponent one of the names {@link #lstComponent()} returns
     * @return the command line as it was handed to the operating system;
     *         empty when this process did not start that component
     */
    List<String> lstCommandOf(String strComponent);


    /**
     * @param strComponent one of the names {@link #lstComponent()} returns
     * @return the configuration files that component was started on, in the
     *         order they were passed; empty when it takes none
     */
    List<Path> lstFileConfOf(String strComponent);


    /**
     * The nodes an outside consumer can speak to.
     *
     * @return one entry per node; empty when the stack cannot name them yet
     */
    List<StackNode> lstNode();

}
