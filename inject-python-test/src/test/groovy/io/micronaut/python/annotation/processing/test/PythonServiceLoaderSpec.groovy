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
package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.python.annotation.processing.test.services.MethodContributions
import io.micronaut.python.annotation.processing.test.services.MethodContributor
import io.micronaut.python.compiler.PyronautCompiler
import spock.lang.Shared

import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Python classes registered in {@code META-INF/services} and instantiated by {@code SoftServiceLoader}.
 */
class PythonServiceLoaderSpec extends AbstractPythonTypeElementSpec {

    @Shared File sourceDir
    @Shared File resourcesDir

    def setupSpec() {
        sourceDir = File.createTempDir("python-service-loader-src", "")
        def packageDir = new File(sourceDir, "example")
        packageDir.mkdirs()
        new File(packageDir, "BookMethods.py").text = '''
from io.micronaut.python.annotation.processing.test.services import MethodContributor


class BookMethods(MethodContributor):

    def describe(self) -> str:
        return "book methods"
'''
        new File(packageDir, "Catalog.py").text = '''
from io.micronaut.python.annotation.processing.test.services import MethodContributions
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

# the services are loaded, and used, while this module is being imported
DESCRIPTIONS = list(MethodContributions.sharedDescriptions())


@Singleton
class Catalog:

    @Executable
    def entries(self) -> list[str]:
        return list(DESCRIPTIONS)

    @Executable
    def describe_all(self) -> list[str]:
        return [contributor.describe() for contributor in MethodContributions.shared()]
'''
        resourcesDir = File.createTempDir("python-service-loader-resources", "")
        def servicesDir = new File(resourcesDir, "META-INF/services")
        servicesDir.mkdirs()
        new File(servicesDir, MethodContributor.name).text = "example.BookMethods\n"
    }

    def cleanupSpec() {
        sourceDir?.deleteDir()
        resourcesDir?.deleteDir()
    }

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.pythonSrc(sourceDir.absolutePath)
        compilerBuilder.parentClassLoader(new URLClassLoader([resourcesDir.toURI().toURL()] as URL[], getClass().classLoader))
    }

    void "test a Python service instantiated by the parallel service loader at import time does not deadlock"() {
        when:
        def context = buildContext("")
        def catalog = withTimeout(60) { getBean(context, "example.Catalog") }

        then:
        catalog.entries() == ["book methods"]
        catalog.describe_all() == ["book methods"]

        cleanup:
        context?.close()
    }

    void "test a Python service held in a JVM static holder works across consecutive contexts"() {
        when:
        def first = buildContext("")
        def firstCatalog = withTimeout(60) { getBean(first, "example.Catalog") }

        then:
        firstCatalog.describe_all() == ["book methods"]

        when:
        first.close()
        def second = buildContext("")
        def secondCatalog = withTimeout(60) { getBean(second, "example.Catalog") }

        then:
        secondCatalog.describe_all() == ["book methods"]
        withTimeout(60) { MethodContributions.shared().first().describe() } == "book methods"

        cleanup:
        second?.close()
    }

    private static <T> T withTimeout(int seconds, Callable<T> action) {
        def executor = Executors.newSingleThreadExecutor()
        try {
            def future = executor.submit(action)
            try {
                return future.get(seconds, TimeUnit.SECONDS)
            } catch (TimeoutException e) {
                throw new AssertionError("Timed out after ${seconds}s; threads:\n${threadDump()}" as Object)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private static String threadDump() {
        ManagementFactory.threadMXBean.dumpAllThreads(true, true)
            .collect { it.toString() + it.stackTrace.collect { "\n    at " + it }.join("") }
            .join("\n")
    }
}
