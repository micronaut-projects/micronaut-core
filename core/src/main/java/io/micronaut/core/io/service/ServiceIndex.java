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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A precomputed index of the services of a closed class path, which lets service loading on the JVM skip the scan of
 * {@code META-INF/services} and {@code META-INF/micronaut} for the types it covers.
 *
 * <p>Besides the class loader it was built for, the index only holds names. Services are still instantiated, filtered
 * and ordered as the scan does it, with one fork-join task per name, joined in order. A packager that sees the
 * complete class path of an application builds the index at packaging time, for example with
 * {@link ServiceIndexBuilder}, and registers it at run time through a {@link StaticOptimizations.Loader}:</p>
 *
 * <pre>{@code
 * public final class GeneratedServiceIndexLoader implements StaticOptimizations.Loader<ServiceIndex> {
 *     public ServiceIndex load() {
 *         return new ServiceIndex(GeneratedServiceIndexLoader.class.getClassLoader(), micronautServices, standardServices);
 *     }
 * }
 * }</pre>
 *
 * <p>The index follows these rules:</p>
 * <ul>
 *     <li>It is only served for the {@link #classLoader() class loader} it was built for. Any other class loader,
 *     including a child of that class loader, scans the class path.</li>
 *     <li>It is ignored in native image code, where the service table built by the native image feature is used.</li>
 *     <li>A {@link SoftServiceLoader.StaticServiceLoader} registered for a type through
 *     {@link SoftServiceLoader.Optimizations} is still used for that type.</li>
 *     <li>The name condition given to {@link SoftServiceLoader#load(Class, ClassLoader, java.util.function.Predicate)}
 *     is tested on every name of the index.</li>
 *     <li>At most one index can be registered: registering a second one fails.</li>
 *     <li>Setting the system property {@value #ENABLED_PROPERTY} to {@code false} switches the index off, so that
 *     every lookup scans the class path.</li>
 * </ul>
 *
 * <p>An index describes a closed world: a JAR added to the class path after the index was built is not seen for the
 * types the index covers. It should only be emitted for packagings whose class path cannot change.</p>
 *
 * @param classLoader       The class loader the index was built for, and the only one it is served for
 * @param micronautServices The entries under {@code META-INF/micronaut/<type>/} for every type, as
 *                          {@link MicronautMetaServiceLoaderUtils#findAllMicronautMetaServices(ClassLoader)} finds them.
 *                          It is exhaustive: a type that is missing has no such entries
 * @param standardServices  The names listed by the {@code META-INF/services/<type>} files of each indexed type, in the
 *                          order the scan finds them. A type that is missing is scanned
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Experimental
public record ServiceIndex(ClassLoader classLoader,
                           Map<String, Set<String>> micronautServices,
                           Map<String, List<String>> standardServices) {

    /**
     * The system property that switches the index off when it is set to {@code false}.
     */
    public static final String ENABLED_PROPERTY = "micronaut.service.index.enabled";

    /**
     * @param classLoader       The class loader the index was built for, and the only one it is served for
     * @param micronautServices The entries under {@code META-INF/micronaut/<type>/} for every type
     * @param standardServices  The names listed by the {@code META-INF/services/<type>} files of each indexed type
     */
    public ServiceIndex {
        Objects.requireNonNull(classLoader, "classLoader");
        micronautServices = copyOfSets(micronautServices);
        standardServices = copyOfLists(standardServices);
    }

    /**
     * Finds the registered index that applies to a lookup.
     *
     * @param classLoader The class loader of the lookup
     * @return The index, or null if the class path must be scanned
     */
    static @Nullable ServiceIndex find(ClassLoader classLoader) {
        if (NativeImageUtils.inImageCode()) {
            // the service table of the native image is used there, and the index is not even read
            return null;
        }
        ServiceIndex index = Registered.INDEX;
        if (index == null
            || index.classLoader != classLoader
            || StringUtils.FALSE.equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY))) {
            return null;
        }
        return index;
    }

    private static Map<String, Set<String>> copyOfSets(Map<String, Set<String>> services) {
        Map<String, Set<String>> copy = new LinkedHashMap<>(services.size());
        for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<String>> copyOfLists(Map<String, List<String>> services) {
        Map<String, List<String>> copy = new LinkedHashMap<>(services.size());
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Holds the index registered through {@link StaticOptimizations}, read once, on the first lookup.
     */
    private static final class Registered {
        @Nullable
        private static final ServiceIndex INDEX = StaticOptimizations.get(ServiceIndex.class).orElse(null);
    }
}
