/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.consul.registration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceIdGenerator;
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.Check;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.HTTPCheck;
import io.micronaut.discovery.consul.client.v1.NewCheck;
import io.micronaut.discovery.consul.client.v1.NewServiceEntry;
import io.micronaut.discovery.consul.client.v1.TTLCheck;
import io.micronaut.discovery.exceptions.DiscoveryException;
import io.micronaut.discovery.registration.RegistrationException;
import io.micronaut.health.HealthStatus;
import io.micronaut.health.HeartbeatConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto registration implementation for consul.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = {ConsulClient.class, ConsulConfiguration.class})
public class ConsulAutoRegistration extends DiscoveryServiceAutoRegistration {

    private final ConsulClient consulClient;
    private final HeartbeatConfiguration heartbeatConfiguration;
    private final ConsulConfiguration consulConfiguration;
    private final ServiceInstanceIdGenerator idGenerator;
    private final Environment environment;

    /**
     * @param environment            The environment
     * @param consulClient           The Consul client
     * @param heartbeatConfiguration The heartbeat configuration
     * @param consulConfiguration    The Consul configuration
     * @param idGenerator            The id generator
     */
    protected ConsulAutoRegistration(
        Environment environment,
        ConsulClient consulClient,
        HeartbeatConfiguration heartbeatConfiguration,
        ConsulConfiguration consulConfiguration,
        ServiceInstanceIdGenerator idGenerator) {

        super(consulConfiguration.getRegistration());
        this.environment = environment;
        this.consulClient = consulClient;
        this.heartbeatConfiguration = heartbeatConfiguration;
        this.consulConfiguration = consulConfiguration;
        this.idGenerator = idGenerator;
    }

    @Override
    protected void pulsate(ServiceInstance instance, HealthStatus status) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null && !registration.getCheck().isHttp() && registration.getCheck().isEnabled() && registered.get()) {

            String checkId = "service:" + idGenerator.generateId(environment, instance);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reporting status for Check ID [{}]: {}", checkId, status);
            }

            if (status.equals(HealthStatus.UP)) {
                // send a request to /agent/check/pass/:check_id
                Single<HttpStatus> passPublisher = Single.fromPublisher(consulClient.pass(checkId));
                passPublisher.subscribe((httpStatus, throwable) -> {
                    if (throwable == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Successfully reported passing state to Consul");
                        }
                    } else {
                        // check if the service is still registered with Consul

                        Single.fromPublisher(consulClient.getServiceIds()).subscribe((serviceIds, throwable1) -> {
                            if (throwable1 == null) {
                                String serviceId = idGenerator.generateId(environment, instance);
                                if (!serviceIds.contains(serviceId)) {
                                    if (LOG.isInfoEnabled()) {
                                        LOG.info("Instance [{}] no longer registered with Consul. Attempting re-registration.", instance.getId());
                                    }
                                    register(instance);
                                }
                            }
                        });

                        String errorMessage = getErrorMessage(throwable, "Error reporting passing state to Consul: ");
                        if (LOG.isErrorEnabled()) {
                            LOG.error(errorMessage, throwable);
                        }
                    }
                });
            } else {
                // send a request to /agent/check/fail/:check_id
                Single<HttpStatus> failPublisher = Single.fromPublisher(consulClient.fail(checkId, status.getDescription().orElse(null)));
                failPublisher.subscribe((httpStatus, throwable) -> {
                    if (throwable == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Successfully reported failure state to Consul");
                        }
                    } else {
                        String errorMessage = getErrorMessage(throwable, "Error reporting passing state to Consul: ");
                        if (LOG.isErrorEnabled()) {
                            LOG.error(errorMessage, throwable);
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void deregister(ServiceInstance instance) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null) {
            String applicationName = instance.getId();
            String serviceId = idGenerator.generateId(environment, instance);
            Publisher<HttpStatus> deregisterPublisher = consulClient.deregister(serviceId);
            final String discoveryService = "Consul";
            performDeregistration(discoveryService, registration, deregisterPublisher, applicationName);
        }
    }

    @SuppressWarnings("MagicNumber")
    @Override
    protected void register(ServiceInstance instance) {
        ConsulConfiguration.ConsulRegistrationConfiguration registration = consulConfiguration.getRegistration();
        if (registration != null) {
            String applicationName = instance.getId();
            validateApplicationName(applicationName);
            if (StringUtils.isNotEmpty(applicationName)) {
                NewServiceEntry serviceEntry = new NewServiceEntry(applicationName);
                List<String> tags = new ArrayList<>(registration.getTags());

                String address;
                if (registration.isPreferIpAddress()) {
                    address = registration.getIpAddr().orElseGet(() -> {
                        final String host = instance.getHost();
                        try {
                            final InetAddress inetAddress = InetAddress.getByName(host);
                            return inetAddress.getHostAddress();
                        } catch (UnknownHostException e) {
                            throw new RegistrationException("Failed to lookup IP address for host [" + host + "]: " + e.getMessage(), e);
                        }
                    });
                } else {
                    address = instance.getHost();
                }

                serviceEntry.address(address)
                    .port(instance.getPort())
                    .tags(tags);

                String serviceId = idGenerator.generateId(environment, instance);
                serviceEntry.id(serviceId);

                if (instance instanceof EmbeddedServerInstance) {
                    NewCheck check = null;
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

                    // include metadata as tags
                    ConvertibleValues<String> metadata = embeddedServerInstance.getMetadata();
                    for (Map.Entry<String, String> entry : metadata) {
                        tags.add(entry.getKey() + "=" + entry.getValue());
                    }

                    ConsulConfiguration.ConsulRegistrationConfiguration.CheckConfiguration checkConfig = registration.getCheck();
                    if (checkConfig.isEnabled()) {

                        if (heartbeatConfiguration.isEnabled() && !checkConfig.isHttp()) {
                            TTLCheck ttlCheck = new TTLCheck();
                            ttlCheck.ttl(heartbeatConfiguration.getInterval().plus(Duration.ofSeconds(10)));
                            check = ttlCheck;
                        } else {

                            EmbeddedServer embeddedServer = ((EmbeddedServerInstance) instance).getEmbeddedServer();
                            URL serverURL = embeddedServer.getURL();
                            if (registration.isPreferIpAddress() && address != null) {

                                try {
                                    serverURL = new URL(embeddedServer.getURL().getProtocol(), address, embeddedServer.getPort(), embeddedServer.getURL().getPath());
                                } catch (MalformedURLException e) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.error("invalid url for health check:" + embeddedServer.getURL().getProtocol() + address + ":" + embeddedServer.getPort() + "/" + embeddedServer.getURL().getPath());
                                    }
                                    throw new DiscoveryException("Invalid health path configured: " + registration.getHealthPath());
                                }
                            }

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
                        check.status(Check.Status.PASSING);
                        checkConfig.getDeregisterCriticalServiceAfter().ifPresent(check::deregisterCriticalServiceAfter);
                        checkConfig.getNotes().ifPresent(check::notes);
                        checkConfig.getId().ifPresent(check::id);
                        serviceEntry.check(check);
                    }

                }

                customizeServiceEntry(instance, serviceEntry);
                Publisher<HttpStatus> registerFlowable = consulClient.register(serviceEntry);
                performRegistration("Consul", registration, instance, registerFlowable);
            }
        }
    }

    /**
     * Allows sub classes to override and customize the configuration.
     *
     * @param instance     The instance
     * @param serviceEntry The service entry
     */
    protected void customizeServiceEntry(ServiceInstance instance, NewServiceEntry serviceEntry) {
        // no-op
    }
}
