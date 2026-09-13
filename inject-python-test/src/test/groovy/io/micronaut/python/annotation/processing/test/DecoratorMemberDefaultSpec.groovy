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

import io.micronaut.inject.BeanDefinition

/**
 * Defaults declared on Python decorator members.
 */
class DecoratorMemberDefaultSpec extends AbstractPythonTypeElementSpec {

    void "test enum constant default on a typed member"() {
        given:
        def pythonCode = '''
from enum import Enum
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

class Colour(Enum):
    RED = "RED"
    GREEN = "GREEN"

@micronaut_annotation("defaults.Defaults")
def defaults_ann(enumValue: Colour = Colour.GREEN):
    def decorator(target):
        return target
    return decorator

@Singleton
@defaults_ann()
class Test:
    pass
'''

        when:
        def context = buildContext(pythonCode)
        BeanDefinition definition = context.getBeanDefinition(context.classLoader.loadClass("python.Test"))

        then: 'the generated annotation type declares the enum constant as its default'
        definition.getAnnotation("defaults.Defaults").getDefaultValues() == [enumValue: "GREEN"]

        cleanup:
        context?.close()
    }

    void "test enum constant set at the usage site"() {
        given:
        def pythonCode = '''
from enum import Enum
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

class Colour(Enum):
    RED = "RED"
    GREEN = "GREEN"

@micronaut_annotation("usage.Usage")
def usage_ann(enumValue: Colour = None):
    def decorator(target):
        return target
    return decorator

@Singleton
@usage_ann(enumValue=Colour.RED)
class Test:
    pass
'''

        when:
        def context = buildContext(pythonCode)
        BeanDefinition definition = context.getBeanDefinition(context.classLoader.loadClass("python.Test"))

        then:
        definition.stringValue("usage.Usage", "enumValue").get() == "RED"

        cleanup:
        context?.close()
    }
}
