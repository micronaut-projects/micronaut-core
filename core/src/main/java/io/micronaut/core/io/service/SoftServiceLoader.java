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
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.reflect.ClassUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final Map<String, SoftServiceLoader.StaticServiceLoader<?>> STATIC_SERVICES =
            StaticOptimizations.get(Optimizations.class)
                    .map(Optimizations::getServiceLoaders)
                    .orElse(Collections.emptyMap());

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
        this.unloadedServices = STATIC_SERVICES.containsKey(serviceType.getName()) ? new StaticServicesLoaderIterator() : new ServiceLoaderIterator();
        this.condition = condition == null ? (String name) -> true : condition;
    }

    /**
     * Creates a new {@link SoftServiceLoader} using the thread context loader by default.
     *
     * @param service The service type
     * @param <S> The service generic type
     * @return A new service loader
     */
    public static <S> SoftServiceLoader<S> load(Class<S> service) {
        return SoftServiceLoader.load(service, SoftServiceLoader.class.getClassLoader());
    }

    /**
     * Creates a new {@link SoftServiceLoader} using the given type and class loader.
     *
     * @param service The service type
     * @param loader The class loader
     * @param <S> The service generic type
     * @return A new service loader
     */
    public static <S> SoftServiceLoader<S> load(Class<S> service,
                                                ClassLoader loader) {
        return new SoftServiceLoader<>(service, loader);
    }

    /**
     * Creates a new {@link SoftServiceLoader} using the given type and class loader.
     *
     * @param service The service type
     * @param loader The class loader to use
     * @param condition A {@link Predicate} to use to conditionally load the service. The predicate is passed the service class name
     * @param <S> The service generic type
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

        @SuppressWarnings("unchecked") Class<S> alternativeClass = ClassUtils.forName(alternative, classLoader)
                .orElse(null);
        if (alternativeClass != null) {
            return Optional.of(createService(alternative, alternativeClass));
        }
        return Optional.empty();
    }

    /**
     * Collects all initialized instances.
     *
     * @param values The collection to be populated.
     * @param predicate The predicated to filter the instances or null if not needed.
     */
    @SuppressWarnings("unchecked")
    public void collectAll(@NonNull Collection<S> values, @Nullable Predicate<S> predicate) {
        String name = serviceType.getName();
        SoftServiceLoader.StaticServiceLoader<?> serviceLoader = STATIC_SERVICES.get(name);
        if (serviceLoader != null) {
            collectStaticServices(values, predicate, (StaticServiceLoader<S>) serviceLoader);
        } else {
            collectDynamicServices(values, predicate, name);
        }
    }

    private void collectDynamicServices(
            Collection<S> values,
            Predicate<S> predicate,
            String name) {
        ServiceCollector<S> collector = newCollector(name, condition, classLoader, className -> {
            try {
                @SuppressWarnings("unchecked") final Class<S> loadedClass =
                        (Class<S>) Class.forName(className, false, classLoader);
                S result = loadedClass.getDeclaredConstructor().newInstance();
                if (predicate != null && !predicate.test(result)) {
                    return null;
                }
                return result;
            } catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException e) {
                // Ignore
            } catch (Exception e) {
                throw new ServiceLoadingException(e);
            }
            return null;
        });
        collector.collect(values);
    }

    private void collectStaticServices(Collection<S> values, Predicate<S> predicate, StaticServiceLoader<S> loader) {
        values.addAll(loader.load(predicate));
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
    @NonNull
    public Iterator<ServiceDefinition<S>> iterator() {
        return new Iterator<ServiceDefinition<S>>() {
            final Iterator<ServiceDefinition<S>> loaded = loadedServices.values().iterator();

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
                throw new ServiceConfigurationError("Bug in iterator");
            }
        };
    }

    /**
     * @param name The name
     * @param loadedClass The loaded class
     * @return The service definition
     * @deprecated No longer used
     */
    @SuppressWarnings({"unchecked", "ProtectedMemberInFinalClass", "OptionalUsedAsFieldOrParameterType", "java:S3740",
            "java:S1133", "rawtypes"})
    @Deprecated
    protected ServiceDefinition<S> newService(String name, Optional<Class> loadedClass) {
        return new DefaultServiceDefinition<>(name, loadedClass.orElse(null));
    }

    /**
     * @param name The name
     * @param loadedClass The loaded class
     * @return The service definition
     */
    private ServiceDefinition<S> createService(String name, Class<S> loadedClass) {
        return new DefaultServiceDefinition<>(name, loadedClass);
    }

    @SuppressWarnings("java:S3398")
    private static Set<String> computeServiceTypeNames(URI uri, String path) {
        Set<String> typeNames = new HashSet<>();
        IOUtils.eachFile(
                uri, path, currentPath -> {
                    if (Files.isRegularFile(currentPath)) {
                        final String typeName = currentPath.getFileName().toString();
                        typeNames.add(typeName);
                    }
                }
        );
        return typeNames;
    }

    public static <S> ServiceCollector<S> newCollector(String serviceName,
                                                       Predicate<String> lineCondition,
                                                       ClassLoader classLoader,
                                                       Function<String, S> transformer) {
        return new DefaultServiceCollector<>(serviceName, lineCondition, classLoader, transformer);
    }

    /**
     * A {@link ServiceDefinition} implementation that uses a {@link MethodHandles.Lookup} object to find a public constructor.
     *
     * @param <S> The service type
     */
    public static final class StaticDefinition<S> implements ServiceDefinition<S> {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
        private static final MethodType VOID_TYPE = MethodType.methodType(void.class);

        private final String name;
        private final Supplier<S> value;

        private StaticDefinition(String name, Supplier<S> value) {
            this.name = name;
            this.value = value;
        }

        public static <S> StaticDefinition<S> of(String name, Class<S> value) {
            return new StaticDefinition<>(name, () -> doCreate(value));
        }

        public static <S> StaticDefinition<S> of(String name, Supplier<S> value) {
            return new StaticDefinition<>(name, value);
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public S load() {
            return value.get();
        }

        @SuppressWarnings({"unchecked"})
        private static <S> S doCreate(Class<S> clazz) {
            try {
                return (S) LOOKUP.findConstructor(clazz, VOID_TYPE).invoke();
            } catch (Throwable e) {
                throw new ServiceLoadingException(e);
            }
        }
    }

    private final class StaticServicesLoaderIterator implements Iterator<ServiceDefinition<S>> {

        Iterator<StaticDefinition<S>> iterator;

        @SuppressWarnings("unchecked")
        private void ensureIterator() {
            if (iterator == null) {
                StaticServiceLoader<S> staticServiceLoader = (StaticServiceLoader<S>) STATIC_SERVICES.get(serviceType.getName());
                iterator = staticServiceLoader.findAll(s -> condition == null || condition.test(s.getClass().getName()))
                        .iterator();
            }
        }

        @Override
        public boolean hasNext() {
            ensureIterator();
            return iterator.hasNext();
        }

        @Override
        public ServiceDefinition<S> next() {
            ensureIterator();
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return iterator.next();
        }
    }

    /**
     * A service loader iterator implementation.
     */
    private final class ServiceLoaderIterator implements Iterator<ServiceDefinition<S>> {
        private Enumeration<URL> serviceConfigs = null;
        private Iterator<String> unprocessed = null;

        @Override
        @SuppressWarnings({"java:S3776", "java:S135"})
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
                @SuppressWarnings("unchecked")
                final Class<S> loadedClass = (Class<S>) Class.forName(nextName, false, classLoader);
                return createService(nextName, loadedClass);
            } catch (NoClassDefFoundError | ClassNotFoundException e) {
                return createService(nextName, null);
            }
        }
    }

    /**
     * Service collector for loading services of the given type.
     *
     * @param <S> The service type
     */
    public interface ServiceCollector<S> {
        void collect(Collection<S> values);

        default void collect(Consumer<? super S> consumer) {
            List<S> values = new ArrayList<>();
            collect(values);
            values.forEach(e -> {
                if (e != null) {
                    consumer.accept(e);
                }
            });
        }
    }

    /**
     * Fork-join recursive services loader.
     *
     * @param <S> The type
     */
    @SuppressWarnings("java:S1948")
    private static class DefaultServiceCollector<S> extends RecursiveActionValuesCollector<S> implements ServiceCollector<S> {

        private final String serviceName;
        private final Predicate<String> lineCondition;
        private final ClassLoader classLoader;
        private final Function<String, S> transformer;
        private final List<RecursiveActionValuesCollector<S>> tasks = new LinkedList<>();

        public DefaultServiceCollector(String serviceName, Predicate<String> lineCondition, ClassLoader classLoader, Function<String, S> transformer) {
            this.serviceName = serviceName;
            this.lineCondition = lineCondition;
            this.classLoader = classLoader;
            this.transformer = transformer;
        }

        @Override
        protected void compute() {
            try {
                Enumeration<URL> serviceConfigs = classLoader.getResources(META_INF_SERVICES + '/' + serviceName);
                while (serviceConfigs.hasMoreElements()) {
                    URL url = serviceConfigs.nextElement();
                    UrlServicesLoader<S> task = new UrlServicesLoader<>(url, lineCondition, transformer);
                    tasks.add(task);
                    task.fork();
                }
                final String path = "META-INF/micronaut/" + serviceName;
                final Enumeration<URL> micronautResources = classLoader.getResources(path);
                Set<URI> uniqueURIs = new LinkedHashSet<>();
                while (micronautResources.hasMoreElements()) {
                    URL url = micronautResources.nextElement();
                    final URI uri = url.toURI();
                    uniqueURIs.add(uri);
                }

                for (URI uri : uniqueURIs) {
                    String uriStr = uri.toString();
                    // on GraalVM there are spurious extra resources that end with # and then a number
                    // we ignore this extra ones
                    if (!(uriStr.startsWith("resource:") && uriStr.contains("#"))) {
                        final MicronautMetaServicesLoader<S> task = new MicronautMetaServicesLoader<>(uri, path, transformer);
                        tasks.add(task);
                        task.fork();
                    }
                }
            } catch (IOException | URISyntaxException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        public void collect(Collection<S> values) {
            ForkJoinPool.commonPool().invoke(this);
            for (RecursiveActionValuesCollector<S> task : tasks) {
                task.join();
                task.collect(values);
            }
        }
    }

    private static final class MicronautMetaServicesLoader<S> extends RecursiveActionValuesCollector<S> {
        private final URI uri;
        private final transient Function<String, S> transformer;
        private final List<ServiceInstanceLoader<S>> tasks = new LinkedList<>();
        private final String path;

        private MicronautMetaServicesLoader(URI uri, String path, Function<String, S> transformer) {
            this.uri = uri;
            this.path = path;
            this.transformer = transformer;
        }

        @Override
        public void collect(Collection<S> values) {
            for (ServiceInstanceLoader<S> task : tasks) {
                task.join();
                task.collect(values);
            }
        }

        @Override
        @SuppressWarnings("java:S2095")
        protected void compute() {
            Set<String> typeNames = computeServiceTypeNames(uri, path);
            for (String typeName : typeNames) {
                ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(typeName, transformer);
                tasks.add(task);
                task.fork();
            }
        }
    }

    /**
     * Reads URL, parses the file and produces sub tasks to initialize the entry.
     *
     * @param <S> The type
     */
    @SuppressWarnings("java:S1948")
    private static final class UrlServicesLoader<S> extends RecursiveActionValuesCollector<S> {

        private final URL url;
        private final Predicate<String> lineCondition;
        private final Function<String, S> transformer;
        private final List<ServiceInstanceLoader<S>> tasks = new LinkedList<>();

        public UrlServicesLoader(URL url, Predicate<String> lineCondition, Function<String, S> transformer) {
            this.url = url;
            this.lineCondition = lineCondition;
            this.transformer = transformer;
        }

        @Override
        @SuppressWarnings({"java:S3776", "java:S135"})
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
                        ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(line, transformer);
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
    @SuppressWarnings("java:S1948")
    private static final class ServiceInstanceLoader<S> extends RecursiveActionValuesCollector<S> {

        private final String className;
        private final Function<String, S> transformer;
        private S result;
        private Throwable throwable;

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

        public void collect(Collection<S> values) {
            if (throwable != null) {
                throw new ServiceLoadingException("Failed to load a service: " + throwable.getMessage(), throwable);
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

    /**
     * Service loader that uses {@link StaticDefinition}.
     *
     * @param <S> The service type
     */
    public interface StaticServiceLoader<S> {
        Stream<StaticDefinition<S>> findAll(Predicate<String> predicate);

        default List<S> load(Predicate<S> predicate) {
            return load(n -> true, predicate);
        }

        default List<S> load(Predicate<String> condition, Predicate<S> predicate) {
            return findAll(condition)
                    .map(ServiceDefinition::load)
                    .filter(s -> predicate == null || predicate.test(s))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Static optimizations for service loaders.
     */
    public static final class Optimizations {
        private final Map<String, SoftServiceLoader.StaticServiceLoader<?>> serviceLoaders;

        public Optimizations(Map<String, StaticServiceLoader<?>> serviceLoaders) {
            this.serviceLoaders = serviceLoaders;
        }

        @SuppressWarnings("java:S1452")
        public Map<String, StaticServiceLoader<?>> getServiceLoaders() {
            return serviceLoaders;
        }
    }

    /**
     * Exception thrown when a service cannot be loaded.
     */
    private static class ServiceLoadingException extends RuntimeException {
        public ServiceLoadingException(String message, Throwable cause) {
            super(message, cause);
        }

        public ServiceLoadingException(Throwable cause) {
            super(cause);
        }
    }
}
