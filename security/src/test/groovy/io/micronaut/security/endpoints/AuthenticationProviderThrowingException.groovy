package io.micronaut.security.endpoints

import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UsernamePasswordCredentials

import javax.inject.Singleton

@Singleton
class AuthenticationProviderThrowingException implements AuthenticationProvider {
    @Override
    AuthenticationResponse authenticate(UsernamePasswordCredentials usernamePasswordCredentials) {
        throw new Exception()
    }
}
