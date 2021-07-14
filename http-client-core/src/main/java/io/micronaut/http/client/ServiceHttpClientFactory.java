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
package io.micronaut.http.client;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.TaskScheduler;
import reactor.core.publisher.Flux;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Creates {@link HttpClient} instances for each defined {@link ServiceHttpClientConfiguration}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Internal
public class ServiceHttpClientFactory {

    private final TaskScheduler taskScheduler;
    private final BeanProvider<HttpClientRegistry<?>> clientFactory;

    /**
     * Default constructor.
     *
     * @param taskScheduler The task scheduler
     * @param clientFactory The client factory
     */
    public ServiceHttpClientFactory(
            TaskScheduler taskScheduler,
            BeanProvider<HttpClientRegistry<?>> clientFactory) {
        this.taskScheduler = taskScheduler;
        this.clientFactory = clientFactory;
    }

    /**
     * Create a {@link io.micronaut.discovery.ServiceInstanceList} for each configured client.
     *
     * @param configuration The configuration
     * @return The instance list
     */
    @EachBean(ServiceHttpClientConfiguration.class)
    @Requires(condition = ServiceHttpClientCondition.class)
    StaticServiceInstanceList serviceInstanceList(ServiceHttpClientConfiguration configuration) {
        List<URI> originalURLs = configuration.getUrls();
        Collection<URI> loadBalancedURIs = new ConcurrentLinkedQueue<>(originalURLs);
        return new StaticServiceInstanceList(configuration.getServiceId(), loadBalancedURIs, configuration.getPath().orElse(null));
    }

    /**
     * Creates a {@link ApplicationEventListener} that listens to {@link ServerStartupEvent} for each configured HTTP client
     * in order to register a health check if necessary.
     *
     * @param configuration The configuration
     * @param instanceList  The instance list
     * @return The event listener
     */
    @EachBean(ServiceHttpClientConfiguration.class)
    @Requires(condition = ServiceHttpClientCondition.class)
    ApplicationEventListener<ServerStartupEvent> healthCheckStarter(@Parameter ServiceHttpClientConfiguration configuration,
                                                                    @Parameter StaticServiceInstanceList instanceList) {
        if (configuration.isHealthCheck()) {
            return event -> {
                    final List<URI> originalURLs = configuration.getUrls();
                    Collection<URI> loadBalancedURIs = instanceList.getLoadBalancedURIs();
                    final HttpClient httpClient = clientFactory.get()
                            .getClient(
                                    configuration.getHttpVersion(),
                                    configuration.getServiceId(),
                                    configuration.getPath().orElse(null));
                    final Duration initialDelay = configuration.getHealthCheckInterval();
                    Duration delay = configuration.getHealthCheckInterval();
                    taskScheduler.scheduleWithFixedDelay(initialDelay, delay, () -> Flux.fromIterable(originalURLs)
                            .flatMap(originalURI -> {
                                URI healthCheckURI = originalURI.resolve(configuration.getHealthCheckUri());
                                return Flux.from(httpClient.exchange(HttpRequest.GET(healthCheckURI)))
                                        .onErrorResume(throwable -> {
                                            if (throwable instanceof HttpClientResponseException) {
                                                HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                                                return Flux.just((HttpResponse<ByteBuffer>) responseException.getResponse());
                                            }
                                            return Flux.just(HttpResponse.serverError());
                                        }).map(response -> Collections.singletonMap(originalURI, response.getStatus()));
                            }).subscribe(uriToStatusMap -> {
                                Map.Entry<URI, HttpStatus> entry = uriToStatusMap.entrySet().iterator().next();
                                URI uri = entry.getKey();
                                HttpStatus status = entry.getValue();
                                if (status.getCode() >= 300) {
                                    loadBalancedURIs.remove(uri);
                                } else if (!loadBalancedURIs.contains(uri)) {
                                    loadBalancedURIs.add(uri);
                                }
                    }));
            };
        }
        throw new DisabledBeanException("HTTP Client Health Check not enabled");
    }
}
