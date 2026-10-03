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

import io.micronaut.aop.writer.RuntimeProxyBeanDefinitionWriter
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.inject.ProxyBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.validation.Validated
import jakarta.validation.ConstraintViolationException
import org.intellij.lang.annotations.Language

/**
 * Constrained members make a Python bean validated without an explicit {@code @Validated},
 * the way the validation visitor makes a Java bean validated at compile time. Cascading into a
 * {@code @Valid} dataclass needs the introspection of the generated class at runtime, which the
 * in-memory compilation of this suite does not expose; the test suite of the Python docs covers it.
 */
class ImplicitValidationSpec extends AbstractPythonTypeElementSpec {

    void "constrained method parameters are validated without an explicit Validated decorator"() {
        given:
        @Language("python") def pythonCode = '''
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation import ConstraintViolationException
from jakarta.validation.constraints import NotBlank
from micronaut.context.annotation import Executable


@Singleton
class PersonService:
    def say_hello(self, name: Annotated[str, NotBlank]) -> str:
        return f"Hello {name}"


@Singleton
class PersonCaller:
    def __init__(self, service: PersonService):
        self.service = service

    @Executable
    def call(self, name: str) -> str:
        try:
            return self.service.say_hello(name)
        except ConstraintViolationException as exception:
            return "violation: " + exception.getMessage()
'''
        def context = buildContext(pythonCode, true)
        def definition = getBeanDefinition(context, "python.PersonService")
        def service = getBean(context, "python.PersonService")
        def caller = getBean(context, "python.PersonCaller")

        expect: "the bean is proxied by the Python runtime proxy"
        definition instanceof ProxyBeanDefinition
        definition.class.name == 'python.$PersonService' + RuntimeProxyBeanDefinitionWriter.RUNTIME_PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX
        definition.executableMethods.find { it.methodName == "say_hello" }.hasStereotype(Validated)

        when: "a Java caller passes a valid argument"
        def result = service.say_hello("Fred")

        then:
        result == "Hello Fred"

        when: "a Java caller passes an invalid argument"
        service.say_hello("")

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "say_hello.name: must not be blank"

        when: "a Python caller passes an invalid argument"
        result = caller.call("")

        then:
        result == "violation: say_hello.name: must not be blank"

        when: "a Python caller passes a valid argument"
        result = caller.call("Bob")

        then:
        result == "Hello Bob"

        cleanup:
        context?.close()
    }

    void "a constrained return value is validated without an explicit Validated decorator"() {
        given:
        @Language("python") def pythonCode = '''
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank


@Singleton
class NameService:
    def name(self, value: str) -> Annotated[str, NotBlank]:
        return value
'''
        def context = buildContext(pythonCode, true)
        def service = getBean(context, "python.NameService")

        when:
        def result = service.name("Fred")

        then:
        result == "Fred"

        when:
        service.name("")

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "name.<return value>: must not be blank"

        cleanup:
        context?.close()
    }

    void "constrained constructor parameters are validated when the bean is created"() {
        given:
        @Language("python") def pythonCode = '''
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
from micronaut.context.annotation import Property


@Singleton
class ConfiguredService:
    def __init__(self, name: Annotated[str, NotBlank, Property(name="app.name")]):
        self.name = name

    def current_name(self) -> str:
        return self.name
'''
        def context = buildContext(pythonCode, true, ["app.name": ""])

        when:
        getBean(context, "python.ConfiguredService")

        then:
        def e = thrown(DependencyInjectionException)
        e.cause instanceof BeanInstantiationException
        e.cause.message.contains("python.ConfiguredService.name - must not be blank")

        cleanup:
        context?.close()
    }
}
