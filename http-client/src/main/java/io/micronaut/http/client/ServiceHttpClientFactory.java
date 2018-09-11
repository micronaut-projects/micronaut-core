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

package io.micronaut.http.client;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import io.micronaut.scheduling.TaskScheduler;
import io.reactivex.Flowable;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Creates {@link HttpClient} instances for each defined {@link ServiceHttpClientConfiguration}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class ServiceHttpClientFactory {

    private final BeanContext beanContext;
    private final ServiceInstanceListLoadBalancerFactory loadBalancerFactory;
    private final TaskScheduler taskScheduler;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param loadBalancerFactory The load balancer factory
     * @param taskScheduler The task scheduler
     */
    public ServiceHttpClientFactory(
            BeanContext beanContext,
            ServiceInstanceListLoadBalancerFactory loadBalancerFactory,
            TaskScheduler taskScheduler) {
        this.beanContext = beanContext;
        this.loadBalancerFactory = loadBalancerFactory;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Creates {@link HttpClient} instances for each defined {@link ServiceHttpClientConfiguration}.
     * @param configuration The configuration
     * @return The client bean
     */
    @EachBean(ServiceHttpClientConfiguration.class)
    @Requires(condition = ServiceHttpClientCondition.class)
    DefaultHttpClient serviceHttpClient(ServiceHttpClientConfiguration configuration) {
        List<URI> originalURLs = configuration.getUrls();
        Collection<URI> loadBalancedURIs = new ConcurrentLinkedQueue<>(originalURLs);
        boolean isHealthCheck = configuration.isHealthCheck();

        ServiceInstanceList instanceList = new ServiceInstanceList() {
            @Override
            public String getID() {
                return configuration.getServiceId();
            }

            @Override
            public List<ServiceInstance> getInstances() {
                return loadBalancedURIs.stream().map(url -> {
                    ServiceInstance.Builder builder = ServiceInstance.builder(configuration.getServiceId(), url);
                    builder.status(HealthStatus.UP);
                    return builder.build();
                }).collect(Collectors.toList());
            }
        };
        LoadBalancer loadBalancer = loadBalancerFactory.create(instanceList);

        Optional<String> path = configuration.getPath();
        DefaultHttpClient httpClient;
        if (path.isPresent()) {
            httpClient = beanContext.createBean(DefaultHttpClient.class, loadBalancer, configuration, path.get());
        } else {
            httpClient = beanContext.createBean(DefaultHttpClient.class, loadBalancer, configuration);
        }


        if (isHealthCheck) {
            taskScheduler.scheduleWithFixedDelay(configuration.getHealthCheckInterval(), configuration.getHealthCheckInterval(), () -> Flowable.fromIterable(originalURLs).flatMap(originalURI -> {
                URI healthCheckURI = originalURI.resolve(configuration.getHealthCheckUri());
                return httpClient.exchange(HttpRequest.GET(healthCheckURI)).onErrorResumeNext(throwable -> {
                    if (throwable instanceof HttpClientResponseException) {
                        HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                        HttpResponse response = responseException.getResponse();
                        //noinspection unchecked
                        return Flowable.just(response);
                    }
                    return Flowable.just(HttpResponse.serverError());
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
        }
        return httpClient;
    }
}
