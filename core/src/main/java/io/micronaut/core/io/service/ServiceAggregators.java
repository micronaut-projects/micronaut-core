/*
 * Copyright 2017-2026 original authors
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
import org.graalvm.nativeimage.ImageSingletons;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Discovers and caches the {@link ServiceAggregator}s on a class loader.
 *
 * <p>Discovery deliberately goes through {@link ClassLoader#getResources(String)} on a single fixed
 * resource name rather than through the {@code META-INF/micronaut/} directory walk. The class loader
 * already holds the parsed central directory of every jar, so this is a hash lookup per jar, while
 * the directory walk has to open each jar a second time as a zip {@link java.nio.file.FileSystem}.
 * </p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
final class ServiceAggregators {

    private static final String AGGREGATOR_RESOURCE = SoftServiceLoader.META_INF_SERVICES + '/' + ServiceAggregator.SERVICE_NAME;
    private static final String ROOT_RESOURCE = SoftServiceLoader.META_INF_SERVICES + '/' + ServiceAggregatorRoot.SERVICE_NAME;

    /**
     * The most lanes to spread a service's chunks across, defaulting to as many as the common pool
     * will give.
     *
     * <p>This work is class loading, which is bounded by locks in the class loader and in the jars
     * being read rather than by CPU, so it stops scaling well before the lane count. That makes a
     * low cap tempting: on a synthetic application four lanes costs 26% less CPU than an uncapped
     * run for the same wall time.</p>
     *
     * <p>It is the wrong default though. On a hello world HTTP application, where loading overlaps
     * with the rest of the server starting rather than being all there is to do, capping at four
     * costs <em>11% more wall time than the marker file scan it replaces</em>, while uncapped it is
     * 2% faster and still 11% cheaper in CPU. Startup that is slower than the mechanism it replaces
     * is not worth any CPU saving, so the default matches what the scan does - fork per unit of work
     * and let the pool decide.</p>
     *
     * <p>Set {@code micronaut.aggregator.lanes} to cap it where CPU is billed and wall time is not
     * the constraint.</p>
     */
    private static final int MAX_LANES = Integer.getInteger("micronaut.aggregator.lanes", Integer.MAX_VALUE);

    @Nullable
    private static volatile CacheEntry cacheEntry;

    private ServiceAggregators() {
    }

    /**
     * Instantiates the implementations of the given service contributed by aggregated modules.
     *
     * <p>Each chunk of each aggregator is forked onto the common pool, mirroring what the marker
     * file scan does per implementation. Class loading is the bulk of the work here and it is what
     * the fork is for; collecting on one thread costs roughly twice as much.</p>
     *
     * <p>The filter runs inside the forked task rather than on the results, because for the services
     * that matter it is not a cheap test: {@code BeanDefinitionReference::isPresent} loads the bean
     * definition and the bean type. Applying it on the calling thread would serialise most of the
     * class loading this method exists to parallelise.</p>
     *
     * @param classLoader The class loader
     * @param serviceName The service name
     * @param predicate   Filter applied on the forked task, or {@code null} to keep everything
     * @param consumer    The consumer of the retained services
     * @return {@code true} if at least one aggregator declared the service
     */
    static boolean collect(ClassLoader classLoader,
                           String serviceName,
                           @Nullable Predicate<Object> predicate,
                           Consumer<Object> consumer) {
        List<Chunk> chunks = null;
        for (ServiceAggregator aggregator : load(classLoader)) {
            if (!aggregator.getServiceNames().contains(serviceName)) {
                continue;
            }
            int count = aggregator.getChunkCount(serviceName);
            for (int chunk = 0; chunk < count; chunk++) {
                if (chunks == null) {
                    chunks = new ArrayList<>();
                }
                chunks.add(new Chunk(aggregator, serviceName, chunk));
            }
        }
        if (chunks == null) {
            return false;
        }
        int lanes = Math.min(MAX_LANES, Math.min(chunks.size(), ForkJoinPool.getCommonPoolParallelism()));
        List<Lane> tasks = new ArrayList<>(Math.max(lanes, 1));
        if (lanes <= 1) {
            tasks.add(new Lane(chunks, predicate));
            tasks.get(0).compute();
        } else {
            for (int i = 0; i < lanes; i++) {
                int from = (int) ((long) chunks.size() * i / lanes);
                int to = (int) ((long) chunks.size() * (i + 1) / lanes);
                tasks.add(new Lane(chunks.subList(from, to), predicate));
            }
            // submitted as a single root task rather than forking each lane from here: forking from
            // a thread that is not a pool worker goes through the external submission path once per
            // task, which on a cold JVM costs more than the parallelism buys back
            ForkJoinPool.commonPool().invoke(new RootTask(tasks));
        }
        // consumed in aggregator and chunk order so the result does not depend on scheduling
        for (Lane task : tasks) {
            for (Object value : task.values) {
                consumer.accept(value);
            }
        }
        return true;
    }

    /**
     * @param classLoader The class loader
     * @return The aggregators visible from the class loader
     */
    static List<ServiceAggregator> load(ClassLoader classLoader) {
        List<ServiceAggregator> fromImage = findStaticAggregators();
        if (fromImage != null) {
            // a native image resolves and constructs the aggregators during the build, so at runtime
            // there is no resource to read and no class to resolve by name
            return fromImage;
        }
        CacheEntry ce = cacheEntry;
        if (ce == null || ce.classLoader != classLoader) {
            ce = new CacheEntry(classLoader, resolve(classLoader));
            cacheEntry = ce;
        }
        return ce.aggregators;
    }

    /**
     * @return The aggregators baked into a native image, or {@code null} outside one
     */
    @Nullable
    static List<ServiceAggregator> findStaticAggregators() {
        if (!NativeImageUtils.hasImageSingletons()) {
            return null;
        }
        return ImageSingletons.contains(ExclusiveStaticAggregators.class)
            ? ImageSingletons.lookup(ExclusiveStaticAggregators.class).aggregators()
            : null;
    }

    private static List<ServiceAggregator> resolve(ClassLoader classLoader) {
        // a root, where the application build could see the whole classpath, constructs every module
        // aggregator itself, so the root is the only class that has to be resolved by name
        List<ServiceAggregator> fromRoots = new ArrayList<>();
        for (Object root : instantiateAll(classLoader, readNames(classLoader, ROOT_RESOURCE), ServiceAggregatorRoot.class)) {
            fromRoots.addAll(((ServiceAggregatorRoot) root).getAggregators());
        }
        if (!fromRoots.isEmpty()) {
            return fromRoots;
        }
        // no root: fall back to resolving one aggregator per module by name
        List<ServiceAggregator> aggregators = new ArrayList<>();
        for (Object aggregator : instantiateAll(classLoader, readNames(classLoader, AGGREGATOR_RESOURCE), ServiceAggregator.class)) {
            aggregators.add((ServiceAggregator) aggregator);
        }
        return aggregators;
    }

    private static Set<String> readNames(ClassLoader classLoader, String resource) {
        Set<String> names = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(resource);
            while (resources.hasMoreElements()) {
                readInto(resources.nextElement(), names);
            }
        } catch (IOException e) {
            // ignore, can't do anything here and can't log because the class is used in the compiler
        }
        return names;
    }

    private static void readInto(URL url, Set<String> names) {
        // deliberately not disabling URL connection caching: for a jar: URL that reopens the jar
        // per file, and with one of these per module the reopens add up to more than everything
        // else this class does
        try (InputStream is = url.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                int comment = line.indexOf('#');
                if (comment > -1) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private static List<Object> instantiateAll(ClassLoader classLoader, Set<String> names, Class<?> type) {
        if (names.isEmpty()) {
            return List.of();
        }
        List<Object> instances = new ArrayList<>(names.size());
        for (String name : names) {
            try {
                // plain reflection rather than a method handle: there is one of these per
                // application when a root is generated, and bootstrapping the method handle
                // machinery costs more than the reflective call it would replace
                Class<?> loadedClass = Class.forName(name, false, classLoader);
                instances.add(type.cast(loadedClass.getDeclaredConstructor().newInstance()));
            } catch (NoClassDefFoundError | ReflectiveOperationException | IllegalAccessError e) {
                // the module is not fully on the classpath, skip it
            } catch (Throwable e) {
                throw new SoftServiceLoader.ServiceLoadingException("Failed to load " + type.getSimpleName() + ": " + name, e);
            }
        }
        return instances;
    }

    /**
     * Forks the lanes from inside the pool, where {@code fork} is a push onto the worker's own
     * deque rather than an external submission.
     */
    @SuppressWarnings("java:S1948")
    private static final class RootTask extends RecursiveAction {

        private final List<Lane> tasks;

        private RootTask(List<Lane> tasks) {
            this.tasks = tasks;
        }

        @Override
        protected void compute() {
            for (int i = 1; i < tasks.size(); i++) {
                tasks.get(i).fork();
            }
            tasks.get(0).compute();
            for (int i = tasks.size() - 1; i > 0; i--) {
                tasks.get(i).join();
            }
        }
    }

    /**
     * One unit of a module aggregator's work.
     *
     * @param aggregator  The aggregator to call
     * @param serviceName The service being loaded
     * @param chunk       The chunk index within that service
     */
    private record Chunk(ServiceAggregator aggregator, String serviceName, int chunk) {
    }

    /**
     * Runs a share of the chunks on one thread. Values are buffered rather than handed straight to
     * the caller's consumer because lanes run concurrently and most consumers are not thread safe.
     */
    @SuppressWarnings("java:S1948")
    private static final class Lane extends RecursiveAction {

        private final List<Chunk> chunks;
        @Nullable
        private final Predicate<Object> predicate;
        private final List<Object> values = new ArrayList<>();

        private Lane(List<Chunk> chunks, @Nullable Predicate<Object> predicate) {
            this.chunks = chunks;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            for (Chunk c : chunks) {
                c.aggregator().collect(c.serviceName(), c.chunk(), value -> {
                    if (predicate == null || predicate.test(value)) {
                        values.add(value);
                    }
                });
            }
        }
    }

    private record CacheEntry(ClassLoader classLoader, List<ServiceAggregator> aggregators) {
    }

    /**
     * The aggregators a native image build resolved, held in the image heap so the runtime has
     * nothing to discover.
     *
     * @param aggregators The aggregators
     */
    @Internal
    record ExclusiveStaticAggregators(List<ServiceAggregator> aggregators) {
    }

}
