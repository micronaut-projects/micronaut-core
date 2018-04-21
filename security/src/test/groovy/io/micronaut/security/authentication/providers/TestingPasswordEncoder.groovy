package io.micronaut.security.authentication.providers

import io.micronaut.context.annotation.Requires

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.authentication')
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
