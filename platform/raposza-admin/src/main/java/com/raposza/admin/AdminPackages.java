// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.admin;

import com.raposza.wire.DescriptorSet;
import com.raposza.wire.DynamicCall;
import com.raposza.wire.ServerReflection;
import com.raposza.wire.WireException;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import io.grpc.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAR and package handles on the Canton admin API.
 *
 * <h2>Nothing here is written down per version</h2>
 *
 * The service is `PackageService` under
 * `com.digitalasset.canton.participant.admin.v0` on the 2.x line and under
 * `com.digitalasset.canton.admin.participant.v30` on the 3.x one - same verbs,
 * renamed package. So the service is found BY SHAPE: the one that declares
 * both {@link #STR_LIST} and {@link #STR_REMOVE_DAR}. The Ledger API has a
 * `PackageService` too and it removes nothing, so the removal method is what
 * tells the two apart rather than the package name.
 *
 * Request FIELDS are read off the descriptor as well. Every method here takes
 * one identifier, and the rule is that its request carries exactly one string
 * field; anything else is refused by name rather than guessed at. Responses
 * are returned as name-to-text maps, so a version that carries an extra column
 * shows it instead of losing it.
 *
 * <h2>No credentials</h2>
 *
 * The admin API is plaintext and unauthenticated on a local sandbox. Anything
 * else belongs on the {@link Channel} the caller supplies.
 *
 * Author Claude/bentzn
 */
public final class AdminPackages {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPackages.class);

    public static final String STR_LIST = "ListDars";

    public static final String STR_REMOVE_DAR = "RemoveDar";

    public static final String STR_REMOVE_PACKAGE = "RemovePackage";

    public static final String STR_VET = "VetDar";

    public static final String STR_UNVET = "UnvetDar";

    /** What an upload method's name begins with, on both generations. */
    public static final String STR_UPLOAD_PREFIX = "Upload";

    /** What a DAR-contents method's name ends with, where there is one. */
    public static final String STR_CONTENTS_SUFFIX = "DarContents";

    /** The field a DAR's main package id comes back in. */
    public static final String STR_FIELD_MAIN = "main";

    /** The simple name both generations give the service. */
    private static final String STR_SERVICE = "PackageService";

    private final DescriptorSet set;

    private final DynamicCall call;

    private final String strService;


    /**
     * @param channel an open channel to the participant's ADMIN api
     * @param setNew the descriptors that channel advertised
     */
    public AdminPackages(Channel channel, DescriptorSet setNew) {
        this.set = setNew;
        this.call = DynamicCall.of(channel, setNew);
        this.strService = strServiceIn(setNew);
    }


    /**
     * @param channel an open channel to the participant's ADMIN api
     * @return a facade over whatever that participant advertises
     */
    public static AdminPackages of(Channel channel) {
        return new AdminPackages(channel, ServerReflection.of(channel).descriptorSet());
    }


    /**
     * @return the fully qualified service this participant carries
     */
    public String strService() {
        return strService;
    }


    /**
     * @return the descriptors this facade was built over
     */
    public DescriptorSet set() {
        return set;
    }


    /**
     * @param setHere the descriptors a participant advertised
     * @return the admin package service's full name
     * @throws WireException when no advertised service has the shape
     */
    public static String strServiceIn(DescriptorSet setHere) {
        List<String> lstSeen = new ArrayList<>();
        for (Descriptors.ServiceDescriptor service : setHere.lstService()) {
            if (!STR_SERVICE.equals(service.getName()))
                continue;
            lstSeen.add(service.getFullName());
            if (service.findMethodByName(STR_LIST) != null
                    && service.findMethodByName(STR_REMOVE_DAR) != null)
                return service.getFullName();
        }
        throw new WireException("no advertised " + STR_SERVICE + " declares both "
                + STR_LIST + " and " + STR_REMOVE_DAR + "; saw " + lstSeen);
    }


    /**
     * @return one map per DAR, field name to text, exactly as the participant
     *         described them
     */
    public List<Map<String, String>> lstDar() {
        Message msgResponse = call.call(strFullMethod(STR_LIST));
        Descriptors.FieldDescriptor field = fieldSoleRepeatedMessage(
                msgResponse.getDescriptorForType());

        List<Map<String, String>> lstOut = new ArrayList<>();
        int cntItem = msgResponse.getRepeatedFieldCount(field);
        for (int idxItem = 0; idxItem < cntItem; idxItem++) {
            lstOut.add(mapOf((Message) msgResponse.getRepeatedField(field, idxItem)));
        }
        return lstOut;
    }


    /**
     * @param strDarId the DAR's hash, as {@link #lstDar} reported it
     */
    public void removeDar(String strDarId) {
        call.call(strFullMethod(STR_REMOVE_DAR), msgFor(set, strFullMethod(STR_REMOVE_DAR),
                strDarId));
    }


    /**
     * @param strPackageId the package's hash
     */
    public void removePackage(String strPackageId) {
        call.call(strFullMethod(STR_REMOVE_PACKAGE),
                msgFor(set, strFullMethod(STR_REMOVE_PACKAGE), strPackageId));
    }


    /**
     * @param strDarId the DAR's hash
     */
    public void vetDar(String strDarId) {
        call.call(strFullMethod(STR_VET), msgFor(set, strFullMethod(STR_VET), strDarId));
    }


    /**
     * @param strDarId the DAR's hash
     */
    public void unvetDar(String strDarId) {
        call.call(strFullMethod(STR_UNVET), msgFor(set, strFullMethod(STR_UNVET), strDarId));
    }


    /**
     * Sends a DAR to the running participant.
     *
     * @param arrDar the file's bytes
     * @param strName what the participant should call it
     * @return the response, field name to text - the DAR's own identifier is in
     *         there under whatever the version calls it
     */
    public Map<String, String> uploadDar(byte[] arrDar, String strName) {
        if (arrDar == null || arrDar.length == 0)
            throw new IllegalArgumentException("a DAR is required");

        String strFullMethod = strFullMethod(strMethodUpload());
        LOG.info("uploading {} bytes as {} through {}", arrDar.length, strName, strFullMethod);
        Message msgResponse = call.call(strFullMethod,
                msgUpload(set, strFullMethod, arrDar, strName));
        return mapOf(msgResponse);
    }


    /**
     * @return the upload method this participant declares
     */
    public String strMethodUpload() {
        return strMethodUploadIn(set.serviceFor(strService));
    }


    /**
     * The upload method, BY SHAPE rather than by name.
     *
     * The name is `UploadDar` on both lines, but what it accepts is not the
     * same message: 3.x takes a repeated `{bytes, description}` with
     * `vet_all_packages` and `synchronize_vetting` beside it, 2.x takes one
     * `bytes` and a `filename`. `ValidateDar` sits next to it on 3.x with a
     * payload of the same shape, so a prefix alone would not do - the method
     * chosen is the first one whose name begins with `Upload` AND whose
     * request carries a DAR payload.
     *
     * @param service the admin package service
     * @return the method's simple name
     * @throws WireException when no method has the shape
     */
    public static String strMethodUploadIn(Descriptors.ServiceDescriptor service) {
        List<String> lstSeen = new ArrayList<>();
        for (Descriptors.MethodDescriptor method : service.getMethods()) {
            lstSeen.add(method.getName());
            if (!method.getName().startsWith(STR_UPLOAD_PREFIX))
                continue;
            Descriptors.Descriptor descRequest = method.getInputType();
            if (fieldBytes(descRequest) != null || fieldRepeatedWithBytes(descRequest) != null)
                return method.getName();
        }
        throw new WireException(service.getFullName() + " declares no method beginning with "
                + STR_UPLOAD_PREFIX + " whose request carries a DAR payload; it has " + lstSeen);
    }


    /**
     * The upload request, built from whatever shape the version declares.
     *
     * @param setHere the descriptors
     * @param strFullMethod the upload method
     * @param arrDar the DAR's bytes
     * @param strName the file name, put in the first string field the payload
     *        offers - `description` on 3.x, `filename` on 2.x
     * @return the message
     * @throws WireException when the request carries no DAR payload
     */
    public static DynamicMessage msgUpload(DescriptorSet setHere, String strFullMethod,
            byte[] arrDar, String strName) {
        Descriptors.Descriptor descRequest = setHere.methodFor(strFullMethod).getInputType();
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descRequest);

        Descriptors.FieldDescriptor fieldFlat = fieldBytes(descRequest);
        if (fieldFlat != null) {
            builder.setField(fieldFlat, ByteString.copyFrom(arrDar));
            Descriptors.FieldDescriptor fieldName = fieldFirstString(descRequest);
            if (fieldName != null)
                builder.setField(fieldName, strName);
        }
        else {
            Descriptors.FieldDescriptor fieldList = fieldRepeatedWithBytes(descRequest);
            if (fieldList == null) {
                throw new WireException(descRequest.getFullName() + " carries no DAR payload:"
                        + " no bytes field, and no repeated message holding one; it has "
                        + DynamicCall.lstFieldName(descRequest));
            }
            Descriptors.Descriptor descItem = fieldList.getMessageType();
            DynamicMessage.Builder builderItem = DynamicMessage.newBuilder(descItem);
            builderItem.setField(fieldBytes(descItem), ByteString.copyFrom(arrDar));
            Descriptors.FieldDescriptor fieldName = fieldFirstString(descItem);
            if (fieldName != null)
                builderItem.setField(fieldName, strName);
            builder.addRepeatedField(fieldList, builderItem.build());
        }

        setVetting(builder);
        return builder.build();
    }


    /**
     * Turns on every vetting flag the request declares.
     *
     * An upload that is not vetted is a DAR the participant will not execute,
     * which is not what a person pressing Upload means, and the vendor's own
     * example sends both flags true. They are set BY NAME because the two are
     * not the same field on the two lines, and a version that spells the
     * choice some other way leaves the DAR unvetted - which the Vetted column
     * then shows, and one press of Vet fixes.
     *
     * @param builder the request being built
     * @return the flags that were set, for the log
     */
    public static List<String> setVetting(DynamicMessage.Builder builder) {
        List<String> lstSet = new ArrayList<>();
        for (Descriptors.FieldDescriptor field : builder.getDescriptorForType().getFields()) {
            if (field.isRepeated() || field.getType() != Descriptors.FieldDescriptor.Type.BOOL)
                continue;
            String strName = field.getName();
            if (!strName.contains("vet") && !strName.contains("synchronize"))
                continue;
            builder.setField(field, Boolean.TRUE);
            lstSet.add(strName);
        }
        LOG.info("upload flags set: {}", lstSet);
        return lstSet;
    }


    /**
     * The main package id of each DAR, which is the thing vetting is a
     * property of.
     *
     * Measured off the 2.10.4 proto. The 3.x description carries
     * the id already; the 2.x one carries `hash` and `name` and nothing else,
     * so every 2.x row read `?`. `ListDarContents` answers it - `dar_id` in,
     * `main` out - and it is asked ONLY for a DAR whose description does not
     * carry the id, so a 3.x refresh makes no extra call.
     *
     * @param lstDar the DARs, as {@link #lstDar} described them
     * @return one id per DAR in the same order, null where there is none
     */
    public List<String> lstPackageIdMain(List<Map<String, String>> lstDar) {
        String strMethod = strMethodContents();
        List<String> lstOut = new ArrayList<>();
        for (Map<String, String> mapDar : lstDar) {
            String strFound = strPackageIdIn(mapDar);
            if (strFound == null && strMethod != null)
                strFound = strPackageIdMain(strMethod, strFirstValue(mapDar));
            lstOut.add(strFound);
        }
        return lstOut;
    }


    /**
     * @param mapDar one DAR as the participant described it
     * @return its package id where the description carries one, else null
     */
    public static String strPackageIdIn(Map<String, String> mapDar) {
        String strFound = null;
        for (Map.Entry<String, String> entry : mapDar.entrySet()) {
            if (entry.getKey().contains("package_id")) {
                strFound = entry.getValue();
                break;
            }
        }
        if (strFound == null)
            strFound = mapDar.get(STR_FIELD_MAIN);
        return strFound == null || strFound.isBlank() ? null : strFound;
    }


    /**
     * @param mapDar one DAR as the participant described it
     * @return its first field, which is the identifier every other call takes
     */
    public static String strFirstValue(Map<String, String> mapDar) {
        for (Map.Entry<String, String> entry : mapDar.entrySet()) {
            return entry.getValue();
        }
        return null;
    }


    /**
     * @return the DAR-contents method this participant declares, or null
     */
    public String strMethodContents() {
        for (Descriptors.MethodDescriptor method : set.serviceFor(strService).getMethods()) {
            if (!method.getName().endsWith(STR_CONTENTS_SUFFIX))
                continue;
            if (fieldFirstString(method.getInputType()) == null)
                continue;
            Descriptors.FieldDescriptor fieldMain =
                    method.getOutputType().findFieldByName(STR_FIELD_MAIN);
            if (fieldMain != null && !fieldMain.isRepeated()
                    && fieldMain.getType() == Descriptors.FieldDescriptor.Type.STRING)
                return method.getName();
        }
        return null;
    }


    /**
     * @param strMethod the DAR-contents method
     * @param strDarId the DAR's own identifier
     * @return its main package id, or null when the call did not answer with
     *         one - a column is not worth a failed refresh
     */
    public String strPackageIdMain(String strMethod, String strDarId) {
        if (strDarId == null || strDarId.isBlank())
            return null;

        String strFullMethod = strFullMethod(strMethod);
        try {
            Message msgResponse = call.call(strFullMethod,
                    msgFor(set, strFullMethod, strDarId));
            String strMain = DynamicCall.strField(msgResponse, STR_FIELD_MAIN);
            return strMain.isBlank() ? null : strMain;
        }
        catch (RuntimeException ex) {
            LOG.info("{} did not answer for {}: {}", strMethod, strDarId, ex.getMessage());
            return null;
        }
    }


    /**
     * @param strMethod the simple RPC name
     * @return it, qualified by this participant's service
     */
    public String strFullMethod(String strMethod) {
        return strService + "/" + strMethod;
    }


    /**
     * The request for a method that takes one identifier.
     *
     * @param setHere the descriptors
     * @param strFullMethod the method
     * @param strId what to put in its single string field
     * @return the message
     */
    public static DynamicMessage msgFor(DescriptorSet setHere, String strFullMethod,
            String strId) {
        Descriptors.Descriptor descRequest = setHere.methodFor(strFullMethod).getInputType();
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descRequest);
        builder.setField(fieldSoleString(descRequest), strId);
        return builder.build();
    }


    /**
     * The field an identifier goes in: THE FIRST STRING FIELD, by declaration
     * order.
     *
     * Measured on 3.5.12. `RemoveDarRequest` carries one string and
     * the rule was originally "exactly one" - but `UnvetDarRequest` carries
     * `main_package_id` and then `synchronizer_id`, so "exactly one" refused a
     * request whose subject was never in doubt. The subject is declared first
     * and the qualifiers follow it, on every message in this service.
     *
     * The qualifiers are LEFT UNSET, which is what a single-synchronizer
     * sandbox wants: an empty `synchronizer_id` is "wherever this participant
     * is connected". A participant on several would need it, and that is a
     * question for the day the Sandbox can start one.
     *
     * @param descType a request type
     * @return its first string field
     * @throws WireException when it has none
     */
    public static Descriptors.FieldDescriptor fieldSoleString(Descriptors.Descriptor descType) {
        Descriptors.FieldDescriptor field = fieldFirstString(descType);
        if (field == null) {
            throw new WireException(descType.getFullName() + " has no string field to put an"
                    + " id in; it has " + DynamicCall.lstFieldName(descType));
        }
        return field;
    }


    /**
     * @param descType any message type
     * @return its first singular string field, or null when it has none
     */
    public static Descriptors.FieldDescriptor fieldFirstString(Descriptors.Descriptor descType) {
        for (Descriptors.FieldDescriptor field : descType.getFields()) {
            if (!field.isRepeated()
                    && field.getType() == Descriptors.FieldDescriptor.Type.STRING)
                return field;
        }
        return null;
    }


    /**
     * @param descType any message type
     * @return its first singular bytes field, or null when it has none
     */
    public static Descriptors.FieldDescriptor fieldBytes(Descriptors.Descriptor descType) {
        for (Descriptors.FieldDescriptor field : descType.getFields()) {
            if (!field.isRepeated()
                    && field.getType() == Descriptors.FieldDescriptor.Type.BYTES)
                return field;
        }
        return null;
    }


    /**
     * @param descType any message type
     * @return its first repeated message field whose element carries bytes, or
     *         null when it has none
     */
    public static Descriptors.FieldDescriptor fieldRepeatedWithBytes(
            Descriptors.Descriptor descType) {
        for (Descriptors.FieldDescriptor field : descType.getFields()) {
            if (!field.isRepeated()
                    || field.getType() != Descriptors.FieldDescriptor.Type.MESSAGE)
                continue;
            if (fieldBytes(field.getMessageType()) != null)
                return field;
        }
        return null;
    }


    /**
     * @param descType a response type
     * @return its one repeated message field
     * @throws WireException when it has none or more than one
     */
    public static Descriptors.FieldDescriptor fieldSoleRepeatedMessage(
            Descriptors.Descriptor descType) {
        Descriptors.FieldDescriptor fieldFound = null;
        for (Descriptors.FieldDescriptor field : descType.getFields()) {
            if (!field.isRepeated()
                    || field.getType() != Descriptors.FieldDescriptor.Type.MESSAGE)
                continue;
            if (fieldFound != null) {
                throw new WireException(descType.getFullName() + " has more than one repeated"
                        + " message field: " + DynamicCall.lstFieldName(descType));
            }
            fieldFound = field;
        }
        if (fieldFound == null) {
            throw new WireException(descType.getFullName() + " carries no list; it has "
                    + DynamicCall.lstFieldName(descType));
        }
        return fieldFound;
    }


    /**
     * @param msg one element of a response list
     * @return every field of it as text, in declaration order
     */
    public static Map<String, String> mapOf(Message msg) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        for (Descriptors.FieldDescriptor field : msg.getDescriptorForType().getFields()) {
            mapOut.put(field.getName(), String.valueOf(msg.getField(field)));
        }
        return mapOut;
    }
}
