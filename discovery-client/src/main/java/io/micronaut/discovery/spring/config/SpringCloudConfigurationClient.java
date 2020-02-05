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
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.spring.config.client.SpringCloudConfigClient;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
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
@BootstrapContextCompatible
public class SpringCloudConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(SpringCloudConfigurationClient.class);

    private final SpringCloudConfigClient springCloudConfigClient;
    private final SpringCloudClientConfiguration springCloudConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private ExecutorService executionService;

    /**
     * @param springCloudConfigClient  The Spring Cloud client
     * @param springCloudConfiguration The Spring Cloud configuration
     * @param applicationConfiguration The application configuration
     * @param executionService         The executor service to use
     */
    protected SpringCloudConfigurationClient(SpringCloudConfigClient springCloudConfigClient,
                                             SpringCloudClientConfiguration springCloudConfiguration,
                                             ApplicationConfiguration applicationConfiguration,
                                             @Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {

        this.springCloudConfigClient = springCloudConfigClient;
        this.springCloudConfiguration = springCloudConfiguration;
        this.applicationConfiguration = applicationConfiguration;
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
            String profiles = StringUtils.trimToNull(String.join(",", activeNames));

            if (LOG.isDebugEnabled()) {
                LOG.debug("Spring Cloud Config Active: {}", springCloudConfiguration.getUri());
                LOG.debug("Application Name: {}, Application Profiles: {}, label: {}", applicationName, profiles,
                         springCloudConfiguration.getLabel());
            }

            Publisher<ConfigServerResponse> responsePublisher =
                    springCloudConfiguration.getLabel() == null ?
                    springCloudConfigClient.readValues(applicationName, profiles) :
                    springCloudConfigClient.readValues(applicationName,
                            profiles, springCloudConfiguration.getLabel());

            Flowable<PropertySource> configurationValues = Flowable.fromPublisher(responsePublisher)
                    .onErrorResumeNext(throwable -> {
                        if (throwable instanceof HttpClientResponseException) {
                            HttpClientResponseException httpClientResponseException = (HttpClientResponseException) throwable;
                            if (httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND) {
                                if (springCloudConfiguration.isFailFast()) {
                                    return Flowable.error(
                                        new ConfigurationException("Could not locate PropertySource and the fail fast property is set", throwable));
                                } else {
                                    return Flowable.empty();
                                }
                            }
                        }
                        return Flowable.error(new ConfigurationException("Error reading distributed configuration from Spring Cloud: " + throwable.getMessage(), throwable));
                    })
                    .flatMap(response -> {
                        List<ConfigServerPropertySource> springSources = response.getPropertySources();
                        if (CollectionUtils.isEmpty(springSources)) {
                            return Flowable.empty();
                        }
                        int baseOrder = EnvironmentPropertySource.POSITION + 100;
                        List<PropertySource> propertySources = new ArrayList<>(springSources.size());
                        //spring returns the property sources with the highest precedence first
                        //reverse order and increment priority so the last (after reversed) item will
                        //have the highest order
                        for (int i = springSources.size() - 1; i >= 0; i--) {
                            ConfigServerPropertySource springSource = springSources.get(i);
                            propertySources.add(PropertySource.of(springSource.getName(), springSource.getSource(), ++baseOrder));
                        }

                        return Flowable.fromIterable(propertySources);
                    });

            if (executionService != null) {
                return configurationValues.subscribeOn(Schedulers.from(executionService));
            } else {
                return configurationValues;
            }
        }
    }

    @Override
    public final String getDescription() {
        return io.micronaut.discovery.spring.config.client.SpringCloudConfigClient.CLIENT_DESCRIPTION;
    }

}
