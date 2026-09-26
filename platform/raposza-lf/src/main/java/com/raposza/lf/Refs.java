// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.model.DataId;
import com.digitalasset.daml.lf.data.Ref;

/**
 * Reference types of the decoded AST, turned into DataId.
 *
 * Ref.Identifier is a TYPE ALIAS, not a class - javap reports it absent while
 * Ref$Identifier$ exists. Every identifier in the AST therefore arrives as
 * Ref.FullReference&lt;String&gt;, whose pkg() is the package id and whose
 * qualifiedName() carries the module and the entity, each as a DottedName.
 *
 * An entity name may legitimately contain a dot: a variant constructor with
 * fields becomes a synthetic record named Main:Shape.Circle, measured on both
 * generations. DottedName.dottedName() renders it and NOTHING here splits on a
 * dot, which is the rule DataId already assumes.
 *
 * Author Claude/bentzn
 */
public final class Refs {

    private Refs() {
    }


    /**
     * @param ref a fully qualified reference from the decoded AST
     * @return the same identity in the semantic model
     */
    public static DataId dataId(Ref.FullReference<String> ref) {
        if (ref == null)
            throw new IllegalArgumentException("reference is required");

        Ref.QualifiedName nameQual = ref.qualifiedName();
        return new DataId(ref.pkg(), nameQual.module().dottedName(),
                nameQual.name().dottedName());
    }


    /**
     * @param name a dotted name, as a module name or an entity name
     * @return its rendered form, dots included
     */
    public static String dotted(Ref.DottedName name) {
        if (name == null)
            throw new IllegalArgumentException("name is required");

        return name.dottedName();
    }

}
