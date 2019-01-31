package io.micronaut.views.model.security

import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import javax.inject.Singleton

@Requires(property = 'spec.name', value = 'SecurityViewModelProcessorSpec')
@Singleton
class MockAuthenticationProvider implements AuthenticationProvider {
    @Override
    Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        UserDetailsEmail userDetailsEmail = new UserDetailsEmail(authenticationRequest.identity as String, [], 'john@email.com')
        Flowable.just(userDetailsEmail)
    }
}