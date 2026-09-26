// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The bindings of one run, and which contract ids have been consumed. Sec. 6
 * and sec. 7.
 *
 * <h2>The environment is SEEDED from a register, and sec. 6 said otherwise</h2>
 *
 * Sec. 6 says earlier runs' bindings are never carried forward. That is right
 * for a fixture run from a file and wrong for the editor, where statements are
 * run one line at a time: allocating a party and then using it are two runs,
 * and bindings that die with their run leave the second one with nothing to
 * use. So the window keeps a REGISTER and {@link #restore} puts it back before
 * a run.
 *
 * A name carried in that way is REPLACED rather than refused when a later run
 * binds it again - {@link #bind}. Single assignment still holds where the
 * document argues for it, which is within ONE script, and there the parser
 * refuses the duplicate with the whole text in view.
 *
 * THE CONSUMED SET IS NOT CARRIED. A contract archived by an earlier run
 * leaves its register entry looking fresh, and the participant is what says
 * otherwise - which is the pre-existing answer for a contract archived by
 * anything other than this run.
 *
 * <h2>Staleness is tracked by CONTRACT ID</h2>
 *
 * Sec. 7, and the reason is in its example: two bindings can hold the same
 * contract id, and archiving through one stales both. So the consumed ids are
 * the state and staleness is asked of a binding rather than stored on it. Three
 * things follow for free, and all three are cases a boolean per binding gets
 * wrong:
 *
 * <ul>
 * <li>a binding made BEFORE the exercise stales;</li>
 * <li>a binding made AFTER it, from the same id, is stale immediately rather
 *     than looking fresh;</li>
 * <li>a contract id nested inside a structured choice result stales its
 *     binding, which sec. 7 requires in as many words.</li>
 * </ul>
 *
 * <h2>What this class does not do</h2>
 *
 * It does not decide whether a choice consumes - that comes from the registry
 * before submitting, which is what lets a stale use fail LOCALLY with a real
 * reason instead of as a puzzling rejection later. It is told.
 *
 * Author Claude/bentzn
 */
public final class Env {

    private static final int CNT_NAME_LISTED = 8;

    private final Map<String, Binding> mapBinding = new LinkedHashMap<>();
    private final Set<String> setConsumed = new LinkedHashSet<>();

    /** Names put back by {@link #restore}, which a run may bind over. */
    private final Set<String> setRestored = new LinkedHashSet<>();


    /**
     * @param binding the binding to add
     * @param strSource the statement, for the message
     * @return the binding, so a caller can bind and use in one expression
     * @throws CaqlException when the name is already bound BY THIS RUN - sec. 6,
     *         refused and not shadowed. A name restored from the register is
     *         replaced instead
     */
    public Binding bind(Binding binding, String strSource) {
        Binding bindingOld = mapBinding.get(binding.name());
        // A name that came in from the REGISTER is bound over rather than
        // refused. The parser has already refused a duplicate within one
        // script, with the whole text in view, so the only way to reach a
        // taken name here is a line the operator has run before - and running
        // it again is how a value gets rebound.
        if (bindingOld != null && setRestored.remove(binding.name()))
            bindingOld = null;
        if (bindingOld != null) {
            throw new CaqlException(binding.numLine(), strSource, "'" + binding.name()
                    + "' is already bound, on line " + bindingOld.numLine()
                    + "; rebinding is refused rather than shadowed");
        }

        mapBinding.put(binding.name(), binding);
        return binding;
    }


    /**
     * @param name the binding name
     * @return the binding, empty when nothing is bound to it
     */
    public Optional<Binding> lookup(String name) {
        return Optional.ofNullable(mapBinding.get(name));
    }


    /**
     * The lookup a statement makes, which fails rather than returning empty.
     *
     * Both failures name what the operator can do about them: an unknown name
     * lists what IS bound, because the overwhelmingly likely cause is a typo
     * against a name that is right there; a stale one names the contract id and
     * the line that consumed it.
     *
     * @param name the binding name
     * @param numLine the line using it
     * @param strSource the statement using it
     * @return the binding
     * @throws CaqlException when unknown or stale
     */
    public Binding require(String name, int numLine, String strSource) {
        Binding binding = mapBinding.get(name);
        if (binding == null) {
            throw new CaqlException(numLine, strSource, "'$" + name + "' is not bound"
                    + (mapBinding.isEmpty() ? "; nothing is bound yet"
                            : "; bound so far: " + names()));
        }

        Optional<String> optStale = staleFor(binding);
        if (optStale.isPresent()) {
            throw new CaqlException(numLine, strSource, "'$" + name + "' is stale: contract "
                    + optStale.get() + " has been archived by an earlier statement in this run");
        }
        return binding;
    }


    /**
     * Records that a contract id no longer exists.
     *
     * @param idContract the archived contract id
     */
    public void consume(String idContract) {
        if (idContract != null && !idContract.isBlank())
            setConsumed.add(idContract);
    }


    /**
     * @param binding the binding to test
     * @return the consumed contract id it holds, empty when it holds none
     */
    public Optional<String> staleFor(Binding binding) {
        if (setConsumed.isEmpty())
            return Optional.empty();

        List<String> lstId = new ArrayList<>();
        collectContractIds(binding.value(), lstId);
        for (String idContract : lstId) {
            if (setConsumed.contains(idContract))
                return Optional.of(idContract);
        }
        return Optional.empty();
    }


    /**
     * Puts a binding back without counting it as this run's.
     *
     * @param binding what an earlier run left in the register, ignored when
     *        null
     */
    public void restore(Binding binding) {
        if (binding == null)
            return;

        mapBinding.put(binding.name(), binding);
        setRestored.add(binding.name());
    }


    /** @return every binding, in the order the script made them */
    public List<Binding> all() {
        return List.copyOf(mapBinding.values());
    }


    /** @return the contract ids consumed in this run, in the order they went */
    public Set<String> consumed() {
        return Set.copyOf(setConsumed);
    }


    /**
     * Every contract id anywhere inside a value.
     *
     * Structured results are searched rather than skipped because sec. 7 says
     * so: a choice can return a record carrying a contract id, and archiving
     * that contract has to stale the binding that holds it even though the
     * language cannot read into the record to find it.
     */
    private static void collectContractIds(DamlValue value, List<String> lstOut) {
        if (value == null)
            return;

        switch (value) {
            case DamlValue.ContractRef val -> lstOut.add(val.idContract());

            case DamlValue.Rec val -> {
                for (DamlValue.Rec.Field fld : val.lstField()) {
                    collectContractIds(fld.value(), lstOut);
                }
            }

            case DamlValue.Variant val -> collectContractIds(val.value(), lstOut);

            case DamlValue.Lst val -> {
                for (DamlValue elem : val.lstElem()) {
                    collectContractIds(elem, lstOut);
                }
            }

            case DamlValue.Opt val -> collectContractIds(val.value(), lstOut);

            case DamlValue.TextMap val -> {
                for (DamlValue.TextMap.Entry entry : val.lstEntry()) {
                    collectContractIds(entry.value(), lstOut);
                }
            }

            // Keys too: a GenMap keyed by contract id is legal, and a key that
            // has been archived is as stale as a value that has.
            case DamlValue.GenMap val -> {
                for (DamlValue.GenMap.Entry entry : val.lstEntry()) {
                    collectContractIds(entry.key(), lstOut);
                    collectContractIds(entry.value(), lstOut);
                }
            }

            case DamlValue.Unit ignored -> {
                // no contract id inside a scalar
            }
            case DamlValue.Bool ignored -> {
            }
            case DamlValue.Int64 ignored -> {
            }
            case DamlValue.Decimal ignored -> {
            }
            case DamlValue.Text ignored -> {
            }
            case DamlValue.TimeVal ignored -> {
            }
            case DamlValue.DateVal ignored -> {
            }
            case DamlValue.Party ignored -> {
            }
            case DamlValue.EnumVal ignored -> {
            }
        }
    }


    private String names() {
        List<String> lstName = new ArrayList<>();
        for (String name : mapBinding.keySet()) {
            if (lstName.size() == CNT_NAME_LISTED) {
                lstName.add("... and " + (mapBinding.size() - CNT_NAME_LISTED) + " more");
                break;
            }
            lstName.add("$" + name);
        }
        return String.join(", ", lstName);
    }


    /**
     * A binding for a value the participant generated, which is the only kind
     * the language makes - grammar sec. 6.
     *
     * @param name the name
     * @param value the value
     * @param type its type
     * @param numLine the statement's line
     * @param idUpdate the update that produced it, or null when there was no
     *                 submission
     * @return the binding, not yet added
     */
    public static Binding of(String name, DamlValue value, DamlType type, int numLine,
            String idUpdate) {
        return new Binding(name, value, type, numLine, Optional.ofNullable(idUpdate));
    }

}
