package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'endpoints')
class AuthenticationProviderThrowingException implements AuthenticationProvider {
    @Override
    AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        throw new Exception()
    }
}
