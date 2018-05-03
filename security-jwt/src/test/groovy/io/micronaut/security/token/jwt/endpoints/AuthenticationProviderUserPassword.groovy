package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'endpoints')
class AuthenticationProviderUserPassword implements AuthenticationProvider {

    @Override
    AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        if ( authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password' ) {
            return new UserDetails('user', [])
        }
        return new AuthenticationFailed()
    }
}
