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
package io.micronaut.dev;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.loader.GenerationClassLoader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Keeps what lives as long as the JVM from keeping a retired generation of a {@link DevRuntime}: what a library of the
 * parent tier initializes once and keeps, the threads that took a generation's loader as their context class loader,
 * and what the process-wide caches hold of a retired generation. Reports the generations that stay reachable.
 */
@Internal
@NullMarked
final class GenerationMemory {

    // the runtime's logger: what is logged here is logged as the runtime's
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final int LEAK_TOLERANCE = 2;
    /**
     * What Netty initializes once per JVM and keeps: {@code PlatformDependent} keeps the exception that tells why it does
     * not use {@code Unsafe}, whose stack trace holds the classes on the stack of the thread that first used Netty. It reads
     * its system properties, such as {@code io.netty.noUnsafe}, then: in development mode they are those the JVM was
     * launched with, not those an application's {@code main} would set before it runs Micronaut. H2's {@code DbException}
     * preallocates the exceptions it reports an out of memory error with, whose stack traces hold the classes on the stack
     * of the thread that first opened a connection; it loads its messages then, in the locale the JVM was launched with,
     * not one an application's {@code main} would set before it opens a connection.
     */
    private static final List<String> PARENT_TIER_STATICS = List.of(
        "io.netty.util.internal.PlatformDependent",
        "org.h2.message.DbException"
    );
    /**
     * Reactor's schedulers: the shared ones live as long as the JVM and create their threads as work needs them.
     */
    private static final String REACTOR_SCHEDULERS = "reactor.core.scheduler.Schedulers";

    private final DevClassLoader classLoader;
    private volatile boolean reactorThreads;

    /**
     * @param classLoader The runtime's reloadable loader
     */
    GenerationMemory(DevClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Initializes, on the launcher's thread and outside any generation, what a library of the parent tier initializes
     * once per JVM and keeps for good, such as Netty's {@code PlatformDependent}. Initialized by the first generation, it
     * would keep that generation: an exception created then holds, in its stack trace, the classes of the generation's
     * frames, its application class among them. The threads of Reactor's schedulers, which live as long as the JVM, are
     * given the parent tier's loader as their context class loader, not the loader of the generation they are created
     * from, and run each task with the current generation's. A library missing from the classpath is skipped.
     */
    void initializeParentTierStatics() {
        ClassLoader parentTier = parentTier();
        for (String name : PARENT_TIER_STATICS) {
            try {
                Class.forName(name, true, parentTier);
            } catch (ClassNotFoundException | LinkageError e) {
                // not there, or not initializable outside the application: the generation that uses it initializes it
                LOG.trace("Not initialized ahead of the first generation: {}", name, e);
            }
        }
        try {
            // the helper links to the Reactor of the launcher's own loader: used only when that is the parent tier's
            Class<?> schedulers = Class.forName(REACTOR_SCHEDULERS, false, parentTier);
            if (schedulers == Class.forName(REACTOR_SCHEDULERS, false, DevRuntime.class.getClassLoader())) {
                ReactorThreads.use(classLoader, parentTier);
                reactorThreads = true;
            }
        } catch (ClassNotFoundException | LinkageError e) {
            LOG.trace("The threads of Reactor's schedulers keep the context class loader of the thread that creates them", e);
        }
    }

    /**
     * Releases the threads of Reactor's shared schedulers, when {@link #initializeParentTierStatics()} gave them the
     * parent tier's loader: they end with the schedulers, and the schedule hook keeps the runtime's loader no longer.
     */
    void releaseReactorThreads() {
        if (reactorThreads) {
            ReactorThreads.release(classLoader);
        }
    }

    /**
     * Gives the live threads whose context class loader is a retired generation, such as the housekeeper a retained
     * connection pool started in the generation that created it, the parent tier's loader: see {@link GenerationThreads}.
     * The threads of the runtime itself are left as they are.
     *
     * @param current The thread restarting the application
     * @param applicationThread The thread running the application's main, if any
     */
    void releaseThreads(Thread current, @Nullable Thread applicationThread) {
        List<String> released = GenerationThreads.release(classLoader.liveRetiredGenerations(), parentTier(), Arrays.asList(current, applicationThread));
        if (!released.isEmpty() && LOG.isDebugEnabled()) {
            LOG.debug("Gave {} thread(s) the parent tier's loader as their context class loader, in place of a retired generation: {}", released.size(), released);
        }
    }

    private ClassLoader parentTier() {
        return classLoader.getParent() != null ? classLoader.getParent() : DevRuntime.class.getClassLoader();
    }

    /**
     * Forgets what the process-wide caches hold of a retired generation: the introspections the shared introspector
     * indexed for its loader, held softly so that they would keep it until the memory runs short, and its service entries.
     *
     * @param retired The retired generation
     */
    static void forgetRetired(GenerationClassLoader retired) {
        MicronautMetaServiceLoaderUtils.invalidate(retired);
        BeanIntrospector.SHARED.invalidate();
    }

    /**
     * Warns, a moment after a reload, of the retired generations older than the tolerance that are still reachable.
     */
    void detectLeaks() {
        if (NativeImageUtils.inImageRuntimeCode()) {
            // a native image never unloads a class it defined at runtime: every retired generation stays, as the budget expects
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            System.gc();
            int current = classLoader.generation();
            List<GenerationClassLoader> live = classLoader.liveRetiredGenerations();
            List<Integer> old = live.stream().map(GenerationClassLoader::generation).filter(generation -> generation < current - LEAK_TOLERANCE).toList();
            if (!old.isEmpty()) {
                LOG.warn("{} retired generation(s) {} are still reachable after the reload: a static cache or a thread of the application keeps old classes alive", old.size(), old);
            }
        }, "micronaut-dev-leak-detector");
        thread.setDaemon(true);
        thread.start();
    }
}
