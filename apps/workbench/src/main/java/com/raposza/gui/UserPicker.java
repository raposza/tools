// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Which user the session is working as. ONE, never several.
 *
 * <h2>Why this replaced a multi-select of parties</h2>
 *
 * The party picker let a session read as any set of parties the participant
 * hosted, which is not a thing a ledger permits: the credential decides, and
 * asking for one party too many is refused as a whole with no clue which one
 * caused it. That is what it did, for a whole session.
 *
 * A user is the unit the participant actually authorises, and it is also the
 * unit CaQL will name in `AS &lt;user&gt;`. One selection here, one token, one
 * set of rights, and a refusal that means what it says.
 *
 * Author Claude/bentzn
 */
public final class UserPicker extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JComboBox<String> cmbUser = new JComboBox<>();

    private transient Runnable runOnChange;

    /** Set while the model is being replaced, so a rebuild is not a choice. */
    private transient boolean flagFilling;


    public UserPicker() {
        super(new BorderLayout(GuiScale.scale(6), 0));
        add(new JLabel("work as"), BorderLayout.WEST);
        add(cmbUser, BorderLayout.CENTER);
        cmbUser.addActionListener(ev -> {
            if (!flagFilling && runOnChange != null)
                runOnChange.run();
        });
    }


    /**
     * @param runOnChangeNew what to do when the operator picks another user
     */
    public void onChange(Runnable runOnChangeNew) {
        this.runOnChange = runOnChangeNew;
    }


    /**
     * @param lstUser who is on offer
     * @param idSelected who to select, or null for the first
     * @param flagChoosable whether the credential allows a choice at all
     */
    public void setUsers(List<String> lstUser, String idSelected, boolean flagChoosable) {
        flagFilling = true;
        try {
            cmbUser.removeAllItems();
            for (String idUser : lstUser) {
                cmbUser.addItem(idUser);
            }
            if (idSelected != null && lstUser.contains(idSelected))
                cmbUser.setSelectedItem(idSelected);
            else if (!lstUser.isEmpty())
                cmbUser.setSelectedIndex(0);
        }
        finally {
            flagFilling = false;
        }
        cmbUser.setEnabled(flagChoosable && lstUser.size() > 1);
        cmbUser.setToolTipText(flagChoosable
                ? "the participant user this session works as; changing it mints a new token"
                : "the credential names this user and cannot be exchanged for another");
    }


    /**
     * @return the selected user, or null when there is none
     */
    public String selected() {
        return (String) cmbUser.getSelectedItem();
    }

}
