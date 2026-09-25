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
import io.micronaut.core.util.NativeImageUtils;
import org.jspecify.annotations.Nullable;
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
import java.util.function.Consumer;
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
    @Nullable
    private final ServiceIndex index;

    public ServiceScanner(ClassLoader classLoader, String serviceName, Predicate<String> lineCondition, Function<String, S> transformer) {
        this(classLoader, serviceName, lineCondition, transformer, ServiceIndex.find(classLoader));
    }

    /**
     * @param classLoader   The class loader
     * @param serviceName   The name of the service type
     * @param lineCondition The condition tested on the service names
     * @param transformer   The transformer of the service names
     * @param index         The service index that applies to the class loader, or null to scan the class path
     */
    ServiceScanner(ClassLoader classLoader, String serviceName, Predicate<String> lineCondition, Function<String, S> transformer, @Nullable ServiceIndex index) {
        this.classLoader = classLoader;
        this.serviceName = serviceName;
        this.lineCondition = lineCondition;
        this.transformer = transformer;
        this.index = index;
    }

    static ServiceScanner.@Nullable ExclusiveStaticServiceDefinitions findStaticServiceDefinitions() {
        if (NativeImageUtils.hasImageSingletons()) {
            return ImageSingletons.contains(ExclusiveStaticServiceDefinitions.class) ? ImageSingletons.lookup(ExclusiveStaticServiceDefinitions.class) : null;
        } else {
            return null;
        }
    }

    /**
     * Reads the service names listed by a {@code META-INF/services} file, the way the scan reads them.
     *
     * @param url The URL of the file
     * @return The names
     */
    static Set<String> readStandardServiceNames(URL url) {
        return UrlServicesLoader.computeStandardServiceTypeNames(url, name -> true);
    }

    SoftServiceLoader.ServiceCollector<S> createCollector() {
        return new SoftServiceLoader.ServiceCollector<>() {

            @Override
            public void collect(Collection<S> values) {
                collect(values::add, true);
            }

            @Override
            public void collect(Collection<S> values, boolean allowFork) {
                collect(values::add, allowFork);
            }

            @Override
            public void collect(Consumer<? super S> consumer) {
                collect(consumer, true);
            }

            private void collect(Consumer<? super S> consumer, boolean allowFork) {
                boolean fork = allowFork && ForkJoinPool.getCommonPoolParallelism() > 1;
                ServiceEntriesLoader<S> task = new ServiceEntriesLoader<>(serviceName, classLoader, lineCondition, transformer, fork, index);
                if (fork) {
                    ForkJoinPool.commonPool().invoke(task);
                } else {
                    task.compute();
                }
                task.consume(consumer);
            }
        };
    }

    /**
     * Fork-join recursive services loader.
     *
     * @param <S> The type
     */
    @SuppressWarnings("java:S1948")
    private static final class ServiceEntriesLoader<S> extends RecursiveActionValuesCollector<S> {

        private final List<RecursiveActionValuesCollector<S>> tasks = new ArrayList<>();

        private final String serviceName;
        private final ClassLoader classLoader;
        private final Predicate<String> lineCondition;
        private final Function<String, S> transformer;
        private final boolean fork;
        @Nullable
        private final Set<String> serviceEntries;
        @Nullable
        private final ServiceIndex index;

        private ServiceEntriesLoader(String serviceName, ClassLoader classLoader, Predicate<String> lineCondition, Function<String, S> transformer, boolean fork, @Nullable ServiceIndex index) {
            this.serviceName = serviceName;
            this.classLoader = classLoader;
            this.lineCondition = lineCondition;
            this.transformer = transformer;
            this.index = index;
            final ExclusiveStaticServiceDefinitions ssd = ServiceScanner.findStaticServiceDefinitions();
            if (ssd != null) {
                Map<String, Set<String>> stringSetMap = ssd.serviceTypeMap();
                serviceEntries = stringSetMap.get(serviceName);
                if (serviceEntries == null) {
                    this.fork = fork;
                } else {
                    this.fork = false;
                }
            } else {
                serviceEntries = null;
                this.fork = fork;
            }
        }

        @Override
        protected void compute() {
            try {
                if (serviceEntries != null) {
                    for (String serviceEntry : serviceEntries) {
                        final ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(serviceEntry, transformer);
                        tasks.add(task);
                        if (fork) {
                            task.fork();
                        } else {
                            task.compute();
                        }
                    }
                    return;
                }
                if (index != null) {
                    computeFromIndex(index);
                    return;
                }
                scanStandardServiceConfigs();
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                for (String serviceEntry : serviceEntries) {
                    final ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(serviceEntry, transformer);
                    tasks.add(task);
                    if (fork) {
                        task.fork();
                    } else {
                        task.compute();
                    }
                }
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        /**
         * Loads the services named by the index. A type that is not indexed has its {@code META-INF/services} files
         * scanned, while its {@code META-INF/micronaut} entries always come from the index, which lists all of them.
         * The tasks are forked as for a scan, and the condition is tested on every name of the index.
         *
         * @param index The index
         * @throws IOException If the {@code META-INF/services} files of a type that is not indexed cannot be found
         */
        private void computeFromIndex(ServiceIndex index) throws IOException {
            List<String> standardNames = index.standardServices().get(serviceName);
            if (standardNames == null) {
                scanStandardServiceConfigs();
            } else {
                loadIndexedEntries(standardNames);
            }
            loadIndexedEntries(index.micronautServices().getOrDefault(serviceName, Set.of()));
        }

        private void loadIndexedEntries(Collection<String> names) {
            for (String name : names) {
                if (lineCondition.test(name)) {
                    ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(name, transformer);
                    tasks.add(task);
                    if (fork) {
                        task.fork();
                    } else {
                        task.compute();
                    }
                }
            }
        }

        private void scanStandardServiceConfigs() throws IOException {
            Enumeration<URL> serviceConfigs = classLoader.getResources(SoftServiceLoader.META_INF_SERVICES + '/' + serviceName);
            while (serviceConfigs.hasMoreElements()) {
                URL url = serviceConfigs.nextElement();
                UrlServicesLoader<S> task = new UrlServicesLoader<>(url, lineCondition, transformer, fork);
                tasks.add(task);
                if (fork) {
                    task.fork();
                } else {
                    task.compute();
                }
            }
        }

        @Override
        public void consume(Consumer<? super S> consumer) {
            for (RecursiveActionValuesCollector<S> task : tasks) {
                if (fork) {
                    task.join();
                }
                task.consume(consumer);
            }
        }

    }

    /**
     * Reads URL, parses the file and produces sub-tasks to initialize the entry.
     *
     * @param <S> The type
     */
    @SuppressWarnings("java:S1948")
    private static final class UrlServicesLoader<S> extends RecursiveActionValuesCollector<S> {

        private final URL url;
        private final List<ServiceInstanceLoader<S>> tasks = new ArrayList<>();
        private final Predicate<String> lineCondition;
        private final Function<String, S> transformer;
        private final boolean fork;

        public UrlServicesLoader(URL url, Predicate<String> lineCondition, Function<String, S> transformer, boolean fork) {
            this.url = url;
            this.lineCondition = lineCondition;
            this.transformer = transformer;
            this.fork = fork;
        }

        @Override
        @SuppressWarnings({"java:S3776", "java:S135"})
        protected void compute() {
            for (String typeName : computeStandardServiceTypeNames(url, lineCondition)) {
                ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(typeName, transformer);
                tasks.add(task);
                if (fork) {
                    task.fork();
                } else {
                    task.compute();
                }
            }
        }

        @Override
        public void consume(Consumer<? super S> consumer) {
            for (ServiceInstanceLoader<S> task : tasks) {
                if (fork) {
                    task.join();
                }
                task.consume(consumer);
            }
        }

        @SuppressWarnings("java:S3398")
        private static Set<String> computeStandardServiceTypeNames(URL url, Predicate<String> lineCondition) {
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

    }

    /**
     * Initializes and filters the entry.
     *
     * @param <S> The type
     */
    @SuppressWarnings("java:S1948")
    private static final class ServiceInstanceLoader<S> extends RecursiveActionValuesCollector<S> {

        private final String className;
        @Nullable
        private S result;
        @Nullable
        private Throwable throwable;
        private final Function<String, S> transformer;

        public ServiceInstanceLoader(String className, Function<String, S> transformer) {
            this.className = className;
            this.transformer = transformer;
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
        public void consume(Consumer<? super S> consumer) {
            if (throwable != null) {
                throw new SoftServiceLoader.ServiceLoadingException("Failed to load a service: " + throwable.getMessage(), throwable);
            }
            if (result != null) {
                consumer.accept(result);
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
         * Consume loaded value.
         *
         * @param consumer The consumer
         */
        public abstract void consume(Consumer<? super S> consumer);

    }

    @Internal
    record ExclusiveStaticServiceDefinitions(Map<String, Set<String>> serviceTypeMap) {
        ExclusiveStaticServiceDefinitions {
            if (serviceTypeMap == null) {
                serviceTypeMap = new HashMap<>();
            }
        }
    }

}
