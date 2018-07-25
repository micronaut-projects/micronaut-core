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

package io.micronaut.discovery.consul.config;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jackson.env.JsonPropertySourceLoader;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * A {@link ConfigurationClient} for Consul.
 */
@Singleton
@RequiresConsul
@Requires(beans = ConsulClient.class)
@Requires(property = ConfigurationClient.ENABLED, value = "true", defaultValue = "false")
public class ConsulConfigurationClient implements ConfigurationClient {

    private final ConsulClient consulClient;
    private final ConsulConfiguration consulConfiguration;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();
    private ExecutorService executionService;

    /**
     * @param consulClient        The consul client
     * @param consulConfiguration The consul configuration
     * @param environment         The environment
     */
    public ConsulConfigurationClient(ConsulClient consulClient, ConsulConfiguration consulConfiguration, Environment environment) {
        this.consulClient = consulClient;
        this.consulConfiguration = consulConfiguration;
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
    public String getDescription() {
        return consulClient.getDescription();
    }

    @SuppressWarnings("MagicNumber")
    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!consulConfiguration.getConfiguration().isEnabled()) {
            return Flowable.empty();
        }

        Set<String> activeNames = environment.getActiveNames();
        Optional<String> serviceId = consulConfiguration.getServiceId();
        ConsulConfiguration.ConsulConfigDiscoveryConfiguration configDiscoveryConfiguration = consulConfiguration.getConfiguration();

        ConfigDiscoveryConfiguration.Format format = configDiscoveryConfiguration.getFormat();
        String path = configDiscoveryConfiguration.getPath().orElse(ConfigDiscoveryConfiguration.DEFAULT_PATH);
        if (!path.endsWith("/")) {
            path += "/";
        }

        String pathPrefix = path;
        String commonConfigPath = path + Environment.DEFAULT_NAME;
        final boolean hasApplicationSpecificConfig = serviceId.isPresent();
        String applicationSpecificPath = hasApplicationSpecificConfig ? path + serviceId.get() : null;

        String dc = configDiscoveryConfiguration.getDatacenter().orElse(null);
        Function<Throwable, Publisher<? extends List<KeyValue>>> errorHandler = throwable -> {
            if (throwable instanceof HttpClientResponseException) {
                HttpClientResponseException httpClientResponseException = (HttpClientResponseException) throwable;
                if (httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND) {
                    return Flowable.empty();
                }
            }
            return Flowable.error(throwable);
        };
        Flowable<List<KeyValue>> configurationValues = Flowable.fromPublisher(consulClient.readValues(commonConfigPath, dc, null, null))
            .onErrorResumeNext(errorHandler);
        if (hasApplicationSpecificConfig) {
            configurationValues = Flowable.concat(
                configurationValues,
                Flowable.fromPublisher(consulClient.readValues(applicationSpecificPath, dc, null, null))
                    .onErrorResumeNext(errorHandler)
            );
        }

        Flowable<PropertySource> propertySourceFlowable = configurationValues.flatMap(keyValues -> Flowable.create(emitter -> {
            if (CollectionUtils.isEmpty(keyValues)) {
                emitter.onComplete();
            } else {
                Map<String, Map<String, Object>> propertySources = new HashMap<>();
                Base64.Decoder base64Decoder = Base64.getDecoder();

                for (KeyValue keyValue : keyValues) {
                    String key = keyValue.getKey();
                    String value = keyValue.getValue();
                    boolean isFolder = key.endsWith("/") && value == null;
                    boolean isCommonConfigKey = key.startsWith(commonConfigPath);
                    boolean isApplicationSpecificConfigKey = hasApplicationSpecificConfig && key.startsWith(applicationSpecificPath);
                    boolean validKey = isCommonConfigKey || isApplicationSpecificConfigKey;
                    if (!isFolder && validKey) {

                        switch (format) {
                            case FILE:
                                String fileName = key.substring(pathPrefix.length());
                                int i = fileName.lastIndexOf('.');
                                if (i > -1) {
                                    String ext = fileName.substring(i + 1, fileName.length());
                                    fileName = fileName.substring(0, i);
                                    PropertySourceLoader propertySourceLoader = resolveLoader(ext);
                                    if (propertySourceLoader != null) {
                                        String propertySourceName = resolvePropertySourceName(Environment.DEFAULT_NAME, fileName, activeNames);
                                        if (hasApplicationSpecificConfig && propertySourceName == null) {
                                            propertySourceName = resolvePropertySourceName(serviceId.get(), fileName, activeNames);
                                        }
                                        if (propertySourceName != null) {
                                            byte[] decoded = base64Decoder.decode(value);
                                            Map<String, Object> properties = propertySourceLoader.read(propertySourceName, decoded);
                                            Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                            values.putAll(properties);
                                        }
                                    }
                                }
                                break;

                            case NATIVE:
                                String property = null;
                                Set<String> propertySourceNames = null;
                                if (isCommonConfigKey) {
                                    property = resolvePropertyName(commonConfigPath, key);
                                    propertySourceNames = resolvePropertySourceNames(pathPrefix, key, activeNames);

                                } else if (isApplicationSpecificConfigKey) {
                                    property = resolvePropertyName(applicationSpecificPath, key);
                                    propertySourceNames = resolvePropertySourceNames(pathPrefix, key, activeNames);
                                }
                                if (property != null && propertySourceNames != null) {
                                    for (String propertySourceName : propertySourceNames) {
                                        Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                        byte[] decoded = base64Decoder.decode(value);
                                        values.put(property, new String(decoded));
                                    }
                                }
                                break;

                            case JSON:
                            case YAML:
                            case PROPERTIES:
                                String fullName = key.substring(pathPrefix.length());
                                if (!fullName.contains("/")) {
                                    propertySourceNames = calcPropertySourceNames(fullName, activeNames);
                                    String formatName = format.name().toLowerCase(Locale.ENGLISH);
                                    PropertySourceLoader propertySourceLoader = resolveLoader(formatName);

                                    if (propertySourceLoader == null) {
                                        emitter.onError(new ConfigurationException("No PropertySourceLoader found for format [" + format + "]. Ensure ConfigurationClient is running within Micronaut container."));
                                        return;
                                    } else {
                                        if (propertySourceLoader.isEnabled()) {
                                            byte[] decoded = base64Decoder.decode(value);
                                            Map<String, Object> properties = propertySourceLoader.read(fullName, decoded);
                                            for (String propertySourceName : propertySourceNames) {
                                                Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                                values.putAll(properties);
                                            }
                                        }
                                    }
                                }
                                break;
                            default:
                                // no-op
                        }
                    }
                }

                for (Map.Entry<String, Map<String, Object>> entry : propertySources.entrySet()) {
                    String name = entry.getKey();
                    int priority = EnvironmentPropertySource.POSITION + (name.endsWith("]") ? 150 : 100);
                    if (hasApplicationSpecificConfig && name.startsWith(serviceId.get())) {
                        priority += 10;
                    }
                    emitter.onNext(PropertySource.of(ConsulClient.SERVICE_ID + '-' + name, entry.getValue(), priority));
                }
                emitter.onComplete();
            }
        }, BackpressureStrategy.ERROR));

        propertySourceFlowable = propertySourceFlowable.onErrorResumeNext(throwable -> {
            if (throwable instanceof ConfigurationException) {
                return Flowable.error(throwable);
            } else {
                return Flowable.error(new ConfigurationException("Error reading distributed configuration from Consul: " + throwable.getMessage(), throwable));
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

    private String resolvePropertySourceName(String rootName, String fileName, Set<String> activeNames) {
        String propertySourceName = null;
        if (fileName.startsWith(rootName)) {
            String envString = fileName.substring(rootName.length());
            if (StringUtils.isEmpty(envString)) {
                propertySourceName = rootName;
            } else if (envString.startsWith("-")) {
                String env = envString.substring(1);
                if (activeNames.contains(env)) {
                    propertySourceName = rootName + '[' + env + ']';
                }
            }
        }
        return propertySourceName;
    }

    private PropertySourceLoader resolveLoader(String formatName) {
        return loaderByFormatMap.computeIfAbsent(formatName, f -> defaultLoader(formatName));
    }

    private PropertySourceLoader defaultLoader(String format) {
        try {
            switch (format) {
                case "json":
                    return new JsonPropertySourceLoader();
                case "properties":
                    return new PropertiesPropertySourceLoader();
                case "yml":
                case "yaml":
                    return new YamlPropertySourceLoader();
                default:
                    // no-op
            }
        } catch (Exception e) {
            // ignore, fallback to exception
        }
        throw new ConfigurationException("Unsupported properties file format: " + format);
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

    private Set<String> resolvePropertySourceNames(String finalPath, String key, Set<String> activeNames) {
        Set<String> propertySourceNames = null;
        String prefix = key.substring(finalPath.length());
        int i = prefix.indexOf('/');
        if (i > -1) {
            prefix = prefix.substring(0, i);
            propertySourceNames = calcPropertySourceNames(prefix, activeNames);
            if (propertySourceNames == null) {
                return null;
            }
        }
        return propertySourceNames;
    }

    private Set<String> calcPropertySourceNames(String prefix, Set<String> activeNames) {
        Set<String> propertySourceNames;
        if (prefix.indexOf(',') > -1) {

            String[] tokens = prefix.split(",");
            if (tokens.length == 1) {
                propertySourceNames = Collections.singleton(tokens[0]);
            } else {
                String name = tokens[0];
                Set<String> newSet = new HashSet<>(tokens.length - 1);
                for (int j = 1; j < tokens.length; j++) {
                    String envName = tokens[j];
                    if (!activeNames.contains(envName)) {
                        return Collections.emptySet();
                    }
                    newSet.add(name + '[' + envName + ']');
                }
                propertySourceNames = newSet;
            }
        } else {
            propertySourceNames = Collections.singleton(prefix);
        }
        return propertySourceNames;
    }

    private String resolvePropertyName(String commonConfigPath, String key) {
        String property = key.substring(commonConfigPath.length());

        if (StringUtils.isNotEmpty(property)) {
            if (property.charAt(0) == '/') {
                property = property.substring(1);
            } else if (property.lastIndexOf('/') > -1) {
                property = property.substring(property.lastIndexOf('/') + 1);
            }
        }
        if (property.indexOf('/') == -1) {
            return property;
        }
        return null;
    }
}
