// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

/**
 * Told about each statement of a run as it starts and as it ends - his
 * instruction, 2026-10-04: "When executing CaQL with 'Run' show progress line
 * by line."
 *
 * A run of the USDCx fixture is 122 statements and a minute of submissions,
 * and the transcript arrives only when the run is over. This is how a caller
 * shows the run while it happens. Called on the thread that runs the script,
 * so a window hops to its own thread before it touches a component.
 *
 * Only statements that are RUN are reported: a run stops at the first one that
 * does not let it continue, and the statements after it are neither started
 * nor finished.
 *
 * Author Claude/bentzn
 */
public interface RunProgress_i {

    /**
     * @param numStmt one-based position of the statement in the script
     * @param cntStmt how many statements the script holds
     * @param stmt the statement about to run
     */
    void started(int numStmt, int cntStmt, Stmt stmt);


    /**
     * @param numStmt one-based position of the statement in the script
     * @param cntStmt how many statements the script holds
     * @param entry what it did - the same entry the transcript will carry
     */
    void finished(int numStmt, int cntStmt, Entry entry);

}
