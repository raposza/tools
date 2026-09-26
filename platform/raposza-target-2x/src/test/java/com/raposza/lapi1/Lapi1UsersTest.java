// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.UserInfo;

import com.daml.ledger.api.v1.admin.UserManagementServiceOuterClass;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Rights-to-UserInfo mapping, with no participant involved.
 *
 * This is where the mapping is actually pinned. The live test proves the two
 * calls reach the participant and come back; it does NOT prove a CanActAs
 * lands in lstPartyAct rather than lstPartyRead, because a sandbox will not
 * hand out a controlled mix of rights on demand. Constructed protos will.
 *
 * The two-argument toUser is private; reached reflectively rather than widened,
 * the same trade as Lapi1TreeTest.
 *
 * Author Claude/bentzn
 */
class Lapi1UsersTest {

    private static UserInfo toUser(UserManagementServiceOuterClass.User user,
            List<UserManagementServiceOuterClass.Right> lstRight) throws Exception {
        Method mth = Lapi1Client.class.getDeclaredMethod("toUser",
                UserManagementServiceOuterClass.User.class, List.class);
        mth.setAccessible(true);
        try {
            return (UserInfo) mth.invoke(null, user, lstRight);
        }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException rex)
                throw rex;
            throw ex;
        }
    }


    private static UserManagementServiceOuterClass.Right actAs(String idParty) {
        return UserManagementServiceOuterClass.Right.newBuilder()
                .setCanActAs(UserManagementServiceOuterClass.Right.CanActAs.newBuilder()
                        .setParty(idParty))
                .build();
    }


    private static UserManagementServiceOuterClass.Right readAs(String idParty) {
        return UserManagementServiceOuterClass.Right.newBuilder()
                .setCanReadAs(UserManagementServiceOuterClass.Right.CanReadAs.newBuilder()
                        .setParty(idParty))
                .build();
    }


    private static UserManagementServiceOuterClass.Right admin() {
        return UserManagementServiceOuterClass.Right.newBuilder()
                .setParticipantAdmin(UserManagementServiceOuterClass.Right.ParticipantAdmin
                        .getDefaultInstance())
                .build();
    }


    /**
     * The distinction that matters: act and read are different authorities and
     * a tool that shows one as the other invites a submission that will be
     * refused, or worse, one that will not be.
     */
    @Test
    void actAndReadRightsLandInDifferentLists() throws Exception {
        UserManagementServiceOuterClass.User user = UserManagementServiceOuterClass.User
                .newBuilder().setId("alice-user").setPrimaryParty("alice::1220ab").build();

        UserInfo info = toUser(user, List.of(actAs("alice::1220ab"), readAs("bank::1220ab"),
                readAs("regulator::1220ab")));

        assertEquals("alice-user", info.idUser());
        assertEquals("alice::1220ab", info.idPartyPrimary());
        assertFalse(info.flagAdmin());
        assertEquals(List.of("alice::1220ab"), info.lstPartyAct());
        assertEquals(List.of("bank::1220ab", "regulator::1220ab"), info.lstPartyRead());
    }


    @Test
    void participantAdminSetsTheFlagAndAddsNoParty() throws Exception {
        UserInfo info = toUser(UserManagementServiceOuterClass.User.newBuilder()
                .setId("participant_admin").build(), List.of(admin()));

        assertTrue(info.flagAdmin());
        assertTrue(info.lstPartyAct().isEmpty());
        assertTrue(info.lstPartyRead().isEmpty());
    }


    /**
     * "" is what protobuf yields for an unset string; the model says null, and
     * the difference is "no primary party" versus "a party named nothing".
     */
    @Test
    void absentPrimaryPartyIsNullNotEmptyString() throws Exception {
        UserInfo info = toUser(
                UserManagementServiceOuterClass.User.newBuilder().setId("u").build(), List.of());
        assertNull(info.idPartyPrimary());
    }


    @Test
    void userWithNoRightsIsStillAUser() throws Exception {
        UserInfo info = toUser(
                UserManagementServiceOuterClass.User.newBuilder().setId("dormant").build(),
                List.of());

        assertEquals("dormant", info.idUser());
        assertFalse(info.flagAdmin());
        assertTrue(info.lstPartyAct().isEmpty());
    }


    /**
     * A right kind this build does not know is dropped, not fatal - unlike a
     * Value variant, it cannot corrupt the rights that ARE understood, and a
     * participant growing a new kind should not stop the user list rendering.
     * The trade is recorded because it points the other way from ProtoValues.
     */
    @Test
    void unknownRightKindIsIgnoredRatherThanFatal() throws Exception {
        UserInfo info = toUser(
                UserManagementServiceOuterClass.User.newBuilder().setId("mixed").build(),
                List.of(UserManagementServiceOuterClass.Right.getDefaultInstance(),
                        actAs("alice::1220ab")));

        assertEquals(List.of("alice::1220ab"), info.lstPartyAct());
    }


    @Test
    void repeatedActAsRightsAreAllKept() throws Exception {
        UserInfo info = toUser(
                UserManagementServiceOuterClass.User.newBuilder().setId("multi").build(),
                List.of(actAs("a::1220ab"), actAs("b::1220ab"), admin()));

        assertEquals(2, info.lstPartyAct().size());
        assertTrue(info.flagAdmin());
    }

}
