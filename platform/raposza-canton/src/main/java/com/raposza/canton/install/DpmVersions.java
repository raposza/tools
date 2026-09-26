// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The SDK bundles DPM publishes, read out of what `dpm version --all -o json`
 * prints.
 *
 * <h2>This is a PARSER, not a caller</h2>
 *
 * It turns the command's output into offers and nothing else. Running the
 * command is a process concern and is not done here, so this stays answerable
 * with no network, no DPM on the machine and no registry.
 *
 * <h2>One call carries the catalogue AND what is installed</h2>
 *
 * Each entry names a version and flags `installed` and `remote`. A missing
 * `installed` is false rather than unknown - the command omits the key for a
 * bundle it has not got - so nothing here has to walk the cache to answer a
 * question the vendor's own tool already answered.
 *
 * <h2>Only three dotted numbers survive</h2>
 *
 * The listing carries release candidates beside stable bundles - `3.4.0-rc2` is
 * the first line of it on a workstation. Anything that is not
 * `major.minor.patch` is dropped, for the reason {@link DpmCatalogue} drops the
 * same shapes on the component side: a window that offered one would offer a
 * version whose meaning moves.
 *
 * <h2>A JSON reader, and the array is FOUND rather than assumed</h2>
 *
 * This read the document with a regex, one brace run at a time, which skipped
 * any entry carrying a nested object and said nothing. It is parsed now, so an
 * entry is read by its keys whatever else it carries.
 *
 * The runner merges stderr into what this is handed, so a line the command
 * writes beside the document can precede it. The array is the first `[` from
 * which a JSON array of objects parses; a bracket in a line of prose parses as
 * nothing or as something other than objects and is passed over. Output with
 * no such array is REFUSED rather than read as an empty catalogue - an empty
 * answer and a broken one must not look alike.
 *
 * Author Claude/bentzn
 */
public final class DpmVersions {

    public static final String STR_KEY_VERSION = "version";

    public static final String STR_KEY_INSTALLED = "installed";

    /** How much of an unreadable listing a refusal quotes. */
    private static final int N_CHARS_QUOTED = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();


    private DpmVersions() {
    }


    /**
     * @param strOutput what the command printed, or null
     * @return one offer per stable bundle, in the order the command listed
     *         them; never null
     * @throws IllegalArgumentException when the output is not blank and holds
     *         no JSON array of objects
     */
    public static List<SdkOffer> lstOffer(String strOutput) {
        List<SdkOffer> lstOut = new ArrayList<>();
        if (strOutput == null || strOutput.isBlank())
            return Collections.unmodifiableList(lstOut);

        for (JsonNode nodeEntry : nodeArrayIn(strOutput)) {
            JsonNode nodeVersion = nodeEntry.get(STR_KEY_VERSION);
            if (nodeVersion == null || !nodeVersion.isTextual())
                continue;

            Optional<VersionId> optVersion = VersionId.tryParse(nodeVersion.asText());
            if (optVersion.isEmpty())
                continue;

            JsonNode nodeInstalled = nodeEntry.get(STR_KEY_INSTALLED);
            boolean flagInstalled = nodeInstalled != null && nodeInstalled.isBoolean()
                    && nodeInstalled.booleanValue();
            lstOut.add(new SdkOffer(optVersion.get(), SdkChannel.DPM, flagInstalled));
        }
        return Collections.unmodifiableList(lstOut);
    }


    /**
     * @param strOutput what the command printed, not blank
     * @return the first JSON array of objects in it
     * @throws IllegalArgumentException when there is none
     */
    private static JsonNode nodeArrayIn(String strOutput) {
        int idx = strOutput.indexOf('[');
        while (idx >= 0) {
            JsonNode node = nodeParsed(strOutput.substring(idx));
            if (node != null && isArrayOfObjects(node))
                return node;
            idx = strOutput.indexOf('[', idx + 1);
        }
        String strQuoted = strOutput.strip();
        if (strQuoted.length() > N_CHARS_QUOTED)
            strQuoted = strQuoted.substring(0, N_CHARS_QUOTED) + "...";
        throw new IllegalArgumentException("no JSON array of bundles in what the listing printed: "
                + strQuoted);
    }


    /**
     * @param strFrom text starting at a candidate bracket
     * @return the value it opens, or null when it does not parse
     */
    private static JsonNode nodeParsed(String strFrom) {
        try {
            return MAPPER.readTree(strFrom);
        }
        catch (JsonProcessingException ex) {
            return null;
        }
    }


    /**
     * @param node a parsed value
     * @return whether it is an array holding objects only; an empty array is one
     */
    private static boolean isArrayOfObjects(JsonNode node) {
        if (!node.isArray())
            return false;
        for (JsonNode nodeEntry : node) {
            if (!nodeEntry.isObject())
                return false;
        }
        return true;
    }

}
