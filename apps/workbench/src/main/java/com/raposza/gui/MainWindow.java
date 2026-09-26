// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.profile.HostProfile;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * The Workbench window: a tab per participant, and nothing else.
 *
 * <h2>What this class is, since 2026-09-22</h2>
 *
 * It used to BE the workbench - one participant, one navigator, one client.
 * Operator instruction: one tab per participant, the Connect menu item becomes
 * a button on top of each tab. So everything that belongs to a connection is in
 * {@link ParticipantPane} and what is left here is the frame, the tab strip and
 * the shutdown, which are the three things a process has one of.
 *
 * <h2>Where the tabs come from</h2>
 *
 * The dialog still opens on start and it produces one of two things.
 *
 * A STANDALONE LEDGER IS ONE PARTICIPANT, so it gets one tab.
 *
 * A DISCOVERY ENDPOINT IS A TOPOLOGY, so it gets a tab per LEDGER in the
 * document it serves - {@link Discovery#lstIdxLedger}. A Sandbox publishes one
 * node and opens one tab; LocalNetND publishes three and opens three. The
 * window does not choose among them and never did: choosing was the shape this
 * replaced.
 *
 * A discovery document chosen from a TAB's own Connect button reaches here too,
 * through the sink each pane is built with, and rebuilds the whole strip -
 * because a document answers "which participants are there", which is a
 * question about the window rather than about one tab.
 *
 * <h2>The shutdown hook is the window's, not the pane's</h2>
 *
 * gRPC keeps non-daemon threads alive, so a disposed window is not a finished
 * process. One hook closes every pane's channel; it runs on its own thread and
 * so calls only the Swing-free half - {@link ParticipantPane#closeQuietly}.
 *
 * Author Claude/bentzn
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** How wide a wrapped participant message is allowed to run. */
    private static final int CNT_WRAP = 88;

    /**
     * The margin around the whole page, matching the Sandbox's
     * `GuiTheme.N_PAD_PAGE`. See the constructor for why it is a literal.
     */
    private static final int N_PAD_PAGE = 22;

    /**
     * The catalogue, handed to every pane so the dialog's standalone form can
     * be pre-filled. It is not a combo and has not been one since the dialog: a
     * catalogue line carries no generation, which is what the dialog asks for.
     */
    private final transient List<HostProfile> lstProfileAll;

    private final JTabbedPane tabsParticipant = new JTabbedPane();

    /**
     * The panes, in tab order, so the shutdown reaches them without asking
     * Swing for its children from a thread that may not ask.
     */
    private final transient List<ParticipantPane> lstPane = new ArrayList<>();

    private final transient Thread threadShutdown =
            new Thread(this::closeQuietly, "workbench-shutdown");


    /** @param lstProfile the catalogue, may be empty */
    public MainWindow(List<HostProfile> lstProfile) {
        super("Raposza Workbench");

        this.lstProfileAll = lstProfile == null ? List.of() : List.copyOf(lstProfile);

        AppIcon.apply(this);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // THE SAME PAGE MARGIN THE SANDBOX HAS - his instruction, 2026-09-22.
        // That window wraps its tabs in a page panel bordered `N_PAD_PAGE` on
        // every side and this one had the strip flush against the frame, so
        // two windows of one suite sat differently on the screen. The number
        // is REPEATED rather than imported: `GuiTheme` lives in `apps/sandbox`
        // and this module does not depend on it - and should not start doing
        // so to read one int.
        JPanel pnlPage = new JPanel(new BorderLayout());
        pnlPage.setBorder(GuiScale.border(N_PAD_PAGE, N_PAD_PAGE, N_PAD_PAGE, N_PAD_PAGE));
        pnlPage.add(tabsParticipant, BorderLayout.CENTER);
        add(pnlPage, BorderLayout.CENTER);

        // NO MENU BAR. Its one item was Connect, and that is a button on each
        // tab now - operator instruction, 2026-09-22.

        Runtime.getRuntime().addShutdownHook(threadShutdown);
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent ev) {
                System.exit(0);
            }

        });

        setSize(GuiScale.dimClamped(1400, 820));
        setLocationRelativeTo(null);
    }


    /**
     * A tab per ledger in the document, each connecting itself.
     *
     * EVERY EXISTING TAB GOES FIRST, channels included. A document describes
     * the topology that is up now, and leaving the previous set beside it would
     * put tabs on the strip pointing at participants that may no longer be
     * there.
     *
     * @param doc what the endpoint served
     */
    public void useDiscovery(Discovery doc) {
        List<Integer> lstIdx = lstIdxInTabOrder(doc);
        if (lstIdx.isEmpty()) {
            // THE DOCUMENT IS THE ANSWER AND IT SAYS NO. A stopped stack lists
            // no node, and an empty strip would report that as a window with
            // nothing in it rather than as a stack that is down.
            clearPanes();
            ParticipantPane paneOnly = paneNew();
            addTab(paneOnly);
            paneOnly.showProblem(doc.strUrl() + " reports state " + doc.strState()
                    + " and lists no ledger to connect to.\n\nStart the stack and press"
                    + " Connect.");
            return;
        }

        clearPanes();
        for (Integer idxNode : lstIdx) {
            ParticipantPane pane = paneNew();
            pane.useNode(doc, idxNode.intValue());
            addTab(pane);
        }
        tabsParticipant.setSelectedIndex(0);
    }


    /**
     * THE TAB ORDER - app-provider, app-user, then sv. His instruction,
     * 2026-09-22, `todo.md` A-37: delivered, lost to a whole-file replace,
     * delivered again and lost again, so it is now asserted by
     * `MainWindowTabOrderTest` rather than trusted to survive.
     *
     * A STABLE SORT ON THE ROLE, so nodes of one rank keep the producer's
     * order and a Sandbox's single node is untouched.
     *
     * @param doc the document the tabs come from
     * @return the ledger nodes' indices, in the order their tabs are added
     */
    static List<Integer> lstIdxInTabOrder(Discovery doc) {
        List<Integer> lstOut = new ArrayList<>(doc.lstIdxLedger());
        lstOut.sort(Comparator.comparingInt(
                idx -> nRankRole(doc.onNode(idx.intValue()).strRoleNode())));
        return lstOut;
    }


    /**
     * @param strRole what a node is for
     * @return 0 for app-provider, 1 for app-user, 2 for anything else
     */
    static int nRankRole(String strRole) {
        if ("app-provider".equals(strRole))
            return 0;
        if ("app-user".equals(strRole))
            return 1;
        return 2;
    }


    /**
     * @param choice what the dialog produced for a standalone ledger
     */
    private void useStandalone(ConnectDialog.Choice choice) {
        clearPanes();
        ParticipantPane pane = paneNew();
        pane.useStandalone(choice);
        addTab(pane);
    }


    /**
     * The startup dialog, asked THROUGH A PANE.
     *
     * ON START. A window with no target is a window with nothing in it, so the
     * question is asked before the operator has to work out where to ask it.
     *
     * THE PANE ASKS RATHER THAN THE WINDOW, and that is not ceremony: the
     * dialog reads and writes an {@link AuthPanel}, which holds the settings
     * file and any pasted token for the target it is pointed at. A window
     * asking with a panel of its own would produce a standalone connection
     * whose credential the tab then knows nothing about.
     *
     * The pane is already on the strip when it asks, so a cancelled dialog
     * leaves a tab with its own Connect button rather than an empty window.
     */
    private void askFirst() {
        ParticipantPane paneFirst = paneNew();
        addTab(paneFirst);
        paneFirst.connectPressed();
    }


    /**
     * @return a pane wired to hand any discovery document it is given back to
     *         this window
     */
    private ParticipantPane paneNew() {
        return new ParticipantPane(lstProfileAll, this::useDiscovery);
    }


    /** @param pane the pane to put on the strip under its own name */
    private void addTab(ParticipantPane pane) {
        lstPane.add(pane);
        tabsParticipant.addTab(pane.nameTab(), pane);
    }


    /**
     * Drops every tab and closes what it was holding.
     *
     * THE CHANNEL FIRST. A pane removed from the strip is unreachable and its
     * gRPC channel is not: dropping the component without closing it leaks a
     * connection and the non-daemon threads under it.
     */
    private void clearPanes() {
        for (ParticipantPane pane : lstPane) {
            pane.close();
        }
        lstPane.clear();
        tabsParticipant.removeAll();
    }


    /**
     * Releases every channel and touches NO component.
     *
     * This is what the shutdown hook runs, and a hook runs on its own thread.
     * Emptying a navigator from there would be a Swing call off the event
     * dispatch thread during teardown, which is a hang or an exception at the
     * exact moment nobody is watching.
     *
     * Idempotent: the hook and dispose may both reach it.
     */
    private void closeQuietly() {
        for (ParticipantPane pane : lstPane) {
            pane.closeQuietly();
        }
    }


    @Override
    public void dispose() {
        for (ParticipantPane pane : lstPane) {
            pane.close();
        }
        try {
            Runtime.getRuntime().removeShutdownHook(threadShutdown);
        }
        catch (IllegalStateException ex) {
            // Already shutting down, which means the hook is doing the same
            // work this method just did. Closing is idempotent.
        }
        super.dispose();
    }


    /** @param lstProfile the catalogue */
    public static void open(List<HostProfile> lstProfile) {
        open(lstProfile, null);
    }


    /**
     * @param lstProfile the catalogue
     * @param discovery a document already read - from `--discovery` - or null
     */
    public static void open(List<HostProfile> lstProfile, Discovery discovery) {
        SwingUtilities.invokeLater(() -> {
            MainWindow wnd = new MainWindow(lstProfile);
            wnd.setVisible(true);
            // A DOCUMENT ON THE COMMAND LINE IS AN ANSWER ALREADY GIVEN. The
            // dialog used to open over it and overwrite it, which asked the
            // operator the one question they had just answered.
            if (discovery == null)
                wnd.askFirst();
            else
                wnd.useDiscovery(discovery);
        });
    }


    /**
     * @param cnt how many
     * @param strOne singular form
     * @param strMany plural form, passed in because party/parties is not the
     *        rule an "add an s" helper would apply
     * @return the count and the right word
     */
    static String count(int cnt, String strOne, String strMany) {
        return cnt + " " + (cnt == 1 ? strOne : strMany);
    }


    /**
     * A participant's error message is one long line and the detail pane does
     * not wrap, so it would run off the right edge with the useful half beyond
     * it.
     *
     * IT STAYED HERE when the panes moved out. It is a string helper that
     * reaches nothing, `CaqlResults` and `LabelsTest` already call it as this
     * class's, and a second copy beside the panes would be two of one rule.
     *
     * @param strText what to wrap, may be null
     * @return it, broken on spaces
     */
    static String wrap(String strText) {
        if (strText == null)
            return "";

        StringBuilder buf = new StringBuilder();
        for (String strLine : strText.split("\n", -1)) {
            int cntCol = 0;
            for (String strWord : strLine.split(" ")) {
                if (cntCol > 0 && cntCol + strWord.length() + 1 > CNT_WRAP) {
                    buf.append('\n');
                    cntCol = 0;
                }
                else if (cntCol > 0) {
                    buf.append(' ');
                    cntCol++;
                }
                buf.append(strWord);
                cntCol += strWord.length();
            }
            buf.append('\n');
        }
        return buf.toString();
    }

}
