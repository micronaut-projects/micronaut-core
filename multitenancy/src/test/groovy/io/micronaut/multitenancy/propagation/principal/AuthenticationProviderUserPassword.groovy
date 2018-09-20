package io.micronaut.multitenancy.propagation.principal

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'multitenancy.principal.gateway')
class AuthenticationProviderUserPassword implements AuthenticationProvider {

    @Override
    Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        if ( authenticationRequest.identity == 'sherlock' && authenticationRequest.secret == 'elementary' ) {
            return Flowable.just(new UserDetails('sherlock', []))
        }
        if ( authenticationRequest.identity == 'watson' && authenticationRequest.secret == 'elementary' ) {
            return Flowable.just(new UserDetails('watson', []))
        }
        return Flowable.just(new AuthenticationFailed())
    }
}

