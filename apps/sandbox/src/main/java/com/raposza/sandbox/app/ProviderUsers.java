// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WHO CAN SIGN IN AT THE WINDOW'S PROVIDER, as the provider itself says - his
 * instruction, 2026-10-04: "The OIDC Users tab should obviously list all users
 * that can work with that server."
 *
 * The tab used to show only the users the window had registered: LocalNetND's
 * role users, or the single participant's ledger users. A user added at the
 * provider's own UI - the USDCx demo's five - could sign in and was not on it.
 * The provider's store is `users.json` beside its keys, and only the provider
 * reads it; `GET /api/ui/users` is how anyone else asks. It is guarded by the
 * admin credential, sent as HTTP Basic like every other call the window makes
 * there - {@link MintRegistration#strAuthBasic}. It returns names and standard
 * claims, never a password.
 *
 * <h2>The passwords are read from the server's own file - his instruction,
 * 2026-10-04</h2>
 *
 * "OF COURSE it should show the usernames and the passwords. The tab belongs
 * to the freakin' server!!! ... this is not supposed to be secure at all. But
 * it has to work exactly as any other secure OIDC server." The API keeps its
 * rule; the window reads `users.json`, the file the server names in the same
 * answer, which it can because it started that server on this machine. The
 * format is `OidcUsers`'s: a user is a password string, or an object whose
 * `password` is one.
 *
 * Author Claude/bentzn
 */
public final class ProviderUsers {

    private static final Duration DUR_REQUEST = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The User-Agent of this read. The window reads every 3 s, and the OIDC
     * pane's access log leaves these lines out by it - `OidcAccess` - so that
     * log keeps saying who signed in rather than that the window looked.
     */
    public static final String STR_AGENT = "raposza-sandbox-users";


    /**
     * @param strFile where the provider keeps them, as it reports it
     * @param lstName every user, in the provider's order
     */
    public record Listing(String strFile, List<String> lstName) {
    }


    private ProviderUsers() {
    }


    /**
     * The provider answers `{"file": ..., "names": [...], "claims": {...}}`.
     *
     * @param strBody what `GET /api/ui/users` returned
     * @return the file and the names
     * @throws IOException when it is not that shape
     */
    public static Listing listingOf(String strBody) throws IOException {
        JsonNode nodeRoot = MAPPER.readTree(strBody);
        JsonNode nodeNames = nodeRoot == null ? null : nodeRoot.get("names");
        if (nodeNames == null || !nodeNames.isArray())
            throw new IOException("the provider's user list has no names: " + strBody);
        List<String> lstOut = new ArrayList<>();
        for (JsonNode nodeName : nodeNames) {
            String strName = nodeName.asText("");
            if (!strName.isEmpty() && !lstOut.contains(strName))
                lstOut.add(strName);
        }
        return new Listing(nodeRoot.path("file").asText(""), lstOut);
    }


    /**
     * @param strJson the content of `users.json`
     * @return name to password, in the file's order
     * @throws IOException when it is not that shape
     */
    public static Map<String, String> mapPasswordOf(String strJson) throws IOException {
        JsonNode nodeRoot = MAPPER.readTree(strJson);
        if (nodeRoot == null || !nodeRoot.isObject())
            throw new IOException("the user store is not a JSON object of name to password");
        Map<String, String> mapOut = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> itUser = nodeRoot.fields();
        while (itUser.hasNext()) {
            Map.Entry<String, JsonNode> entUser = itUser.next();
            JsonNode node = entUser.getValue();
            JsonNode nodePassword = node.isTextual() ? node : node.get("password");
            if (nodePassword != null && nodePassword.isTextual())
                mapOut.put(entUser.getKey(), nodePassword.asText());
        }
        return mapOut;
    }


    /**
     * @param fileStore the server's `users.json`
     * @return name to password
     * @throws IOException when the file cannot be read or is not that shape
     */
    public static Map<String, String> mapPassword(Path fileStore) throws IOException {
        return mapPasswordOf(Files.readString(fileStore, StandardCharsets.UTF_8));
    }


    /**
     * @param strUrlProvider the provider, no trailing slash
     * @return its users
     * @throws IOException when the provider refuses or cannot be reached
     * @throws InterruptedException when interrupted while waiting
     */
    public static Listing read(String strUrlProvider) throws IOException, InterruptedException {
        String strUrl = strUrlProvider + MintRegistration.STR_PATH_USERS;
        HttpClient client = HttpClient.newBuilder().connectTimeout(DUR_REQUEST).build();
        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(strUrl))
                .timeout(DUR_REQUEST)
                .header("Authorization", MintRegistration.strAuthBasic())
                .header("User-Agent", STR_AGENT)
                .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2)
            throw new IOException("GET " + strUrl + " answered " + res.statusCode() + ": "
                    + res.body());
        return listingOf(res.body());
    }

}
