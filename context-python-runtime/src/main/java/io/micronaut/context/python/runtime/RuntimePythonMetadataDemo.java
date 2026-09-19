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
import io.micronaut.core.beans.BeanIntrospector;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** Separate-JVM smoke runner for the prototype, using only runtime dependencies. */
@Internal
public final class RuntimePythonMetadataDemo {
    private RuntimePythonMetadataDemo() {
    }

    /**
     * Starts an application from compiler output and checks real runtime-generated metadata.
     *
     * @param args Output directory, singleton wrapper name, introspected wrapper name
     * @throws Exception If loading or verification fails
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        for (String compiler : new String[]{"io.micronaut.python.compiler.PyronautCompiler",
            "io.micronaut.annotation.processing.BeanDefinitionInjectProcessor", "com.sun.tools.javac.api.JavacTool"}) {
            try {
                Class.forName(compiler);
                throw new IllegalStateException("Compiler unexpectedly present: " + compiler);
            } catch (ClassNotFoundException expected) {
                // The fixture must run from disk with no compiler objects or processor implementation.
            }
        }
        try (var loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}, RuntimePythonMetadataDemo.class.getClassLoader())) {
            Class<?> beanType = loader.loadClass(args[1]);
            Class<?> personType = loader.loadClass(args[2]);
            if (RuntimePythonMetadata.isDefinitionGenerated(beanType) || RuntimePythonMetadata.isIntrospectionGenerated(personType)) {
                throw new IllegalStateException("Metadata generated before application lookup");
            }
            try (var context = ApplicationContext.builder().classLoader(loader)
                .beanDefinitionsProvider(new RuntimePythonBeanDefinitionsProvider()).start()) {
                Object bean = context.getBean(beanType);
                if (context.getBean(beanType) != bean || !RuntimePythonMetadata.isDefinitionGenerated(beanType)) {
                    throw new IllegalStateException("Runtime singleton definition was not used");
                }
                if (!"hello from Python".equals(beanType.getMethod("greeting").invoke(bean))) {
                    throw new IllegalStateException("Python-backed bean invocation failed");
                }
                var introspection = BeanIntrospector.forClassLoader(loader).getIntrospection((Class<Object>) personType);
                Object person = introspection.instantiate();
                var name = introspection.getRequiredProperty("name", String.class);
                name.set(person, "generated at runtime");
                if (!"generated at runtime".equals(name.get(person))) {
                    throw new IllegalStateException("Generated property dispatch failed");
                }
                System.out.println("COMPILER_ABSENT=true");
                System.out.println("JAVAC_ABSENT=true");
                System.out.println("DEFINITION=" + context.getBeanDefinition(beanType).getClass().getName());
                System.out.println("INTROSPECTION=" + introspection.getClass().getName());
                System.out.println("PROPERTY=" + name.get(person));
            }
        }
    }
}
