/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.client.registration;

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.registration.AutoRegistration;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for {@link AutoRegistration} with discovery services.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DiscoveryServiceAutoRegistration extends AutoRegistration {

    /**
     * @param registrationConfiguration The registration configuration
     */
    protected DiscoveryServiceAutoRegistration(RegistrationConfiguration registrationConfiguration) {
        super(registrationConfiguration);
    }

    /**
     * Register a new service instance in the discovery service.
     *
     * @param discoveryService       The discovery service
     * @param registration           The registration configuration
     * @param instance               The service instance
     * @param registrationObservable The registration observable
     */
    protected void performRegistration(
        String discoveryService,
        RegistrationConfiguration registration,
        ServiceInstance instance,
        Publisher<HttpStatus> registrationObservable) {

        Flowable<HttpStatus> registrationFlowable = Flowable.fromPublisher(registrationObservable);
        final Duration timeout = registration.getTimeout().orElse(null);
        if (timeout != null) {
            registrationFlowable = registrationFlowable.timeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        registrationFlowable.subscribe(new Subscriber<HttpStatus>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(HttpStatus httpStatus) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Registered service [{}] with {}", instance.getId(), discoveryService);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (LOG.isErrorEnabled()) {
                    String message = getErrorMessage(discoveryService, t);
                    LOG.error(message, t);
                }
                if (registration.isFailFast() && instance instanceof EmbeddedServerInstance) {
                    ((EmbeddedServerInstance) instance).getEmbeddedServer().stop();
                }
            }

            @Override
            public void onComplete() {
                registered.set(true);
            }
        });
    }

    /**
     * @param e           The throwable
     * @param description The error's description
     * @return The error message
     */
    protected String getErrorMessage(Throwable e, String description) {
        String message;
        if (e instanceof HttpClientResponseException) {
            HttpClientResponseException hcre = (HttpClientResponseException) e;
            if (hcre.getStatus() == HttpStatus.BAD_REQUEST) {
                message = description + hcre.getResponse().getBody(String.class).orElse(e.getMessage());
            } else {
                message = description + e.getMessage();
            }
        } else {
            message = description + e.getMessage();
        }
        return message;
    }

    /**
     * De-register a service from the discovery client.
     *
     * @param discoveryService    The discovery service
     * @param registration        The registration configuration
     * @param deregisterPublisher The registration publisher
     * @param applicationName     The application name to de-register
     */
    protected void performDeregistration(String discoveryService, RegistrationConfiguration registration, Publisher<HttpStatus> deregisterPublisher, String applicationName) {
        Flowable<HttpStatus> deregisterFlowable = Flowable.fromPublisher(deregisterPublisher);
        final Duration timeout = registration.getTimeout().orElse(null);
        if (timeout != null) {
            deregisterFlowable = deregisterFlowable.timeout(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }
        if (registration.isFailFast()) {

            try {
                deregisterFlowable
                        .blockingFirst();
                if (LOG.isInfoEnabled()) {
                    LOG.info("De-registered service [{}] with {}", applicationName, discoveryService);
                }
            } catch (Throwable t) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error occurred de-registering service [" + applicationName + "] with " + discoveryService + ": " + t.getMessage(), t);
                }
            } finally {
                registered.set(false);
            }
        } else {
            deregisterFlowable.subscribe(new Subscriber<HttpStatus>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(HttpStatus httpStatus) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("De-registered service [{}] with {}", applicationName, discoveryService);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error occurred de-registering service [" + applicationName + "] with " + discoveryService + ": " + t.getMessage(), t);
                    }
                    registered.set(false);
                }

                @Override
                public void onComplete() {
                    registered.set(false);
                }
            });
        }
    }

    private String getErrorMessage(String discoveryService, Throwable e) {
        String description = "Error occurred during service registration with " + discoveryService + ": ";
        return getErrorMessage(e, description);
    }
}
