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

import io.micronaut.core.type.Argument
import io.micronaut.core.type.WildcardArgument
import io.micronaut.inject.BeanDefinition

import java.util.function.Consumer
import java.util.function.Supplier

class WildcardTypeArgumentSpec extends AbstractPythonTypeElementSpec {

    private static WildcardArgument<?> wildcard(Argument<?> argument) {
        argument instanceof WildcardArgument ? (WildcardArgument<?>) argument : null
    }

    private static List<String> upper(Argument<?> argument) {
        wildcard(argument)?.upperBounds*.type*.name
    }

    private static List<String> lower(Argument<?> argument) {
        wildcard(argument)?.lowerBounds*.type*.name
    }

    void "a wildcard in a Java signature implemented by a Python bean is recorded on the argument it compiles to"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import WildcardContract, Book

@Singleton
class PythonContract(WildcardContract[Book]):
    def accept(self, numbers):
        pass

    def consume(self, consumer):
        pass

    def any(self, value):
        pass
'''
        when:
        def context = buildContext(pythonCode)
        BeanDefinition<?> definition = getBeanDefinition(context, "python.PythonContract")
        def accept = definition.findMethod("accept", List).get()
        def consume = definition.findMethod("consume", Consumer).get()
        def any = definition.findMethod("any", Optional).get()

        then: 'method parameters'
        accept.arguments[0].typeParameters[0].type == Number
        upper(accept.arguments[0].typeParameters[0]) == [accept.arguments[0].typeParameters[0].type.name] && lower(accept.arguments[0].typeParameters[0]) == []

        consume.arguments[0].typeParameters[0].type.name == 'io.micronaut.python.annotation.processing.test.generics.Book'
        lower(consume.arguments[0].typeParameters[0]) == [consume.arguments[0].typeParameters[0].type.name] && upper(consume.arguments[0].typeParameters[0]) == ['java.lang.Object']

        any.arguments[0].typeParameters[0].type == Object
        upper(any.arguments[0].typeParameters[0]) == ['java.lang.Object'] && lower(any.arguments[0].typeParameters[0]) == []

        and: 'the enclosing argument is not marked'
        !(accept.arguments[0] instanceof WildcardArgument)

        cleanup:
        context.close()
    }

    void "a wildcard in the type arguments of a Java interface implemented by a Python bean is recorded"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.generics import WildcardSupplier

@Singleton
class PythonSupplier(WildcardSupplier):
    def get(self) -> list:
        return []
'''
        when:
        def context = buildContext(pythonCode)
        BeanDefinition<?> definition = getBeanDefinition(context, "python.PythonSupplier")
        Argument<?> supplied = definition.getTypeArguments(Supplier)[0]

        then:
        supplied.type == List
        !(supplied instanceof WildcardArgument)
        supplied.typeParameters[0].type == Number
        upper(supplied.typeParameters[0]) == [supplied.typeParameters[0].type.name] && lower(supplied.typeParameters[0]) == []

        cleanup:
        context.close()
    }
}
