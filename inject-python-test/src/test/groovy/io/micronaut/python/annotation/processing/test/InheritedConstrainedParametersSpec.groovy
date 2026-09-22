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

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ProxyBeanDefinition
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.validation.Validated
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.intellij.lang.annotations.Language

import java.util.function.Consumer

/**
 * A Python class implementing a Java interface whose method parameters carry constraint annotations
 * ({@code send(@NotNull @Valid Email, @NotNull Consumer<I>)} of the email module) compiles with the
 * validation processor on the annotation processor path, and the inherited constraints are enforced.
 * Cascading into a {@code @Valid} argument needs the introspection of its class at runtime, which the
 * in-memory compilation of this suite does not provide; the test suite of the Python docs covers it.
 */
class InheritedConstrainedParametersSpec extends AbstractPythonTypeElementSpec {

    private static final String ANN_CONSTRAINT = "jakarta.validation.Constraint"
    private static final String ANN_TEST = "io.micronaut.python.annotation.processing.test.Inherited"

    @Language("python")
    private static final String SENDER = '''
from jakarta.inject import Singleton
from java.util.function import Consumer
from io.micronaut.python.annotation.processing.test import ConstrainedMessageSender, ConstrainedPayload


@Singleton
class MockMessageSender(ConstrainedMessageSender[object]):

    def __init__(self) -> None:
        self.subjects: list[str] = []

    def send(self, subject: str, request: Consumer[object]) -> str:
        self.subjects.append(subject)
        return subject

    def deliver(self, payload: ConstrainedPayload) -> ConstrainedPayload:
        return payload
'''

    void "the parameters of a Python override of a constrained Java method carry the inherited constraints"() {
        when: "the elements are inspected while the compilation is running"
        Map<String, Object> result = buildClassElement(SENDER) { classElement ->
            def query = ElementQuery.ALL_METHODS.onlyInstance().named { it == "send" || it == "deliver" }
            List<MethodElement> methods = classElement.getEnclosedElements(query)
            MethodElement send = methods.find { it.name == "send" && it.parameters.length == 2 }
            MethodElement deliver = methods.find { it.name == "deliver" }
            ParameterElement subject = send.parameters[0]
            ParameterElement request = send.parameters[1]
            ParameterElement payload = deliver.parameters[0]
            [
                subjectName: subject.name,
                subjectConstrained: subject.hasStereotype(ANN_CONSTRAINT),
                subjectNotNull: subject.getAnnotationValuesByType(NotNull).size(),
                subjectNotBlank: subject.getAnnotationValuesByType(NotBlank).size(),
                subjectMethod: subject.methodElement.name,
                subjectOwner: subject.methodElement.owningType.name,
                requestName: request.name,
                requestNotNull: request.getAnnotationValuesByType(NotNull).size(),
                requestType: request.genericType.name,
                requestTypeArgument: request.genericType.typeArguments.values()*.name,
                payloadNotNull: payload.getAnnotationValuesByType(NotNull).size(),
                payloadValid: payload.hasAnnotation(Valid),
                payloadType: payload.type.name,
                // A visitor inheriting annotations from the overridden method annotates the parameters, and
                // the annotations are kept by the parameters of a later query of the methods
                subjectAnnotated: subject.annotate(ANN_TEST).hasAnnotation(ANN_TEST),
                payloadAnnotated: payload.annotate(AnnotationValue.builder(ANN_TEST).member("value", "cascade").build()) == payload,
                subjectAnnotationKept: classElement.getEnclosedElements(query).find { it.name == "send" && it.parameters.length == 2 }.parameters[0].hasAnnotation(ANN_TEST),
                payloadAnnotationKept: classElement.getEnclosedElements(query).find { it.name == "deliver" }.parameters[0].stringValue(ANN_TEST).orElse(null),
                methodCount: methods.size()
            ]
        }

        then: "the Python override is the one method, with the Python names and the inherited constraints"
        result.methodCount == 3 // send(String, Consumer), the default send(String) and deliver(ConstrainedPayload)
        result.subjectName == "subject"
        result.subjectConstrained
        result.subjectNotNull == 1
        result.subjectNotBlank == 1
        result.subjectMethod == "send"
        result.subjectOwner == "python.MockMessageSender"
        result.requestName == "request"
        result.requestNotNull == 1
        result.requestType == Consumer.name
        result.requestTypeArgument == [Object.name]
        result.payloadNotNull == 1
        result.payloadValid
        result.payloadType == ConstrainedPayload.name
        result.subjectAnnotated
        result.payloadAnnotated
        result.subjectAnnotationKept
        result.payloadAnnotationKept == "cascade"
    }

    void "a Python implementation of a Java interface with constrained parameters is validated"() {
        given:
        def context = buildContext(SENDER, true)
        def definition = getBeanDefinition(context, "python.MockMessageSender")
        ConstrainedMessageSender sender = getBean(context, "python.MockMessageSender") as ConstrainedMessageSender

        expect: "the validation processor made the bean validated"
        definition instanceof ProxyBeanDefinition
        definition.getRequiredMethod("send", String, Consumer).hasStereotype(Validated)
        definition.getRequiredMethod("send", String, Consumer).arguments[0].annotationMetadata.hasAnnotation(NotBlank)

        when: "the Python override is called with a valid argument"
        def result = sender.send("hello", { })

        then:
        result == "hello"

        when: "the Python override is called with a blank argument"
        sender.send("", { })

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "send.subject: must not be blank"

        when: "the Python override is called with a null argument"
        sender.send(null, { })

        then:
        e = thrown(ConstraintViolationException)
        e.message.contains("send.subject: must not be")

        when: "the inherited default method is called with a blank argument"
        sender.send("")

        then:
        e = thrown(ConstraintViolationException)
        e.message == "send.subject: must not be blank"

        when: "the argument of a cascaded parameter is missing"
        sender.deliver(null)

        then:
        e = thrown(ConstraintViolationException)
        e.message == "deliver.payload: must not be null"

        cleanup:
        context?.close()
    }
}
