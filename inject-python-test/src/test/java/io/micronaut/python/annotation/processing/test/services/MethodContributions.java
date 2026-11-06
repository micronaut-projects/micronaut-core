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
package io.micronaut.python.annotation.processing.test.services;

import io.micronaut.core.io.service.SoftServiceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

/**
 * Loads the {@link MethodContributor} services once per JVM, the way a library holding its
 * contributions in a static holder does.
 */
public final class MethodContributions {

    private MethodContributions() {
    }

    /**
     * @return The contributors loaded from the context class loader, once per JVM
     */
    public static List<MethodContributor> shared() {
        return Holder.CONTRIBUTORS;
    }

    /**
     * @return The descriptions of the shared contributors
     */
    public static List<String> sharedDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (MethodContributor contributor : Holder.CONTRIBUTORS) {
            descriptions.add(contributor.describe());
        }
        return descriptions;
    }

    /**
     * Loads the contributors the way the parallel service loader does: the services are
     * instantiated on a worker of the common pool while the calling thread waits.
     *
     * @return The contributors
     */
    public static List<MethodContributor> load() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader serviceClassLoader = classLoader == null ? MethodContributions.class.getClassLoader() : classLoader;
        try {
            return CompletableFuture.supplyAsync(
                () -> new ArrayList<>(SoftServiceLoader.load(MethodContributor.class, serviceClassLoader).collectAll()),
                ForkJoinPool.commonPool()
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static final class Holder {
        private static final List<MethodContributor> CONTRIBUTORS = load();
    }
}
