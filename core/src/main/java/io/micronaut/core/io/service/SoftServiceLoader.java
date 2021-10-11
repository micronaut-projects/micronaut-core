/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Predicate;

/**
 * <p>Variation of {@link java.util.ServiceLoader} that allows soft loading and conditional loading of
 * META-INF/services classes.</p>
 *
 * @param <S> The service type
 * @author Graeme Rocher
 * @since 1.0
 */
public final class SoftServiceLoader<S> implements Iterable<ServiceDefinition<S>> {

    public static final String META_INF_SERVICES = "META-INF/services";

    private final Class<S> serviceType;
    private final ClassLoader classLoader;
    private final Map<String, ServiceDefinition<S>> loadedServices = new LinkedHashMap<>();
    private final Iterator<ServiceDefinition<S>> unloadedServices;
    private final Predicate<String> condition;

    private SoftServiceLoader(Class<S> serviceType, ClassLoader classLoader) {
        this(serviceType, classLoader, (String name) -> true);
    }

    private SoftServiceLoader(Class<S> serviceType, ClassLoader classLoader, Predicate<String> condition) {
        this.serviceType = serviceType;
        this.classLoader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
        this.unloadedServices = new ServiceLoaderIterator();
        this.condition = condition == null ? (String name) -> true : condition;
    }

    /**
     * Creates a new {@link SoftServiceLoader} using the thread context loader by default.
     *
     * @param service The service type
     * @param <S>     The service generic type
     * @return A new service loader
     */
    public static <S> SoftServiceLoader<S> load(Class<S> service) {
        return SoftServiceLoader.load(service, SoftServiceLoader.class.getClassLoader());
    }

    /**
     * Creates a new {@link SoftServiceLoader} using the given type and class loader.
     *
     * @param service The service type
     * @param loader  The class loader
     * @param <S>     The service generic type
     * @return A new service loader
     */
    public static <S> SoftServiceLoader<S> load(Class<S> service,
                                                ClassLoader loader) {
        return new SoftServiceLoader<>(service, loader);
    }

    /**
     * Creates a new {@link SoftServiceLoader} using the given type and class loader.
     *
     * @param service   The service type
     * @param loader    The class loader to use
     * @param condition A {@link Predicate} to use to conditionally load the service. The predicate is passed the service class name
     * @param <S>       The service generic type
     * @return A new service loader
     */
    public static <S> SoftServiceLoader<S> load(Class<S> service,
                                                ClassLoader loader,
                                                Predicate<String> condition) {
        return new SoftServiceLoader<>(service, loader, condition);
    }

    /**
     * @return Return the first such instance
     */
    public Optional<ServiceDefinition<S>> first() {
        Iterator<ServiceDefinition<S>> i = iterator();
        if (i.hasNext()) {
            return Optional.of(i.next());
        }
        return Optional.empty();
    }

    /**
     * @param alternative An alternative type to use if the this type is not present
     * @param classLoader The classloader
     * @return Return the first such instance
     */
    public Optional<ServiceDefinition<S>> firstOr(String alternative, ClassLoader classLoader) {
        Iterator<ServiceDefinition<S>> i = iterator();
        if (i.hasNext()) {
            return Optional.of(i.next());
        }

        Optional<Class> alternativeClass = ClassUtils.forName(alternative, classLoader);
        if (alternativeClass.isPresent()) {
            return Optional.of(newService(alternative, alternativeClass));
        }
        return Optional.empty();
    }

    /**
     * Collects all initialized instances.
     *
     * @param values The collection to be populated.
     * @param predicate The predicated to filter the instances or null if not needed.
     */
    public void collectAll(@NonNull Collection<S> values, @Nullable Predicate<S> predicate) {
        ServicesLoader<S> servicesLoader = new ServicesLoader<>(serviceType.getName(), condition, classLoader, predicate);
        ForkJoinPool.commonPool().invoke(servicesLoader);
        servicesLoader.collect(values);
    }

    /**
     * Collects all initialized instances.
     *
     * @param values The collection to be populated.
     */
    public void collectAll(@NonNull Collection<S> values) {
        collectAll(values, null);
    }

    /**
     * @return The iterator
     */
    @Override
    public Iterator<ServiceDefinition<S>> iterator() {
        return new Iterator<ServiceDefinition<S>>() {
            Iterator<ServiceDefinition<S>> loaded = loadedServices.values().iterator();

            @Override
            public boolean hasNext() {
                if (loaded.hasNext()) {
                    return true;
                }
                return unloadedServices.hasNext();
            }

            @Override
            public ServiceDefinition<S> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                if (loaded.hasNext()) {
                    return loaded.next();
                }
                if (unloadedServices.hasNext()) {
                    ServiceDefinition<S> nextService = unloadedServices.next();
                    loadedServices.put(nextService.getName(), nextService);
                    return nextService;
                }
                // should not happen
                throw new Error("Bug in iterator");
            }
        };
    }

    /**
     * @param name        The name
     * @param loadedClass The loaded class
     * @return The service definition
     */
    @SuppressWarnings("unchecked")
    protected ServiceDefinition<S> newService(String name, Optional<Class> loadedClass) {
        return new DefaultServiceDefinition(name, loadedClass);
    }

    /**
     * A service loader iterator implementation.
     */
    private final class ServiceLoaderIterator implements Iterator<ServiceDefinition<S>> {
        private Enumeration<URL> serviceConfigs = null;
        private Iterator<String> unprocessed = null;

        @Override
        public boolean hasNext() {

            if (serviceConfigs == null) {
                String name = serviceType.getName();
                try {
                    serviceConfigs = classLoader.getResources(META_INF_SERVICES + '/' + name);
                } catch (IOException e) {
                    throw new ServiceConfigurationError("Failed to load resources for service: " + name, e);
                }
            }
            while (unprocessed == null || !unprocessed.hasNext()) {
                if (!serviceConfigs.hasMoreElements()) {
                    return false;
                }
                URL url = serviceConfigs.nextElement();
                try {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                        List<String> lines = new LinkedList<>();
                        while (true) {
                            String line = reader.readLine();
                            if (line == null) {
                                break;
                            }
                            if (line.length() == 0 || line.charAt(0) == '#') {
                                continue;
                            }
                            if (!condition.test(line)) {
                                continue;
                            }
                            int i = line.indexOf('#');
                            if (i > -1) {
                                line = line.substring(0, i);
                            }
                            lines.add(line);
                        }
                        unprocessed = lines.iterator();
                    }
                } catch (IOException | UncheckedIOException e) {
                    // ignore, can't do anything here and can't log because class used in compiler
                }
            }
            return unprocessed.hasNext();
        }

        @Override
        public ServiceDefinition<S> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            String nextName = unprocessed.next();
            try {
                final Class<?> loadedClass = Class.forName(nextName, false, classLoader);
                return newService(nextName, Optional.of(loadedClass));
            } catch (NoClassDefFoundError | ClassNotFoundException e) {
                return newService(nextName, Optional.empty());
            }
        }
    }

    /**
     * Fork-join recursive services loader.
     *
     * @param <S> The type
     */
    private static class ServicesLoader<S> extends RecursiveActionValuesCollector<S> {

        private final String serviceName;
        private final Predicate<String> lineCondition;
        private final ClassLoader classLoader;
        private final Predicate<S> predicate;
        private final List<RecursiveActionValuesCollector<S>> tasks = new LinkedList<>();

        public ServicesLoader(String serviceName, Predicate<String> lineCondition, ClassLoader classLoader, Predicate<S> predicate) {
            this.serviceName = serviceName;
            this.lineCondition = lineCondition;
            this.classLoader = classLoader;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                Enumeration<URL> serviceConfigs = classLoader.getResources(META_INF_SERVICES + '/' + serviceName);
                while (serviceConfigs.hasMoreElements()) {
                    URL url = serviceConfigs.nextElement();
                    UrlServicesLoader<S> task = new UrlServicesLoader<>(url, lineCondition, classLoader, predicate);
                    tasks.add(task);
                    task.fork();
                }
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        public void collect(Collection<S> values) {
            for (RecursiveActionValuesCollector<S> task : tasks) {
                task.join();
                task.collect(values);
            }
        }
    }

    /**
     * Reads URL, parses the file and produces sub tasks to initialize the entry.
     *
     * @param <S> The type
     */
    private static class UrlServicesLoader<S> extends RecursiveActionValuesCollector<S> {

        private final URL url;
        private final Predicate<String> lineCondition;
        private final ClassLoader classLoader;
        private final Predicate<S> predicate;
        private final List<ServiceInstanceLoader<S>> tasks = new LinkedList<>();

        public UrlServicesLoader(URL url, Predicate<String> lineCondition, ClassLoader classLoader, Predicate<S> predicate) {
            this.url = url;
            this.lineCondition = lineCondition;
            this.classLoader = classLoader;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        if (line.length() == 0 || line.charAt(0) == '#') {
                            continue;
                        }
                        if (!lineCondition.test(line)) {
                            continue;
                        }
                        int i = line.indexOf('#');
                        if (i > -1) {
                            line = line.substring(0, i);
                        }
                        ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(line, classLoader, predicate);
                        tasks.add(task);
                        task.fork();
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                // ignore, can't do anything here and can't log because class used in compiler
            }
        }

        public void collect(Collection<S> values) {
            for (ServiceInstanceLoader<S> task : tasks) {
                task.join();
                task.collect(values);
            }
        }

    }

    /**
     * Initializes and filters the entry.
     *
     * @param <S> The type
     */
    private static class ServiceInstanceLoader<S> extends RecursiveActionValuesCollector<S> {

        private final String className;
        private final ClassLoader classLoader;
        private final Predicate<S> predicate;
        private S result;
        private Throwable throwable;

        public ServiceInstanceLoader(String className, ClassLoader classLoader, Predicate<S> predicate) {
            this.className = className;
            this.classLoader = classLoader;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                final Class<?> loadedClass = Class.forName(className, false, classLoader);
                result = (S) loadedClass.getDeclaredConstructor().newInstance();
                if (predicate != null && !predicate.test(result)) {
                    result = null;
                }
            } catch (NoClassDefFoundError | ClassNotFoundException e) {
                // Ignore
            } catch (Throwable e) {
                throwable = e;
            }
        }

        public void collect(Collection<S> values) {
            if (throwable != null) {
                throw new RuntimeException("Failed to load a service: " + throwable.getMessage(), throwable);
            }
            if (result != null) {
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
}
