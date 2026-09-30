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
import io.micronaut.core.optim.StaticOptimizations;
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
    // the name of ServiceIndex, written out because a class literal would load the class
    private static final String SERVICE_INDEX = "io.micronaut.core.io.service.ServiceIndex";

    private final ClassLoader classLoader;
    private final String serviceName;
    private final Predicate<String> lineCondition;
    private final Function<String, S> transformer;
    @Nullable
    private final ServiceIndex index;

    public ServiceScanner(ClassLoader classLoader, String serviceName, Predicate<String> lineCondition, Function<String, S> transformer) {
        this(classLoader, serviceName, lineCondition, transformer, findServiceIndex(classLoader));
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
     * Finds the registered service index that applies to a lookup.
     *
     * <p>The registered index is read on each lookup, and not once: an index that a
     * {@link StaticOptimizations.Loader} registers after a lookup, for example because a loader that runs before it
     * looks a service up, is used from then on. It is read by name and without recording the read, so an
     * application without an index does not load {@link ServiceIndex}, and the read does not make the registration
     * that follows fail.</p>
     *
     * <p>The read does not wait for the loaders once a thread has started to run them: see
     * {@link StaticOptimizations.SetOnce#find(String)}. A lookup can therefore start on a thread of the fork-join
     * pool while a loader waits for that thread, as it does when a loader looks up a service whose constructor looks
     * a service up.</p>
     *
     * <p>A lookup calls this method once, on the thread that starts it, and hands the answer to its fork-join tasks,
     * so that the whole lookup uses one answer: see
     * {@link MicronautMetaServiceLoaderUtils#findMicronautMetaServiceEntries(ClassLoader, String, ServiceIndex)}.</p>
     *
     * @param classLoader The class loader of the lookup
     * @return The index, or null if the class path must be scanned
     * @throws ServiceConfigurationError If the index was validated and does not match the class path
     */
    static @Nullable ServiceIndex findServiceIndex(ClassLoader classLoader) {
        Object registered = StaticOptimizations.SetOnce.find(SERVICE_INDEX);
        if (registered == null) {
            return null;
        }
        return ((ServiceIndex) registered).forLookup(classLoader);
    }

    /**
     * Reads the service names listed by the {@code META-INF/services} files of a type, the way the scan reads them:
     * file by file in the order of the class path, so a name that two files list is there twice.
     *
     * <p>The names of one file come from a {@link HashSet}, as in the scan, so they are in the order that the hash
     * codes of the names give them, not in the order of the file. An index built from this method has the order of
     * the scan only because both iterate the same set of the same names. The tests that compare the two rely on
     * that: they do not show that the order is guaranteed, and this has to be revisited if the scan ever keeps the
     * order of the file.</p>
     *
     * @param classLoader The class loader
     * @param serviceName The name of the service type
     * @return The names
     * @throws IOException If the files cannot be found
     */
    static List<String> readStandardServiceNames(ClassLoader classLoader, String serviceName) throws IOException {
        List<String> names = new ArrayList<>();
        Enumeration<URL> serviceConfigs = classLoader.getResources(SoftServiceLoader.META_INF_SERVICES + '/' + serviceName);
        while (serviceConfigs.hasMoreElements()) {
            names.addAll(UrlServicesLoader.computeStandardServiceTypeNames(serviceConfigs.nextElement(), name -> true));
        }
        return names;
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
                // no index applied when the lookup started, and a task does not ask for it again
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName, null);
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
