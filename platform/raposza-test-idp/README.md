<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-test-idp

Local test identity provider.

Must be structurally incapable of reaching a production configuration.

## What it serves

```
GET  /.well-known/openid-configuration   issuer, token_endpoint, jwks_uri
GET  /jwks.json                          the PUBLIC half of the key
POST /token                              client_credentials, and nothing else
```

The key is `JwksMaterial` from `raposza-auth` - the same type the sandbox
already generates for a participant's `auth-services` block, so the participant
and the clients minting against it share one key by construction rather than by
configuration.

```java
JwksMaterial material = JwksMaterial.generate("raposza-sandbox");

try (TestIdp idp = new TestIdp(material)
        .register(IdpClient.ofAudience("raposza-pqs", secret, "raposza-pqs",
                AuthOverlay.audienceForParticipant("sandbox")))
        .start()) {

    // the participant reads its keys here
    AuthOverlay.ofJwksAudience(idp.strUrlJwks(), audience);

    // scribe posts its credentials here
    idp.strUrlToken();
}
```

## Why it exists rather than a staged Dex or Hydra

Both are single static binaries and both would fit a runtime made of ordinary
OS processes - which is exactly what this project stages Canton and scribe as.
Build won on ONE criterion, the release one: installation from documented
instructions succeeding on a clean machine. Hydra needs its own database and a
client registration; Dex needs a staged binary and a configuration file. Either
is one more artefact to obtain before the sandbox works, in a product whose
premise is that it comes up without one. An endpoint over keys this project
already generates needs nothing staged and adds no dependency: the HTTP surface
is `com.sun.net.httpserver` from the JDK and Nimbus already does the crypto.

## What makes it unable to reach production

By construction, not by warning:

* it binds the LOOPBACK address and no constructor takes a bind address;
* it speaks plain HTTP and cannot be given a certificate, so a client that
  requires TLS cannot use it at all;
* clients are registered in the process that starts it - no registration
  endpoint, no persistence, nothing survives the run;
* `client_credentials` is the only grant, and a requested scope is IGNORED: what
  a token carries is fixed at registration, so the endpoint cannot be talked
  into minting something it was not configured for.

## What it does not do

No user store, no consent, no authorization code, no refresh tokens, no
revocation, no rate limiting, no TLS. It is named for what it is.
