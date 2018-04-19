package io.micronaut.security.endpoints

import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials

import javax.inject.Singleton

@Singleton
class AuthenticationProviderUserPassword implements AuthenticationProvider {

    @Override
    AuthenticationResponse authenticate(UsernamePasswordCredentials creds) {
        if ( creds.username == 'user' && creds.password == 'password' ) {
            return new UserDetails('user', [])
        }
        return new AuthenticationFailed()
    }
}
