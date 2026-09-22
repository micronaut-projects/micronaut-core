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

import io.micronaut.context.ApplicationContext
import org.graalvm.polyglot.Value

class FunctionalInterfaceOverloadSpec extends AbstractPythonTypeElementSpec {

    private static final String SOURCE = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.context.python import PythonInterop
from micronaut.python.annotation.processing.test import FunctionalOverloads
from java.util.function import Consumer

import functools
import java


@Singleton
class LambdaCaller:
    def __init__(self):
        self.overloads = FunctionalOverloads()

    @Executable
    def one_arg_function(self) -> str:
        return self.overloads.apply(lambda a: a.upper())

    @Executable
    def two_arg_function(self) -> str:
        return self.overloads.apply(lambda a, b: a + b)

    @Executable
    def one_arg_predicate(self) -> str:
        return self.overloads.check(lambda value: value == "x")

    @Executable
    def two_arg_predicate(self) -> str:
        return self.overloads.check(lambda value, count: count == 3)

    @Executable
    def zero_arg_supplier(self) -> str:
        return self.overloads.run(lambda: "value")

    @Executable
    def zero_arg_function_returning_none(self) -> str:
        return self.overloads.run(print_hello)

    @Executable
    def custom_one_arg(self) -> str:
        return self.overloads.callback(PythonInterop.fn(FunctionalOverloads.CustomCallback, lambda value: value + "!"))

    @Executable
    def custom_two_arg(self) -> str:
        return self.overloads.callback(PythonInterop.fn(FunctionalOverloads.CustomBiCallback, lambda value, count: value * count))

    @Executable
    def custom_one_arg_plain(self) -> str:
        return self.overloads.callback(lambda value: value + "!")

    @Executable
    def custom_two_arg_plain(self) -> str:
        return self.overloads.callback(lambda value, count: value * count)

    @Executable
    def explicit_consumer(self) -> str:
        seen = []
        consumer = PythonInterop.fn(Consumer, lambda value: seen.append(value))
        self.overloads.register(consumer)
        stored = self.overloads.registered()
        stored.accept("explicit")
        return f"{stored == consumer}:{stored.getClass().getInterfaces()[0].getSimpleName()}:{seen}"

    @Executable
    def bound_method(self) -> str:
        return self.overloads.check(self.two_arg_check)

    def two_arg_check(self, value, count):
        return value == "x" and count == 3

    @Executable
    def default_argument(self) -> str:
        return self.overloads.apply(lambda a, b="default": a + b)

    @Executable
    def varargs(self) -> str:
        return self.overloads.run(lambda *args: "varargs")

    @Executable
    def defaults_beyond_the_arity(self) -> str:
        return self.overloads.check(lambda a, b, c=1: True)

    @Executable
    def partial_function(self) -> str:
        return self.overloads.apply(functools.partial(join, "p"))

    @Executable
    def varargs_function(self) -> str:
        return self.overloads.apply(lambda *args: "v")

    @Executable
    def registered_is_original(self) -> bool:
        callback = lambda value: None
        self.overloads.register(callback)
        stored = self.overloads.registered()
        return stored == callback and stored is callback

    @Executable
    def registered_is_callable(self) -> bool:
        seen = []
        self.overloads.register(lambda value: seen.append(value))
        stored = self.overloads.registered()
        stored("hello")
        return seen == ["hello"]

    @Executable
    def registered_class_check(self) -> bool:
        self.overloads.register(lambda value: None)
        stored = self.overloads.registered()
        return isinstance(stored, java.type("java.util.function.Consumer"))

    @Executable
    def registered_name(self) -> str:
        self.overloads.register(lambda value: None)
        return type(self.overloads.registered()).__name__


def print_hello():
    print("hello")


def join(prefix, value):
    return prefix + value
'''

    void "Python lambdas select the functional interface overload by arity"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.LambdaCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('one_arg_function').asString() == 'function:A'
        caller.invokeMember('two_arg_function').asString() == 'bifunction:ab'
        caller.invokeMember('one_arg_predicate').asString() == 'predicate:true'
        caller.invokeMember('two_arg_predicate').asString() == 'bipredicate:true'
        caller.invokeMember('bound_method').asString() == 'bipredicate:true'
        caller.invokeMember('default_argument').asString() == 'bifunction:ab'
        caller.invokeMember('varargs').asString() == 'supplier:varargs'

        cleanup:
        ctx?.close()
    }

    void "a callable matching an arity only through defaults or varargs is chosen when nothing matches exactly"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.LambdaCaller')).asPolyglotValue()

        expect: "three parameters, one with a default: only the two-argument overload fits"
        caller.invokeMember('defaults_beyond_the_arity').asString() == 'bipredicate:true'

        cleanup:
        ctx?.close()
    }

    void "a callable whose arity cannot be read or fits every overload is converted as before"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.LambdaCaller')).asPolyglotValue()

        expect: "the arity mappings stay out of the decision, the default host interop conversion applies"
        caller.invokeMember('partial_function').asString() == 'function:pa'
        caller.invokeMember('varargs_function').asString() == 'function:v'

        cleanup:
        ctx?.close()
    }

    void "zero-argument lambdas prefer the value-returning overload"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.LambdaCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('zero_arg_supplier').asString() == 'supplier:value'
        caller.invokeMember('zero_arg_function_returning_none').asString() == 'supplier:null'

        cleanup:
        ctx?.close()
    }

    void "PythonInterop.fn adapts a callable to a custom functional interface"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.LambdaCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('custom_one_arg').asString() == 'custom:x!'
        caller.invokeMember('custom_two_arg').asString() == 'custombi:xx'
        caller.invokeMember('explicit_consumer').asString() == "True:Consumer:['explicit']"

        and: "the custom interfaces of an imported type are selected by arity as well"
        caller.invokeMember('custom_one_arg_plain').asString() == 'custom:x!'
        caller.invokeMember('custom_two_arg_plain').asString() == 'custombi:xx'

        cleanup:
        ctx?.close()
    }

    void "a lambda stored in Java and read back is the original callable"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE, true)
        Value caller = ctx.getBean(ctx.classLoader.loadClass('python.LambdaCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('registered_is_original').asBoolean()
        caller.invokeMember('registered_is_callable').asBoolean()
        caller.invokeMember('registered_class_check').asBoolean()
        caller.invokeMember('registered_name').asString() == 'function'

        cleanup:
        ctx?.close()
    }
}
