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
package io.micronaut.context.python.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import org.jspecify.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts an application from a compilation output directory in a JVM that has neither compiler, reports what it
 * used and how long each step took. The same runner serves the build-time and the runtime backend, so the two can
 * be compared on identical steps.
 *
 * <p>Arguments: the output directory, then {@code key=value} pairs: {@code bean=} the beans to look up (the first one is
 * timed alone), {@code introspection=} the classes to introspect (the first one is timed alone), {@code property=} a string property of it to set and read, {@code absent=} class names that
 * must not be loadable, {@code startups=} how many contexts to start after the first (to separate the first from the
 * warm start).</p>
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeMetadataRunner {

    private static final String[] COMPILER_CLASSES = {
        "io.micronaut.python.compiler.PyronautCompiler",
        "io.micronaut.annotation.processing.BeanDefinitionInjectProcessor",
        "com.sun.tools.javac.api.JavacTool",
        "io.micronaut.inject.ast.ClassElement",
        "io.micronaut.inject.visitor.VisitorContext",
        "io.micronaut.sourcegen.model.ClassDef",
        "io.micronaut.sourcegen.bytecode.ByteCodeWriter",
        "com.github.javaparser.JavaParser"
    };

    private PythonRuntimeMetadataRunner() {
    }

    private static List<Class<?>> classes(ClassLoader loader, @Nullable String names) throws ClassNotFoundException {
        List<Class<?>> types = new ArrayList<>();
        if (names != null) {
            for (String name : names.split(",", -1)) {
                if (!name.isBlank()) {
                    types.add(loader.loadClass(name.trim()));
                }
            }
        }
        return types;
    }

    /**
     * Runs the checks and prints one {@code KEY=value} line per result.
     *
     * @param args The arguments
     * @throws Exception If anything fails
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            int separator = args[i].indexOf('=');
            options.put(args[i].substring(0, separator), args[i].substring(separator + 1));
        }
        for (String compiler : COMPILER_CLASSES) {
            try {
                Class.forName(compiler);
                throw new IllegalStateException("Compiler class unexpectedly present: " + compiler);
            } catch (ClassNotFoundException expected) {
                // The application runs from disk with no compiler, AST API or source generator available
            }
        }
        for (String absent : options.getOrDefault("absent", "").split(",", -1)) {
            if (!absent.isBlank()) {
                try {
                    Class.forName(absent.trim());
                    throw new IllegalStateException("Class unexpectedly present: " + absent);
                } catch (ClassNotFoundException expected) {
                    // build-time artifacts of runtime-generated classes must not exist
                }
            }
        }
        System.out.println("COMPILER_ABSENT=true");
        System.out.println("JAVAC_ABSENT=true");
        System.out.println("AST_API_ABSENT=true");
        System.out.println("SOURCEGEN_ABSENT=true");
        long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
        System.out.println("JVM_UPTIME_AT_MAIN_MS=" + (System.currentTimeMillis() - jvmStart));
        try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}, PythonRuntimeMetadataRunner.class.getClassLoader())) {
            List<Class<?>> beanTypes = classes(loader, options.get("bean"));
            List<Class<?>> introspectedTypes = classes(loader, options.get("introspection"));
            Class<?> beanType = beanTypes.isEmpty() ? null : beanTypes.getFirst();
            Class<?> introspectedType = introspectedTypes.isEmpty() ? null : introspectedTypes.getFirst();
            for (Class<?> type : beanTypes) {
                if (PythonRuntimeMetadata.isDefinitionGenerated(type)) {
                    throw new IllegalStateException("Definition generated before the context started");
                }
            }
            long t0 = System.nanoTime();
            try (ApplicationContext context = ApplicationContext.builder().classLoader(loader).start()) {
                long started = System.nanoTime();
                System.out.println("T_CONTEXT_START_MS=" + (started - t0) / 1_000_000.0);
                System.out.println("BEAN_DEFINITION_COUNT=" + context.getAllBeanDefinitions().size());
                if (beanType != null) {
                    long b0 = System.nanoTime();
                    Object instance = context.getBean(beanType);
                    System.out.println("T_FIRST_BEAN_MS=" + (System.nanoTime() - b0) / 1_000_000.0);
                    if (context.getBean(beanType) != instance) {
                        throw new IllegalStateException("The bean is not a singleton");
                    }
                    System.out.println("DEFINITION=" + context.getBeanDefinition(beanType).getClass().getName());
                    String method = options.get("method");
                    if (method != null) {
                        System.out.println("METHOD_RESULT=" + beanType.getMethod(method).invoke(instance));
                    }
                    long all0 = System.nanoTime();
                    for (Class<?> type : beanTypes) {
                        context.getBean(type);
                    }
                    System.out.println("T_ALL_BEANS_MS=" + (System.nanoTime() - all0) / 1_000_000.0);
                    System.out.println("BEANS_RESOLVED=" + beanTypes.size());
                }
                if (introspectedType != null) {
                    long i0 = System.nanoTime();
                    BeanIntrospection<Object> introspection = BeanIntrospector.forClassLoader(loader).getIntrospection((Class<Object>) introspectedType);
                    System.out.println("T_FIRST_INTROSPECTION_MS=" + (System.nanoTime() - i0) / 1_000_000.0);
                    System.out.println("INTROSPECTION=" + introspection.getClass().getName());
                    Object instance = introspection.instantiate();
                    String property = options.get("property");
                    if (property != null) {
                        BeanProperty<Object, String> name = introspection.getRequiredProperty(property, String.class);
                        name.set(instance, "generated at runtime");
                        System.out.println("PROPERTY=" + name.get(instance));
                    }
                    boolean enumerated = BeanIntrospector.forClassLoader(loader)
                        .findIntrospectedTypes(reference -> reference.getName().equals(introspectedType.getName()))
                        .stream().anyMatch(type -> type == introspectedType);
                    System.out.println("ENUMERATED=" + enumerated);
                    long allI0 = System.nanoTime();
                    for (Class<?> type : introspectedTypes) {
                        BeanIntrospector.forClassLoader(loader).getIntrospection((Class<Object>) type);
                    }
                    System.out.println("T_ALL_INTROSPECTIONS_MS=" + (System.nanoTime() - allI0) / 1_000_000.0);
                    System.out.println("INTROSPECTIONS_RESOLVED=" + introspectedTypes.size());
                }
            }
            int startups = Integer.parseInt(options.getOrDefault("startups", "0"));
            for (int i = 0; i < startups; i++) {
                long s0 = System.nanoTime();
                try (ApplicationContext context = ApplicationContext.builder().classLoader(loader).start()) {
                    if (beanType != null) {
                        context.getBean(beanType);
                    }
                }
                System.out.println("T_WARM_START_" + (i + 1) + "_MS=" + (System.nanoTime() - s0) / 1_000_000.0);
            }
            System.out.println("GENERATED_CLASSES=" + PythonRuntimeMetadata.generatedClassCount());
            System.out.println("LOADED_CLASSES=" + ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
            System.gc();
            Thread.sleep(200);
            System.gc();
            Runtime runtime = Runtime.getRuntime();
            System.out.println("HEAP_USED_AFTER_GC_BYTES=" + (runtime.totalMemory() - runtime.freeMemory()));
            System.out.println("JVM_UPTIME_AT_END_MS=" + (System.currentTimeMillis() - jvmStart));
        }
    }
}
