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
package io.micronaut.kotlin.processing.ksp

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

/**
 * A {@code vararg} parameter is an array: one of a primitive type for a primitive element, and one of the element type
 * otherwise (KSP #2379). An executable method taking one is invoked with that array.
 */
class VarargExecutableMethodSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
class Varargs {

    @Executable
    fun sum(vararg values: Int): Int = values.sum()

    @Executable
    fun longs(vararg values: Long): Long = values.sum()

    @Executable
    fun join(separator: String, vararg parts: String): String = parts.joinToString(separator)

    @Executable
    fun nullableParts(vararg parts: String?): Int = parts.count { it == null }

    @Executable
    fun lists(vararg lists: List<Int>): Int = lists.sumOf { it.size }

    @Executable
    fun <T : CharSequence> lengths(vararg values: T): Int = values.sumOf { it.length }
}
'''

    void "vararg parameters are arrays of their element types"() {
        when:
        Map<String, List<String>> parameters = buildClassElementMapped('test.Varargs', SOURCE) { ClassElement ce ->
            ce.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()).collectEntries { MethodElement m ->
                [(m.name): (m.isVarArgs() ? ['varargs'] : []) + m.parameters.collect { p -> p.type.name + (p.type.isArray() ? '[]' : '') }]
            }
        }

        then:
        parameters == [
                sum          : ['varargs', 'int[]'],
                longs        : ['varargs', 'long[]'],
                join         : ['varargs', 'java.lang.String', 'java.lang.String[]'],
                nullableParts: ['varargs', 'java.lang.String[]'],
                lists        : ['varargs', 'java.util.List[]'],
                lengths      : ['varargs', 'java.lang.CharSequence[]']
        ]
    }

    void "executable methods taking varargs are invoked with arrays"() {
        given:
        def context = buildContext(SOURCE)
        def definition = getBeanDefinition(context, 'test.Varargs')
        def bean = getBean(context, 'test.Varargs')

        expect:
        definition.findMethod('sum', int[]).get().invoke(bean, [[1, 2, 3] as int[]] as Object[]) == 6
        definition.findMethod('sum', int[]).get().invoke(bean, [new int[0]] as Object[]) == 0
        definition.findMethod('longs', long[]).get().invoke(bean, [[Long.MAX_VALUE - 1, 1L] as long[]] as Object[]) == Long.MAX_VALUE
        definition.findMethod('join', String, String[]).get().invoke(bean, '-', ['a', 'b'] as String[]) == 'a-b'
        definition.findMethod('nullableParts', String[]).get().invoke(bean, [['a', null, null] as String[]] as Object[]) == 2
        definition.findMethod('lists', List[]).get().invoke(bean, [[[1], [2, 3]] as List[]] as Object[]) == 3
        definition.findMethod('lengths', CharSequence[]).get().invoke(bean, [['ab', 'c'] as CharSequence[]] as Object[]) == 3

        and:
        definition.findMethod('sum', int[]).get().arguments[0].type == int[]
        definition.findMethod('join', String, String[]).get().arguments*.type == [String, String[]]
        definition.findMethod('lists', List[]).get().arguments[0].type == List[]

        cleanup:
        context.close()
    }
}
