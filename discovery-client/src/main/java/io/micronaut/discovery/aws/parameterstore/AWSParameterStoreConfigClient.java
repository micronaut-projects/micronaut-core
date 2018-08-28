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

package io.micronaut.discovery.aws.parameterstore;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClient;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import io.micronaut.configuration.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration;
import io.micronaut.discovery.client.ClientUtil;
import io.micronaut.discovery.config.ConfigurationClient;
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
@Requires(env = Environment.AMAZON_EC2)
@Requires(beans = AWSParameterStoreConfiguration.class)
public class AWSParameterStoreConfigClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(AWSParameterStoreConfiguration.class);
    private final int PRIORITY_TOP = 150;
    private final int PRIORITY_DOWN = 100;
    private final int PRIORITY_INCREMENT = 10;
    private final AWSClientConfiguration awsConfiguration;
    private final AWSParameterStoreConfiguration awsParameterStoreConfiguration;
    private final Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration;
    private AWSSimpleSystemsManagementAsync client;
    private ExecutorService executionService;


    /**
     * Initialize @Singleton.
     * @param awsConfiguration your aws configuration credentials
     * @param awsParameterStoreConfiguration configuration for the parameter store
     * @param route53ClientDiscoveryConfiguration configuration for route53 service discovery, if you are using this (not required)
     */
    AWSParameterStoreConfigClient(
            AWSClientConfiguration awsConfiguration,
            AWSParameterStoreConfiguration awsParameterStoreConfiguration,
            Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration) {
        this.awsConfiguration = awsConfiguration;
        this.awsParameterStoreConfiguration = awsParameterStoreConfiguration;

        try {
            this.client = AWSSimpleSystemsManagementAsyncClient.asyncBuilder().withClientConfiguration(awsConfiguration.getClientConfiguration()).build();
        } catch (SdkClientException sce) {
            LOG.warn("Error creating Simple Systems Management client - check your credentials: " + sce.getMessage(), sce);
        }

        this.route53ClientDiscoveryConfiguration = route53ClientDiscoveryConfiguration;
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
        Set<String> activeNames = environment.getActiveNames();
        Optional<String> serviceId = route53ClientDiscoveryConfiguration.getServiceId();


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

        Flowable<GetParametersResult> configurationValues = Flowable.fromPublisher(getParameters(commonConfigPath));

        if (hasApplicationSpecificConfig) {
            configurationValues = Flowable.concat(
                    configurationValues,
                    Flowable.fromPublisher(getParameters(applicationSpecificPath)));
        }
        if (activeNames != null && activeNames.size() > 0) {
            // look for the environment configs since we can't wildcard partial paths on aws
            for (String activeName : activeNames) {
                String environmentSpecificPath = commonConfigPath + "_" + activeName;
                configurationValues = Flowable.concat(
                        configurationValues,
                        Flowable.fromPublisher(getParameters(environmentSpecificPath)));
            }

        }

        Flowable<PropertySource> propertySourceFlowable = configurationValues.flatMap(keyValues -> Flowable.create(emitter -> {
            if (CollectionUtils.isEmpty(keyValues.getParameters())) {
                emitter.onComplete();
            } else {
                Map<String, Map<String, Object>> propertySources = new HashMap<>();

                for (Parameter keyValue : keyValues.getParameters()) {
                    String key = keyValue.getName();
                    String value = keyValue.getValue();
                    boolean isFolder = key.endsWith("/") && value == null;
                    boolean isCommonConfigKey = key.substring(1).startsWith(commonPrefix);
                    boolean isApplicationSpecificConfigKey = hasApplicationSpecificConfig && key.startsWith(applicationPrefix);
                    boolean validKey = isCommonConfigKey || isApplicationSpecificConfigKey;
                    if (!isFolder && validKey) {

                        String fullName = key.substring(pathPrefix.length() + 1);
                        if (!fullName.contains("/")) {
                            Set<String> propertySourceNames = calcPropertySourceNames(fullName, activeNames);
                            String lookupKey = key;
                            if (!lookupKey.startsWith("/")) {
                                lookupKey = "/" + lookupKey;
                            }

                            Flowable<Map<String, Object>> properties = Flowable.fromPublisher(convertParameterHierarchyToMap(keyValues));
                            // if we we have child properties
                            Flowable<GetParametersByPathResult> hierarchy = Flowable.fromPublisher(getHierarchy(lookupKey));
                            Flowable<Map<String, Object>> propertiesHierarchy = Flowable.fromPublisher(convertParameterHierarchyToMap(hierarchy.blockingFirst()));
                            properties = Flowable.concat(
                                    properties,
                                    propertiesHierarchy);

                            for (String propertySourceName : propertySourceNames) {
                                Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                for (Map<String, Object> propMap : properties.toList().blockingGet()) {
                                    values.putAll(propMap);

                                }
                            }

                        }
                    }
                }

                for (Map.Entry<String, Map<String, Object>> entry : propertySources.entrySet()) {
                    String name = entry.getKey();
                    int priority = EnvironmentPropertySource.POSITION + (name.endsWith("]") ? PRIORITY_TOP : PRIORITY_DOWN);
                    if (hasApplicationSpecificConfig && name.startsWith(serviceId.get())) {
                        priority += PRIORITY_INCREMENT;
                    }
                    emitter.onNext(PropertySource.of(Route53ClientDiscoveryConfiguration.SERVICE_ID + '-' + name, entry.getValue(), priority));
                }
                emitter.onComplete();
            }

        }, BackpressureStrategy.ERROR));

        propertySourceFlowable = propertySourceFlowable.onErrorResumeNext(throwable -> {
            if (throwable instanceof ConfigurationException) {
                return Flowable.error(throwable);
            } else {
                return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
            }
        });
        if (executionService != null) {
            return propertySourceFlowable.subscribeOn(io.reactivex.schedulers.Schedulers.from(
                    executionService
            ));
        } else {
            return propertySourceFlowable;
        }

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

    /**
     * Gets the Parameter hierarchy from AWS parameter store.
     * Please note this only returns something if the current node has children and will not return itself.
     * @param path path based on the parameter names PRIORITY_TOP.e. /config/application/.*
     * @return Publisher for GetParametersByPathResult
     */
    private Publisher<GetParametersByPathResult> getHierarchy(String path) {

        GetParametersByPathRequest getRequest = new GetParametersByPathRequest().withWithDecryption(awsParameterStoreConfiguration.getUseSecureParameters()).withPath(path).withRecursive(true);

        Future<GetParametersByPathResult> future = client.getParametersByPathAsync(getRequest);

        Flowable<GetParametersByPathResult> invokeFlowable = Flowable.fromFuture(future, Schedulers.io());


        invokeFlowable = invokeFlowable.onErrorResumeNext(throwable -> {
            if (throwable instanceof SdkClientException) {
                return Flowable.error(throwable);
            } else {
                return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
            }
        });
        return invokeFlowable;

    }


    /**
     * Gets the parameters from AWS.
     * @param path this is the hierarchy path (via name field) from the property store
     * @return invokeFlowable - converted future from AWS SDK Async
     */
    private Publisher<GetParametersResult> getParameters(String path) {

        GetParametersRequest getRequest = new GetParametersRequest().withWithDecryption(awsParameterStoreConfiguration.getUseSecureParameters()).withNames(path);

        Future<GetParametersResult> future = client.getParametersAsync(getRequest);

        Flowable<GetParametersResult> invokeFlowable = Flowable.fromFuture(future, Schedulers.io());


        invokeFlowable = invokeFlowable.onErrorResumeNext(throwable -> {
            if (throwable instanceof SdkClientException) {
                return Flowable.error(throwable);
            } else {
                return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
            }
        });
        return invokeFlowable;

    }

    /**
     * Execution service to make call to AWS.
     * @param executionService ExectorService
     */
    @Inject
    void setExecutionService(@Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {
        if (executionService != null) {
            this.executionService = executionService;
        }
    }

    /**
     * Calculates property names to look for.
     * @param prefix The prefix
     * @param activeNames active environment names
     * @return A set of calculated property names
     */
    private Set<String> calcPropertySourceNames(String prefix, Set<String> activeNames) {
        return ClientUtil.calcPropertySourceNames(prefix, activeNames);
    }

    /**
     * Helper class for converting parameters from amazon format to a map.
     * @param result Result from AWS
     * @return map of the results, converted
     */
    private Publisher<Map<String, Object>> convertParameterHierarchyToMap(GetParametersByPathResult result) {
        return convertParametersToMap(result.getParameters());

    }

    /**
     * Helper class for converting parameters from amazon format to a map.
     * @param result Result from AWS
     * @return map of the results, converted
     */
    private Publisher<Map<String, Object>> convertParameterHierarchyToMap(GetParametersResult result) {
        return convertParametersToMap(result.getParameters());

    }

    /**
     * Helper class for converting parameters from amazon format to a map.
     * @param params Result from AWS
     * @return map of the results, converted
     */
    private Publisher<Map<String, Object>> convertParametersToMap(List<Parameter> params) {

        Map<String, Object> output = new HashMap<>();
        for (Parameter param : params) {
            switch (param.getType()) {
                case "StringList":
                    // command delimited list back into a set/list and exvalues value to be key=value,key=value
                    String[] items = param.getValue().split(",");
                    for (String item : items) {
                        // now split to key value
                        String[] keyValue = item.split("=");
                        output.put(keyValue[0], keyValue[1]);

                    }
                    break;

                case "SecureString":
                    // if decrypt is set to true on request KMS is supposed to decrypt these for us otherwise we get
                    // back an encoded encrypted string of gobbly gook. It uses the default account key unless
                    // one is specified in the config
                    String[] keyValue = param.getValue().split("=");
                    output.put(keyValue[0], keyValue[1]);

                    break;

                default:
                case "String":
                    String[] keyVal = param.getValue().split("=");
                    output.put(keyVal[0], keyVal[1]);
                    break;
            }
        }
        return Publishers.just(output);

    }

}


