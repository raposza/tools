// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import com.raposza.api.model.ApiGeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Finds the translation targets on the classpath.
 *
 * only() is the method applications should call, and the reason is a measured
 * one rather than a simplification. The v1 classes come from
 * com.daml:bindings-java and the v2 classes from the Canton jar, and they
 * collide on 306 fully qualified class names. Two targets can therefore never
 * share a classpath in a running application, whatever the dependency graph
 * says. So an application has exactly one, and asking which generation it is
 * is a question about the build, not a choice to make at runtime.
 *
 * byGeneration() exists for tests and for the eventual isolated-classloader or
 * two-launcher answer to the collision, not for an application to branch on.
 *
 * Author Claude/bentzn
 */
public final class Targets {

    private Targets() {
    }


    /**
     * @return every registered target, in whatever order ServiceLoader reports
     */
    public static List<Target_i> all() {
        List<Target_i> lstTarget = new ArrayList<>();
        ServiceLoader.load(Target_i.class, Target_i.class.getClassLoader())
                .forEach(lstTarget::add);
        return List.copyOf(lstTarget);
    }


    /**
     * The one target on this classpath.
     *
     * @return it
     * @throws IllegalStateException when none is registered, or when more than
     *         one is - which for the reason above means the build is wrong and
     *         failing here is kinder than failing on whichever jar happened to
     *         win the classpath ordering
     */
    public static Target_i only() {
        List<Target_i> lstTarget = all();

        if (lstTarget.isEmpty()) {
            throw new IllegalStateException("no Target_i is registered. This application"
                    + " needs exactly one canton-target-* module on its runtime classpath");
        }
        if (lstTarget.size() > 1) {
            List<String> lstId = new ArrayList<>();
            for (Target_i target : lstTarget) {
                lstId.add(target.id());
            }
            throw new IllegalStateException("more than one Target_i is registered: "
                    + String.join(", ", lstId) + ". The Ledger API generations collide on"
                    + " 306 class names and cannot share a classpath; keep one");
        }

        return lstTarget.get(0);
    }


    /**
     * @param generation the generation wanted
     * @return the target speaking it, empty when none is registered
     */
    public static Optional<Target_i> byGeneration(ApiGeneration generation) {
        for (Target_i target : all()) {
            if (target.generation() == generation)
                return Optional.of(target);
        }
        return Optional.empty();
    }


    /**
     * @param idTarget the target id
     * @return the target, empty when not registered
     */
    public static Optional<Target_i> byId(String idTarget) {
        for (Target_i target : all()) {
            if (target.id().equals(idTarget))
                return Optional.of(target);
        }
        return Optional.empty();
    }

}
