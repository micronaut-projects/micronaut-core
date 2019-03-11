/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.discovery.spring.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.spring.SpringCloudConfigConfiguration;
import io.micronaut.discovery.spring.config.client.SpringCloudConfigClient;
import io.micronaut.discovery.spring.config.client.response.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.response.ConfigServerResponse;
import io.micronaut.discovery.spring.condition.RequiresSpringCloudConfig;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * A {@link ConfigurationClient} for Spring Cloud client.
 *
 * @author Thiago Locatelli
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@RequiresSpringCloudConfig
@Requires(beans = SpringCloudConfigClient.class)
@Requires(property = ConfigurationClient.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@BootstrapContextCompatible
public class SpringCloudConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(SpringCloudConfigurationClient.class);

    private final SpringCloudConfigClient springCloudConfigClient;
    private final SpringCloudConfigConfiguration springCloudConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private ExecutorService executionService;
    private String uri;

    /**
     * @param springCloudConfigClient  The Spring Cloud client
     * @param springCloudConfiguration The Spring Cloud configuration
     * @param applicationConfiguration The application configuration
     * @param uri                      The Spring cloud config server endpoint
     * @param executionService         The executor service to use
     */
    protected SpringCloudConfigurationClient(SpringCloudConfigClient springCloudConfigClient,
                                             SpringCloudConfigConfiguration springCloudConfiguration,
                                             ApplicationConfiguration applicationConfiguration,
                                             @Value(SpringCloudConfigConfiguration.SPRING_CLOUD_CONFIG_ENDPOINT) String uri,
                                             @Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {

        this.springCloudConfigClient = springCloudConfigClient;
        this.springCloudConfiguration = springCloudConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.uri = uri;
        this.executionService = executionService;
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!springCloudConfiguration.getConfiguration().isEnabled()) {
            return Flowable.empty();
        }

        Optional<String> configuredApplicationName = applicationConfiguration.getName();
        if (!configuredApplicationName.isPresent()) {
            return Flowable.empty();
        } else {
            String applicationName = configuredApplicationName.get();
            Set<String> activeNames = environment.getActiveNames();
            String profiles = String.join(",", activeNames);

            if (StringUtils.isEmpty(profiles)) {
                profiles = Environment.DEVELOPMENT;
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("Spring Cloud Config Active: {}", uri);
                LOG.info("Application Name: {}, Application Profiles: {}", applicationName, profiles);
            }

            Function<Throwable, Publisher<? extends ConfigServerResponse>> errorHandler = throwable -> {
                if (throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException httpClientResponseException = (HttpClientResponseException) throwable;
                    if (httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND) {
                        return Flowable.empty();
                    }
                }
                return Flowable.error(throwable);
            };

            Flowable<ConfigServerResponse> configurationValues =
                    Flowable.fromPublisher(springCloudConfigClient.readValues(applicationName, profiles))
                            .onErrorResumeNext(errorHandler);

            Flowable<PropertySource> propertySourceFlowable = configurationValues.flatMap(configServerResponse -> Flowable.create(emitter -> {
                List<ConfigServerPropertySource> propertySources = configServerResponse.getPropertySources();
                if (CollectionUtils.isEmpty(propertySources)) {
                    emitter.onComplete();
                } else {
                    int priority = Integer.MAX_VALUE;
                    for (ConfigServerPropertySource propertySource : propertySources) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Obtained property source [{}] from Spring Cloud Configuration Server", propertySource.getName());
                        }

                        emitter.onNext(PropertySource.of(propertySource.getName(), propertySource.getSource(), priority));
                        priority -= 10;
                    }
                    emitter.onComplete();
                }
            }, BackpressureStrategy.ERROR));

            if (executionService != null) {
                return propertySourceFlowable.subscribeOn(io.reactivex.schedulers.Schedulers.from(
                        executionService
                ));
            } else {
                return propertySourceFlowable;
            }
        }
    }

    @Override
    public final String getDescription() {
        return io.micronaut.discovery.spring.config.client.SpringCloudConfigClient.CLIENT_DESCRIPTION;
    }

}
