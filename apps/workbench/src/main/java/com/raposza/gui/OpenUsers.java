// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.TokenSource_i;
import com.raposza.api.model.UserInfo;

import java.util.List;

/**
 * The users of a participant that asks for no credential.
 *
 * <h2>The choice is real even though the token is not</h2>
 *
 * A wildcard participant grants everything to everybody, so a token here
 * changes nothing - and the window's reads were never driven by one. They are
 * driven by the parties of the selected user, which come from the participant's
 * own user rights. So `work as` is a client-side act-as on this kind of stack:
 * it narrows what is read to what that user would be allowed to read, and asks
 * the participant for no permission to do it.
 *
 * <h2>Every entry is a user</h2>
 *
 * A `*` entry meaning WHATEVER USER IS NECESSARY led this list until
 * 2026-09-09 and is gone - operator instruction. It was not a name the
 * participant answered to, so every caller that had to NAME a user carried a
 * branch for it, and a submission made under it went out with no `user_id` at
 * all and was refused PERMISSION_DENIED with the reason redacted. A
 * participant reporting no users now offers no user, and the window reads as
 * every party it hosts.
 *
 * Author Claude/bentzn
 */
public final class OpenUsers implements UserTokens_i {

    private final transient List<String> lstUser;


    /**
     * @param lstUserNew the users the participant reported
     */
    public OpenUsers(List<UserInfo> lstUserNew) {
        this.lstUser = List.copyOf(MintTokens.lstIdOf(lstUserNew));
    }


    @Override
    public boolean canChoose() {
        return true;
    }


    @Override
    public List<String> lstUser() {
        return lstUser;
    }


    /**
     * @param idUser who the caller wants to be, ignored
     * @return null - the participant asked for nothing and is given nothing
     */
    @Override
    public TokenSource_i sourceFor(String idUser) {
        return null;
    }


    @Override
    public String describe() {
        return "none - the participant is unauthenticated";
    }

}
