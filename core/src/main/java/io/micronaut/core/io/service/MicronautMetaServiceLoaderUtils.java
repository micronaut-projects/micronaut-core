/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Predicate;

/**
 * The loader of Micronaut services under META-INF/micronaut/.
 *
 * @author Denis Stepanov
 * @since 4.7
 */
@Internal
public final class MicronautMetaServiceLoaderUtils {

    private static final String MICRONAUT_SERVICES_PATH = "META-INF/micronaut/";

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final MethodType VOID_TYPE = MethodType.methodType(void.class);

    private static volatile CacheEntry cacheEntry;

    /**
     * Find all instantiated Micronaut service entries.
     *
     * @param classLoader  The classloader
     * @param serviceClass The service class
     * @param predicate    The predicate
     * @param <S>          The service type
     * @return the result
     */
    @NonNull
    public static <S> List<S> findMetaMicronautServiceEntries(@NonNull ClassLoader classLoader,
                                                              @NonNull Class<S> serviceClass,
                                                              @Nullable Predicate<S> predicate) {
        SoftServiceLoader.StaticServiceLoader<S> staticServiceLoader = (SoftServiceLoader.StaticServiceLoader<S>) SoftServiceLoader.STATIC_SERVICES.get(serviceClass.getName());
        if (staticServiceLoader != null) {
            return staticServiceLoader.load(predicate);
        }
        return new MicronautServiceCollector<>(classLoader, serviceClass.getName(), predicate)
            .collect(true);
    }

    /**
     * Find Micronaut service entries.
     *
     * @param classLoader The classloader
     * @param serviceName The service name
     * @return The entries
     * @throws IOException
     */
    @NonNull
    public static Set<String> findMicronautMetaServiceEntries(@NonNull ClassLoader classLoader,
                                                              @NonNull String serviceName) throws IOException {
        CacheEntry ce = cacheEntry;
        if (ce == null || ce.classLoader != classLoader) {
            ce = new CacheEntry(classLoader, findAllMicronautMetaServices(classLoader));
            cacheEntry = ce;
        }
        return ce.services.getOrDefault(serviceName, Set.of());
    }

    /**
     * Find all Micronaut services.
     *
     * @param classLoader The classloader
     * @return the all entries
     * @throws IOException
     */
    @NonNull
    public static Map<String, Set<String>> findAllMicronautMetaServices(@NonNull ClassLoader classLoader) throws IOException {
        final ServiceScanner.StaticServiceDefinitions ssd = ServiceScanner.findStaticServiceDefinitions();
        if (ssd != null) {
            return ssd.serviceTypeMap();
        }
        List<URI> resourceDefs = IOUtils.getResources(classLoader, MICRONAUT_SERVICES_PATH);
        if (resourceDefs.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> services = new LinkedHashMap<>();

        FileVisitor<Path> visitor = new FileVisitor<>() {

            private Set<String> definitions;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.endsWith(MICRONAUT_SERVICES_PATH)) {
                    return FileVisitResult.CONTINUE;
                }
                String serviceName = dir.getFileName().toString();
                definitions = services.get(serviceName);
                if (definitions == null) {
                    definitions = new LinkedHashSet<>();
                    services.put(serviceName, definitions);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path currentPath, BasicFileAttributes attrs) throws IOException {
                if (Files.isHidden(currentPath)) {
                    return FileVisitResult.CONTINUE;
                }
                Path fileName = currentPath.getFileName();
                if (fileName.startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }
                definitions.add(fileName.toString());
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        };

        List<Closeable> toClose = new ArrayList<>();
        try {
            for (URI uri : resourceDefs) {
                Path myPath = IOUtils.resolvePath(uri, MICRONAUT_SERVICES_PATH, toClose);
                if (myPath != null) {
                    Files.walkFileTree(myPath, Collections.emptySet(), 2, visitor);
                }
            }
        } catch (IOException e) {
            // ignore, can't do anything here and can't log because class used in compiler
        } finally {
            for (Closeable closeable : toClose) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                }
            }
        }
        return services;
    }

    private static <S> S instantiate(String className, ClassLoader classLoader) {
        try {
            @SuppressWarnings("unchecked") final Class<S> loadedClass =
                (Class<S>) Class.forName(className, false, classLoader);
            // MethodHandler should more performant than the basic reflection
            return (S) LOOKUP.findConstructor(loadedClass, VOID_TYPE).invoke();
        } catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException |
                 IllegalAccessException | IllegalAccessError e) {
            // Ignore
            return null;
        } catch (Throwable e) {
            return sneakyThrow(e);
        }
    }

    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Fork-join recursive services loader.
     *
     * @param <S> The service type
     */
    @SuppressWarnings("java:S1948")
    private static final class MicronautServiceCollector<S> extends RecursiveActionValuesCollector<S> {

        private final ClassLoader classLoader;
        private final String serviceName;
        private final Predicate<S> predicate;
        private final List<RecursiveActionValuesCollector<S>> tasks = new ArrayList<>();
        private int size;

        MicronautServiceCollector(ClassLoader classLoader, String serviceName, Predicate<S> predicate) {
            this.classLoader = classLoader;
            this.serviceName = serviceName;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                size = serviceEntries.size();
                for (String serviceEntry : serviceEntries) {
                    final ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(classLoader, serviceEntry, predicate);
                    tasks.add(task);
                    task.fork();
                }
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        public List<S> collect(boolean allowFork) {
            if (allowFork && ForkJoinPool.getCommonPoolParallelism() > 1) {
                ForkJoinPool.commonPool().invoke(this);
                List<S> collection = null;
                for (RecursiveActionValuesCollector<S> task : tasks) {
                    task.join();
                    if (collection == null) {
                        collection = new ArrayList<>(size);
                    }
                    task.collect(collection);
                }
                if (collection == null) {
                    return List.of();
                }
                return collection;
            }
            try {
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                List<S> collection = new ArrayList<>(serviceEntries.size());
                for (String serviceEntry : serviceEntries) {
                    S val = instantiate(serviceEntry, classLoader);
                    if (val != null && (predicate == null || predicate.test(val))) {
                        collection.add(val);
                    }
                }
                return collection;
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        @Override
        public void collect(Collection<S> values) {
            throw new IllegalStateException("Only constructor method is supported!");
        }
    }

    /**
     * Initializes and filters the entry.
     *
     * @param <S> The service type
     */
    private static final class ServiceInstanceLoader<S> extends RecursiveActionValuesCollector<S> {

        private final ClassLoader classLoader;
        private final String className;
        private final Predicate<S> predicate;
        private S result;
        private Throwable throwable;

        public ServiceInstanceLoader(ClassLoader classLoader, String className, Predicate<S> predicate) {
            this.classLoader = classLoader;
            this.className = className;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                result = instantiate(className, classLoader);
                if (result != null && predicate != null && !predicate.test(result)) {
                    result = null;
                }
            } catch (Throwable e) {
                throwable = e;
            }
        }

        @Override
        public void collect(Collection<S> values) {
            if (throwable != null) {
                throw new SoftServiceLoader.ServiceLoadingException("Failed to load a service: " + throwable.getMessage(), throwable);
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

    private record CacheEntry(ClassLoader classLoader, Map<String, Set<String>> services) {
    }

}
