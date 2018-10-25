package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;

@Requires(property = "spec.name", value = "tokenpropagation.gateway")
@Singleton
public class SampleAuthenticationProvider implements AuthenticationProvider {
    @Override
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        if (authenticationRequest.getIdentity() == null) {
            return Flowable.just(new AuthenticationFailed());
        }
        if (authenticationRequest.getSecret() == null) {
            return Flowable.just(new AuthenticationFailed());
        }
        if (Arrays.asList("sherlock", "watson").contains(authenticationRequest.getIdentity().toString()) &&
                authenticationRequest.getSecret().equals("elementary")) {
            return Flowable.just(new UserDetails(authenticationRequest.getIdentity().toString(), new ArrayList<>()));
        }
        return Flowable.just(new AuthenticationFailed());
    }
}