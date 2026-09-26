// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.render;

import com.raposza.api.model.Command;
import com.raposza.api.model.SubmitContext;

/**
 * Emits a reproducible invocation for a command the GUI is about to submit, or
 * has just submitted.
 *
 * This is how the tool stays honest about what it does: every GUI action can be
 * copied out as a grpcurl call, a console command, a JSON API request or a Daml
 * Script snippet, and graduated into a script.
 *
 * Author Claude/bentzn
 */
public interface CommandEmitter_i {

    /**
     * @return short name for the UI tab, e.g. "grpcurl", "Daml Script"
     */
    String name();


    /**
     * @param cmd the command
     * @param ctx submitting identity
     * @return the invocation, ready to copy
     */
    String emit(Command cmd, SubmitContext ctx);

}
