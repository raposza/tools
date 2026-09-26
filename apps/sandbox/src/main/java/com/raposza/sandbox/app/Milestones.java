// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.sandbox.PhaseSink_i;
import com.raposza.sandbox.StackComponent;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * The half-dozen lines a reader actually watches during a start.
 *
 * <pre>
 * Starting postgres
 * Started postgres in 3 seconds
 * Starting participant
 * Started participant in 45 seconds
 * </pre>
 *
 * <h2>Why the wording is here and not in the window</h2>
 *
 * A {@link PhaseSink_i} carries a component name and a {@link Duration}, which
 * is the fact. Turning that into a sentence is one decision - what to call each
 * component, and how to say a length of time - and it is made once here rather
 * than at each surface that might want to print it. The terminal and the window
 * then cannot disagree about how long a participant took.
 *
 * <h2>The numbers</h2>
 *
 * WHOLE SECONDS, and `< 1 second` below that. A start measured at 21 s does
 * not become more useful reported as 21.4, and the decimal read worst on the
 * components it was meant for: it gave a PostgreSQL that came up more or less
 * instantly the two significant figures of a measurement.
 *
 * Author Claude/bentzn
 */
public final class Milestones implements PhaseSink_i {

    /** What anything faster than a second reads as. */
    private static final String STR_UNDER_A_SECOND = "< 1 second";

    /** What a line begins with while the thing it names is still coming up. */
    public static final String STR_PREFIX_STARTING = "Starting ";

    /** And what a wait begins with. */
    public static final String STR_PREFIX_WAITING = "Waiting ";

    /** And what something being put onto the ledger begins with. */
    public static final String STR_PREFIX_CREATING = "Creating ";

    private final Consumer<String> outLine;


    /**
     * @param outLineNew where a milestone goes; never null
     */
    public Milestones(Consumer<String> outLineNew) {
        if (outLineNew == null)
            throw new IllegalArgumentException("a sink is required");
        this.outLine = outLineNew;
    }


    /**
     * Whether a line names something still happening, which is what a
     * surface showing progress needs to know.
     *
     * THE PREFIXES ARE THIS CLASS'S OWN WORDS, not a vendor's. Matching on
     * a string someone else writes is the trap this avoids; matching on one
     * written thirty lines above is a constant with a longer name.
     *
     * @param strLine a line this class produced
     * @return whether it is still in progress
     */
    public static boolean isPending(String strLine) {
        return strLine != null && (strLine.startsWith(STR_PREFIX_STARTING)
                || strLine.startsWith(STR_PREFIX_CREATING)
                || strLine.startsWith(STR_PREFIX_WAITING));
    }


    @Override
    public void starting(String strComponent) {
        outLine.accept(STR_PREFIX_STARTING + strNameOf(strComponent));
    }


    @Override
    public void started(String strComponent, Duration duration) {
        outLine.accept("Started " + strNameOf(strComponent) + " in " + strTimeOf(duration));
    }


    /**
     * @param strLine anything else worth one line
     */
    public void note(String strLine) {
        outLine.accept(strLine);
    }


    /**
     * THE STACK IS DOWN. Reserved for a start that died, which is the only
     * thing here that ends the run - the window stays open and the operator can
     * try again, but nothing is serving.
     *
     * A script that failed, a profile that would not save and a snapshot
     * directory that would not list are NOT this. They leave a working stack
     * behind them, and labelling them FATAL trains the reader to skip the word
     * on the one occasion it is true. Say what happened instead.
     *
     * @param strMessage why the start stopped
     */
    public void fatal(String strMessage) {
        outLine.accept("FATAL: " + strMessage);
    }


    /**
     * @param strComponent one of {@link StackComponent}
     * @return what to call it on screen
     */
    public static String strNameOf(String strComponent) {
        if (StackComponent.STR_PQS.equals(strComponent))
            return "PQS";
        if (StackComponent.STR_JSON_API.equals(strComponent))
            return "JSON API";
        return strComponent;
    }


    /**
     * @param duration how long it took
     * @return it, in seconds, as a reader would say it
     */
    public static String strTimeOf(Duration duration) {
        if (duration == null)
            return "no time at all";

        long cntMilli = duration.toMillis();
        if (cntMilli < 1000L)
            return STR_UNDER_A_SECOND;

        // TRUNCATED, not rounded. 1900 ms reported as `2 seconds` is a
        // number the run did not produce, and the whole point of dropping
        // the decimal was to stop implying a precision that is not there.
        long cntSecond = cntMilli / 1000L;
        return cntSecond + (cntSecond == 1L ? " second" : " seconds");
    }
}
