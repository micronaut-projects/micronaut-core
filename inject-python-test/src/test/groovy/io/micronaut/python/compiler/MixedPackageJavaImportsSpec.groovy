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
import spock.lang.Shared
import spock.lang.Specification

import javax.tools.ToolProvider

/**
 * An application package sharing its name with a Java package (micronaut/context next to
 * io.micronaut.context) exports the Java members as the Java package alone does, keeps a Java type
 * bound under its name when the module of the type is imported through it, and an annotation whose
 * value holds a class accepts a class argument even when the annotation type is on the compile class
 * path only: the annotation is compiled into a directory the run time class loader does not see.
 */
class MixedPackageJavaImportsSpec extends Specification {

    private static final String CLASS_VALUED_ANNOTATION = '''
package compileonly;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ClassValued {
    Class<?> value();
}
'''

    private static final String PROBE = '''
import sys

from compileonly import ClassValued
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable, Replaces


def _imports():
    # never called: the imports make the compiler record the Java imports the probe resolves at run time
    from micronaut.context import ApplicationContext
    from micronaut.context.annotation import Requires
    from micronaut.http import HttpResponse
    from micronaut.http.HttpResponse import HttpResponse as HttpResponseType
    return ApplicationContext, Requires, HttpResponse, HttpResponseType


class Marker:
    pass


@ClassValued(Marker)
class Marked:
    pass


@Singleton
class Probe:
    @Executable
    def report(self) -> str:
        result = {}

        namespace = {}
        exec('from micronaut.context import *', namespace)
        result['star_python_member'] = namespace['Helper'].__name__
        result['star_java_class'] = namespace['ApplicationContext'].__name__
        result['star_java_subpackage'] = namespace['annotation'].__name__
        result['star_java_annotation_decorator'] = namespace['annotation'].Requires.java_class_name

        package = sys.modules['micronaut.context']
        result['all'] = ','.join(package.__all__)
        result['all_python_first'] = package.__all__.index('Helper') < package.__all__.index('ApplicationContext')
        result['dir_lists_java'] = all(name in dir(package) for name in ('Helper', 'ApplicationContext', 'annotation'))
        result['package_file'] = 'yes' if getattr(package, '__file__', None) else 'no'

        from micronaut.http import HttpResponse
        from micronaut.http.HttpResponse import HttpResponse as HttpResponseType
        import micronaut.http.HttpResponse
        result['type_module_import_keeps_class'] = micronaut.http.HttpResponse is HttpResponseType is HttpResponse
        result['type_module_keeps_python_member'] = micronaut.http.Responder().value()
        result['type_module_class_usable'] = micronaut.http.HttpResponse.ok().status().getCode()

        result['class_valued_present'] = Replaces(Marker)(Marked) is Marked
        try:
            result['class_valued_absent'] = 'ok' if ClassValued(Marker)(Marked) is Marked else 'wrong target'
        except TypeError as e:
            result['class_valued_absent'] = 'TypeError'
        result['class_valued_absent_java_class'] = ClassValued.java_class
        result['class_valued_absent_name'] = ClassValued.java_class_name
        return ';'.join(f'{key}={value}' for key, value in result.items())
'''

    @Shared
    File srcDir
    @Shared
    File annotationDir
    @Shared
    File targetDir

    def setupSpec() {
        srcDir = File.createTempDir("python-mixed-package-src", "")
        annotationDir = File.createTempDir("python-mixed-package-annotation", "")
        targetDir = File.createTempDir("python-mixed-package-target", "")
        compileAnnotation()
        writeSource("app/probe.py", PROBE)
        writeSource("micronaut/context/helper.py", '''
from jakarta.inject import Singleton


@Singleton
class Helper:
    def value(self) -> str:
        return "helper"
''')
        writeSource("micronaut/http/responder.py", '''
class Responder:
    def value(self) -> str:
        return "responder"
''')
        PythonContextRuntime.resetContext()
        PyronautCompiler.builder()
            .pythonSrc(srcDir.absolutePath)
            .targetDir(targetDir)
            .classpath([annotationDir])
            .build()
            .compile()
    }

    def cleanupSpec() {
        PythonContextRuntime.resetContext()
        srcDir.deleteDir()
        annotationDir.deleteDir()
        targetDir.deleteDir()
    }

    def "the manifest records an annotation whose value member holds a class"() {
        when:
        def manifest = JavaImportsManifest.read(targetDir)

        then:
        manifest.member("compileonly", "ClassValued") == ["compileonly.ClassValued", "annotation", "class-value"]
        manifest.member("micronaut.context.annotation", "Replaces") == ["io.micronaut.context.annotation.Replaces", "annotation", "class-value"]
        manifest.member("jakarta.inject", "Singleton") == ["jakarta.inject.Singleton", "annotation"]
        manifest.member("micronaut.context.annotation", "Executable") == ["io.micronaut.context.annotation.Executable", "annotation"]
    }

    def "a mixed package exports the Java members, keeps an imported type module's class and accepts a class value of an absent annotation"() {
        given: "the annotation type is on the compile class path only"
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()

        when:
        def probe = context.getBean(classLoader.loadClass("app.Probe"))
        Map<String, String> report = probe.report().split(';').collectEntries { it.split('=', 2) as List }

        then: "a star import of the mixed package binds the Python members, the Java classes, annotations and sub-packages"
        report.star_python_member == "Helper"
        report.star_java_class == "ApplicationContext"
        report.star_java_subpackage == "micronaut.context.annotation"
        report.star_java_annotation_decorator == "io.micronaut.context.annotation.Requires"

        and: "__all__ lists the Python members first, then the Java members and sub-packages, and dir() lists them all"
        report.all.split(',').toList().containsAll(['Helper', 'ApplicationContext', 'annotation'])
        report.all_python_first == "True"
        report.dir_lists_java == "True"
        report.package_file == "yes"

        and: "importing the module of a Java type through the mixed package keeps the type bound on it"
        report.type_module_import_keeps_class == "True"
        report.type_module_keeps_python_member == "responder"
        report.type_module_class_usable == "200"

        and: "a class argument of an annotation whose value holds a class is a value, whether the type is present or absent at run time"
        report.class_valued_present == "True"
        report.class_valued_absent == "ok"
        report.class_valued_absent_java_class == "None"
        report.class_valued_absent_name == "compileonly.ClassValued"

        cleanup:
        context?.close()
        classLoader?.close()
    }

    private void writeSource(String path, String content) {
        def file = new File(srcDir, path)
        file.parentFile.mkdirs()
        file.text = content
    }

    private void compileAnnotation() {
        def source = new File(annotationDir, "compileonly/ClassValued.java")
        source.parentFile.mkdirs()
        source.text = CLASS_VALUED_ANNOTATION
        def compiler = ToolProvider.getSystemJavaCompiler()
        int result = compiler.run(null, null, null, "-d", annotationDir.absolutePath, "-proc:none", source.absolutePath)
        assert result == 0
        source.delete()
    }
}
