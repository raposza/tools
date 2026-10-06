// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.DpmBundles;
import com.raposza.canton.install.HostPlatform;
import com.raposza.canton.install.SdkInstallations;
import com.raposza.canton.install.SdkOffer;
import com.raposza.canton.install.SdkOffers;
import com.raposza.canton.install.SpliceAcquire;
import com.raposza.canton.install.ToolchainAdvice;
import com.raposza.canton.install.ToolchainInstall;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.process.CantonProcess;
import com.raposza.canton.pqs.PqsOAuth;
import com.raposza.canton.pqs.PqsSpec;
import com.raposza.canton.pqs.ScribeProcess;
import com.raposza.canton.topology.AuthOverlay;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.canton.topology.StorageOverlay;
import com.raposza.jwt.TokenShape;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.SandboxStack;
import com.raposza.runtime.lifecycle.StackService_i;
import com.raposza.runtime.localnet.LocalNetAuth;
import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.localnet.LocalNetSpec;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.localnet.LocalNetRunner;
import com.raposza.runtime.localnet.LocalNetUi;
import com.raposza.runtime.localnet.SpliceInstallations;
import com.raposza.sandbox.StackComponent;
import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.LocalNetPqs;
import com.raposza.sandbox.app.MintRegistration;
import com.raposza.sandbox.app.PqsLog;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.sandbox.app.DiscoveryDoc;
import com.raposza.sandbox.app.DiscoveryNode;
import com.raposza.sandbox.app.DiscoveryServer;
import com.raposza.sandbox.app.ProviderUsers;
import com.raposza.sandbox.app.RawarClient;
import com.raposza.sandbox.app.RawarEnv;
import com.raposza.sandbox.app.RawarServer;
import com.raposza.sandbox.app.RawarSites;
import com.raposza.sandbox.app.SandboxUsers;
import com.raposza.sandbox.app.Milestones;
import com.raposza.sandbox.app.ReadyReport;
import com.raposza.sandbox.app.SandboxOptions;
import com.raposza.sandbox.app.SandboxService;
import com.raposza.sandbox.caps.SandboxCapabilities;
import com.raposza.sandbox.caps.SandboxFeature;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * The Sandbox control surface: what to start, whether it is up, what each
 * component printed, and what is in the database underneath.
 *
 * <h2>One tab per thing that can be wrong</h2>
 *
 * The window was a launch form beside one log, and that log was the union of
 * everything - which meant the only question it could answer was "did the
 * start fail". The component logs are the sub-tabs of ONE `Logs` tab -
 * PostGres, Participant, PQS - and the JSON API keeps its own tab because it
 * is a client as well as a log. Each carries its own output, so a participant
 * that is retrying its database connection is visible as a participant problem
 * rather than as noise between two scribe lines.
 *
 * <h2>Where the lines come from, which is not where they used to</h2>
 *
 * `ManagedProcess` pumps a process's output to its listeners and to a bounded
 * tail and NOWHERE ELSE - it never reaches standard output. So the per-process
 * panes are fed by {@link SandboxService#useComponentSink}, which the stacks
 * attach before each process starts. The Sandbox tab's own log keeps the
 * stream {@link LogTee} tees: slf4j, the service's own lines, and anything
 * printed directly. Both are needed and neither is a subset of the other.
 *
 * <h2>Threading, which is the whole of the risk here</h2>
 *
 * A start takes about a minute against a real Canton. Nothing of that may
 * happen on the event dispatch thread, so {@link SandboxService} is driven
 * from a worker and every line it produces is hopped back through
 * {@link LogPane#append}. The buttons are driven off the service STATE rather
 * than off which button was last pressed, so an interrupted or failed start
 * cannot leave a window whose Stop is enabled over nothing.
 *
 * <h2>Closing</h2>
 *
 * The window owns a running stack. Closing it therefore stops the stack FIRST
 * and disposes afterwards, with the close request refused while that runs - a
 * disposed window over a live Canton would leave four node processes and a
 * PostgreSQL with nothing to stop them but the shutdown hook, which is a worse
 * outcome than a second of waiting. The hook stays as the last defence.
 *
 * <h2>Everything below the Canton box belongs to the selected version</h2>
 *
 * The settings, the snapshots and the tabs are all per version. Selecting a
 * different Canton writes the outgoing version's {@link SandboxProfile}, reads
 * the incoming one, re-points {@link Snapshots} at that version's own root, and
 * takes away the surfaces the new version has no component behind - the JSON
 * API tab and its lamp on 3.x, where the participant serves the HTTP Ledger API
 * itself and there is no second process to watch.
 *
 * <h2>Three moments a profile is written, and no listener per field</h2>
 *
 * Before a selection change, at Start, and at close. Between them every edit a
 * developer can make is either followed by one of the three or discarded by
 * closing without doing anything - which is what discarding an edit should
 * mean. A document listener per field would write the file on every keystroke
 * and would still need all three, because a field being edited when the window
 * closes has not fired one.
 *
 * Author Claude/bentzn
 */
public final class SandboxWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** The window's own title. */
    private static final String STR_TITLE = "Raposza Sandbox";

    /** What the LocalNet topology adds to it - his instruction, 2026-09-21. */
    private static final String STR_TITLE_LOCALNET = STR_TITLE + " - LocalNetND";

    private static final int N_WIDTH_RIGHT_MIN = 420;

    private static final int N_HEIGHT_MIN = 680;

    /** What `MainWindow` sets, so the two windows match. */
    private static final int N_WIDTH_WORKBENCH = 1400;

    private static final int N_HEIGHT_WORKBENCH = 820;

    /** How often the lamps re-read the service. */
    private static final int N_MS_LAMP = 500;

    /**
     * What slf4j-simple prints for the two loggers that ARE PostgreSQL.
     *
     * Measured from a run rather than guessed at: the embedded server's own
     * output - initdb, the postmaster, every `LOG:` line the server writes -
     * arrives on standard error through Zonky's logger, and the database
     * lifecycle through ours. Neither is process output, so neither reaches
     * the component sink, and the PostGres tab showed two lines while the
     * server's whole log sat in the Sandbox tab beside Canton's.
     */
    private static final String STR_LOGGER_PG =
            "com.raposza.runtime.db.SandboxPostgres";

    private static final String STR_LOGGER_ZONKY = "io.zonky.test.db.postgres.embedded";

    /** The same problem as the two above, one tab along. */
    private static final String STR_LOGGER_PQS = "com.raposza.canton.pqs";

    private static final String STR_NOTE_PARTICIPANT_DATA =
            "Parties, users, packages and contracts go here. Not built - the source"
            + " has not been decided, and both candidates are unmeasured on at least"
            + " one generation:\n\n"
            + "(a) the JSON Ledger API. Free on 3.x, where the participant already"
            + " serves it on port.json-api; absent on 2.x until the JSON API tab's"
            + " note is resolved. The v2 endpoint paths are not banked in the"
            + " inventory, so they would be guessed.\n\n"
            + "(b) Canton's own schema, over JDBC, which is what the CantonSandboxPqs"
            + " prototype did. It works on 2.10 - participant_events_create minus"
            + " participant_events_consuming_exercise, with template ids resolved"
            + " through ledger_api.string_interning - and that layout is 2.10's."
            + " Nothing in the corpus measures the 3.x internal schema.\n\n"
            + "The PostGres tab reads the same server generically in the meantime,"
            + " so the tables are visible even where this pane cannot name them.";

    /**
     * THE MINT, and it is not a stack component. It runs outside the
     * profiles on a fixed port, because every `auth-services` block
     * written against it carries that url and a port that moved between
     * runs would invalidate configuration that is correct in every other
     * respect. The window starts it on open and stops it on close; it
     * survives a Canton selection change, a Start and a Stop.
     */
    private final JwtPane paneJwt = new JwtPane();

    private final SettingsPane paneSettings = new SettingsPane();

    /**
     * B-8. It belongs to the WINDOW and not to the stack: an endpoint that went
     * down with the participant could not answer "nothing is running", which is
     * the one answer a client cannot get any other way.
     */
    private final transient DiscoveryServer discovery = new DiscoveryServer();

    /**
     * The RAWAR server - `rawar.md` section 5. The window's, like the
     * discovery endpoint, and it serves from the same snapshot.
     */
    private final transient RawarServer rawar = new RawarServer();

    /**
     * The last document, built on the event thread and read by the discovery
     * handler's own thread. VOLATILE because those are two threads and this is
     * the whole of what passes between them.
     */
    private transient volatile DiscoveryDoc docDiscovery;

    private final SandboxForm form;

    /**
     * What a single-participant window says when this machine has neither a
     * Daml Assistant nor DPM - the operator's words, 2026-09-26. Nothing is
     * offered for install there: the catalogue a clean machine can see stops
     * at 3.4.11 and reads as outdated.
     */
    public static final String STR_NO_TOOLCHAIN =
            "Please install DPM (DAML >= 3.5.4) and/or DAML Assistant (DAML <= 3.4.11).";

    /** Hidden unless this machine has nothing to start - LocalNetND only. */
    private final ToolchainBanner banner = new ToolchainBanner();

    /**
     * The Aviation fixture, which is offered once per window and run once per
     * start.
     *
     * ASSIGNED IN THE CONSTRUCTOR, not here. Its sink is `logMain`, which is
     * declared further down this class, and a field initialiser that names a
     * later field by its simple name is an illegal forward reference - even
     * inside a lambda, whose body is not what the rule looks at. Measured
     * 2026-09-08: `SandboxWindow.java:[214,41] illegal forward reference`.
     */
    private final transient AviationSession aviation;

    /**
     * The Pharma fixture, which is offered once per window on the LocalNetND
     * topology and put on the ledger once per start.
     *
     * ASSIGNED IN THE CONSTRUCTOR for the same reason as the field above.
     */
    private final transient PharmaSession pharma;

    /** How much of the right column the Status card opens with. */
    private static final double N_FRACTION_STATUS = 0.70;

    /** The form's share of the Sandbox tab's width - 40 / 60, his instruction, 2026-10-04. */
    private static final double N_FRACTION_FORM = 0.40;

    /** True once the developer has pressed the form/status divider; it is then left alone. */
    private transient boolean flagDividerDragged;

    private final StatusPane status = new StatusPane();

    private final LampBar lamps = new LampBar();

    /** The tee'd stream: slf4j, the service's lines, anything printed. */
    /**
     * MILESTONES ONLY, and no control bar. This pane used to carry the tee -
     * every line slf4j and every process printed - which is thousands of
     * lines for one start and is the reason nobody read it. What a reader
     * wants from a start is which component is up and how long each took;
     * the firehose is a tab away, per component, where it always was.
     */
    private final LogPane logMain = new LogPane(false);

    /**
     * THE LEDGER LOG, beside the application one - his instruction,
     * 2026-10-04: a line per contract created and per contract archived on
     * each participant, so a reader can see that something happened and which
     * template it involved. {@link LedgerWatch} reads them; it follows the
     * participant databases while the stack is RUNNING and nothing otherwise.
     */
    private final LogPane logLedger = new LogPane(false);

    private final transient LedgerWatch ledgerWatch = new LedgerWatch(logLedger::append);

    /**
     * NOT final. The profile store's ROOT is a global setting, so a save on the
     * Settings tab re-points it - the one piece of state in this window that
     * would otherwise go on writing to the directory the operator has just
     * stopped using.
     */
    private transient ProfileStore profiles = ProfileStore.ofDefaults();

    /**
     * The snapshots of the SELECTED version, or null when nothing is selected.
     *
     * Re-pointed on every selection change rather than filtered on read: a
     * store that held every version's snapshots and hid the wrong ones would
     * be one forgotten filter away from offering a 2.10 cluster to a 3.5
     * participant, and that failure arrives some way into a start as a
     * complaint about topology.
     */
    private transient Snapshots snapshots;

    /**
     * The FOUNDING snapshot for what is selected, or null when nothing is.
     *
     * Re-pointed on every selection change for the reason above it: a
     * founding snapshot is only meaningful to the software that wrote it, and
     * {@link Founding} keys it accordingly.
     */
    private transient Founding founding;

    /**
     * True while the DAR-FREE founding pass is in flight.
     *
     * The pass is one start whose only product is the founding snapshot, so
     * it carries no DARs and does not run the fixture. It is cleared before
     * the restart that follows - see {@link #foundingPassDone} for why that
     * ordering is what stops the window starting and stopping for ever.
     */
    private transient boolean flagFoundingPass;

    /** The pending line the main log currently holds open, or null. */
    private transient String strPendingOpen;

    /**
     * The founding key a pass has ALREADY BEEN ATTEMPTED for, or null.
     *
     * WHAT IT STOPS IS A LOOP, and the loop is not hypothetical. A pass ends
     * by copying the cluster; if that copy fails - a full disk, a mode
     * PostgreSQL wrote that the copy cannot read - then `exists()` is still
     * false when the restart asks, the restart is another pass, and the window
     * starts and stops for as long as the disk stays full. One attempt per key
     * per window terminates whatever the copy does, and a failed one leaves
     * exactly the behaviour every start had before this existed: found, and
     * keep nothing. `Reset founding` clears it, which is how a retry is asked
     * for once the reason has been dealt with.
     */
    private transient String strFoundingTried;

    /** Whose profile the fields currently hold, or null for none. */
    private transient CantonInstallation installProfile;

    /**
     * True until the first selection has been resolved.
     *
     * The first one takes its fallback from the FIELDS, which the constructor
     * filled from the command line, so `--pg-port 5555` seeds that
     * version's profile. Every later one falls back to the version's own
     * defaults, because by then the fields hold the version being left.
     */
    private transient boolean flagFirstSelect = true;

    /**
     * The snapshot the RUNNING start laid down, or null when it began empty.
     *
     * Read after the stack is up, which is why it is a field: by then
     * `startRequested`'s local is gone and the list can have been clicked
     * somewhere else.
     */
    private transient String strSnapshotRunning;

    /**
     * Whether the Aviation fixture is between `Yes` and a ledger that holds it.
     *
     * START IS HELD FOR THE WHOLE OF THAT, not just for the part a running
     * stack already covers. The build is a compiler and takes a minute, the
     * window sits in STOPPED throughout, and a Start pressed inside that
     * minute brings a stack up under a DAR that is still being written - and
     * the fixture then starts a second one on top of it.
     */
    private transient boolean flagAviationBusy;

    /**
     * The same, for Pharma. Two flags rather than one: the two fixtures are
     * offered on different topologies and a shared flag would read as a state
     * neither of them is in.
     */
    private transient boolean flagPharmaBusy;

    /**
     * Whether a snapshot was taken in this window.
     *
     * WHAT KEEPS THE FIXTURE ON DISK AT CLOSE. Without one, the staged
     * project and its DAR go with the window. With one, the saved cluster
     * holds contracts whose packages that DAR declares, and removing it would
     * leave a snapshot nothing can be built against.
     */
    private transient boolean flagSnapshotSaved;

    /** Where the JSON API tab goes back when a 2.x is selected again. */
    private static final int N_TAB_JSON_API = 2;

    /**
     * The one tab the component logs live under. `Debug`, not `Logs` - his
     * instruction, 2026-10-04: what a user reads is on Sandbox, Web and OIDC,
     * and this is the processes' own output.
     */
    private static final String STR_TAB_LOGS = "Debug";

    /**
     * WHICH DARS THE NEXT START UPLOADS. Its own tab rather than a row on the
     * form: it is a directory plus a subset of what is in it, which is a table
     * rather than a field, and the form's rule is one control per answer.
     */
    private final DarsPane paneDars = new DarsPane();

    /** What the RUNNING participant holds, which is a different list. */
    private final DarsLivePane paneDarsLive = new DarsLivePane();

    /**
     * THE ORDER HIS WORKBENCH TABS TAKE - `todo.md` A-37, app-provider,
     * app-user, then sv - and the LocalNetND DARs tabs follow it, so the two
     * windows list the three participants the same way.
     */
    private static final List<String> LST_ROLE_TAB = List.of("app-provider", "app-user", "sv");

    /**
     * ONE DARs TAB PER PARTICIPANT ON LOCALNETND - his decision, 2026-09-23,
     * `todo.md` section 9's DAR workflow, break 3. Each is the live pane on that
     * participant's own admin port, so a DAR the Pharma fixture uploaded is
     * listed on the two participants it went to, and Upload, Remove, Vet and
     * Unvet act on the one participant whose tab is open. Empty on Sandbox
     * Simple, whose DARs tab keeps Store and Participant.
     */
    private final transient Map<String, DarsLivePane> mapDarsLocal = new LinkedHashMap<>();

    /**
     * The Web tab: every page this window serves - LocalNetND's UIs, each
     * participant's JSON Ledger API and the RAWARs - with its node and URL.
     * His instruction, 2026-10-02; `todo.md` A-40 before it.
     */
    private final WebPane paneWeb = new WebPane();

    /** OIDC's Users tab: who can sign in, on which node, with which password. */
    private final UsersPane paneUsers = new UsersPane();

    private static final String STR_TAB_WEB = "Web";

    /** How long a Users registration waits for the provider to mint. */
    private static final Duration DUR_USERS_MINT = Duration.ofSeconds(30);

    /**
     * How often the Users tab reads the OIDC server again - his instruction,
     * 2026-10-04: no Refresh button, "auto refresh every 3 seconds".
     */
    private static final int N_MS_USERS = 3000;

    /** The state line and the milestone when the ledger's users were not written. */
    private static final String STR_USERS_REGISTER_FAILED =
            "OIDC user registration FAILED, a RAWAR page cannot sign in - ";

    /** What the running LocalNetND verifies, which decides the password column. */
    private transient LocalNetAuth authLocalRunning;

    private static final String STR_TAB_JSON_API = "JSON API";

    /**
     * THE PROVIDER, NOT THE TOKEN FORMAT. It was `JWT` while the tab was a
     * mint form with a log under it; it is an OpenID Provider that this window
     * either runs or is pointed at - operator instruction, 2026-09-21.
     */
    private static final String STR_TAB_OIDC = "OIDC";

    private final JTabbedPane tabs = new JTabbedPane();

    private final JButton btnSave = new JButton("Save snapshot");

    /**
     * WHAT A START WILL RUN ON. Not a button: loading a snapshot is not a
     * second way to start a stack, it is a property of the next start, and a
     * `Load snapshot` button beside `Start` read as two ways to do the same
     * thing with no way to tell which one had won.
     */
    private final DefaultListModel<String> modelSnapshot = new DefaultListModel<>();

    private final JList<String> lstSnapshot = new JList<>(modelSnapshot);

    /** The row meaning `start empty`, always first and always present. */
    private static final String STR_SNAPSHOT_NONE = "(none - empty ledger)";

    /** How long a copied value stays in the footer. */
    private static final int N_MS_COPY_SHOWN = 3000;

    /**
     * How long the last thing that happened stays in the footer before it
     * gives way to what IS.
     *
     * A start ends on `Started PQS in 4.6 seconds`, which is worth reading
     * once and is then a fact about the past sitting where the reader looks
     * for the present. Ten seconds is long enough to read it and short
     * enough that nobody comes back to a window still reporting an event.
     */
    private static final int N_MS_SETTLE = 10000;

    /** When Start was pressed, for the total the components cannot give. */
    private long nNanoStart;

    private static final String STR_FOOTER_RUNNING = "Running";

    /** The end of the fixture, and the end of the whole opening sequence. */
    private static final String STR_AVIATION_DONE = "Aviation data created, system running";

    private static final String STR_PHARMA_DONE = "Pharma data created, system running";

    private static final String STR_PHARMA_FAILED =
            "Pharma data was not created; LocalNetND is running";

    /** The same moment, where a script exited non-zero. */
    private static final String STR_AVIATION_FAILED =
            "Aviation data was not created; the Sandbox is running";

    private static final String STR_FOOTER_STOPPED = "Stopped";

    private final LogPane logPostgres = new LogPane();

    private final LogPane logParticipant = new LogPane();

    private final LogPane logPqs = new LogPane();

    /**
     * SCRIBE ON THE LocalNetND TOPOLOGY, which this window owns rather than
     * the stack - `LocalNetPqs` says why it cannot be the stack's. Null when
     * PQS is OFF, when nothing is running, or before it has started.
     */
    private transient ScribeProcess procPqsLocal;

    /**
     * WHAT THE PQS PANE SHOWS, which is not what scribe printed - his
     * instruction of 2026-09-22, "Not tech. Simple. Informative."
     */
    private final transient PqsLog logPqsPretty = new PqsLog();

    /** LocalNetND's splice namespace - D-791, the logs are BY SOURCE. */
    private final LogPane logSplice = new LogPane();

    /** And the pages this process serves itself. */
    private final LogPane logWeb = new LogPane();

    private final Map<String, LogPane> mapLog = new LinkedHashMap<>();

    private final JsonApiPane paneJsonApi = new JsonApiPane(this::service);

    private final ParticipantDataPane paneDataParticipant =
            new ParticipantDataPane(this::service);

    private final PostgresDataPane paneDataPostgres = new PostgresDataPane(this::service);

    private final JButton btnStart = new JButton("Start");

    private final JButton btnStop = new JButton("Stop");

    /**
     * BACK TO THE KNOWN STATE, in one press.
     *
     * A button where the snapshot list is not, and the two are not in tension:
     * the list says WHAT a start runs on, this says RUN IT AGAIN. It chooses
     * nothing, so there is no second way to do the same thing with no way to
     * tell which one won - which is the reason there is no `Load snapshot`
     * button.
     *
     * Reset is why the snapshots exist. A developer puts the ledger into the
     * state a test needs, saves it once, and from then on getting back there is
     * this button rather than a stop, a click and a start. With nothing
     * selected the known state is the empty ledger with the ticked DARs on it,
     * which is what a first start produces.
     *
     * ENABLED ONLY WHILE A STACK IS RUNNING. Stopped, Start already is this.
     */
    private final JButton btnReset = new JButton("Reset");

    private final JLabel lblFooter = new JLabel(" ");

    /**
     * The whole stack's state, in the bottom right.
     *
     * It was the first thing in the Status card, in the middle of the window,
     * and it is the one word a reader looks for without hunting. The footer
     * already carries the last thing that happened; the state belongs at the
     * other end of the same line.
     */
    private final JPanel pnlChipState = new JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));

    /**
     * What the component named by {@link #strFooter} is doing, or null.
     *
     * Kept beside the milestone rather than folded into it because the footer
     * has to be able to say the milestone alone - after a copy, and under any
     * milestone whose component prints nothing this window recognises.
     */
    private transient String strPhase;

    /** The last milestone, which the footer returns to after a copy. */
    private String strFooter = " ";

    /** What the footer settles to, or null when nothing is pending. */
    private String strSettle;

    private final transient Timer timerFooter = new Timer(N_MS_COPY_SHOWN, evt -> {
        lblFooter.setText(strFooter);
    });

    private final transient Timer timerSettle = new Timer(N_MS_SETTLE, evt -> {
        footerSettled();
    });

    private final transient LogTail tailCanton =
            new LogTail(this::fileCantonLog, this::onCantonLine);

    /**
     * THE LOCALNET TABS, one tail per file the stack writes - 2026-09-21.
     *
     * Until now these four panes were fed by the component sink alone, and
     * `LocalNetRunner` calls it exactly four times in a whole run: `postgres`
     * listening, `canton` started, `splice` started, `web` on its ports. So
     * every tab held ONE line and read as broken, while the run's actual
     * output sat unread in the run directory.
     *
     * THE SINKS ARE LAMBDAS, NOT METHOD REFERENCES ON THE PANES. A field
     * initialiser that named `logSplice::append` would read that field at the
     * moment THIS field is built, and field order would decide whether it was
     * null. A lambda reads it when a line arrives.
     *
     * PostGres keeps its single line: the embedded cluster is this
     * application's own and writes no file of its own here.
     */
    /** What the runner calls Canton's log inside the run directory. */
    private static final String STR_FILE_CANTON_LOG = "canton.log";

    private final transient LogTail tailLocalCanton = new LogTail(
            () -> fileLocalNetLog(STR_FILE_CANTON_LOG),
            strLine -> logParticipant.append(strLine));

    private final transient LogTail tailLocalSplice = new LogTail(
            () -> fileLocalNetLog("splice.log"), strLine -> logSplice.append(strLine));

    /** The raw line on Debug, and worded on the Web tab's Log. */
    private final transient LogTail tailLocalWeb = new LogTail(
            () -> fileLocalNetLog("web.log"), strLine -> {
                logWeb.append(strLine);
                paneWeb.access(WebAccess.strLocalNet(strLine));
            });

    private final transient Timer timerLamp;

    /** Reads the Users tab again every {@link #N_MS_USERS}. */
    private final transient Timer timerUsers;

    /** One read at a time: a tick that finds one under way skips. */
    private final transient AtomicBoolean flagUsersBusy = new AtomicBoolean();

    /**
     * A start or the fixture asked for EVERY ledger user to be written, not
     * only the ones the server lacks - a write sets the password, the rule
     * `SandboxUsers` follows. Held until a read of the ledger runs it.
     */
    private final transient AtomicBoolean flagUsersAllDue = new AtomicBoolean();

    /**
     * For the OIDC lamp: the server answered with its users, and no stack is
     * between a start and its users being written. Written off the event
     * thread.
     */
    private volatile boolean flagUsersComplete;

    /**
     * Counts the window's state changes, so a read begun before a start does
     * not turn the lamp green once the start is under way.
     */
    private final transient AtomicInteger cntUsersEpoch = new AtomicInteger();

    /**
     * The bearer the single participant's users are read with. Minted once
     * and again only when the ledger refuses it, so the access log does not
     * carry a mint every {@link #N_MS_USERS}.
     */
    private transient volatile String strTokenUsers;

    /** The last failure the Users tab reported, so a milestone is written once. */
    private transient volatile String strUsersFailLast;

    private transient SandboxService service;

    /**
     * THE LOCALNET STACK, when this window was opened on that topology.
     *
     * A SECOND FIELD rather than a widened one: the panes that describe ONE
     * participant keep the concrete `SandboxService` and are not rewritten,
     * and {@link #stack()} is what the lifecycle reads.
     */
    private transient LocalNetRunner runner;

    /**
     * SET WHEN STOP IS PRESSED DURING A START, and read by whichever of
     * {@link #startDone} and {@link #startFailed} the start thread reaches.
     *
     * A cancelled start almost always arrives as a FAILURE - the stop takes
     * the participant away and the start thread finds it gone - and that is
     * not what a developer who pressed Stop should be shown. It can also
     * arrive as a SUCCESS, when the stop lands after everything was already
     * up, and that reading is worse: the window would say RUNNING over a
     * stack that has just been taken down. Both ends check this.
     *
     * Volatile because the start thread reads what the event dispatch thread
     * wrote.
     */
    private transient volatile boolean flagCancelStart;

    /** What {@link #enterState} last announced. */
    private transient SandboxService.State stateNow = SandboxService.State.STOPPED;

    /** The last failed start's whole message; see {@link #strFailureLast}. */
    private transient volatile String strFailureLast;

    /**
     * Told every state the window moves through, or null for none.
     *
     * For an unattended harness, which cannot press Stop until it knows the
     * stack came up and cannot move to the next version until it knows this
     * one went down. Polling the pane would have worked and would have been a
     * second definition of what the states are.
     */
    private transient Consumer<SandboxService.State> sinkState;

    private transient Thread threadShutdown;

    /** Which stack this window drives; `DiscoveryDoc.STR_TOPOLOGY_*`. */
    private final transient String strTopology;


    /**
     * @param options what the command line asked for, used to pre-fill the form
     */
    public SandboxWindow(SandboxOptions options) {
        this(options, DiscoveryDoc.STR_TOPOLOGY_SANDBOX);
    }


    /**
     * ONE WINDOW, EITHER TOPOLOGY - D-790, his decision of 2026-09-21.
     *
     * @param options what the command line asked for, used to pre-fill the form
     * @param strTopologyNew which stack this window drives
     */
    public SandboxWindow(SandboxOptions options, String strTopologyNew) {
        super(strTitleFor(strTopologyNew));
        this.strTopology = strTopologyNew;
        this.form = new SandboxForm(options);

        AppIcon.apply(this);

        mapLog.put(StackComponent.STR_POSTGRES, logPostgres);
        mapLog.put(StackComponent.STR_PARTICIPANT, logParticipant);
        mapLog.put(StackComponent.STR_PQS, logPqs);
        mapLog.put(StackComponent.STR_JSON_API, paneJsonApi.log());
        // THE LOCALNET NAMES TOO, in the same map and unconditionally: a
        // component sink that named a pane this window does not show would
        // land in the main log, which is where a reader stops looking.
        mapLog.put(LocalNetRunner.STR_COMP_POSTGRES, logPostgres);
        mapLog.put(LocalNetRunner.STR_NS_CANTON, logParticipant);
        mapLog.put(LocalNetRunner.STR_NS_SPLICE, logSplice);
        mapLog.put(LocalNetRunner.STR_COMP_WEB, logWeb);
        if (isLocalNet()) {
            // READ WHILE THE TOPOLOGY QUESTION WAS OPEN - A-63 (b).
            OpenPrefetch.Splice splice = OpenPrefetch.splice();
            form.useSplice(splice.lstVersion(), splice.mapShown());
            // THE AUTH ROWS ARE FILLED HERE, AND NOWHERE ELSE ON THIS
            // TOPOLOGY. `installChanged` is what fills them on the Sandbox
            // path and it runs off a `CantonInstallation` selection, which
            // this box does not hold - `useSplice` puts Strings in it. So the
            // six rows came up empty, all of them visible, and a start read
            // blanks out of them.
            form.setAuth(authLocalNetDefault());
            // THE NAME THE STACK KNOWS ON THE LEFT, THE ONE A PERSON READS
            // ON THE RIGHT. PQS is listed and reports OFF until there is one.
            Map<String, String> mapLamp = new LinkedHashMap<>();
            mapLamp.put(LocalNetRunner.STR_COMP_POSTGRES, "PostgreSQL");
            mapLamp.put(LocalNetRunner.STR_NS_SPLICE, "Splice");
            mapLamp.put(LocalNetRunner.STR_COMP_WEB, "Web");
            mapLamp.put(StackComponent.STR_PQS, "PQS");
            lamps.setLamps(mapLamp);
        }

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent evt) {
                closeRequested();
            }
        });

        btnStart.addActionListener(evt -> startRequested());
        btnStop.addActionListener(evt -> stopRequested());
        btnReset.addActionListener(evt -> resetRequested());
        btnReset.setEnabled(false);
        btnStop.setEnabled(false);
        btnSave.addActionListener(evt -> saveRequested());
        btnSave.setEnabled(false);
        lstSnapshot.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstSnapshot.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent evt) {
                maybePopup(evt);
            }


            @Override
            public void mouseReleased(MouseEvent evt) {
                maybePopup(evt);
            }
        });
        lstSnapshot.setVisibleRowCount(6);
        // NOT HERE ANY MORE. There is no snapshot store until a version is
        // selected, and the selection is resolved at the end of this
        // constructor once the form can report it.

        tabs.addTab("Sandbox", buildSandboxTab());
        tabs.addTab(STR_TAB_LOGS, buildLogsTab());
        // NOT ON LOCALNET - his instruction, 2026-09-21. The pane describes
        // ONE participant's HTTP Ledger API and a LocalNet has three.
        if (!isLocalNet())
            tabs.addTab(STR_TAB_JSON_API, paneJsonApi);
        JTabbedPane tabsDars = new JTabbedPane();
        if (isLocalNet()) {
            for (String strRole : LST_ROLE_TAB) {
                DarsLivePane paneRole = new DarsLivePane();
                mapDarsLocal.put(strRole, paneRole);
                tabsDars.addTab(strRole, paneRole);
            }
        }
        else {
            tabsDars.addTab("Store", paneDars);
            tabsDars.addTab("Participant", paneDarsLive);
        }
        tabs.addTab("DARs", tabsDars);
        // WEB AT THE TOP LEVEL, ON BOTH TOPOLOGIES - his instruction,
        // 2026-10-02. The pages are the RAWARs as well as Splice's UIs, and a
        // RAWAR is served on either topology.
        tabs.addTab(STR_TAB_WEB, paneWeb);
        // USERS UNDER OIDC, NOT BESIDE IT: they are the provider's users -
        // his instruction, 2026-10-02, and the A-40 reasoning before it.
        JTabbedPane tabsOidc = new JTabbedPane();
        tabsOidc.addTab("Provider", paneJwt);
        tabsOidc.addTab("Users", paneUsers);
        tabs.addTab(STR_TAB_OIDC, tabsOidc);
        paneWeb.useRefresh(this::webRefresh);
        // AND WHENEVER THE TAB IS OPENED, so the list is current without a
        // click - a RAWAR added on disk is served at once, and the tab that
        // names them should not lag behind the server.
        tabs.addChangeListener(evt -> {
            if (tabs.getSelectedComponent() == paneWeb)
                webRefresh();
        });
        tabs.addTab("Settings", paneSettings);

        lblFooter.setForeground(GuiTheme.colMuted());
        // THE LAST MILESTONE, not a standing notice. What stood here said
        // the Ledger API is unauthenticated, which is true, unchanging, and
        // therefore read once and never again - a line of the window's
        // height spent on something the reader already knows. The last thing
        // that happened is worth the same space every second.
        status.useCopySink(this::footerCopied);
        lamps.useClickSink(this::lampClicked);
        paneJwt.useNoticeSink(this::footerNotice);
        // THE JWKS ROW FOLLOWS THE OIDC TAB. Without this the row keeps the
        // provider that was in force when the window opened, and the start
        // polls it - 2026-09-21.
        paneJwt.useProviderSink(form::repointJwks);
        paneSettings.useNoticeSink(this::footerNotice);
        paneSettings.useSavedSink(this::settingsSaved);
        setStateChip(SandboxService.State.STOPPED);
        logMain.useClock(true);
        logLedger.useClock(true);
        // EVERY REQUEST TO A RAWAR, on the Web tab - his instruction,
        // 2026-10-04. Set once; the server keeps it across its rebinds.
        rawar.useAccess(access -> paneWeb.access(WebAccess.strRawar(access)));
        // ONE SHOT. A repeating timer would put the footer back every three
        // seconds for the life of the window, which is a repaint per tick
        // forever to undo something that was already undone.
        timerFooter.setRepeats(false);
        timerSettle.setRepeats(false);

        JPanel pnlPage = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlPage.setBorder(GuiTheme.borderScaled(GuiTheme.N_PAD_PAGE, GuiTheme.N_PAD_PAGE,
                GuiTheme.N_PAD_PAGE, GuiTheme.N_PAD_PAGE));
        pnlPage.add(tabs, BorderLayout.CENTER);
        // NORTH, above the tabs rather than inside one. A machine with no
        // toolchain has nothing to look at on any tab, so the one thing it
        // CAN do should not be behind a tab it has no reason to open.
        banner.useAction(this::installToolchain);
        pnlPage.add(banner, BorderLayout.NORTH);
        refreshBanner();
        this.aviation = new AviationSession(logMain::append, logMain::appendSpinning,
                this::footerStep);
        this.pharma = new PharmaSession(logMain::append, logMain::appendSpinning,
                this::footerStep);
        paneSettings.useAviationAction(this::aviationRequested);
        paneSettings.usePharmaAction(this::pharmaRequested);
        // ONE FIXTURE PER TOPOLOGY ON THE TAB TOO - operator instruction,
        // 2026-09-22. The other one cannot be built on this window and its
        // rows are a question the reader cannot act on.
        paneSettings.setTopologyLocalNet(isLocalNet());
        pharma.useSettingsSink(paneSettings::refresh);
        aviation.useSettingsSink(paneSettings::refresh);
        paneSettings.useFoundingAction(this::resetFoundingRequested);
        // AFTER THE WINDOW IS ON THE SCREEN. The dialog is modal and owned by
        // this frame; raised from inside the constructor it would have a
        // parent that is not showing yet.
        //
        // AND ONE FIXTURE PER TOPOLOGY. Aviation is the Sandbox's - one
        // participant, seven roles inside one organisation - and Pharma is
        // LocalNetND's, because `fixture_pharma.md` is about two organizations
        // on two participants and the other topology has one. Asking both
        // would be two dialogs at open, of which one can never be answered
        // usefully.
        SwingUtilities.invokeLater(isLocalNet() ? this::offerPharma : this::openSingle);

        pnlChipState.setOpaque(false);
        JPanel pnlFooter = new JPanel(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), 0));
        pnlFooter.setOpaque(false);
        pnlFooter.add(lblFooter, BorderLayout.CENTER);
        pnlFooter.add(pnlChipState, BorderLayout.EAST);
        pnlPage.add(pnlFooter, BorderLayout.SOUTH);

        setContentPane(pnlPage);
        LogTee.attach(this::teeLine);

        // The lamps poll rather than being pushed to, because the interesting
        // case is a process that goes down WITHOUT anything in this window
        // being told - which is exactly the case a push would miss.
        timerLamp = new Timer(N_MS_LAMP, evt -> {
            lamps.setStack(stack());
            // NOT A STACK COMPONENT - it runs with or without one - so it is
            // read off the OIDC pane rather than the stack, and green only
            // once the Users tab has read every user - `LampBar.healthOidc`.
            lamps.setOidcHealth(LampBar.healthOidc(paneJwt.isProviderUp(),
                    JwtMintProcess.isExternal(), flagUsersComplete));
            paneJsonApi.refresh();
            // THE SNAPSHOT THE DISCOVERY ENDPOINT SERVES, built here because
            // this is a tick that already holds the event thread. Reading the
            // form from the handler's thread instead would be reading a
            // JTextField from a thread that has no business doing so.
            docDiscovery = docDiscoveryNow();
        });
        timerLamp.setRepeats(true);
        timerLamp.start();
        timerUsers = new Timer(N_MS_USERS, evt -> usersRefresh());
        timerUsers.setRepeats(true);
        timerUsers.start();

        sizeToScreen();

        // LAST, and in this order. The sink is wired first so that any later
        // change of selection reports through the same path, and the first is
        // then pushed through by hand - the form settled on it inside its own
        // constructor, before there was anything here to tell.
        // BEFORE the first selection, because applyProfile lists the directory
        // and an empty directory field means `<run>/dars` - which it can only
        // resolve by asking the form what the run directory is right now.
        paneDars.useRunDirectory(() -> form.run().dirRun());
        paneDarsLive.useAdminPort(this::nPortAdmin);
        paneDarsLive.useNoteSink(this::milestone);
        paneDarsLive.useDarDirectory(() -> form.run().dirDars());
        paneDarsLive.useDarStore(() -> form.run().dirDars());
        for (Map.Entry<String, DarsLivePane> entry : mapDarsLocal.entrySet()) {
            String strRole = entry.getKey();
            entry.getValue().useAdminPort(() -> nPortAdminLocal(strRole));
            entry.getValue().useNoteSink(strLine -> milestone(strRole + ": " + strLine));
            // WHERE EVERY VERSION'S DAR STORE HANGS - `<home>/sandbox/<version>/dars`.
            // LocalNetND has no store of its own, and a chooser opening in the
            // home directory started as far from a built DAR as it could.
            entry.getValue().useDarDirectory(() -> RaposzaSettings.current().dirSandbox());
            entry.getValue().useDarStore(SandboxWindow::dirDarsLocal);
        }
        form.useSdkInstallAction(this::installSdkRequested);
        form.useInstallSink(this::installChanged);
        installChanged(form.selected());
        // LAST, so the first document describes a window that is fully wired.
        discoveryRebind();
        rawarRebind();
        webRefresh();
    }


    /**
     * Rebuilds the Web tab from what is serving right now. Event thread.
     *
     * THE RAWARS ARE LISTED WITH NO STACK, because the server that serves them
     * is the window's and a developer edits pages before starting anything.
     * The stack's own rows - LocalNetND's UIs, the JSON Ledger APIs - only
     * while it runs.
     */
    private void webRefresh() {
        List<WebPane.Row> lstRow = new ArrayList<>();
        String strNodeRawar;
        if (isLocalNet()) {
            strNodeRawar = DiscoveryNode.STR_ROLE_APP_PROVIDER;
            LocalNetRunner runnerHere = runner;
            if (runnerHere != null && stateNow == SandboxService.State.RUNNING)
                lstRow.addAll(WebPane.lstRowLocalNet(runnerHere.lstPageWeb(),
                        mapJsonLocal(runnerHere)));
        }
        else {
            strNodeRawar = StorageOverlay.STR_NODE_PARTICIPANT;
            SandboxService serviceHere = service;
            String strPortJson = serviceHere == null || !serviceHere.isRunning() ? null
                    : serviceHere.report().value(ReadyReport.KEY_JSON_API);
            if (strPortJson != null)
                lstRow.addAll(WebPane.lstRowJson(Map.of(strNodeRawar,
                        "http://127.0.0.1:" + strPortJson)));
        }
        // THE STATE LINE SAYS WHERE THE RAWARS ARE READ FROM, WHAT IS NOT
        // SERVED AND WHEN. A Refresh that found nothing new looked exactly
        // like a Refresh that did nothing - 2026-10-02, his report - and a
        // RAWAR put anywhere but this directory is never found.
        String strState;
        if (rawar.isRunning()) {
            RawarSites.Scan scanNow = rawar.scan();
            lstRow.addAll(WebPane.lstRowRawar(scanNow, rawar.nPort(), strNodeRawar));
            strState = "RAWARs from " + rawar.dirRoot()
                    + (scanNow.lstProblem().isEmpty() ? ""
                            : " - NOT SERVED: " + String.join("; ", scanNow.lstProblem()))
                    + " - refreshed " + LocalTime.now().withNano(0);
        }
        else {
            strState = rawar.strStatus(RaposzaSettings.current().nPortRawar());
        }
        paneWeb.show(lstRow, strState);
    }


    /**
     * Reads the Users tab again. Event thread; every {@link #N_MS_USERS}, and
     * at once after a start, a stop and the fixture.
     *
     * Every row is a user the OIDC SERVER holds - his instruction, 2026-10-04,
     * "The tab belongs to the freakin' server" - so it is read whenever the
     * window's own server runs, with or without a stack. What the window knows
     * besides is the node: on LocalNetND the role users the start registered -
     * `registerAtProvider`; on the single participant every ledger user, which
     * this read writes to the server when the server lacks it - `SandboxUsers`
     * - so a user a Daml script made after the start can sign in within a tick.
     */
    private void usersRefresh() {
        if (JwtMintProcess.isExternal()) {
            paneUsers.show(List.of(), strWhyNoUsers());
            return;
        }
        if (!paneJwt.isMintRunning()) {
            flagUsersComplete = false;
            strTokenUsers = null;
            paneUsers.show(List.of(), "the OIDC server is not running");
            return;
        }
        // A STACK BETWEEN STOPPED AND RUNNING has users still to be written,
        // so no read taken now is every user - the OIDC lamp stays amber.
        boolean flagSettled = stateNow == SandboxService.State.STOPPED
                || stateNow == SandboxService.State.RUNNING;
        if (!flagSettled)
            flagUsersComplete = false;
        if (!flagUsersBusy.compareAndSet(false, true))
            return;
        int nEpoch = cntUsersEpoch.get();

        List<UsersPane.Row> lstKnown = List.of();
        LedgerRead read = null;
        if (isLocalNet()) {
            LocalNetRunner runnerHere = runner;
            String strPassword = strPasswordWebUi();
            if (runnerHere != null && stateNow == SandboxService.State.RUNNING
                    && strPassword != null)
                lstKnown = UsersPane.lstRowLocalNet(runnerHere.lstPageWeb(), strPassword);
        }
        else {
            read = ledgerReadNow();
        }
        List<UsersPane.Row> lstKnownHere = lstKnown;
        LedgerRead readHere = read;
        Thread thread = new Thread(() -> {
            try {
                usersRead(lstKnownHere, readHere, flagSettled, nEpoch);
            }
            finally {
                flagUsersBusy.set(false);
            }
        }, "raposza-users");
        thread.setDaemon(true);
        thread.start();
    }


    /** A start or the fixture: every ledger user is written, not only the new ones. */
    private void usersRefreshAll() {
        flagUsersAllDue.set(true);
        usersRefresh();
    }


    /**
     * What reading the single participant's users needs, taken on the event
     * thread.
     *
     * @param auth what the participant checks
     * @param version the Canton running
     * @param strUrlJson its JSON Ledger API, no trailing slash
     */
    private record LedgerRead(AuthSettings auth, VersionId version, String strUrlJson) {
    }


    /**
     * @return what reading the ledger's users needs, or null when there is no
     *         ledger to read or its tokens are not checked against this server
     */
    private LedgerRead ledgerReadNow() {
        SandboxService serviceHere = service;
        CantonInstallation instNow = form.selected();
        AuthSettings authHere = form.auth();
        String strPortJson = serviceHere == null || !serviceHere.isRunning() ? null
                : serviceHere.report().value(ReadyReport.KEY_JSON_API);
        if (strPortJson == null || instNow == null || authHere == null
                || !authHere.mode().flagTargets())
            return null;
        return new LedgerRead(authHere, instNow.version(), "http://127.0.0.1:" + strPortJson);
    }


    /**
     * The read itself. OFF the event thread: it is HTTP, and a first mint
     * waits for the server.
     *
     * @param lstKnown the rows for the role users a running LocalNetND wrote
     * @param read the single participant's ledger, or null
     * @param flagSettled whether no start or stop was under way
     * @param nEpoch {@link #cntUsersEpoch} when the read was asked for
     */
    private void usersRead(List<UsersPane.Row> lstKnown, LedgerRead read, boolean flagSettled,
            int nEpoch) {
        String strUrlBase = JwtMintProcess.strUrlBase();
        String strStage = "";
        try {
            ProviderUsers.Listing listing = ProviderUsers.read(strUrlBase);
            List<UsersPane.Row> lstKnownAll = lstKnown;
            if (read != null) {
                strStage = STR_USERS_REGISTER_FAILED;
                List<String> lstLedger = lstLedgerUsers(read);
                boolean flagAll = flagUsersAllDue.getAndSet(false);
                List<String> lstWrite = new ArrayList<>();
                for (String strUser : lstLedger) {
                    if (flagAll || !listing.lstName().contains(strUser))
                        lstWrite.add(strUser);
                }
                if (!lstWrite.isEmpty()) {
                    SandboxUsers.register(strUrlBase, lstWrite);
                    String strNote = "OIDC users " + String.join(", ", lstWrite)
                            + " - password " + JwtMintProcess.STR_PASSWORD;
                    SwingUtilities.invokeLater(() -> paneJwt.note(strNote));
                    strStage = "";
                    listing = ProviderUsers.read(strUrlBase);
                }
                lstKnownAll = UsersPane.lstRowSandbox(lstLedger,
                        StorageOverlay.STR_NODE_PARTICIPANT, JwtMintProcess.STR_PASSWORD);
            }
            // THE PASSWORDS FROM THE SERVER'S OWN FILE - his instruction,
            // 2026-10-04. The API never returns one; `ProviderUsers` says why
            // the file may be read here.
            Map<String, String> mapPassword = Map.of();
            String strWhyNoPassword = "";
            try {
                mapPassword = ProviderUsers.mapPassword(Path.of(listing.strFile()));
            }
            catch (IOException | RuntimeException ex) {
                strWhyNoPassword = " - passwords not readable: " + ex.getMessage();
            }
            List<UsersPane.Row> lstRow = UsersPane.lstRowProvider(listing.lstName(), mapPassword,
                    lstKnownAll);
            String strState = lstRow.size() + " OIDC users - " + listing.strFile()
                    + strWhyNoPassword;
            strUsersFailLast = null;
            flagUsersComplete = flagSettled && nEpoch == cntUsersEpoch.get();
            SwingUtilities.invokeLater(() -> paneUsers.show(lstRow, strState));
        }
        catch (IOException ex) {
            flagUsersComplete = false;
            String strWhy = ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            String strLine = !strStage.isEmpty() ? strStage + strWhy
                    : ex instanceof ConnectException ? "waiting for the OIDC server to answer"
                            : "the OIDC server's users could not be read - " + strWhy;
            boolean flagMilestone = !strStage.isEmpty() && !strLine.equals(strUsersFailLast);
            strUsersFailLast = strLine;
            SwingUtilities.invokeLater(() -> {
                paneUsers.state(strLine);
                if (flagMilestone)
                    milestone(strLine);
            });
        }
        catch (InterruptedException ex) {
            flagUsersComplete = false;
            Thread.currentThread().interrupt();
        }
    }


    /**
     * The participant's users, with the bearer minted last time while the
     * ledger takes it - see {@link #strTokenUsers}.
     *
     * @param read the ledger
     * @return its users, in ledger order
     * @throws IOException when the ledger refuses a fresh token too
     * @throws InterruptedException when interrupted while waiting
     */
    private List<String> lstLedgerUsers(LedgerRead read) throws IOException, InterruptedException {
        String strToken = strTokenUsers;
        if (strToken != null) {
            try {
                return SandboxUsers.lstRead(read.strUrlJson(), strToken);
            }
            catch (IOException ex) {
                // EXPIRED, or another stack since: minted again below.
                strTokenUsers = null;
            }
        }
        strToken = JwtMintProcess.strMintBlocking(read.auth(), read.version(),
                JwtPane.STR_USER_ADMIN, StorageOverlay.STR_NODE_PARTICIPANT, DUR_USERS_MINT);
        if (strToken == null)
            throw new IOException("the provider minted no token for " + JwtPane.STR_USER_ADMIN
                    + " within " + DUR_USERS_MINT);
        List<String> lstOut = SandboxUsers.lstRead(read.strUrlJson(), strToken.trim());
        strTokenUsers = strToken.trim();
        return lstOut;
    }


    /**
     * @return why the window registered nobody - an external provider is
     *         somebody else's, and a stack with auth off checks no token
     */
    private String strWhyNoUsers() {
        if (JwtMintProcess.isExternal())
            return "an external provider - its users are managed there";
        return "auth is off - no token is checked, so nobody signs in";
    }


    /**
     * Binds the RAWAR server, or says why it could not - the shape and the
     * reasons of {@link #discoveryRebind}: silent on success, a notice and
     * not a dialog on a conflict.
     *
     * A MOVED HOME MOVES THE PAGES. The directory is read from the settings
     * every time, so a bind that kept the old one would serve the RAWARs of a
     * home the Settings tab no longer names.
     */
    private void rawarRebind() {
        RaposzaSettings settingsNow = RaposzaSettings.current();
        int nPortWanted = settingsNow.nPortRawar();
        Path dirWanted = settingsNow.dirRawars();
        if (rawar.isRunning() && rawar.nPort() == nPortWanted && dirWanted.equals(rawar.dirRoot()))
            return;
        try {
            rawar.start(nPortWanted, dirWanted, () -> docDiscovery);
        }
        catch (IOException ex) {
            milestone("RAWAR port " + nPortWanted + " is in use - change the RAWAR port on the"
                    + " Settings tab");
        }
    }


    /**
     * Says where the RAWARs are, once a stack is up, and registers their
     * client at the window's own provider when it is checking clients -
     * {@link RawarClient}. Off the event thread: the provider is a request
     * away.
     *
     * NOTHING WHEN NONE IS SERVED. A developer who has written no page has
     * nothing to be told about, and a line per start saying so is noise.
     */
    private void rawarAnnounce() {
        if (!rawar.isRunning())
            return;
        List<String> lstMount = rawar.scan().lstMount();
        if (lstMount.isEmpty())
            return;
        milestone("RAWARs - " + rawar.strStatus(rawar.nPort()));
        AuthSettings authHere = form.auth();
        if (JwtMintProcess.isExternal() || authHere == null || !authHere.mode().flagTargets()
                || !paneJwt.isMintRunning())
            return;
        List<String> lstRedirect = RawarEnv.lstRedirect(rawar.nPort(), lstMount);
        Thread thread = new Thread(() -> {
            try {
                String strLine = RawarClient.strRegister(JwtMintProcess.strUrlBase(), lstRedirect);
                SwingUtilities.invokeLater(() -> paneJwt.note(strLine));
            }
            catch (IOException ex) {
                String strLine = "RAWAR client registration FAILED, the pages cannot sign in - "
                        + ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    paneJwt.note(strLine);
                    milestone(strLine);
                });
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "raposza-rawar-client");
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * Binds the discovery endpoint, or says why it could not.
     *
     * A SUCCESSFUL BIND IS SILENT. It happens before the first start and had
     * nothing to do with one, so the line sat above `Starting Sandbox` in every
     * run and was read as the first step of a start. The port is on the
     * Settings tab, which is where somebody looking for it goes.
     *
     * A CONFLICT IS A NOTICE, NOT A DIALOG. An unattended harness opens this window and
     * leaves it running unattended for hours, and a modal here would stop the
     * whole run over a port nothing in the run reads, which is the hang
     * {@link Modals} exists for with a different cause. The line names the
     * port and the setting that moves it.
     */
    private void discoveryRebind() {
        int nPortWanted = RaposzaSettings.current().nPortDiscovery();
        if (discovery.isRunning() && discovery.nPort() == nPortWanted)
            return;

        // BEFORE the bind. A request can arrive on the first millisecond and
        // there is no reason for it to be told there is no snapshot yet.
        this.docDiscovery = docDiscoveryNow();
        try {
            discovery.start(nPortWanted, () -> docDiscovery);
            // AGAIN, now that the port is known. The document names the port it
            // was served from, and the one built a moment ago named zero.
            this.docDiscovery = docDiscoveryNow();
        }
        catch (IOException ex) {
            milestone("Discovery port " + nPortWanted + " is in use - change the Discovery"
                    + " port on the Settings tab");
        }
    }


    /**
     * What the status table says about the endpoint.
     *
     * NOT `0`. An unbound endpoint reported its port as zero, and a table row
     * reading `port.discovery 0` is a port as far as anybody reading it is
     * concerned - so the one state worth stating was the one state that looked
     * like every other row.
     *
     * THE TABLE AND NOT THE LOG, because the log is where this went wrong: the
     * conflict is one line, it is printed at window open, and by the time a
     * stack is up it is some hundred lines above the bottom of a pane that
     * follows its own tail. The table is four rows and it does not move.
     *
     * @return the port, or what is wrong and which setting moves it
     */
    private String strPortDiscovery() {
        if (discovery.isRunning())
            return String.valueOf(discovery.nPort());
        return "NOT BOUND - port " + RaposzaSettings.current().nPortDiscovery()
                + " is in use, change it on the Settings tab";
    }


    /**
     * Says that the stack is up and cannot be found.
     *
     * Called from both done paths, after the state is RUNNING. Everything the
     * window reports at that moment says the stack is serving, and on this one
     * path all of it is true and none of it is reachable by the thing that
     * looks for it.
     */
    private void discoveryWarnIfUnbound() {
        if (discovery.isRunning())
            return;
        milestone("No discovery endpoint - port " + RaposzaSettings.current().nPortDiscovery()
                + " is in use, so the Workbench cannot find this stack; change the"
                + " Discovery port on the Settings tab");
    }


    /**
     * What the window would publish right now. Called on the event thread only.
     *
     * THE REPORT IS GATED ON RUNNING, and not on being non-null: the service
     * keeps its last report after a stop, so a document that took it whenever
     * it existed would publish the ports of a stack that went down ten minutes
     * ago. Same rule as {@link #nPortAdmin}.
     *
     * @return never null
     */
    private DiscoveryDoc docDiscoveryNow() {
        if (isLocalNet())
            return docDiscoveryLocalNet();

        SandboxService serviceHere = service;
        CantonInstallation inst = form.selected();
        boolean flagUp = serviceHere != null && serviceHere.isRunning();
        ReadyReport reportHere = flagUp ? serviceHere.report() : null;
        // THE LEDGER USER THE BOOTSTRAP MADE, which is the one a consumer has
        // any reason to speak as. Absent before a start, and the token url goes
        // with it - a url minting for a user that does not exist yet
        // authenticates and then authorises nothing.
        // FALLING BACK TO THE ADMIN USER RATHER THAN PUBLISHING NOTHING. The
        // bootstrap writes `user.id` only when the Sandbox was asked for a
        // party and a user, and a hand-over with no way to get a token is no
        // hand-over. `participant_admin` exists on every fresh participant and
        // is the user PQS already speaks as.
        String strUser = reportHere == null ? null : reportHere.value(ReadyReport.KEY_USER_ID);
        if (flagUp && (strUser == null || strUser.isBlank()))
            strUser = JwtPane.STR_USER_ADMIN;

        // ONE NODE, and the list is the only place a stack is described. The
        // report is GATED ON RUNNING and not on being non-null: the service
        // keeps its last report after a stop, so a document that took it
        // whenever it existed would publish the ports of a stack that went
        // down ten minutes ago.
        List<DiscoveryNode> lstNode = reportHere == null ? List.of()
                : List.of(new DiscoveryNode(StorageOverlay.STR_NODE_PARTICIPANT,
                        DiscoveryNode.STR_ROLE_APP_PROVIDER, form.auth(),
                        JwtMintProcess.strUrlJwks(), paneJwt.strUrlTokenFor(strUser),
                        paneJwt.mapOidcFor(strUser), reportHere));

        return new DiscoveryDoc(stateNow.name(), discovery.nPort(),
                DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                inst == null ? null : inst.version().toString(),
                inst == null ? null : inst.edition().name(),
                form.run().dirRun(), fileCantonLog(),
                paneJwt.isMintRunning(),
                JwtMintProcess.isExternal() ? DiscoveryDoc.STR_MODE_EXTERNAL
                        : DiscoveryDoc.STR_MODE_EMBEDDED,
                JwtMintProcess.mapProvider(), lstNode);
    }


    /**
     * WHAT THE WINDOW PUBLISHES ON THE LOCALNETND TOPOLOGY - `todo.md` A-31 (1).
     *
     * <h2>Why it is a method of its own and not a widened one</h2>
     *
     * Every field the Sandbox spelling reads is a Sandbox field. `service` is
     * null here, so the node list was empty; `form.selected()` is null, so the
     * Canton was absent; `form.run().dirRun()` is the Sandbox's run directory,
     * not `~/.splice/native-localnet`; and the topology was the literal
     * `sandbox` whatever the window was driving. Measured against the served
     * document, 2026-09-22: state, the discovery port and the provider block
     * were the only three fields that meant anything.
     *
     * <h2>THREE NODES, AND THE NAME IS THE ROLE</h2>
     *
     * `DiscoveryNode` states it: on LocalNet the two coincide, because there
     * the role IS the Canton node name. The order is `LocalNetPorts.LST_ROLE` -
     * sv first - which is the order everything else in this application lists
     * them in.
     *
     * <h2>ONE AUTH BLOCK FOR THE THREE</h2>
     *
     * `LocalNetAuth` gives all three roles the same audience, so a token minted
     * for one works against whichever Ledger API it is presented to - which is
     * a property of the stack rather than of the document, and the document
     * states it by carrying the same block on each node.
     *
     * <h2>AND THE TOKEN URL MINTS FOR `participant_admin`</h2>
     *
     * Not for the role users. Those are written to the provider at every start
     * - `MintRegistration`, `todo.md` A-34 (b) - and a url that mints for a
     * user the participant does not have authenticates and then authorises
     * nothing, which is the defect the Sandbox path has a comment about.
     *
     * @return never null
     */
    private DiscoveryDoc docDiscoveryLocalNet() {
        LocalNetRunner runnerHere = runner;
        CantonInstallation instStock = instCantonStock();
        // GATED ON RUNNING, exactly as the Sandbox one is: a runner that has
        // stopped still answers for its ports, and a document that published
        // them would describe a stack that went down ten minutes ago.
        boolean flagUp = runnerHere != null && stateNow == SandboxService.State.RUNNING;
        LocalNetPorts portsHere = portsLocalNet();
        Path dirRun = runnerHere == null ? null : runnerHere.dirRun();

        List<DiscoveryNode> lstNode = new ArrayList<>();
        if (flagUp && instStock != null) {
            for (String strRole : LocalNetPorts.LST_ROLE) {
                lstNode.add(new DiscoveryNode(strRole, strRole, form.auth(),
                        JwtMintProcess.strUrlJwks(),
                        paneJwt.strUrlTokenFor(JwtPane.STR_USER_ADMIN),
                        paneJwt.mapOidcFor(JwtPane.STR_USER_ADMIN),
                        new ReadyReport(instStock.version().toString(),
                                instStock.edition().name(), strRole, LocalNetRunner.STR_HOST,
                                portsHere, dirRun, runnerHere.dirPgData())));
            }
        }

        return new DiscoveryDoc(stateNow.name(), discovery.nPort(),
                DiscoveryDoc.STR_TOPOLOGY_LOCALNET,
                instStock == null ? null : instStock.version().toString(),
                instStock == null ? null : instStock.edition().name(),
                dirRun, fileLocalNetLog(STR_FILE_CANTON_LOG),
                paneJwt.isMintRunning(),
                JwtMintProcess.isExternal() ? DiscoveryDoc.STR_MODE_EXTERNAL
                        : DiscoveryDoc.STR_MODE_EMBEDDED,
                JwtMintProcess.mapProvider(), List.copyOf(lstNode));
    }


    /**
     * A different Canton: a different profile, different snapshots, and
     * possibly a different set of tabs.
     *
     * @param instNew what is selected now, or null when nothing startable is
     *        installed
     */
    private void installChanged(CantonInstallation instNew) {
        // BEFORE THE GUARD BELOW, and on every call. On the LocalNet topology
        // `selected()` is always null, so `instNew` never changes and every
        // call after the first would return early - while the SPLICE version
        // behind the founding key has moved. And running it at all here is
        // what makes `Reset founding` work from window open rather than from
        // the first start - operator instruction, 2026-09-22.
        if (isLocalNet()) {
            founding = foundingOfLocalNet();
            // AND THE USER SNAPSHOTS, which is A-35 (a): this field was never
            // assigned on this topology, so the list, Save and the right-click
            // Delete were drawn and reached nothing.
            snapshots = snapshotsOfLocalNet();
            strFoundingTried = null;
            refreshFoundingState();
            refreshSnapshots();
        }
        boolean flagFirst = flagFirstSelect;
        if (!flagFirst && Objects.equals(installProfile, instNew))
            return;

        // THE OUTGOING VERSION, and before anything overwrites the fields.
        if (!flagFirst)
            saveProfile();

        flagFirstSelect = false;
        installProfile = instNew;

        // A NAME CARRIED OVER FROM ANOTHER VERSION IS NOT A SELECTION. The
        // roots are separate, so a same-named snapshot under the new version
        // would be a different cluster silently armed for the next start.
        lstSnapshot.clearSelection();

        if (instNew == null) {
            // NOT ON THE LOCALNET TOPOLOGY, for the reason the founding key
            // gives below: `selected()` is always null there, so this branch
            // runs on every call and would undo what the top of this method
            // just worked out from the Splice box.
            if (!isLocalNet())
                snapshots = null;
            // NOT ON THE LOCALNET TOPOLOGY. `selected()` is ALWAYS null
            // there, so this branch is the one that runs on every call - and
            // it was undoing the key the top of this method had just worked
            // out from the Splice box, four lines earlier. That is why
            // `Reset founding` stayed dead until the first start put the key
            // back: measured at his console, 2026-09-22.
            if (!isLocalNet()) {
                founding = null;
                strFoundingTried = null;
            }
            // No version, so there is no version's DAR setting to show. Back
            // to following the run directory, everything in it ticked.
            paneDars.reset();
            form.applyCaps();
            applyTabs(null);
            refreshSnapshots();
            refreshFoundingState();
            return;
        }

        SandboxProfile profile = profiles.read(instNew, profileFallback(instNew, flagFirst));
        form.applyProfile(profile);
        // AFTER the form, because the pane reads the run directory out of it
        // whenever the profile names no directory of its own.
        paneDars.applyProfile(profile);
        form.applyCaps();
        snapshots = Snapshots.ofVersion(instNew.version(), instNew.edition());
        // THE FORM'S PORTS ARE IN THE KEY - D-852 - and the profile was applied
        // four lines up. Fields that are not ports leave no founding store
        // until the start, which refuses them by name.
        try {
            founding = foundingOfSandbox(instNew);
        }
        catch (IllegalArgumentException ex) {
            founding = null;
        }
        strFoundingTried = null;
        applyTabs(instNew);
        refreshSnapshots();
        refreshFoundingState();
    }


    /**
     * @param inst the version being selected
     * @param flagFirst whether this is the window's first selection
     * @return what a version with no profile file on disk starts from
     */
    private SandboxProfile profileFallback(CantonInstallation inst, boolean flagFirst) {
        SandboxProfile profileDefault =
                SandboxProfile.ofDefaults(inst.version(), inst.edition());
        if (!flagFirst)
            return profileDefault;

        SandboxProfile profileForm;
        try {
            profileForm = form.profile();
        }
        catch (RuntimeException ex) {
            // A command line the form could not turn into a profile. The
            // version's own defaults, and the start will refuse the field
            // itself with a message that names it.
            return profileDefault;
        }

        // THE RUN DIRECTORY IS THE ONE EXCEPTION. The command line's default
        // is the shared `~/.raposza/sandbox`, which is not any version's,
        // and taking it here would give the first version selected a run
        // directory the others do not get.
        if (profileForm.dirRun().equals(SandboxOptions.dirWorkDefault().toAbsolutePath()
                .normalize())) {
            return new SandboxProfile(profileForm.nPortFirst(), profileForm.nPortPostgres(),
                    profileDefault.dirRun(), profileForm.nSecondsReady(),
                    profileForm.dirDars(), profileForm.lstDarSelected(),
                    profileForm.auth());
        }
        return profileForm;
    }


    /**
     * @param inst what is selected, or null
     */
    private void applyTabs(CantonInstallation inst) {
        // THE FORM HAS NO CANTON SELECTED ON THIS TOPOLOGY, so every answer
        // below would be computed from nulls and would show, hide and reshow
        // a tab and a lamp that this window does not have.
        if (isLocalNet())
            return;
        boolean flagJsonApi = SandboxCapabilities.isExposed(SandboxFeature.JSON_API_PROCESS,
                inst == null ? null : inst.version(), inst == null ? null : inst.edition());
        setTabShown(STR_TAB_JSON_API, paneJsonApi, N_TAB_JSON_API, flagJsonApi);
        lamps.setJsonApiShown(flagJsonApi);
    }


    /**
     * @param strTitle the tab's title, which is also how it is found again
     * @param comp what the tab holds
     * @param idxWanted where it goes when it comes back
     * @param flagShow whether it belongs on this version
     */
    private void setTabShown(String strTitle, Component comp, int idxWanted, boolean flagShow) {
        int idxNow = tabs.indexOfTab(strTitle);
        if (flagShow == (idxNow >= 0))
            return;

        if (flagShow)
            tabs.insertTab(strTitle, null, comp, null, Math.min(idxWanted, tabs.getTabCount()));
        else
            tabs.removeTabAt(idxNow);
    }


    /**
     * Writes what the fields hold to the profile of whichever version they
     * belong to.
     *
     * A profile that cannot be written is REPORTED and nothing more. It must
     * not refuse a start and must not refuse a close: the settings are a
     * convenience, and losing them is a smaller failure than a window that
     * will not shut over a read-only home directory.
     */
    private void saveProfile() {
        CantonInstallation inst = installProfile;
        if (inst == null)
            return;

        try {
            profiles.write(inst, form.profile()
                    .withDars(paneDars.dirDars(), paneDars.lstSelected()));
        }
        catch (RuntimeException ex) {
            milestone("Could not save the profile: " + ex.getMessage());
        }
    }


    /**
     * @return the running service, or null; the data pane asks each time
     */
    /**
     * @return where Canton is writing, or null; the tail asks each poll
     */
    private Path fileCantonLog() {
        SandboxService serviceHere = service;
        return serviceHere == null ? null : serviceHere.fileCantonLog();
    }


    /**
     * @param strName the file's name in the run directory
     * @return where the LocalNet stack is writing it, or null when no stack
     *         has been built yet; the tail asks each poll
     */
    private Path fileLocalNetLog(String strName) {
        LocalNetRunner runnerHere = runner;
        return runnerHere == null ? null : runnerHere.dirRun().resolve(strName);
    }


    /**
     * ALL OF THEM, from the places that used to stop one. A tail that was
     * never started is stopped safely - `LogTail.stop` only clears a flag and
     * interrupts a thread that may not exist - so this needs no topology test.
     */
    private void stopTails() {
        tailCanton.stop();
        tailLocalCanton.stop();
        tailLocalSplice.stop();
        tailLocalWeb.stop();
    }


    /**
     * Public for an unattended harness, which reads the three healths and the
     * ready report off it while a cell is up. The panes that take it as a
     * supplier were the only callers until then, and none of them needed it
     * from outside this class.
     *
     * @return the running service, or null when nothing is up
     */
    public SandboxService service() {
        return service;
    }


    /**
     * The whole of the last failed start's message: the exit code, the ERROR
     * and WARN lines of the Canton log, its tail and standard output, as
     * `SandboxStack.failure` assembled them.
     *
     * Public for an unattended harness. Until it existed this text went to
     * the participant pane and nowhere else, so a long unattended run could
     * report that many of its steps failed and not one reason.
     *
     * @return the message, or null when the last start did not fail or said
     *         nothing
     */
    public String strFailureLast() {
        return strFailureLast;
    }


    /**
     * The launch form, the buttons, the lamps, the report table and the tee'd
     * log - everything that is about the stack as a whole rather than about
     * one component of it.
     *
     * @return the first tab
     */
    private JPanel buildSandboxTab() {
        JPanel pnlButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,
                GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnStart);
        pnlButtons.add(btnStop);
        pnlButtons.add(btnReset);
        pnlButtons.add(btnSave);

        // NORTH, not CENTER. A GridBagLayout inside a BorderLayout centre
        // slot is centred VERTICALLY, which put a third of the card's height
        // above the first field and the buttons a screen away from the last
        // one. The stack pins the form to the top and the buttons under it.
        JPanel pnlStack = new JPanel();
        pnlStack.setLayout(new BoxLayout(pnlStack, BoxLayout.Y_AXIS));
        pnlStack.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        pnlButtons.setAlignmentX(LEFT_ALIGNMENT);
        pnlStack.add(form);
        pnlStack.add(Box.createVerticalStrut(GuiTheme.scale(GuiTheme.N_GAP)));
        pnlStack.add(pnlButtons);
        pnlStack.add(Box.createVerticalStrut(GuiTheme.scale(GuiTheme.N_GAP)));

        JLabel lblSnapshot = new JLabel("Snapshots");
        lblSnapshot.setAlignmentX(LEFT_ALIGNMENT);
        pnlStack.add(lblSnapshot);

        JScrollPane scrollSnapshot = new JScrollPane(lstSnapshot);
        scrollSnapshot.setAlignmentX(LEFT_ALIGNMENT);
        scrollSnapshot.setBorder(javax.swing.BorderFactory.createLineBorder(
                GuiTheme.colCardBorder(), 1, true));

        // THE LIST TAKES WHAT IS LEFT - operator instruction. Inside the
        // stack it was one more component at its preferred height, so the
        // card stopped partway down the panel and the space under it was
        // empty however tall the window was.
        JPanel pnlForm = new JPanel(new BorderLayout());
        pnlForm.setOpaque(false);
        pnlForm.add(pnlStack, BorderLayout.NORTH);
        pnlForm.add(scrollSnapshot, BorderLayout.CENTER);

        JPanel pnlFormCard = GuiTheme.card("Sandbox", pnlForm);
        // THE CARD FOLLOWS THE VIEWPORT'S WIDTH. Without this the scroll pane
        // lays the card out at its own preferred width and clips whatever does
        // not fit, which is the horizontal scrollbar and the cut-off fields.
        // Tracking the width instead makes the fields narrow with the divider,
        // which is what a divider is for.
        JPanel pnlFormTrack = new WidthTracking(pnlFormCard);
        JScrollPane scrollForm = new JScrollPane(pnlFormTrack);
        scrollForm.setBorder(null);
        scrollForm.getVerticalScrollBar().setUnitIncrement(GuiTheme.scale(16));
        scrollForm.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // SMALL, so the divider can be dragged LEFT. A minimum set to what the
        // form would like is a minimum the operator cannot get past, and the
        // split pane refuses the drag with no explanation.
        scrollForm.setMinimumSize(new Dimension(GuiTheme.scale(160), GuiTheme.scale(240)));

        JPanel pnlStatus = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlStatus.setOpaque(false);
        pnlStatus.add(lamps, BorderLayout.NORTH);
        pnlStatus.add(status, BorderLayout.CENTER);

        // TWO LOGS IN ONE CARD: what the application did, and what the
        // ledger did - his instruction, 2026-10-04; `Main`, not `Application`,
        // his correction the same day.
        JTabbedPane tabsLog = new JTabbedPane();
        tabsLog.addTab("Main", logMain);
        tabsLog.addTab("Ledger", logLedger);
        JSplitPane splitRight = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                GuiTheme.card("Status", pnlStatus), GuiTheme.card(null, tabsLog));
        splitRight.setBorder(null);
        splitRight.setResizeWeight(0.45);
        splitRight.setDividerSize(GuiTheme.scale(8));
        // THE SAME TREATMENT AS THE OUTER SPLIT, and for the same reason.
        // `setResizeWeight` governs where GROWTH goes, not where the divider
        // starts; without this the first position comes from preferred sizes,
        // which differ per platform - measured as roughly 70/30 on Linux and
        // 87/13 on Windows from the same build.
        splitRight.addComponentListener(new ComponentAdapter() {

            private boolean flagPlaced;

            @Override
            public void componentResized(ComponentEvent evt) {
                if (flagPlaced || splitRight.getHeight() <= 0)
                    return;
                flagPlaced = true;
                splitRight.setDividerLocation(N_FRACTION_STATUS);
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollForm, splitRight);
        split.setBorder(null);
        split.setResizeWeight(N_FRACTION_FORM);
        split.setDividerSize(GuiTheme.scale(8));
        // 40 / 60 - his instruction, 2026-10-04 - AND HELD AT EVERY RESIZE
        // UNTIL THE DIVIDER IS DRAGGED. Placed once, on the first resize, it
        // opened at 58 percent at his console on 2026-10-04: a width the split
        // was given after the first placement did not keep the fraction, and
        // which layout pass did that was not measured. Re-applying it on every
        // resize does not depend on the answer.
        //
        // A DRAG IS A PRESS ON THE DIVIDER. A change of the divider location
        // is not: the split's own layout moves it on every resize - measured
        // under Xvfb, JDK 21, three resizes each read as a drag - so only the
        // mouse on the divider stops the re-placing, and from then on the
        // divider stays where the developer put it.
        split.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent evt) {
                if (flagDividerDragged || split.getWidth() <= 0)
                    return;
                split.setDividerLocation(N_FRACTION_FORM);
            }
        });
        if (split.getUI() instanceof BasicSplitPaneUI uiSplit) {
            uiSplit.getDivider().addMouseListener(new MouseAdapter() {

                @Override
                public void mousePressed(MouseEvent evt) {
                    flagDividerDragged = true;
                }
            });
        }

        JPanel pnlTab = new JPanel(new BorderLayout());
        pnlTab.setOpaque(false);
        pnlTab.setBorder(GuiTheme.borderScaled(GuiTheme.N_GAP, 0, 0, 0));
        pnlTab.add(split, BorderLayout.CENTER);
        return pnlTab;
    }


    /**
     * @param log the component's own output
     * @param paneData what is in it
     * @return a Log / Data pair
     */
    /**
     * The admin API port of the stack that is up.
     *
     * READ OFF THE REPORT rather than off the form: the form says what the
     * next start will ask for, and the report says what the running
     * participant actually bound.
     *
     * PUBLIC because an unattended harness's DAR step calls the admin API directly rather
     * than through the button, which opens a file chooser nothing gates.
     *
     * @return the port, or 0 when nothing is running
     */
    public int nPortAdmin() {
        SandboxService serviceHere = service;
        if (serviceHere == null || !serviceHere.isRunning() || serviceHere.report() == null)
            return 0;

        String strPort = serviceHere.report().value(ReadyReport.KEY_ADMIN_API);
        if (strPort == null || strPort.isBlank())
            return 0;
        try {
            return Integer.parseInt(strPort.trim());
        }
        catch (NumberFormatException ex) {
            return 0;
        }
    }


    /**
     * THE DAR STORE OF THE CANTON THE THREE PARTICIPANTS RUN INSIDE -
     * `&lt;home&gt;/sandbox/&lt;version&gt;-&lt;edition&gt;/dars`, the same
     * directory Sandbox Simple's Store tab lists for that Canton. One DAR is
     * one LF build and the three participants share one Canton, so one store
     * serves all three tabs.
     *
     * @return the store, or null when no installed Canton carries a runtime
     */
    private static Path dirDarsLocal() {
        CantonInstallation instStock = instCantonStock();
        if (instStock == null)
            return null;
        return SandboxProfile.dirRunDefault(instStock.version(), instStock.edition())
                .resolve(RunDirectory.STR_DIR_DARS);
    }


    /**
     * One LocalNetND participant's admin API, for its DARs tab.
     *
     * @param strRole sv, app-provider or app-user
     * @return the port, or 0 when LocalNetND is not running
     */
    private int nPortAdminLocal(String strRole) {
        LocalNetRunner runnerHere = runner;
        if (runnerHere == null || runnerHere.state() != StackService_i.State.RUNNING)
            return 0;
        return runnerHere.ports().nPortAdmin(strRole);
    }


    /**
     * THE PASSWORD THE WEB UIs TAB SHOWS: the one the window writes to its own
     * provider at start - `registerAtProvider`, under the same condition - or
     * null where it wrote nothing.
     *
     * @return `123456`, or null
     */
    private String strPasswordWebUi() {
        LocalNetAuth authHere = authLocalRunning;
        if (JwtMintProcess.isExternal() || authHere == null || !authHere.isProvider())
            return null;
        return JwtMintProcess.STR_PASSWORD;
    }


    /**
     * @param runnerHere the stack that is up
     * @return role to its JSON Ledger API base url, in the tab order
     */
    private static Map<String, String> mapJsonLocal(LocalNetRunner runnerHere) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        for (String strRole : LST_ROLE_TAB) {
            mapOut.put(strRole, "http://" + LocalNetRunner.STR_HOST + ":"
                    + runnerHere.ports().nPortJson(strRole));
        }
        return mapOut;
    }


    /**
     * THE PORT ACTUALLY BOUND, which is not the port in the settings when the
     * bind was refused. A harness asking the settings would poll a port nothing
     * is answering on and report every install red.
     *
     * @return the discovery endpoint's port, or 0 when nothing is bound
     */
    public int nPortDiscovery() {
        return discovery.nPort();
    }


    /**
     * The three component logs, one sub-tab each.
     *
     * <h2>ONE TAB, NOT THREE</h2>
     *
     * A log is opened when something went wrong, and the reader does not yet
     * know which process it was. Three top-level tabs put that decision before
     * the reading; one tab and three sub-tabs put it after.
     *
     * The data panes that sat beside PostGres and Participant are not shown
     * anywhere. `PostgresDataPane` and `ParticipantDataPane` stay in the source
     * and stay reset with the run, so nothing that reads them changes.
     *
     * @return the Logs tab
     */
    private JPanel buildLogsTab() {
        JTabbedPane inner = new JTabbedPane();
        if (isLocalNet()) {
            // FOUR, BY SOURCE - D-791. LocalNetND is two JVMs and sv,
            // app-provider and app-user share one of them, so three
            // participant tabs would be three views of one file.
            inner.addTab("PostGres", logPostgres);
            inner.addTab("Canton", logParticipant);
            inner.addTab("Splice", logSplice);
            inner.addTab("Web", logWeb);
            // AND PQS, which is the window's own process on this topology and
            // has nowhere else to print - his instruction of 2026-09-22.
            inner.addTab("PQS", logPqs);
        }
        else {
            inner.addTab("PostGres", logPostgres);
            inner.addTab("Participant", logParticipant);
            inner.addTab("PQS", logPqs);
        }
        // THE OPENID PROVIDER'S OWN OUTPUT, moved here off the OIDC tab - his
        // instruction, 2026-10-04. That tab shows who asked it for what.
        inner.addTab("OIDC", paneJwt.logService());
        // UNWRAPPED, every pane here - A-63, measured 2026-10-04: a wrapped
        // area of megabytes held the event thread 3 to 8 s per validate in
        // `WrappedPlainView.breakLines`. `LogPane` says why; Wrap still turns
        // it back on, pane by pane.
        for (int idxTab = 0; idxTab < inner.getTabCount(); idxTab++) {
            if (inner.getComponentAt(idxTab) instanceof LogPane paneLog)
                paneLog.useWrap(false);
        }

        JPanel pnlTab = new JPanel(new BorderLayout());
        pnlTab.setOpaque(false);
        pnlTab.setBorder(GuiTheme.borderScaled(GuiTheme.N_GAP, 0, 0, 0));
        pnlTab.add(inner, BorderLayout.CENTER);
        return pnlTab;
    }


    /**
     * @param options what the command line asked for
     */
    public static SandboxWindow open(SandboxOptions options) {
        return open(options, DiscoveryDoc.STR_TOPOLOGY_SANDBOX);
    }


    /**
     * @param options what the command line asked for
     * @param strTopology which stack the window drives
     * @return the window, on screen
     */
    public static SandboxWindow open(SandboxOptions options, String strTopology) {
        SandboxWindow window = new SandboxWindow(options, strTopology);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        // REVALIDATED ONCE IT IS ON SCREEN. Several of the panels size
        // themselves from a width that does not exist until the frame is
        // realised - the split's 0.40 among them - and a layout computed
        // before that leaves fields clipped until the operator resizes the
        // window by hand. The pass is one shot and costs nothing.
        SwingUtilities.invokeLater(() -> {
            window.getContentPane().revalidate();
            window.getContentPane().repaint();
        });
        // THE MINT IS NOT STARTED HERE. It comes up with the stack and goes
        // down with it, so that the participant reading its JWKS and the
        // service serving it have one lifetime rather than two. The JWT
        // tab's Restart still starts it alone, which is how it is looked at
        // without a Canton.
        return window;
    }


    /**
     * Called from the pump thread of whichever process produced the line.
     *
     * @param strComponent a {@link StackComponent} name
     * @param strLine one line, without its terminator
     */
    /**
     * The tee'd stream: everything slf4j and anything printed directly.
     *
     * It all stays in the Sandbox tab, which is the union and is where a start
     * is diagnosed. PostgreSQL's share is COPIED to its own tab rather than
     * moved, because a line that appears in exactly one of two panes is a line
     * someone will look for in the other.
     *
     * @param strLine one line, without its terminator
     */
    private void teeLine(String strLine) {
        logMain.tick();
        // ROUTED, NOT COPIED. This used to append every tee'd line to the
        // Participant tab and then ALSO copy the PostgreSQL ones to the
        // PostGres tab, so Zonky's initdb narration and the server's own log
        // sat in the middle of Canton's. A line belongs to one component.
        if (strLine.contains(STR_LOGGER_PG) || strLine.contains(STR_LOGGER_ZONKY)) {
            logPostgres.append(strLine);
            return;
        }
        if (strLine.contains(STR_LOGGER_PQS)) {
            logPqs.append(strLine);
            return;
        }
        logParticipant.append(strLine);
    }


    /**
     * One milestone: into the pane, spinning when it names something still
     * happening, and into the footer either way.
     *
     * @param strLine what {@link Milestones} produced
     */
    /**
     * A step of something already announced, for the footer alone.
     *
     * NOT IN THE PANE. The pane is the record of what a run did, and
     * `allocating the parties` is not one of the things it did - it is where
     * the line the pane already carries has got to.
     *
     * @param strLine what is happening now
     */
    private void footerStep(String strLine) {
        this.strFooter = strLine;
        this.strPhase = null;
        lblFooter.setText(strLine);
        timerSettle.stop();
    }


    /**
     * A BLANK LINE BEFORE A RUN, so it is not read as a continuation of the
     * one above it - and none before the first, which would open the pane
     * with an empty line.
     */
    /**
     * @param strLine a milestone, or null
     * @return what it is ABOUT - everything before ` - `, which is where an
     *         update to a pending line puts its figures. Never null, so two
     *         absent subjects do not compare equal
     */
    private static String strSubjectOf(String strLine) {
        if (strLine == null)
            return "\u0000";
        int idx = strLine.indexOf(" - ");
        return idx < 0 ? strLine : strLine.substring(0, idx);
    }


    private void separateRun() {
        if (!logMain.isEmpty())
            logMain.append("");
    }


    private void milestone(String strLine) {
        // TWO PENDING LINES ARE ONE LINE THAT CHANGED ONLY WHEN THEY NAME
        // THE SAME SUBJECT. The gate wait reports `3 of 8`, then `4 of 8`,
        // and appending each would put eight lines in the pane and stop the
        // spinner between every one of them - so the two minutes where
        // nothing answers would show a dead line rather than dots.
        //
        // BUT `Starting Splice` FOLLOWED BY `Waiting for the network` IS TWO
        // THINGS, and replacing the first with the second would delete a
        // line that had not finished being true. The subject is what comes
        // before ` - `, which is where an update puts its figures.
        if (Milestones.isPending(strLine)) {
            if (logMain.isSpinning() && strSubjectOf(strPendingOpen).equals(
                    strSubjectOf(strLine)))
                logMain.replaceSpinning(strLine);
            else
                logMain.appendSpinning(strLine);
            this.strPendingOpen = strLine;
        }
        else {
            logMain.append(strLine);
            this.strPendingOpen = null;
        }
        this.strFooter = strLine;
        // A NEW MILESTONE IS A NEW SUBJECT. The phase belongs to the one that
        // has just ended and would sit under the next one unchanged until its
        // component happened to print something matchable.
        this.strPhase = null;
        lblFooter.setText(strLine);
        // A NEW EVENT CANCELS A PENDING SETTLE. Otherwise a stack stopped
        // nine seconds after it came up would show `Running` a second later.
        timerSettle.stop();
    }


    /**
     * @param strState what the window is once the last event has been read
     */
    private void footerSettles(String strState) {
        this.strSettle = strState;
        timerSettle.restart();
    }


    private void footerSettled() {
        if (strSettle == null)
            return;

        this.strFooter = strSettle;
        this.strSettle = null;
        // NOT over a copy. A reader who clicked a row two seconds ago is
        // still looking at what they copied, and the settle has no deadline
        // - the copy's own timer restores `strFooter`, which is now this.
        if (!timerFooter.isRunning())
            lblFooter.setText(strFooter);
    }


    /**
     * @param state what the window has become
     */
    private void setStateChip(SandboxService.State state) {
        pnlChipState.removeAll();
        pnlChipState.add(GuiTheme.chip(state.name(), colForState(state)));
        pnlChipState.revalidate();
        pnlChipState.repaint();
    }


    private static java.awt.Color colForState(SandboxService.State state) {
        switch (state) {
            case RUNNING:
                return GuiTheme.COL_OK;
            case FAILED:
                return GuiTheme.COL_BAD;
            case STARTING:
            case STOPPING:
                return GuiTheme.COL_BUSY;
            case STOPPED:
            default:
                return GuiTheme.COL_STOPPED;
        }
    }


    /**
     * A line that is worth reading and is not a milestone.
     *
     * @param strLine a few words, shown for as long as a copied value is
     */
    /**
     * Re-points everything derived from a global root, after a save.
     *
     * The two stores are re-made rather than told to re-read: both are
     * immutable around a root, and a store that could be re-pointed in place
     * would be one missed call away from listing one directory and writing to
     * another.
     */
    private void settingsSaved() {
        // THE PROVIDER MAY HAVE MOVED. `oidc.url` is one of the settings this
        // tab writes, and a key set url held from the provider named before it
        // changed would configure the next participant against the wrong
        // service with nothing on screen saying so.
        JwtMintProcess.forgetDiscovered();
        profiles = ProfileStore.ofDefaults();
        // The discovery port is one of the nine, and a saved port that nothing
        // rebound would leave the endpoint on the number the tab no longer
        // shows.
        discoveryRebind();
        rawarRebind();
        webRefresh();
        // THE PORTS ARE IN THE LOCALNETND KEY - `todo.md` A-42. A first port or
        // a PostgreSQL port saved here names another founding store and another
        // snapshot root, and the Settings tab and the list must say so before
        // the next start rather than after it. A stack that is up keeps its
        // own - `portsLocalNet`.
        if (isLocalNet()) {
            founding = foundingOfLocalNet();
            snapshots = snapshotsOfLocalNet();
            refreshFoundingState();
            refreshSnapshots();
            return;
        }
        CantonInstallation instNow = form.selected();
        if (instNow != null) {
            snapshots = Snapshots.ofVersion(instNow.version(), instNow.edition());
            refreshSnapshots();
        }
    }


    /**
     * Asks whether this machine has a reason to be offered a toolchain, and
     * shows or hides the strip accordingly. Called at open and after an
     * install, because both are moments when the answer can change.
     */
    private void refreshBanner() {
        // NOT ON THE SINGLE-PARTICIPANT WINDOW - the operator's instruction of
        // 2026-09-26. There a machine without a toolchain gets STR_NO_TOOLCHAIN
        // and no install is offered.
        banner.setOffered(isLocalNet() && ToolchainAdvice.flagOffer(ToolchainRoots.ofDefaults(),
                SandboxService.lstCanton()));
    }


    /**
     * @return whether a Daml Assistant or DPM launcher is on PATH
     */
    private static boolean hasToolchain() {
        ToolchainRoots roots = ToolchainRoots.ofDefaults();
        return roots.flagDaml() || roots.flagDpm();
    }


    /**
     * The single-participant window at open: the notice when there is no
     * toolchain, then the Aviation offer as before.
     */
    private void openSingle() {
        if (!hasToolchain())
            Modals.inform(this, STR_NO_TOOLCHAIN, "Raposza Sandbox");
        offerAviation();
    }


    /**
     * Offers the Aviation fixture, once, for the version the form has selected.
     *
     * ON THE EVENT THREAD and at open only - see {@link AviationSession}. What it
     * ends in is a DAR ticked in the DARs tab, which the next start uploads
     * like any other.
     */
    private void offerAviation() {
        aviation.offer(this, form.selected(), form.run().dirDars(), this::aviationBegun,
                this::aviationStaged, this::aviationFailed);
    }


    /**
     * A build has started.
     *
     * AND START GOES DOWN WITH IT. It stays down until the fixture is on a
     * ledger or has failed, which is what {@link #flagAviationBusy} carries into
     * {@link #applyControls}.
     *
     * THE LAMP GOES UP HERE AND NOT AT STAGING. The fixture is under way from
     * this moment - a minute of compiler and then a start - and a lamp that
     * appeared only when the DAR was ready would be dark for the whole of the
     * part a reader is waiting through.
     */
    private void aviationBegun() {
        this.flagAviationBusy = true;
        applyControls(stateNow);
        paneSettings.setAviationEnabled(false);
        lamps.setFixtureShown(true);
        lamps.setFixtureHealth(SandboxService.Health.STARTING);
    }


    /**
     * The Settings tab's button. Builds whatever was answered at open, and the
     * ordinary staging path takes it from there.
     */
    private void aviationRequested() {
        tabs.setSelectedIndex(0);
        aviation.buildNow(form.selected(), form.run().dirDars(), this::aviationBegun,
                this::aviationStaged, this::aviationFailed);
    }


    /**
     * Ticks the DAR the fixture just built and starts the stack.
     *
     * STARTED HERE RATHER THAN LEFT FOR THE OPERATOR. Somebody who has just
     * asked for test data wants the ledger it goes onto; a window that built a
     * DAR and then waited would be asking them to press a button whose answer
     * it already knows.
     *
     * ONLY WHEN IT WAS JUST BUILT, and only from STOPPED. This method also runs
     * on the path where the DAR was already on disk from an earlier session -
     * there the developer asked for nothing, and a window that started a stack
     * every time it opened would be doing something nobody requested.
     */
    private void aviationStaged() {
        Path fileStaged = aviation.fileStaged();
        if (fileStaged == null) {
            aviationIdle();
            return;
        }

        if (!paneDars.flagTick(fileStaged.getFileName().toString())) {
            logMain.append("the Aviation DAR was staged but the DARs tab cannot see it");
            aviationIdle();
            return;
        }

        if (stateNow != SandboxService.State.STOPPED) {
            logMain.append("Aviation test data ready; the next start will upload it.");
            aviationIdle();
            return;
        }
        logMain.append("Aviation test data ready; starting the Sandbox.");
        startRequested();
    }


    /**
     * A build began and produced nothing. {@link AviationSession} has already
     * said why, so this is only the controls and the lamp.
     */
    private void aviationFailed() {
        lamps.setFixtureHealth(SandboxService.Health.DOWN);
        aviationIdle();
    }


    /**
     * Whether `Create Aviation test data` is worth offering at all.
     *
     * NOT WHEN SOMETHING IS ALREADY LOADED. The button builds the fixture and
     * the next start uploads it, so a run that already has DARs selected or a
     * fixture on this window's ledger would be putting a second set of parties
     * on top of the first. That fails, with a message about a party that
     * already exists rather than about the button that was pressed.
     *
     * @return true when nothing is in the way
     */
    private boolean flagAviationOffered() {
        return !aviation.hasRun() && paneDars.lstSelected().isEmpty();
    }


    /**
     * The fixture is no longer in flight, so Start belongs to the state again.
     *
     * CALLED FROM EVERY END, including the ones that end nothing: a staging
     * that could not tick the DARs tab leaves the window exactly where it was
     * and must give the button back, or the operator is left with a STOPPED
     * window that cannot be started.
     */
    private void aviationIdle() {
        this.flagAviationBusy = false;
        applyControls(stateNow);
    }


    /**
     * Runs the fixture against the stack that has just come up.
     *
     * <h2>ONCE, AND ONLY ONTO A LEDGER THAT CANNOT ALREADY HOLD IT</h2>
     *
     * `Main:setupParties` allocates parties and creates users. Run a second
     * time against a ledger that has them it exits 1 with a gRPC status and
     * nothing else happens - which is what a create / snapshot / start again
     * cycle produced on 2026-09-08, because the only gate was `isStaged()` and
     * that is true from the first staging until the window closes.
     *
     * Two things must hold:
     *
     * <ul>
     * <li>IT HAS NOT RUN IN THIS WINDOW - one staging, one ledger;</li>
     * <li>THE START BEGAN EMPTY - a restored snapshot holds whatever was
     * saved, and the window cannot tell from the outside whether that includes
     * the fixture. It says so and does not guess. The cost is real: building
     * the fixture and then starting onto a snapshot uploads the DAR and
     * creates nothing, and the Sandbox tab is where that is said.</li>
     * </ul>
     *
     * <h2>WHO BUILT THE DAR DECIDES NOTHING - 2026-09-15</h2>
     *
     * A third condition used to require that this window compiled it, and a
     * DAR found where an earlier session left it was uploaded and never run.
     * That answered the wrong question: what makes the scripts unsafe is a
     * ledger that may already hold the parties, and a start from STOPPED onto
     * no snapshot is an empty one whoever produced the bytes. With the fixture
     * store now filled by `run-fixture.sh` as well as by this window, the
     * condition delivered the fixture's DAR and none of its data.
     *
     * @param serviceHere what came up
     */
    private void runAviationIfStaged(SandboxService serviceHere) {
        if (serviceHere == null || !aviation.isStaged())
            return;

        // SILENTLY. A window that has already put the fixture on a ledger and
        // is being started again is not doing anything wrong, and the line
        // that said so appeared at the end of every such start.
        if (aviation.hasRun())
            return;

        if (strSnapshotRunning != null) {
            logMain.append("this start restored snapshot " + strSnapshotRunning + ", so the"
                    + " Aviation scripts are not run - the ledger holds whatever was saved");
            return;
        }

        CantonInstallation instNow = form.selected();
        if (instNow == null)
            return;

        String strPort = serviceHere.report().value(ReadyReport.KEY_LEDGER_API);
        int nPortLedger;
        try {
            nPortLedger = Integer.parseInt(strPort == null ? "" : strPort.trim());
        }
        catch (NumberFormatException ex) {
            logMain.append("the Aviation fixture needs the Ledger API port and the ready report"
                    + " carries none");
            return;
        }

        milestone(AviationSession.STR_PHASE);
        aviation.run(form.auth(), instNow.version(), nPortLedger, this::aviationDone);
    }


    /**
     * The scripts have finished.
     *
     * THE BUTTON STAYS OFF WHEN THEY WORKED. Pressing it again would rebuild
     * and re-run against a ledger that already holds the parties, which is a
     * failure with a confusing message rather than a second copy of anything.
     *
     * AND THE FOOTER SAYS WHAT IS TRUE NOW. The last thing it carried was a
     * step of the run, which is a spinner over something that has finished.
     *
     * @param flagOk whether every phase exited zero
     */
    private void aviationDone(Boolean flagOk) {
        boolean flagGreen = Boolean.TRUE.equals(flagOk);
        lamps.setFixtureHealth(flagGreen ? SandboxService.Health.UP
                : SandboxService.Health.DOWN);
        // AND THE BUTTON IS `applyControls`' BUSINESS, which `aviationIdle`
        // reaches: the fixture having run is exactly what closes it.
        milestone(flagGreen ? STR_AVIATION_DONE : STR_AVIATION_FAILED);
        footerSettles(STR_FOOTER_RUNNING);
        aviationIdle();
        // THE FIXTURE MADE LEDGER USERS, and a user the provider has not been
        // told about cannot sign in.
        usersRefreshAll();
    }


    /**
     * Offers the Pharma fixture, once, for the Canton the three participants
     * run inside.
     *
     * ON THE EVENT THREAD and at open only. What it ends in is a DAR built and
     * waiting; nothing reaches a ledger until one is up, which on this topology
     * is the only moment an upload is possible at all.
     */
    /**
     * The Settings tab's button. Builds whatever was answered at open, and the
     * ordinary path takes it from there - `pharmaReady` starts the stack, and
     * the start puts the story on it.
     */
    private void pharmaRequested() {
        tabs.setSelectedIndex(0);
        pharma.buildNow(instCantonStock(), this::pharmaBegun, this::pharmaReady,
                this::pharmaFailed);
    }


    private void offerPharma() {
        pharma.offer(this, instCantonStock(), this::pharmaBegun, this::pharmaReady,
                this::pharmaFailed);
    }


    /**
     * A build has started, and Start goes down with it - the reason
     * {@link #aviationBegun} gives, unchanged.
     */
    private void pharmaBegun() {
        this.flagPharmaBusy = true;
        applyControls(stateNow);
        lamps.setFixtureShown(true);
        lamps.setFixtureHealth(SandboxService.Health.STARTING);
    }


    /**
     * The DAR is there, so the stack starts.
     *
     * STARTED HERE RATHER THAN LEFT FOR THE OPERATOR - operator instruction,
     * 2026-09-22, and {@link #aviationStaged} already works this way. Somebody
     * who has just said Yes to test data wants the ledger it goes onto, and a
     * window that built a DAR and then waited would be asking them to press a
     * button whose answer it already knows.
     *
     * ONLY FROM STOPPED. A stack that is already up gets the fixture on its
     * next start, which is what `runPharmaIfStaged` does.
     */
    private void pharmaReady() {
        pharmaIdle();
        if (stateNow != SandboxService.State.STOPPED) {
            logMain.append("Pharma test data ready; the next start puts it on the ledger.");
            return;
        }
        startRequested();
    }


    /** A build began and produced nothing; {@link PharmaSession} said why. */
    private void pharmaFailed() {
        lamps.setFixtureHealth(SandboxService.Health.DOWN);
        pharmaIdle();
    }


    private void pharmaIdle() {
        this.flagPharmaBusy = false;
        applyControls(stateNow);
    }


    /**
     * Puts the fixture on the LocalNetND that has just come up.
     *
     * <h2>AFTER THE START, WHICH IS THE ONLY PLACE IT FITS</h2>
     *
     * The Sandbox path uploads its DAR during the start, from the DARs tab.
     * There is no such route on this topology, so the upload and the population
     * both happen here, over the JSON Ledger API that `probes/pharma` measured.
     *
     * <h2>NOT ONTO A RESTORED SNAPSHOT</h2>
     *
     * A restored cluster holds whatever was saved and the window cannot tell
     * from the outside whether that includes the fixture. It says so and does
     * not guess - {@link #runAviationIfStaged}'s rule.
     */
    private void runPharmaIfStaged() {
        if (!pharma.isStaged() || pharma.hasRun())
            return;

        if (strSnapshotRunning != null) {
            logMain.append("this start restored snapshot " + strSnapshotRunning + ", so the"
                    + " Pharma story is not run - the ledger holds whatever was saved");
            return;
        }

        CantonInstallation instStock = instCantonStock();
        if (instStock == null) {
            logMain.append("the Pharma fixture needs the Canton the participants run inside"
                    + " and there is none");
            return;
        }

        this.flagPharmaBusy = true;
        applyControls(stateNow);
        lamps.setFixtureShown(true);
        lamps.setFixtureHealth(SandboxService.Health.STARTING);
        milestone(PharmaSession.STR_PHASE);
        // THE SAME NUMBERING THE RUNNER WAS GIVEN - `portsLocalNet`. A literal
        // here would address ports the stack does not bind the day the
        // setting moves.
        pharma.run(form.auth(), instStock.version(), STR_NODE_LOCALNET,
                portsLocalNet().nPortFirst(), this::pharmaDone);
    }


    /**
     * The story has finished.
     *
     * @param flagOk whether every step committed
     */
    private void pharmaDone(Boolean flagOk) {
        boolean flagGreen = Boolean.TRUE.equals(flagOk);
        lamps.setFixtureHealth(flagGreen ? SandboxService.Health.UP
                : SandboxService.Health.DOWN);
        milestone(flagGreen ? STR_PHARMA_DONE : STR_PHARMA_FAILED);
        footerSettles(STR_FOOTER_RUNNING);
        pharmaIdle();
        // THE PHARMA DARs ARE LISTED WHERE THEY LANDED - his instruction,
        // 2026-09-22d: "The DARs used in Pharma should be listed in the tab."
        mapDarsLocal.values().forEach(DarsLivePane::refresh);
    }


    /**
     * Acquires a toolchain, on a thread of its own.
     *
     * NOT ON THE EVENT THREAD. This is hundreds of megabytes over someone
     * else's server and takes minutes on a slow link; running it where the
     * window is painted would freeze the window for the whole of it and leave
     * the operator unable to tell a slow download from a hung application.
     *
     * The log is the Sandbox tab's own, so what the vendor's installers print
     * lands where every other thing this window runs already prints.
     */
    private void installToolchain() {
        banner.setBusy(true);
        logMain.append("Installing a toolchain.");
        tabs.setSelectedIndex(0);

        Thread threadInstall = new Thread(() -> {
            try {
                ToolchainInstall install = ToolchainInstall.ofDefaults(
                        HostPlatform.ofDefaults(), ToolchainRoots.ofDefaults(),
                        dirInstallWork(), logMain::append,
                        logMain::replaceSpinning, null);
                VersionId version = install.versionInstallLatest(dirInstallWork());
                SwingUtilities.invokeLater(() -> installToolchainDone(version, null));
            }
            catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> installToolchainDone(null, ex.getMessage()));
            }
        }, "raposza-toolchain-install");
        threadInstall.setDaemon(true);
        threadInstall.start();
    }


    /**
     * @param version what was installed, or null when it failed
     * @param strError why it failed, or null when it did not
     */
    private void installToolchainDone(VersionId version, String strError) {
        banner.setBusy(false);
        if (strError != null) {
            logMain.append("Install FAILED: " + strError);
            footerNotice("Install failed");
            return;
        }

        logMain.append("Installed Canton " + version + ".");
        // THE FORM READS THE MACHINE AGAIN. Nothing about the install reaches
        // the version box on its own - what changed is on disk, and the box is
        // built from a scan.
        form.rescan(null);
        refreshBanner();
        footerNotice("Installed Canton " + version);
    }


    /**
     * Raises the SDK catalogue and, when something was installed, reads the
     * machine again.
     *
     * The dialog is MODAL and returns what it installed, so the rescan happens
     * here rather than inside it: what changed is on disk, and the box is built
     * from a scan.
     */
    private void installSdkRequested() {
        // ON LOCALNETND THE BOX HOLDS SPLICE, and the button beside it installs
        // Splice - todo.md A-38. It opened the Canton SDK dialog until then.
        if (isLocalNet()) {
            installSpliceRequested();
            return;
        }
        if (!hasToolchain()) {
            Modals.inform(this, STR_NO_TOOLCHAIN, "Raposza Sandbox");
            return;
        }
        // WHICH CANTON A BUNDLE BRINGS, off the manifests of the bundles on this
        // machine - a dozen small files, read once - T-3, D-834.
        ToolchainRoots roots = ToolchainRoots.ofDefaults();
        Map<String, String> mapCanton = roots.dirDpm() == null ? Map.of()
                : DpmBundles.mapCanton(DpmBundles.dirSdk(roots));
        SdkInstallDialog dialog = new SdkInstallDialog(this, this::lstOfferSdk,
                this::installSdk, offer -> mapCanton.get(offer.version().toString()));
        dialog.setVisible(true);

        VersionId version = dialog.versionInstalled();
        if (version == null)
            return;

        form.rescan(null);
        refreshBanner();
        footerNotice("Installed DAML SDK " + version);
    }


    /**
     * The Splice counterpart of {@link #installSdkRequested}: every published
     * version with its size, a download straight into `~/.splice`, and an
     * archive fetched by hand - the operator's instruction of 2026-09-23. The
     * box is refilled from the disk afterwards, as the SDK path rescans.
     */
    private void installSpliceRequested() {
        Path dirRoot = SpliceInstallations.dirRoot();
        SpliceInstallDialog dialog = new SpliceInstallDialog(this,
                new HashSet<>(SpliceInstallations.lstVersion()),
                SpliceAcquire::lstVersionPublished, SpliceAcquire::nBytesPublished,
                (strVersion, lineProgress) -> SpliceAcquire.download(strVersion, dirRoot,
                        lineBoth(lineProgress), strLine -> {
                            // ONE LINE, REWRITTEN IN PLACE - his instruction.
                            logMain.replaceSpinning(strLine);
                            lineProgress.accept(strLine);
                        }),
                (fileArchive, lineProgress) -> SpliceAcquire.install(fileArchive, dirRoot,
                        fileArchive.toAbsolutePath().toString(), lineBoth(lineProgress))
                        .getParent().getFileName().toString());
        dialog.setVisible(true);

        String strVersion = dialog.strInstalled();
        if (strVersion == null)
            return;

        form.useSplice(SpliceInstallations.lstVersion());
        footerNotice("Installed Splice " + strVersion);
    }


    /**
     * @param lineProgress the dialog's footer
     * @return a sink writing to the Sandbox tab's log AND the footer, as the
     *         SDK path does
     */
    private Consumer<String> lineBoth(Consumer<String> lineProgress) {
        return strLine -> {
            logMain.append(strLine);
            lineProgress.accept(strLine);
        };
    }


    /**
     * Reads both catalogues, from the dialog's own thread.
     *
     * @return every SDK version either toolchain publishes, oldest first
     * @throws IOException when a toolchain refuses
     */
    private List<SdkOffer> lstOfferSdk() throws IOException {
        ToolchainRoots roots = ToolchainRoots.ofDefaults();
        return SdkOffers.lstMerged(
                SdkOffers.lstAssistant(HostPlatform.ofDefaults(),
                        SdkInstallations.setInstalled(roots)),
                toolchain(null).lstOfferDpm(dirInstallWork()));
    }


    /**
     * NOT ON THE EVENT THREAD - the dialog calls this from a thread of its own.
     *
     * @param offer which SDK, and which toolchain installs it
     * @param lineProgress told what is happening
     * @throws IOException when any step fails
     */
    private void installSdk(SdkOffer offer, Consumer<String> lineProgress)
            throws IOException {
        toolchain(lineProgress).installOffer(offer, dirInstallWork());
    }


    /**
     * THE VENDOR'S OUTPUT GOES BOTH WAYS. The log is the Sandbox tab's own, so
     * what an installer prints lands where every other thing this window runs
     * already prints; the same lines reach the dialog, which is what the operator
     * is looking at while it runs.
     *
     * @param lineProgress the dialog's footer, or null when nothing is watching
     * @return an install that really fetches and really runs
     */
    private ToolchainInstall toolchain(Consumer<String> lineProgress) {
        // A PULL IS A PENDING LINE. dpm prints one line per component and
        // nothing while the bytes move - minutes, for a Canton - so the line
        // carries the pane's spinner until the next one arrives.
        Consumer<String> lineLog = strLine -> {
            if (strLine.startsWith("Pulling "))
                logMain.appendSpinning(strLine);
            else
                logMain.append(strLine);
        };
        Consumer<String> lineOut = lineProgress == null ? lineLog
                : strLine -> {
                    lineLog.accept(strLine);
                    lineProgress.accept(strLine);
                };
        // A PULL'S FIGURE REWRITES ITS OWN PENDING LINE - D-833. Every other
        // progress line is the dialog's alone, as before.
        Consumer<String> lineFigure = strLine -> {
            if (strLine.startsWith("Pulling "))
                logMain.replaceSpinning(strLine);
            if (lineProgress != null)
                lineProgress.accept(strLine);
        };
        return ToolchainInstall.ofDefaults(HostPlatform.ofDefaults(),
                ToolchainRoots.ofDefaults(), dirInstallWork(), lineOut, lineFigure,
                null);
    }


    private static Path dirInstallWork() {
        return RaposzaSettings.current().dirHome().resolve("install");
    }


    private void footerNotice(String strLine) {
        lblFooter.setText(strLine);
        timerFooter.restart();
    }


    /**
     * A lamp was clicked: show what that component was started with.
     *
     * @param strLabel the lamp's own label
     */
    private void lampClicked(String strLabel) {
        SandboxService serviceHere = service;
        if (serviceHere == null) {
            footerNotice("nothing has been started yet");
            return;
        }

        if ("PQS".equals(strLabel)) {
            ScribeProcess procPqs = serviceHere.processPqs();
            if (procPqs == null) {
                footerNotice("this stack is not running PQS");
                return;
            }
            LaunchDialog.show(this, "PQS - what it was started with",
                    LaunchText.strOf(procPqs));
            return;
        }

        CantonProcess procCanton = serviceHere.processParticipant();
        if (procCanton == null) {
            footerNotice("no participant has been started yet");
            return;
        }
        LaunchDialog.show(this, "Participant - what it was started with",
                LaunchText.strOf(procCanton));
    }


    /**
     * @param strKey which row it came from
     * @param strValue what went to the clipboard
     */
    private void footerCopied(String strKey, String strValue) {
        lblFooter.setText("Copied " + strKey + ": " + strValue);
        // RESTARTED rather than started: a reader clicking three rows in a
        // row would otherwise have the first click's timer put the footer
        // back while the third value was still on the clipboard.
        timerFooter.restart();
    }
    /**
     * The Canton log's own lines, which are not routed through
     * {@link #route} because the tail has one destination and no component
     * name to route by.
     *
     * @param strLine one raw line of `canton.log`
     */
    private void onCantonLine(String strLine) {
        logParticipant.append(strLine);
        phase(StackComponent.STR_PARTICIPANT, strLine);
    }


    /**
     * What the footer adds to `Starting participant` while the participant is
     * still coming up.
     *
     * Called from process reader threads and from the log tail, so the hop is
     * here rather than at each caller.
     *
     * @param strComponent whose line this is
     * @param strLine the raw line
     */
    private void phase(String strComponent, String strLine) {
        String strPhaseNew = StartPhases.strOf(strComponent, strLine);
        if (strPhaseNew == null)
            return;
        SwingUtilities.invokeLater(() -> showPhase(strComponent, strPhaseNew));
    }


    /**
     * @param strComponent whose phase this is
     * @param strPhaseNew what it is doing
     */
    private void showPhase(String strComponent, String strPhaseNew) {
        // ONLY UNDER THE MILESTONE THAT NAMES THIS COMPONENT. PostgreSQL keeps
        // printing while the participant comes up - the pool opens connections
        // for the whole start - and `Starting participant - accepting
        // connections` is a worse footer than no detail at all.
        if (!strFooter.equals(Milestones.STR_PREFIX_STARTING
                + Milestones.strNameOf(strComponent)))
            return;
        if (strPhaseNew.equals(strPhase))
            return;

        this.strPhase = strPhaseNew;
        // NOT OVER A COPY, for the reason footerSettled gives: a reader who
        // clicked a row two seconds ago is still looking at what they copied.
        if (!timerFooter.isRunning())
            lblFooter.setText(strFooter + " - " + strPhaseNew);
    }


    /**
     * PQS on the LocalNetND topology: started once the stack is up, against
     * the app-provider ledger.
     *
     * HIS INSTRUCTION, 2026-09-22. `LocalNetPqs` holds every decision - the
     * participant, the ports, the database and which binary - and this method
     * holds only the two things a window can supply: the credential, minted
     * the way the Sandbox path mints it, and the moment.
     *
     * THE MOMENT IS AFTER THE STACK REPORTS RUNNING. scribe retries a refused
     * Ledger API for ever and prints the same line each time, so a scribe
     * started while canton is still binding is indistinguishable from one
     * pointed at the wrong port.
     *
     * OFF THE EVENT DISPATCH THREAD. The mint is a Spring Boot service and
     * `strMintBlocking` waits up to {@link #TIMEOUT_MINT} for it; doing that
     * on the EDT freezes the window for a minute at the moment it has just
     * reported the stack up.
     */
    private void startLocalNetPqs() {
        if (procPqsLocal != null)
            return;

        // NOT `form.toOptions`. It resolves the Canton combo through
        // `selected()`, and on THIS topology that combo holds Splice versions,
        // so `selected()` answers null and `toOptions` throws "no startable
        // Canton is installed". Thrown from here it left `localNetDone`
        // half-run: the stack was up, the lamps were set, and everything after
        // the call - the "up in" milestone, RUNNING, the fixture offer - never
        // happened. The window sat at STARTING with a network behind it.
        // MEASURED at his console, 2026-09-22.
        if (form.pqs() == SandboxOptions.PqsMode.OFF)
            return;

        LocalNetRunner runnerHere = runner;
        CantonInstallation instStock = instCantonStock();
        if (runnerHere == null || instStock == null)
            return;

        AuthSettings authNow = form.auth();
        // `participant_admin` DIRECTLY, which is what `strUserPqs` returns for
        // every 3.x stack whose options carry no user id - and `toOptions`
        // never sets one. Reading it off an options object this form cannot
        // build on this topology is what broke the start.
        String strUser = STR_USER_PQS;
        LocalNetPorts portsHere = runnerHere.ports();
        Path dirWork = runnerHere.dirRun();

        lamps.setPqsHealth(SandboxService.Health.STARTING);
        logPqsPretty.reset();
        // THE CONTEXT GOES IN THE PQS TAB, NOT THE MAIN LOG - his instruction
        // of 2026-09-22, "Too much PQS log in main log". The milestone log is
        // the stack's own story; which port scribe dials and which database it
        // writes belong beside scribe's own output, where a reader who cares
        // is already looking. The main log now carries PQS only when it FAILS,
        // because that is the one thing a person not looking at the tab has to
        // know.
        pqsSay("connecting to " + LocalNetPqs.STR_ROLE + " at " + LocalNetRunner.STR_HOST
                + ":" + LocalNetPqs.nPortLedger(portsHere));
        pqsSay("writing to database " + LocalNetSpec.STR_DB_PQS + " on "
                + LocalNetRunner.STR_HOST + ":" + portsHere.nPortPostgres());

        Thread threadPqs = new Thread(() -> {
            try {
                String strToken = null;
                // NO CREDENTIAL WHERE THE STACK VERIFIES NOTHING. `PqsSpec`
                // renders `--source-ledger-auth NoAuth` for a null token,
                // which is what a wildcard participant wants and what a token
                // it cannot verify would break.
                if (authNow.mode() != AuthSettings.Mode.NONE) {
                    strToken = JwtMintProcess.strMintBlocking(authNow, instStock.version(),
                            strUser, STR_NODE_LOCALNET, TIMEOUT_MINT);
                    if (strToken == null) {
                        SwingUtilities.invokeLater(() -> {
                            lamps.setPqsHealth(SandboxService.Health.DOWN);
                            pqsSay("the mint did not answer in "
                                    + TIMEOUT_MINT.toSeconds() + " s, so scribe was not"
                                    + " started");
                            milestone("PQS did not start - see the PQS tab");
                        });
                        return;
                    }
                }
                // A MINTED TOKEN AND NOT OAuth, MEASURED 2026-09-22 at his
                // console. `PqsSpec` prefers OAuth because a static token
                // cannot outlive its own lifetime - and on THIS provider
                // scribe's client credentials are refused: the token endpoint
                // answered HTTP 401 to every retry until scribe gave up and
                // exited. The client the window registers on this mint is
                // `localnet-ui` with its six origins, at every start, by
                // `MintRegistration` - `todo.md` A-34 (a) - and
                // `participant_admin` is not one, so there is nothing for
                // scribe to authenticate as.
                //
                // The mint issues the token itself without that, which is the
                // route the window already uses for the ledger. The cost is
                // `PqsSpec`'s stated one: the token lasts as long as it lasts
                // and a restart is the renewal. Registering a scribe client
                // would remove that, and it is A-34's work rather than this
                // instruction's.
                PqsSpec spec = LocalNetPqs.specFor(instStock.version(), strToken, null);
                ScribeProcess procNew = LocalNetPqs.processFor(spec,
                        LocalNetRunner.STR_HOST, portsHere, dirWork);
                procNew.addOutputListener(strLine -> {
                    String strSaid = logPqsPretty.strFor(strLine);
                    if (strSaid != null)
                        SwingUtilities.invokeLater(() -> route(StackComponent.STR_PQS, strSaid));
                });
                procNew.start();
                SwingUtilities.invokeLater(() -> {
                    this.procPqsLocal = procNew;
                    lamps.setPqsHealth(SandboxService.Health.UP);
                    pqsSay(spec.describe());
                    // AND A LINE THAT SAYS IT IS ACTUALLY RUNNING. Without it
                    // the tab ends at `database ready` and a scribe that is
                    // ingesting looks exactly like one that stalled - which is
                    // how it read at his console, 2026-09-22.
                    pqsSay("scribe is running - health on "
                            + LocalNetPqs.nPortHealth(portsHere));
                });
            }
            catch (RuntimeException ex) {
                // DOWN AND NOT OFF - `StackService_i.Health` draws that line:
                // OFF says nothing will ever start it, and something just
                // tried and failed.
                SwingUtilities.invokeLater(() -> {
                    lamps.setPqsHealth(SandboxService.Health.DOWN);
                    pqsSay("did not start: " + ex.getMessage());
                    // THE ONE PQS LINE THE MAIN LOG KEEPS.
                    milestone("PQS did not start - see the PQS tab");
                });
            }
        }, "localnet-pqs-start");
        threadPqs.setDaemon(true);
        threadPqs.start();
    }


    /**
     * Stops whatever {@link #startLocalNetPqs} left running and gives the lamp
     * back to the stack.
     *
     * CALLED FROM THE STOP THREAD, not the EDT: `ScribeProcess.stop` waits for
     * the process to go.
     */
    private void stopLocalNetPqs() {
        ScribeProcess procHere = procPqsLocal;
        this.procPqsLocal = null;
        if (procHere == null)
            return;

        try {
            procHere.stop(TIMEOUT_PQS_STOP);
        }
        finally {
            SwingUtilities.invokeLater(() -> {
                pqsSay("stopped");
                lamps.setPqsHealth(null);
            });
        }
    }


    /**
     * @param strLine one line for the PQS tab, in the window's own voice
     *        rather than scribe's
     */
    private void pqsSay(String strLine) {
        route(StackComponent.STR_PQS, strLine);
    }



    /** How long scribe is given to go before it is killed. */
    private static final Duration TIMEOUT_PQS_STOP = Duration.ofSeconds(20);


    private void route(String strComponent, String strLine) {
        LogPane pane = mapLog.get(strComponent);
        if (pane == null)
            pane = logMain;
        pane.append(strLine);
        phase(strComponent, strLine);
    }


    /**
     * The ledger user PQS falls back to.
     *
     * `participant_admin` is the user Canton creates itself, so it is on every
     * participant without a bootstrap step, and it is what the JWT tab already
     * authenticates its own user query as. IT CANNOT SERVE SCRIBE on the 3.x
     * column: see {@link #strUserPqs}.
     */
    private static final String STR_USER_PQS = "participant_admin";

    /**
     * Which ledger user PQS speaks as, and it is NOT `participant_admin` on
     * the 3.x column.
     *
     * MEASURED on Canton 3.5.12 with scribe v3.5.7, once the OAuth
     * handshake finally worked and the run got far enough to ask: scribe reads
     * `Retrieved 1 user rights`, `0 parties can actAs/readAs`, and then retries
     * for ever on `UNAVAILABLE: No parties found matching '*'`.
     * `--pipeline-filter-parties=*` resolves against the parties the TOKEN'S
     * USER may read, and `participant_admin` carries `ParticipantAdmin` and no
     * party rights at all. Administering a participant and reading its
     * contracts are separate rights, and scribe needs the second one.
     *
     * `Canton3xBootstrap.withPartyAndUser` creates a user with
     * `readAsAnyParty = true` precisely for this, so when the form
     * named a party and a user, that user is the one PQS has to be.
     *
     * THE 2.x COLUMN KEEPS `participant_admin`. `--party` and `--user` are
     * reported as IGNORED there because that arm runs no such bootstrap, so the
     * user the form names does not exist on it.
     *
     * @param optionsHere what this run was asked for
     * @return the user id the PQS token's `sub` carries
     */
    private static String strUserPqs(SandboxOptions optionsHere) {
        if (optionsHere.version().major() == 2)
            return STR_USER_PQS;
        return optionsHere.strUserId() == null ? STR_USER_PQS : optionsHere.strUserId();
    }

    /** How long the start thread waits for the JWT service to answer. */
    private static final Duration TIMEOUT_MINT = Duration.ofSeconds(60);

    /**
     * THE MINT SAYS NOTHING IN THE MILESTONE PANE, and that is a decision
     * rather than an omission.
     *
     * It had three lines here once - `Starting mint`, `Started mint in N
     * seconds`, and `Minting the PQS token`. The pane is eight lines high and
     * holds the shape of a start: postgres, participant, PQS. The mint is not
     * a member of that stack; it is a service this window keeps alive
     * alongside it, and three of the eight lines went to a component the
     * reader is not waiting for.
     *
     * WHAT THIS COSTS: the stretch between `Starting Sandbox` and `Starting
     * postgres` is now unnarrated for as long as the JWT service takes to
     * answer - two seconds warm, longer on a cold JVM. The JWT tab is where
     * the mint is watched.
     *
     * A start that CANNOT get a token or a JWKS still fails loudly: see the
     * two throws in the start thread.
     */
    /**
     * START ON THE LOCALNET TOPOLOGY - D-790.
     *
     * NOTHING IS REFUSED HERE. His specification of 2026-09-21 describes one
     * window that WORKS on both topologies, so Start starts and the Aviation
     * offer is left exactly where it was; the 2026-09-21b delivery added a
     * refusal and a suppression his words do not ask for, and it was reverted.
     */
    private void startLocalNet() {
        String strVersion = form.strVersionSelected();
        if (strVersion == null) {
            Modals.warn(this, "no Splice bundle is installed under ~/.splice");
            return;
        }

        logPostgres.clear();
        logParticipant.clear();
        logSplice.clear();
        logWeb.clear();

        // BEFORE ANYTHING ELSE, because each participant's auth block is
        // written from this and cannot be revised once canton is up. An
        // external provider that has not answered leaves the key set url a
        // guess, and a guess reaches three participants as a start that times
        // out on a 404.
        String strWhyNoProvider = paneJwt.strWhyNoProvider();
        if (strWhyNoProvider != null) {
            Modals.warn(this, strWhyNoProvider);
            return;
        }

        CantonInstallation instStock = instCantonStock();
        if (instStock == null) {
            Modals.warn(this, "no installed Canton carries a runtime jar, and the"
                    + " three participants run inside one");
            return;
        }

        // WHAT THE OPERATOR SELECTED, not a constant. Until 2026-09-21 this
        // path built `ofJwks` off the local mint whatever the six rows held,
        // so the rows were drawn and reached nothing.
        AuthSettings authNow = form.auth();
        LocalNetAuth authStack;
        try {
            authStack = localNetAuthOf(authNow, instStock.version());
        }
        catch (RuntimeException ex) {
            Modals.warn(this, String.valueOf(ex.getMessage()));
            return;
        }

        // BEFORE THE STACK, for the same reason the Sandbox path restarts it
        // there: the participants read the key set while they start.
        paneJwt.restartService();
        // AND TOLD WHAT THEY WILL VERIFY, which is what lets the tab mint a
        // token the running stack accepts. Without this the tab holds no
        // settings at all on this topology, `strUrlTokenFor` answers null and
        // `mapOidcFor` runs on a null one.
        paneJwt.useAuth(authNow, instStock.version(), STR_NODE_LOCALNET, 0);

        LocalNetRunner runnerNew;
        // THE SETTINGS TAB'S NUMBERS - `todo.md` A-42. Until 2026-09-23 this
        // was `LocalNetPorts.ofDefaults()`, so a first port, a PostgreSQL port
        // or a web UI port moved on the Settings tab reached nothing.
        LocalNetPorts portsHere = RaposzaSettings.current().portsLocalNet();
        try {
            // FRESH EVERY TIME - operator instruction, 2026-09-22. The fourth
            // argument was `false`, so `pgdata` survived and the network
            // RESUMED with the same DSO and the same primary party - D-608.
            // Nothing a run accumulated survives a start now; what makes that
            // affordable is the founding snapshot laid back down by the
            // prepare below instead of a 72-second re-found.
            runnerNew = LocalNetRunner.of(strVersion, null, instStock.fileRuntime(),
                    true, false, true,
                    portsHere, authStack);
        }
        catch (RuntimeException ex) {
            Modals.warn(this, String.valueOf(ex.getMessage()));
            return;
        }

        // THE KEY IS THREE THINGS ON THIS TOPOLOGY - the Splice bundle, the
        // Canton jar the three participants run inside, and the port block
        // Canton's topology state names. {@link Founding} says why each one
        // is load-bearing. RECOMPUTED rather than trusted: `installChanged`
        // points it too, and this is the path that must not be wrong.
        founding = foundingOfLocalNet();
        snapshots = snapshotsOfLocalNet();
        Founding foundingHere = founding;
        if (foundingHere == null) {
            Modals.warn(this, "no Splice bundle and Canton pair to key a founding"
                    + " snapshot on");
            return;
        }
        // THE USER'S SELECTION WINS, exactly as it does on the Sandbox path -
        // Founding.actionOf, and the order is the operator's of 2026-09-22.
        String strSnapshot = strSnapshotSelected();
        Snapshots snapshotsHere = snapshots;
        Founding.Action action = Founding.actionOf(
                snapshotsHere == null ? null : strSnapshot, foundingHere.exists());
        if (action == Founding.Action.RESTORE_USER) {
            String strWhy = whyRefused(strSnapshot);
            if (strWhy != null) {
                Modals.warn(this, "that snapshot does not match this form:\n" + strWhy);
                return;
            }
        }
        this.flagFoundingPass = action == Founding.Action.FOUND
                && !foundingHere.strKey().equals(strFoundingTried);
        if (flagFoundingPass)
            this.strFoundingTried = foundingHere.strKey();
        // THE SETTINGS TAB FOLLOWS, so the label and `Reset founding` mean
        // something on this topology too.
        refreshFoundingState();
        if (action == Founding.Action.RESTORE_USER) {
            // AFTER THE RUNNER'S OWN WIPE, which is the only moment either
            // kind of snapshot fits - see LocalNetRunner.useClusterPrepare.
            String strNameHere = strSnapshot;
            runnerNew.useClusterPrepare(dirData ->
                    snapshotsHere.restore(strNameHere, dirData));
        }
        else if (action == Founding.Action.RESTORE_FOUNDING) {
            runnerNew.useClusterPrepare(foundingHere::restore);
        }
        // WHAT THIS RUN IS ON, which is what stops the fixture being written
        // over a restored ledger - `runPharmaIfStaged`.
        this.strSnapshotRunning = action == Founding.Action.RESTORE_USER ? strSnapshot : null;

        runnerNew.useMilestoneSink(this::milestone);
        runnerNew.useComponentSink(this::route);
        this.runner = runnerNew;
        // AFTER the runner exists and BEFORE the stack does: the supplier
        // resolves against its run directory, and `LogTail.start` opens at the
        // END of whatever is there, so the previous run's log is not replayed
        // into the pane.
        tailLocalCanton.start();
        tailLocalSplice.start();
        tailLocalWeb.start();

        threadShutdown = new Thread(runnerNew::stop, "sandbox-gui-shutdown");
        Runtime.getRuntime().addShutdownHook(threadShutdown);

        this.nNanoStart = System.nanoTime();
        separateRun();
        milestone("Starting LocalNetND " + strVersion
                + (strSnapshotRunning == null ? "" : " with snapshot " + strSnapshotRunning));
        // HIS WORDING, 2026-09-23 - the one start that runs twice as long says
        // so before the reader starts wondering why.
        if (flagFoundingPass)
            milestone("Founding. This takes a bit longer the first time.");
        // THE VERSION, NOT THE JAR FILE NAME. The window knows which Canton
        // was selected; the runner only holds a Path and printed that.
        milestone("Canton SDK " + instStock.version());
        // THE PQS LAMP FROM THE MOMENT START IS PRESSED, not from the moment
        // scribe launches. PENDING is the statement `StackService_i.Health`
        // defines for exactly this: it IS expected to come up and its own
        // start has not begun. Grey said the opposite - that nothing here
        // would ever start it - for the whole 76 seconds of the stack start.
        if (form.pqs() != SandboxOptions.PqsMode.OFF)
            lamps.setPqsHealth(SandboxService.Health.PENDING);
        this.strFailureLast = null;
        this.flagCancelStart = false;
        applyControls(SandboxService.State.STARTING);
        enterState(SandboxService.State.STARTING);
        status.setReport(null, null);

        LocalNetAuth authStackHere = authStack;
        this.authLocalRunning = authStack;
        Thread threadStart = new Thread(() -> {
            try {
                runnerNew.start();
                registerAtProvider(runnerNew, authStackHere);
                SwingUtilities.invokeLater(() -> localNetDone(runnerNew));
            }
            catch (RuntimeException ex) {
                // STOPPED BEFORE IT IS REPORTED, and this is the whole reason
                // the method exists twice over.
                //
                // `LocalNetRunner.start` leaves standing whatever it managed to
                // bring up - its own javadoc says close() is the caller's to
                // make - so a failed start otherwise keeps the splice JVM and
                // the embedded PostgreSQL alive, and the NEXT start refuses
                // with "another LocalNet is running" naming ports this window
                // opened itself. MEASURED 2026-09-21: canton exited on a
                // configuration error, splice and PostgreSQL did not, and
                // 32101 with the five splice ports were still open afterwards
                // while the canton ports were not.
                runnerNew.stop();
                SwingUtilities.invokeLater(() -> {
                    this.runner = null;
                    removeHook();
                    startFailed(String.valueOf(ex.getMessage()));
                });
            }
        }, "localnet-gui-start");
        threadStart.setDaemon(true);
        threadStart.start();
    }


    /**
     * THE CLIENT, ITS ORIGINS AND THE ROLE USERS, written to the provider this
     * window started, before anybody can be sent to a login box.
     *
     * ONLY ON THE EMBEDDED PROVIDER AND ONLY WHEN THE UIs SIGN IN THERE. An
     * external provider registers its clients through its own admins, and a
     * stack on the bundle's own HS256 secret signs nobody in anywhere. A
     * refusal is reported and does not fail the start: the stack is up, and
     * what is lost is the sign-in, which the line says.
     *
     * WHAT WAS REGISTERED GOES TO THE OIDC TAB, not the main log - his
     * instruction, 2026-09-23. The main log is the start; the tab is where the
     * provider is looked at. A FAILURE goes to the main log as well, because
     * it is something the start did not do.
     *
     * Called on the start thread.
     *
     * @param runnerHere the stack that came up
     * @param authHere what it verifies
     */
    private void registerAtProvider(LocalNetRunner runnerHere, LocalNetAuth authHere) {
        if (JwtMintProcess.isExternal() || authHere == null || !authHere.isProvider())
            return;
        try {
            // THE UPSTREAM NAMES TOO: a page opened as `wallet.localhost` signs
            // in and is sent back there, so that origin has to be registered.
            List<LocalNetUi.Page> lstPageAll = new ArrayList<>(runnerHere.lstPageWeb());
            lstPageAll.addAll(runnerHere.lstPageAliasWeb());
            List<String> lstLine = MintRegistration.lstRegister(JwtMintProcess.strUrlBase(),
                    lstPageAll);
            SwingUtilities.invokeLater(() -> lstLine.forEach(paneJwt::note));
        }
        catch (IOException ex) {
            String strLine = "OIDC registration FAILED, the web UIs cannot sign in - "
                    + ex.getMessage();
            SwingUtilities.invokeLater(() -> {
                paneJwt.note(strLine);
                milestone(strLine);
            });
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }


    /**
     * @param runnerHere the stack that came up
     */
    private void localNetDone(LocalNetRunner runnerHere) {
        if (flagCancelStart)
            return;
        // THE ENDPOINT FIRST, because it is the one address in this table
        // that is the window's own rather than the stack's - and it was the
        // one address the table did not have. A LocalNetND that came up with
        // an unbound endpoint looked identical to one that came up.
        Map<String, String> mapRow = new LinkedHashMap<>();
        mapRow.put(ReadyReport.KEY_DISCOVERY, strPortDiscovery());
        mapRow.putAll(runnerHere.mapReach());
        status.setRows(mapRow);
        lamps.setStack(runnerHere);
        rawarAnnounce();
        startLocalNetPqs();
        mapDarsLocal.values().forEach(DarsLivePane::refresh);
        webRefresh();
        enterState(SandboxService.State.RUNNING);
        applyControls(SandboxService.State.RUNNING);
        // AFTER RUNNING: the Users tab lists the role users of a RUNNING stack.
        usersRefresh();
        // THE WALL CLOCK, the same instrument the Sandbox path uses and the
        // same phrasing: between the component milestones sit the port wait,
        // the bootstrap and the founding, and a total that left those out
        // would be a smaller number than the one the reader just sat through.
        discoveryWarnIfUnbound();
        milestone("LocalNetND up in " + Milestones.strTimeOf(
                Duration.ofNanos(System.nanoTime() - nNanoStart)));
        // AND NOTHING IS UPLOADED ON THIS PATH, so the ledger is founded and
        // empty right here - unlike the Sandbox, where the DARs go in during
        // the participant phase and the pass has to run DAR-free to find this
        // moment at all.
        if (flagFoundingPass) {
            // THE FOUNDING SNAPSHOT IS TAKEN OF AN EMPTY LEDGER, so the fixture
            // waits: `foundingPassDone` stops the stack, copies the cluster and
            // starts again, and the start that follows is the one that gets the
            // story. A fixture written before the copy would be IN the founding
            // snapshot and would then arrive on every start after it.
            foundingPassDone();
            return;
        }
        runPharmaIfStaged();
    }


    private void startRequested() {
        // THE BIND IS TRIED AGAIN HERE, and this is the defect of 2026-09-22.
        // The constructor binds once. An operator who starts this window while
        // a previous Sandbox still holds the discovery port gets the conflict
        // line at window open, closes the old process a second later - and
        // this one never looks at the port again, because the only other
        // caller is `settingsSaved`. The stack then came up with no endpoint
        // and the Workbench could not find it.
        //
        // A SUCCESSFUL BIND IS STILL SILENT and a bind that is already good
        // returns on the first `if`, so the ordinary start says nothing new.
        discoveryRebind();
        rawarRebind();
        if (isLocalNet()) {
            startLocalNet();
            return;
        }
        SandboxOptions options;
        // DECLARED OUT HERE so the opening line can name it. It is read
        // inside the try because reading it can refuse the start.
        String strSnapshot = null;
        try {
            // BEFORE the options are read, because the wipe is what makes the
            // cluster path a first start every time and because toOptions()
            // asks the DAR directory whether it has anything in it - a
            // question that has to be put to a directory that exists.
            form.run().prepare();
            // READ AT START, not when the row was clicked. The list says what
            // the next start runs on, so pressing Start twice runs the same
            // snapshot twice - which is the reset this was asked for.
            strSnapshot = strSnapshotSelected();
            // RECOMPUTED rather than trusted, as the LocalNetND path does: the
            // port block is in the key since D-852 and the fields may have
            // moved since the version was selected.
            founding = foundingOfSandbox(form.selected());
            refreshFoundingState();
            Founding foundingHere = founding;
            Founding.Action action = Founding.actionOf(strSnapshot,
                    foundingHere != null && foundingHere.exists());
            // THE PASS IS DECIDED HERE AND NOT AFTER THE START, because it
            // changes what the start is GIVEN. A founding snapshot holds
            // nothing but the founded, empty ledger - operator instruction,
            // 2026-09-22 - and on this path the DARs go in as `--dar` on the
            // Canton command line DURING the participant phase, so a start
            // that carried them has no DAR-free moment left to copy.
            this.flagFoundingPass = action == Founding.Action.FOUND
                    && foundingHere != null
                    && !foundingHere.strKey().equals(strFoundingTried);
            // MARKED BEFORE THE START, not after the copy. The mark is `this
            // was attempted`, not `this succeeded` - see the field.
            if (flagFoundingPass)
                this.strFoundingTried = foundingHere.strKey();
            if (action == Founding.Action.RESTORE_USER) {
                // AFTER the wipe, over the top of it.
                String strWhy = whyRefused(strSnapshot);
                if (strWhy != null) {
                    throw new IllegalArgumentException("that snapshot does not"
                            + " match this form:\n" + strWhy);
                }
                snapshots.restore(strSnapshot, form.run().dirData());
            }
            else if (action == Founding.Action.RESTORE_FOUNDING) {
                // THE SAME PLACE AND THE SAME ORDER as a user snapshot: after
                // the wipe, over the top of it. The only differences are that
                // nothing chose it and nothing displays it.
                foundingHere.restore(form.run().dirData());
            }
            this.strSnapshotRunning = strSnapshot;
            // NO DARs ON THE PASS. They are the whole of what would make the
            // snapshot something other than the initial data, and the restart
            // that follows carries them as usual.
            options = form.toOptions(flagFoundingPass ? List.<Path>of()
                    : paneDars.lstFileSelected());
            // AFTER the fields have been proved usable and before anything
            // starts. A profile written from fields that toOptions() would
            // have refused is a profile that reloads into a form which cannot
            // start, which is a worse state than not having saved.
            saveProfile();
        }
        catch (RuntimeException ex) {
            // CLEARED ON EVERY REFUSAL. A flag left set by a start that never
            // happened would make the next one a pass it was not asked to be.
            this.flagFoundingPass = false;
            Modals.warn(this, ex.getMessage());
            return;
        }

        logPostgres.clear();
        logParticipant.clear();
        logPqs.clear();
        paneDataPostgres.reset();
        paneDataParticipant.reset();

        // BEFORE Canton, and restarted rather than left alone. The
        // BEFORE ANYTHING ELSE, because the participant's own configuration is
        // built three lines below this and cannot be revised afterwards. An
        // external provider that has not answered leaves the key set url
        // unknown, and a start that went ahead would configure the participant
        // from a guessed path and fail a minute later as a timeout.
        String strWhyNoProvider = paneJwt.strWhyNoProvider();
        if (strWhyNoProvider != null) {
            Modals.warn(this, strWhyNoProvider);
            return;
        }

        // BEFORE Canton, and restarted rather than left alone. The
        // participant reads the JWKS while it starts, so the service has to
        // be answering by then; and a mint left over from the previous run
        // may have been given different settings since.
        paneJwt.restartService();
        // WHAT THE MINT WILL BE ASKED FOR, told before Canton comes up so the
        // tab can say what a token will look like while the start is running.
        AuthSettings authNow = form.auth();
        paneJwt.useAuth(authNow, options.version(),
                StorageOverlay.STR_NODE_PARTICIPANT, form.nPortJsonApi());

        // The VERBOSE narration goes to the participant tab beside the tee.
        // The milestones are the pane on this tab.
        SandboxService serviceNew = new SandboxService(options,
                logParticipant::append);
        serviceNew.useMilestoneSink(this::milestone);
        // ONE PLAN, BOTH SIDES. The participant is configured from it and the
        // mint issues against it; neither is told anything the other was not.
        // THE SELECTED VERSION, not a constant and not just its family. The
        // JWKS auth-service type is spelt differently on the two generations,
        // and `max-token-life` is refused below 3.5.6, measured across nine
        // installs. A hard-coded V3X here is a 2.x stack that will not
        // start; a family-wide gate is a 3.4 stack that will not start.
        serviceNew.useAuth(authNow.overlay(options.version(),
                StorageOverlay.STR_NODE_PARTICIPANT, JwtMintProcess.strUrlJwks()),
                UUID.randomUUID().toString());
        tailCanton.start();
        serviceNew.useComponentSink(this::route);
        this.service = serviceNew;

        // Last defence only. The window stops the stack itself on close; this
        // is what catches a kill that never reaches the window.
        threadShutdown = new Thread(serviceNew::stop, "sandbox-gui-shutdown");
        Runtime.getRuntime().addShutdownHook(threadShutdown);

        this.nNanoStart = System.nanoTime();
        // THE OPENING LINE OF A RUN, and the only place the snapshot is
        // named. `Loaded snapshot X` said the same thing one line earlier
        // and left the reader to join them up.
        separateRun();
        milestone("Starting Sandbox"
                + (strSnapshot == null ? "" : " with snapshot " + strSnapshot));
        // ITS OWN LINE, not a clause on the one above - operator instruction,
        // 2026-09-22. The run that follows looks like any other start apart
        // from this, and a reader scanning the left edge for `Starting
        // Sandbox` should find it in the same shape every time.
        if (flagFoundingPass)
            milestone("No founding snapshot. Creating.");
        // CLEARED HERE, so a run that fails cannot be handed the previous
        // run's reason by a harness that reads it after the state arrives.
        this.strFailureLast = null;
        this.flagCancelStart = false;
        applyControls(SandboxService.State.STARTING);
        enterState(SandboxService.State.STARTING);
        timerSettle.stop();
        status.setReport(null, null);

        Thread threadStart = new Thread(() -> {
            try {
                // BEFORE the start and ON THIS THREAD, because it blocks on a
                // Spring Boot process that was restarted moments ago and the
                // event dispatch thread cannot wait for one.
                //
                // BOTH LINES, and 2.x was only ever the half that had been
                // measured. The note here used to say the 3.x participant
                // takes the pinned admin token through `admin-token-config`,
                // which is true and is not enough: that token is a UUID with
                // no `sub`, and scribe opens with
                // `UserManagementService/ListUserRights`, which Canton refuses
                // with INVALID_TOKEN(8) when the user-id is empty. Measured
                // on scribe v3.5.7 against a 3.5.12 participant.
                //
                // A wildcard participant is left alone: it checks nothing, so
                // PQS presents nothing and scribe runs NoAuth, which is what it
                // did before the auth row existed.
                if (authNow.mode() != AuthSettings.Mode.NONE) {
                    // BEFORE ANY WAIT. Waiting a minute for a JWKS from a
                    // process that failed to launch reports the timeout and
                    // buries the cause, which on a machine with no packaged
                    // mint is the whole of the answer.
                    String strWhyNoMint = paneJwt.strWhyNoService();
                    if (strWhyNoMint != null) {
                        throw new IllegalStateException("the JWT service did not start, so"
                                + " the participant would have no key to verify tokens"
                                + " against:\n\n" + strWhyNoMint);
                    }
                    // WHETHER THE TOKEN HAS A CONSUMER AT ALL.
                    // `SandboxService.pqsFor` returns null on PqsMode.OFF, so
                    // with PQS off nothing ever reads the token or the OAuth
                    // endpoint and minting them is work done for nobody. What
                    // still has to happen either way is the WAIT: the
                    // participant fetches the JWKS while it starts, and the
                    // mint is a Spring Boot process launched moments ago. So
                    // the OFF column waits on the JWKS itself, which is the
                    // participant's actual precondition rather than a proxy
                    // for it.
                    boolean flagPqs = options.pqs() != SandboxOptions.PqsMode.OFF;
                    if (flagPqs) {
                        String strUserPqs = strUserPqs(options);
                        String strTokenPqs = JwtMintProcess.strMintBlocking(authNow,
                                options.version(), strUserPqs,
                                StorageOverlay.STR_NODE_PARTICIPANT, TIMEOUT_MINT);
                        if (strTokenPqs == null) {
                            throw new IllegalStateException("the JWT service did not mint a"
                                    + " token within " + TIMEOUT_MINT + ", so PQS would have"
                                    + " nothing to present to an authenticated Ledger API");
                        }
                        serviceNew.usePqsToken(strTokenPqs);
                        // AND THE ENDPOINT, which is what scribe actually runs
                        // on. The token above stays as the liveness proof that
                        // the mint answered and signed with the key this
                        // participant was configured for, taken before Canton
                        // starts rather than three minutes into a stream.
                        serviceNew.usePqsOAuth(JwtMintProcess.oauthPqs(authNow,
                                options.version(), strUserPqs,
                                StorageOverlay.STR_NODE_PARTICIPANT));
                    }
                    else if (!JwtMintProcess.isJwksServing(
                            authNow.strUrlJwksEffective(JwtMintProcess.strUrlJwks()),
                            TIMEOUT_MINT, null)) {
                        throw new IllegalStateException("nothing served the JWKS at "
                                + authNow.strUrlJwksEffective(JwtMintProcess.strUrlJwks())
                                + " within " + TIMEOUT_MINT + ", so the participant would"
                                + " have no key to verify tokens against. Check the JWKS URI"
                                + " row against the provider on the OIDC tab.");
                    }
                }
                // THE LAST CHEAP PLACE TO GIVE UP. Everything above this is
                // the mint, which the cancel path stops on its own; below it
                // the stack is running and the stop has to reach processes.
                if (flagCancelStart)
                    throw new IllegalStateException("the start was cancelled");
                serviceNew.start();
                SwingUtilities.invokeLater(() -> startDone(serviceNew));
            }
            catch (RuntimeException ex) {
                String strMessage = ex.getMessage();
                SwingUtilities.invokeLater(() -> startFailed(strMessage));
            }
        }, "sandbox-gui-start");
        threadStart.setDaemon(true);
        threadStart.start();
    }


    private void startDone(SandboxService serviceHere) {
        if (flagCancelStart) {
            // The stop landed after the stack was up. What this method would
            // report - RUNNING, with a report table - describes a stack that
            // is being taken down as it is read.
            startCancelled(serviceHere);
            return;
        }
        // THE WALL CLOCK, not the sum of the component times. Between them
        // sit the port wait, the bootstrap and the DAR upload, and a total
        // that left those out would be a smaller number than the one the
        // reader just sat through.
        milestone("Sandbox started in " + Milestones.strTimeOf(
                Duration.ofNanos(System.nanoTime() - nNanoStart)));
        enterState(SandboxService.State.RUNNING);
        footerSettles(STR_FOOTER_RUNNING);
        discoveryWarnIfUnbound();
        rawarAnnounce();
        webRefresh();
        usersRefreshAll();
        status.setReport(serviceHere.report().withFirst(ReadyReport.KEY_DISCOVERY,
                strPortDiscovery()),
                String.valueOf(serviceHere.fileStatus()));
        for (String strLine : serviceHere.report().lstLines()) {
            logParticipant.append(strLine);
        }
        lamps.setService(serviceHere);
        applyControls(SandboxService.State.RUNNING);
        paneDarsLive.refresh();
        // THE PASS TAKES ITS SNAPSHOT AND RESTARTS, and the fixture does NOT
        // run first: the snapshot is the founded, empty ledger and the
        // fixture's contracts would be inside it. The restart runs the fixture
        // on the restored cluster like any other start.
        if (flagFoundingPass) {
            foundingPassDone();
            return;
        }
        runAviationIfStaged(serviceHere);
    }


    private void startFailed(String strMessage) {
        if (flagCancelStart) {
            startCancelled(service);
            return;
        }
        // The message carries Canton's own log problems and the tail of its
        // output. It goes to the LOG rather than into a dialog, because it is
        // routinely twenty lines and a dialog would truncate the part that
        // says why.
        stopTails();
        if (strMessage != null) {
            // ONE line in the milestone pane and the whole of it beside the
            // output it came from. A twenty-line stack trace in a pane of
            // eight lines buries the seven that say how far the start got.
            milestone("FATAL: " + LaunchText.strFirstLine(strMessage));
            logParticipant.append(strMessage);
        }
        else {
            milestone("FATAL: the start failed and said nothing");
        }
        // BEFORE enterState, which is what wakes a harness waiting on the state
        // sink. Set after it, the harness can read the field between the two.
        this.strFailureLast = strMessage;
        enterState(SandboxService.State.FAILED);
        // NO SETTLE. A failure's own line is the whole of what the footer
        // has to say, and replacing it ten seconds later with a state word
        // would take away the only summary a reader gets.
        timerSettle.stop();
        status.setReport(null, null);
        removeHook();
        this.flagAviationBusy = false;
        this.flagFoundingPass = false;
        applyControls(SandboxService.State.FAILED);
    }


    private void stopRequested() {
        if (stateNow == SandboxService.State.STARTING) {
            // THE START IS THE THING BEING ABANDONED HERE, not a running
            // stack, and the question says which of the two it is.
            if (!Modals.isConfirmed(this, "Cancel the start and take down what is up?",
                    "Stop", JOptionPane.QUESTION_MESSAGE))
                return;

            cancelStart();
            return;
        }

        if (!Modals.isConfirmed(this, "Stop the sandbox?", "Stop",
                JOptionPane.QUESTION_MESSAGE))
            return;

        stopThen(null);
    }


    /**
     * Takes down a stack that is still coming up.
     *
     * The start runs on its own thread and cannot be interrupted usefully -
     * it is blocked on a process, a port or a JWKS fetch - so the stack is
     * stopped underneath it and the start thread is left to discover that.
     * What it discovers is a failure, and {@link #flagCancelStart} is what
     * stops it being reported as one.
     *
     * The stop runs off the event dispatch thread, because it waits for
     * processes to go.
     */
    private void cancelStart() {
        // THE STACK, NOT THE SANDBOX SERVICE. On the LocalNet topology
        // `service` is null, so this returned without stopping anything and
        // Stop during a start left the whole network running.
        StackService_i serviceHere = stack();
        if (serviceHere == null)
            return;

        this.flagCancelStart = true;
        applyControls(SandboxService.State.STOPPING);
        milestone("Cancelling the start");
        enterState(SandboxService.State.STOPPING);

        Thread threadCancel = new Thread(serviceHere::stop, "sandbox-gui-cancel");
        threadCancel.setDaemon(true);
        threadCancel.start();
    }


    /**
     * The end of a cancelled start, reached from either end of the start
     * thread. Leaves the window exactly as a Stop does.
     *
     * @param serviceHere what was being started, or null
     */
    private void startCancelled(SandboxService serviceHere) {
        this.flagCancelStart = false;
        this.strFailureLast = null;
        tailCanton.stop();

        // A SECOND STOP, and it is not belt and braces. `cancelStart` may have
        // run while the start thread was between its own check and the stack
        // being assigned, in which case the first stop found nothing to do and
        // there are processes nobody is holding. stop() is idempotent.
        if (serviceHere != null) {
            Thread threadStop = new Thread(serviceHere::stop, "sandbox-gui-cancel");
            threadStop.setDaemon(true);
            threadStop.start();
        }

        enterStopped("Stopped Sandbox - the start was cancelled");
    }


    /**
     * Everything a window does on its way back to STOPPED, from one place.
     *
     * A CANCELLED START AND AN ORDINARY STOP END IDENTICALLY, and until this
     * method they said so twice, in eighteen lines each, with one of the two
     * copies quietly missing a control. Two copies of an end state is two
     * places to forget a pane the next increment adds.
     *
     * `tailCanton.stop()` is NOT here. Both callers already stop it, and each
     * does so at a different point in its own sequence.
     *
     * @param strMilestone what the log says this stop was
     */
    private void enterStopped(String strMilestone) {
        milestone(strMilestone);
        // NO BLANK LINE HERE ANY MORE - it is written at the HEAD of the next
        // run instead, by `separateRun`. Two reasons, and the second is the
        // one that moved it. A stop is not always followed by a start - the
        // window closes through one - so a separator written here left a
        // trailing blank as the last thing in the pane. And anything a run
        // reports AFTER its stop, which is what the founding pass does with
        // `Founding snapshot taken`, landed under the separator and read as
        // the first line of the next run rather than the last of its own.
        enterState(SandboxService.State.STOPPED);
        footerSettles(STR_FOOTER_STOPPED);
        status.setReport(null, null);
        paneDataPostgres.reset();
        paneDataParticipant.reset();
        removeHook();
        service = null;
        lamps.setService(null);
        paneJwt.stopService();
        this.flagAviationBusy = false;
        applyControls(SandboxService.State.STOPPED);
        // A START UPLOADS DARS, and a stack that has just stopped is the
        // moment a developer goes and builds another one.
        paneDars.refresh();
        paneDarsLive.reset();
        mapDarsLocal.values().forEach(DarsLivePane::reset);
        webRefresh();
        strTokenUsers = null;
        usersRefresh();
        // THE FIXTURE LAMP GOES OFF WITH THE STACK. It was left on the last
        // fixture result, so a stopped window showed a green Test fixture over
        // a ledger that was no longer there.
        lamps.setFixtureHealth(SandboxService.Health.OFF);
        refreshSnapshots();
    }


    /**
     * @param runAfter what to do on the EDT once the stack is down, or null
     */
    /**
     * @return whether this window was opened on the LocalNet topology
     */
    /**
     * @param strTopology which stack the window drives
     * @return the title bar
     */
    private static String strTitleFor(String strTopology) {
        return DiscoveryDoc.STR_TOPOLOGY_LOCALNET.equals(strTopology)
                ? STR_TITLE_LOCALNET : STR_TITLE;
    }


    /**
     * THE STOCK CANTON INSTALLATION, FOUND RATHER THAN DEMANDED.
     *
     * `CantonImage.fromEnvironment` looks in `~/.raposza/canton-image` and
     * refuses when nothing is banked there, which is what stopped the first
     * LocalNet start from this window on 2026-09-21. This application already
     * discovers every Canton on the machine for its own version box, so the
     * newest one carrying a runtime is handed to the runner as a pinned jar
     * and the banked directory becomes a fallback rather than a requirement.
     *
     * NEWEST BY VERSION, the same comparison the form's box uses to pick its
     * default - `CantonInstallation.compareTo` sorts newest first, so a
     * version comparison is the one that reads as it means.
     *
     * THE INSTALLATION AND NOT THE JAR, since 2026-09-21: the three
     * participants run inside this Canton, so its VERSION is what decides
     * whether their auth block may carry a `max-token-life` at all.
     *
     * @return the installation, or null when no installed Canton has a
     *         runtime; null leaves the runner on its own resolution and its
     *         own message
     */
    private static CantonInstallation instCantonStock() {
        CantonInstallation instBest = null;
        for (CantonInstallation inst : SandboxService.lstCanton()) {
            if (!inst.hasRuntime())
                continue;
            if (instBest == null || inst.version().compareTo(instBest.version()) > 0)
                instBest = inst;
        }
        return instBest;
    }


    /**
     * The node name the LocalNet audience convention is built on.
     *
     * ONE NAME FOR THE THREE, deliberately - `LocalNetAuth.strConfCanton`
     * gives all three roles the same target, so one token works against
     * whichever of the three Ledger APIs it is pointed at.
     */
    private static final String STR_NODE_LOCALNET = "localnet";

    /**
     * The OAuth client the four web UIs identify as. The window registers it,
     * with its origins, at every start on the embedded provider -
     * {@link MintRegistration}.
     */
    private static final String STR_CLIENT_UI = MintRegistration.STR_CLIENT_UI;


    /**
     * What the LocalNet auth rows come up holding.
     *
     * JWKS, because both providers this window can use - the embedded one and
     * an external one - are read through a key set; and an audience of this
     * node's own, because `AuthSettings`'s convention names the SANDBOX node
     * and that audience means nothing to a LocalNet participant.
     *
     * @return the settings the rows are filled from
     */
    private static AuthSettings authLocalNetDefault() {
        return new AuthSettings(AuthSettings.Mode.JWKS, TokenShape.AUDIENCE,
                AuthSettings.ofDefaults().strAudienceFor(STR_NODE_LOCALNET), "", "", "");
    }


    /**
     * The six rows, as the thing the three participants are written to verify.
     *
     * @param auth what the rows hold
     * @param version the Canton the three participants run inside
     * @return what the stager renders into each role's app-auth.conf
     * @throws IllegalArgumentException naming the row, when the selection
     *         cannot be rendered
     */
    private static LocalNetAuth localNetAuthOf(AuthSettings auth, VersionId version) {
        // THE CEILING THE MINT ISSUES AT, rendered into the entry that
        // verifies against it. Absent, the participant applies its own default
        // of a few minutes and refuses the 24 h token with a log line saying
        // only that it was rejected - which is a correctly configured stack
        // that authenticates nothing. NULL BELOW THE FLOOR, where the key
        // itself is refused and the participant does not start.
        Duration ttlToken = version.compareTo(AuthOverlay.VER_FLOOR_TOKEN_LIFE) < 0
                ? null : auth.ttlToken(version);
        String strAudience = auth.shape() == TokenShape.AUDIENCE
                ? auth.strAudienceFor(STR_NODE_LOCALNET) : null;
        String strScope = auth.shape() == TokenShape.SCOPE
                ? auth.strScopeEffective() : null;

        switch (auth.mode()) {
            case NONE:
                return LocalNetAuth.ofWildcard();
            case UNSAFE_HMAC_256:
                return LocalNetAuth.ofSecret(auth.strSecretEffective(), strAudience,
                        strScope, ttlToken);
            case JWKS:
                return LocalNetAuth.ofJwks(
                        auth.strUrlJwksEffective(JwtMintProcess.strUrlJwks()),
                        strAudience, strScope, ttlToken)
                        // AND THE ISSUER, WHICH IS NOT THE KEY SET URL. A
                        // participant is handed the JWKS document; a browser
                        // is handed the issuer and reads
                        // `/.well-known/openid-configuration` itself to find
                        // the authorization and token endpoints. Only this
                        // window knows both, and it is the same value whether
                        // the provider is the embedded one or an external one.
                        .withProvider(JwtMintProcess.strUrlBase(), STR_CLIENT_UI);
            default:
                return LocalNetAuth.ofCertificate(auth.mode().strType(),
                        auth.strFileCert(), strAudience, strScope, ttlToken);
        }
    }


    /**
     * @return whether this window was opened on the LocalNet topology
     */
    private boolean isLocalNet() {
        return DiscoveryDoc.STR_TOPOLOGY_LOCALNET.equals(strTopology);
    }


    /**
     * @return whichever stack this window is driving, or null when nothing was
     *         started
     */
    private StackService_i stack() {
        return runner != null ? runner : service;
    }


    private void stopThen(Runnable runAfter) {
        StackService_i serviceHere = stack();
        if (serviceHere == null || !serviceHere.isRunning()) {
            removeHook();
            if (runAfter != null)
                runAfter.run();
            return;
        }

        applyControls(SandboxService.State.STOPPING);
        enterState(SandboxService.State.STOPPING);
        milestone("Stopping Sandbox");

        Thread threadStop = new Thread(() -> {
            try {
                // BEFORE THE STACK. scribe streams from a participant that is
                // about to go, and a scribe still ingesting while the Ledger
                // API closes under it prints a page of connection errors that
                // read as a fault rather than as a shutdown.
                stopLocalNetPqs();
                serviceHere.stop();
            }
            finally {
                SwingUtilities.invokeLater(() -> {
                    stopTails();
                    enterStopped("Stopped Sandbox");
                    if (runAfter != null)
                        runAfter.run();
                });
            }
        }, "sandbox-gui-stop");
        threadStop.setDaemon(true);
        threadStop.start();
    }


    /**
     * Stop, then start again on the same snapshot selection.
     *
     * It is {@link #stopThen} with {@link #startRequested} hung off the end,
     * and deliberately nothing more. Every rule a hand-driven stop-then-start
     * obeys is obeyed here because it IS one: the run directory is wiped by
     * `prepare()`, the snapshot is re-checked against the form before it is
     * laid down, the profile is saved, the mint is restarted, and a snapshot
     * that no longer matches refuses the start rather than being loaded anyway.
     * A shorter path that reused the running cluster would have to restate all
     * of that and would drift from it on the first change to either.
     *
     * WHAT IT COSTS TODAY: the start waits on the PostgreSQL port, up to a
     * minute, because {@link com.raposza.runtime.port.PortGuard}
     * binds with `SO_REUSEADDR` off and the sockets the stopped postmaster
     * left are still in TIME_WAIT. That wait is the guard's, not the
     * database's, and removing it is a separate change to a separate
     * module.
     */
    private void resetRequested() {
        // WHAT IT COSTS IS THE LEDGER, and the message says so rather than
        // saying `are you sure`: a restart onto the selected snapshot - or
        // onto an empty ledger when none is selected - discards whatever the
        // running stack has accumulated, and that is the whole reason to ask.
        if (!Modals.isConfirmed(this,
                "Reset stops the sandbox and starts it again.\nEverything the running"
                        + " ledger holds is discarded.",
                "Reset", JOptionPane.WARNING_MESSAGE))
            return;

        stopThen(this::startRequested);
    }


    private void closeRequested() {
        // THE SAME CORRECTION, AND THIS ONE IS WHAT LEAKED A WHOLE LOCALNET.
        // `service` is null on the LocalNet topology, so closing the window
        // took the branch below, removed the shutdown hook and disposed - and
        // left PostgreSQL, canton and splice running and holding the ports,
        // so the NEXT start refused. Measured 2026-09-21.
        StackService_i serviceHere = stack();
        if (serviceHere == null || !serviceHere.isRunning()) {
            saveProfile();
            discardFixtures();
            removeHook();
            timerLamp.stop();
            timerUsers.stop();
            discovery.stop();
            rawar.stop();
            paneJwt.stopService();
            dispose();
            return;
        }

        if (!Modals.isConfirmed(this, "The sandbox is running. Stop it and close?",
                "raposza sandbox", JOptionPane.QUESTION_MESSAGE))
            return;

        stopThen(() -> {
            // AFTER the stop, because the form is disabled during it and the
            // fields are the same either way. Before the dispose, because a
            // disposed window's fields are still readable and that is not
            // something to rely on.
            saveProfile();
            discardFixtures();
            timerLamp.stop();
            timerUsers.stop();
            discovery.stop();
            rawar.stop();
            paneJwt.stopService();
            dispose();
        });
    }


    /**
     * Takes both fixtures off disk on the way out.
     *
     * NOTHING OF EITHER SURVIVES THIS WINDOW. The staged project, the DAR the
     * compiler wrote and the copy in the run directory all go; the source is
     * a resource of the jar, so the next window stages it again.
     *
     * UNLESS A SNAPSHOT WAS TAKEN HERE. That cluster holds the fixture's
     * contracts and the packages they were created under are in the DAR, so
     * removing it would leave a snapshot with nothing to read it.
     *
     * BOTH, WHATEVER THE TOPOLOGY. Only one of them is ever offered in a given
     * window, and the other's `discard` on a session that never built anything
     * removes an earlier session's leftovers, which is what it is for.
     */
    private void discardFixtures() {
        if (flagSnapshotSaved)
            return;
        aviation.discard(form.selected());
        pharma.discard(instCantonStock());
    }


    /**
     * The second half of the founding pass: the cluster that has just been
     * founded, copied, and then a real start laid on top of it.
     *
     * WHY IT STOPS. A snapshot is the data directory copied file for file, and
     * a cluster copied out from under a running server is a cluster mid-write -
     * {@link Snapshots}. This is the same stop {@link #saveRequested} takes and
     * for the same reason. There is no online route that does not add a
     * PostgreSQL binary this application deliberately does not need.
     *
     * THE FLAG IS CLEARED BEFORE THE RESTART, unconditionally. The restart runs
     * {@link #startRequested} again, which decides the pass afresh - and with
     * the snapshot now on disk it takes the restore branch instead. A flag left
     * set would answer `FOUND` for ever and the window would start, stop and
     * start without end.
     */
    private void foundingPassDone() {
        Founding foundingHere = founding;
        this.flagFoundingPass = false;
        if (foundingHere == null) {
            // The selection went away while the founding was running. The
            // stack is up on a founded ledger, which is what was asked for -
            // there is just nowhere left to keep a copy of it.
            milestone("No founding snapshot taken: the Canton selection went away");
            return;
        }

        // READ BEFORE THE STOP, exactly as `saveRequested` does: afterwards
        // the form is the only thing left and it is what the operator may
        // already be editing for the next run. THE CLUSTER PATH TOO - the
        // LocalNet one comes off the runner, and `enterStopped` lets that
        // field go.
        Properties props = propsOfRun();
        props.setProperty(Founding.STR_KEY_TOPOLOGY, String.valueOf(strTopology));
        Path dirCluster = dirClusterNow();
        if (dirCluster == null) {
            milestone("No founding snapshot taken: there is no cluster to copy");
            return;
        }
        stopThen(() -> {
            try {
                foundingHere.save(dirCluster, props);
                milestone("Founding snapshot taken");
            }
            catch (RuntimeException ex) {
                // NOT FATAL AND NOT RETRIED. The start that follows founds
                // again, which is exactly the behaviour of every start before
                // this existed, so a failed copy costs time and nothing else.
                milestone("Founding snapshot failed: " + ex.getMessage());
            }
            refreshFoundingState();
            startRequested();
        });
    }


    /**
     * @return the cluster of whichever stack is running, or null when there is
     *         none to read
     */
    private Path dirClusterNow() {
        if (!isLocalNet())
            return form.run().dirData();
        LocalNetRunner runnerHere = runner;
        return runnerHere == null ? null : runnerHere.dirPgData();
    }


    /**
     * DELETES THE FOUNDING SNAPSHOT so the next start founds again - operator
     * instruction, 2026-09-22.
     *
     * THE USER SNAPSHOTS ARE NOT TOUCHED, and the confirmation says so: the
     * two live in different roots and a reader who took this for the button
     * that clears the list would lose the state they built one to keep.
     */
    private void resetFoundingRequested() {
        Founding foundingHere = founding;
        if (foundingHere == null || !foundingHere.exists()) {
            warn("there is no founding snapshot to reset");
            return;
        }

        if (!Modals.isConfirmed(this,
                "Delete the founding snapshot for " + foundingHere.strKey() + "?\nThe"
                        + " next start founds the network again and takes a new one.\n"
                        + "Your own snapshots are not touched.",
                "Reset founding", JOptionPane.WARNING_MESSAGE))
            return;

        try {
            foundingHere.delete();
        }
        catch (RuntimeException ex) {
            warn(ex.getMessage());
            return;
        }

        // AND THE ONE-ATTEMPT GUARD GOES WITH IT. Reset founding is the only
        // way to ask for another pass after one failed, so it has to.
        this.strFoundingTried = null;
        // ITS OWN BLOCK. This is a thing the operator did between runs, not
        // part of either, so it is set apart from both.
        separateRun();
        milestone("Founding snapshot deleted");
        refreshFoundingState();
    }


    /**
     * THE NUMBERING LOCALNETND IS ON, or would be on at the next start.
     *
     * A STACK THAT IS UP KEEPS ITS OWN. A port moved on the Settings tab while
     * LocalNetND runs changes the NEXT start; the discovery document, the
     * Pharma story, the founding key and a snapshot saved now all describe the
     * stack that is running, so they read the runner's numbering until it has
     * stopped. Otherwise it is the settings - `RaposzaSettings.portsLocalNet`,
     * which is what `startLocalNet` hands the runner.
     *
     * @return never null
     */
    /**
     * Where each participant of the running stack keeps its ledger, for the
     * Ledger log.
     *
     * On LocalNetND the three participants share the stack's PostgreSQL and
     * `LocalNetSpec` names their databases; on the Sandbox topology the ready
     * report carries the one participant's JDBC url, or nothing when it runs
     * in memory - and then there is nothing to follow.
     *
     * @return node label to coordinates, possibly empty; never null
     */
    private Map<String, PostgresCoordinates> mapLedgerDatabases() {
        Map<String, PostgresCoordinates> mapOut = new LinkedHashMap<>();
        if (isLocalNet()) {
            int nPortPg = portsLocalNet().nPortPostgres();
            for (String strRole : LocalNetPorts.LST_ROLE) {
                String strDb = LocalNetSpec.strDbParticipant(strRole);
                if (strDb != null) {
                    mapOut.put(strRole, new PostgresCoordinates(LocalNetRunner.STR_HOST, nPortPg,
                            strDb, LocalNetSpec.STR_DB_USER, LocalNetSpec.STR_DB_PASSWORD));
                }
            }
            return mapOut;
        }
        PostgresCoordinates coord = LedgerProbe.coordinatesParticipant(service);
        if (coord != null)
            mapOut.put("participant", coord);
        else
            logLedger.append("nothing to follow - this participant keeps no database");
        return mapOut;
    }


    private LocalNetPorts portsLocalNet() {
        LocalNetRunner runnerHere = runner;
        if (runnerHere != null && runnerHere.state() != StackService_i.State.STOPPED)
            return runnerHere.ports();
        return RaposzaSettings.current().portsLocalNet();
    }


    /**
     * THE LOCALNETND KEY, from what is selected now and nothing that has
     * started. Both halves are known before any stack runs, which is what
     * lets the Settings tab and the snapshot list answer at window open.
     *
     * @return the key, or null when either half of it is missing
     */
    private String strKeyLocalNet() {
        String strVersion = form.strVersionSelected();
        CantonInstallation instStock = instCantonStock();
        if (strVersion == null || instStock == null)
            return null;
        // THE SAME NUMBERING THE RUNNER IS GIVEN - `portsLocalNet`. Two
        // literals here would key a snapshot on ports the stack does not bind
        // the day either setting moves.
        LocalNetPorts portsHere = portsLocalNet();
        return Founding.strKeyOfLocalNet(strVersion, instStock.version(),
                instStock.edition(), portsHere.nPortFirst(), portsHere.nPortPostgres());
    }


    /**
     * THE SANDBOX KEY, from the version and the ports the form holds - D-852.
     *
     * @param inst the selected Canton, or null when there is none
     * @return the founding store, or null when no Canton is selected
     * @throws IllegalArgumentException when a port field is not a port, with
     *         the row's name
     */
    private Founding foundingOfSandbox(CantonInstallation inst) {
        if (inst == null)
            return null;
        return Founding.of(Founding.strKeyOfSandbox(inst.version(), inst.edition(),
                form.nPortJsonApi(), form.nPortPostgres()));
    }


    /**
     * @return the founding store for this topology, or null when either half
     *         of the key is missing
     */
    private Founding foundingOfLocalNet() {
        String strKey = strKeyLocalNet();
        return strKey == null ? null : Founding.of(strKey);
    }


    /**
     * ONE KEY, TWO STORES. The user snapshots sit under `localnet/<key>/` and
     * the founding one under `founding/<key>/`, so a bundle or a port block
     * that moves hides both at once rather than offering a list that cannot be
     * restored.
     *
     * @return the user snapshot store for this topology, or null when either
     *         half of the key is missing
     */
    private Snapshots snapshotsOfLocalNet() {
        String strKey = strKeyLocalNet();
        return strKey == null ? null : Snapshots.ofLocalNet(strKey);
    }


    /** Tells the Settings tab whether a founding snapshot exists now. */
    private void refreshFoundingState() {
        Founding foundingHere = founding;
        paneSettings.setFoundingState(foundingHere != null && foundingHere.exists(),
                foundingHere == null ? null : foundingHere.strKey());
    }


    private void removeHook() {
        Thread threadHere = threadShutdown;
        if (threadHere == null)
            return;
        threadShutdown = null;
        try {
            Runtime.getRuntime().removeShutdownHook(threadHere);
        }
        catch (IllegalStateException ex) {
            // The JVM is already shutting down; the hook is running or has run.
        }
    }


    /**
     * Save STOPS THE STACK. A cluster copied out from under a running
     * server is a cluster mid-write, and this is the one place the window
     * can be certain nothing is holding it: after its own stop.
     */
    private void saveRequested() {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null) {
            // A SNAPSHOT BELONGS TO A VERSION. With none selected there is
            // nothing for it to belong to, and a snapshot in a root nobody
            // owns is one no window would ever offer again.
            warn("no Canton is selected, so there is no version to take a snapshot for");
            return;
        }

        String strName = Modals.strInput(this,
                "Name for this snapshot. The sandbox will be STOPPED to take it.",
                "Save snapshot");
        if (strName == null)
            return;

        String strTrim = strName.trim();
        String strWhyName = SnapshotRules.strRefusalOfName(strTrim, snapshotsHere.lstNames());
        if (strWhyName != null) {
            warn(strWhyName);
            return;
        }

        Properties props;
        try {
            props = propsOfRun();
        }
        catch (IllegalArgumentException ex) {
            // A PORT FIELD THAT IS NOT A PORT, on a stopped stack - D-852.
            warn(String.valueOf(ex.getMessage()));
            return;
        }
        // THE CLUSTER PATH TOO, and before the stop: on this topology it comes
        // off the runner and `enterStopped` lets that field go, so a save that
        // read it afterwards would find nothing to copy.
        Path dirCluster = dirClusterNow();
        if (dirCluster == null) {
            warn("there is no cluster to copy");
            return;
        }
        stopThen(() -> saveNow(strTrim, props, dirCluster));
    }


    /**
     * @param strName what to call it
     * @param props what was running when Save was pressed - read BEFORE the
     *        stop, because afterwards the form is the only thing left and it
     *        is what the operator may already be editing for the next run
     * @param dirCluster the cluster to copy, read before the stop for the same
     *        reason and because the LocalNet one comes off the runner
     */
    private void saveNow(String strName, Properties props, Path dirCluster) {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null) {
            milestone("No snapshot taken: the selection went away");
            return;
        }

        try {
            snapshotsHere.save(strName, dirCluster, props);
            // WHAT SPARES THE FIXTURE AT CLOSE. The cluster just written
            // holds its contracts, and their packages are in the DAR.
            this.flagSnapshotSaved = true;
            milestone("Saved snapshot " + strName);
            refreshSnapshots();
            lstSnapshot.setSelectedValue(strName, true);
        }
        catch (RuntimeException ex) {
            milestone("Snapshot failed: " + ex.getMessage());
            warn(ex.getMessage());
        }
    }


    /**
     * @return the snapshot the next start runs on, or null for an empty one
     */
    private String strSnapshotSelected() {
        String strName = lstSnapshot.getSelectedValue();
        return strName == null || STR_SNAPSHOT_NONE.equals(strName) ? null : strName;
    }


    /**
     * @param strName the snapshot to check
     * @return what disagrees with the form, or null when nothing does
     */
    private String whyRefused(String strName) {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null)
            return "no Canton is selected";

        Properties props;
        try {
            props = snapshotsHere.read(strName);
        }
        catch (RuntimeException ex) {
            return ex.getMessage();
        }
        try {
            return SnapshotRules.strDisagreement(props, propsOfForm());
        }
        catch (IllegalArgumentException ex) {
            // A PORT FIELD THAT IS NOT A PORT. The start refuses it by name
            // anyway; here it is simply what disagrees.
            return ex.getMessage();
        }
    }


    /** What the form asks for, as a snapshot would record it. */
    private Properties propsOfForm() {
        if (!isLocalNet())
            return SnapshotRules.propsOfForm(form.selected(), form.nPortJsonApi(),
                    form.nPortPostgres());

        // A-35 (b). `form.selected()` is ALWAYS null here, so the Sandbox
        // spelling recorded an empty Canton version and edition; the Canton
        // the three participants run inside is the stock one.
        CantonInstallation instStock = instCantonStock();
        LocalNetPorts portsHere = portsLocalNet();
        return SnapshotRules.propsOfLocalNetForm(form.strVersionSelected(),
                instStock == null ? null : instStock.version(),
                instStock == null ? null : instStock.edition(),
                portsHere.nPortFirst(), portsHere.nPortPostgres());
    }


    /** The same, plus what the RUNNING stack adds to it. */
    private Properties propsOfRun() {
        if (isLocalNet()) {
            Properties props = propsOfForm();
            props.setProperty(Snapshots.STR_KEY_PQS, "");
            return props;
        }
        // THE RUNNING STACK'S BLOCK, not the fields', which may have been
        // edited since the start - D-852.
        SandboxService serviceHere = service;
        if (serviceHere == null)
            return SnapshotRules.propsOfRun(form.selected(), "", form.nPortJsonApi(),
                    form.nPortPostgres());
        SandboxOptions optionsHere = serviceHere.options();
        return SnapshotRules.propsOfRun(form.selected(), String.valueOf(optionsHere.pqs()),
                optionsHere.nPortOffset() + SandboxPorts.N_DEFAULT_JSON_API,
                optionsHere.nPortPostgres());
    }


    /**
     * Re-reads the snapshot directory, keeping the selection when it is
     * still there.
     */
    private void refreshSnapshots() {
        String strWas = lstSnapshot.getSelectedValue();
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null) {
            // No version selected, so there is no version whose snapshots
            // these would be. The empty row alone, which is what a start with
            // nothing selected would do anyway.
            modelSnapshot.clear();
            modelSnapshot.addElement(STR_SNAPSHOT_NONE);
            lstSnapshot.setSelectedValue(STR_SNAPSHOT_NONE, true);
            return;
        }

        List<String> lstName;
        try {
            lstName = snapshotsHere.lstNames();
        }
        catch (RuntimeException ex) {
            // A snapshot directory that cannot be listed is not a reason to
            // refuse to show a window, and the label now says so.
            milestone("Could not list snapshots: " + ex.getMessage());
            return;
        }

        modelSnapshot.clear();
        // FIRST AND ALWAYS. A list of names alone has no way to say `start
        // clean` once a row has been clicked: a mouse can select a row and
        // cannot unselect one.
        modelSnapshot.addElement(STR_SNAPSHOT_NONE);
        for (String strName : lstName) {
            modelSnapshot.addElement(strName);
        }
        lstSnapshot.setSelectedValue(strWas == null ? STR_SNAPSHOT_NONE : strWas,
                true);
    }


    /**
     * The right-click menu on the snapshot list.
     *
     * A menu rather than a Delete button, for the reason the list has no Load
     * button: the row is the subject, so the action belongs on the row. A
     * button beside `Save snapshot` would act on whatever happened to be
     * selected, which is also what a start runs on, and one misread click
     * would remove the state a developer built the snapshot to keep.
     *
     * Both mouse handlers call this because the popup trigger is press on
     * some platforms and release on others.
     *
     * @param evt the mouse event that may be a popup trigger
     */
    private void maybePopup(MouseEvent evt) {
        if (!evt.isPopupTrigger())
            return;

        int idxRow = lstSnapshot.locationToIndex(evt.getPoint());
        // locationToIndex answers with the NEAREST row for a click below the
        // last one, so the bounds are checked rather than trusted.
        if (idxRow < 0 || !lstSnapshot.getCellBounds(idxRow, idxRow).contains(evt.getPoint()))
            return;

        lstSnapshot.setSelectedIndex(idxRow);
        String strName = lstSnapshot.getSelectedValue();
        if (strName == null || STR_SNAPSHOT_NONE.equals(strName))
            return;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem itemDelete = new JMenuItem("Delete snapshot");
        itemDelete.addActionListener(evtItem -> deleteRequested(strName));
        menu.add(itemDelete);
        menu.show(lstSnapshot, evt.getX(), evt.getY());
    }


    /**
     * @param strName the snapshot to remove, as it is displayed
     */
    private void deleteRequested(String strName) {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null)
            return;

        if (!Modals.isConfirmed(this,
                "Delete the snapshot " + strName + "?\nThe cluster it holds goes with it and"
                        + " there is no undo.",
                "Delete snapshot", JOptionPane.WARNING_MESSAGE))
            return;

        try {
            snapshotsHere.delete(strName);
        }
        catch (RuntimeException ex) {
            warn(ex.getMessage());
            return;
        }

        milestone("Deleted snapshot " + strName);
        // BEFORE the refresh, which restores whatever was selected when it
        // ran. The row that was selected is the one that has just gone.
        lstSnapshot.setSelectedValue(STR_SNAPSHOT_NONE, true);
        refreshSnapshots();
    }


    private void warn(String strMessage) {
        Modals.warn(this, strMessage);
    }


    /**
     * @param state the state the window has reached
     */
    private void applyControls(SandboxService.State state) {
        LifecycleControls controls = LifecycleControls.of(state);
        // AND NOT WHILE THE FIXTURE IS BEING BUILT. The table answers for the
        // STACK; the fixture is a second thing in flight that the stack knows
        // nothing about, and STOPPED is exactly the state it runs in.
        btnStart.setEnabled(controls.flagStart() && !flagAviationBusy && !flagPharmaBusy);
        btnStop.setEnabled(controls.flagStop());
        btnReset.setEnabled(controls.flagReset());
        // AND NEITHER IS Save snapshot - operator instruction. The fixture
        // is still being built or its scripts are still running, so a
        // snapshot taken now holds half of it.
        btnSave.setEnabled(controls.flagSave() && !flagAviationBusy && !flagPharmaBusy);
        lstSnapshot.setEnabled(controls.flagSnapshots());
        form.setInputEnabled(controls.flagInput());
        paneDars.setInputEnabled(controls.flagInput());
        // WHAT A RUNNING STACK IS BUILT ON is locked on the Settings tab by
        // the same rule that locks the form - his instruction, 2026-10-04.
        paneSettings.setLocked(!controls.flagInput());
        paneSettings.setAviationEnabled(!flagAviationBusy && flagAviationOffered());
        // AND PHARMA'S, by the same rule: off while one is being built, and
        // off once the story is on a ledger - a second run would allocate
        // parties that are already there.
        paneSettings.setPharmaEnabled(!flagPharmaBusy && !pharma.hasRun());
    }


    /**
     * The pane and the sink, in that order and from one place.
     *
     * Every state change goes through here rather than to
     * {@link #setStateChip} directly, so a sink cannot miss one because a
     * later increment added a sixth call site and updated only the chip.
     *
     * @param state what the window has become
     */
    private void enterState(SandboxService.State state) {
        this.stateNow = state;
        cntUsersEpoch.incrementAndGet();
        setStateChip(state);
        ledgerWatch.follow(state == SandboxService.State.RUNNING ? mapLedgerDatabases() : Map.of());
        Consumer<SandboxService.State> sinkHere = sinkState;
        if (sinkHere != null)
            sinkHere.accept(state);
    }


    /**
     * @param sinkNew told every state this window moves through, from the
     *        event dispatch thread; null for none
     */
    public void useStateSink(Consumer<SandboxService.State> sinkNew) {
        this.sinkState = sinkNew;
    }


    /**
     * @return the form, so a harness can set what a developer would type
     */
    public SandboxForm form() {
        return form;
    }


    /**
     * @return whether the window would accept a Start right now
     */
    public boolean canStart() {
        return btnStart.isEnabled();
    }


    /**
     * Presses Start exactly as a developer would, listener and all. Does
     * nothing when the button is disabled, which is what a disabled button
     * means.
     */
    public void pressStart() {
        btnStart.doClick();
    }


    public void pressStop() {
        btnStop.doClick();
    }


    /**
     * The Reset button, for a harness. Does nothing when Reset is not offered,
     * which is every state but RUNNING.
     */
    public void pressReset() {
        btnReset.doClick();
    }


    /**
     * What the controls ARE, as against what {@link LifecycleControls} says
     * they should be. The two being separate is the whole value: a harness can
     * assert the table against the widgets rather than against itself.
     *
     * @return the enabled state of every lifecycle control, read off screen
     */
    LifecycleControls controlsNow() {
        return new LifecycleControls(btnStart.isEnabled(), btnStop.isEnabled(),
                btnReset.isEnabled(), btnSave.isEnabled(), lstSnapshot.isEnabled(),
                form.isInputEnabled());
    }


    /**
     * Puts the list back on the empty ledger.
     *
     * A harness has to do this before every start: a snapshot left selected from
     * a hand-driven run would be restored over the wipe, and one taken under a
     * different Canton would refuse the start with a modal dialog that nothing
     * is going to dismiss.
     */
    public void clearSnapshotSelection() {
        lstSnapshot.setSelectedValue(STR_SNAPSHOT_NONE, true);
    }


    /**
     * @return the DARs tab, so a harness can re-read the directory it staged a
     *         file into
     */
    public DarsPane paneDars() {
        return paneDars;
    }


    /**
     * Arms the next start on a named snapshot, which is what clicking a row
     * does.
     *
     * @param strName the snapshot to run the next start on
     * @return whether the list holds it; false means the next start would run
     *         on an empty ledger, and a caller that ignored that would report
     *         a restore that never happened
     */
    public boolean selectSnapshot(String strName) {
        if (strName == null)
            return false;
        lstSnapshot.setSelectedValue(strName, true);
        return strName.equals(lstSnapshot.getSelectedValue());
    }


    /**
     * Save WITHOUT the dialog, for a harness. Stops the stack exactly as the
     * button does - see {@link #saveRequested} for why the stop is not
     * optional.
     *
     * The name is FIXED for a harness, so a second run finds the first one's
     * snapshot in the way. `flagReplace` deletes it rather than refusing,
     * which is the one behaviour that differs from the button: a developer
     * being told the name is taken can pick another, and a harness cannot.
     *
     * @param strName what to call it
     * @param flagReplace whether to delete a snapshot of that name first
     * @return null when the save was started, else what refused it; the stack
     *         reaching STOPPED is what says it finished
     */
    public String saveSnapshotNamed(String strName, boolean flagReplace) {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null)
            return "no Canton is selected, so there is no version to take a snapshot for";

        String strTrim = strName == null ? "" : strName.trim();
        try {
            Snapshots.requireName(strTrim);
            if (snapshotsHere.lstNames().contains(strTrim)) {
                if (!flagReplace)
                    return "there is already a snapshot called " + strTrim;
                snapshotsHere.delete(strTrim);
            }
        }
        catch (RuntimeException ex) {
            return String.valueOf(ex.getMessage());
        }

        Properties props;
        try {
            props = propsOfRun();
        }
        catch (IllegalArgumentException ex) {
            return String.valueOf(ex.getMessage());
        }
        Path dirCluster = dirClusterNow();
        if (dirCluster == null)
            return "there is no cluster to copy";
        stopThen(() -> saveNow(strTrim, props, dirCluster));
        return null;
    }


    /**
     * The window size, clamped: a forced scale on a small screen must still
     * produce a window that fits on it.
     */
    /**
     * @return the snapshots the selected version has, or empty when there is
     *         no selection and when the directory cannot be read - neither is
     *         a reason to throw at a caller that only wants to list them
     */
    public List<String> lstSnapshotNames() {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null)
            return List.of();
        try {
            return snapshotsHere.lstNames();
        }
        catch (RuntimeException ex) {
            return List.of();
        }
    }


    /**
     * Delete without the dialog, for a harness.
     *
     * NO CONFIRMATION, which is the whole difference from the right-click
     * menu. A harness has nobody to answer one, and the menu keeps its dialog
     * because a person clicking a row is the case the dialog exists for.
     *
     * @param strName the snapshot to remove, as it is displayed
     * @return null when it went, else why it did not
     */
    public String deleteSnapshotNamed(String strName) {
        Snapshots snapshotsHere = snapshots;
        if (snapshotsHere == null)
            return "no Canton is selected, so there is no version to delete from";

        try {
            snapshotsHere.delete(strName == null ? "" : strName.trim());
        }
        catch (RuntimeException ex) {
            return String.valueOf(ex.getMessage());
        }

        milestone("Deleted snapshot " + strName);
        lstSnapshot.setSelectedValue(STR_SNAPSHOT_NONE, true);
        refreshSnapshots();
        return null;
    }


    private void sizeToScreen() {
        int nFormWidth = form.getPreferredSize().width + GuiTheme.scale(4 * GuiTheme.N_PAD_CARD);
        // THE SAME SIZE AS THE WORKBENCH - operator instruction, 2026-09-09.
        // The two windows are opened side by side and one of them being
        // narrower for no reason a reader can see is untidy. The minimum is
        // still taken off the form, so a narrow screen does not clip it.
        Dimension dimWanted = new Dimension(GuiTheme.scale(N_WIDTH_WORKBENCH),
                GuiTheme.scale(N_HEIGHT_WORKBENCH));
        Dimension dimMin = new Dimension(nFormWidth + GuiTheme.scale(N_WIDTH_RIGHT_MIN),
                GuiTheme.scale(N_HEIGHT_MIN));

        Rectangle rectMax = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        dimWanted = new Dimension(Math.min(dimWanted.width, rectMax.width),
                Math.min(dimWanted.height, rectMax.height));
        // AND THE MINIMUM NEVER EXCEEDS THE SIZE. `setMinimumSize` is
        // enforced by the peer, so a minimum computed off a form wider than
        // 1400 silently kept the old width and the requested size did nothing
        // at all.
        dimMin = new Dimension(Math.min(Math.min(dimMin.width, rectMax.width),
                dimWanted.width),
                Math.min(Math.min(dimMin.height, rectMax.height), dimWanted.height));

        setMinimumSize(dimMin);
        setSize(dimWanted);
    }


    /**
     * One milestone from OUTSIDE this window.
     *
     * The pane is the record of what a run did, and a caller driving the
     * window through its own buttons is doing part of that run. Without this
     * its steps reach the console only, and the pane then reads as a start
     * that happened for no reason.
     *
     * HOPS ONTO THE EVENT DISPATCH THREAD, because every other write to the
     * pane is already on it and the callers are worker threads.
     *
     * @param strLine what to put in the pane and the footer
     */
    public void note(String strLine) {
        SwingUtilities.invokeLater(() -> milestone(strLine));
    }

}
