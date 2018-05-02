package io.micronaut.security.token.jwt.bearer

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.providers.PasswordEncoder

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'delegatingauthenticationprovider')
class TestingPasswordEncoder implements PasswordEncoder {

    @Override
    String encode(String rawPassword) {
        rawPassword
    }

    @Override
    boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword != "invalid"
    }
}
