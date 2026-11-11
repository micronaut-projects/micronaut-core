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
package io.micronaut.kotlin.processing.inject.generics

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class ResolvedGenericTypeAnnotationsSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.core.annotation.Introspected

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE)
annotation class Usage(val value: String = "occurrence")

interface Container<E, F>
open class Middle<T> : Container<List<@Usage("occurrence") T>, List<T>>
@Introspected
class Concrete : Middle<String>()

@Introspected
open class Parent<T> {
    var items: List<@Usage("occurrence") T> = listOf()
    var plain: List<T> = listOf()
}
@Introspected
class Child : Parent<String>()
'''

    void "resolved supertype arguments preserve occurrence annotations"() {
        given:
        def introspection = buildBeanIntrospection('test.Concrete', SOURCE)
        def arguments = introspection.getTypeArguments('test.Container')

        expect:
        arguments*.type == [List, List]
        arguments[0].typeParameters[0].type == String
        arguments[0].typeParameters[0].annotationMetadata.stringValue('test.Usage').get() == 'occurrence'
        arguments[1].typeParameters[0].type == String
        !arguments[1].typeParameters[0].annotationMetadata.hasAnnotation('test.Usage')
    }

    void "inherited properties preserve annotations without changing the plain occurrence"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', SOURCE)
        def items = introspection.getRequiredProperty('items', List).asArgument().typeParameters[0]
        def plain = introspection.getRequiredProperty('plain', List).asArgument().typeParameters[0]

        expect:
        items.type == String
        items.annotationMetadata.stringValue('test.Usage').get() == 'occurrence'
        plain.type == String
        !plain.annotationMetadata.hasAnnotation('test.Usage')
    }
}
