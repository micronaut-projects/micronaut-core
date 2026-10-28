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

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Retainable
import io.micronaut.python.annotation.processing.test.retain.Bundled
import io.micronaut.python.annotation.processing.test.retain.Contract
import io.micronaut.python.annotation.processing.test.retain.Contracted
import io.micronaut.python.annotation.processing.test.retain.Limit
import io.micronaut.python.annotation.processing.test.retain.Repeated
import io.micronaut.python.annotation.processing.test.retain.Required
import io.micronaut.python.annotation.processing.test.retain.Single

/**
 * Retained stereotypes of annotations held by a container that is not declared repeatable.
 */
class RetainedContainerSpec extends AbstractPythonTypeElementSpec {

    void "annotations in a container that is not repeatable retain what they compose"() {
        given:
        def definition = buildBeanDefinition('python', 'Test', '''
from io.micronaut.python.annotation.processing.test.retain import Bundled
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton

@Singleton
class Test:
    @Bundled.List([Bundled(min=3), Bundled(min=7)])
    @Executable
    def bundled(self) -> str:
        return ""
''')
        def bundled = definition.getRequiredMethod("bundled").getAnnotationMetadata()
                .getAnnotation(Bundled.List.name)
                .getAnnotations("value")

        expect: 'each occurrence read back from the bean definition retains what it composes, aliases applied'
        bundled*.values == [[min: 3], [min: 7]]
        bundled.collect { it.getStereotypes()*.annotationName } == [[Limit.name]] * 2
        bundled.collect { it.getStereotypes()*.values } == [[[min: 3]], [[min: 7]]]
    }
    void "annotations in a container that is not repeatable retain what they compose when a remapper set their stereotypes"() {
        given:
        def definition = buildBeanDefinition('python', 'Test', '''
from io.micronaut.python.annotation.processing.test.retain import Contracted, Repeated
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton

@Singleton
class Test:
    @Contracted.List([Contracted(message="first"), Contracted(message="second")])
    @Executable
    def bundled(self) -> str:
        return ""

    @Repeated(message="first")
    @Repeated(message="second")
    @Executable
    def repeated(self) -> str:
        return ""
''')
        List<AnnotationValue<?>> bundled = definition.getRequiredMethod("bundled").getAnnotationMetadata()
                .getAnnotation(Contracted.List.name)
                .getAnnotations("value")
        List<AnnotationValue<?>> repeated = definition.getRequiredMethod("repeated").getAnnotationMetadata()
                .getAnnotationValuesByName(Repeated.name)

        expect: 'each occurrence read back from the bean definition keeps its own values'
        bundled*.values == [[message: 'first'], [message: 'second']]
        repeated*.values == [[message: 'first'], [message: 'second']]

        and: 'each occurrence retains the contract, the marker and the composed annotation the remapper set, as in a repeatable container'
        (bundled + repeated).collect { it.getStereotypes()*.annotationName.toSet() } == [
                [Contract.name, Single.name, Required.name] as Set
        ] * 4

        and: 'the composed annotation retains its own contract'
        (bundled + repeated).every { value ->
            value.getStereotypes().findAll { it.annotationName == Required.name }*.getStereotypes()*.annotationName == [[Contract.name]]
        }

        and: 'the marker is not retained'
        (bundled + repeated).every { !it.getStereotypes()*.annotationName.contains(Retainable.name) }
    }
}
