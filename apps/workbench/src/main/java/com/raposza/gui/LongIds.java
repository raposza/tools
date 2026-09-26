// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Shortened ids, put back the way they were.
 *
 * <h2>Why an editor may hold a short id at all</h2>
 *
 * {@link ShortIds} exists because a party id is longer than most panes and a
 * contract id is longer still, and the CaQL editor is a pane like any other:
 * a script seeded with five party ids in full is a wall nobody reads. So the
 * editor holds the short form and this puts the long form back on the way to
 * the participant, which never sees an abbreviation.
 *
 * <h2>It resolves against WHAT WAS READ, and refuses rather than guesses</h2>
 *
 * The candidates are the ids the window is holding - the parties and the
 * contracts of the last read. A short id matching none of them cannot be
 * expanded, and one matching several is worse than one matching none: sending
 * either would put a wrong contract under a right-looking statement. Both
 * refuse the run, naming the token, before anything is parsed or sent.
 *
 * An id typed in FULL passes through untouched, because nothing here fires
 * without the marker.
 *
 * Author Claude/bentzn
 */
public final class LongIds {

    /**
     * @param strText the script, with the short forms expanded, null when one
     *        could not be
     * @param strProblem why not, null when the expansion succeeded
     */
    public record Result(String strText, String strProblem) {}


    private LongIds() {
    }


    /**
     * @param strText the script as the editor holds it
     * @param lstKnown every id the window has read, in any order
     * @return the script with each short id replaced, or the reason it is not
     */
    public static Result expand(String strText, List<String> lstKnown) {
        List<String> lstProblem = new ArrayList<>();
        String strOut = strWalk(strText, lstKnown, lstProblem);
        if (lstProblem.isEmpty())
            return new Result(strOut, null);

        StringBuilder bufWhy = new StringBuilder("The script holds shortened ids that this"
                + " window cannot put back, so nothing was sent:\n");
        for (String strWhy : lstProblem) {
            bufWhy.append('\n').append(strWhy);
        }
        // NOT `press Reload`. What the window holds is decided by the user
        // in `work as`: a narrow one cannot list the parties, and reloading
        // as that user reads the same nothing again.
        return new Result(null, bufWhy.append("\n\nType the id in full, or work as a user"
                + " that can read it.").toString());
    }


    /**
     * The same expansion, for the SCREEN rather than for the participant.
     *
     * A DISPLAY TOGGLE MUST NOT BE ALL OR NOTHING. `expand` refuses the whole
     * script when one token cannot be placed, which is right on the way to a
     * ledger and wrong on the way to the editor: unticking `Short ids` with
     * one unknown id in the script used to leave every OTHER id abbreviated,
     * so the box appeared to do nothing at all.
     *
     * What cannot be placed is left exactly as it was written, and the run
     * path still refuses it and names it.
     *
     * @param strText the script as the editor holds it
     * @param lstKnown every id the window has read, in any order
     * @return the script with every short id that could be placed put back
     */
    public static String textBest(String strText, List<String> lstKnown) {
        return strWalk(strText, lstKnown, new ArrayList<>());
    }


    /**
     * @param strText the script as the editor holds it
     * @param lstKnown the candidates
     * @param lstProblem where a token that could not be placed is recorded
     * @return the script, with every token that could be placed replaced
     */
    private static String strWalk(String strText, List<String> lstKnown,
            List<String> lstProblem) {
        if (strText == null || strText.indexOf(ShortIds.STR_CUT) < 0)
            return strText;

        StringBuilder buf = new StringBuilder(strText.length());

        int idxAt = 0;
        while (idxAt < strText.length()) {
            int idxCut = strText.indexOf(ShortIds.STR_CUT, idxAt);
            if (idxCut < 0) {
                buf.append(strText, idxAt, strText.length());
                break;
            }

            int idxFrom = idxCut;
            while (idxFrom > idxAt && isTokenChar(strText.charAt(idxFrom - 1))) {
                idxFrom--;
            }
            int idxTo = idxCut + ShortIds.STR_CUT.length();
            while (idxTo < strText.length() && isTokenChar(strText.charAt(idxTo))) {
                idxTo++;
            }

            String strToken = strText.substring(idxFrom, idxTo);
            String strFound = strMatch(strToken.substring(0, idxCut - idxFrom),
                    strText.substring(idxCut + ShortIds.STR_CUT.length(), idxTo), lstKnown,
                    lstProblem, strToken);

            buf.append(strText, idxAt, idxFrom).append(strFound == null ? strToken : strFound);
            idxAt = idxTo;
        }

        return buf.toString();
    }


    /**
     * @param strHead what stood before the marker
     * @param strTail what stood after it
     * @param lstKnown the candidates
     * @param lstProblem where a refusal is recorded
     * @param strToken the token as written, for the message
     * @return the one id that fits, or null when none or several do
     */
    private static String strMatch(String strHead, String strTail, List<String> lstKnown,
            List<String> lstProblem, String strToken) {
        List<String> lstHit = new ArrayList<>();
        if (lstKnown != null) {
            for (String strKnown : lstKnown) {
                if (strKnown != null && strKnown.startsWith(strHead) && strKnown.endsWith(strTail)
                        && !lstHit.contains(strKnown)) {
                    lstHit.add(strKnown);
                }
            }
        }

        if (lstHit.size() == 1)
            return lstHit.get(0);

        lstProblem.add(lstHit.isEmpty()
                ? "  " + strToken + "  matches nothing this window has read"
                : "  " + strToken + "  matches " + lstHit.size() + " ids that have been read");
        return null;
    }


    /**
     * What may sit either side of the marker and still be part of one id: a
     * party is `hint::fingerprint` and a hint carries letters, digits and
     * punctuation, so the boundary is drawn at the characters that cannot be
     * inside an id at all - quotes, whitespace, commas, braces.
     *
     * @param chAt a character
     * @return true when it belongs to the token
     */
    private static boolean isTokenChar(char chAt) {
        return !Character.isWhitespace(chAt) && chAt != '"' && chAt != '\''
                && chAt != ',' && chAt != '{' && chAt != '}' && chAt != '[' && chAt != ']'
                && chAt != '(' && chAt != ')';
    }

}
