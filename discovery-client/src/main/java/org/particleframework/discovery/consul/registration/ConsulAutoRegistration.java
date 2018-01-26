/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.consul.registration;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import org.particleframework.context.annotation.Requires;
import org.particleframework.discovery.consul.ConsulConfiguration;
import org.particleframework.discovery.consul.client.v1.ConsulClient;
import org.particleframework.discovery.consul.client.v1.NewServiceEntry;
import org.particleframework.discovery.registration.AutoRegistration;
import org.particleframework.discovery.registration.RegistrationException;
import org.particleframework.health.HeartbeatConfiguration;
import org.particleframework.http.HttpStatus;
import org.particleframework.runtime.ApplicationConfiguration;
import org.particleframework.runtime.server.EmbeddedServer;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Auto registration implementation for consul
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = ConsulClient.class)
public class ConsulAutoRegistration extends AutoRegistration {
    private final ConsulClient consulClient;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private final ConsulConfiguration consulConfiguration;

    protected ConsulAutoRegistration(
            ConsulClient consulClient,
            HeartbeatConfiguration heartbeatConfiguration,
            ApplicationConfiguration applicationConfiguration,
            ConsulConfiguration consulConfiguration) {
        this.consulClient = consulClient;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.consulConfiguration = consulConfiguration;
    }

    @Override
    protected void deregister(EmbeddedServer server) {
        Optional<String> applicationName = applicationConfiguration.getName();
        if(applicationName.isPresent()) {

            ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
            if(registration.isEnabled() && registration.isDeregister()) {
                try {
                    Flowable.fromPublisher(consulClient.deregister(applicationName.get())).blockingFirst();
                    if(LOG.isInfoEnabled()) {
                        LOG.info("De-registered service [{}] with Consul", applicationName.get());
                    }
                } catch (Throwable t) {
                    if(LOG.isErrorEnabled()) {
                        LOG.error("Error occurred de-registering service ["+applicationName.get()+"] with Consul: " + t.getMessage(), t);
                    }
                }
            }
        }
    }

    @Override
    protected void register(EmbeddedServer server) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        Optional<String> applicationName = applicationConfiguration.getName();
        if(registration.isEnabled() && applicationName.isPresent()) {
            NewServiceEntry serviceEntry = new NewServiceEntry(applicationName.get());
            serviceEntry.address(server.getHost())
                        .port(server.getPort());

            io.reactivex.Observable<HttpStatus> registrationObservable = Flowable
                                                                            .fromPublisher(consulClient.register(serviceEntry))
                                                                            .toObservable();

            Optional<Duration> timeout = registration.getTimeout();
            if(timeout.isPresent()) {
                registrationObservable = registrationObservable.timeout(timeout.get().toMillis(), TimeUnit.MILLISECONDS);
            }
            int retryCount = registration.getRetryCount();
            boolean doRetry = retryCount > 1;
            if(doRetry) {
                registrationObservable = registrationObservable.retryWhen(attempts ->
                        attempts.zipWith(Observable.range(1, retryCount), (n, i) -> i).flatMap(i ->
                                Observable.timer(registration.getRetryDelay().toMillis(), TimeUnit.MILLISECONDS)
                        )
                );
            }
            if(registration.isFailFast()) {
                // will throw an exception if a failure response code is called
                try {
                    registrationObservable.blockingSingle();
                    if(LOG.isInfoEnabled()) {
                        LOG.debug("Registered service [{}] with Consul", applicationName.get());
                    }
                }
                catch (NoSuchElementException e) {
                    if(doRetry) {
                        // timeouts throw NoSuchElementException from RxJava for some inexplicable reason
                        throw new RegistrationException("Retry timeout error occurred during service registration with Consul");
                    }
                    else {
                        throw new RegistrationException("Error occurred during service registration with Consul: " + e.getMessage(), e);
                    }
                }
                catch (Throwable e) {
                    throw new RegistrationException("Error occurred during service registration with Consul: " + e.getMessage(), e);
                }
            }
            else {
                registrationObservable.subscribe(new Observer<HttpStatus>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }
                    @Override
                    public void onNext(HttpStatus httpStatus) {
                        if(LOG.isInfoEnabled()) {
                            LOG.info("Registered service [{}] with Consul", applicationName.get());
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if(LOG.isErrorEnabled()) {
                            LOG.error("Error occurred registering service ["+applicationName.get()+"] with Consul: " + t.getMessage(), t);
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
            }
        }
    }
}
