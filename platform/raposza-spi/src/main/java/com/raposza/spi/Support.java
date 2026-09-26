// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

/**
 * What a target claims about one capability.
 *
 * Three values, not two, and the third is the point. A boolean forces
 * "not measured yet" to be written as "no", which is a claim nobody made and
 * which reads identically to a measured refusal six months later.
 * Unmeasured is cheap to write and expensive to discover.
 *
 * Author Claude/bentzn
 */
public enum Support {

    /**
     * Measured green against a named version on a named date. The target does
     * this and a check proved it.
     */
    SUPPORTED,

    /**
     * The target refuses this, deliberately and by design, against a named
     * version on a named date. A call is expected to raise
     * UnsupportedCapability rather than fail in some other way.
     */
    UNSUPPORTED,

    /**
     * Nobody has looked. NOT a refusal. A caller may proceed; it simply has no
     * standing to blame the target when the call misbehaves, and has every
     * reason to report the outcome back into the compatibility matrix.
     */
    UNMEASURED

}
