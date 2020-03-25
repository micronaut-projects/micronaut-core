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
package io.micronaut.core.io.service;

import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Variation of {@link SoftServiceLoader} that returns a stream instead of an iterable thus allowing parallel loading etc.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class StreamSoftServiceLoader {

    /**
     * @param serviceType The service type
     * @param classLoader The class loader
     * @param <T>         The type
     * @return A stream
     */
    @SuppressWarnings("unchecked")
    public static <T> Stream<ServiceDefinition<T>> loadParallel(Class<T> serviceType, ClassLoader classLoader) {
        Enumeration<URL> serviceConfigs;
        String name = serviceType.getName();
        try {
            serviceConfigs = classLoader.getResources(SoftServiceLoader.META_INF_SERVICES + '/' + name);
        } catch (IOException e) {
            throw new ServiceConfigurationError("Failed to load resources for service: " + name, e);
        }
        Set<URL> urlSet = CollectionUtils.enumerationToSet(serviceConfigs);

        return urlSet
            .stream()
            .parallel()
            .flatMap(url -> {
                    List<String> lines = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                        String line = reader.readLine();
                        while (line != null) {
                            if (line.length() != 0 && line.charAt(0) != '#') {
                                int i = line.indexOf('#');
                                if (i > -1) {
                                    line = line.substring(0, i);
                                }
                                lines.add(line);
                            }
                            line = reader.readLine();
                        }
                    } catch (IOException e) {
                        throw new ServiceConfigurationError("Failed to load resources for URL: " + url, e);
                    }
                    return lines.stream();
                }
            ).map(serviceName -> {
                Optional<Class> loadedClass = ClassUtils.forName(serviceName, classLoader);
                return new DefaultServiceDefinition(name, loadedClass);
            });
    }

    /**
     * @param serviceType The service type
     * @param classLoader The class loader
     * @param <T>         The type
     * @return A stream with services loaded
     */
    @SuppressWarnings("unchecked")
    public static <T> Stream<T> loadPresentParallel(Class<T> serviceType, ClassLoader classLoader) {
        return loadParallel(serviceType, classLoader)
            .filter(ServiceDefinition::isPresent)
            .map(ServiceDefinition::load);
    }
}
