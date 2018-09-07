package io.micronaut.security.atsecured

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Singleton

@Singleton
@Requires(env = Environment.TEST)
@Requires(property = 'spec.name', value = 'AtSecuredAppliedToServiceMethodSpec')
class AuthenticationProviderUserPassword implements AuthenticationProvider {

    @Override
    Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        if ( authenticationRequest.identity == 'sherlock' && authenticationRequest.secret == 'password' ) {
            return Flowable.just(new UserDetails('user', ['ROLE_DETECTIVE']))
        }
        if ( authenticationRequest.identity == 'watson' && authenticationRequest.secret == 'password' ) {
            return Flowable.just(new UserDetails('user', ['ROLE_DOCTOR']))
        }
        return Flowable.just(new AuthenticationFailed())
    }
}
