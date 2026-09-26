// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import com.raposza.api.LedgerException;
import com.raposza.api.RefProbe_i;
import com.raposza.api.Resolver_i;
import com.raposza.api.model.Resolved;
import com.raposza.spi.LedgerClient_i;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ordered probe chain.
 *
 * The rule, and the whole reason this is a module rather than a method:
 *
 *   1. Probes run in order, cheapest CONFIRMING probe first.
 *   2. A finding the ledger confirmed ends the chain immediately.
 *   3. A finding based on the string's SHAPE alone does not. It is collected
 *      and the chain continues.
 *   4. Exhausted with nothing: None. With one guess: that guess. With several:
 *      Ambiguous, and the UI asks.
 *
 * "First hit wins" - what the design originally said - is the failure this
 * avoids. Contract ids, update ids and offsets are all hex strings of similar
 * shape, so the first probe whose regex matches is not evidence of anything. A
 * wrong object presented with total confidence is worse than no object.
 *
 * A probe that THROWS is not a probe that found nothing. If every probe either
 * declined or failed, the failure is rethrown rather than reported as None: a
 * permission error on the ACS read must not surface to the operator as "no such
 * contract".
 *
 * Author Claude/bentzn
 */
public final class ChainResolver implements Resolver_i {

    private static final Logger LOG = LoggerFactory.getLogger(ChainResolver.class);

    private final List<RefProbe_i> lstProbe;


    /**
     * The standard chain for a Ledger API client.
     *
     * Deliberately incomplete. CommandIdProbe, TemplateNameProbe and
     * PackageIdProbe from design sec. 10 are absent because nothing they need
     * exists yet: v1 has no lookup by command id, the type registry lands at
     * P3, and the package service is not on LedgerClient_i. Adding them as
     * shape-matchers that cannot confirm would produce candidates on every
     * paste and make the ambiguity real rather than reported.
     *
     * @param client the ledger to confirm against
     */
    public ChainResolver(LedgerClient_i client) {
        this(List.of(new PartyProbe(client), new UpdateIdProbe(client),
                new ContractIdProbe(client), new OffsetProbe()));
    }


    /**
     * @param lstProbe probes; sorted by order() here so a caller cannot get the
     *        ordering wrong by accident
     */
    public ChainResolver(List<RefProbe_i> lstProbe) {
        List<RefProbe_i> lstSorted = new ArrayList<>(lstProbe);
        lstSorted.sort(Comparator.comparingInt(RefProbe_i::order));
        this.lstProbe = List.copyOf(lstSorted);
    }


    @Override
    public Resolved resolve(String strRef, List<String> lstPartyRead) {
        String strRefTrim = strRef == null ? "" : strRef.trim();
        if (strRefTrim.isEmpty())
            return new Resolved.None("");

        List<Resolved> lstGuess = new ArrayList<>();
        LedgerException exFirst = null;

        for (RefProbe_i probe : lstProbe) {
            if (!probe.accepts(strRefTrim))
                continue;

            Optional<RefProbe_i.Finding> found;
            try {
                found = probe.probe(strRefTrim, lstPartyRead);
            }
            catch (LedgerException ex) {
                // Kept, not swallowed. One probe failing should not stop the
                // others, but if NOTHING resolves, this is why - and reporting
                // None instead would be a lie about the ledger.
                LOG.debug("probe {} failed on '{}'", probe.getClass().getSimpleName(),
                        strRefTrim, ex);
                if (exFirst == null)
                    exFirst = ex;
                continue;
            }

            if (found.isEmpty())
                continue;

            if (found.get().flagConfirmed())
                return found.get().resolved();

            lstGuess.add(found.get().resolved());
        }

        if (!lstGuess.isEmpty())
            return lstGuess.size() == 1 ? lstGuess.get(0)
                    : new Resolved.Ambiguous(strRefTrim, List.copyOf(lstGuess));

        if (exFirst != null)
            throw exFirst;

        return new Resolved.None(strRefTrim);
    }

}
