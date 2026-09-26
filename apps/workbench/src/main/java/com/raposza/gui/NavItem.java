// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Contract;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.UserInfo;

/**
 * What a node in the navigator tree stands for.
 *
 * Sealed so that the detail pane's switch is exhaustive: a new kind of node
 * that nobody wrote a renderer for is a compile error rather than a node that
 * clicks to a blank pane.
 *
 * The tree carries these as user objects and never carries a formatted String,
 * so selecting a node hands the real domain object to whatever renders it.
 *
 * Author Claude/bentzn
 */
public sealed interface NavItem {

    /** How wide the template column is, in characters. */
    int CNT_TEMPLATE_COL = 22;

    /** @return what the tree shows for this node */
    String label();


    /** @return the full text for the tooltip, or null for no tooltip */
    String tip();


    /**
     * A section heading, or a note standing in for a section that could not be
     * read.
     *
     * @param strLabel the heading
     * @param strNote why the section is empty or capped, null when it is neither
     */
    record Group(String strLabel, String strNote, String strTip) implements NavItem {

        /**
         * @param strLabelNew the heading
         * @param strNoteNew what to show beside it, and to explain it
         */
        Group(String strLabelNew, String strNoteNew) {
            this(strLabelNew, strNoteNew, strNoteNew);
        }


        @Override
        public String label() {
            return strNote == null ? strLabel : strLabel + "   \u2014 " + strNote;
        }


        @Override
        public String tip() {
            return strTip;
        }

    }

    /** @param user the ledger user */
    record User(UserInfo user) implements NavItem {

        @Override
        public String label() {
            return user.idUser() + (user.flagAdmin() ? "   [admin]" : "");
        }


        @Override
        public String tip() {
            return user.idPartyPrimary() == null ? user.idUser()
                    : user.idUser() + "  primary party " + user.idPartyPrimary();
        }

    }

    /**
     * @param party the party
     * @param strShown what to put in the row, or null to shorten it here
     */
    record Party(PartyInfo party, String strShown) implements NavItem {

        /**
         * SHORTENED WITHOUT KNOWING THE OTHERS, which is only safe when there
         * is nothing to collide with. {@link NavigatorPanel} has the whole
         * party list and computes a label that stays unambiguous; this
         * constructor is for a single party out of context, where there is no
         * list to be ambiguous against.
         *
         * @param partyNew the party
         */
        Party(PartyInfo partyNew) {
            this(partyNew, null);
        }




        @Override
        public String label() {
            String strId = strShown == null
                    ? strBeforeNamespace(party.idParty()) : strShown;
            if (party.label().equals(party.idParty()))
                return strId;
            return party.label() + "   " + strId;
        }


        @Override
        public String tip() {
            return party.idParty();
        }


    }

    /** @param group the template and the contracts read for it */
    record Template(LedgerSnapshot.TemplateGroup group) implements NavItem {

        @Override
        public String label() {
            return group.strLabel();
        }


        @Override
        public String tip() {
            return group.idTemplate().toString();
        }

    }

    /** @param contract the contract */
    record Ct(Contract contract) implements NavItem {

        @Override
        public String label() {
            return pad(contract.idTemplate().shortName()) + shorten(contract.idContract());
        }


        @Override
        public String tip() {
            return contract.idContract();
        }


        /**
         * A contract id is 130-odd characters of hex and the tree is 320
         * pixels wide, so the row gets the shared short form and the whole id
         * stays in the tooltip and in the detail pane.
         *
         * HEAD AND TAIL, not the head alone. Contract ids differing only in
         * their last characters exist, and abbreviating from one end renders
         * two of them as one row.
         *
         * @param idContract the contract id
         * @return the ends of it, with the middle marked as cut
         */
        static String shorten(String idContract) {
            return ShortIds.id(idContract);
        }

    }


    /**
     * The readable half of a party id.
     *
     * @param idParty the party id
     * @return everything before `::`, or the whole id when there is none
     */
    static String strBeforeNamespace(String idParty) {
        if (idParty == null)
            return "";

        int idxSep = idParty.indexOf("::");
        return idxSep < 0 ? idParty : idParty.substring(0, idxSep);
    }


    /**
     * Pads a template name so the contract ids under it line up.
     *
     * A TAB WOULD NOT DO IT. The tree renders in a proportional font and a tab
     * stop lands wherever the look and feel puts it, which is not the same
     * place for `Main:Pet` and `Main:AdoptionRequest`.
     *
     * @param strName the template's short name
     * @return it, padded, with a trailing gap
     */
    static String pad(String strName) {
        StringBuilder buf = new StringBuilder(strName == null ? "" : strName);
        while (buf.length() < CNT_TEMPLATE_COL) {
            buf.append(' ');
        }
        return buf.append("  ").toString();
    }

}
