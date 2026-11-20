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
package io.micronaut.inject.annotation.childloader

import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The values a method that does not keep them computes on every call.
 */
class ExecutableMethodCachedValuesSpec extends Specification {

    @TempDir
    Path services

    void "a method that does not keep values computes them on every call"() {
        given:
        def value = AnnotationValue.builder(ChildMark).value("mark").build()
        ExecutableMethod method = [getAnnotation: { Class type -> type == ChildMark ? value : null }] as ExecutableMethod
        int calls = 0

        expect:
        method.getCachedValue(Integer, m -> ++calls) == 1
        method.getCachedValue(Integer, m -> ++calls) == 2
        method.getCachedAnnotationValue(ChildMark).is(value)
        method.getCachedAnnotationValue(Deprecated) == null
        method.getCachedAnnotation(Deprecated) == null
    }

    void "an annotation is built with a builder of the context class loader"() {
        given:
        def value = AnnotationValue.builder(ChildMark).value("mark").build()
        ExecutableMethod method = [getAnnotation: { Class type -> value }] as ExecutableMethod
        Path entry = services.resolve("META-INF/micronaut/" + AnnotationBuilder.name + "/" + ChildMarkBuilders$io_micronaut_inject_annotation_childloader_ChildMark$AnnotationBuilder.name)
        Files.createDirectories(entry.parent)
        Files.createFile(entry)
        def application = new URLClassLoader([services.toUri().toURL()] as URL[], getClass().classLoader)
        def thread = Thread.currentThread()
        def previous = thread.contextClassLoader

        when:
        thread.contextClassLoader = application
        def built = method.getCachedAnnotation(ChildMark)

        then:
        built.value() == "mark"

        cleanup:
        thread.contextClassLoader = previous
        application.close()
    }
}
