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

package io.micronaut.discovery.vault.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 *  A {@link ConfigurationClient} for Vault Configuration.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@Singleton
@BootstrapContextCompatible
public class VaultConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(VaultConfigurationClient.class);
    private static final String DEFAULT_APPLICATION = "application";

    private final VaultConfigHttpClient<?> configHttpClient;
    private final VaultClientConfiguration vaultClientConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private final Environment environment;
    private final ExecutorService executorService;

    /**
     * Default Constructor.
     *
     * @param configHttpClient          The http client
     * @param vaultClientConfiguration  Vault Client Configuration
     * @param applicationConfiguration  The application configuration
     * @param environment               The environment
     * @param executorService           Executor Service
     */
    public VaultConfigurationClient(VaultConfigHttpClient<?> configHttpClient,
                                    VaultClientConfiguration vaultClientConfiguration,
                                    ApplicationConfiguration applicationConfiguration,
                                    Environment environment,
                                    @Named(TaskExecutors.IO) @Nullable ExecutorService executorService) {
        this.configHttpClient = configHttpClient;
        this.vaultClientConfiguration = vaultClientConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.environment = environment;
        this.executorService = executorService;
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!vaultClientConfiguration.getDiscoveryConfiguration().isEnabled()) {
            return Flowable.empty();
        }

        final String applicationName = applicationConfiguration.getName().orElse(null);
        final Set<String> activeNames = environment.getActiveNames();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Vault server endpoint: {}, secret engine version: {}, secret-engine-name: {}",
                    vaultClientConfiguration.getUri(),
                    vaultClientConfiguration.getKvVersion(),
                    vaultClientConfiguration.getSecretEngineName());
            LOG.debug("Application name: {}, application profiles: {}", applicationName, activeNames);
        }

        List<Flowable<PropertySource>> propertySources = new ArrayList<>();

        String token = vaultClientConfiguration.getToken();
        String engine = vaultClientConfiguration.getSecretEngineName();

        buildVaultKeys(applicationName).entrySet().forEach(entry -> {
            propertySources.add(
                    Flowable.fromPublisher(
                            configHttpClient.readConfigurationValues(token, engine, entry.getValue()))
                            .map(data -> PropertySource.of(entry.getValue(), data.getSecrets(), entry.getKey()))
                            .onErrorResumeNext(throwable -> {
                                //TODO: Discover why the below hack is necessary
                                Throwable t = (Throwable) throwable;
                                if (t instanceof HttpClientResponseException) {
                                    if (((HttpClientResponseException) t).getStatus() == HttpStatus.NOT_FOUND) {
                                        if (vaultClientConfiguration.isFailFast()) {
                                            return Flowable.error(new ConfigurationException(
                                                    "Could not locate PropertySource and the fail fast property is set", t));
                                        }
                                    }
                                    return Flowable.empty();
                                }
                                return Flowable.error(new ConfigurationException("Error reading distributed configuration from Vault: " + t.getMessage(), t));
                            })
            );

        });

        Flowable<PropertySource> propertySourceFlowable = Flowable.merge(propertySources);
        if (executorService != null) {
            return propertySourceFlowable.subscribeOn(Schedulers.from(executorService));
        } else {
            return propertySourceFlowable;
        }
    }

    /**
     * Builds the keys used to get vault properties.
     *
     * @param applicationName The application name
     * @return list of vault keys
     */
    protected Map<Integer, String> buildVaultKeys(@Nullable String applicationName) {
        Map<Integer, String> vaultKeys = new HashMap<>();

        int baseOrder = EnvironmentPropertySource.POSITION + 100;
        int envOrder = baseOrder + 200;
        int appIncrement = 10;

        vaultKeys.put(baseOrder, DEFAULT_APPLICATION);
        if (applicationName != null) {
            vaultKeys.put(baseOrder + appIncrement, applicationName);
        }

        List<String> reverseOrderActiveNames = new ArrayList<>(environment.getActiveNames());
        Collections.reverse(reverseOrderActiveNames);
        for (String activeName : reverseOrderActiveNames) {
            vaultKeys.put(envOrder + appIncrement, DEFAULT_APPLICATION + "/" + activeName);
            appIncrement += 10;
            if (applicationName != null) {
                vaultKeys.put(envOrder + appIncrement, applicationName + "/" + activeName);
                appIncrement += 10;
            }
        }
        return vaultKeys;
    }

    @Override
    public String getDescription() {
        return configHttpClient.getDescription();
    }
}
