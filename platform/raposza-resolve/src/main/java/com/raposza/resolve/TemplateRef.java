// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DataId;
import com.raposza.api.model.TemplateInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a written template reference resolves to, against a registry SNAPSHOT.
 *
 * <h2>Why this is a result type rather than a lookup that throws</h2>
 *
 * The four outcomes are genuinely different work for the caller. A script stops
 * on any of them, but with different text; a pane can offer a choice on
 * Ambiguous and can only apologise on Unknown; and Unknown has to distinguish
 * "no such template" from "the registry is empty because packages have not been
 * fetched yet", which is not the operator's mistake and is fixed by reconnecting
 * rather than by editing the reference.
 *
 * An exception would flatten all four into a string.
 *
 * <h2>Against a snapshot, deliberately</h2>
 *
 * The registry is asked once and the answer holds for the statement being
 * resolved. A reference that resolved to one template at the start of a fixture
 * script and to another halfway through - because a DAR was uploaded in between
 * - would produce a run nobody can reproduce. Design sec. 6.4.
 *
 * <h2>Ambiguity is not resolved by picking</h2>
 *
 * Two versions of the same DAR on one ledger is the normal case, not the odd
 * one, and the two templates are DIFFERENT templates with different package
 * ids. Choosing the first, or the newest, would submit against a package the
 * operator did not name. The candidates are returned so the reference can be
 * made specific - which is what the package-qualified form is for.
 *
 * Author Claude/bentzn
 */
public sealed interface TemplateRef {

    /** @return the reference as it was written */
    String strRef();


    /**
     * @param strRef the reference as written
     * @param template the one template it names
     */
    record Found(String strRef, TemplateInfo template) implements TemplateRef {}


    /**
     * @param strRef the reference as written
     * @param lstCandidate every template it could name, package id included,
     *                     sorted so the message is stable between runs
     */
    record Ambiguous(String strRef, List<TemplateInfo> lstCandidate) implements TemplateRef {}


    /**
     * @param strRef the reference as written
     * @param cntKnown how many templates the registry holds. ZERO is a
     *                 different failure from a name that is simply not there:
     *                 it means no package has been read from the participant,
     *                 and the reference may well be correct
     */
    record Unknown(String strRef, int cntKnown) implements TemplateRef {}


    /**
     * @param strRef the reference as written
     * @param strWhy what is wrong with its shape
     */
    record Invalid(String strRef, String strWhy) implements TemplateRef {}


    /**
     * Resolves module:entity, entity, or package:module:entity.
     *
     * The package part is matched against whatever the registry holds rather
     * than assumed to be a hash. On 2.10 and later an identifier may carry a
     * package NAME instead - design sec. 7 - and a resolver that insisted on
     * hex would refuse a reference the participant would have accepted.
     *
     * @param registry the snapshot to resolve against
     * @param strRef the written reference
     * @return what it names
     */
    static TemplateRef resolve(TypeRegistry_i registry, String strRef) {
        if (registry == null)
            throw new IllegalArgumentException("a registry is required");
        if (strRef == null || strRef.isBlank())
            return new Invalid(strRef, "a template reference cannot be empty");

        String strTrim = strRef.trim();

        // No escape: ':' is not a regex metacharacter. The -1 limit keeps
        // trailing empties, so "Main:" is refused rather than silently becoming
        // "Main".
        String[] arrPart = strTrim.split(":", -1);

        String idPackage;
        String nameShort;

        switch (arrPart.length) {
            case 1: {
                idPackage = null;
                nameShort = arrPart[0];
                break;
            }

            case 2: {
                idPackage = null;
                nameShort = arrPart[0] + ":" + arrPart[1];
                break;
            }

            case 3: {
                idPackage = arrPart[0];
                nameShort = arrPart[1] + ":" + arrPart[2];
                break;
            }

            default:
                return new Invalid(strTrim, "expected entity, module:entity or"
                        + " package:module:entity, found " + arrPart.length + " colon-separated"
                        + " parts");
        }

        for (String strPart : arrPart) {
            if (strPart.isBlank()) {
                return new Invalid(strTrim, "a colon-separated part is empty; expected entity,"
                        + " module:entity or package:module:entity");
            }
        }

        // ONE call, and the result is not re-queried below. That is what makes
        // this a snapshot rather than a sequence of lookups that could disagree.
        List<TemplateInfo> lstMatch = new ArrayList<>(registry.templatesByName(nameShort));

        if (idPackage != null)
            lstMatch.removeIf(info -> !idPackage.equals(info.idTemplate().idPackage()));

        if (lstMatch.isEmpty())
            return new Unknown(strTrim, registry.templates().size());

        if (lstMatch.size() == 1)
            return new Found(strTrim, lstMatch.get(0));

        lstMatch.sort(Comparator.comparing(TemplateInfo::idTemplate,
                Comparator.comparing(DataId::nameModule).thenComparing(DataId::nameEntity)
                        .thenComparing(DataId::idPackage)));
        return new Ambiguous(strTrim, List.copyOf(lstMatch));
    }


    /**
     * The failure as a caller should report it. On Found this is empty, because
     * there is nothing to report.
     *
     * Written here rather than at each call site so a script transcript and a
     * pane say the same thing about the same reference.
     *
     * @return the message, empty when the reference resolved
     */
    default String strProblem() {
        return switch (this) {
            case Found ignored -> "";

            case Invalid val -> "'" + val.strRef() + "' is not a template reference: "
                    + val.strWhy();

            // The count is the whole message here. "Unknown template" against an
            // empty registry sends the operator to check their spelling when the
            // tool has simply not read the packages yet.
            case Unknown val -> val.cntKnown() == 0
                    ? "'" + val.strRef() + "' cannot be resolved: the registry holds no templates"
                            + " at all, so no package has been read from the participant"
                    : "no template matches '" + val.strRef() + "' among the " + val.cntKnown()
                            + " the registry holds";

            case Ambiguous val -> {
                List<String> lstName = new ArrayList<>(val.lstCandidate().size());
                for (TemplateInfo info : val.lstCandidate()) {
                    lstName.add(info.idTemplate().toString());
                }
                yield "'" + val.strRef() + "' matches " + val.lstCandidate().size()
                        + " templates; name one of them in full: " + String.join(", ", lstName);
            }
        };
    }

}
