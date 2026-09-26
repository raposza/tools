// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;
import com.raposza.api.model.TemplateInfo;
import java.util.List;
import java.util.Optional;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.Resolved;
import com.raposza.api.model.UserInfo;

/**
 * What the result pane shows when a navigator node is clicked.
 *
 * Contracts and parties go through ResolvedText, so a contract reached by
 * clicking it in the tree renders identically to the same contract reached by
 * pasting its id. Two renderings of one object drift, and the first symptom is
 * an operator who trusts one pane and not the other.
 *
 * Users and templates have no Resolved variant to borrow, so they are rendered
 * here. The template text says what it knows and marks what it does not: with
 * no type registry there is no signature, only the field labels the ledger
 * returned on the contracts that were read.
 *
 * Author Claude/bentzn
 */
public final class NavText {

    private final ResolvedText text = new ResolvedText();


    /**
     * @param item the selected node
     * @return text for the result pane
     */
    public String text(NavItem item) {
        return text(item, null);
    }


    /**
     * @param item the selected node
     * @param registry the decoded packages, or null before they are read
     * @return text for the result pane
     */
    public String text(NavItem item, TypeRegistry_i registry) {
        return switch (item) {
            case NavItem.Ct val -> text.text(new Resolved.AsContract(val.contract()));

            case NavItem.Party val -> text.text(new Resolved.AsParty(val.party()));

            case NavItem.User val -> user(val.user());

            case NavItem.Template val -> template(val.group(), registry);

            case NavItem.Group val -> group(val);
        };
    }


    /**
     * @param user the user
     * @return the user, its primary party and its rights
     */
    static String user(UserInfo user) {
        StringBuilder buf = new StringBuilder(Banner.head("USER"));
        buf.append(user.idUser()).append('\n');
        buf.append("primary party  ")
                .append(user.idPartyPrimary() == null ? "(none)" : user.idPartyPrimary())
                .append('\n');
        buf.append("admin          ").append(user.flagAdmin()).append('\n');

        buf.append("\ncan act as (").append(user.lstPartyAct().size()).append(")\n");
        for (String idParty : user.lstPartyAct()) {
            buf.append("  ").append(idParty).append('\n');
        }

        buf.append("\ncan read as (").append(user.lstPartyRead().size()).append(")\n");
        for (String idParty : user.lstPartyRead()) {
            buf.append("  ").append(idParty).append('\n');
        }

        return buf.toString();
    }


    /**
     * @param group the template and the contracts read for it
     * @return the identifier, the count, and the field labels observed
     */
    static String template(LedgerSnapshot.TemplateGroup group, TypeRegistry_i registry) {
        StringBuilder buf = new StringBuilder(
                Banner.head("TEMPLATE - seen on this ledger"));
        buf.append(group.idTemplate()).append("\n\n");
        buf.append("package   ").append(group.idTemplate().idPackage()).append('\n');
        buf.append("module    ").append(group.idTemplate().nameModule()).append('\n');
        buf.append("entity    ").append(group.idTemplate().nameEntity()).append('\n');
        buf.append("contracts ").append(group.lstContract().size()).append(" read\n");

        Optional<TemplateInfo> optInfo = registry == null
                ? Optional.empty() : registry.template(group.idTemplate());
        if (optInfo.isPresent())
            return buf.append(strSignature(optInfo.get(), registry)).toString();

        buf.append("\nfields observed in the payloads that were read:\n");
        Contract contract = group.lstContract().isEmpty() ? null : group.lstContract().get(0);
        DamlValue.Rec payload = contract == null ? null : contract.payload();
        if (payload == null || payload.lstField().isEmpty()) {
            buf.append("  (none - the ledger returned an unlabelled payload)\n");
        }
        else {
            for (DamlValue.Rec.Field fld : payload.lstField()) {
                buf.append("  ").append(fld.nameField()).append("   ")
                        .append(fld.value().getClass().getSimpleName()).append('\n');
            }
        }

        buf.append("\nThese are the labels the participant returned on a contract, not a"
                + "\ntemplate signature. Choices, the key type and any field that happens"
                + "\nto be absent from the contracts read are NOT listed, because nothing"
                + "\nhere has decoded the package.\n");

        return buf.toString();
    }


    /**
     * @param group the section heading
     * @return the heading and whatever it is carrying
     */
    static String group(NavItem.Group group) {
        if (group.strTip() == null)
            return group.strLabel();
        return group.strLabel() + "\n\n" + group.strTip();
    }


    /**
     * The template as the package DECLARES it, not as a payload happened to
     * arrive.
     *
     * <h2>Why this is the better answer when it exists</h2>
     *
     * Reading field labels off a contract shows only the fields that contract
     * carried, in whatever order the participant sent them, with the runtime
     * class of a value standing in for its type. The registry has the decoded
     * package: every field with its real type whether or not any contract used
     * it, the choices, and the key. It is only absent before the packages have
     * been read, and the payload view stays for exactly that case.
     *
     * @param info the declaration
     * @param registry what resolves a choice argument type, may be null
     * @return the signature, as text
     */
    private static String strSignature(TemplateInfo info, TypeRegistry_i registry) {
        StringBuilder buf = new StringBuilder("\nfields\n");
        if (info.lstField().isEmpty()) {
            buf.append("  (none)\n");
        }
        else {
            int cntWide = 0;
            for (FieldInfo fld : info.lstField()) {
                cntWide = Math.max(cntWide, fld.nameField().length());
            }
            for (FieldInfo fld : info.lstField()) {
                buf.append("  ").append(strPad(fld.nameField(), cntWide))
                        .append("  ").append(TypeText.strOf(fld.type())).append('\n');
            }
        }

        info.typeKey().ifPresent(type ->
                buf.append("\nkey\n  ").append(TypeText.strOf(type)).append('\n'));

        buf.append("\nchoices\n");
        if (info.lstChoice().isEmpty()) {
            buf.append("  (none)\n");
        }
        else {
            for (ChoiceInfo choice : info.lstChoice()) {
                choice(buf, choice, registry);
            }
        }

        if (!info.lstInterface().isEmpty()) {
            buf.append("\nimplements\n");
            for (DataId idIface : info.lstInterface()) {
                buf.append("  ").append(idIface.shortName()).append('\n');
            }
        }

        return buf.toString();
    }


    /**
     * One choice: what it takes, what it gives back, and whether it ends the
     * contract.
     *
     * <h2>Why the arguments are worth the vertical space</h2>
     *
     * The question a template pane exists to answer is what can be done with a
     * contract and what has to be supplied to do it. A choice rendered as a
     * name and a return type answers the first half and leaves the second to a
     * rejected submission.
     *
     * The argument is a TYPE and not a field list - ChoiceInfo says so - so it
     * is resolved here through the registry rather than assumed. A choice whose
     * argument record is empty prints nothing: Archive takes nothing, and a
     * line saying so on every such choice is noise.
     *
     * CONSUMING IS STATED ONLY WHEN TRUE, the same rule the activity pane
     * follows. Nonconsuming is the common case and marking it on every line
     * hides the one that matters.
     *
     * @param buf where it goes
     * @param choice the choice
     * @param registry what resolves the argument type, may be null
     */
    private static void choice(StringBuilder buf, ChoiceInfo choice, TypeRegistry_i registry) {
        buf.append("  ").append(choice.nameChoice()).append('\n');

        List<FieldInfo> lstArg = lstArgument(choice, registry);
        if (!lstArg.isEmpty()) {
            int cntWide = 0;
            for (FieldInfo fld : lstArg) {
                cntWide = Math.max(cntWide, fld.nameField().length());
            }
            for (FieldInfo fld : lstArg) {
                buf.append("    ").append(strPad(fld.nameField(), cntWide)).append(" : ")
                        .append(TypeText.strOf(fld.type())).append('\n');
            }
        }
        else if (choice.typeArg() != null && !(choice.typeArg() instanceof DamlType.Ref)) {
            // An argument that is not a record reference has no field names to
            // lay out, and dropping it would understate what the choice takes.
            buf.append("    argument : ").append(TypeText.strOf(choice.typeArg())).append('\n');
        }

        buf.append("    \u2192 ").append(TypeText.strOf(choice.typeReturn())).append('\n');
        if (choice.flagConsuming())
            buf.append("    Consuming\n");
    }


    /**
     * The choice's argument fields, resolved.
     *
     * EMPTY IS ALSO THE ANSWER WHEN THE REGISTRY CANNOT RESOLVE IT. Both cases
     * print no argument lines, which is honest in one and silent in the other -
     * but the registry is either loaded for the whole ledger or for none of it,
     * and a per-choice apology in a pane that already says nothing was decoded
     * would be the second copy of one message.
     *
     * @param choice the choice
     * @param registry what resolves it, may be null
     * @return the fields in declaration order, empty when there are none or the
     *         argument is not a resolvable record
     */
    private static List<FieldInfo> lstArgument(ChoiceInfo choice, TypeRegistry_i registry) {
        if (registry == null || !(choice.typeArg() instanceof DamlType.Ref ref))
            return List.of();

        Optional<DataShape> optShape = registry.shape(ref.idData());
        if (optShape.isPresent() && optShape.get() instanceof DataShape.Rec rec)
            return rec.lstField();

        return List.of();
    }


    /**
     * @param strValue what to pad
     * @param cntWide how wide
     * @return it, padded with spaces
     */
    private static String strPad(String strValue, int cntWide) {
        StringBuilder buf = new StringBuilder(strValue);
        while (buf.length() < cntWide) {
            buf.append(' ');
        }
        return buf.toString();
    }

}
