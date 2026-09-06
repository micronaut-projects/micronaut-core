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
 * Member values of Python decorators whose parameters carry no type annotation keep their literal value.
 */
class UntypedDecoratorMemberSpec extends AbstractPythonTypeElementSpec {

    void "test untyped string members keep empty and single character values"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("test.Tagged")
def tagged(prefix="x", letter="y", empty="z", count=1):
    def decorator(target):
        return target
    return decorator

@Singleton
@tagged(prefix="pre", letter="a", empty="", count=42)
class Service:
    pass
'''

        when:
        def context = buildContext(pythonCode)
        BeanDefinition definition = context.getBeanDefinition(context.classLoader.loadClass("python.Service"))
        def values = definition.getAnnotation("test.Tagged").getValues()

        then:
        definition.stringValue("test.Tagged", "prefix").get() == "pre"
        definition.stringValue("test.Tagged", "letter").get() == "a"
        values.get("letter") instanceof String
        definition.stringValue("test.Tagged", "empty").isPresent()
        definition.stringValue("test.Tagged", "empty").get() == ""
        definition.intValue("test.Tagged", "count").getAsInt() == 42

        cleanup:
        context?.close()
    }
}
