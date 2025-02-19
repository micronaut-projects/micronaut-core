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
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.reflect.ClassUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    static final Map<String, SoftServiceLoader.StaticServiceLoader<?>> STATIC_SERVICES =
            StaticOptimizations.get(Optimizations.class)
                    .map(Optimizations::getServiceLoaders)
                    .orElse(Collections.emptyMap());
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final MethodType VOID_TYPE = MethodType.methodType(void.class);
    private final Class<S> serviceType;
    private final ClassLoader classLoader;
    private Collection<ServiceDefinition<S>> servicesForIterator;
    private final Predicate<String> condition;
    private boolean allowFork = true;

    private SoftServiceLoader(Class<S> serviceType, @Nullable ClassLoader classLoader) {
        this(serviceType, classLoader, (String name) -> true);
    }

    private SoftServiceLoader(Class<S> serviceType, @Nullable ClassLoader classLoader, Predicate<String> condition) {
        this.serviceType = serviceType;
        this.classLoader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
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

    public SoftServiceLoader<S> disableFork() {
        allowFork = false;
        return this;
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
     * Find the first service definition that is {@link ServiceDefinition#isPresent() present}, and
     * then {@link ServiceDefinition#load() load} it.
     *
     * @return Return the first such instance, or {@link Optional#empty()} if there is no definition
     * or none of the definitions are present on the classpath.
     */
    public Optional<S> firstAvailable() {
        for (ServiceDefinition<S> def : this) {
            if (def.isPresent()) {
                return Optional.of(def.load());
            }
        }
        return Optional.empty();
    }

    /**
     * @param alternative An alternative type to use if this type is not present
     * @param classLoader The classloader
     * @return Return the first such instance
     */
    public Optional<ServiceDefinition<S>> firstOr(String alternative, ClassLoader classLoader) {
        Iterator<ServiceDefinition<S>> i = iterator();
        if (i.hasNext()) {
            return Optional.of(i.next());
        }

        @SuppressWarnings("unchecked") Class<S> alternativeClass = (Class<S>) ClassUtils.forName(alternative, classLoader)
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
                // MethodHandler should more performant than the basic reflection
                S result = (S) LOOKUP.findConstructor(loadedClass, VOID_TYPE).invoke();
                if (predicate != null && !predicate.test(result)) {
                    return null;
                }
                return result;
            } catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
                // Ignore
            } catch (Throwable e) {
                throw new ServiceLoadingException(e);
            }
            return null;
        });
        collector.collect(values, allowFork);
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
     * Collects all initialized instances.
     *
     * @return The instances of this service.
     */
    public List<S> collectAll() {
        return collectAll((Predicate<S>) null);
    }

    /**
     * Collects all initialized instances.
     *
     * @param predicate The predicated to filter the instances or null if not needed.
     * @return The instances of this service.
     */
    public List<S> collectAll(Predicate<S> predicate) {
        List<S> values = new ArrayList<>();
        collectAll(values, predicate);
        return values;
    }

    /**
     * @return The iterator
     */
    @Override
    @NonNull
    public Iterator<ServiceDefinition<S>> iterator() {
        if (servicesForIterator == null) {
            if (STATIC_SERVICES.containsKey(serviceType.getName())) {
                @SuppressWarnings("unchecked")
                StaticServiceLoader<S> staticServiceLoader = (StaticServiceLoader<S>) STATIC_SERVICES.get(serviceType.getName());
                this.servicesForIterator = staticServiceLoader.findAll(s -> condition == null || condition.test(s.getClass().getName()))
                    .collect(Collectors.toList());
            } else {
                List<ServiceDefinition<S>> serviceDefinitions = new ArrayList<>();
                newCollector(serviceType.getName(), condition, classLoader, name -> {
                    try {
                        @SuppressWarnings("unchecked")
                        final Class<S> loadedClass = (Class<S>) Class.forName(name, false, classLoader);
                        return createService(name, loadedClass);
                    } catch (NoClassDefFoundError | ClassNotFoundException e) {
                        return createService(name, null);
                    }
                }).collect(serviceDefinitions, false);
                this.servicesForIterator = serviceDefinitions;
            }
        }

        return servicesForIterator.iterator();
    }

    /**
     * @param name The name
     * @param loadedClass The loaded class
     * @return The service definition
     */
    private ServiceDefinition<S> createService(String name, Class<S> loadedClass) {
        return new DefaultServiceDefinition<>(name, loadedClass);
    }

    public static <S> ServiceCollector<S> newCollector(String serviceName,
                                                       Predicate<String> lineCondition,
                                                       ClassLoader classLoader,
                                                       Function<String, S> transformer) {
        return new ServiceScanner<>(classLoader, serviceName, lineCondition, transformer).new DefaultServiceCollector();
    }

    /**
     * A {@link ServiceDefinition} implementation that uses a {@link MethodHandles.Lookup} object to find a public constructor.
     *
     * @param <S> The service type
     */
    public static final class StaticDefinition<S> implements ServiceDefinition<S> {

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

    /**
     * Service collector for loading services of the given type.
     *
     * @param <S> The service type
     */
    public interface ServiceCollector<S> {
        void collect(Collection<S> values);

        default void collect(Collection<S> values, boolean allowFork) {
            collect(values);
        }

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
                    .toList();
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
    static final class ServiceLoadingException extends RuntimeException {
        public ServiceLoadingException(String message, Throwable cause) {
            super(message, cause);
        }

        public ServiceLoadingException(Throwable cause) {
            super(cause);
        }
    }
}
