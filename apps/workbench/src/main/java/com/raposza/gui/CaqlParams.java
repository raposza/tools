// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.caql.Binding;
import com.raposza.render.LineRenderer;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * The parameters the runs have bound, and the register behind them.
 *
 * <h2>It outlives a run, which is the whole point of it</h2>
 *
 * Ctrl-Enter runs one line. Allocating a party and using it are therefore two
 * runs, and a binding that ended with its run would make every statement after
 * the first one unrunnable on its own. This list is what the next run is
 * seeded from, so a script can be built a line at a time the way one is
 * actually written.
 *
 * <h2>Removing is a right-click, and it is only tidying</h2>
 *
 * A run that binds a name already here REPLACES it, so nothing has to be
 * removed before a line is run again. Removal is for a register that has
 * collected names the operator is done with - and for a contract that was
 * archived outside this window, where the entry still looks fresh and the
 * participant is what refuses it.
 *
 * <h2>Values follow the id box above the tabs</h2>
 *
 * Same rule as every other pane: one id reads one way everywhere. A contract
 * id here and the same contract id in the Ledger tab must not look like two
 * different things.
 *
 * Author Claude/bentzn
 */
public final class CaqlParams extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int CNT_WIDTH_NAME = 140;

    private static final int CNT_WIDTH_TYPE = 160;

    private static final int CNT_WIDTH_VALUE = 600;

    /** What the register holds, in the order the runs bound it. */
    private final transient List<Binding> lstBinding = new ArrayList<>();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] { "Parameter", "Type", "Value" }, 0) {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int numRow, int numCol) {
            return false;
        }

    };

    private final JTable table = new JTable(model);

    private transient boolean flagShortIds = true;


    public CaqlParams() {
        super(new BorderLayout());

        table.setFont(GuiScale.fontMono(CaqlSyntax.CNT_FONT));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(CNT_WIDTH_NAME);
        table.getColumnModel().getColumn(1).setPreferredWidth(CNT_WIDTH_TYPE);
        table.getColumnModel().getColumn(2).setPreferredWidth(CNT_WIDTH_VALUE);

        // BOTH pressed and released: which one carries the popup trigger is
        // platform-dependent, and a menu that appears on one desktop and not
        // on another reads as a broken table rather than as a convention.
        table.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent ev) {
                menu(ev);
            }


            @Override
            public void mouseReleased(MouseEvent ev) {
                menu(ev);
            }

        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
    }


    /**
     * @return the register, in the order it was bound, for seeding the next run
     */
    public List<Binding> lstParam() {
        return List.copyOf(lstBinding);
    }


    /**
     * Replaces the register with what a run ended holding.
     *
     * The run was SEEDED from this list, so what comes back carries the
     * entries that survived it as well as the ones it made. Replacing is
     * therefore not a loss, and it is what makes a removed entry stay removed.
     *
     * @param lstNew the run's bindings, null or empty for an empty register
     */
    public void setParams(List<Binding> lstNew) {
        lstBinding.clear();
        if (lstNew != null)
            lstBinding.addAll(lstNew);
        repaintRows();
    }


    /**
     * @param flagShortIdsNew whether ids are shown in their short form, which
     *        follows the box above the tabs
     */
    public void setShortIds(boolean flagShortIdsNew) {
        this.flagShortIds = flagShortIdsNew;
        repaintRows();
    }


    /**
     * Shows the one thing a parameter can be told to do.
     *
     * The row under the pointer is SELECTED first, so the menu never acts on a
     * row other than the one it appeared over.
     *
     * @param ev the mouse event, acted on only when it carries the trigger
     */
    private void menu(MouseEvent ev) {
        if (!ev.isPopupTrigger())
            return;

        int numRow = table.rowAtPoint(ev.getPoint());
        if (numRow < 0 || numRow >= lstBinding.size())
            return;

        table.setRowSelectionInterval(numRow, numRow);
        String nameParam = lstBinding.get(numRow).name();

        JMenuItem item = new JMenuItem("Remove $" + nameParam);
        item.addActionListener(evAct -> remove(nameParam));

        JPopupMenu menu = new JPopupMenu();
        menu.add(item);
        menu.show(table, ev.getX(), ev.getY());
    }


    /**
     * @param nameParam the binding to drop from the register
     */
    private void remove(String nameParam) {
        lstBinding.removeIf(binding -> binding.name().equals(nameParam));
        repaintRows();
    }


    private void repaintRows() {
        model.setRowCount(0);
        for (Binding binding : lstBinding) {
            model.addRow(new Object[] { "$" + binding.name(), TypeText.strOf(binding.type()),
                strValue(binding) });
        }
    }


    /**
     * @param binding the parameter
     * @return its value on one line, in the id form the window is showing
     */
    private String strValue(Binding binding) {
        String strLine = LineRenderer.line(binding.value());
        return flagShortIds ? ShortIds.text(strLine) : strLine;
    }

}
