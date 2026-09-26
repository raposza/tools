// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.digitalasset.daml.lf.data.ImmArray;
import com.digitalasset.daml.lf.data.Ref;
import com.digitalasset.daml.lf.language.Ast;

/**
 * Builds the AST fragments the mapping tests need, from Java.
 *
 * Every constructor used here is in banked javap output. The builtin type
 * constants are the one exception: they are Scala objects and their singleton
 * field is codegen rather than a signature anybody published, so they are
 * fetched REFLECTIVELY. That trades a compile-time failure this session cannot
 * see for a runtime failure that names exactly what changed.
 *
 * Author Claude/bentzn
 */
final class Lf {

    private Lf() {
    }


    /**
     * @param nameBt the builtin type object's name, e.g. "BTParty"
     * @return the singleton the reader uses for it
     */
    static Ast.BuiltinType bt(String nameBt) {
        String nameClass = "com.digitalasset.daml.lf.language.Ast$" + nameBt + "$";
        try {
            Class<?> cls = Class.forName(nameClass);
            return (Ast.BuiltinType) cls.getField("MODULE$").get(null);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("no builtin type singleton " + nameClass, ex);
        }
    }


    /**
     * @param nameBt the builtin type object's name
     * @return it, as a type
     */
    static Ast.Type prim(String nameBt) {
        return new Ast.TBuiltin(bt(nameBt));
    }


    /**
     * @param strDotted a dotted name, e.g. "Shape.Circle"
     * @return the same as the AST holds it
     */
    static Ref.DottedName dotted(String strDotted) {
        String[] arrSegment = strDotted.split("[.]");
        Object[] arrBoxed = new Object[arrSegment.length];
        System.arraycopy(arrSegment, 0, arrBoxed, 0, arrSegment.length);
        ImmArray<String> arrSeg = ImmArray.unsafeFromArray(arrBoxed);
        return new Ref.DottedName(arrSeg);
    }


    /**
     * @param idPackage package id
     * @param nameModule dotted module name
     * @param nameEntity dotted entity name
     * @return the reference the AST would carry
     */
    static Ref.FullReference<String> ref(String idPackage, String nameModule, String nameEntity) {
        Ref.QualifiedName nameQual =
                new Ref.QualifiedName(dotted(nameModule), dotted(nameEntity));
        return new Ref.FullReference<>(idPackage, nameQual);
    }


    /**
     * @param idPackage package id
     * @param nameModule dotted module name
     * @param nameEntity dotted entity name
     * @return a reference to that type
     */
    static Ast.Type con(String idPackage, String nameModule, String nameEntity) {
        return new Ast.TTyCon(ref(idPackage, nameModule, nameEntity));
    }


    /**
     * @param typeFun the head
     * @param arrArg arguments, applied left to right
     * @return the application spine
     */
    static Ast.Type app(Ast.Type typeFun, Ast.Type... arrArg) {
        Ast.Type typeOut = typeFun;
        for (Ast.Type typeArg : arrArg) {
            typeOut = new Ast.TApp(typeOut, typeArg);
        }
        return typeOut;
    }

}
