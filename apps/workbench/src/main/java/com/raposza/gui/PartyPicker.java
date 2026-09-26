// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.PartyInfo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Drop-down multi-select over the parties the participant knows.
 *
 * Nothing is selected until a connection reports parties, and the button says
 * how many are chosen, because "read as" silently defaulting to everything is
 * how an operator ends up looking at a view no real user of the ledger has.
 * On connect the selection defaults to ALL, which is right for a sandbox and
 * wrong for a large participant - the count on the button is what makes that
 * visible rather than implicit.
 *
 * ONE party is listed but not selected: the participant's own admin party,
 * identified by its id being the participant id rather than by any guess at the
 * format. Reading as it shows a view no user of the ledger has, and "all
 * parties" is not what an operator means when they say it. It stays in the list
 * and can be ticked.
 *
 * That preference yields when it would leave NOTHING selected - a fresh sandbox
 * knows only its own admin party, and a picker reading "0 of 1" is a tool that
 * looks broken.
 *
 * Author Claude/bentzn
 */
public final class PartyPicker extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JButton btnOpen = new JButton("no parties");
    private final DefaultListModel<PartyInfo> model = new DefaultListModel<>();
    private final JList<PartyInfo> lstView = new JList<>(model);
    private final JPopupMenu popup = new JPopupMenu();


    public PartyPicker() {
        super(new BorderLayout());

        lstView.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        lstView.setVisibleRowCount(12);
        lstView.setCellRenderer((list, value, numIndex, flagSel, flagFocus) -> {
            javax.swing.JLabel lbl = new javax.swing.JLabel(value.label());
            lbl.setToolTipText(value.idParty());
            lbl.setOpaque(true);
            if (flagSel) {
                lbl.setBackground(list.getSelectionBackground());
                lbl.setForeground(list.getSelectionForeground());
            }
            return lbl;
        });

        JScrollPane scroll = new JScrollPane(lstView);
        scroll.setPreferredSize(GuiScale.dim(420, 220));

        JPanel pnlButton = new JPanel();
        JButton btnAll = new JButton("All");
        JButton btnNone = new JButton("None");
        btnAll.addActionListener(ev -> {
            lstView.setSelectionInterval(0, model.getSize() - 1);
            refresh();
        });
        btnNone.addActionListener(ev -> {
            lstView.clearSelection();
            refresh();
        });
        pnlButton.add(btnAll);
        pnlButton.add(btnNone);

        JPanel pnlPopup = new JPanel(new BorderLayout());
        pnlPopup.add(scroll, BorderLayout.CENTER);
        pnlPopup.add(pnlButton, BorderLayout.SOUTH);
        popup.add(pnlPopup);

        lstView.addListSelectionListener(ev -> refresh());
        btnOpen.setEnabled(false);
        btnOpen.addActionListener(ev -> popup.show(btnOpen, 0, btnOpen.getHeight()));

        add(btnOpen, BorderLayout.CENTER);
    }


    /**
     * @param lstParty parties from the participant
     * @param idParticipant the participant id, so its own admin party can be
     *        left unticked; null or blank selects everything
     */
    public void setParties(List<PartyInfo> lstParty, String idParticipant) {
        model.clear();
        for (PartyInfo party : lstParty) {
            model.addElement(party);
        }

        btnOpen.setEnabled(!lstParty.isEmpty());
        if (lstParty.isEmpty()) {
            refresh();
            return;
        }

        lstView.clearSelection();
        for (Integer numIdx : defaultSelection(lstParty, idParticipant)) {
            lstView.addSelectionInterval(numIdx.intValue(), numIdx.intValue());
        }
        refresh();
    }


    /**
     * Which parties start ticked.
     *
     * Everything except the participant's own admin party - UNLESS that leaves
     * nothing, in which case everything. A fresh sandbox knows exactly one
     * party and it is the admin one; excluding it there produced a picker
     * reading "0 of 1" and a navigator that could only say "no read-as parties
     * selected", which looks like a broken tool rather than like a deliberate
     * default.
     *
     * The rule is a preference, not a prohibition. A preference that can leave
     * the operator with nothing selected has stopped being a preference.
     *
     * @param lstParty parties as listed
     * @param idParticipant the participant id; null or blank selects everything
     * @return indices into lstParty, never empty when lstParty is not empty
     */
    static List<Integer> defaultSelection(List<PartyInfo> lstParty, String idParticipant) {
        List<Integer> lstIdx = new ArrayList<>();
        for (int idx = 0; idx < lstParty.size(); idx++) {
            if (!isAdminParty(lstParty.get(idx), idParticipant))
                lstIdx.add(Integer.valueOf(idx));
        }

        if (!lstIdx.isEmpty())
            return lstIdx;

        for (int idx = 0; idx < lstParty.size(); idx++) {
            lstIdx.add(Integer.valueOf(idx));
        }
        return lstIdx;
    }


    /**
     * @param party a party
     * @param idParticipant the participant id
     * @return true when this is the participant's own admin party. Matched on
     *         equality with the participant id and on nothing else: the party
     *         id format is not something this tool is entitled to assume
     */
    static boolean isAdminParty(PartyInfo party, String idParticipant) {
        if (idParticipant == null || idParticipant.isBlank())
            return false;
        return idParticipant.equals(party.idParty());
    }


    /** @return the selected party ids, never null */
    public List<String> selected() {
        List<String> lstId = new ArrayList<>();
        for (PartyInfo party : lstView.getSelectedValuesList()) {
            lstId.add(party.idParty());
        }
        return lstId;
    }


    private void refresh() {
        int cntSel = lstView.getSelectedValuesList().size();
        int cntAll = model.getSize();
        if (cntAll == 0) {
            btnOpen.setText("no parties");
            return;
        }
        btnOpen.setText("read as: " + cntSel + " of " + cntAll + "  \u25be");
    }

}
