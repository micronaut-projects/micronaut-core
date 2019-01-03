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

package io.micronaut.discovery.spring.config;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.spring.SpringCloudConfiguration;
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
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * A {@link ConfigurationClient} for Spring Cloud client.
 *
 *  @author Thiago Locatelli
 *  @since 1.0
 */
@Singleton
@RequiresSpringCloudConfig
@Requires(beans = SpringCloudConfigClient.class)
@Requires(property = ConfigurationClient.ENABLED, value = "true", defaultValue = "false")
public class SpringCloudConfigConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(SpringCloudConfigConfigurationClient.class);

    private final SpringCloudConfigClient springCloudConfigClient;
    private final SpringCloudConfiguration springCloudConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private final Environment environment;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();

    private ExecutorService executionService;

    private String uri;

    /**
     * @param springCloudConfigClient   The Spring Cloud client
     * @param springCloudConfiguration  The Spring Cloud configuration
     * @param applicationConfiguration  The application configuration
     * @param environment               The environment
     * @param uri                       The Spring cloud config server endpoing
     */
    public SpringCloudConfigConfigurationClient(SpringCloudConfigClient springCloudConfigClient, SpringCloudConfiguration springCloudConfiguration,
                                                ApplicationConfiguration applicationConfiguration, Environment environment,
                                                @Value(SpringCloudConfiguration.SPRING_CLOUD_CONFIG_ENDPOINT) String uri) {

        this.springCloudConfigClient = springCloudConfigClient;
        this.springCloudConfiguration = springCloudConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.environment = environment;
        this.uri = uri;

        if (environment != null) {
            Collection<PropertySourceLoader> loaders = environment.getPropertySourceLoaders();
            for (PropertySourceLoader loader : loaders) {
                Set<String> extensions = loader.getExtensions();
                for (String extension : extensions) {
                    loaderByFormatMap.put(extension, loader);
                }
            }
        }
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!springCloudConfiguration.getConfiguration().isEnabled()) {
            return Flowable.empty();
        }

        String applicationName = applicationConfiguration.getName().get();
        Set<String> activeNames = environment.getActiveNames();
        String profiles = activeNames.stream().collect(Collectors.joining(","));

        if (StringUtils.isEmpty(profiles)) {
            profiles = Environment.DEVELOPMENT;
        }

        LOG.info("Config Server endpoint: {}", uri);
        LOG.info("Application name: {}, application profiles: {}", applicationName, profiles);

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

        Flowable<PropertySource> propertySourceFlowable = configurationValues.flatMap(env -> Flowable.create(emitter -> {

            if (CollectionUtils.isEmpty(env.getPropertySources())) {
                emitter.onComplete();
            } else {
                int priority = Integer.MAX_VALUE;
                for (ConfigServerPropertySource propertySource : env.getPropertySources()) {
                    LOG.info("Property source entry: {}", propertySource.getName());
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

    @Override
    public String getDescription() {
        return io.micronaut.discovery.spring.config.client.SpringCloudConfigClient.CLIENT_DESCRIPTION;
    }

    /**
     * @param executionService The execution service
     */
    @Inject
    void setExecutionService(@Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {
        if (executionService != null) {
            this.executionService = executionService;
        }
    }

}
