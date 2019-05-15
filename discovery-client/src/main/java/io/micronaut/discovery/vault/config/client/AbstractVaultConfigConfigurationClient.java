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

package io.micronaut.discovery.vault.config.client;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.vault.VaultClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 *  A {@link ConfigurationClient} for Vault Configuration.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
public abstract class AbstractVaultConfigConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVaultConfigConfigurationClient.class);
    private static final String DEFAULT_APPLICATION = "application";

    private VaultClientConfiguration vaultClientConfiguration;
    private ApplicationConfiguration applicationConfiguration;
    private Environment environment;
    private ExecutorService executorService;

    /**
     * Default Constructor.
     *
     * @param vaultClientConfiguration  Vault Client Configuration
     * @param applicationConfiguration  The application configuration
     * @param environment               The environment
     * @param executorService           Executor Service
     */
    public AbstractVaultConfigConfigurationClient(final VaultClientConfiguration vaultClientConfiguration,
                                                  final ApplicationConfiguration applicationConfiguration,
                                                  final Environment environment,
                                                  final ExecutorService executorService) {

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

        final String applicationName = applicationConfiguration.getName().get();
        final Set<String> activeNames = environment.getActiveNames();

        if (LOG.isInfoEnabled()) {
            LOG.info("Vault server endpoint: {}, secret engine version: {}, secret-engine-name: {}",
                    vaultClientConfiguration.getUri(),
                    vaultClientConfiguration.getKvVersion(),
                    vaultClientConfiguration.getSecretEngineName());
            LOG.info("Application name: {}, application profiles: {}", applicationName, activeNames);
        }

        Flowable<PropertySource> propertySourceFlowable = getProperySources();
        if (executorService != null) {
            return propertySourceFlowable.subscribeOn(io.reactivex.schedulers.Schedulers.from(
                    executorService
            ));
        } else {
            return propertySourceFlowable;
        }
    }

    /**
     * Builds the keys used to get vault properties.
     *
     * @return list of vault keys
     */
    protected Set<String> buildVaultKeys() {

        Set<String> vaultKeys = new LinkedHashSet<>(Arrays.asList());
        vaultKeys.add(DEFAULT_APPLICATION);
        vaultKeys.add(getApplicationConfiguration().getName().get());
        List<String> listActiveNames = new ArrayList(getEnvironment().getActiveNames());

        for (int i = listActiveNames.size() - 1; i >= 0;  i--) {
            vaultKeys.add(DEFAULT_APPLICATION + "/" + listActiveNames.get(i));
            vaultKeys.add(getApplicationConfiguration().getName().get() + "/" + listActiveNames.get(i));
        }
        return vaultKeys;
    }

    /**
     * @return The property sources
     */
    protected abstract Flowable<PropertySource> getProperySources();

    /**
     * @return The Application Configuration
     */
    public ApplicationConfiguration getApplicationConfiguration() {
        return this.applicationConfiguration;
    }

    /**
     * @return The Vault Client configuration
     */
    public VaultClientConfiguration getVaultClientConfiguration() {
        return this.vaultClientConfiguration;
    }

    /**
     * @return The Environment
     */
    public Environment getEnvironment() {
        return this.environment;
    }

    /**
     * Object holding a pair of objects.
     *
     * @param <L> Left object
     * @param <R> Right object
     */
    public class Pair<L, R> {

        private L left;
        private R right;

        /**
         * Default Constructor.
         *
         * @param left the left object
         * @param right the right object
         */
        protected Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        /**
         * @return The left object
         */
        public L getLeft() {
            return left;
        }

        /**
         * @return The right object
         */
        public R getRight() {
            return right;
        }

    }
}
