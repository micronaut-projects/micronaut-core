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

class PythonAnnotationAsClassSpec extends AbstractPythonTypeElementSpec {

    void "test a Python annotation is the generated annotation type where a Class is expected"() {
        given:
        def pythonCode = '''
from jakarta.inject import Qualifier, Singleton
from java.lang import Class
from micronaut.context.annotation import Executable


@Qualifier
def Cylinders(value: int = 4):
    def decorator(target):
        return target
    return decorator


@Singleton
class Engines:

    @Executable
    def annotation_type(self) -> Class:
        return Cylinders
'''

        when:
        def context = buildContext(pythonCode)
        def engines = getBean(context, "python.Engines")
        def annotationType = engines.annotation_type()

        then: "the annotation function converts to the annotation type generated for it"
        annotationType == context.classLoader.loadClass("python.Cylinders")
        annotationType.isAnnotation()

        cleanup:
        context?.close()
    }

    void "test a Python annotation is passed to a Java method expecting an annotation type"() {
        given:
        def pythonCode = '''
from jakarta.inject import Qualifier, Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import AnnotationMetadata


@Qualifier
def Cylinders(value: int = 4):
    def decorator(target):
        return target
    return decorator


@Cylinders(8)
class V8Engine:
    pass


@Singleton
class Engines:

    @Executable
    def is_annotated(self, metadata: AnnotationMetadata) -> bool:
        return metadata.hasAnnotation(Cylinders)
'''

        when:
        def context = buildContext(pythonCode)
        def engines = getBean(context, "python.Engines")
        def definition = getBeanDefinition(context, "python.V8Engine")

        then: "the annotation function is accepted as the annotation type of the metadata query"
        engines.is_annotated(definition.getAnnotationMetadata())

        cleanup:
        context?.close()
    }
}
