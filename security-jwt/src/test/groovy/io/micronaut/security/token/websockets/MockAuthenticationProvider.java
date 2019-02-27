package io.micronaut.security.token.websockets;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.ArrayList;

@Requires(property = "spec.name", value = "websockets-on-open-header")
@Singleton
public class MockAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        return Flowable.just(new UserDetails("john", new ArrayList<>()));
    }
}
