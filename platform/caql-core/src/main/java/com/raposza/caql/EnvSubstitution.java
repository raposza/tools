// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.api.Substitution_i;
import com.raposza.api.TypeRegistry_i;

import java.util.List;
import java.util.Optional;

/**
 * The run's bindings, as the coercer wants to see them.
 *
 * Made per statement rather than per run, because the line and the source are
 * what a failure has to carry and they change on every statement. That is also
 * why staleness THROWS here instead of returning empty: the coercer would
 * report "not bound", which is a different thing from "bound and archived", and
 * only this class knows which one happened.
 *
 * Author Claude/bentzn
 */
public final class EnvSubstitution implements Substitution_i {

    private final Env env;
    private final TypeRegistry_i registry;
    private final int numLine;
    private final String strSource;


    /**
     * @param env the run's environment
     * @param numLine the line of the statement being read
     * @param strSource that statement, for the message
     */
    public EnvSubstitution(Env env, int numLine, String strSource) {
        this(env, null, numLine, strSource);
    }


    /**
     * @param env the run's environment
     * @param registry the run's registry snapshot, needed only to resolve the
     *                 DECLARED TYPE of a projected field. Null disables
     *                 projection rather than resolving it untyped: a value
     *                 handed back with a guessed type would be checked against
     *                 the wrong thing at the point of use
     * @param numLine the line of the statement being read
     * @param strSource that statement, for the message
     */
    public EnvSubstitution(Env env, TypeRegistry_i registry, int numLine, String strSource) {
        if (env == null)
            throw new IllegalArgumentException("an environment is required");

        this.env = env;
        this.registry = registry;
        this.numLine = numLine;
        this.strSource = strSource;
    }


    /**
     * The name may carry a dotted projection - {@code r.owner.party} - because
     * the coercer hands over whatever followed the dollar and the split is a
     * question about bindings, which is here.
     *
     * STALENESS IS CHECKED ON THE BINDING, not on the projection. A record
     * holding an archived contract id stales the whole binding by sec. 7, so
     * reaching into it for a different field is refused too. That is the
     * intended reading: the statement that produced it no longer describes the
     * ledger.
     *
     * {@code $p.asText} - a party read as its id, typed TEXT - needs no
     * registry, so it is answered without one; see {@link FieldPath}.
     */
    @Override
    public Optional<Bound> lookup(String name) {
        int numDot = name.indexOf('.');
        String nameRoot = numDot < 0 ? name : name.substring(0, numDot);

        Optional<Binding> optBinding = env.lookup(nameRoot);
        if (optBinding.isEmpty())
            return Optional.empty();

        Binding binding = optBinding.get();
        Optional<String> optStale = env.staleFor(binding);
        if (optStale.isPresent()) {
            throw new CaqlException(numLine, strSource, "'$" + nameRoot + "' is stale: contract "
                    + optStale.get() + " has been archived by an earlier statement in this run");
        }

        if (numDot < 0)
            return Optional.of(new Bound(binding.value(), binding.type()));

        String strPath = name.substring(numDot + 1);
        if (registry == null && !FieldPath.STR_AS_TEXT.equals(strPath)) {
            throw new CaqlException(numLine, strSource, "'$" + name + "' projects into a record"
                    + " and no registry is available to say what type that field is");
        }

        List<String> lstField = List.of(strPath.split("\\."));
        return Optional.of(FieldPath.project(binding, lstField, registry, "$" + name, numLine,
                strSource));
    }

}
