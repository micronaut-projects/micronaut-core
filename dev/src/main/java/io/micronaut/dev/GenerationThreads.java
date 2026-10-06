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
import io.micronaut.dev.loader.GenerationClassLoader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the threads that outlive a generation from keeping it through their context class loader. A thread takes the
 * context class loader of the thread that creates it: one a bean starts as it is created, such as the housekeeper of a
 * connection pool, has the loader of the generation that created the bean, and, when the bean is retained across
 * restarts, lives on, keeping that generation reachable for as long as the bean lives.
 *
 * <p>Once a restart stopped the old context, a live thread whose context class loader is a retired generation, or a
 * loader with one among its parents, is given the parent tier's loader instead: the loader the classes of a bean that
 * survives a restart come from, as the threads of Reactor's schedulers are given. The current generation's loader is
 * not given: the thread belongs to a library, not to the application, and would keep that generation in turn once it
 * retires, while its later lookups would see one generation's classes among several. Nor is the development runtime's
 * own loader given: the JVM records the loader {@code Class.forName} is called with as an initiating loader, which would
 * keep the classes of the generation current then.</p>
 *
 * <p>Changing a thread's context class loader only changes what its later {@code getContextClassLoader()} calls return,
 * not the classes it already runs or the objects it holds. A thread still running code of a retired generation, such as
 * a task that drains, is left as it is: that code keeps the generation anyway, and its lookups may still need the
 * generation's classes. The next restart looks at it again.</p>
 *
 * <p>Only platform threads are seen: the JVM does not enumerate virtual threads, which an application starts per task
 * rather than for as long as a bean lives.</p>
 */
@Internal
@NullMarked
final class GenerationThreads {

    private GenerationThreads() {
    }

    /**
     * Gives the live threads whose context class loader is one of the given retired generations, or has one among its
     * parents, the parent tier's loader as their context class loader.
     *
     * @param retired    The retired generations still reachable
     * @param parentTier The loader of the parent tier
     * @param skipped    Threads left as they are, the development runtime's own
     * @return The names of the threads given the parent tier's loader
     */
    static List<String> release(Collection<GenerationClassLoader> retired, ClassLoader parentTier, Collection<@Nullable Thread> skipped) {
        if (retired.isEmpty()) {
            return List.of();
        }
        Set<String> retiredNames = new HashSet<>();
        for (GenerationClassLoader generation : retired) {
            retiredNames.add(generation.getName());
        }
        List<String> released = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (skipped.contains(thread) || !thread.isAlive()) {
                continue;
            }
            ClassLoader loader;
            try {
                loader = thread.getContextClassLoader();
            } catch (SecurityException e) {
                continue;
            }
            // the stack is taken again for each candidate, just before its loader changes, not from the snapshot of all
            // threads, which a worker that took a task since no longer matches
            if (!descendsFromAny(loader, retired) || runsAny(thread.getStackTrace(), retiredNames)) {
                continue;
            }
            try {
                thread.setContextClassLoader(parentTier);
                if (runsAny(thread.getStackTrace(), retiredNames) && thread.getContextClassLoader() == parentTier) {
                    // it took a task of the retired generation meanwhile: given its loader back, for the next restart
                    thread.setContextClassLoader(loader);
                    continue;
                }
                released.add(thread.getName());
            } catch (SecurityException e) {
                // not ours to change: it keeps the generation
            }
        }
        return released;
    }

    private static boolean descendsFromAny(@Nullable ClassLoader loader, Collection<GenerationClassLoader> retired) {
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            if (current instanceof GenerationClassLoader && retired.contains(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a frame of the stack runs a class a retired generation defined, by the name of its loader.
     */
    private static boolean runsAny(StackTraceElement[] stack, Set<String> retiredNames) {
        for (StackTraceElement frame : stack) {
            String loaderName = frame.getClassLoaderName();
            if (loaderName != null && retiredNames.contains(loaderName)) {
                return true;
            }
        }
        return false;
    }
}
