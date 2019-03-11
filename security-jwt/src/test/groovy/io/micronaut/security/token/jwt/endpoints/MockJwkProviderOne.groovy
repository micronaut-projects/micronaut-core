package io.micronaut.security.token.jwt.endpoints

import com.nimbusds.jose.jwk.JWK
import io.micronaut.context.annotation.Requires

import javax.inject.Singleton

@Requires(property = "spec.name", value = "keyscontrollerwithmultiplekeys")
@Singleton
class MockJwkProviderOne implements JwkProvider {
    @Override
    JWK retrieveJsonWebKey() {
        JWK.parse("{\"kty\": \"EC\", \"crv\": \"P-256\", \"kid\": \"1\", \"x\": \"28iajOQ4y2CNpLhJdhRQzgLsJGkbIWKMMDYTI5suAmE\", \"y\": \"X6lIFqP4eMeW3V5-8qDrSqEiWa3gLdw-KHhFvEQ82CY\" }")
    }
}
