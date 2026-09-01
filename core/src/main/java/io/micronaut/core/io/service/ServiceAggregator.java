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

import java.util.Set;
import java.util.function.Consumer;

/**
 * A compile-time generated aggregator of the Micronaut services produced by a single module.
 *
 * <p>Without an aggregator every generated service implementation is advertised by its own marker
 * file under {@code META-INF/micronaut/<service name>/<implementation class name>}, and is
 * instantiated at runtime by name using reflection. A module that produces several hundred bean
 * definitions therefore ships several hundred marker files, all of which have to be discovered by
 * walking the jar as a {@link java.nio.file.FileSystem}, and instantiated one by one.</p>
 *
 * <p>An aggregator collapses that into a single generated class per module, advertised through one
 * ordinary {@code META-INF/services/io.micronaut.core.io.service.ServiceAggregator} entry. The
 * generated {@link #collect} implementation instantiates each service directly, so no reflective
 * lookup, no class name string and no per-implementation resource is needed.</p>
 *
 * <p>Aggregated modules do not write the per-implementation marker files at all, so the two
 * mechanisms never produce duplicates: a module is either aggregated or scanned, and a classpath
 * may freely mix both.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public interface ServiceAggregator {

    /**
     * The service name aggregators themselves are advertised under.
     */
    String SERVICE_NAME = "io.micronaut.core.io.service.ServiceAggregator";

    /**
     * The names of the service types this aggregator provides implementations for, for example
     * {@code io.micronaut.inject.BeanDefinitionReference}.
     *
     * <p>Used to skip aggregators that cannot contribute to the service being loaded, so that
     * asking for bean definitions never instantiates introspections and vice versa.</p>
     *
     * @return The aggregated service names, never {@code null}
     */
    Set<String> getServiceNames();

    /**
     * How many independent chunks the implementations of the given service are split into.
     *
     * <p>Instantiating a service implementation loads its class, and class loading dominates the
     * cost of starting a context. The marker file scan hides that cost by forking a task per
     * implementation onto the common pool, so an aggregator that collected everything in one call
     * would be markedly slower than the mechanism it replaces. Chunks exist so the runtime can fork
     * the same way.</p>
     *
     * @param serviceName The service name, one of {@link #getServiceNames()}
     * @return The chunk count, or {@code 0} if this aggregator provides nothing for the service
     */
    int getChunkCount(String serviceName);

    /**
     * Instantiates the implementations in one chunk of the given service and passes them to the
     * consumer.
     *
     * <p>The consumer is typed as {@link Object} on purpose: it keeps the JVM from resolving the
     * implementation types when the generated method is verified, so an implementation whose
     * optional dependencies are absent fails in isolation instead of taking the whole module with
     * it. Implementations that cannot be loaded are skipped silently, matching the behaviour of the
     * marker file scan.</p>
     *
     * @param serviceName The service name, one of {@link #getServiceNames()}
     * @param chunk       The chunk index, from {@code 0} to {@link #getChunkCount(String)} exclusive
     * @param consumer    The consumer of the instantiated services
     */
    void collect(String serviceName, int chunk, Consumer<Object> consumer);

}
