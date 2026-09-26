// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.TokenSource_i;

import java.util.List;

/**
 * Where a credential for a NAMED USER comes from.
 *
 * <h2>Why this is an interface and not a method on the window</h2>
 *
 * The Workbench works AS one user at a time, and the ways of getting
 * that user's token have nothing in common with each other:
 *
 * <ul>
 * <li>a local Sandbox publishes a mint that will issue a token for any user
 *     asked for - {@link MintTokens};</li>
 * <li>a participant that asks for no credential lets the window work as any
 *     user it hosts, with no token at all - {@link OpenUsers};</li>
 * <li>an operator with one JWT for one user can only ever be that user -
 *     {@link FixedToken};</li>
 * <li>a real OIDC provider, which is not built and is the reason this seam
 *     exists at all. It will need a client id, a grant and a redirect, and
 *     none of that belongs in a window.</li>
 * </ul>
 *
 * The window asks two questions - which users can I be, and give me a token
 * for this one - and does not care which of the three answered.
 *
 * Author Claude/bentzn
 */
public interface UserTokens_i {

    /**
     * @return whether the caller may pick a user; false when the credential
     *         decides and the choice is not the window's to offer
     */
    boolean canChoose();


    /**
     * @return the users this can issue for. On a fixed credential it is the
     *         one user that credential names, so a caller always has something
     *         to display
     */
    List<String> lstUser();


    /**
     * @param idUser one of {@link #lstUser}
     * @return the credential to present when working as that user
     * @throws com.raposza.api.LedgerException when no token can be got
     */
    TokenSource_i sourceFor(String idUser);


    /**
     * @return where the tokens come from, for the status bar; never key
     *         material
     */
    String describe();

}
