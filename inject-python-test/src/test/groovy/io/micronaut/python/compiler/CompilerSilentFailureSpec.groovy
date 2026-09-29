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
package io.micronaut.python.compiler

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.python.processing.PythonAnnotationProcessor
import spock.lang.Specification

/**
 * Failures of the Python compiler that used to be silent or opaque: a Python package coinciding
 * with an imported Java package, a Java import outside the Micronaut packages, and two
 * definitions of one generated Java class.
 */
class CompilerSilentFailureSpec extends Specification {

    File srcDir
    File targetDir

    def setup() {
        srcDir = File.createTempDir("python-silent-failure-src", "")
        targetDir = File.createTempDir("python-silent-failure-target", "")
        PythonContextRuntime.resetContext()
    }

    def cleanup() {
        PythonContextRuntime.resetContext()
        srcDir.deleteDir()
        targetDir.deleteDir()
    }

    def "a Python package coinciding with an imported Java package serves the Java members from its initializer"() {
        given: "a module in the package micronaut.context, which is also imported as a Java package with a sub-package"
        writeSource("micronaut/context/helper.py", '''
from jakarta.inject import Singleton
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Executable


@Singleton
class Helper:
    def __init__(self, context: ApplicationContext):
        self.context = context

    @Executable
    def value(self) -> str:
        return "ok" if self.context is not None else "missing"

    @Executable
    def probes(self) -> str:
        import sys
        namespace = {}
        exec('from micronaut.context import *', namespace)
        import micronaut.context.ApplicationContext
        import micronaut.context as pkg
        # a Java member the initializer serves is bound on the package, so it resolves once; a class the
        # class path lacks keeps the identity of its facade, as the generated package bindings did
        import micronaut_java_imports
        micronaut_java_imports._micronaut_java_imports().members['micronaut.context']['Missing'] = ('missing.Type', 'class')
        missing = pkg.Missing
        return ';'.join([
            'star_java=' + str('ApplicationContext' in namespace and 'annotation' in namespace),
            'star_python=' + str('Helper' in namespace),
            'type_module_keeps_class=' + str(not isinstance(pkg.ApplicationContext, type(sys)) and getattr(pkg.ApplicationContext, 'class').getName() == 'io.micronaut.context.ApplicationContext'),
            'member_binds=' + str('ApplicationContext' in vars(pkg)),
            'missing_class_binds=' + str('Missing' in vars(pkg) and missing is pkg.Missing),
            'missing_class_facade=' + str(type(missing).__name__ == '_MicronautJavaType'),
        ])
''')

        when:
        compile()
        def members = packageMembers(vfsFile("micronaut/context"))
        def manifest = JavaImportsManifest.read(targetDir)

        then: "the application module is the package's only contribution; the Java members are recorded for the runtime, which the initializer falls back to (a module importing a Java type from its own package while it initializes is served that way too)"
        members.contains("from .helper import Helper")
        !members.contains("ApplicationContext")
        vfsFile("micronaut/context/__init__.py").text.contains("__micronaut_merge_members")
        vfsFile("micronaut/context/__init__.py").text.contains("__micronaut_java_package_attribute")
        vfsFile("micronaut/context/helper.py").exists()
        !vfsFile("micronaut/context/annotation").exists()
        manifest.packages["micronaut.context"] == "io.micronaut.context"
        manifest.packages["micronaut.context.annotation"] == "io.micronaut.context.annotation"
        manifest.member("micronaut.context", "ApplicationContext") == ["io.micronaut.context.ApplicationContext", "interface"]
        manifest.member("micronaut.context.annotation", "Executable") == ["io.micronaut.context.annotation.Executable", "annotation"]

        when: "the application starts"
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()
        def helper = context.getBean(classLoader.loadClass("micronaut.context.Helper"))

        then:
        helper.value() == "ok"

        and: "a star import binds the Java members and sub-packages next to the Python members, importing a type's module keeps the type on the package, and a resolved member (a facade of an absent class included) is bound on the package"
        helper.probes() == "star_java=True;star_python=True;type_module_keeps_class=True;member_binds=True;missing_class_binds=True;missing_class_facade=True"

        cleanup:
        context?.close()
        classLoader?.close()
    }

    def "a module directly inside an imported Java package path is compiled and its package serves the Java members"() {
        given: "a module in the package jakarta.inject, which is imported for its annotations"
        writeSource("jakarta/inject/registry.py", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class Registry:
    @Executable
    def value(self) -> str:
        return "registered"
''')

        when:
        compile()
        def members = packageMembers(vfsFile("jakarta/inject"))

        then:
        !members.contains("Singleton")
        members.contains("from .registry import Registry")
        vfsFile("jakarta/inject/__init__.py").text.contains("__micronaut_merge_members")
        JavaImportsManifest.read(targetDir).member("jakarta.inject", "Singleton") == ["jakarta.inject.Singleton", "annotation"]

        when:
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()
        def registry = context.getBean(classLoader.loadClass("jakarta.inject.Registry"))

        then:
        registry.value() == "registered"

        cleanup:
        context?.close()
        classLoader?.close()
    }

    def "a module named like a Java type imported from its package is reported"() {
        given:
        writeSource("jakarta/inject/Singleton.py", '''
from jakarta.inject import Singleton


@Singleton
class Registry:
    pass
''')

        when:
        compile()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Python source [jakarta/inject/Singleton.py] is named like the Java type [jakarta.inject.Singleton] imported from its package")
    }

    def "importing a Java class outside the Micronaut packages compiles the module and its bean"() {
        given:
        writeSource("app/logging_service.py", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from org.slf4j import LoggerFactory

LOG = LoggerFactory.getLogger("app.logging_service")


@Singleton
class LoggingService:
    @Executable
    def value(self) -> str:
        LOG.info("value requested")
        return "logged"
''')

        when:
        compile()

        then:
        new File(targetDir, "app/LoggingService.class").exists()
        JavaImportsManifest.read(targetDir).member("org.slf4j", "LoggerFactory") == ["org.slf4j.LoggerFactory", "class"]
        !vfsFile("org").exists()

        when:
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()
        def service = context.getBean(classLoader.loadClass("app.LoggingService"))

        then:
        service.value() == "logged"

        cleanup:
        context?.close()
        classLoader?.close()
    }

    def "two top-level classes of one name in one package are a compile error instead of overwriting each other"() {
        given:
        writeSource("app/first.py", '''
from jakarta.inject import Singleton


@Singleton
class Service:
    def value(self) -> str:
        return "first"
''')
        writeSource("app/second.py", '''
from jakarta.inject import Singleton


@Singleton
class Service:
    def value(self) -> str:
        return "second"
''')

        when:
        compile()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Duplicate Python type [app.Service]")
        e.message.contains("first.py")
        e.message.contains("second.py")
    }

    def "a module of decorated functions named like a class of another module is a compile error"() {
        given:
        writeSource("app/service.py", '''
from micronaut.context.annotation import Executable


@Executable
def run() -> str:
    return "run"
''')
        writeSource("app/models.py", '''
class Service:
    pass
''')

        when:
        compile()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Duplicate Python type [app.Service]")
    }

    def "module-level assignments named like a class of another module do not conflict"() {
        given:
        writeSource("app/service.py", '''
NAME = "service"
''')
        writeSource("app/models.py", '''
from jakarta.inject import Singleton


@Singleton
class Service:
    def value(self) -> str:
        return "service"
''')

        when:
        compile()

        then:
        new File(targetDir, "app/Service.class").exists()
    }

    def "module-private helper classes of one name in several modules of a package compile and run"() {
        given: "two modules each defining a plain Helper next to the service using it"
        writeSource("app/a.py", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


class Helper:
    def name(self) -> str:
        return "a"


@Singleton
class ServiceA:
    @Executable
    def value(self) -> str:
        return Helper().name()
''')
        writeSource("app/b.py", '''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@dataclass
class Helper:
    suffix: str = "b"

    def name(self) -> str:
        return self.suffix


@Singleton
class ServiceB:
    @Executable
    def value(self) -> str:
        return Helper().name()
''')

        when:
        compile()
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()

        then:
        context.getBean(classLoader.loadClass("app.ServiceA")).value() == "a"
        context.getBean(classLoader.loadClass("app.ServiceB")).value() == "b"

        cleanup:
        context?.close()
        classLoader?.close()
    }

    def "an annotated class of one name wins over a plain class of another module whatever the module order"() {
        given: "the plain class sorts after the bean"
        writeSource("app/${beanModule}.py", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class Service:
    @Executable
    def value(self) -> str:
        return "bean"
''')
        writeSource("app/${plainModule}.py", '''
class Service:
    def value(self) -> str:
        return "plain"
''')

        when:
        compile()
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()

        then:
        context.getBean(classLoader.loadClass("app.Service")).value() == "bean"

        cleanup:
        context?.close()
        classLoader?.close()

        where:
        beanModule | plainModule
        "a"        | "b"
        "b"        | "a"
    }

    def "a class annotated with an application-defined decorator conflicts with another annotated class"() {
        given:
        writeSource("app/timed.py", '''
from micronaut.aop import Around


@Around
def Timed(func):
    return func
''')
        writeSource("app/first.py", '''
from .timed import Timed


@Timed
class Job:
    def run(self) -> str:
        return "first"
''')
        writeSource("app/second.py", '''
from jakarta.inject import Singleton


@Singleton
class Job:
    def run(self) -> str:
        return "second"
''')

        when:
        compile()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Duplicate Python type [app.Job]")
    }

    def "a class and a module of decorated functions of one name clash in the default package too"() {
        given:
        writeSource("service.py", '''
from micronaut.context.annotation import Executable


@Executable
def run() -> str:
    return "run"
''')
        writeSource("models.py", '''
from jakarta.inject import Singleton


@Singleton
class Service:
    pass
''')

        when:
        compile()

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Duplicate Python type [python.Service]")
    }

    private void writeSource(String relativePath, String code) {
        def file = new File(srcDir, relativePath)
        file.parentFile.mkdirs()
        file.text = code
    }

    private void compile() {
        PyronautCompiler.builder()
            .pythonSrc(srcDir.absolutePath)
            .targetDir(targetDir)
            .build()
            .compile()
    }

    private File vfsFile(String relativePath) {
        new File(targetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}${relativePath}")
    }

    /**
     * The members contributed to a package by the given number of compilations, written to members modules next
     * to the initializer that merges them.
     */
    private static String packageMembers(File packageDirectory, int contributions = 1) {
        def modules = packageDirectory.listFiles()
            .findAll { it.name.startsWith(PythonAnnotationProcessor.PACKAGE_MEMBERS_MODULE_PREFIX) }
            .sort { it.name }
        assert modules.size() == contributions : "${contributions} members module(s) expected in ${packageDirectory}: ${modules*.name}"
        modules*.text.join('\n')
    }
}
