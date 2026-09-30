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
package io.micronaut.dev.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * What a module asserts over a reload: that what it holds followed the new generation, that what
 * it retained survived, and that it keeps no retired generation reachable. Each failure names what
 * a module has to change.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ReloadTck {

    private static final Duration COLLECTION_TIMEOUT = Duration.ofSeconds(10);

    private ReloadTck() {
    }

    /**
     * Asserts that what the probe reaches through the module, after a reload, belongs to the current
     * generation: the bean, definition or class a registry hands out is the new generation's, not one
     * the registry kept from before. The probe must reach something of the reloadable tier, an instance
     * of a class written with {@link ReloadHarness#source}, so that the assertion means anything.
     *
     * @param harness The harness, after {@link ReloadHarness#reload()}
     * @param probe What the module holds, reached through the current context: a bean, its definition or its class
     */
    public static void assertFollowsReload(ReloadHarness harness, Function<ApplicationContext, @Nullable Object> probe) {
        Object reached = probe.apply(harness.context());
        if (reached == null) {
            throw new AssertionError("The probe reached nothing: the module holds no entry for the reloaded bean after the reload");
        }
        Class<?> type = switch (reached) {
            case Class<?> c -> c;
            // the definition class itself: a factory method's bean type may be a parent-tier interface
            case BeanDefinition<?> definition -> definition.getClass();
            default -> reached.getClass();
        };
        DevClassLoader loader = harness.runtime().classLoader();
        if (!(type.getClassLoader() instanceof GenerationClassLoader generation)) {
            throw new AssertionError("The probe reached " + type.getName() + ", which is not of the reloadable tier: probe for a bean of a class written with ReloadHarness.source, since only those change across a reload");
        }
        // the current loader itself, not merely one this runtime has not retired: a class of another runtime's
        // generation, kept by a static registry across harnesses, is as wrong
        if (generation != loader.current()) {
            throw new AssertionError("The module still holds " + type.getName() + " of generation " + generation.generation() + " after the reload to generation " + loader.generation()
                + ": a registry built once must watch the definitions it holds (WatchableBeanContext.watchDefinitions or watchBeans) or resolve through the context on use");
        }
    }

    /**
     * Asserts that a bean retained across the reload is the very instance the new generation serves,
     * so that a module's retention policy, or the manifest's list, kept it.
     *
     * @param harness The harness, after {@link ReloadHarness#reload()}
     * @param retained The instance obtained from the context before the reload
     */
    public static void assertRetained(ReloadHarness harness, Object retained) {
        // by identity among the registrations of its type, whatever qualifier or proxy it has
        boolean adopted = harness.context().getActiveBeanRegistrations(Qualifiers.any()).stream().anyMatch(registration -> registration.getBean() == retained);
        if (!adopted) {
            throw new AssertionError(retained.getClass().getName() + " was not retained across the reload: the new generation created another instance. A retained bean must be parent-tier and hold nothing of the reloadable tier, and a BeanRetentionPolicy or the harness's retain() must name it");
        }
    }

    /**
     * Asserts that no retired generation is reachable any more, once the garbage collector has run:
     * a static cache, a thread, or a registry that kept an instance of the old generation would keep
     * its loader, and every class it defined, alive for the life of the process.
     *
     * @param harness The harness, after {@link ReloadHarness#reload()}
     */
    public static void assertRetiredGenerationsCollected(ReloadHarness harness) {
        assertRetiredGenerationsCollected(harness, COLLECTION_TIMEOUT);
    }

    /**
     * Asserts that no retired generation is reachable any more, within the given time.
     *
     * @param harness The harness, after {@link ReloadHarness#reload()}
     * @param timeout How long to let the garbage collector run
     */
    public static void assertRetiredGenerationsCollected(ReloadHarness harness, Duration timeout) {
        DevClassLoader loader = harness.runtime().classLoader();
        long deadline = System.nanoTime() + timeout.toNanos();
        // the loaders are never held here across a collection: a list of them would keep them alive itself
        while (anyRetiredAlive(loader) && System.nanoTime() < deadline) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        List<Integer> live = retiredGenerations(loader);
        if (!live.isEmpty()) {
            throw new AssertionError("Retired generation(s) " + live + " are still reachable after the reload to generation " + loader.generation()
                + ": a static cache, a thread that was not stopped, or a registry entry keeps a class of the old generation alive; caches keyed by class must evict with ClassChangeEvent.isStale");
        }
    }

    private static boolean anyRetiredAlive(DevClassLoader loader) {
        return !loader.liveRetiredGenerations().isEmpty();
    }

    private static List<Integer> retiredGenerations(DevClassLoader loader) {
        return loader.liveRetiredGenerations().stream().map(GenerationClassLoader::generation).toList();
    }
}
