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

import io.micronaut.core.annotation.Introspected
import io.micronaut.python.annotation.processing.test.reflective.MicronautTestMarker
import io.micronaut.python.compiler.PyronautCompiler
import jakarta.inject.Singleton

/**
 * A Micronaut annotation a module reads reflectively from the generated class rather than through the
 * annotation metadata ({@code @TestResourcesProperties} of micronaut-test-resources): it is reflection
 * data like a third-party annotation, while the Micronaut annotations the compiler and the Java
 * processing round act on stay off the generated source.
 */
class MicronautAnnotationCopySpec extends AbstractPythonTypeElementSpec {

    private static final String ALLOW_REFLECTION_OPTION = "micronaut.introspection.allowReflection"

    String allowReflection = "python.*"

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        if (allowReflection != null) {
            compilerBuilder.options(["-A" + ALLOW_REFLECTION_OPTION + "=" + allowReflection])
        }
    }

    private static final String SOURCE = '''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.reflective import MicronautTestMarker


@Introspected
@Singleton
@MicronautTestMarker("connection")
@dataclass
class ConnectionSpec:

    name: str = "default"

    @Executable
    @MicronautTestMarker("probe")
    def probe(self) -> str:
        return self.name
'''

    void "a Micronaut annotation with runtime retention is copied when the type allows reflection"() {
        given:
        def context = buildContext(SOURCE)
        Class<?> specClass = context.classLoader.loadClass("python.ConnectionSpec")

        expect: "the annotation a module reads reflectively is on the generated class and method"
        specClass.getAnnotation(MicronautTestMarker)?.value() == "connection"
        specClass.getMethod("probe").getAnnotation(MicronautTestMarker)?.value() == "probe"

        and: "the Micronaut annotations the processing rounds act on stay off the generated source"
        specClass.getAnnotation(Introspected) == null
        specClass.getAnnotation(Singleton) == null

        cleanup:
        context?.close()
    }

    void "a Micronaut annotation stays off the generated source when the gate does not allow the type"() {
        given:
        allowReflection = null
        def context = buildContext(SOURCE)
        Class<?> specClass = context.classLoader.loadClass("python.ConnectionSpec")

        expect:
        specClass.getAnnotation(MicronautTestMarker) == null
        specClass.getMethod("probe").getAnnotation(MicronautTestMarker) == null

        and: "the annotation metadata still serves it"
        getBeanIntrospection(context, "python.ConnectionSpec")
            .getAnnotation(MicronautTestMarker).stringValue().get() == "connection"

        cleanup:
        context?.close()
    }
}
