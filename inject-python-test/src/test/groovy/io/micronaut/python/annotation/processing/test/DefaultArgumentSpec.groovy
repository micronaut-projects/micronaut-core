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

import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.inject.ast.ClassElement

/**
 * Tests that a Python parameter with a default value is reported to core as optional through the
 * language-neutral {@link io.micronaut.inject.ast.ParameterElement#hasDefault()}, rather than
 * through the Kotlin specific marker interface.
 */
class DefaultArgumentSpec extends AbstractPythonTypeElementSpec {

    void "a Python parameter with a default value reports hasDefault"() {
        expect:
        buildClassElement('''
class Greeter:
    def greet(self, who: str, greeting: str = "hello", times: int = 1) -> str:
        return greeting

    def plain(self, who: str) -> str:
        return who
''') { ClassElement element ->
            def greet = element.findMethod("greet").get()
            assert !greet.parameters[0].hasDefault()
            assert greet.parameters[1].hasDefault()
            assert greet.parameters[2].hasDefault()

            def plain = element.findMethod("plain").get()
            assert !plain.parameters[0].hasDefault()
            return element
        }
    }

    void "a defaulted parameter on an executable method is optional at the injection point"() {
        given:
        def definition = buildBeanDefinition("python", "GreeterService", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class GreeterService:
    @Executable
    def greet(self, who: str, greeting: str = "hello") -> str:
        return greeting + who
''')

        when:
        def method = definition.findMethod("greet", String, String).get()

        then: 'the parameter without a default stays required'
        !method.arguments[0].isDeclaredNullable()

        and: 'the defaulted parameter is optional'
        method.arguments[1].isDeclaredNullable()
    }

    void "an all-defaulted Python dataclass is instantiable with no arguments"() {
        given: 'a dataclass whose constructor parameters all have literal defaults'
        def introspection = buildBeanIntrospection("python.Conf", '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Conf:
    host: str = "localhost"
    port: int = 8080
    debug: bool = False
''')

        when: 'the arguments are omitted'
        def instance = introspection.instantiate()

        then: 'the Python declared defaults are applied, not the default values of the types'
        introspection.getRequiredProperty("host", String).get(instance) == "localhost"
        introspection.getRequiredProperty("port", Integer).get(instance) == 8080
        introspection.getRequiredProperty("debug", Boolean).get(instance) == false
    }

    void "an all-defaulted Python dataclass is still constructible with its arguments"() {
        given:
        def introspection = buildBeanIntrospection("python.Conf", '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Conf:
    host: str = "localhost"
    port: int = 8080
''')

        when:
        def instance = introspection.instantiate("example.com", 9090)

        then:
        introspection.getRequiredProperty("host", String).get(instance) == "example.com"
        introspection.getRequiredProperty("port", Integer).get(instance) == 9090
    }

    void "a dataclass with a mutable default is left to the stub rather than materialised"() {
        given: 'a default_factory, which Python must evaluate itself'
        def introspection = buildBeanIntrospection("python.Tags", '''
from dataclasses import dataclass, field
from typing import List
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Tags:
    names: List[str] = field(default_factory=list)
''')

        expect: 'no caller-side default is available, so no-arg instantiation is refused'
        introspection != null

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }
}
