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
package io.micronaut.dev.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupRegistry;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.RetainedEventLoopGroups;
import io.micronaut.http.netty.configuration.NettyGlobalConfiguration;
import io.netty.channel.EventLoopGroup;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Keeps the event loop groups of the HTTP servers and clients across generations: their threads and their selectors
 * are made once, and each generation's channels, with their pipelines and handlers, run on them. The groups are those
 * the event loop group registry makes on the framework's own threads; the registry of each generation asks for them
 * by name, and leaves them running when it shuts down.
 *
 * <p>Retained across restarts until a change under {@value EventLoopGroupConfiguration#EVENT_LOOPS}, which sizes and
 * names the groups, or under {@value NettyGlobalConfiguration#PREFIX}, which configures their threads: the next
 * generation then makes new groups from the changed configuration, and the old ones are shut down. It copies what
 * it needs of a group's configuration, its shutdown periods, and keeps no configuration bean.</p>
 *
 * <p>A group no registry asked for during a whole generation is shut down as that generation stops: the generation
 * that would have used it now makes its own, on threads of an application bean or with an interceptor. A group asked
 * for with other settings, which an application's configuration bean computes, is made again. A group whose
 * configuration or factory is of the application's classes is not retained: it would keep running the old code.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Retain(invalidatedBy = {EventLoopGroupConfiguration.EVENT_LOOPS, NettyGlobalConfiguration.PREFIX})
@Requires(classes = RetainedEventLoopGroups.class)
@DevelopmentActive
@Requires(beans = DevRuntime.class)
final class DevEventLoopGroups implements RetainedEventLoopGroups {

    private static final Logger LOG = LoggerFactory.getLogger(DevEventLoopGroups.class);

    private final Map<String, Retained> groups = new LinkedHashMap<>();
    /**
     * The number of generations that ended while the groups were retained: a group asked for during the running one
     * was claimed at this count.
     */
    private long generation;
    private boolean closed;

    @Override
    public synchronized EventLoopGroup eventLoopGroup(String name, EventLoopGroupConfiguration configuration, EventLoopGroupFactory factory,
                                                     Supplier<EventLoopGroup> create) {
        Retained retained = groups.get(name);
        if (closed || isOfAGeneration(configuration) || isOfAGeneration(factory)) {
            // the runtime closes, or the group would run, and keep, the application's own code: the registry's to shut down
            return create.get();
        }
        List<Object> settings = settings(configuration, factory);
        if (retained != null && retained.settings.equals(settings) && !retained.group.isShuttingDown()) {
            retained.claimed = generation;
            LOG.debug("Event loop group [{}] retained across the restart", name);
            return retained.group;
        }
        if (retained != null) {
            // made from other settings, which the application's code set rather than its configuration, or shut down
            groups.remove(name);
            retained.shutdown();
        }
        EventLoopGroup group = create.get();
        groups.put(name, new Retained(group, settings, configuration.getShutdownQuietPeriod(), configuration.getShutdownTimeout(), generation));
        return group;
    }

    @Override
    public synchronized Set<EventLoopGroup> release(Collection<EventLoopGroup> released) {
        Set<EventLoopGroup> leftRunning = Collections.newSetFromMap(new IdentityHashMap<>());
        if (closed) {
            return leftRunning;
        }
        for (Retained retained : groups.values()) {
            if (released.contains(retained.group)) {
                leftRunning.add(retained.group);
            }
        }
        return leftRunning;
    }

    /**
     * Called as a generation stops: a group not asked for while it ran is shut down, the generation that would have
     * used it making its own, on threads of an application bean, with an interceptor, or none at all.
     */
    synchronized void generationEnded() {
        if (closed) {
            return;
        }
        List<Retained> unclaimed = new ArrayList<>();
        for (Iterator<Retained> iterator = groups.values().iterator(); iterator.hasNext(); ) {
            Retained retained = iterator.next();
            if (retained.claimed != generation) {
                iterator.remove();
                unclaimed.add(retained);
            }
        }
        generation++;
        unclaimed.forEach(Retained::shutdown);
    }

    /**
     * Whether a group is retained.
     *
     * @return True once a registry was handed one
     */
    synchronized boolean isEmpty() {
        return groups.isEmpty();
    }

    /**
     * Shuts the groups down: a change released them, or the development runtime closes.
     */
    @PreDestroy
    synchronized void close() {
        closed = true;
        List<Retained> retained = new ArrayList<>(groups.values());
        groups.clear();
        retained.forEach(Retained::shutdown);
    }

    /**
     * What a group is made from: the settings of its configuration, which an {@code EventLoopGroupConfiguration} bean
     * of the application may compute rather than bind, and the kind of factory.
     */
    private static List<Object> settings(EventLoopGroupConfiguration configuration, EventLoopGroupFactory factory) {
        return Arrays.asList(
            factory.getClass().getName(),
            DefaultEventLoopGroupRegistry.numThreads(configuration),
            configuration.getIoRatio().orElse(null),
            configuration.getExecutorName().orElse(null),
            configuration.isPreferNativeTransport(),
            List.copyOf(configuration.getTransport()),
            configuration.isLoomCarrier()
        );
    }

    private static boolean isOfAGeneration(Object object) {
        return object.getClass().getClassLoader() instanceof GenerationClassLoader;
    }

    /**
     * A retained group and the shutdown periods copied from its configuration.
     */
    private static final class Retained {
        final EventLoopGroup group;
        final List<Object> settings;
        final Duration quietPeriod;
        final Duration timeout;
        /**
         * The generation, counted as {@link #generation} is, that last asked for the group.
         */
        long claimed;

        Retained(EventLoopGroup group, List<Object> settings, Duration quietPeriod, Duration timeout, long claimed) {
            this.group = group;
            this.settings = settings;
            this.quietPeriod = quietPeriod;
            this.timeout = timeout;
            this.claimed = claimed;
        }

        void shutdown() {
            try {
                group.shutdownGracefully(quietPeriod.toMillis(), timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                LOG.warn("Error shutting down a retained event loop group: {}", e.getMessage(), e);
            }
        }
    }
}
