// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.lifecycle.StackService_i;
import com.raposza.sandbox.app.SandboxService;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One lamp per component, in the order a start brings them up.
 *
 * The lamp reads the SERVICE rather than remembering what a button did. A
 * participant that exits on its own after a green start leaves the window's
 * own idea of the state untouched, and the lamp is the only thing that would
 * ever say so.
 *
 * <h2>Five colours, and the fifth is the one a reader was missing</h2>
 *
 * <pre>
 * green  UP        serving
 * amber  STARTING  its own start has begun and it is not serving yet
 * blue   PENDING   this start WILL bring it up, and its turn has not come
 * red    DOWN      it is supposed to be serving and it is not
 * grey   OFF       not part of this stack at all
 * </pre>
 *
 * Blue and grey were one colour until now, and they are opposite statements:
 * a JSON API waiting for the participant it fronts looked exactly like a PQS
 * nobody asked for. Grey is also LIGHTER than the chip grey - a dot carries no
 * text on it, so it does not need the contrast a filled pill does, and at the
 * chip's value it read as a component that was merely dark.
 *
 * Author Claude/bentzn
 */
public final class LampBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Lamp lampPostgres = new Lamp("PostgreSQL");

    private final Lamp lampParticipant = new Lamp("Participant");

    private final Lamp lampJsonApi = new Lamp("JSON API");

    private final Lamp lampPqs = new Lamp("PQS");

    /**
     * THE OPENID PROVIDER, right of PQS - his instruction, 2026-10-04. NOT A
     * COMPONENT OF ANY STACK: it runs whether a stack does or not, so the
     * window sets it and {@link #setStack} and {@link #setService} leave it
     * alone. Green when the built-in provider runs, or when an external one
     * has answered its discovery document.
     */
    private final Lamp lampOidc = new Lamp("OIDC");

    /**
     * WHAT THE WINDOW SAYS PQS IS DOING, or null to ask the stack.
     *
     * On the LocalNetND topology the stack does not own scribe and cannot -
     * `LocalNetPqs` says why - so `healthOf("pqs")` there answers OFF for a
     * process that may well be up. The window that started it overrides this
     * one lamp, and {@link #setStack} then leaves it alone.
     */
    private transient SandboxService.Health healthPqs;

    /**
     * NOT A PROCESS, which is why it is not in {@link #setService}.
     *
     * The other four watch something that is running and answer a health probe.
     * This one says whether the test data asked for at open has reached the
     * ledger, which no probe can see - so the window sets it directly and
     * `setService` leaves it alone. It is hidden unless a fixture is being
     * built, because a lamp for something nobody asked for is a dot that can
     * only ever be grey.
     */
    private final Lamp lampFixture = new Lamp("Test fixture");

    /** Told which lamp was clicked, by its own label. */
    private transient Consumer<String> sinkClick;

    /**
     * WHAT THE LAMPS ARE SHOWING, when a caller has said so.
     *
     * The health lookup goes through THESE names and not through the
     * stack's own list: a bar showing three of a stack's four components
     * would otherwise read the fourth one's health into the third lamp.
     */
    private transient Map<String, String> mapLamp = new LinkedHashMap<>();


    public LampBar() {
        super(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(18), GuiTheme.scale(2)));
        setOpaque(false);
        add(lampPostgres);
        add(lampParticipant);
        add(lampJsonApi);
        add(lampPqs);
        add(lampOidc);
        add(lampFixture);
        lampFixture.setVisible(false);
        setService(null);
    }


    /**
     * Makes the two lamps that have something to show clickable.
     *
     * NOT ALL FOUR. PostgreSQL is embedded and was never a command line, and
     * the JSON API on 3.x is the participant under a second name. A cursor that
     * promised a dialog on those two would be promising an empty one.
     *
     * @param sinkNew told the label of the lamp that was clicked, on the event
     *        dispatch thread; null to make them inert again
     */
    public void useClickSink(Consumer<String> sinkNew) {
        this.sinkClick = sinkNew;
        lampParticipant.setClickable(sinkNew != null);
        lampPqs.setClickable(sinkNew != null);
    }


    /**
     * ON 3.x THERE IS NO SUCH COMPONENT.
     *
     * `SandboxService.healthJsonApi` falls through to the participant's own
     * health on 3.x, because the participant IS the HTTP Ledger API there.
     * That is the right answer and it makes the lamp a second name for the
     * lamp beside it - two dots that can never disagree, one of which is
     * lying about what it watches.
     *
     * @param flagShown whether this stack has a JSON API process of its own
     */
    public void setJsonApiShown(boolean flagShown) {
        if (lampJsonApi.isVisible() == flagShown)
            return;
        lampJsonApi.setVisible(flagShown);
        revalidate();
        repaint();
    }


    /**
     * @param flagShown whether this window is creating test data at all
     */
    public void setFixtureShown(boolean flagShown) {
        if (lampFixture.isVisible() == flagShown)
            return;
        lampFixture.setVisible(flagShown);
        // THE PARENT TOO. This bar sits inside a card whose layout has already
        // been computed; revalidating only this panel leaves the row the width
        // it had when the lamp was not in it.
        revalidate();
        if (getParent() != null)
            getParent().revalidate();
        repaint();
    }


    /**
     * @param health what the fixture is doing; never null
     */
    public void setFixtureHealth(SandboxService.Health health) {
        if (health == null)
            throw new IllegalArgumentException("a health is required");
        lampFixture.setHealth(health);
    }


    /**
     * GREEN ONLY ONCE IT HOLDS EVERYBODY - his instruction, 2026-10-04: "It
     * should only turn green when OIDC has all information necessary."
     *
     * A running process is not that. Spring Boot answers seconds after its
     * process starts, and a stack writes its users after that. Until the
     * Users tab has read the server's list with no start between it and its
     * users, the lamp is amber. An external server is somebody else's: the
     * window writes nobody there, so found is all it can be.
     *
     * @param flagProviderUp built in: the process runs; external: discovered
     * @param flagExternal whether the server is somebody else's
     * @param flagUsersComplete whether the Users tab read every user
     * @return what the OIDC lamp shows
     */
    static SandboxService.Health healthOidc(boolean flagProviderUp, boolean flagExternal,
            boolean flagUsersComplete) {
        if (!flagProviderUp)
            return flagExternal ? SandboxService.Health.DOWN : SandboxService.Health.OFF;
        if (flagExternal || flagUsersComplete)
            return SandboxService.Health.UP;
        return SandboxService.Health.STARTING;
    }


    /**
     * @param health what the OpenID Provider is doing; never null
     */
    public void setOidcHealth(SandboxService.Health health) {
        if (health == null)
            throw new IllegalArgumentException("a health is required");
        lampOidc.setHealth(health);
    }


    /**
     * @param health what PQS is doing when the window owns the process, or
     *        null to go back to reading it off the stack
     */
    public void setPqsHealth(SandboxService.Health health) {
        this.healthPqs = health;
        if (health != null)
            lampPqs.setHealth(health);
    }


    /**
     * NOT `update`. `JComponent` already declares `update(Graphics)`, and a
     * call passing null matches BOTH - which is an ambiguity javac reports at
     * the call site rather than at the declaration, so the name looked fine
     * until something called it with a null service.
     *
     * @param service the running service, or null when nothing was started
     */
    /**
     * THE FOUR ARE WHATEVER THE STACK CALLS THEM - D-790.
     *
     * A Sandbox names postgres, participant, json-api and pqs; LocalNetND
     * names postgres, splice, web and - listed but never started - pqs. The
     * bar holds four lamps either way and is told which four it is showing.
     *
     * THE KEY IS NOT THE LABEL. The health lookup goes through the component
     * NAME the stack knows, while the lamp shows a name a person reads, so
     * `pqs` can appear as `PQS` without the bar asking the stack about a
     * component called `PQS`.
     *
     * @param mapLampNew component name to the label shown for it, in lamp
     *        order; fewer than four hides the lamps it does not reach
     */
    public void setLamps(Map<String, String> mapLampNew) {
        this.mapLamp = new LinkedHashMap<>(mapLampNew);
        Lamp[] arrLamp = { lampPostgres, lampParticipant, lampJsonApi, lampPqs };
        int idxLamp = 0;
        for (Map.Entry<String, String> entry : mapLamp.entrySet()) {
            if (idxLamp >= arrLamp.length)
                break;
            arrLamp[idxLamp].setVisible(true);
            arrLamp[idxLamp].setLabel(entry.getValue());
            idxLamp++;
        }
        while (idxLamp < arrLamp.length) {
            arrLamp[idxLamp].setVisible(false);
            idxLamp++;
        }
        revalidate();
        repaint();
    }


    /**
     * The same reading as {@link #setService}, taken through the interface so
     * a LocalNet stack answers it too.
     *
     * @param stack the running stack, or null when nothing was started
     */
    public void setStack(StackService_i stack) {
        if (stack == null) {
            setService(null);
            return;
        }
        Lamp[] arrLamp = { lampPostgres, lampParticipant, lampJsonApi, lampPqs };
        if (mapLamp.isEmpty()) {
            List<String> lstName = stack.lstComponent();
            for (int idx = 0; idx < arrLamp.length && idx < lstName.size(); idx++) {
                arrLamp[idx].setHealth(stack.healthOf(lstName.get(idx)));
            }
            return;
        }
        int idxLamp = 0;
        for (String strKey : mapLamp.keySet()) {
            if (idxLamp >= arrLamp.length)
                break;
            // THE OVERRIDE WINS. A lamp the window drives is not re-read off a
            // stack that does not own the process behind it.
            if (!(arrLamp[idxLamp] == lampPqs && healthPqs != null))
                arrLamp[idxLamp].setHealth(stack.healthOf(strKey));
            idxLamp++;
        }
    }


    /**
     * @param service the running service, or null when nothing was started
     */
    public void setService(SandboxService service) {
        if (service == null) {
            lampPostgres.setHealth(SandboxService.Health.OFF);
            lampParticipant.setHealth(SandboxService.Health.OFF);
            lampJsonApi.setHealth(SandboxService.Health.OFF);
            lampPqs.setHealth(SandboxService.Health.OFF);
            return;
        }
        lampPostgres.setHealth(service.healthPostgres());
        lampParticipant.setHealth(service.healthParticipant());
        lampJsonApi.setHealth(service.healthJsonApi());
        lampPqs.setHealth(service.healthPqs());
    }


    /**
     * @param health what the component is doing
     * @return its colour, from the one place that decides it - `StackLamps`
     *         asks the same question and a second answer here would be a
     *         disagreement nobody reports, because both bars look deliberate
     */
    private static Color colFor(SandboxService.Health health) {
        return HealthColour.of(health);
    }


    /**
     * A dot and a name.
     *
     * AN INNER CLASS, not a static one: a click has to reach the sink the
     * enclosing bar holds, and a static nested class would need its own copy of
     * it - four copies of one field, three of which nothing ever sets.
     */
    private final class Lamp extends JPanel {

        private static final long serialVersionUID = 1L;

        private final Dot dot = new Dot();

        private final JLabel lblName;

        private boolean flagClickable;


        Lamp(String strName) {
            super(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(6), 0));
            setOpaque(false);
            lblName = new JLabel(strName);
            add(dot);
            add(lblName);

            // ON THE PANEL, not on the label: the dot and the six pixels
            // between them are part of what a reader is aiming at.
            MouseAdapter adapter = new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent evt) {
                    clicked(lblName.getText());
                }
            };
            addMouseListener(adapter);
            dot.addMouseListener(adapter);
            lblName.addMouseListener(adapter);
        }


        void setLabel(String strNew) {
            lblName.setText(strNew);
        }


        void setClickable(boolean flagOn) {
            setCursor(Cursor.getPredefinedCursor(
                    flagOn ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            this.flagClickable = flagOn;
        }


        void setHealth(SandboxService.Health health) {
            dot.setColour(colFor(health));
            // The tooltip carries the word, because a colour alone is not a
            // reading anyone can quote back.
            setToolTipText(lblName.getText() + ": " + health.name()
                    + (flagClickable ? " - click for what it was started with" : ""));
        }
    }


    /**
     * @param strLabel which lamp was hit
     */
    private void clicked(String strLabel) {
        Consumer<String> sinkHere = sinkClick;
        if (sinkHere != null)
            sinkHere.accept(strLabel);
    }



}
