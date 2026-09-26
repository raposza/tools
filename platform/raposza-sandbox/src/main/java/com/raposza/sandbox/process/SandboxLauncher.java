// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.process;

/**
 * Which of the two 3.x launchers a stack runs on.
 *
 * They are not interchangeable and the difference is not a preference. The
 * subcommand supplies its own topology, its own ports as flags and its own
 * generated bootstrap - and that bootstrap re-proposes a synchronizer trust
 * certificate on every start, which is why a stack started on it a second time
 * against the same storage dies. `daemon` supplies none of those three and
 * runs the script it is handed, which is what makes a restart possible and
 * what makes every one of them this project's own work.
 *
 * The default is the subcommand, and not because the other is unmeasured:
 * `daemon` carries auth and a pinned admin token and uploads DARs from its
 * bootstrap, both live-verified on 3.5.11. It is the default because it is the
 * one the headless application and every 2.x-shaped assumption were built
 * against.
 *
 * Author Claude/bentzn
 */
public enum SandboxLauncher {

    /** `java -jar canton.jar sandbox`. Single-use against a storage. */
    SUBCOMMAND,

    /** `java -jar canton.jar daemon -c ... --bootstrap ...`. Restartable. */
    DAEMON
}
