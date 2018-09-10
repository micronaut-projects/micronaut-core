/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.security.authentication;

import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * An Authenticator operates on several {@link AuthenticationProvider} instances returning the first
 * authenticated {@link AuthenticationResponse}.
 *
 * @author Sergio del Amo
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class Authenticator {
    private static final Logger LOG = LoggerFactory.getLogger(Authenticator.class);

    protected final Collection<AuthenticationProvider> authenticationProviders;

    /**
     * @param authenticationProviders a List of availabble authentication providers
     */
    public Authenticator(Collection<AuthenticationProvider> authenticationProviders) {
        this.authenticationProviders = authenticationProviders;
    }

    /**
     * @param credentials instance of {@link UsernamePasswordCredentials}
     * @return Empty optional if authentication failed. If any {@link AuthenticationProvider} authenticates, that {@link AuthenticationResponse} is sent.
     */
    public Publisher<AuthenticationResponse> authenticate(UsernamePasswordCredentials credentials) {
        if (this.authenticationProviders == null) {
            return Flowable.empty();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(authenticationProviders.stream().map(AuthenticationProvider::getClass).map(Class::getName).collect(Collectors.joining()));
        }
        Iterator<AuthenticationProvider> providerIterator = authenticationProviders.iterator();
        if (providerIterator.hasNext()) {
            Flowable<AuthenticationProvider> providerFlowable = Flowable.just(providerIterator.next());
            AtomicReference<AuthenticationResponse> lastFailure = new AtomicReference<>();
            return attemptAuthenticationRequest(credentials, providerIterator, providerFlowable, lastFailure);
        } else {
            return Flowable.empty();
        }
    }

    private Flowable<AuthenticationResponse> attemptAuthenticationRequest(
        UsernamePasswordCredentials credentials,
        Iterator<AuthenticationProvider> providerIterator,
        Flowable<AuthenticationProvider> providerFlowable, AtomicReference<AuthenticationResponse> lastFailure) {

        return providerFlowable.switchMap(authenticationProvider -> {
            Flowable<AuthenticationResponse> responseFlowable = Flowable.fromPublisher(authenticationProvider.authenticate(credentials));
            Flowable<AuthenticationResponse> authenticationAttemptFlowable = responseFlowable.switchMap(authenticationResponse -> {
                if (authenticationResponse.isAuthenticated()) {
                    return Flowable.just(authenticationResponse);
                } else if (providerIterator.hasNext()) {
                    lastFailure.set(authenticationResponse);
                    // recurse
                    return attemptAuthenticationRequest(
                        credentials,
                        providerIterator,
                        Flowable.just(providerIterator.next()),
                        lastFailure);
                } else {
                    lastFailure.set(authenticationResponse);
                    return Flowable.just(authenticationResponse);
                }
            });
            return authenticationAttemptFlowable.onErrorResumeNext(throwable -> {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Authentication provider threw exception", throwable);
                }
                if (providerIterator.hasNext()) {
                    // recurse
                    return attemptAuthenticationRequest(
                        credentials,
                        providerIterator,
                        Flowable.just(providerIterator.next()),
                        lastFailure);
                } else {
                    AuthenticationResponse lastFailureResponse = lastFailure.get();
                    if (lastFailureResponse != null) {
                        return Flowable.just(lastFailureResponse);
                    }
                    return Flowable.empty();
                }
            });
        });
    }
}
