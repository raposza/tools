// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import java.time.Duration;

/**
 * What a run needs that the script does not say.
 *
 * @param idApplication WHO IS SUBMITTING, and on v2 that is a participant
 *                      user rather than a label. Canton refuses to default it
 *                      when the token does not carry one, and the refusal
 *                      arrives as INVALID_ARGUMENT on the submit rather than on
 *                      the token - which reads like a bad command.
 *
 *                      v2 renamed `application_id` to `user_id` and changed the
 *                      MEANING with it: a name no user answers to is refused
 *                      PERMISSION_DENIED, redacted, naming neither the claim nor
 *                      the party. Measured on 3.5.12, 2026-08-25, where a
 *                      hard-coded `workbench-caql` was refused identically for
 *                      two different users. A read is unaffected because an
 *                      active-contract request carries no user id at all, which
 *                      is what made this look like a rights problem
 * @param timeout how long to wait for each completion. An expiry is
 *                OUTCOME_UNKNOWN, not a failure
 * @param flagValidateOnly resolve and coerce every statement, submit nothing.
 *                         A run mode, never a statement - sec. 4
 *
 * Author Claude/bentzn
 */
public record RunConfig(String idApplication, Duration timeout, boolean flagValidateOnly) {

    /**
     * What a session that does not know its user reports.
     *
     * KEPT, and it is a fallback rather than a default anybody should reach
     * for: a 2.x participant takes any application id and an unauthenticated
     * one takes any at all. Against an authenticated 3.x participant it is
     * refused, which is correct - the caller has to say who is submitting.
     */
    public static final String ID_UNNAMED = "workbench-caql";


    /** @return a run that submits, with a 30 s completion wait */
    public static RunConfig ofRun() {
        return ofRun(null);
    }


    /**
     * @param idUser the participant user submitting, null when the session
     *        names none
     * @return a run that submits, with a 30 s completion wait
     */
    public static RunConfig ofRun(String idUser) {
        return new RunConfig(idOr(idUser), Duration.ofSeconds(30), false);
    }


    /** @return a run that sends nothing */
    public static RunConfig ofValidate() {
        return ofValidate(null);
    }


    /**
     * @param idUser the participant user the run would submit as
     * @return a run that sends nothing
     */
    public static RunConfig ofValidate(String idUser) {
        return new RunConfig(idOr(idUser), Duration.ofSeconds(30), true);
    }


    private static String idOr(String idUser) {
        return idUser == null || idUser.isBlank() ? ID_UNNAMED : idUser.trim();
    }

}
