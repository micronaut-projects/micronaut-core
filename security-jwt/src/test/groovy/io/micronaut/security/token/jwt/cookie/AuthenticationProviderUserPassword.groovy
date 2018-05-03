package io.micronaut.security.token.jwt.cookie

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails

import javax.inject.Singleton

@Requires(property = "spec.name", value = "jwtcookie")
@Singleton
class AuthenticationProviderUserPassword implements AuthenticationProvider  {
    @Override
    AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        if ( authenticationRequest.getIdentity().equals("sherlock") &&
                authenticationRequest.getSecret().equals("password") ) {
            return new UserDetails((String) authenticationRequest.getIdentity(), new ArrayList<>())
        }
        return new AuthenticationFailed()
    }
}
