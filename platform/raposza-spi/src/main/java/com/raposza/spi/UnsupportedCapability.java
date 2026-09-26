// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

/**
 * A target correctly refused something it declares it cannot do.
 *
 * This is a DIFFERENT TYPE from LedgerException, and the separation is the
 * whole reason the declaration exists. LedgerException means the ledger, the
 * transport or the target is broken. This means the target is working exactly
 * as declared and the caller asked for something outside the declaration.
 *
 * A caller that catches both and reports them the same way has thrown away the
 * distinction this class was created to carry.
 *
 * Author Claude/bentzn
 */
public class UnsupportedCapability extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Capability capability;

    private final String idTarget;


    /**
     * @param capability what was asked for
     * @param idTarget the target that refused
     * @param strNote the declared reason, as recorded in the report
     */
    public UnsupportedCapability(Capability capability, String idTarget, String strNote) {
        super(idTarget + " does not support " + capability.strId()
                + (strNote == null || strNote.isBlank() ? "" : " - " + strNote));
        this.capability = capability;
        this.idTarget = idTarget;
    }


    /** @return the capability that was refused */
    public Capability getCapability() {
        return capability;
    }


    /** @return the id of the target that refused */
    public String getIdTarget() {
        return idTarget;
    }

}
