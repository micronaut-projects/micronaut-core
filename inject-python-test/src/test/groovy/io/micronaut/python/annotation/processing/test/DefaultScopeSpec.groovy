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

import io.micronaut.context.annotation.DefaultScope
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import jakarta.inject.Singleton

class DefaultScopeSpec extends AbstractPythonTypeElementSpec {

    void "test default scope no override"() {
        given:
        def source = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import DefaultScope

@DefaultScope(Singleton)
def SomeAnn(func):
    return func

@SomeAnn
class MyBean:
    pass
'''
        def definition = buildBeanDefinition("python", "MyBean", source)

        expect:
        definition != null
        definition.hasStereotype(DefaultScope)
        definition.hasDeclaredStereotype(Singleton)
        definition.isSingleton()
    }

    void "test default scope with override"() {
        given:
        def definition = buildBeanDefinition("python", "MyBean", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import DefaultScope, Prototype

@DefaultScope(Singleton)
def SomeAnn(func):
    return func

@SomeAnn
@Prototype
class MyBean:
    pass
''')

        expect:
        !definition.hasDeclaredStereotype(AnnotationUtil.SINGLETON)
        definition.hasDeclaredStereotype(Prototype)
        !definition.isSingleton()
    }
}
