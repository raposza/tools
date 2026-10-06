// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.PackageCache_i;
import com.raposza.api.Resolver_i;
import com.raposza.api.TokenSource_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.model.Contract;
import com.raposza.api.model.LedgerInfo;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.UserInfo;
import com.raposza.api.model.Resolved;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UpdateScan;
import com.raposza.api.profile.HostProfile;
import com.raposza.api.profile.HostProfileStore;
import com.raposza.caql.AuditLog;
import com.raposza.caql.Binding;
import com.raposza.caql.CaqlException;
import com.raposza.caql.CaqlParser;
import com.raposza.caql.Entry;
import com.raposza.caql.RunConfig;
import com.raposza.caql.RunProgress_i;
import com.raposza.caql.RunStatus;
import com.raposza.caql.Runner;
import com.raposza.caql.Stmt;
import com.raposza.caql.Transcript;
import com.raposza.jwt.ProfileAuth;
import com.raposza.jwt.ProfileAuthStore;
import com.raposza.jwt.TokenParties;
import com.raposza.lf.LfDecoder;
import com.raposza.resolve.ChainResolver;
import com.raposza.spi.LedgerClient_i;
import com.raposza.spi.LfDecoder_i;
import com.raposza.spi.Target_i;
import com.raposza.spi.Targets;
import com.raposza.types.FilePackageCache;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/**
 * One participant, and everything this window can do to it.
 *
 * <h2>Why this is a panel and not the window</h2>
 *
 * It WAS the window. A discovery document describes a topology rather than a
 * participant - LocalNetND publishes three - and the operator asked for one tab
 * per participant, each with its own Connect button on top. So everything that
 * belongs to ONE connection moved here and {@link MainWindow} kept only what
 * belongs to the process: the frame, the tab set and the shutdown.
 *
 * WHAT IS PER PANE is the whole of a session: the client, the resolver, the
 * type registry, the navigator, the CaQL register and transcript, the user
 * picker, the back stack and the status bar. Two tabs are two participants and
 * share nothing but the package cache, which is keyed by content hash and is
 * the same answer for both.
 *
 * Two rules shape the code more than the layout does.
 *
 * Nothing touches the ledger on the event dispatch thread. Every call goes
 * through a SwingWorker, because an ACS scan against a real participant takes
 * long enough to freeze a window that looks broken rather than busy.
 *
 * The status bar carries the profile colour and the access mode, always. The
 * whole reason AccessMode is fixed in a file rather than toggled in the session
 * is that reaching the wrong participant should be visible before the mistake,
 * not after it. It is per pane for the same reason: two tabs on two
 * participants with one bar between them would name neither.
 *
 * The navigator loads once on connect and then only when Reload is pressed. It
 * is NOT refreshed when the read-as selection changes, deliberately: a pane
 * that silently re-scans the ACS every time a party is ticked turns a click
 * into a stream against a production participant. What is shown is what was
 * read, and the Reload button is how it becomes current.
 *
 * The identity in use is ALWAYS on the status bar, next to the participant and
 * the access mode. A connection whose token is invisible is a connection whose
 * privileges are a guess, and on a shared participant that guess is the one
 * that matters. Where no token is sent the bar says so in the same place, in
 * the same words, rather than falling silent.
 *
 * Author Claude/bentzn
 */
final class ParticipantPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How much of a token source description the status bar carries. */
    private static final int CNT_STATUS_MAX = 90;

    /** How many blank lines follow a block, so it does not end on the edge. */
    private static final int CNT_TAIL = 10;

    private static final String STR_TAIL = "\n ".repeat(CNT_TAIL);

    private static final String CARD_TEXT = "text";
    private static final String CARD_TREE = "tree";

    /**
     * The catalogue, kept so the dialog can pre-fill its standalone form. It
     * is no longer a combo: a catalogue line carries no generation, which is
     * the whole reason the dialog asks for one.
     */
    private final transient List<HostProfile> lstProfileAll;

    /** What the dialog last chose, or null before the first connection. */
    private transient HostProfile profileChosen;

    /** What the dialog last chose to present, never null. */
    private transient ProfileAuth authChosen = ProfileAuth.NONE;

    /**
     * The generation a STANDALONE target was said to speak, or null when a
     * discovery document answers it instead.
     */
    private transient ApiGeneration generationChosen;
    private final UserPicker picker = new UserPicker();

    /**
     * Where a token for a named user comes from, or null before the first
     * connection. {@link MintTokens} on a Sandbox, {@link OpenUsers} on an
     * unauthenticated participant, {@link FixedToken} on one pasted JWT, and
     * an OIDC client one day.
     */
    private transient UserTokens_i tokens;

    /** What the participant reported, so a user's read parties are known. */
    private transient List<UserInfo> lstUserInfo = List.of();

    /**
     * Every party the participant hosts, as of the last connection.
     *
     * NOT DERIVED FROM THE USERS. A party no user holds a right to is hosted
     * all the same - `sandbox` is one on a fresh stack - and a union of the
     * rights would quietly drop it. It is what a participant reporting NO
     * USERS is read as, which is the one thing the `*` entry did that no
     * named user can.
     */
    private transient List<String> lstPartyHosted = List.of();

    /**
     * The last read, kept so that a short id typed into CaQL can be put
     * back: {@link LongIds} resolves against the parties and contracts this
     * window has actually seen, and there is nowhere else holding them.
     */
    private transient LedgerSnapshot snapshotShown;

    /**
     * EVERY ID THIS WINDOW HAS SEEN SINCE IT CONNECTED, not just the ones
     * the last read returned.
     *
     * A read is made as the selected user, so changing `work as` narrows
     * what comes back - and on a participant that refuses `parties()` to a
     * non-admin it returns no parties at all. The editor still holds the
     * script seeded from a wider read, in the SHORT form, and every one of
     * its party ids then resolved to nothing: `Run` refused a script this
     * window had written itself.
     *
     * Emptied when the operator CONNECTS, with the parameter register and
     * the transcript - and by nothing else. A `work as` change reaches the
     * same code and must not empty any of the three: it is the same
     * participant, the ids it issued are still its own, and emptying them is
     * what left the editor holding a script this window had written itself
     * whose party ids resolved to nothing.
     */
    private final transient List<String> lstIdSeen = new ArrayList<>();

    /**
     * How far back the pane remembers. Bounded because every frame holds a
     * rendered block, and an unbounded stack in a window that stays open for a
     * day is a leak with a nice name.
     */
    private static final int CNT_BACK_MAX = 50;

    /** One pane, as it was, so Back repaints instead of re-reading. */
    private record PaneState(String strBase, String strActivity, Contract contract) {}

    private final JButton btnConnect = new JButton("Connect...");

    private final JButton btnBack = new JButton("Back");

    private final transient Deque<PaneState> lstBack = new ArrayDeque<>();

    private final JEditorPane areaResult = new JEditorPane();
    private final JLabel lblStatus = new JLabel(" not connected");
    private final NavigatorPanel navigator = new NavigatorPanel();
    private final AuthPanel auth = new AuthPanel();
    private final TxTreePanel paneTx = new TxTreePanel();
    private final JPanel pnlResult = new JPanel(new CardLayout());

    /**
     * The two views of one connection, and they sit BESIDE THE NAVIGATOR
     * rather than above it - operator instruction, 2026-09-09. The tree is
     * what both are about: a script names a contract and the detail of that
     * contract is one tab away with the list still on screen, instead of
     * behind a tab that also took the tree away.
     *
     * TAB 0 IS `Detail`, which {@link #openFromCaql} relies on. The session
     * identity, the profile colour and `Back` stay outside: they govern
     * whatever is in front.
     */
    private final JTabbedPane tabs = new JTabbedPane();

    private final CaqlPanel paneCaql = new CaqlPanel();

    private final ResolvedText text = new ResolvedText();
    private final NavText textNav = new NavText();

    /**
     * Whether the panes shorten the ids they show. ON by default: the form it
     * produces is what fits the window, and the full form is one click away.
     *
     * It governs the two DETAIL panes - the result pane and the transaction
     * detail. The two TREES shorten their rows always, as they already did: a
     * 320 pixel row was never going to hold 138 characters, and the head and
     * tail a row shows are the same ones the detail shows, so a row and its
     * detail still match by eye.
     */
    private final JCheckBox chkShortIds = new JCheckBox("Short ids", true);

    /**
     * What the pane holds, EXACTLY as the renderers produced it.
     *
     * The shortened form is derived on the way to the screen and never stored,
     * so toggling `Short ids` re-renders from these and touches no ledger. The
     * activity is kept apart from the contract because it is appended below it
     * and must replace, not stack, when it is read again.
     */
    private transient String strRawBase = "";

    private transient String strRawActivity = "";

    /** What the pane is ABOUT, so it does not offer to open itself. */
    private transient String idSelf;

    /**
     * Which activity read is the current one.
     *
     * A scan runs on a worker and the operator can select another contract, or
     * turn the box off, before it lands. Without this the answer to a question
     * nobody is asking any more paints itself under a contract it is not
     * about.
     */
    private transient int cntActivityReq;


    /**
     * The cache is per USER, not per profile: a package id is a content hash,
     * so two participants hosting the same package share one entry and nothing
     * has to be re-fetched when the profile changes.
     */
    private final transient PackageCache_i cachePackage = new FilePackageCache();

    private final transient LfDecoder_i decoderLf = new LfDecoder();

    private transient LedgerClient_i client;
    private transient Resolver_i resolver;
    private transient TypeRegistry_i registry;
    private transient HostProfile profileActive;
    private transient String strAuthActive = "none";
    private transient TxTree treeShown;
    private transient Contract contractShown;

    /**
     * The Sandbox behind this session, or null for a standalone target.
     *
     * NOT FINAL since the dialog: a session can be pointed at a Sandbox, then
     * at a standalone ledger, without restarting the window.
     */
    private transient Discovery discovery;


    /**
     * What this pane is called on its tab.
     *
     * OFF THE DOCUMENT for a discovered node - `sv`, `app-provider` - and off
     * the profile for a standalone one. A tab strip is read at a glance and
     * `app-provider 3.5.13 open-source` is not a glance.
     */
    private transient String strNameTab = "participant";

    /**
     * Where a discovery document goes when one is chosen from THIS pane's
     * Connect button.
     *
     * A DOCUMENT IS NOT A PANE'S BUSINESS. It describes a topology, and the
     * answer to choosing one is a tab per participant in it - which only the
     * window can do. A standalone choice is this pane's own and never reaches
     * here.
     */
    private final transient Consumer<Discovery> sinkDiscovery;


    /**
     * @param lstProfile the catalogue, used to pre-fill the dialog's standalone
     *        form; may be empty
     * @param sinkDiscovery what to hand a discovery document to when this
     *        pane's Connect button produces one
     */
    ParticipantPane(List<HostProfile> lstProfile, Consumer<Discovery> sinkDiscovery) {
        super(new BorderLayout());

        this.lstProfileAll = lstProfile == null ? List.of() : List.copyOf(lstProfile);
        this.sinkDiscovery = sinkDiscovery;

        add(buildTop(), BorderLayout.NORTH);
        add(buildLedger(), BorderLayout.CENTER);
        add(buildStatus(), BorderLayout.SOUTH);

        navigator.onSelect(this::showItem);
        navigator.onReload(ev -> loadSnapshot());
        // A NEW USER REBUILDS THE CLIENT AND NOTHING ELSE. The token is
        // presented per call, so the client could in principle be kept - but
        // the rights it was built against decide what every pane already
        // holds, and reusing it would leave the old user's snapshot on screen
        // under the new user's name.
        //
        // IT IS NOT A NEW CONNECTION - operator instruction, 2026-09-09. The
        // register, the transcript and the ids belong to the SESSION and
        // survive it; only the Connect dialog empties them.
        picker.onChange(() -> connect(false));
        chkShortIds.addActionListener(ev -> shortIds());
        paneCaql.onRun(this::runCaql);
        paneCaql.onLink(this::openFromCaql);
        paneCaql.onExpand(this::strExpanded);

        lblStatus.setText("  not connected");
    }



    /**
     * Everything LEFT ALIGNED - operator instruction, 2026-08-25. A GridBag
     * with no weight on any column centres the strip, which put the one
     * control that decides what every pane shows in the middle of an empty
     * bar.
     *
     * CONNECT IS A BUTTON HERE, NOT A MENU ITEM - operator instruction,
     * 2026-09-22. It was on the window's menu bar while the window was one
     * participant; with a tab per participant a single menu item would have to
     * mean "the tab in front", which is a target the operator reads off the
     * strip rather than one the control names.
     */
    private JPanel buildTop() {
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiScale.scale(12),
                GuiScale.scale(2)));
        pnl.setBorder(GuiScale.border(6, 8, 2, 8));

        btnConnect.setToolTipText("connect this tab to a participant, or open a"
                + " discovery endpoint and take a tab per participant in it");
        btnConnect.addActionListener(ev -> connectPressed());
        pnl.add(btnConnect);

        // `Ask the ledger` IS GONE - operator instruction, 2026-08-24. CaqL
        // will carry identifier resolution, and a box that resolves one id is
        // not worth the two text fields beside it being mistaken for it. The
        // navigator's filter stays; nothing else here reaches the participant.
        pnl.add(new JLabel("Session"));
        pnl.add(picker);

        // `Short ids` IS PER PANE, and it was global when there was one pane.
        // Within a tab it still governs both views, which is what its reason
        // asks for: CaQL results render identifiers through the same LinkText
        // the Ledger panes do, so one identifier must not read two ways
        // depending on which tab of THIS participant found it.
        chkShortIds.setToolTipText("show ids as head[...]tail, and a party as hint::[...]tail");
        pnl.add(chkShortIds);

        return pnl;
    }


    /**
     * The controls that belong to ONE view.
     *
     * `Back` walks the result pane's link stack. It is above both the
     * navigator and the two panes because it is about the DETAIL pane's
     * history, which is what a link followed from either tab fills.
     */
    private JPanel buildLedgerBar() {
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiScale.scale(12),
                GuiScale.scale(2)));
        pnl.setBorder(GuiScale.border(2, 8, 2, 8));

        btnBack.setEnabled(false);
        btnBack.setToolTipText("back to the pane the last link was followed from");
        btnBack.addActionListener(ev -> back());
        pnl.add(btnBack);

        return pnl;
    }


    /**
     * The whole of the window under the session strip: `Back` over a split
     * of the navigator and the two panes.
     */
    private JPanel buildLedger() {
        JPanel pnlLedger = new JPanel(new BorderLayout());
        pnlLedger.add(buildLedgerBar(), BorderLayout.NORTH);
        pnlLedger.add(buildCentre(), BorderLayout.CENTER);
        return pnlLedger;
    }




    /**
     * Navigator left, result right. A split rather than a fixed width because
     * a contract id is 130 characters and a party id is longer than most panes
     * anyone would size by hand.
     */
    private JSplitPane buildCentre() {
        pnlResult.add(buildResult(), CARD_TEXT);
        pnlResult.add(paneTx, CARD_TREE);
        paneTx.onJson(ev -> showJson());

        // Selecting arms the detail pane; opening switches to it. A table that
        // jumped away on every click could not be scanned, and the
        // creating-transaction button lives beside the detail.

        tabs.addTab("Detail", pnlResult);
        tabs.addTab("CaQL", paneCaql);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigator, tabs);
        split.setDividerLocation(navigator.cntWidthWanted());
        split.setResizeWeight(0);
        split.setBorder(null);
        return split;
    }


    /**
     * The result pane, which is HTML rather than plain text so that every
     * identifier in it can be a link - operator instruction, 2026-08-25.
     *
     * THE ACTIVITY BUTTON IS GONE - operator instruction, 2026-08-25. It is a
     * checkbox on the top bar now, beside the user it reads as, which is where
     * a standing choice belongs. A button under the pane made the operator ask
     * for the same thing again on every contract.
     */
    private JPanel buildResult() {
        HTMLEditorKit kit = new HTMLEditorKit();
        for (String strRule : LinkText.arrStyle()) {
            kit.getStyleSheet().addRule(strRule);
        }
        areaResult.setEditorKit(kit);
        areaResult.setEditable(false);
        // The pane owns the font rather than the stylesheet, so it lands on
        // the same size as the navigator instead of on a CSS length that only
        // happens to look close.
        areaResult.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        areaResult.setFont(GuiDesign.fontMonoAt(navigator.cntFontSize()));
        // A non-standard scheme has no protocol handler, so getURL() is null
        // and the raw href comes back through getDescription().
        areaResult.addHyperlinkListener(ev -> {
            if (ev.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                openLink(ev.getDescription());
        });
        // A right-click is not a hyperlink activation, so the listener above
        // never sees one and the two do not compete.
        areaResult.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent ev) {
                popupCopy(ev);
            }


            @Override
            public void mouseReleased(MouseEvent ev) {
                popupCopy(ev);
            }

        });
        setPane("Pick a participant and press Connect.");

        JScrollPane scroll = new JScrollPane(areaResult);
        scroll.setBorder(null);

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.setBorder(GuiScale.border(4, 4, 4, 8));
        pnl.add(scroll, BorderLayout.CENTER);
        return pnl;
    }


    private JPanel buildStatus() {
        JPanel pnl = new JPanel(new BorderLayout());
        lblStatus.setOpaque(true);
        lblStatus.setForeground(Color.WHITE);
        lblStatus.setPreferredSize(GuiScale.dim(10, 26));
        pnl.add(lblStatus, BorderLayout.CENTER);
        return pnl;
    }


    /**
     * Colour comes from the profile, falling back to the mode's default. It is
     * shown before a connection exists as well as after, because the point is
     * to notice the wrong participant BEFORE talking to it.
     */
    private void showProfileColour(HostProfile profile, String strWhat) {
        if (profile == null)
            return;

        String strColour = profile.strColour() == null || profile.strColour().isBlank()
                ? HostProfileStore.defaultColour(profile.mode())
                : profile.strColour();

        Color colour;
        try {
            colour = Color.decode(strColour);
        }
        catch (NumberFormatException ex) {
            colour = Color.DARK_GRAY;
        }

        lblStatus.setBackground(colour);
        // WHERE, and nothing else. The bar used to carry the access mode and
        // then a tally of everything the read returned - all of which is on
        // screen in the tree a few pixels above it. What is NOWHERE else is
        // which participant this window is actually talking to.
        lblStatus.setText("  " + profile.nameDisplay() + "   " + strWhat
                + "   " + profile.nameHost() + ":" + profile.portLedger());
    }


    /**
     * Opens the dialog and does whatever it produced.
     *
     * OK CONNECTS. The dialog returning a choice IS the instruction to
     * connect, so there is no second press and no state in between where the
     * pane knows a target it has not tried.
     *
     * A DISCOVERY DOCUMENT LEAVES THIS PANE. It describes a topology and the
     * operator's rule for one is a tab per participant, so it goes to the
     * window and the tab set is rebuilt from it - this pane included. A
     * standalone ledger is one participant and stays here.
     */
    void connectPressed() {
        ConnectDialog dlg = new ConnectDialog(SwingUtilities.getWindowAncestor(this),
                auth, lstProfileAll);
        dlg.setVisible(true);

        ConnectDialog.Choice choice = dlg.choice();
        if (choice == null)
            return;

        if (choice.discovery() != null && sinkDiscovery != null) {
            sinkDiscovery.accept(choice.discovery());
            return;
        }
        useStandalone(choice);
    }


    /**
     * Point this pane at ONE node of a discovery document, and connect.
     *
     * @param doc the document
     * @param idxNode which of its nodes, 0-based
     */
    void useNode(Discovery doc, int idxNode) {
        Discovery docHere = doc.onNode(idxNode);

        this.discovery = docHere;
        this.profileChosen = docHere.profile();
        this.authChosen = ProfileAuth.NONE;
        this.generationChosen = null;
        this.strNameTab = docHere.strNameNode();
        showProfileColour(profileChosen, "not connected");
        // THE ONE CALLER THAT STARTS A SESSION. Everything the previous one
        // accumulated is about a participant the operator has just left.
        connect(true);
    }


    /**
     * Point this pane at a standalone ledger, and connect.
     *
     * @param choice what the dialog produced; its discovery is null
     */
    void useStandalone(ConnectDialog.Choice choice) {
        this.discovery = null;
        this.profileChosen = choice.profile();
        this.authChosen = choice.auth() == null ? ProfileAuth.NONE : choice.auth();
        this.generationChosen = choice.generation();
        this.strNameTab = choice.profile() == null ? "participant"
                : choice.profile().nameDisplay();
        showProfileColour(profileChosen, "not connected");
        connect(true);
    }


    /**
     * @param strWhat what to put in the detail pane when there is no
     *        participant to connect to
     */
    void showProblem(String strWhat) {
        showText(strWhat);
    }


    /** @return what the tab strip calls this pane */
    String nameTab() {
        return strNameTab;
    }


    /** @return the participant this pane is pointed at, or null */
    HostProfile profileChosen() {
        return profileChosen;
    }


    /**
     * Connects as whoever the picker names, or as the bootstrap credential
     * when there is not yet a picker to name anybody.
     *
     * THE FIRST CONNECTION IS A CHICKEN AND EGG. The users can only be read
     * from a participant, and the participant can only be reached with a
     * credential - so the first pass uses the credential the dialog produced,
     * reads the users, and fills the picker. Choosing a different one from
     * there mints a token and comes back through here.
     *
     * @param flagFresh whether this is the operator connecting from the
     *        Connect dialog, which is the only thing that empties the
     *        register, the transcript and the ids. A `work as` change is
     *        false: same participant, same session, same identifiers
     */
    private void connect(boolean flagFresh) {
        HostProfile profile = profileChosen;
        if (profile == null)
            return;

        closeClient();
        showProfileColour(profile, "connecting...");

        // Read on the event dispatch thread, used on the worker: the panel's
        // state belongs to the EDT and the file and key reading do not.
        ProfileAuth authNow = authChosen;
        String idUserWanted = tokens != null && tokens.canChoose() ? picker.selected() : null;
        UserTokens_i tokensNow = tokens;
        // WHAT THIS USER MAY READ, taken BEFORE the worker starts, because
        // `parties()` is an admin call and a user that is not an admin is
        // refused it. The rights were read on the connection that listed the
        // users, and they are the answer the participant would have given.
        List<PartyInfo> lstPartyRight = lstPartyInfoOfUser(idUserWanted);

        new SwingWorker<Object[], Void>() {

            @Override
            protected Object[] doInBackground() {
                // only used before there is one. Re-minting on every read
                // would ask the mint for a token nothing had invalidated.
                //
                // `*` GOES THROUGH THE SEAM LIKE ANY OTHER SELECTION. It
                // resolves to a real user there and mints for it; a null
                // comes back only when nothing on the participant is
                // broader than the credential already in hand.
                TokenSource_i source = null;
                if (tokensNow != null && idUserWanted != null)
                    source = tokensNow.sourceFor(idUserWanted);
                if (source == null) {
                    source = discovery == null
                            ? ProfileAuthStore.sourceFor(authNow, profile)
                            : discovery.tokenSource();
                }
                LedgerClient_i clientNew = targetFor().connect(profile, source);
                LedgerInfo infoNew = clientNew.info();

                // parties() is an ADMIN call, and a token minted to read
                // contracts is refused it. Failing the connection here left the
                // operator with a window that displayed nothing while holding a
                // token that could have read everything on the ledger.
                //
                // The token names the parties it grants, so when the
                // participant will not say who they are, ask the credential.
                List<UserInfo> lstUserNew;
                try {
                    lstUserNew = clientNew.users();
                }
                catch (RuntimeException ex) {
                    // NOT FATAL. Listing users is an admin call and a token
                    // minted for `alice` is refused it, which is correct and
                    // is not a reason to refuse the connection. The picker
                    // keeps what it already had.
                    lstUserNew = List.of();
                }

                List<PartyInfo> lstPartyNew;
                String strProblemPartyNew = null;
                try {
                    lstPartyNew = clientNew.parties();
                }
                catch (RuntimeException ex) {
                    strProblemPartyNew = LedgerSnapshot.describe(ex);
                    lstPartyNew = partiesFromToken(source);

                    // AND THEN THE USER'S OWN RIGHTS. With the stack on
                    // AUDIENCE the token carries no party claims at all - the
                    // rights live on the participant, against the `sub` user -
                    // so a minted token names nothing and this was the whole
                    // of `Could not connect` for every non-admin user.
                    if (lstPartyNew.isEmpty())
                        lstPartyNew = lstPartyRight;

                    // A refused party list is survivable ONLY when something
                    // else names parties to read as. When nothing does there
                    // is nothing this window can read, and reporting
                    // "connected" over four empty sections is a worse answer
                    // than the refusal itself.
                    if (lstPartyNew.isEmpty())
                        throw ex;
                }

                return new Object[] { clientNew, infoNew, lstPartyNew,
                        source == null ? "none" : source.describe(), strProblemPartyNew,
                        lstUserNew, source };
            }


            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] arrOut = get();
                    client = (LedgerClient_i) arrOut[0];
                    resolver = new ChainResolver(client);
                    profileActive = profile;

                    LedgerInfo info = (LedgerInfo) arrOut[1];

                    // A PARAMETER IS A VALUE ON ONE PARTICIPANT, and so is
                    // a transcript and so is an id. Carried across a CONNECT
                    // the register would offer party ids and contract ids the
                    // new participant never issued.
                    //
                    // ONLY A CONNECT - operator instruction, 2026-09-09.
                    // `work as` reaches this method too, and emptying the
                    // three of them on that path is the whole of the fault:
                    // the editor kept a script this window had written
                    // itself, in the short form, against a window that had
                    // just thrown away every id that could resolve it.
                    if (flagFresh) {
                        paneCaql.setParams(List.of());
                        paneCaql.clearResult();
                        lstIdSeen.clear();
                    }

                    List<PartyInfo> lstParty = (List<PartyInfo>) arrOut[2];
                    lstPartyHosted = lstIdOf(lstParty);
                    String strAuthFull = (String) arrOut[3];
                    String strProblemParty = (String) arrOut[4];
                    strAuthActive = clip(strAuthFull);
                    lblStatus.setToolTipText("auth: " + strAuthFull);

                    List<UserInfo> lstUser = (List<UserInfo>) arrOut[5];
                    TokenSource_i sourceUsed = (TokenSource_i) arrOut[6];
                    if (!lstUser.isEmpty())
                        lstUserInfo = lstUser;

                    // THE SEAM IS CHOSEN ONCE PER CONNECTION, not once per
                    // window: reconnecting to a standalone ledger after a
                    // Sandbox must not leave a mint behind that would issue
                    // tokens the new participant never heard of.
                    String strUrlMint = discovery == null ? null : discovery.strUrlMintToken();
                    if (strUrlMint != null)
                        tokens = new MintTokens(strUrlMint, lstUserInfo);
                    // AN UNAUTHENTICATED PARTICIPANT STILL HAS USERS, and the
                    // reads are keyed off the SELECTED USER's parties rather
                    // than off any token - so the choice is meaningful with no
                    // credential in it. Without this branch the picker names
                    // `(no credential)`, which is no user, and every pane reads
                    // as nobody: an empty Contracts section on a ledger holding
                    // a fixture.
                    else if (sourceUsed == null && !lstUserInfo.isEmpty())
                        tokens = new OpenUsers(lstUserInfo);
                    else
                        tokens = new FixedToken(sourceUsed);

                    picker.setUsers(tokens.lstUser(),
                            idUserWanted == null ? idUserActive(sourceUsed) : idUserWanted,
                            tokens.canChoose());

                    showProfileColour(profile, "connected on");
                    showText("Connected.\n\nledger id      " + orRefused(info.idLedger())
                            + "\nparticipant id " + orRefused(info.idParticipant())
                            + "\napi version    "
                            + info.versionApi() + "\ngeneration     " + info.generation()
                            + noteParties(strProblemParty, lstParty)
                            + "\n\nPick something on the left, or paste an identifier above.");

                    loadSnapshot();
                }
                catch (Exception ex) {
                    profileActive = null;
                    strAuthActive = "none";
                    showProfileColour(profile, "NOT CONNECTED");
                    showText("Could not connect.\n\n" + describe(ex)
                            + hintAuth(authNow, ex));
                }
            }

        }.execute();
    }


    /**
     * Identity fields degrade to an empty string when the call behind them is
     * refused - getParticipantId needs an admin token and a read-only operator
     * is expected to be turned away from it.
     *
     * A blank line in the result pane is the one rendering that must not
     * happen: it reads as "this participant has no id", which is not a thing
     * that is true of any participant.
     *
     * @param strValue what the client reported
     * @return the value, or why there is not one
     */
    private static String orRefused(String strValue) {
        if (strValue == null || strValue.isBlank())
            return "(not available - the token was refused this)";
        return strValue;
    }


    /**
     * The parties a token grants, when the participant will not list them.
     *
     * Not verification: the signature and the expiry are the participant's
     * business, and every call made with a party from here is authorised by the
     * participant on its own terms. A token that overstates its rights produces
     * entries that fail on use, which is what a revoked right does anyway.
     *
     * @param source the token source in use, may be null
     * @return parties named by the token, empty when it names none
     */
    private static List<PartyInfo> partiesFromToken(TokenSource_i source) {
        if (source == null)
            return List.of();

        List<PartyInfo> lstParty = new ArrayList<>();
        try {
            for (String idParty : TokenParties.readable(source.token())) {
                // flagLocal false: hosting is something the participant knows
                // and a token does not say.
                lstParty.add(new PartyInfo(idParty, null, false));
            }
        }
        catch (RuntimeException ex) {
            return List.of();
        }
        return lstParty;
    }


    /**
     * @param strProblemParty why the participant would not list parties, null
     *        when it did
     * @param lstParty what the picker was given instead
     * @return a line for the result pane, empty when there is nothing to say
     */
    private static String noteParties(String strProblemParty, List<PartyInfo> lstParty) {
        if (strProblemParty == null)
            return "";
        if (lstParty.isEmpty()) {
            return "\n\nThe participant would not list its parties and the token names\nnone: "
                    + strProblemParty;
        }
        return "\n\nParties come from the TOKEN, not the participant, which refused\nto list them: "
                + strProblemParty;
    }


    /**
     * Fills the navigator. Reads as whatever the picker currently has selected,
     * which on a fresh connection is everything.
     */
    private void loadSnapshot() {
        if (client == null)
            return;

        LedgerClient_i clientRead = client;
        List<String> lstPartyRead = lstPartyOfUser();
        navigator.setSnapshot(null);
        showProfileColour(profileActive, "reading ledger...");

        new SwingWorker<Loaded, Void>() {

            @Override
            protected Loaded doInBackground() {
                LedgerSnapshot snapshot = LedgerSnapshot.load(clientRead, lstPartyRead,
                        LedgerSnapshot.CNT_LIMIT_DEFAULT);

                // Packages are read on the SAME pass, and a failure here is
                // carried rather than thrown - for the reason every section of
                // LedgerSnapshot already carries its own: one refused call must
                // not empty a window that could still show everything else.
                try {
                    TypeRegistry_i registryNew =
                            RegistryLoader.load(clientRead, cachePackage, decoderLf);
                    return new Loaded(snapshot, registryNew,
                            MainWindow.count(cachePackage.ids().size(), "package", "packages")
                                    + "   "
                                    + MainWindow.count(registryNew.templates().size(),
                                            "template known", "templates known"));
                }
                catch (RuntimeException ex) {
                    return new Loaded(snapshot, null, "packages: " + LedgerSnapshot.describe(ex));
                }
            }


            @Override
            protected void done() {
                try {
                    Loaded loaded = get();
                    LedgerSnapshot snapshot = loaded.snapshot();
                    registry = loaded.registry();
                    navigator.setSnapshot(snapshot);
                    snapshotShown = snapshot;
                    remember(snapshot);
                    paneCaql.setScriptIfEmpty(AviationScript.strFor(snapshot));
                    paneCaql.setScriptIfEmpty(PharmaScript.strFor(snapshot));
                    showProfileColour(profileActive, summary(snapshot, loaded.strPackages()));
                }
                catch (Exception ex) {
                    showProfileColour(profileActive, "READ FAILED");
                    showText("Could not read the ledger.\n\n" + describe(ex));
                }
            }

        }.execute();
    }


    /**
     * What one Reload produced. The navigator's read and the type registry come
     * off the same connection, so they arrive together and the operator reads
     * ONE status line rather than watching two race.
     *
     * @param snapshot the navigator's read
     * @param registry the registry over the decoded packages, null when they
     *        could not be read
     * @param strPackages what to show about them, counts or the reason
     */
    private record Loaded(LedgerSnapshot snapshot, TypeRegistry_i registry, String strPackages) {}


    /**
     * SEEN and KNOWN are different claims and the line now says which is which.
     * Templates SEEN come from the contracts that were read, so a template with
     * no active contracts is absent; templates KNOWN come from the decoded
     * packages, so it is there. On a fresh ledger the first number is 0 and the
     * second is in the hundreds, and an operator who reads that as a
     * contradiction has been told something wrong.
     *
     * @param snapshot what was read
     * @param strPackages the package counts, or why there are none
     * @return the counts, with the cap called out because a truncated listing
     *         that does not say so is a wrong answer
     */
    private String summary(LedgerSnapshot snapshot, String strPackages) {
        return snapshot.flagCapped()
                ? "connected, READ CAPPED at " + snapshot.cntLimit() + ", on"
                : "connected on";
    }


    /**
     * @return the registry over the participant's decoded packages, null before
     *         a successful read. P4's choice list is what reads it; it is kept
     *         here because it belongs to the connection and dies with it
     */
    TypeRegistry_i registry() {
        return registry;
    }


    /**
     * UNAUTHENTICATED against a participant that was sent no token is not a
     * mystery, and the operator should not have to work out that it was the
     * missing token rather than the address or the port.
     *
     * @param authNow what was used
     * @param ex what came back
     * @return a hint to append, empty when there is nothing useful to say
     */
    private static String hintAuth(ProfileAuth authNow, Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        String strMsg = String.valueOf(cause.getMessage());
        if (!authNow.isNone() || !strMsg.contains("UNAUTHENTICATED"))
            return "";

        return "\n\nNo token was sent. This participant appears to require one:"
                + "\npress the auth button and paste a JWT, or configure the profile's"
                + "\nsettings file.";
    }


    /**
     * A token source describes itself in as much detail as it safely can, which
     * is more than a status bar has room for. The full description stays on the
     * tooltip; nothing anywhere reproduces the token itself.
     *
     * @param strWhat the description
     * @return it, shortened to fit
     */
    private static String clip(String strWhat) {
        if (strWhat == null)
            return "none";
        if (strWhat.length() <= CNT_STATUS_MAX)
            return strWhat;
        return strWhat.substring(0, CNT_STATUS_MAX) + "\u2026";
    }


    /**
     * @param strText what to show in the text pane, which also becomes the
     *        visible pane
     */
    private void showText(String strText) {
        push();
        setPane(strText);
    }


    /**
     * The same, WITHOUT remembering where it came from.
     *
     * A link resolves on a worker and puts "resolving..." up while it runs. If
     * that placeholder went on the stack, Back would land on it and then have
     * to resolve again to get anywhere - so the step is pushed once, when the
     * link is clicked, and the two frames it paints are both plain repaints.
     *
     * @param strText what to show
     */
    private void setPane(String strText) {
        idSelf = null;
        contractShown = null;
        strRawBase = strText == null ? "" : strText;
        strRawActivity = "";
        cntActivityReq++;
        paint();
        ((CardLayout) pnlResult.getLayout()).show(pnlResult, CARD_TEXT);
    }


    /**
     * Remembers what is on screen so a link can be walked back out of.
     *
     * Following a reference is the point of the links, and a reference walked
     * forward with no way back is a pane the operator has to rebuild by hand -
     * worse still for a contract reached through `Created as consequence`,
     * which may not be in the navigator at all.
     */
    private void push() {
        if (strRawBase.isBlank())
            return;

        while (lstBack.size() >= CNT_BACK_MAX) {
            lstBack.removeLast();
        }
        lstBack.push(new PaneState(strRawBase, strRawActivity, contractShown));
        btnBack.setEnabled(true);
    }


    /** Back to the previous pane, exactly as it was, with no ledger call. */
    private void back() {
        if (lstBack.isEmpty())
            return;

        PaneState state = lstBack.pop();
        contractShown = state.contract();
        strRawBase = state.strBase();
        strRawActivity = state.strActivity();
        cntActivityReq++;
        paint();
        ((CardLayout) pnlResult.getLayout()).show(pnlResult, CARD_TEXT);
        btnBack.setEnabled(!lstBack.isEmpty());
    }


    /**
     * Follows a link.
     *
     * The href carries the WHOLE identifier - that is the point of the link
     * text being short - so nothing here re-reads the screen. A party is asked
     * for by name; anything else is a contract id or an update id and the
     * resolver is what tells those apart, since they are hex strings of similar
     * shape and a regular expression guessing between them would present the
     * wrong object with total confidence.
     *
     * @param strHref `kind:value`, as LinkText wrote it
     */
    /**
     * Offers the identifier under the pointer to the clipboard.
     *
     * THE WHOLE VALUE, never what is on screen. `Short ids` displays
     * `00e2[...]5c2f`, which is unusable anywhere else, while the anchor has
     * carried the full id all along - so a copy off this pane pastes into a
     * cURL command whether or not the shortening is on.
     *
     * Both mouse events ask, because the platform decides which of them is
     * the popup trigger and X11 and Windows do not agree.
     *
     * @param ev the mouse event
     */
    private void popupCopy(MouseEvent ev) {
        if (!ev.isPopupTrigger())
            return;

        String strValue = strLinkAt(ev.getPoint());
        if (strValue == null)
            return;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem itemCopy = new JMenuItem("Copy " + ShortIds.text(strValue));
        itemCopy.setToolTipText(strValue);
        itemCopy.addActionListener(evAct -> Toolkit.getDefaultToolkit()
                .getSystemClipboard().setContents(new StringSelection(strValue),
                        null));
        menu.add(itemCopy);
        menu.show(areaResult, ev.getX(), ev.getY());
    }


    /**
     * @param pt where in the pane
     * @return the full identifier of the link under that point, or null when
     *         there is no link there
     */
    private String strLinkAt(Point pt) {
        if (!(areaResult.getDocument() instanceof HTMLDocument doc))
            return null;

        int idxAt = areaResult.viewToModel2D(pt);
        if (idxAt < 0)
            return null;

        Element elem = doc.getCharacterElement(idxAt);
        Object objAnchor = elem.getAttributes().getAttribute(HTML.Tag.A);
        if (!(objAnchor instanceof AttributeSet setAnchor))
            return null;

        Object objHref = setAnchor.getAttribute(HTML.Attribute.HREF);
        if (objHref == null)
            return null;

        // `kind:value`, and a template id carries colons of its own - so the
        // FIRST one separates and the rest belong to the value.
        String strHref = objHref.toString();
        int idxSep = strHref.indexOf(':');
        return idxSep < 0 ? strHref : strHref.substring(idxSep + 1);
    }


    private void openLink(String strHref) {
        LedgerClient_i clientRead = client;
        if (strHref == null || clientRead == null)
            return;

        int idxSep = strHref.indexOf(':');
        String strKind = idxSep < 0 ? LinkText.KIND_REF : strHref.substring(0, idxSep);
        String strRef = idxSep < 0 ? strHref : strHref.substring(idxSep + 1);
        if (strRef.isBlank())
            return;

        push();

        if (LinkText.KIND_PARTY.equals(strKind)) {
            follow(strRef);
            setPane(strParty(strRef));
            self(strRef);
            return;
        }

        // ALREADY READ IS ALREADY RENDERED. A contract the navigator holds is
        // opened from the tree's own item, so a contract reached by clicking a
        // link and the same contract reached by clicking its row are one
        // rendering rather than two - and no second call is made for something
        // the window is already holding.
        NavItem item = navigator.item(strRef);
        if (item != null) {
            follow(strRef);
            openItem(item);
            return;
        }

        if (LinkText.KIND_TEMPLATE.equals(strKind)) {
            follow(strRef);
            setPane(Banner.head("TEMPLATE") + strRef
                    + "\n\nNot seen on this ledger. `Templates seen` lists the templates"
                    + "\nthis read found instances of, and this is not one of them.\n");
            return;
        }

        List<String> lstPartyRead = lstPartyOfUser();
        setPane("resolving " + strRef + " ...");

        new SwingWorker<Found, Void>() {

            @Override
            protected Found doInBackground() {
                return find(clientRead, strRef, lstPartyRead);
            }


            @Override
            protected void done() {
                Found found;
                try {
                    found = get();
                }
                catch (Exception ex) {
                    found = new Found(null, describe(ex));
                }

                follow(strRef);

                if (found.resolved() != null) {
                    open(found.resolved(), strRef);
                    return;
                }

                // A refusal is an answer about the reference, not a failure of
                // the window, so it lands in the pane the click was aimed at
                // rather than in a dialog.
                setPane(Banner.head("COULD NOT OPEN") + strRef + "\n\n"
                        + MainWindow.wrap(found.strProblem()) + strNoParties(lstPartyRead));
            }

        }.execute();
    }


    /**
     * Moves the navigator's selection to what was just opened, or drops it.
     *
     * The tree row and the pane must agree. A link to something the tree holds
     * selects it; a link to something it does not - an archived contract, a
     * party the participant would not list - clears the selection rather than
     * leaving the previous row highlighted beside a pane showing something
     * else.
     *
     * @param strRef what was opened
     */
    private void follow(String strRef) {
        if (!navigator.select(strRef))
            navigator.clear();
    }


    /** What a lookup turned out to be, or why it did not. */
    private record Found(Resolved resolved, String strProblem) {}


    /**
     * Asks the participant directly, rather than through the probe chain.
     *
     * <h2>Why not ChainResolver</h2>
     *
     * The chain is built for a string the operator PASTED, where nothing is
     * known about what it is. A link already carries a whole identifier that
     * this window rendered, so the only open question is which of two id spaces
     * it belongs to - and the chain answers that badly here: it rethrows the
     * FIRST probe failure when nothing resolves, so asking UpdateService for a
     * contract id produces an INVALID_ARGUMENT about the update id space and
     * hides whatever the contract lookup said.
     *
     * Each lookup is tried and each failure is kept. A failure means "not that
     * one", never "stop".
     *
     * <h2>The length only decides the ORDER</h2>
     *
     * Measured on this workspace: an update id is 68 hex characters and a
     * contract id 138. That makes a good first guess and a bad rule, since a
     * participant is free to mint ids this window has never seen - so the other
     * lookup is always tried too.
     *
     * @param clientRead the participant
     * @param strRef the whole identifier
     * @param lstPartyRead parties to read as
     * @return what it was, or every reason it was not found
     */
    private static Found find(LedgerClient_i clientRead, String strRef,
            List<String> lstPartyRead) {
        List<String> lstProblem = new ArrayList<>();
        boolean flagUpdateFirst = strRef.length() == LinkText.CNT_UPDATE;

        for (int cntTry = 0; cntTry < 2; cntTry++) {
            boolean flagUpdate = flagUpdateFirst == (cntTry == 0);
            try {
                Optional<Resolved> found = flagUpdate
                        ? clientRead.tree(strRef, lstPartyRead).map(Resolved.AsUpdate::new)
                        : clientRead.contract(strRef, lstPartyRead).map(Resolved.AsContract::new);
                if (found.isPresent())
                    return new Found(found.get(), null);
            }
            catch (RuntimeException ex) {
                lstProblem.add((flagUpdate ? "as a transaction\n" : "as a contract\n")
                        + describe(ex));
            }
        }

        return lstProblem.isEmpty()
                ? new Found(new Resolved.None(strRef), null)
                : new Found(null, String.join("\n\n", lstProblem));
    }


    /**
     * A party id, read off the id itself.
     *
     * NOTHING IS ASKED OF THE PARTICIPANT. Listing parties is an admin call and
     * is refused on exactly the sessions where a party link is most useful, so
     * a lookup would turn a question the id already answers into a permission
     * error. The hint and the namespace ARE the answer: two parties sharing a
     * namespace live in the same place, and one that does not, does not.
     *
     * @param idParty the whole party id
     * @return the block for the pane
     */
    private static String strParty(String idParty) {
        int idxSep = idParty.indexOf("::");
        if (idxSep < 0)
            return Banner.head("PARTY") + idParty + "\n";

        return Banner.head("PARTY") + idParty
                + "\n\nhint       " + idParty.substring(0, idxSep)
                + "\nnamespace  " + idParty.substring(idxSep + 2)
                + "\n\nRead off the id, not from the participant. The namespace is the"
                + "\nfingerprint of the key that allocated the party: two parties carrying"
                + "\nthe same one were allocated in the same place.\n";
    }


    /**
     * @param lstPartyRead what the read was made as
     * @return the note that names the likeliest reason a lookup was refused,
     *         empty when there is nothing to say
     */
    private static String strNoParties(List<String> lstPartyRead) {
        if (!lstPartyRead.isEmpty())
            return "";

        return "\nThe read was made with NO read-as parties. The participant refused to"
                + "\nlist users, so this window does not know which parties the selected"
                + "\nuser may read as, and a lookup that needs them cannot be made.\n";
    }


    /**
     * @param resolved what the reference turned out to be
     * @param strRef what was asked for, so the transaction view can mark the
     *        row that answers it
     */
    private void open(Resolved resolved, String strRef) {
        if (resolved instanceof Resolved.AsUpdate val) {
            treeShown = val.tree();
            showTree(val.tree(), strRef);
            return;
        }

        setPane(text.text(resolved));
        if (resolved instanceof Resolved.AsContract val)
            armContract(val.contract());
    }


    /**
     * Puts the raw text on screen in whichever form is selected.
     *
     * TEN BLANK LINES UNDER IT - operator instruction. The last line of a
     * block sat on the bottom edge of the viewport, which reads as text cut
     * off rather than as text that ended. Each carries a space, because a
     * run of newlines before `</pre>` is whitespace an HTML renderer is
     * free to drop.
     *
     * THE SHORTENING IS THE LAST THING THAT HAPPENS. What every renderer
     * produced is kept exactly as it was, so turning `Short ids` off restores
     * the pastable form without going near the ledger, and no renderer has to
     * know that a display preference exists.
     */
    private void paint() {
        String strRaw = (strRawActivity.isEmpty() ? strRawBase
                : strRawBase + "\n" + strRawActivity) + STR_TAIL;
        areaResult.setText(LinkText.html(strRaw, chkShortIds.isSelected(), idSelf));
        areaResult.setCaretPosition(0);
    }


    /** Both detail panes follow the box, so one contract reads one way. */
    private void shortIds() {
        paint();
        paneTx.setShortIds(chkShortIds.isSelected());
        paneCaql.setShortIds(chkShortIds.isSelected());
    }


    /**
     * Remembers the contract now on screen and reads what happened to it.
     *
     * ALWAYS, with no control in front of it - operator instruction,
     * 2026-09-09. The scan is one bounded stream over the contract's own
     * lifetime, capped at {@link ContractActivity#CNT_SCAN_DEFAULT}
     * transactions, which is not a cost worth a checkbox on the stacks this
     * window is pointed at. The one shape that is not cheap is a long-lived
     * contract on a participant with a long history, because an active one
     * has no upper offset and the window runs to the ledger end; the cap is
     * what stops it, and the pane says so when it bites.
     *
     * @param contract the contract being shown
     */
    private void armContract(Contract contract) {
        contractShown = contract;
        idSelf = contract == null ? null : contract.idContract();
        paint();
        if (contract != null)
            loadActivity();
    }


    /**
     * Reads the contract's activity and puts it BELOW what is already on
     * screen, rather than replacing it.
     *
     * <h2>Why the window is the contract's lifetime</h2>
     *
     * The contract carries the offset it was created at, and an archived one
     * carries the offset it died at. Those two are the only window that is
     * neither a guess nor the whole ledger: everything before the create cannot
     * mention the contract, and nothing after the archive can. An active
     * contract has no upper end, so the scan runs to the ledger end, which the
     * client resolves rather than following the stream forever.
     *
     * The read is made as the SELECTED user's parties, the same ones every
     * other pane in this window reads as. A pane beside them showing events
     * their user cannot see would not be an inconsistent rendering, it would be
     * the window saying two different things about one ledger.
     */
    private void loadActivity() {
        Contract contract = contractShown;
        if (client == null || contract == null)
            return;

        LedgerClient_i clientRead = client;
        List<String> lstPartyRead = lstPartyOfUser();
        String idUser = idUserPicked();
        String offsetTo = contract.offsetArchived().orElse(null);
        int cntThis = ++cntActivityReq;

        strRawActivity = "reading activity...";
        paint();

        new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() {
                UpdateScan scan = clientRead.updates(contract.offsetCreated(), offsetTo,
                        lstPartyRead, ContractActivity.CNT_SCAN_DEFAULT);
                return ContractActivity.text(contract, scan, idUser);
            }


            @Override
            protected void done() {
                // A read the operator has moved on from. Dropped in silence:
                // reporting it would be reporting a question nobody asked.
                if (cntThis != cntActivityReq)
                    return;

                String strActivity;
                try {
                    strActivity = get();
                }
                catch (Exception ex) {
                    // A target that declares no bounded scan arrives here, and
                    // so does a refused read. Both are answers about the
                    // question, not failures of the window, so the contract
                    // stays on screen with the reason under it.
                    strActivity = ContractActivity.textProblem(contract, idUser, describe(ex));
                }

                strRawActivity = strActivity;
                paint();
            }

        }.execute();
    }




    /**
     * @param tx the transaction
     * @param strRefFound what was searched for, so the row that answers it is
     *        marked rather than left for the operator to find
     */
    private void showTree(TxTree tx, String strRefFound) {
        treeShown = tx;
        paneTx.setTree(tx, strRefFound);
        ((CardLayout) pnlResult.getLayout()).show(pnlResult, CARD_TREE);
    }


    /**
     * Back to the exact form. The tree is readable; the JSON is what pastes
     * into something else, and neither replaces the other.
     */
    private void showJson() {
        if (treeShown != null)
            showText(text.text(new Resolved.AsUpdate(treeShown)));
    }


    /**
     * Runs the CaQL tab's script against THIS connection.
     *
     * <h2>The identity is the session's, and there is no second one</h2>
     *
     * The client was built for the user the picker names, so a script executes
     * with exactly the rights every pane in the Ledger tab reads with.
     * Changing the user rebuilds the connection, and the next run uses the new
     * one. Nothing here mints, chooses or caches a credential.
     *
     * <h2>Parsed on the event thread, run on a worker</h2>
     *
     * Parsing is local and instant and its failure means nothing was sent -
     * which is the message the operator wants immediately, without a worker
     * round trip. The submission is a ledger call and never runs here.
     *
     * <h2>The registry is REQUIRED</h2>
     *
     * A run resolves template and choice names against the packages this
     * window decoded. When that read failed, the honest answer is that CaQL
     * cannot run rather than a resolution error per statement.
     *
     * @param strScript what the editor holds
     */
    private void runCaql(String strScript) {
        if (client == null) {
            paneCaql.setResult(CaqlResults.textProblem("Not connected."
                    + " Use Connection > Connect... first."));
            return;
        }
        if (registry == null) {
            paneCaql.setResult(CaqlResults.textProblem("The packages on this participant could"
                    + " not be read, so template and choice names cannot be resolved. Press"
                    + " Reload in the Ledger tab."));
            return;
        }

        // SHORT IDS GO BACK TO LONG ONES BEFORE ANYTHING IS PARSED. The
        // editor may hold the form the panes display, and the participant
        // must never be asked about an abbreviation.
        List<String> lstKnown = lstIdKnown();
        LongIds.Result expanded = LongIds.expand(strScript, lstKnown);
        if (expanded.strProblem() != null) {
            // WHAT THE WINDOW IS HOLDING, said out loud. A refusal that names
            // the token but not the size of the set it was matched against
            // cannot be told apart from a window that has read nothing.
            paneCaql.setResult(CaqlResults.textProblem(expanded.strProblem()
                    + "\n\nThis window is holding " + lstKnown.size()
                    + " identifiers from this participant."));
            return;
        }

        List<Stmt> lstStmt;
        try {
            // BEFORE anything is sent. A script with a syntax error at
            // statement 9 must not leave the participant holding statements 1
            // to 8.
            lstStmt = CaqlParser.parse(expanded.strText());
        }
        catch (CaqlException ex) {
            paneCaql.setResult(CaqlResults.textParse(ex));
            // A missing ';' names the line it belongs at the end of; anything
            // else lands at the start of the statement.
            if (ex.numLineFix() > 0)
                paneCaql.caretToEnd(ex.numLineFix());
            else
                paneCaql.caretTo(ex.numLine());
            return;
        }

        LedgerClient_i clientRun = client;
        TypeRegistry_i registryRun = registry;
        String nameProfile = profileActive == null ? "unnamed" : profileActive.nameDisplay();
        // WHO IS SUBMITTING, taken from the picker rather than hard-coded. On
        // v2 this lands in `Commands.user_id`, which is a participant user and
        // not a label, and a name no user answers to is refused
        // PERMISSION_DENIED with the reason redacted.
        String idUserRun = idUserPicked();
        RunConfig config = RunConfig.ofRun(idUserRun);
        // READ ON THE EVENT THREAD, used on the worker. The register is a
        // component's state and is not touched from another thread.
        List<Binding> lstSeed = paneCaql.lstParam();
        paneCaql.setBusy(true);
        paneCaql.progressStart(CaqlResults.strProgressHead(lstStmt.size(), idUserRun));

        // PROGRESS LINE BY LINE - his instruction, 2026-10-04. Each statement
        // is shown running and then replaced by how it ended and how long it
        // took; the transcript replaces the lot when the run is over. The
        // lines are made on the worker and painted on the event thread.
        new SwingWorker<Transcript, ProgressLine>() {

            @Override
            protected Transcript doInBackground() {
                // The registry is SNAPSHOTTED for the run, which is the
                // instance this window holds: a package uploaded midway must
                // not make statement 12 resolve differently from statement 3.
                Runner runner = new Runner(clientRun, registryRun, config,
                        new AuditLog(), nameProfile);
                // What earlier runs bound, put back before this one starts.
                runner.restore(lstSeed);
                runner.useProgress(new RunProgress_i() {

                    private long nNanoStart;

                    @Override
                    public void started(int numStmt, int cntStmt, Stmt stmt) {
                        nNanoStart = System.nanoTime();
                        publish(new ProgressLine(
                                CaqlResults.strProgressRunning(numStmt, cntStmt, stmt), true));
                    }


                    @Override
                    public void finished(int numStmt, int cntStmt, Entry entry) {
                        long nMs = (System.nanoTime() - nNanoStart) / 1_000_000L;
                        publish(new ProgressLine(
                                CaqlResults.strProgressDone(numStmt, cntStmt, entry, nMs), false));
                    }
                });
                return runner.run(strScript, lstStmt);
            }


            @Override
            protected void process(List<ProgressLine> lstLine) {
                for (ProgressLine line : lstLine) {
                    if (line.flagRunning())
                        paneCaql.progressRunning(line.strLine());
                    else
                        paneCaql.progressDone(line.strLine());
                }
            }


            @Override
            protected void done() {
                paneCaql.setBusy(false);
                Transcript transcript;
                try {
                    transcript = get();
                }
                catch (Exception ex) {
                    paneCaql.setResult(CaqlResults.textProblem(describe(ex)));
                    return;
                }

                paneCaql.setResult(CaqlResults.text(transcript));
                // The run was seeded from the register, so what it ended
                // holding carries the surviving entries as well as its own.
                paneCaql.setParams(transcript.lstBinding());
                refreshAfter(transcript);
            }

        }.execute();
    }


    /**
     * One line of a run's progress, on its way from the worker to the pane.
     *
     * @param strLine the line
     * @param flagRunning true for a statement that is running now, whose line
     *        the next one replaces
     */
    private record ProgressLine(String strLine, boolean flagRunning) {
    }


    /**
     * Re-reads the ledger when a run changed it.
     *
     * THE EXISTING MECHANISM, not a CaQL-specific one: the navigator has one
     * way of becoming current and this is it. Only a COMMITTED statement earns
     * the read - a validate, a query and a locally refused statement changed
     * nothing, and re-scanning the ACS after each of those would turn every
     * press of Run into a stream against the participant.
     *
     * @param transcript what the run produced
     */
    private void refreshAfter(Transcript transcript) {
        for (Entry entry : transcript.lstEntry()) {
            if (entry.status() == RunStatus.COMMITTED) {
                loadSnapshot();
                return;
            }
        }
    }


    /**
     * Follows a link in the CaQL results, which lands in `Detail`.
     *
     * The point of the two panes is that they are two views of ONE ledger,
     * and the moment that becomes visible is when a contract a script just
     * created opens in the pane that shows contracts. `Detail` is brought
     * forward FIRST so the pane the link fills is the one the operator is
     * looking at when it fills. The navigator does not move: it is outside
     * the tabs and stays on screen through the whole gesture.
     *
     * @param strHref `kind:value`, as LinkText wrote it
     */
    private void openFromCaql(String strHref) {
        tabs.setSelectedIndex(0);
        openLink(strHref);
    }


    /**
     * Every id this window is holding, which is what a short id in the
     * editor is matched against.
     *
     * @return the parties and the contracts of the last read
     */
    /**
     * @param strScript the editor's text
     * @return it with every short id this window can place put back, and
     *         the rest of it untouched
     */
    private String strExpanded(String strScript) {
        return LongIds.textBest(strScript, lstIdKnown());
    }


    /**
     * Everything the read named, kept so a short id can be put back.
     *
     * THE PARTY LIST IS AN ADMIN CALL AND IS NORMALLY REFUSED.
     * `LedgerSnapshot.load` fills `lstParty` from `client.parties()`, so
     * under any role user the navigator shows `Parties (-)` and this would
     * learn no party at all - which left the editor holding a script whose
     * party ids resolved to nothing on every user but the broad ones.
     *
     * THE CONTRACTS NAME THE SAME PARTIES. A signatory and an observer are
     * party ids on a contract this window was permitted to read, so they
     * cost no extra call, need no admin credential and do not depend on an
     * earlier connection having been made with one.
     *
     * @param snapshot what a read returned, may be null
     */
    private void remember(LedgerSnapshot snapshot) {
        if (snapshot == null)
            return;

        for (PartyInfo party : snapshot.lstParty()) {
            keep(party.idParty());
        }
        for (Contract contract : snapshot.lstContract()) {
            keep(contract.idContract());
            for (String idParty : contract.lstSignatory()) {
                keep(idParty);
            }
            for (String idParty : contract.lstObserver()) {
                keep(idParty);
            }
        }
    }


    /**
     * @param idAt an identifier the read named, ignored when blank or already
     *        held
     */
    private void keep(String idAt) {
        if (idAt != null && !idAt.isBlank() && !lstIdSeen.contains(idAt))
            lstIdSeen.add(idAt);
    }


    private List<String> lstIdKnown() {
        List<String> lstOut = new ArrayList<>(lstPartyHosted);
        for (String idSeen : lstIdSeen) {
            if (!lstOut.contains(idSeen))
                lstOut.add(idSeen);
        }
        if (snapshotShown == null)
            return lstOut;

        for (PartyInfo party : snapshotShown.lstParty()) {
            if (!lstOut.contains(party.idParty()))
                lstOut.add(party.idParty());
        }
        for (Contract contract : snapshotShown.lstContract()) {
            if (!lstOut.contains(contract.idContract()))
                lstOut.add(contract.idContract());
        }
        return lstOut;
    }


    private void showItem(NavItem item) {
        push();
        openItem(item);
    }


    /**
     * Renders a navigator item, WITHOUT remembering where it came from - the
     * caller decides that, because a link and a click arrive here by different
     * routes and only one of them is a step the operator can go back from.
     *
     * @param item what to show
     */
    private void openItem(NavItem item) {
        // THE PANE THAT FILLS IS THE PANE IN FRONT - operator instruction,
        // 2026-09-09. Selecting a row while CaQL is showing used to render
        // into a tab nobody was looking at, so the click did nothing that
        // could be seen.
        tabs.setSelectedIndex(0);
        setPane(textNav.text(item, registry));
        ((CardLayout) pnlResult.getLayout()).show(pnlResult, CARD_TEXT);

        // WHAT THE PANE IS ABOUT NEVER LINKS TO ITSELF. Every kind of node
        // prints its own identifier at the top, and an identifier offering to
        // open the thing already on screen is a control that does nothing.
        switch (item) {
            case NavItem.Ct val -> armContract(val.contract());
            case NavItem.Party val -> self(val.party().idParty());
            case NavItem.Template val -> self(val.group().idTemplate().toString());
            default -> self(null);
        }
    }


    /**
     * @param idShown the identifier the pane is about, null when it is about
     *        none
     */
    private void self(String idShown) {
        idSelf = idShown;
        paint();
    }


    /**
     * The parties a named user may read as, as {@link PartyInfo}.
     *
     * NO DISPLAY NAME AND NOT LOCAL. Both are properties the participant
     * reports and this is the path where the participant refused to report
     * anything, so inventing them would be worse than leaving them empty -
     * the id is what every pane keys off.
     *
     * @param idUser who, may be null
     * @return the parties; empty when the user is unknown here
     */
    private List<PartyInfo> lstPartyInfoOfUser(String idUser) {
        List<PartyInfo> lstOut = new ArrayList<>();
        if (idUser == null)
            return lstOut;

        for (UserInfo user : lstUserInfo) {
            if (!idUser.equals(user.idUser()))
                continue;
            addParty(lstOut, user.lstPartyRead());
            addParty(lstOut, user.lstPartyAct());
            addParty(lstOut, user.idPartyPrimary() == null
                    ? List.of() : List.of(user.idPartyPrimary()));
        }
        return lstOut;
    }


    /**
     * @param lstInto where they go
     * @param lstId the party ids to add, may be null
     */
    private static void addParty(List<PartyInfo> lstInto, List<String> lstId) {
        if (lstId == null)
            return;
        for (String idParty : lstId) {
            if (idParty == null || idParty.isBlank())
                continue;
            boolean flagSeen = false;
            for (PartyInfo party : lstInto) {
                if (party.idParty().equals(idParty))
                    flagSeen = true;
            }
            if (!flagSeen)
                lstInto.add(new PartyInfo(idParty, null, true));
        }
    }


    /**
     * The parties the selected user may read as.
     *
     * A USER'S RIGHTS, not a selection. The picker names one user and the
     * participant decides what that user can see; asking for a party it holds
     * no right to is refused as a WHOLE - which is what happened for a session
     * with a multi-select of every party the participant hosted.
     *
     * The primary party counts. A user created with one and no explicit
     * `CanReadAs` can still read as it, and leaving it out would produce an
     * empty read that looked like an empty ledger.
     *
     * @return the parties, deduplicated; empty when no user is selected
     */
    /**
     * The picker's selection, which is a PARTICIPANT USER and nothing else.
     *
     * It used to have to strip a `*` entry that named no user, because a
     * submission's `user_id` the participant does not recognise is refused
     * PERMISSION_DENIED with the reason redacted - and stripping it sent an
     * EMPTY `user_id`, which is refused the same way. The entry is gone, so
     * whatever is selected is a name the participant answers to.
     *
     * @return the selected user, or null when nothing is selected
     */
    private String idUserPicked() {
        return picker.selected();
    }


    /**
     * @param lstParty what the participant reported
     * @return their ids, in the order given
     */
    private static List<String> lstIdOf(List<PartyInfo> lstParty) {
        List<String> lstOut = new ArrayList<>();
        for (PartyInfo party : lstParty) {
            lstOut.add(party.idParty());
        }
        return lstOut;
    }


    /**
     * @return the parties the selected user may read as; every hosted party
     *         when the participant reported no user to select. That last case
     *         is the only thing the `*` entry did that a named user cannot,
     *         and dropping the entry without it would leave a ledger with no
     *         users reading as nobody.
     */
    private List<String> lstPartyOfUser() {
        String idUser = picker.selected();
        if (idUser == null)
            return lstPartyHosted;

        List<String> lstOut = new ArrayList<>();
        for (UserInfo user : lstUserInfo) {
            if (!idUser.equals(user.idUser()))
                continue;
            addAll(lstOut, user.lstPartyRead());
            addAll(lstOut, user.lstPartyAct());
            if (user.idPartyPrimary() != null && !user.idPartyPrimary().isBlank()
                    && !lstOut.contains(user.idPartyPrimary())) {
                lstOut.add(user.idPartyPrimary());
            }
        }
        return lstOut;
    }


    /**
     * @param lstInto where they go
     * @param lstFrom what to add, may be null
     */
    private static void addAll(List<String> lstInto, List<String> lstFrom) {
        if (lstFrom == null)
            return;
        for (String strValue : lstFrom) {
            if (!lstInto.contains(strValue))
                lstInto.add(strValue);
        }
    }


    /**
     * @param sourceUsed the credential a connection was made with
     * @return the user it names, or null when it names none
     */
    private static String idUserActive(TokenSource_i sourceUsed) {
        if (sourceUsed == null)
            return null;
        String idUser = new FixedToken(sourceUsed).lstUser().get(0);
        return FixedToken.STR_USER_UNKNOWN.equals(idUser) ? null : idUser;
    }





    /**
     * A Canton error code is usually the whole answer, so it is not buried in a
     * stack trace the operator has to go find in a log.
     */
    private static String describe(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        if (cause instanceof LedgerException lex && lex.getCodeError() != null)
            return lex.getCodeError() + "\n\n" + lex.getMessage();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }


    private void closeClient() {
        closeQuietly();
        navigator.setSnapshot(null);
    }


    /**
     * Releases the channel and touches NO component.
     *
     * This is what the shutdown hook runs, and a hook runs on its own thread.
     * Emptying the navigator from there would be a Swing call off the event
     * dispatch thread during teardown, which is a hang or an exception at the
     * exact moment nobody is watching.
     *
     * Idempotent: the hook and dispose may both reach it.
     */
    void closeQuietly() {
        LedgerClient_i clientOld = client;
        client = null;
        resolver = null;
        profileActive = null;

        if (clientOld == null)
            return;

        try {
            clientOld.close();
        }
        catch (RuntimeException ex) {
            // Closing a channel that is already gone is not worth a dialog.
        }
    }


    /**
     * Releases this pane's connection. The window calls it on every pane when
     * it goes, and the shutdown hook reaches the same method.
     */
    void close() {
        closeClient();
    }


    /**
     * Which client speaks to the participant this window is pointed at.
     *
     * TWO targets can now share a classpath. The collision that made that
     * impossible was between the v1 bindings jar and the Canton jar, and the
     * v2 client carries neither - it speaks through descriptors the
     * participant serves. So the question stopped being "which one did this
     * build ship" and became "which one does THIS participant speak".
     *
     * A Sandbox answers it in its discovery document. A catalogue host does
     * not, and this refuses rather than guessing: connecting a v1 client to a
     * v2 participant fails as UNIMPLEMENTED on every call, which reads as a
     * broken tool rather than as the wrong client.
     *
     * @return the target to connect with
     * @throws LedgerException when the generation cannot be established
     */
    private Target_i targetFor() {
        if (discovery != null) {
            return Targets.byGeneration(discovery.generation())
                    .orElseThrow(() -> new LedgerException("this build hosts no client for "
                            + discovery.generation() + ", which is what "
                            + discovery.strVersion() + " speaks"));
        }

        // THE DIALOG ASKED. A standalone target now carries the generation
        // it was said to speak, which is what a catalogue line never did and
        // what made this method refuse a multi-client build.
        if (generationChosen != null) {
            return Targets.byGeneration(generationChosen)
                    .orElseThrow(() -> new LedgerException("this build hosts no client for "
                            + generationChosen));
        }

        List<Target_i> lstTarget = Targets.all();
        if (lstTarget.size() == 1)
            return lstTarget.get(0);
        if (lstTarget.isEmpty())
            throw new LedgerException("no ledger client is registered on this classpath");

        throw new LedgerException("this build hosts clients for more than one Ledger API"
                + " generation and a catalogue line does not say which one '"
                + (profileActive == null ? "this participant" : profileActive.nameDisplay())
                + "' speaks. Open the window on a Sandbox with --discovery, or connect to it"
                + " from a build hosting one client");
    }



}
