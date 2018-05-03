package io.micronaut.docs.security.securityRule.secured

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'docsecured')
class AuthenticationProviderUserPassword implements AuthenticationProvider {

    @Override
    AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        if ( authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password' ) {
            return new UserDetails('user', [])
        }
        if ( authenticationRequest.identity == 'admin' && authenticationRequest.secret == 'password' ) {
            return new UserDetails((String) authenticationRequest.identity, ['ROLE_ADMIN'])
        }
        return new AuthenticationFailed()
    }
}
