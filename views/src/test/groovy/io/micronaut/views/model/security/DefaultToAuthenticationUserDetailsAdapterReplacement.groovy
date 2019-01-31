package io.micronaut.views.model.security

import io.micronaut.context.annotation.Replaces
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.DefaultToAuthenticationUserDetailsAdapter
import io.micronaut.security.authentication.UserDetailsAuthenticationResponseToAuthenticationAdapter
import io.micronaut.security.token.config.TokenConfiguration
import javax.inject.Singleton

@Replaces(DefaultToAuthenticationUserDetailsAdapter)
@Singleton
class DefaultToAuthenticationUserDetailsAdapterReplacement implements UserDetailsAuthenticationResponseToAuthenticationAdapter {

    private final TokenConfiguration tokenConfiguration

    /**
     *
     * @param tokenConfiguration The Token configuration.
     */
    DefaultToAuthenticationUserDetailsAdapterReplacement(TokenConfiguration tokenConfiguration) {
        this.tokenConfiguration = tokenConfiguration
    }

    @Override
    Authentication adapt(AuthenticationResponse authenticationResponse) {
        if (authenticationResponse instanceof UserDetailsEmail) {
            return new UserDetailsEmailAdapter(authenticationResponse)
        }
        throw new IllegalArgumentException("authenticationResponse is not of type UserDetailsEmail")
    }
}
