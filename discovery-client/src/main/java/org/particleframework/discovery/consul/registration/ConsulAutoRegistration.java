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
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.value.ConvertibleMultiValues;
import org.particleframework.core.util.StringUtils;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.ServiceInstanceIdGenerator;
import org.particleframework.discovery.consul.ConsulConfiguration;
import org.particleframework.discovery.consul.client.v1.*;
import org.particleframework.discovery.exceptions.DiscoveryException;
import org.particleframework.discovery.registration.AutoRegistration;
import org.particleframework.discovery.registration.RegistrationException;
import org.particleframework.health.HealthStatus;
import org.particleframework.health.HeartbeatConfiguration;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.runtime.ApplicationConfiguration;
import org.particleframework.runtime.server.EmbeddedServerInstance;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Auto registration implementation for consul
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = ConsulClient.class)
public class ConsulAutoRegistration extends AutoRegistration {
    private static final Pattern APPLICATION_NAME_PATTERN = Pattern.compile("^[a-zA-Z][\\w\\d-]*[a-zA-Z\\d]$");
    private final ConsulClient consulClient;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ConsulConfiguration consulConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final Environment environment;

    protected ConsulAutoRegistration(
            Environment environment,
            ConsulClient consulClient,
            HeartbeatConfiguration heartbeatConfiguration,
            ConsulConfiguration consulConfiguration,
            ServiceInstanceIdGenerator idGenerator) {
        this.environment = environment;
        this.consulClient = consulClient;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.consulConfiguration = consulConfiguration;
        this.idGenerator = idGenerator;
    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        if(!consulConfiguration.getRegistration().getCheck().isHttp()) {

            String checkId = "service:" + idGenerator.generateId(environment, instance);

            if (status.equals(HealthStatus.UP)) {
                // send a request to /agent/check/pass/:check_id
                consulClient.pass(checkId).subscribe(new Subscriber<HttpStatus>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(HttpStatus httpStatus) {
                        if(LOG.isDebugEnabled()) {
                            LOG.debug("Successfully reported passing state to Consul");
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        String errorMessage = getConsulErrorMessage(throwable, "Error reporting passing state to Consul: ");
                        if(LOG.isErrorEnabled()) {
                            LOG.error(errorMessage, throwable);
                        }

                    }

                    @Override
                    public void onComplete() {

                    }
                });
            } else {
                // send a request to /agent/check/fail/:check_id
                consulClient.fail(checkId, status.getDescription()).subscribe(new Subscriber<HttpStatus>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(HttpStatus httpStatus) {
                        if(LOG.isDebugEnabled()) {
                            LOG.debug("Successfully reported failure state to Consul");
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        String errorMessage = getConsulErrorMessage(throwable, "Error reporting passing state to Consul: ");
                        if(LOG.isErrorEnabled()) {
                            LOG.error("Error reporting failure state to Consul: " +errorMessage, throwable);
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
            }
        }
    }

    @Override
    protected void deregister(ServiceInstance instance) {
        String applicationName = instance.getId();
        String serviceId = idGenerator.generateId(environment, instance);
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration.isEnabled() && registration.isDeregister()) {
            if (registration.isFailFast()) {

                try {
                    Flowable.fromPublisher(consulClient.deregister(serviceId)).blockingFirst();
                    if (LOG.isInfoEnabled()) {
                        LOG.info("De-registered service [{}] with Consul", applicationName);
                    }
                } catch (Throwable t) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error occurred de-registering service [" + applicationName + "] with Consul: " + t.getMessage(), t);
                    }
                }
            } else {
                consulClient.deregister(applicationName).subscribe(new Subscriber<HttpStatus>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(HttpStatus httpStatus) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("De-registered service [{}] with Consul", applicationName);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error occurred de-registering service [" + applicationName + "] with Consul: " + t.getMessage(), t);
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
            }
        }
    }

    @Override
    protected void register(ServiceInstance instance) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        String applicationName = instance.getId();
        validateApplicationName(applicationName);
        if (registration.isEnabled() && StringUtils.isNotEmpty(applicationName)) {
            NewServiceEntry serviceEntry = new NewServiceEntry(applicationName);
            List<String> tags = new ArrayList<>(registration.getTags());
            serviceEntry.address(instance.getHost())
                    .port(instance.getPort())
                    .tags(tags);

            String serviceId = idGenerator.generateId(environment, instance);
            serviceEntry.id(serviceId);

            if (instance instanceof EmbeddedServerInstance) {
                Check check = null;
                EmbeddedServerInstance embeddedServerInstance = (EmbeddedServerInstance) instance;
                ApplicationConfiguration applicationConfiguration = embeddedServerInstance.getEmbeddedServer().getApplicationConfiguration();
                ApplicationConfiguration.InstanceConfiguration instanceConfiguration = applicationConfiguration.getInstance();
                instanceConfiguration.getGroup().ifPresent(g -> {
                            validateName(g, "Instance Group");
                            tags.add(ServiceInstance.GROUP + "=" + g);
                        }

                );
                instanceConfiguration.getZone().ifPresent(z -> {
                            validateName(z, "Instance Zone");
                            tags.add(ServiceInstance.ZONE + "=" + z);
                        }
                );

                ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig = registration.getCheck();
                if (checkConfig.isEnabled()) {

                    if (heartbeatConfiguration.isEnabled() && !checkConfig.isHttp()) {
                        TTLCheck ttlCheck = new TTLCheck();
                        ttlCheck.ttl(heartbeatConfiguration.getInterval().plus(Duration.ofSeconds(10)));
                        check = ttlCheck;
                    } else {

                        URL serverURL = ((EmbeddedServerInstance) instance).getEmbeddedServer().getURL();
                        HTTPCheck httpCheck;
                        try {
                            httpCheck = new HTTPCheck(
                                    new URL(serverURL, registration.getHealthPath().orElse("/health"))
                            );
                        } catch (MalformedURLException e) {
                            throw new DiscoveryException("Invalid health path configured: " + registration.getHealthPath());
                        }

                        httpCheck.interval(checkConfig.getInterval());
                        httpCheck.method(checkConfig.getMethod())
                                .headers(ConvertibleMultiValues.of(checkConfig.getHeaders()));

                        checkConfig.getTlsSkipVerify().ifPresent(httpCheck::setTLSSkipVerify);
                        check = httpCheck;
                    }
                }


                if (check != null) {
                    check.status(Check.HealthStatus.PASSING);
                    checkConfig.getDeregisterCriticalServiceAfter().ifPresent(check::deregisterCriticalServiceAfter);
                    checkConfig.getNotes().ifPresent(check::notes);
                    checkConfig.getId().ifPresent(check::id);
                    serviceEntry.check(check);
                }

            }

            customizeServiceEntry(instance, serviceEntry);
            io.reactivex.Observable<HttpStatus> registrationObservable = Flowable
                    .fromPublisher(consulClient.register(serviceEntry))
                    .toObservable();

            Optional<Duration> timeout = registration.getTimeout();
            if (timeout.isPresent()) {
                registrationObservable = registrationObservable.timeout(timeout.get().toMillis(), TimeUnit.MILLISECONDS);
            }
            int retryCount = registration.getRetryCount();
            boolean doRetry = retryCount > 1;
            if (doRetry) {
                registrationObservable = registrationObservable.retryWhen(attempts ->
                        attempts.zipWith(Observable.range(1, retryCount), (n, i) -> i).flatMap(i ->
                                Observable.timer(registration.getRetryDelay().toMillis(), TimeUnit.MILLISECONDS)
                        )
                );
            }
            if (registration.isFailFast()) {
                // will throw an exception if a failure response code is called
                try {
                    registrationObservable.blockingSingle();
                    if (LOG.isInfoEnabled()) {
                        LOG.debug("Registered service [{}] with Consul", applicationName);
                    }
                } catch (NoSuchElementException e) {
                    if (doRetry) {
                        // timeouts throw NoSuchElementException from RxJava for some inexplicable reason
                        throw new RegistrationException("Retry timeout error occurred during service registration with Consul");
                    } else {
                        throw new RegistrationException("Error occurred during service registration with Consul: " + e.getMessage(), e);
                    }
                } catch (Throwable e) {
                    String message = getConsulErrorMessage(e);
                    throw new RegistrationException(message, e);
                }
            } else {
                registrationObservable.subscribe(new Observer<HttpStatus>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(HttpStatus httpStatus) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Registered service [{}] with Consul", applicationName);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (LOG.isErrorEnabled()) {
                            String message = getConsulErrorMessage(t);
                            LOG.error(message, t);
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
            }
        }
    }

    /**
     * Allows sub classes to override and customize the configuration
     *
     * @param instance     The instance
     * @param serviceEntry The service entry
     */
    protected void customizeServiceEntry(ServiceInstance instance, NewServiceEntry serviceEntry) {
        // no-op
    }

    private String getConsulErrorMessage(Throwable e) {
        String description = "Error occurred during service registration with Consul: ";
        return getConsulErrorMessage(e, description);
    }

    private String getConsulErrorMessage(Throwable e, String description) {
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

    private void validateApplicationName(String name) {
        String typeDescription = "Application name";
        validateName(name, typeDescription);
    }

    private void validateName(String name, String typeDescription) {
        if (!APPLICATION_NAME_PATTERN.matcher(name).matches()) {
            throw new DiscoveryException(typeDescription + " [" + name + "] must start with a letter, end with a letter or digit and contain only letters, digits or hyphens. Example: foo-bar");
        }
    }
}
