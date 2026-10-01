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

import example.reflective.ReflectiveTrigger
import io.micronaut.python.compiler.PyronautCompiler

/**
 * The reflection data of a class that is not {@code @Introspected}: the annotations of the parameters
 * of its methods and of its attributes, which a framework instantiating and driving the generated class
 * reflectively (Azure Functions reading {@code @HttpTrigger}, picocli reading {@code @Option}) reads
 * from the generated declarations rather than from the Micronaut annotation metadata.
 */
class ReflectiveParameterAnnotationSpec extends AbstractPythonTypeElementSpec {

    private static final String ALLOW_REFLECTION_OPTION = "micronaut.introspection.allowReflection"

    String allowReflection = "python.*"

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        if (allowReflection != null) {
            compilerBuilder.options(["-A" + ALLOW_REFLECTION_OPTION + "=" + allowReflection])
        }
    }

    private static final String SOURCE = '''
from typing import Annotated

from example.reflective import ReflectiveTrigger
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class TriggerFunction:

    verbose: Annotated[bool, ReflectiveTrigger("-v")] = False

    @Executable
    def run(self, request: Annotated[str, ReflectiveTrigger("req")], plain: str) -> str:
        return request + str(self.verbose) + plain
'''

    void "the parameter annotations of a plain bean are copied onto the generated method"() {
        given:
        def context = buildContext(SOURCE)
        Class<?> functionClass = context.classLoader.loadClass("python.TriggerFunction")

        when:
        def run = functionClass.getMethod("run", String, String)

        then: "the annotated parameter carries its annotation and the unannotated one carries none"
        run.parameters[0].getAnnotation(ReflectiveTrigger)?.value() == "req"
        run.parameters[1].getAnnotation(ReflectiveTrigger) == null

        cleanup:
        context?.close()
    }

    void "the annotations of an attribute of a plain bean are copied onto its setter"() {
        given:
        def context = buildContext(SOURCE)
        Class<?> functionClass = context.classLoader.loadClass("python.TriggerFunction")

        expect: "the setter, which is what a framework binds the value through, carries the annotation"
        functionClass.getMethod("setVerbose", boolean).getAnnotation(ReflectiveTrigger)?.value() == "-v"

        and: "the getter is left alone so the annotation is declared once"
        functionClass.getMethod("getVerbose").getAnnotation(ReflectiveTrigger) == null

        cleanup:
        context?.close()
    }

    void "reflection data is left off when the gate does not allow the type"() {
        given:
        allowReflection = null
        def context = buildContext(SOURCE)
        Class<?> functionClass = context.classLoader.loadClass("python.TriggerFunction")

        expect:
        functionClass.getMethod("run", String, String).parameters[0].getAnnotation(ReflectiveTrigger) == null
        functionClass.getMethod("setVerbose", boolean).getAnnotation(ReflectiveTrigger) == null

        cleanup:
        context?.close()
    }
}
