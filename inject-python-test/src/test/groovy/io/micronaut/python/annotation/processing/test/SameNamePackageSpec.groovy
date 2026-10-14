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
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.python.compiler.PyronautCompiler

/**
 * A Python package can have the name of a Java package the application imports from, which is also the
 * package the compiler generates the classes of its own modules into.
 */
class SameNamePackageSpec extends AbstractPythonTypeElementSpec {

    void "a Python module of a package named like a Java package is imported as the module"() {
        given: "example/samename/Greeter.py beside the Java package example.samename"
        def sourceDir = File.createTempDir("python-same-name-package", "")
        def packageDir = new File(sourceDir, "example/samename")
        packageDir.mkdirs()
        new File(packageDir, "Greeter.py").text = '''\
GREETING_SUFFIX = ", world"


class Greeter:
    def suffix(self) -> str:
        return GREETING_SUFFIX
'''
        new File(packageDir, "Service.py").text = '''\
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

from example.samename import Greeting
from example.samename.Greeter import GREETING_SUFFIX, Greeter


@Singleton
class Service:
    @Executable
    def greet(self) -> str:
        return Greeting.TEXT + GREETING_SUFFIX

    @Executable
    def greet_through_class(self) -> str:
        return Greeting.TEXT + Greeter().suffix()
'''
        PythonContextRuntime.resetContext()
        def classLoader = PyronautCompiler.builder()
            .pythonSrc(sourceDir.absolutePath)
            .build()
            .buildClassLoader()
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(classLoader)
            .environments("test")
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(false))
            .build()
            .start()

        when:
        def service = getBean(context, "example.samename.Service")

        then: "the Python module wins over the class generated for its own class, and the Java class still imports"
        service.greet() == "Hello, world"
        service.greet_through_class() == "Hello, world"

        cleanup:
        context?.close()
        sourceDir?.deleteDir()
        PythonContextRuntime.resetContext()
    }
}
