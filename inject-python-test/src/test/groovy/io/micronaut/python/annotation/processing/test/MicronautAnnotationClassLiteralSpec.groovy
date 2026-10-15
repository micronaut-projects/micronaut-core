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

import io.micronaut.python.annotation.processing.test.reflective.GenericHandler
import io.micronaut.python.annotation.processing.test.reflective.MicronautClassMarker
import io.micronaut.python.annotation.processing.test.reflective.MicronautInternalMarker
import io.micronaut.python.compiler.PyronautCompiler

/**
 * The Micronaut annotations copied onto a type that allows reflection: an {@code @Internal} one
 * ({@code @DataMethod} of micronaut-data) is served by the annotation metadata and stays off the
 * generated source, and a class member of a copied one is written as the class literal of the raw type.
 */
class MicronautAnnotationClassLiteralSpec extends AbstractPythonTypeElementSpec {

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.options(["-Amicronaut.introspection.allowReflection=python.*"])
    }

    void "an internal Micronaut annotation is not copied when the type allows reflection"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.reflective import GenericHandler, MicronautInternalMarker


@Introspected
@MicronautInternalMarker(GenericHandler)
@dataclass
class InternalSpec:

    name: str = "default"

    @Executable
    @MicronautInternalMarker(GenericHandler)
    def probe(self) -> str:
        return self.name
''')
        Class<?> specClass = context.classLoader.loadClass("python.InternalSpec")

        expect:
        specClass.getAnnotation(MicronautInternalMarker) == null
        specClass.getMethod("probe").getAnnotation(MicronautInternalMarker) == null

        and: "the annotation metadata still serves it"
        getBeanIntrospection(context, "python.InternalSpec")
            .classValue(MicronautInternalMarker).get() == GenericHandler

        cleanup:
        context?.close()
    }

    void "a class member naming a generic type is copied as a raw class literal"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected
from micronaut.context.annotation import Executable
from java.util import ArrayList
from io.micronaut.python.annotation.processing.test.reflective import GenericHandler, MicronautClassMarker


@Introspected
@MicronautClassMarker(value=GenericHandler, others=[GenericHandler, ArrayList])
@dataclass
class ClassLiteralSpec:

    name: str = "default"

    @Executable
    @MicronautClassMarker(GenericHandler)
    def probe(self) -> str:
        return self.name
''')
        Class<?> specClass = context.classLoader.loadClass("python.ClassLiteralSpec")

        expect:
        specClass.getAnnotation(MicronautClassMarker).value() == GenericHandler
        specClass.getAnnotation(MicronautClassMarker).others() == [GenericHandler, ArrayList] as Class[]
        specClass.getMethod("probe").getAnnotation(MicronautClassMarker).value() == GenericHandler

        cleanup:
        context?.close()
    }
}
