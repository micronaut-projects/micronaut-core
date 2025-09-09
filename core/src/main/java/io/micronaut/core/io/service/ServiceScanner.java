/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Wrapper class for the tasks required to find services of a particular type.
 *
 * @param <S> service type
 */
@Internal
final class ServiceScanner<S> {
    private final ClassLoader classLoader;
    private final String serviceName;
    private final Predicate<String> lineCondition;
    private final Function<String, S> transformer;

    public ServiceScanner(ClassLoader classLoader, String serviceName, Predicate<String> lineCondition, Function<String, S> transformer) {
        this.classLoader = classLoader;
        this.serviceName = serviceName;
        this.lineCondition = lineCondition;
        this.transformer = transformer;
    }

    @Nullable
    static StaticServiceDefinitions findStaticServiceDefinitions() {
        if (hasImageSingletons()) {
            return ImageSingletons.contains(StaticServiceDefinitions.class) ? ImageSingletons.lookup(StaticServiceDefinitions.class) : null;
        } else {
            return null;
        }
    }

    @SuppressWarnings("java:S1181")
    private static boolean hasImageSingletons() {
        try {
            //noinspection ConstantValue
            return ImageSingletons.class != null;
        } catch (Throwable e) {
            // not present or not a GraalVM JDK
            return false;
        }
    }

    @SuppressWarnings("java:S3398")
    private Set<String> computeStandardServiceTypeNames(URL url) {
        Set<String> typeNames = new HashSet<>();
        try {
            URLConnection uc = url.openConnection();
            uc.setUseCaches(false);
            try (InputStream is = uc.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    if (!lineCondition.test(line)) {
                        continue;
                    }
                    int i = line.indexOf('#');
                    if (i > -1) {
                        line = line.substring(0, i);
                    }
                    typeNames.add(line);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // ignore, can't do anything here and can't log because class used in compiler
        }
        return typeNames;
    }

    private Enumeration<URL> findStandardServiceConfigs() throws IOException {
        return classLoader.getResources(SoftServiceLoader.META_INF_SERVICES + '/' + serviceName);
    }

    /**
     * Fork-join recursive services loader.
     */
    @SuppressWarnings("java:S1948")
    final class DefaultServiceCollector extends RecursiveActionValuesCollector<S> implements SoftServiceLoader.ServiceCollector<S> {

        private final List<RecursiveActionValuesCollector<S>> tasks = new ArrayList<>();

        @Override
        protected void compute() {
            try {
                Enumeration<URL> serviceConfigs = findStandardServiceConfigs();
                while (serviceConfigs.hasMoreElements()) {
                    URL url = serviceConfigs.nextElement();
                    UrlServicesLoader task = new UrlServicesLoader(url);
                    tasks.add(task);
                    task.fork();
                }
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                for (String serviceEntry : serviceEntries) {
                    final ServiceInstanceLoader task = new ServiceInstanceLoader(serviceEntry);
                    tasks.add(task);
                    task.fork();
                }
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        @Override
        public void collect(Collection<S> values) {
            ForkJoinPool.commonPool().invoke(this);
            for (RecursiveActionValuesCollector<S> task : tasks) {
                task.join();
                task.collect(values);
            }
        }

        @Override
        public void collect(Collection<S> values, boolean allowFork) {
            if (allowFork && ForkJoinPool.getCommonPoolParallelism() > 1) {
                ForkJoinPool.commonPool().invoke(this);
                for (RecursiveActionValuesCollector<S> task : tasks) {
                    task.join();
                    task.collect(values);
                }
            } else {
                try {
                    Enumeration<URL> serviceConfigs = findStandardServiceConfigs();
                    while (serviceConfigs.hasMoreElements()) {
                        URL url = serviceConfigs.nextElement();
                        for (String typeName : computeStandardServiceTypeNames(url)) {
                            S val = transformer.apply(typeName);
                            if (val != null) {
                                values.add(val);
                            }
                        }
                    }
                    Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                    for (String serviceEntry : serviceEntries) {
                        S val = transformer.apply(serviceEntry);
                        if (val != null) {
                            values.add(val);
                        }
                    }
                } catch (IOException e) {
                    throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
                }
            }
        }
    }

    /**
     * Reads URL, parses the file and produces sub-tasks to initialize the entry.
     */
    @SuppressWarnings("java:S1948")
    private final class UrlServicesLoader extends RecursiveActionValuesCollector<S> {

        private final URL url;
        private final List<ServiceInstanceLoader> tasks = new ArrayList<>();

        public UrlServicesLoader(URL url) {
            this.url = url;
        }

        @Override
        @SuppressWarnings({"java:S3776", "java:S135"})
        protected void compute() {
            for (String typeName : computeStandardServiceTypeNames(url)) {
                ServiceInstanceLoader task = new ServiceInstanceLoader(typeName);
                tasks.add(task);
                task.fork();
            }
        }

        @Override
        public void collect(Collection<S> values) {
            for (ServiceInstanceLoader task : tasks) {
                task.join();
                task.collect(values);
            }
        }

    }

    /**
     * Initializes and filters the entry.
     */
    @SuppressWarnings("java:S1948")
    private final class ServiceInstanceLoader extends RecursiveActionValuesCollector<S> {

        private final String className;
        private S result;
        private Throwable throwable;

        public ServiceInstanceLoader(String className) {
            this.className = className;
        }

        @Override
        protected void compute() {
            try {
                result = transformer.apply(className);
            } catch (Throwable e) {
                throwable = e;
            }
        }

        @Override
        public void collect(Collection<S> values) {
            if (throwable != null) {
                throw new SoftServiceLoader.ServiceLoadingException("Failed to load a service: " + throwable.getMessage(), throwable);
            }
            if (result != null && !values.contains(result)) {
                values.add(result);
            }
        }
    }

    /**
     * Abstract recursive action class.
     *
     * @param <S> The type
     */
    private abstract static class RecursiveActionValuesCollector<S> extends RecursiveAction {

        /**
         * Collects loaded values.
         *
         * @param values The values
         */
        public abstract void collect(Collection<S> values);

    }

    @Internal
    record StaticServiceDefinitions(Map<String, Set<String>> serviceTypeMap) {
        StaticServiceDefinitions {
            if (serviceTypeMap == null) {
                serviceTypeMap = new HashMap<>();
            }
        }
    }

}
