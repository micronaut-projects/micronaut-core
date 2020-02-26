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
package io.micronaut.discovery.consul.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.client.ClientUtil;
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
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * A {@link ConfigurationClient} for Consul.
 */
@Singleton
@RequiresConsul
@Requires(beans = ConsulClient.class)
@Requires(property = ConfigurationClient.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@BootstrapContextCompatible
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

        List<String> activeNames = new ArrayList<>(environment.getActiveNames());
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

        Scheduler scheduler = null;
        if (executionService != null) {
            scheduler = Schedulers.from(executionService);
        }
        List<Flowable<List<KeyValue>>> keyValueFlowables = new ArrayList<>();

        Function<Throwable, Publisher<? extends List<KeyValue>>> errorHandler = throwable -> {
            if (throwable instanceof HttpClientResponseException) {
                HttpClientResponseException httpClientResponseException = (HttpClientResponseException) throwable;
                if (httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND) {
                    return Flowable.empty();
                }
            }
            return Flowable.error(new ConfigurationException("Error reading distributed configuration from Consul: " + throwable.getMessage(), throwable));
        };

        Flowable<List<KeyValue>> applicationConfig = Flowable.fromPublisher(
                consulClient.readValues(commonConfigPath, dc, null, null))
                .onErrorResumeNext(errorHandler);
        if (scheduler != null) {
            applicationConfig = applicationConfig.subscribeOn(scheduler);
        }
        keyValueFlowables.add(applicationConfig);

        if (hasApplicationSpecificConfig) {
            Flowable<List<KeyValue>> appSpecificConfig = Flowable.fromPublisher(
                    consulClient.readValues(applicationSpecificPath, dc, null, null))
                    .onErrorResumeNext(errorHandler);
            if (scheduler != null) {
                appSpecificConfig = appSpecificConfig.subscribeOn(scheduler);
            }
            keyValueFlowables.add(appSpecificConfig);
        }

        int basePriority = EnvironmentPropertySource.POSITION + 100;
        int envBasePriority = basePriority + 50;

        return Flowable.merge(keyValueFlowables).flatMap(keyValues -> Flowable.create(emitter -> {
            if (CollectionUtils.isEmpty(keyValues)) {
                emitter.onComplete();
            } else {
                Map<String, LocalSource> propertySources = new HashMap<>();
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
                                    String ext = fileName.substring(i + 1);
                                    fileName = fileName.substring(0, i);
                                    PropertySourceLoader propertySourceLoader = resolveLoader(ext);
                                    if (propertySourceLoader != null) {
                                        String propertySourceName = resolvePropertySourceName(Environment.DEFAULT_NAME, fileName, activeNames);
                                        if (hasApplicationSpecificConfig && propertySourceName == null) {
                                            propertySourceName = resolvePropertySourceName(serviceId.get(), fileName, activeNames);
                                        }
                                        if (propertySourceName != null) {
                                            String finalName = propertySourceName;
                                            byte[] decoded = base64Decoder.decode(value);
                                            Map<String, Object> properties = propertySourceLoader.read(propertySourceName, decoded);
                                            String envName = ClientUtil.resolveEnvironment(fileName, activeNames);
                                            LocalSource localSource = propertySources.computeIfAbsent(propertySourceName, s -> new LocalSource(isApplicationSpecificConfigKey, envName, finalName));
                                            localSource.putAll(properties);
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
                                        String envName = ClientUtil.resolveEnvironment(propertySourceName, activeNames);
                                        LocalSource localSource = propertySources.computeIfAbsent(propertySourceName, s -> new LocalSource(isApplicationSpecificConfigKey, envName, propertySourceName));
                                        byte[] decoded = base64Decoder.decode(value);
                                        localSource.put(property, new String(decoded));
                                    }
                                }
                                break;

                            case JSON:
                            case YAML:
                            case PROPERTIES:
                                String fullName = key.substring(pathPrefix.length());
                                if (!fullName.contains("/")) {
                                    propertySourceNames = ClientUtil.calcPropertySourceNames(fullName, activeNames, ",");
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
                                                String envName = ClientUtil.resolveEnvironment(propertySourceName, activeNames);
                                                LocalSource localSource = propertySources.computeIfAbsent(propertySourceName, s -> new LocalSource(isApplicationSpecificConfigKey, envName, propertySourceName));
                                                localSource.putAll(properties);
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

                for (LocalSource localSource: propertySources.values()) {
                    int priority;
                    if (localSource.environment != null) {
                        priority = envBasePriority + (activeNames.indexOf(localSource.environment) * 2);
                    } else {
                        priority = basePriority + 1;
                    }
                    if (localSource.appSpecific) {
                        priority++;
                    }
                    emitter.onNext(PropertySource.of(ConsulClient.SERVICE_ID + '-' + localSource.name, localSource.values, priority));
                }
                emitter.onComplete();
            }
        }, BackpressureStrategy.ERROR));
    }

    private String resolvePropertySourceName(String rootName, String fileName, List<String> activeNames) {
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

    private Set<String> resolvePropertySourceNames(String finalPath, String key, List<String> activeNames) {
        Set<String> propertySourceNames = null;
        String prefix = key.substring(finalPath.length());
        int i = prefix.indexOf('/');
        if (i > -1) {
            prefix = prefix.substring(0, i);
            propertySourceNames = ClientUtil.calcPropertySourceNames(prefix, activeNames, ",");
            if (propertySourceNames == null) {
                return null;
            }
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
