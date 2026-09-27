// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.VersionId;
import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.sandbox.app.AuthStart;
import com.raposza.jwt.TokenShape;
import com.raposza.runtime.settings.RaposzaSettings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The JWT tab: where the token service is, which ledger users exist, and a way
 * into the API.
 *
 * <h2>There is no minting form here, on purpose</h2>
 *
 * A Swing form over a REST API is a second, worse client for it: it offers only
 * the subset somebody wired up, it goes stale the moment the API gains a field,
 * and it teaches nobody how to call the thing from a script. Swagger UI is
 * generated from the controllers, so it is complete by construction and it
 * issues the requests as well as describing them.
 *
 * <h2>The users come off the ledger</h2>
 *
 * `GET /v2/users` on the participant's own JSON Ledger API, with a token this
 * service mints for `participant_admin`. That is the vendor's documented path
 * and response shape, and it is the only source that includes users a Daml
 * script created after the start - which is the whole reason a Refresh exists
 * rather than a list fixed when the stack came up.
 *
 * Author Claude/bentzn
 */
public final class JwtPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How often the header re-reads the process state. */
    private static final int N_MS_POLL = 1000;

    private static final Duration DUR_HTTP = Duration.ofSeconds(10);

    /**
     * The user Canton creates itself. It is on every participant, so it is
     * always offered and it is what the users query authenticates as.
     */
    /**
     * The user a fresh participant always has. PACKAGE-VISIBLE because the
     * discovery document falls back to it: a Sandbox started without
     * `--user-id` provisions no ledger user, and a hand-over that then
     * published no way to get a token would be no hand-over at all.
     */
    static final String STR_USER_ADMIN = "participant_admin";


    private final LogPane log = new LogPane();

    private final JLabel lblStatus = new JLabel("not started");

    private final JButton btnSwagger = new JButton("Swagger");

    /**
     * The provider this window points at, or empty to run one of its own.
     *
     * ON THIS TAB, not on Settings - operator instruction, 2026-09-21. It is
     * stored machine-wide beside the mint port, because it outlives a window
     * and a headless run reads it too; but it is READ AND CHANGED here, where
     * the provider is the thing on the screen.
     */
    private final JTextField fldUrlOidc = new JTextField(34);

    private final JButton btnUrlApply = new JButton("Apply");

    private final JComboBox<String> cboUser = new JComboBox<>();

    private final JButton btnRefresh = new JButton("Refresh users");

    private final JButton btnMint = new JButton("Mint JWT");

    /**
     * WHAT THE PARTICIPANT CHECKS, shown where a token is made - his
     * instruction, 2026-09-23. A client outside this window - the Workbench's
     * standalone form, Postman - has to put this exact audience (or scope)
     * into its token request, and until now it was only on the Sandbox tab's
     * form, beside nothing that mints.
     */
    private final JLabel lblAudience = new JLabel("Audience");

    private final JTextField fldAudience = new JTextField(34);

    private final JButton btnAudienceCopy = new JButton("Copy");

    private final JTextArea areaToken = new JTextArea(5, 40);

    private final transient JwtMintProcess mint = new JwtMintProcess(this::lineArrived);

    private final transient HttpClient client = HttpClient.newBuilder()
            .connectTimeout(DUR_HTTP)
            .build();

    private final transient ObjectMapper mapper = new ObjectMapper();

    private final transient Timer timerPoll;

    /** Where a one-line notice goes when it belongs in front of the reader. */
    private transient java.util.function.Consumer<String> sinkNotice;

    /** Told where the key set is, so the Sandbox form can follow it. */
    private transient java.util.function.Consumer<String> sinkProvider;

    private final JTabbedPane tabsInner = new JTabbedPane();

    /** The last line that looked like a reason, for the header. */
    private transient String strLastFatal;

    /**
     * Why there is no service, or null when there is one. A missing jar
     * used to be reported HERE and nowhere else, which left the Sandbox
     * tab waiting a full minute for a JWKS from a process that had never
     * been launched, and then blaming the timeout.
     */
    private transient String strWhyNoService;

    /** True while a discovery read is out, so only one ever is. */
    private transient boolean flagDiscovering;

    /**
     * Why the two minting controls are off against a foreign provider. It goes
     * on the buttons rather than in the log, because that is where somebody
     * looks when a button will not press.
     */
    private static final String STR_WHY_EXTERNAL = "This mints through /mint.txt,"
            + " which is this project's own endpoint. No conforming OpenID Provider"
            + " serves it, so it is unavailable against an external one.";

    /** What the running participant verifies, or null when nothing is up. */
    private transient AuthSettings auth;

    private transient VersionId version;

    /** The participant node name, for the audience convention. */
    private transient String nameParticipant;

    /** Where the participant serves the JSON Ledger API, or 0 for unknown. */
    private transient int nPortJsonApi;


    public JwtPane() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(GuiTheme.borderScaled(GuiTheme.N_GAP, 0, 0, 0));

        lblStatus.setForeground(GuiTheme.COL_STOPPED);
        // EDITABLE. Refresh reads what the ledger has; a user about to be
        // created still has to be typeable.
        cboUser.setEditable(true);
        cboUser.addItem(STR_USER_ADMIN);

        btnSwagger.addActionListener(evt -> swaggerRequested());
        fldUrlOidc.setText(RaposzaSettings.current().strUrlOidc());
        fldUrlOidc.addActionListener(evt -> urlApplyRequested());
        btnUrlApply.addActionListener(evt -> urlApplyRequested());
        btnRefresh.addActionListener(evt -> refreshRequested());
        btnMint.addActionListener(evt -> mintRequested());
        fldAudience.setEditable(false);
        fldAudience.setText(STR_AUDIENCE_NONE);
        btnAudienceCopy.addActionListener(evt -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(fldAudience.getText()), null));
        areaToken.setEditable(false);
        areaToken.setLineWrap(true);
        areaToken.setWrapStyleWord(false);

        tabsInner.addTab("Service", buildServiceTab());
        tabsInner.addTab("Log", log);
        add(tabsInner, BorderLayout.CENTER);

        mint.useExitSink(this::exited);

        timerPoll = new Timer(N_MS_POLL, evt -> poll());
        timerPoll.setRepeats(true);
        timerPoll.start();
    }


    /**
     * What the stack that is coming up verifies, and where it serves JSON.
     *
     * @param authNew the settings the Sandbox tab holds, or null for none
     * @param nameParticipantNew the participant node name
     * @param nPortJsonApiNew the JSON Ledger API port, or 0 when there is none
     */
    /**
     * @param sinkNew told, in a few words, about anything worth knowing on
     *        another tab - a token that reached the clipboard is the case it
     *        exists for. Null for none
     */
    public void useNoticeSink(java.util.function.Consumer<String> sinkNew) {
        this.sinkNotice = sinkNew;
    }


    /**
     * @param sinkNew told the key set url whenever the provider this window
     *        points at is settled - on a change, and when a foreign provider's
     *        discovery document comes back. Null for none
     */
    public void useProviderSink(java.util.function.Consumer<String> sinkNew) {
        this.sinkProvider = sinkNew;
        announceProvider();
    }


    /**
     * Tells the sink where keys are, once that is a fact rather than a guess.
     *
     * NOTHING IS ANNOUNCED WHILE A FOREIGN PROVIDER IS UNREAD. `strUrlJwks`
     * falls back to this project's own path when no discovery document has
     * been read, and handing that to the Sandbox form would put a guessed
     * address into a participant's configuration - which is the failure this
     * whole sink exists to stop.
     */
    private void announceProvider() {
        java.util.function.Consumer<String> sinkHere = sinkProvider;
        if (sinkHere != null && JwtMintProcess.isDiscovered())
            sinkHere.accept(JwtMintProcess.strUrlJwks());
    }


    public void useAuth(AuthSettings authNew, VersionId versionNew,
            String nameParticipantNew, int nPortJsonApiNew) {
        this.auth = authNew;
        this.version = versionNew;
        this.nameParticipant = nameParticipantNew;
        this.nPortJsonApi = nPortJsonApiNew;
        showAudience(authNew, nameParticipantNew);
    }


    /** What the audience row says before a stack has told it anything. */
    static final String STR_AUDIENCE_NONE = "shown once a stack is started";

    /** And what it says for a participant that checks nothing. */
    static final String STR_AUDIENCE_WILDCARD = "none - the participant checks no token";


    /**
     * THE SAME RULE THE DISCOVERY DOCUMENT PUBLISHES - `AuthStart.mapOidcFor`,
     * one spelling - so the row cannot say one thing while a token is minted
     * for another.
     *
     * @param authHere what the participant verifies, or null
     * @param strParticipant the node name the audience convention is built on
     * @return the row's label and its value
     */
    static String[] arrAudience(AuthSettings authHere, String strParticipant) {
        if (authHere == null)
            return new String[] { "Audience", STR_AUDIENCE_NONE };
        Map<String, Object> mapOidc = AuthStart.mapOidcFor(authHere, STR_USER_ADMIN,
                strParticipant);
        if (mapOidc.containsKey("scope"))
            return new String[] { "Scope", String.valueOf(mapOidc.get("scope")) };
        if (mapOidc.containsKey("audience"))
            return new String[] { "Audience", String.valueOf(mapOidc.get("audience")) };
        return new String[] { "Audience", STR_AUDIENCE_WILDCARD };
    }


    private void showAudience(AuthSettings authHere, String strParticipant) {
        String[] arrRow = arrAudience(authHere, strParticipant);
        SwingUtilities.invokeLater(() -> {
            lblAudience.setText(arrRow[0]);
            fldAudience.setText(arrRow[1]);
            fldAudience.setCaretPosition(0);
        });
    }


    /**
     * Starts the service. A missing jar is REPORTED and nothing more: the
     * window must open whether or not this module has been packaged.
     */
    public void startService() {
        if (mint.isRunning())
            return;

        this.strLastFatal = null;
        this.strWhyNoService = null;
        if (!timerPoll.isRunning())
            timerPoll.start();

        // NOTHING IS STARTED AGAINST AN EXTERNAL PROVIDER, and saying so is
        // the whole of what this tab does in that mode. The service is
        // somebody else's; this window reports it and reads its document.
        if (JwtMintProcess.isExternal()) {
            log.append("using the external OpenID Provider at "
                    + JwtMintProcess.strUrlBase() + " - nothing is started here");
            discoverLater();
            return;
        }

        try {
            mint.start();
            Path fileJar = mint.fileJar();
            log.append("starting " + fileJar);
            // SHOWN PLAINLY - the Sandbox is for test, and every password it
            // gives the provider is the same one.
            log.append("admin " + JwtMintProcess.STR_ADMIN_USER + " / "
                    + JwtMintProcess.STR_PASSWORD + " at " + JwtMintProcess.strUrlBase() + "/ui");
        }
        catch (RuntimeException ex) {
            this.strWhyNoService = String.valueOf(ex.getMessage());
            lblStatus.setText("not running - no jar");
            lblStatus.setForeground(GuiTheme.COL_BAD);
            log.append(String.valueOf(ex.getMessage()));
        }
    }


    /**
     * Why a start cannot proceed, or null when it can.
     *
     * READ ON THE EVENT THREAD, BEFORE THE PARTICIPANT IS CONFIGURED. The only
     * condition it reports is an external provider whose discovery document
     * has not been read: without `jwks_uri` the key set url is a guess, and a
     * guess reaches the participant as a start that times out on a 404.
     *
     * @return a sentence for the operator, or null
     */
    public String strWhyNoProvider() {
        if (JwtMintProcess.isDiscovered())
            return null;
        return "The external OpenID Provider at " + JwtMintProcess.strUrlBase()
                + " has not answered "
                + JwtMintProcess.STR_PATH_DISCOVERY + ", so the key set the"
                + " participant would fetch is not known.\n\nStart it, or clear"
                + " " + com.raposza.runtime.settings.RaposzaSettings.STR_KEY_URL_OIDC
                + " on the Settings tab to have this window run a provider of its"
                + " own.";
    }


    /**
     * Reads the external provider's document off the event thread.
     *
     * ONE AT A TIME. The poll runs every second and a failed read costs a
     * connect timeout, so a second attempt is not begun while the first is
     * still out.
     */
    private void discoverLater() {
        if (flagDiscovering || JwtMintProcess.isDiscovered())
            return;

        this.flagDiscovering = true;
        Thread threadFind = new Thread(() -> {
            boolean flagFound = JwtMintProcess.rediscover(JwtMintProcess.DUR_DISCOVERY);
            SwingUtilities.invokeLater(() -> {
                this.flagDiscovering = false;
                if (flagFound) {
                    log.append("the provider serves its keys at "
                            + JwtMintProcess.strUrlJwks());
                    // THE SANDBOX FORM FOLLOWS, and this is the moment it can:
                    // before the document came back the key set url was this
                    // project's own path under a foreign host, which is a
                    // guess and not an address.
                    announceProvider();
                }
            });
        }, "oidc-discovery");
        threadFind.setDaemon(true);
        threadFind.start();
    }


    /**
     * Stops the service. Safe to call twice.
     */
    /**
     * <b>The reason a caller about to depend on the mint needs.</b> A start
     * that waits for a JWKS should not wait at all when there is nothing to
     * wait for, and the reason it will never come is here rather than in the
     * timeout.
     *
     * @return why the service is not there, or null when it started
     */
    public String strWhyNoService() {
        return strWhyNoService;
    }


    /**
     * Stops the service. Safe to call twice.
     */
    public void stopService() {
        timerPoll.stop();
        // AN EXTERNAL PROVIDER IS NOT OURS TO STOP. It was running before this
        // window opened and a line saying it was stopped would be false.
        if (JwtMintProcess.isExternal())
            return;
        if (mint.isRunning())
            log.append("stopping the token service");
        mint.stop();
    }


    /**
     * A line in this tab's log, from the window - what it registered at the
     * provider, which belongs here rather than in the start log.
     *
     * @param strLine the line; called on the event thread
     */
    public void note(String strLine) {
        log.append(strLine);
    }


    /**
     * Stops the service and starts it again, which is what a Sandbox Start
     * does: the participant about to come up may read the JWKS from here.
     */
    public void restartService() {
        // UNCONDITIONALLY, whatever the mode is NOW. `isExternal` says where
        // this window is pointed, not what it started: a child launched before
        // the url was typed still holds the mint port, and leaving it would
        // make the next embedded start fail to bind rather than say why.
        mint.stop();
        startService();
    }


    /**
     * Writes the provider url and re-points this window at it.
     *
     * APPLIED ON A PRESS, and not on a settle timer like the Settings tab.
     * This is the one setting that decides whether a process is started at
     * all, so a half-typed url saved on the way past would leave the window
     * pointed at a host that does not exist for as long as it takes to type
     * the rest of the name.
     *
     * IT DOES NOT STOP A PROVIDER THIS WINDOW ALREADY STARTED. A participant
     * that is up fetched its keys from that service and may fetch them again;
     * killing it underneath a running stack is a bigger thing than changing a
     * setting. {@link #restartService} takes it down at the next start, which
     * is also when the new url takes effect.
     */
    private void urlApplyRequested() {
        String strWanted;
        try {
            strWanted = RaposzaSettings.strUrlNormalised(fldUrlOidc.getText());
        }
        catch (IllegalArgumentException ex) {
            log.append(String.valueOf(ex.getMessage()));
            tabsInner.setSelectedComponent(log);
            notice("Not applied - " + ex.getMessage());
            return;
        }

        RaposzaSettings settingsNow = RaposzaSettings.current();
        if (strWanted.equals(settingsNow.strUrlOidc())) {
            fldUrlOidc.setText(strWanted);
            return;
        }

        RaposzaSettings settingsNew = new RaposzaSettings(settingsNow.dirHome(),
                settingsNow.nPortMint(), settingsNow.nPortDiscovery(), settingsNow.strLine(),
                settingsNow.strLauncher(), settingsNow.nPortFirst(),
                settingsNow.nPortPostgres(), settingsNow.nSecondsReady(),
                settingsNow.flagOfferAviation(), settingsNow.flagOfferPharma(), strWanted,
                settingsNow.nPortUiFirst(),
                settingsNow.dirDaml(), settingsNow.dirDpm(), settingsNow.dirSplice());
        try {
            RaposzaSettings.store(settingsNew);
        }
        catch (IOException ex) {
            log.append("the settings could not be written: " + ex.getMessage());
            notice("Not applied - " + ex.getMessage());
            return;
        }

        fldUrlOidc.setText(strWanted);
        JwtMintProcess.forgetDiscovered();
        log.append(strWanted.isEmpty()
                ? "no external provider - this window starts one of its own"
                : "pointed at " + strWanted);
        if (mint.isRunning()) {
            log.append("the provider this window started is still running and is stopped"
                    + " at the next Sandbox start");
        }
        // THE FORM FOLLOWS IMMEDIATELY WHEN THE ANSWER NEEDS NO I/O, which is
        // every embedded case. A foreign provider is announced by
        // `discoverLater` once its document has been read.
        announceProvider();
        log.append("it takes effect on the next Sandbox start");
        notice(strWanted.isEmpty() ? "OIDC: this window's own provider"
                : "OIDC: " + strWanted);
        if (!timerPoll.isRunning())
            timerPoll.start();
        poll();
    }


    /**
     * PUBLIC for the discovery document, B-8, which says whether the mint is
     * answering rather than only where it would answer. A consumer told a JWKS
     * url with nothing behind it has been handed over to a participant it
     * cannot get a token for.
     *
     * @return whether the mint process is up
     */
    public boolean isMintRunning() {
        return mint.isRunning();
    }


    /**
     * What an OIDC-aware client needs, for the discovery document. B-8,
     * operator instruction 2026-08-22.
     *
     * FOUR OF THESE ARE IN THE MINT'S OWN DISCOVERY DOCUMENT and are repeated
     * here so that a client has somewhere to start; the rest of the document is
     * fetched from `discovery` and nothing is duplicated that changes.
     *
     * THE FIFTH IS NOT IN ANY STANDARD DOCUMENT AND IS THE REASON THIS EXISTS.
     * The participant checks an audience or a scope that no discovery document
     * describes, so a conforming client doing plain `client_credentials` gets a
     * token that verifies against the key set and is then refused by the
     * participant - the hardest failure of the lot to read. The value it has to
     * send is here, beside a line saying so.
     *
     * @param strUser the ledger user to speak for, or null for the admin user
     *        every participant has
     * @return never null; the four endpoint entries at minimum
     */

    /**
     * The url that mints a token this participant accepts, for the discovery
     * document. B-8. It is a GET on `/mint.txt` and it returns the bare token
     * rather than an OAuth response, which is what makes it worth publishing
     * beside the standard endpoint - and it is NOT `/oauth2/token`, which is
     * POST-only and answers a GET with 405.
     *
     * WITHOUT THIS THE HAND-OVER IS INCOMPLETE. A consumer given a Ledger API
     * port and a JWKS url still cannot call the participant: it needs a token,
     * signed with the algorithm this mode pins, carrying the audience or scope
     * this shape decides and a lifetime the participant will not refuse. All of
     * that is the rule {@link JwtMintProcess#strUrlToken} already spells, and
     * spelling it a second time in a consumer is how the two drift.
     *
     * @param strUser the ledger user the token is to speak for
     * @return an absolute url, or null when nothing has been configured yet or
     *         the participant checks nothing
     */
    public Map<String, Object> mapOidcFor(String strUser) {
        // ONE SPELLING, and it is the headless one. The rule - which of the
        // audience and the scope, and who to ask as - has to match what the
        // participant was configured with or nothing verifies, and a second
        // copy of it here is the copy that drifts.
        return AuthStart.mapOidcFor(auth, strUser, nameParticipant);
    }


    public String strUrlTokenFor(String strUser) {
        AuthSettings authHere = auth;
        VersionId versionHere = version;
        if (authHere == null || versionHere == null || strUser == null || strUser.isBlank())
            return null;
        // NOT AGAINST A FOREIGN PROVIDER. This url is `/mint.txt`, which is
        // this project's own endpoint; publishing it against a provider that
        // does not serve it hands a consumer a 404 dressed as a hand-over.
        if (JwtMintProcess.isExternal())
            return null;
        // A WILDCARD PARTICIPANT NEEDS NO TOKEN, and publishing a url that
        // mints one would tell a consumer to do work that changes nothing.
        if (!authHere.mode().flagTargets())
            return null;
        // `/mint.txt` AND NOT `/oauth2/token`. MEASURED: a GET on
        // the token endpoint is 405, because RFC 6749 section 3.2 makes it a
        // POST and the mint implements it as one. `strUrlToken` builds that
        // POST target for scribe, which is a different thing from a url a
        // person or a script can fetch. This one is a GET and returns the bare
        // token as text.
        return JwtMintProcess.strUrlBase()
                + JwtMintProcess.strMintQuery(authHere, versionHere, strUser.trim(),
                        nameParticipant);
    }


    private JPanel buildServiceTab() {
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlTop.setOpaque(false);
        pnlTop.add(lblStatus);
        pnlTop.add(btnSwagger);
        pnlTop.setAlignmentX(LEFT_ALIGNMENT);

        JPanel pnlUser = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlUser.setOpaque(false);
        JLabel lblUser = new JLabel("User");
        FieldHelp.install(lblUser, FieldHelp.KEY_JWT_USER);
        pnlUser.add(lblUser);
        cboUser.setPreferredSize(GuiTheme.dimScaled(240, 28));
        pnlUser.add(cboUser);
        pnlUser.add(btnRefresh);
        pnlUser.add(btnMint);
        pnlUser.setAlignmentX(LEFT_ALIGNMENT);

        JPanel pnlUrl = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlUrl.setOpaque(false);
        JLabel lblUrl = new JLabel("External OIDC url");
        FieldHelp.install(lblUrl, FieldHelp.KEY_SET_URL_OIDC);
        pnlUrl.add(lblUrl);
        pnlUrl.add(fldUrlOidc);
        pnlUrl.add(btnUrlApply);
        pnlUrl.setAlignmentX(LEFT_ALIGNMENT);

        JPanel pnlAudience = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlAudience.setOpaque(false);
        pnlAudience.add(lblAudience);
        pnlAudience.add(fldAudience);
        pnlAudience.add(btnAudienceCopy);
        pnlAudience.setAlignmentX(LEFT_ALIGNMENT);

        JPanel pnlStack = new JPanel();
        pnlStack.setLayout(new BoxLayout(pnlStack, BoxLayout.Y_AXIS));
        pnlStack.setOpaque(false);
        // THE URL FIRST. What this tab is pointed at decides what every line
        // under it means, so it reads before the status rather than after it.
        pnlStack.add(pnlUrl);
        pnlStack.add(Box.createVerticalStrut(GuiTheme.scale(GuiTheme.N_GAP)));
        pnlStack.add(pnlTop);
        pnlStack.add(Box.createVerticalStrut(GuiTheme.scale(GuiTheme.N_GAP)));
        pnlStack.add(pnlAudience);
        pnlStack.add(Box.createVerticalStrut(GuiTheme.scale(GuiTheme.N_GAP)));
        pnlStack.add(pnlUser);

        JScrollPane scrollToken = new JScrollPane(areaToken);
        scrollToken.setBorder(BorderFactory.createLineBorder(
                GuiTheme.colCardBorder(), 1, true));

        JPanel pnlInner = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlInner.setOpaque(false);
        pnlInner.add(pnlStack, BorderLayout.NORTH);
        pnlInner.add(scrollToken, BorderLayout.CENTER);

        JPanel pnlTab = new JPanel(new BorderLayout());
        pnlTab.setOpaque(false);
        pnlTab.add(GuiTheme.card("OIDC provider", pnlInner), BorderLayout.CENTER);
        return pnlTab;
    }


    /**
     * @return the user the next token is for, never blank
     */
    public String strUserSelected() {
        Object obj = cboUser.getSelectedItem();
        String strUser = obj == null ? "" : String.valueOf(obj).trim();
        return strUser.isEmpty() ? STR_USER_ADMIN : strUser;
    }


    /**
     * Called from the pump thread of the service process.
     *
     * @param strLine one line, without its terminator
     */
    private void lineArrived(String strLine) {
        SwingUtilities.invokeLater(() -> {
            log.append(strLine);
            if (isFatal(strLine))
                this.strLastFatal = strLine.trim();
        });
    }


    /**
     * @param strLine one line of the service's output
     * @return whether it is the sort of line that explains an exit
     */
    private static boolean isFatal(String strLine) {
        return strLine.contains("APPLICATION FAILED TO START")
                || strLine.contains("Caused by:")
                || strLine.contains("Exception")
                || strLine.contains("ERROR");
    }


    /**
     * The service went down on its own, which is the case a polled header
     * reports as `not running` and leaves there.
     *
     * @param nCode the exit code, from the pump thread
     */
    private void exited(int nCode) {
        SwingUtilities.invokeLater(() -> {
            String strWhy = strLastFatal == null ? "" : " - " + strLastFatal;
            log.append("the token service exited with code " + nCode);
            lblStatus.setText("exited with code " + nCode + strWhy);
            lblStatus.setForeground(GuiTheme.COL_BAD);
            tabsInner.setSelectedComponent(log);
        });
    }


    /**
     * The header follows the PROCESS rather than whichever button was last
     * pressed, so a service that died on its own is visible as one.
     */
    private void poll() {
        // AN EXTERNAL PROVIDER HAS NO PROCESS TO FOLLOW, so what is reported
        // is whether its document answers. The three controls below it are
        // this project's own minting surface - `/mint.txt` and the extra
        // parameters on the token endpoint - which no conforming provider
        // serves, so they are turned off with the reason on them rather than
        // left to fail against a 404.
        if (JwtMintProcess.isExternal()) {
            boolean flagFound = JwtMintProcess.isDiscovered();
            discoverLater();
            lblStatus.setText(flagFound
                    ? "external provider at " + JwtMintProcess.strUrlBase()
                    : "external provider at " + JwtMintProcess.strUrlBase()
                            + " - not answering");
            lblStatus.setForeground(flagFound ? GuiTheme.COL_OK : GuiTheme.COL_BAD);
            btnSwagger.setText("Open provider");
            btnSwagger.setEnabled(true);
            btnRefresh.setEnabled(false);
            btnMint.setEnabled(false);
            btnRefresh.setToolTipText(STR_WHY_EXTERNAL);
            btnMint.setToolTipText(STR_WHY_EXTERNAL);
            return;
        }

        boolean flagUp = mint.isRunning();
        if (flagUp) {
            lblStatus.setText("running on " + JwtMintProcess.strUrlBase());
            lblStatus.setForeground(GuiTheme.COL_OK);
        }
        else if (mint.nExit() < 0) {
            lblStatus.setText("not running");
            lblStatus.setForeground(GuiTheme.COL_STOPPED);
        }
        btnSwagger.setText("Swagger");
        btnSwagger.setEnabled(flagUp);
        btnRefresh.setEnabled(flagUp && nPortJsonApi > 0);
        btnMint.setEnabled(flagUp && auth != null);
        btnRefresh.setToolTipText(null);
        btnMint.setToolTipText(null);
    }


    /**
     * Reads every ledger user off the participant's JSON Ledger API.
     *
     * TWO CALLS, and the first is the reason this belongs on this tab: the
     * query itself needs a token, and the only thing that can mint one against
     * the participant that is running is the service beside it. So a token for
     * `participant_admin` is minted with the settings the participant was
     * started from, and then used as a bearer.
     *
     * A participant with no authentication takes the call without one, and the
     * header is sent anyway - an unauthenticated Ledger API ignores it.
     */
    /**
     * Mints a token for the selected user, with the settings the participant
     * was started from. Nothing about it is asked for here, because nothing
     * about it is this tab's to decide.
     */
    private void mintRequested() {
        AuthSettings authHere = auth;
        if (authHere == null) {
            log.append("no stack has been started, so there is nothing to mint"
                    + " a token for");
            tabsInner.setSelectedComponent(log);
            return;
        }

        String strUser = strUserSelected();
        getFrom(JwtMintProcess.strUrlBase() + strMintQuery(authHere, strUser), null,
                strBody -> {
                    String strToken = strBody.trim();
                    areaToken.setText(strToken);
                    areaToken.setCaretPosition(0);
                    copy(strToken);
                    log.append("minted a token for " + strUser + ", "
                            + AuthSettings.N_HOURS_TOKEN + " h, and copied it");
                    // THE TAB THE READER IS NOT ON. A token that silently
                    // reached the clipboard is indistinguishable from a button
                    // that did nothing until they change tabs.
                    notice("JWT copied to clipboard");
                });
    }


    /**
     * @param authHere what the participant verifies
     * @param strUser the `sub` the token carries
     * @return the query that mints it
     */
    private String strMintQuery(AuthSettings authHere, String strUser) {
        return JwtMintProcess.strMintQuery(authHere, version, strUser,
                nameParticipant);
    }


    private void refreshRequested() {
        AuthSettings authHere = auth;
        if (authHere == null || nPortJsonApi <= 0) {
            log.append("no stack has been started, so there is no ledger to read users from");
            tabsInner.setSelectedComponent(log);
            return;
        }

        getFrom(JwtMintProcess.strUrlBase() + strMintQuery(authHere, STR_USER_ADMIN),
                null, strToken -> usersRequested(strToken.trim()));
    }


    /**
     * @param strToken the bearer to present
     */
    private void usersRequested(String strToken) {
        String strUrl = "http://127.0.0.1:" + nPortJsonApi + "/v2/users";
        getFrom(strUrl, strToken, strBody -> {
            List<String> lstUser = lstUserOf(strBody);
            if (lstUser.isEmpty()) {
                log.append("the ledger reported no users at all, which is not a state a"
                        + " participant is normally in");
                return;
            }

            String strWas = strUserSelected();
            cboUser.removeAllItems();
            for (int idxUser = 0; idxUser < lstUser.size(); idxUser++) {
                cboUser.addItem(lstUser.get(idxUser));
            }
            if (lstUser.contains(strWas))
                cboUser.setSelectedItem(strWas);
            log.append("read " + lstUser.size() + " user(s) from " + strUrl);
        });
    }


    /**
     * The documented response is `{"users":[{"id":...}, ...]}`.
     *
     * @param strBody what the JSON Ledger API returned
     * @return the user ids, in the order they arrived
     */
    private List<String> lstUserOf(String strBody) {
        List<String> lstOut = new ArrayList<>();
        try {
            JsonNode nodeRoot = mapper.readTree(strBody);
            JsonNode nodeUsers = nodeRoot.path("users");
            for (JsonNode nodeUser : nodeUsers) {
                String strId = nodeUser.path("id").asText("");
                if (!strId.isEmpty() && !lstOut.contains(strId))
                    lstOut.add(strId);
            }
        }
        catch (IOException ex) {
            log.append("could not read the user list: " + ex.getMessage());
        }
        return lstOut;
    }


    /**
     * Opens the service's own API documentation in whatever browser this
     * machine uses.
     *
     * TWO ROUTES, and the second is not belt and braces. `Desktop.browse` needs
     * a desktop integration that a bare window manager may not have, and when
     * it is missing it throws rather than doing nothing. The url is put on the
     * clipboard either way, so a failure still leaves something to paste.
     */
    private void swaggerRequested() {
        String strUrl = JwtMintProcess.strUrlSwagger();
        copy(strUrl);

        Thread threadOpen = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(strUrl));
                    return;
                }
            }
            catch (IOException | RuntimeException ex) {
                // Fall through to xdg-open, which is what a desktop-less
                // session has instead.
            }

            try {
                new ProcessBuilder("xdg-open", strUrl).start();
            }
            catch (IOException ex) {
                SwingUtilities.invokeLater(() -> log.append("could not open a browser;"
                        + " the url is on the clipboard: " + strUrl));
            }
        }, "jwtmint-browser");
        threadOpen.setDaemon(true);
        threadOpen.start();
    }


    /**
     * @param strUrl the whole url
     * @param strToken a bearer to present, or null for none
     * @param sinkBody told the body on the event dispatch thread
     */
    private void getFrom(String strUrl, String strToken, Consumer<String> sinkBody) {
        Thread threadCall = new Thread(() -> {
            String strBody;
            try {
                HttpRequest.Builder bld = HttpRequest.newBuilder(URI.create(strUrl))
                        .timeout(DUR_HTTP)
                        .GET();
                if (strToken != null)
                    bld = bld.header("Authorization", "Bearer " + strToken);

                HttpResponse<String> resp =
                        client.send(bld.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    failed(strUrl, "HTTP " + resp.statusCode() + " " + resp.body());
                    return;
                }
                strBody = resp.body();
            }
            catch (IOException ex) {
                failed(strUrl, String.valueOf(ex.getMessage()));
                return;
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            SwingUtilities.invokeLater(() -> {
                try {
                    sinkBody.accept(strBody);
                }
                catch (RuntimeException ex) {
                    log.append("could not read the answer from " + strUrl + ": "
                            + ex.getMessage());
                }
            });
        }, "jwtmint-http");
        threadCall.setDaemon(true);
        threadCall.start();
    }


    private void failed(String strUrl, String strWhy) {
        SwingUtilities.invokeLater(() -> {
            log.append("FATAL: " + strUrl + " - " + strWhy);
            tabsInner.setSelectedComponent(log);
        });
    }


    /**
     * @param strLine a few words for whatever surface is in front of the reader
     */
    private void notice(String strLine) {
        java.util.function.Consumer<String> sinkHere = sinkNotice;
        if (sinkHere != null)
            sinkHere.accept(strLine);
    }


    private void copy(String strValue) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(strValue), null);
    }

}
