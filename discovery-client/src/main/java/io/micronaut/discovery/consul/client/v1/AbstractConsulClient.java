/*
 * Copyright 2018 original authors
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
package io.micronaut.discovery.consul.client.v1;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.*;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.ConsulServiceInstance;
import io.micronaut.http.client.Client;
import io.micronaut.jackson.env.JsonPropertySourceLoader;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Abstract implementation of {@link ConsulClient} that also implements {@link DiscoveryClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = ConsulConfiguration.class)
@Requires(beans = ConsulConfiguration.class)
public abstract class AbstractConsulClient implements ConsulClient, ConfigurationClient {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConsulClient.class);

    private ConsulConfiguration consulConfiguration = new ConsulConfiguration();
    private final Map<ConfigDiscoveryConfiguration.Format, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();
    private ExecutorService executionService;

    @Inject
    public void setConsulConfiguration(ConsulConfiguration consulConfiguration) {
        if (consulConfiguration != null)
            this.consulConfiguration = consulConfiguration;
    }

    @Override
    public String getDescription() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        Set<String> activeNames = environment.getActiveNames();
        Optional<String> serviceId = consulConfiguration.getServiceId();
        ConsulConfiguration.ConsulConfigDiscoveryConfiguration configDiscoveryConfiguration = consulConfiguration.getConfiguration();

        ConfigDiscoveryConfiguration.Format format = configDiscoveryConfiguration.getFormat();
        String path = configDiscoveryConfiguration.getPath().orElse(ConfigDiscoveryConfiguration.DEFAULT_PATH);
        if (!path.endsWith("/")) {
            path += "/";
        }

        String commonConfigPath = path + Environment.DEFAULT_NAME;
        final boolean hasApplicationSpecificConfig = serviceId.isPresent();
        String applicationSpecificPath = hasApplicationSpecificConfig ? path + serviceId.get() : null;

        String dc = configDiscoveryConfiguration.getDatacenter().orElse(null);
        Flowable<List<KeyValue>> configurationValues = Flowable.fromPublisher(readValues(commonConfigPath, dc, null, null));
        if (hasApplicationSpecificConfig) {
            configurationValues = Flowable.merge(
                    configurationValues,
                    Flowable.fromPublisher(readValues(applicationSpecificPath, dc, null, null))
            );
        }
        String finalPath = path;
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
                        byte[] decoded = base64Decoder.decode(value);
                        switch (format) {
                            case NATIVE:
                                String property = null;
                                Set<String> propertySourceNames = null;
                                if (key.startsWith(commonConfigPath)) {
                                    property = resolvePropertyName(commonConfigPath, key);
                                    propertySourceNames = resolvePropertySourceNames(finalPath, key, activeNames);

                                } else if (isApplicationSpecificConfigKey) {
                                    property = resolvePropertyName(applicationSpecificPath, key);
                                    propertySourceNames = resolvePropertySourceNames(finalPath, key, activeNames);
                                }
                                if (property != null && propertySourceNames != null) {
                                    for (String propertySourceName : propertySourceNames) {
                                        Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                        values.put(property, new String(decoded));
                                    }
                                }
                                break;
                            case JSON:
                            case YAML:
                            case PROPERTIES:
                                String fullName = key.substring(finalPath.length());
                                if (!fullName.contains("/")) {
                                    propertySourceNames = calcPropertySourceNames(fullName, activeNames);
                                    PropertySourceLoader propertySourceLoader = loaderByFormatMap.computeIfAbsent(format, f -> defaultLoader(format));


                                    if (propertySourceLoader == null) {
                                        emitter.onError(new ConfigurationException("No PropertySourceLoader found for format [" + format + "]. Ensure ConfigurationClient is running within Micronaut container."));
                                        return;
                                    } else {
                                        if(propertySourceLoader.isEnabled()) {

                                            Map<String, Object> properties = propertySourceLoader.read(fullName, decoded);
                                            for (String propertySourceName : propertySourceNames) {

                                                Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                                values.putAll(properties);
                                            }
                                        }
                                    }
                                }

                                break;
                        }

                    }

                }

                for (Map.Entry<String, Map<String, Object>> entry : propertySources.entrySet()) {
                    String name = entry.getKey();
                    int priority = EnvironmentPropertySource.POSITION + (name.endsWith("]") ? 150 : 100);
                    emitter.onNext(PropertySource.of(ConsulClient.SERVICE_ID + '-' + name, entry.getValue(), priority));
                }
                emitter.onComplete();
            }
        }, BackpressureStrategy.ERROR));

        if(executionService != null) {
            return propertySourceFlowable.subscribeOn(io.reactivex.schedulers.Schedulers.from(
                    executionService
            ));
        }
        else {
            return propertySourceFlowable;
        }
    }

    private PropertySourceLoader defaultLoader(ConfigDiscoveryConfiguration.Format format) {
        try {
            switch (format) {
                case JSON:
                    return new JsonPropertySourceLoader();
                case PROPERTIES:
                    return new PropertiesPropertySourceLoader();
                case YAML:
                    if(ClassUtils.isPresent("org.yaml.snakeyaml.Yaml", YamlPropertySourceLoader.class.getClassLoader())) {
                        return new YamlPropertySourceLoader();
                    }
            }
        } catch (Exception e) {
            // ignore, fallback to exception
        }
        throw new ConfigurationException("Unsupported properties file format: " + format);
    }

    @Inject
    void setExecutionService(@Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {
        if(executionService != null) {
            this.executionService = executionService;
        }
    }

    @Inject
    void setEnvironment(@Nullable Environment environment) {
        if (environment != null) {
            Collection<PropertySourceLoader> loaders = environment.getPropertySourceLoaders();
            for (PropertySourceLoader loader : loaders) {
                Set<String> extensions = loader.getExtensions();
                if (extensions.contains(ConfigDiscoveryConfiguration.Format.JSON.name().toLowerCase(Locale.ENGLISH))) {
                    loaderByFormatMap.put(ConfigDiscoveryConfiguration.Format.JSON, loader);
                } else if (extensions.contains(ConfigDiscoveryConfiguration.Format.YAML.name().toLowerCase(Locale.ENGLISH))) {
                    loaderByFormatMap.put(ConfigDiscoveryConfiguration.Format.YAML, loader);
                }
            }
        }
    }

    private Set<String> resolvePropertySourceNames(String finalPath, String key, Set<String> activeNames) {
        Set<String> propertySourceNames = null;
        String prefix = key.substring(finalPath.length());
        int i = prefix.indexOf('/');
        if (i > -1) {
            prefix = prefix.substring(0, i);
            propertySourceNames = calcPropertySourceNames(prefix, activeNames);
            if (propertySourceNames == null) return null;
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
        if (property.indexOf('/') == -1)
            return property;
        return null;
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        if (SERVICE_ID.equals(serviceId)) {
            return Publishers.just(
                    Collections.singletonList(ServiceInstance.of(SERVICE_ID, consulConfiguration.getHost(), consulConfiguration.getPort()))
            );
        } else {
            ConsulConfiguration.ConsulDiscoveryConfiguration discovery = consulConfiguration.getDiscovery();
            boolean passing = discovery.isPassing();
            Optional<String> datacenter = Optional.ofNullable(discovery.getDatacenters().get(serviceId));
            Optional<String> tag = Optional.ofNullable(discovery.getTags().get(serviceId));
            Optional<String> scheme = Optional.ofNullable(discovery.getSchemes().get(serviceId));

            Publisher<List<HealthEntry>> healthyServicesPublisher = getHealthyServices(serviceId, Optional.of(passing), tag, datacenter);
            return Publishers.map(healthyServicesPublisher, healthEntries -> {
                List<ServiceInstance> serviceInstances = new ArrayList<>();
                for (HealthEntry healthEntry : healthEntries) {
                    serviceInstances.add(new ConsulServiceInstance(healthEntry, scheme.orElse("http")));
                }
                return serviceInstances;
            });
        }
    }

}
