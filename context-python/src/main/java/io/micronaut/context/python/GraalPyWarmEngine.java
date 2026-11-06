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
package io.micronaut.context.python;

import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.reload.LauncherCloseActions;
import io.micronaut.core.value.PropertyResolver;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The GraalPy engine that the test mode of a development launcher keeps warm across the application contexts the
 * tests of one class loader generation start: the test classes of a run, each starting its own context, and the runs
 * that follow an edit patched in place. A context built on a warm engine finds the parsed and compiled code of the
 * earlier contexts, where a new engine starts cold. Development mode starts one context per generation, which has
 * nothing to share its engine with.
 *
 * <p>An engine has one host access configuration for all its contexts, which Truffle compares with
 * {@link HostAccess#equals(Object)}, and ours holds a target type mapping for each generated class of the
 * generation. The engine therefore cannot outlive its generation: it is kept with the host access of the
 * generation, built once from the mappings of its first context, and a context of another generation retires both,
 * the engine closing once its last context closed, so that it keeps no retired generation reachable. A host access
 * is shared only when every target type mapping is stateless, as the generated ones are: the shared host access
 * converts with the mapping instances of the context that built it.</p>
 *
 * <p>Off outside test mode, where every application context creates and closes its own engine, and off with
 * {@value #PROPERTY} set to {@code false}.</p>
 */
@SuppressWarnings("ReferenceEquality") // class loaders, host accesses and engines are compared by identity, as Truffle compares them
final class GraalPyWarmEngine {

    /**
     * Whether test mode keeps the engine warm across the application contexts of a generation, true by default.
     */
    static final String PROPERTY = "micronaut.dev.python.warm-engine";

    private static final Logger LOG = LoggerFactory.getLogger(GraalPyWarmEngine.class);
    private static final Object LOCK = new Object();

    // guarded by LOCK: the generation whose engine is warm
    private static @Nullable Generation current;

    private GraalPyWarmEngine() {
    }

    /**
     * Whether the application context of the given environment keeps the engine warm.
     *
     * @param environment The environment of the application context
     * @return True in test mode, unless switched off
     */
    static boolean isEnabled(PropertyResolver environment) {
        if (!DevelopmentMode.isTestMode(environment)) {
            return false;
        }
        return environment.getProperty(PROPERTY, Boolean.class).orElse(true);
    }

    /**
     * The host access of the generation of the given class loader: the one built for an earlier context of the
     * generation from the same mappings, or a new one, which retires the warm engine of another generation.
     *
     * @param classLoader The class loader of the application context, the generation's
     * @param mappings The target type mappings of the application context
     * @param functionalInterfaces The functional interfaces of the generated providers
     * @param builder Builds the host access
     * @return The host access
     */
    static HostAccess hostAccess(ClassLoader classLoader,
                                 Collection<TargetTypeMapping<?>> mappings,
                                 List<PythonFunctionalInterfaceProvider.Entry> functionalInterfaces,
                                 Supplier<HostAccess> builder) {
        List<Class<?>> mappingTypes = new ArrayList<>(mappings.size());
        for (TargetTypeMapping<?> mapping : mappings) {
            mappingTypes.add(mapping.getClass());
        }
        Generation retired = null;
        HostAccess hostAccess;
        synchronized (LOCK) {
            Generation generation = current;
            if (generation != null && generation.classLoader == classLoader
                && generation.mappingTypes.equals(mappingTypes) && generation.functionalInterfaces.equals(functionalInterfaces)) {
                return generation.hostAccess;
            }
            hostAccess = builder.get();
            String stateful = statefulMapping(mappingTypes);
            if (stateful != null) {
                // the context gets an engine of its own; a warm engine of the same generation stays for the contexts it fits
                LOG.debug("Not keeping the GraalPy engine warm for {}: the target type mapping {} has state", classLoader, stateful);
                if (generation != null && generation.classLoader != classLoader) {
                    retired = generation;
                    current = null;
                }
            } else {
                retired = generation;
                current = new Generation(classLoader, List.copyOf(mappingTypes), List.copyOf(functionalInterfaces), hostAccess);
                // the last generation's engine is retired by no next one: the launcher releases it as it closes
                LauncherCloseActions.register(GraalPyWarmEngine.class.getName(), GraalPyWarmEngine::reset);
            }
        }
        retire(retired);
        return hostAccess;
    }

    /**
     * The warm engine of the generation whose host access is given: the engine of an earlier context of the
     * generation, built with the same options, or a new one that is kept warm. A host access that is not the
     * generation's gets an engine of its own, which the application context closes.
     *
     * @param hostAccess The host access of the application context
     * @param options The engine configuration of the application context
     * @param builder Builds an engine
     * @return The engine
     */
    static Engine engine(HostAccess hostAccess, Map<String, Object> options, Supplier<Engine> builder) {
        Engine replaced = null;
        Engine engine;
        synchronized (LOCK) {
            Generation generation = current;
            if (generation == null || generation.hostAccess != hostAccess) {
                return builder.get();
            }
            if (generation.engine != null && generation.options.equals(options)) {
                LOG.debug("Reusing the warm GraalPy engine of {}", generation.classLoader);
                return generation.engine;
            }
            replaced = generation.engine;
            engine = builder.get();
            generation.engine = engine;
            generation.options = Map.copyOf(options);
        }
        if (replaced != null) {
            LOG.debug("The GraalPy engine configuration changed, closing the warm engine once its contexts are closed");
            closeWhenUnused(replaced);
        }
        return engine;
    }

    /**
     * Whether the given engine is the warm one, which the application context that received it does not close.
     *
     * @param engine The engine
     * @return True if the engine is kept warm
     */
    static boolean isWarm(Engine engine) {
        synchronized (LOCK) {
            Generation generation = current;
            return generation != null && generation.engine == engine;
        }
    }

    /**
     * Forgets the warm generation and closes its engine once its contexts are closed: the launcher closes.
     */
    static void reset() {
        Generation retired;
        synchronized (LOCK) {
            retired = current;
            current = null;
        }
        retire(retired);
    }

    private static void retire(@Nullable Generation generation) {
        if (generation == null) {
            return;
        }
        Engine engine = generation.engine;
        if (engine != null) {
            LOG.debug("Retiring the warm GraalPy engine of {}", generation.classLoader);
            closeWhenUnused(engine);
        }
    }

    private static void closeWhenUnused(Engine engine) {
        PythonContextRegistry.onNoContexts(engine, () -> GraalPyEngineFactory.closeEngine(engine));
    }

    /**
     * The first mapping type with an instance field, which the host access built for one context would carry into
     * the next.
     */
    private static @Nullable String statefulMapping(List<Class<?>> mappingTypes) {
        for (Class<?> mappingType : mappingTypes) {
            for (Class<?> type = mappingType; type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        return mappingType.getName();
                    }
                }
            }
        }
        return null;
    }

    private static final class Generation {
        private final ClassLoader classLoader;
        private final List<Class<?>> mappingTypes;
        private final List<PythonFunctionalInterfaceProvider.Entry> functionalInterfaces;
        private final HostAccess hostAccess;
        // guarded by LOCK
        private @Nullable Engine engine;
        private Map<String, Object> options = Map.of();

        private Generation(ClassLoader classLoader,
                           List<Class<?>> mappingTypes,
                           List<PythonFunctionalInterfaceProvider.Entry> functionalInterfaces,
                           HostAccess hostAccess) {
            this.classLoader = classLoader;
            this.mappingTypes = mappingTypes;
            this.functionalInterfaces = functionalInterfaces;
            this.hostAccess = hostAccess;
        }
    }
}
