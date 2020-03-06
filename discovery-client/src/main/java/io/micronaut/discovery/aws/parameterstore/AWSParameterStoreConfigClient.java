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
package io.micronaut.discovery.aws.parameterstore;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClient;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import io.micronaut.configuration.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration;
import io.micronaut.discovery.client.ClientUtil;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A {@link ConfigurationClient} implementation for AWS ParameterStore.
 *
 * @author Rvanderwerf
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = {AWSSimpleSystemsManagementAsyncClient.class, AWSClientConfiguration.class})
@Requires(env = Environment.AMAZON_EC2)
@Requires(beans = AWSParameterStoreConfiguration.class)
@BootstrapContextCompatible
public class AWSParameterStoreConfigClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(AWSParameterStoreConfiguration.class);
    private final AWSClientConfiguration awsConfiguration;
    private final AWSParameterStoreConfiguration awsParameterStoreConfiguration;
    private final String serviceId;
    private AWSSimpleSystemsManagementAsync client;
    private ExecutorService executorService;


    /**
     * Initialize @Singleton.
     *
     * @param awsConfiguration                    your aws configuration credentials
     * @param awsParameterStoreConfiguration      configuration for the parameter store
     * @param applicationConfiguration            the application configuration
     * @param route53ClientDiscoveryConfiguration configuration for route53 service discovery, if you are using this (not required)
     * @throws SdkClientException If the aws sdk client could not be created
     */
    AWSParameterStoreConfigClient(
            AWSClientConfiguration awsConfiguration,
            AWSParameterStoreConfiguration awsParameterStoreConfiguration,
            ApplicationConfiguration applicationConfiguration,
            @Nullable Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration) throws SdkClientException {
        this.awsConfiguration = awsConfiguration;
        this.awsParameterStoreConfiguration = awsParameterStoreConfiguration;
        this.client = AWSSimpleSystemsManagementAsyncClient.asyncBuilder().withClientConfiguration(awsConfiguration.getClientConfiguration()).build();
        this.serviceId = (route53ClientDiscoveryConfiguration != null ? route53ClientDiscoveryConfiguration.getServiceId() : applicationConfiguration.getName()).orElse(null);
    }


    /**
     * Get your PropertySources from AWS Parameter Store.
     * Property sources are expected to be set up in this way:
     * \ configuration \ micronaut \ environment name \ app name \
     * If you want to change the base \configuration\micronaut set the property aws.system-manager.parameterStore.rootHierarchyPath
     *
     * @param environment The environment
     * @return property source objects by environment.
     */
    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!awsParameterStoreConfiguration.isEnabled()) {
            return Flowable.empty();
        }
        List<String> activeNames = new ArrayList<>(environment.getActiveNames());
        Optional<String> serviceId = Optional.ofNullable(this.serviceId);


        String path = awsParameterStoreConfiguration.getRootHierarchyPath();
        if (!path.endsWith("/")) {
            path += "/";
        }

        String pathPrefix = path.substring(1);
        String commonConfigPath = path + Environment.DEFAULT_NAME;
        String commonPrefix = commonConfigPath.substring(1);
        final boolean hasApplicationSpecificConfig = serviceId.isPresent();
        String applicationSpecificPath = hasApplicationSpecificConfig ? path + serviceId.get() : null;
        String applicationPrefix = hasApplicationSpecificConfig ? applicationSpecificPath.substring(1) : null;

        Flowable<ParametersWithBasePath> configurationValues = Flowable.fromPublisher(getParametersRecursive(commonConfigPath));

        if (hasApplicationSpecificConfig) {
            configurationValues = Flowable.concat(
                    configurationValues,
                    Flowable.fromPublisher(getParametersRecursive(applicationSpecificPath)));
        }
        if (!activeNames.isEmpty()) {
            // look for the environment configs since we can't wildcard partial paths on aws
            for (String activeName : activeNames) {
                String environmentSpecificPath = commonConfigPath + "_" + activeName;
                configurationValues = Flowable.concat(
                        configurationValues,
                        Flowable.fromPublisher(getParametersRecursive(environmentSpecificPath)));

                if (applicationSpecificPath != null) {
                    String appEnvironmentSpecificPath = applicationSpecificPath + "_" + activeName;
                    configurationValues = Flowable.concat(
                            configurationValues,
                            Flowable.fromPublisher(getParametersRecursive(appEnvironmentSpecificPath)));
                }
            }

        }

        int basePriority = EnvironmentPropertySource.POSITION + 100;
        int envBasePriority = basePriority + 50;

        final Map<String, LocalSource> propertySources = new HashMap<>();
        final Flowable<ParametersWithBasePath> parameterFlowable = configurationValues;
        Flowable<PropertySource> propertySourceFlowable = Flowable.create(emitter -> {
            parameterFlowable.subscribe(
                    parametersWithBasePath -> {
                        if (parametersWithBasePath.parameters.isEmpty()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("parameterBasePath={} no parameters found", parametersWithBasePath.basePath);
                            }
                            return;
                        }
                        String key = parametersWithBasePath.basePath;
                        boolean isCommonConfigKey = key.substring(1).startsWith(commonPrefix);
                        boolean isApplicationSpecificConfigKey = hasApplicationSpecificConfig && key.substring(1).startsWith(applicationPrefix);
                        boolean validKey = isCommonConfigKey || isApplicationSpecificConfigKey;
                        if (validKey) {
                            String fullName = key.substring(pathPrefix.length() + 1);
                            Set<String> propertySourceNames = calcPropertySourceNames(fullName, activeNames);
                            Map<String, Object> properties = convertParametersToMap(parametersWithBasePath);
                            if (LOG.isTraceEnabled()) {
                                properties.keySet().iterator().forEachRemaining(param -> LOG.trace("param found: parameterBasePath={} parameter={}", parametersWithBasePath.basePath, param));
                            }
                            for (String propertySourceName : propertySourceNames) {
                                String envName = ClientUtil.resolveEnvironment(propertySourceName, activeNames);
                                LocalSource localSource = propertySources.computeIfAbsent(propertySourceName, s -> new LocalSource(isApplicationSpecificConfigKey, envName, propertySourceName));
                                localSource.putAll(properties);
                            }
                        }
                    },
                    emitter::onError,
                    () -> {
                        for (LocalSource localSource : propertySources.values()) {
                            int priority;
                            if (localSource.environment != null) {
                                priority = envBasePriority + (activeNames.indexOf(localSource.environment) * 2);
                            } else {
                                priority = basePriority + 1;
                            }
                            if (localSource.appSpecific) {
                                priority++;
                            }
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("source={} got priority={}", localSource.name, priority);
                            }
                            emitter.onNext(PropertySource.of(Route53ClientDiscoveryConfiguration.SERVICE_ID + '-' + localSource.name, localSource.values, priority));
                        }
                        emitter.onComplete();
                    });
        }, BackpressureStrategy.ERROR);

        return propertySourceFlowable.onErrorResumeNext(AWSParameterStoreConfigClient::onPropertySourceError);

    }

    /**
     * Description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "AWS Parameter Store";
    }

    private static Publisher<? extends PropertySource> onPropertySourceError(Throwable throwable) {
        if (throwable instanceof ConfigurationException) {
            return Flowable.error(throwable);
        } else {
            return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
        }
    }

    private static Publisher<? extends GetParametersResult> onGetParametersError(Throwable throwable) {
        if (throwable instanceof SdkClientException) {
            return Flowable.error(throwable);
        } else {
            return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
        }
    }

    private static Publisher<? extends GetParametersByPathResult> onGetParametersByPathResult(Throwable throwable) {
        if (throwable instanceof SdkClientException) {
            return Flowable.error(throwable);
        } else {
            return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
        }
    }

    private Publisher<ParametersWithBasePath> getParametersRecursive(String path) {
        return Flowable.concat(
                Flowable.fromPublisher(getParameters(path)).map(r -> new ParametersWithBasePath(path, r.getParameters())),
                Flowable.fromPublisher(getHierarchy(path, new ArrayList<>(), null)).map(r -> new ParametersWithBasePath(path, r))
        );
    }

    private Flowable<List<Parameter>> getHierarchy(final String path, final List<Parameter> parameters, final String nextToken) {
        Flowable<GetParametersByPathResult> paramPage = Flowable.fromPublisher(getHierarchy(path, nextToken));

        return paramPage.flatMap(getParametersByPathResult -> {
            List<Parameter> params = getParametersByPathResult.getParameters();

            if (getParametersByPathResult.getNextToken() != null) {
                return Flowable.merge(
                        Flowable.just(parameters),
                        getHierarchy(path, params, getParametersByPathResult.getNextToken())
                    );
            } else {
                return Flowable.merge(
                        Flowable.just(parameters),
                        Flowable.just(params)
                );
            }
        });
    }

    /**
     * Gets the Parameter hierarchy from AWS parameter store.
     * Please note this only returns something if the current node has children and will not return itself.
     *
     *
     * @param path path based on the parameter names PRIORITY_TOP.e. /config/application/.*
     * @param nextToken token to paginate in the resultset from AWS
     * @return Publisher for GetParametersByPathResult
     */
    private Publisher<GetParametersByPathResult> getHierarchy(String path, String nextToken) {
        LOG.trace("Retrieving parameters by path {}, pagination requested: {}", path, nextToken != null);
        GetParametersByPathRequest getRequest = new GetParametersByPathRequest()
                .withWithDecryption(awsParameterStoreConfiguration.getUseSecureParameters())
                .withPath(path)
                .withRecursive(true)
                .withNextToken(nextToken);

        Future<GetParametersByPathResult> future = client.getParametersByPathAsync(getRequest);

        Flowable<GetParametersByPathResult> invokeFlowable;
        if (executorService != null) {
            invokeFlowable = Flowable.fromFuture(future, Schedulers.from(executorService));
        } else {
            invokeFlowable = Flowable.fromFuture(future);
        }

        return invokeFlowable.onErrorResumeNext(AWSParameterStoreConfigClient::onGetParametersByPathResult);
    }

    /**
     * Gets the parameters from AWS.
     *
     * @param path this is the hierarchy path (via name field) from the property store
     * @return invokeFlowable - converted future from AWS SDK Async
     */
    private Publisher<GetParametersResult> getParameters(String path) {

        GetParametersRequest getRequest = new GetParametersRequest().withWithDecryption(awsParameterStoreConfiguration.getUseSecureParameters()).withNames(path);

        Future<GetParametersResult> future = client.getParametersAsync(getRequest);

        Flowable<GetParametersResult> invokeFlowable;
        if (executorService != null) {
            invokeFlowable = Flowable.fromFuture(future, Schedulers.from(executorService));
        } else {
            invokeFlowable = Flowable.fromFuture(future);
        }

        return invokeFlowable.onErrorResumeNext(AWSParameterStoreConfigClient::onGetParametersError);
    }

    /**
     * Execution service to make call to AWS.
     *
     * @param executorService ExecutorService
     */
    @Inject
    void setExecutionService(@Named(TaskExecutors.IO) @Nullable ExecutorService executorService) {
        if (executorService != null) {
            this.executorService = executorService;
        }
    }

    /**
     * Calculates property names to look for.
     *
     * @param prefix      The prefix
     * @param activeNames active environment names
     * @return A set of calculated property names
     */
    private Set<String> calcPropertySourceNames(String prefix, List<String> activeNames) {
        return ClientUtil.calcPropertySourceNames(prefix, activeNames, "_");
    }

    /**
     * Helper class for converting parameters from amazon format to a map.
     *
     * @param parametersWithBasePath parameters with the base path
     * @return map of the results, converted
     */
    private Map<String, Object> convertParametersToMap(ParametersWithBasePath parametersWithBasePath) {
        Map<String, Object> output = new HashMap<>();
        for (Parameter param : parametersWithBasePath.parameters) {
            String key = param.getName().substring(parametersWithBasePath.basePath.length());
            if (key.length() > 1) {
                key = key.substring(1).replace("/", ".");
            }

            if (param.getType().equals("StringList")) {
                String[] items = param.getValue().split(",");
                output.put(key, Arrays.asList(items));
            } else {
                output.put(key, param.getValue());
            }
        }
        return output;
    }

    /**
     * Simple container class to hold the list of parameters and a base path which was used to collect them.
     */
    private static class ParametersWithBasePath {
        private final String basePath;
        private final List<Parameter> parameters;

        public ParametersWithBasePath(String basePath, List<Parameter> parameters) {
            this.basePath = basePath;
            this.parameters = parameters;
        }
    }

    /**
     * A local property source.
     */
    private static class LocalSource {

        private final boolean appSpecific;
        private final String environment;
        private final String name;
        private final Map<String, Object> values = new LinkedHashMap<>();

        LocalSource(boolean appSpecific,
                    String environment,
                    String name) {
            this.appSpecific = appSpecific;
            this.environment = environment;
            this.name = name;
        }

        void put(String key, Object value) {
            this.values.put(key, value);
        }

        void putAll(Map<String, Object> values) {
            this.values.putAll(values);
        }

    }
}
