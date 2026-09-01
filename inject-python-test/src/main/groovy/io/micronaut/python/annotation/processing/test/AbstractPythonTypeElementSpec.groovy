/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.annotation.processing.test

import groovy.transform.CompileStatic
import io.micronaut.aop.internal.InterceptorRegistryBean
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.context.Qualifier
import io.micronaut.context.event.ApplicationEventPublisherFactory
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.context.python.PythonContextExecutor
import io.micronaut.core.io.IOUtils
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.provider.BeanProviderDefinition
import io.micronaut.inject.provider.JakartaProviderBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.visitor.AbstractPythonClassElement
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import javax.tools.JavaFileObject
import java.util.stream.Collectors
import java.util.stream.StreamSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base class to extend from to allow compilation of Python sources
 * at runtime to allow testing of compile time behavior.
 *
 * @author Micronaut
 * @since 4.8.0
 */
abstract class AbstractPythonTypeElementSpec extends Specification {

    /**
     * Gets a bean from the context for the given class name
     * @param context The context
     * @param className The class name
     * @return The bean instance
     */
    Object getBean(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBean(context.classLoader.loadClass(className), qualifier)
    }


    /**
     * Gets a bean definition from the context for the given class name
     * @param context The context
     * @param className The class name
     * @return The bean instance
     */
    BeanDefinition<?> getBeanDefinition(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBeanDefinition(context.classLoader.loadClass(className), qualifier)
    }

    /**
     * Gets a bean definition from the context for the given class name
     * @param context The context
     * @param className The class name
     * @return The bean instance
     */
    BeanIntrospection<?> getBeanIntrospection(ApplicationContext context, String className, Qualifier qualifier = null) {
        def simpleName = NameUtils.getSimpleName(className)
        def beanDefName = (simpleName.startsWith('$') ? '' : '$') + simpleName + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        try {
            return (BeanIntrospection)context.classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    /**
     * Builds a BeanIntrospection for the given Python source code.
     * The Python code should define a class that will be processed for introspection.
     * @param className The expected class name (e.g., "python.TestClass")
     * @param pythonCode The Python source code containing an @Introspected class
     * @return The BeanIntrospection for the generated class
     */
    protected BeanIntrospection buildBeanIntrospection(String className, @Language("python") String pythonCode) {
        def simpleName = NameUtils.getSimpleName(className)
        def beanDefName = (simpleName.startsWith('$') ? '' : '$') + simpleName + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        def compiler = PyronautCompiler.builder()
                .pythonCode(pythonCode)
                .build()

        ClassLoader pythonClassLoader = compiler.buildClassLoader()

        try {
            return (BeanIntrospection)pythonClassLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    protected BeanDefinition buildBeanDefinition(String packageName, String className, @Language("python") String pythonCode) {
        def beanDefName= (className.startsWith('$') ? '' : '$') + className + BeanDefinitionWriter.CLASS_SUFFIX
        String beanFullName = "${packageName}.${beanDefName}"

        def compiler = PyronautCompiler.builder()
                .pythonCode(pythonCode)
                .build()

        ClassLoader pythonClassLoader = compiler.buildClassLoader()

        try {
            return (BeanDefinition)pythonClassLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    protected BeanDefinitionReference buildBeanDefinitionReference(String packageName, String className, @Language("python") String pythonCode) {
        def beanDefName= (className.startsWith('$') ? '' : '$') + className + BeanDefinitionWriter.CLASS_SUFFIX
        String beanFullName = "${packageName}.${beanDefName}"

        def compiler = PyronautCompiler.builder()
                .pythonCode(pythonCode)
                .build()

        ClassLoader pythonClassLoader = compiler.buildClassLoader()
        (BeanDefinitionReference) pythonClassLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Builds a class element for the given Python source code.
     * @param pythonCode The Python source code
     * @param closure the callback
     * @return The class element
     */
    <T> T buildClassElement(@Language("python") String pythonCode, Closure<T> closure) {
        def localClosure = closure
        T result
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .classElementCallback { ClassElement classElement ->
                if (localClosure != null) {
                    result = localClosure?.call(classElement)
                }
                localClosure = null
            }
            .build()

        compiler.buildClassLoader()

        return result
    }

    /**
     * Builds a class element for the given Python source code.
     * @param pythonCode The Python source code
     * @param closure the callback
     * @return The class element
     */
    <T> T buildClassElement(@Language("python") String pythonCode, String simpleName, Closure<T> closure) {
        def localClosure = closure
        T result
        def compiler = PyronautCompiler.builder()
                .pythonCode(pythonCode)
                .classElementCallback { ClassElement classElement ->
                    if (localClosure != null && classElement.simpleName == simpleName) {
                        result = localClosure?.call(classElement)
                        localClosure = null
                    }
                }
                .build()

        compiler.buildClassLoader()

        return result
    }

    /**
     * Builds an {@link ApplicationContext} containing the Python processing results.
     * This includes generated Java classes, pyronaut_application.py script, and verifies
     * that the GraalPy context is properly initialized and cleaned up.
     *
     * @param pythonCode The Python source code to process
     * @return The context. Should be shutdown after use
     */
    ApplicationContext buildContext(@Language("python") String pythonCode, boolean includeAllBeans = false, Map properties = [:]) {
        // Ensure PythonContextRuntime is clean before starting
        PythonContextRuntime.resetContext()
        assert !PythonContextRuntime.isInitialized()

        // Process Python code and generate Java classes
        List<ClassElement> capturedElements = []
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .classElementCallback { ClassElement classElement ->
                capturedElements.add(classElement)
            }
            .build()

        ClassLoader pythonClassLoader = compiler.buildClassLoader()

        // Create a combined ClassLoader that includes both Python-generated classes
        // and the micronaut-context-python module for testing
        def contextPythonClassLoader = this.class.classLoader
        def combinedClassLoader = new CombinedClassLoader(pythonClassLoader, contextPythonClassLoader)

        // Build ApplicationContext with the combined ClassLoader
        def builder = ApplicationContext.builder()
        builder.classLoader(combinedClassLoader)
        builder.environments("test")
        builder.properties(properties)

        // Include built-in bean references and Python-generated bean definitions
        builder.beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(includeAllBeans))

        configureContext(builder)

        def context = builder.build().start()

        // Verify that the ApplicationContext started successfully
        assert context.isRunning(): "ApplicationContext should be running"
        return context
    }

    /**
     * @return Obtains the built in bean references required for the context to function correctly
     */
    List<BeanDefinitionReference<?>> getBuiltInBeanReferences() {
        return [
                new InterceptorRegistryBean(),
                new BeanProviderDefinition(),
                new JakartaProviderBeanDefinition(),
                new ApplicationEventPublisherFactory<>()
        ]
    }

    /**
     * Allows configuring the context
     * @param contextBuilder The context builder
     */
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
    }

    protected static void warmPool(ApplicationContext context, def executor, int size) {
        def contextExecutor = context.getBean(PythonContextExecutor)
        def acquired = new CountDownLatch(size)
        def release = new CountDownLatch(1)
        def futures = (1..size).collect {
            executor.submit {
                contextExecutor.withContext {
                    acquired.countDown()
                    release.await()
                    null
                }
            }
        }
        try {
            assert acquired.await(30, TimeUnit.SECONDS)
        } finally {
            release.countDown()
        }
        futures.each { it.get(30, TimeUnit.SECONDS) }
    }

    /**
     * Combined ClassLoader that tries the first loader first, then falls back to the second.
     * This allows us to combine Python-generated classes with the micronaut-context-python module.
     */
    private static class CombinedClassLoader extends ClassLoader {
        private final ClassLoader first
        private final ClassLoader second

        CombinedClassLoader(ClassLoader first, ClassLoader second) {
            this.first = first
            this.second = second
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return first.loadClass(name)
            } catch (ClassNotFoundException e) {
                return second.loadClass(name)
            }
        }

        @Override
        public URL getResource(String name) {
            def resource = first.getResource(name)
            if (resource == null) {
                resource = second.getResource(name)
            }
            return resource
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            def firstResources = first.getResources(name)
            def secondResources = second.getResources(name)

            def allResources = new Vector<URL>()
            while (firstResources.hasMoreElements()) {
                allResources.add(firstResources.nextElement())
            }
            while (secondResources.hasMoreElements()) {
                allResources.add(secondResources.nextElement())
            }

            return allResources.elements()
        }
    }
}
