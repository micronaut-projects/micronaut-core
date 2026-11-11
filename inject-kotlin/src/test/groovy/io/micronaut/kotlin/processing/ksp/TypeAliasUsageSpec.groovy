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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.kotlin.processing.visitor.TypeUseRuntimeAnn

/**
 * A type alias used as the type of a parameter, a return value, an injection point or a property is read as the type
 * it expands to: a nested alias (KSP #2588), an alias of an alias, an alias of an array, a nullable alias and a generic
 * alias that renames or reorders the type parameters of the type it expands to.
 */
class TypeAliasUsageSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.visitor.TypeUseRuntimeAnn
import jakarta.inject.Singleton

typealias Names = List<String>
typealias NameList = Names
typealias Strings = Array<String>
typealias MaybeName = String?
typealias MaybeCount = Int?
typealias Swapped<A, B> = Map<B, A>
typealias Labels<V> = Map<String, V>

typealias Motor = Engine
typealias Car = Motor
typealias Engines = List<Engine>
typealias EngineArray = Array<Engine>

class Outer {
    typealias Numbers = List<Int>
}

@Singleton
class Engine {
    val name = "v8"
}

@Singleton
class AliasService(val motor: Car, val engines: Engines, val engineArray: EngineArray) {

    @Executable
    fun join(names: NameList): String = names.joinToString(",")

    @Executable
    fun count(values: Strings): Int = values.size

    @Executable
    fun orDefault(name: MaybeName): String = name ?: "none"

    @Executable
    fun invert(map: Swapped<Int, String>): Map<Int, String> = map.entries.associate { (k, v) -> v to k }

    @Executable
    fun countOrZero(count: MaybeCount): Int = count ?: 0

    @Executable
    fun annotated(names: @TypeUseRuntimeAnn NameList): Int = names.size

    @Executable
    fun label(labels: Labels<Int>): Int = labels.values.sum()

    @Executable
    fun sum(values: Outer.Numbers): Int = values.sum()

    @Executable
    fun names(): NameList = listOf("a", "b")

    @Executable
    fun strings(): Strings = arrayOf("x", "y")
}

@Introspected
data class AliasHolder(
    val names: NameList,
    val values: Strings,
    val maybe: MaybeName,
    val count: MaybeCount,
    val swapped: Swapped<Int, String>,
    val labels: Labels<Long>,
    val numbers: Outer.Numbers
)
'''

    void "parameter and return types of aliases are the types they expand to"() {
        when:
        Map<String, String> signatures = buildClassElementMapped('test.AliasService', SOURCE) { ClassElement ce ->
            ce.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()).collectEntries { MethodElement m ->
                [(m.name): (m.parameters.collect { signature(it.genericType) + (it.isNullable() ? '?' : '') } + ['->', signature(m.genericReturnType)]).join(' ')]
            }
        } as Map<String, String>

        then:
        signatures.join == 'java.util.List<E=java.lang.String> -> java.lang.String'
        signatures.count == 'java.lang.String[] -> int'
        signatures.orDefault == 'java.lang.String? -> java.lang.String'
        signatures.countOrZero == 'java.lang.Integer? -> int'
        signatures.annotated == 'java.util.List<E=java.lang.String> -> int'
        signatures.invert == 'java.util.Map<K=java.lang.String,V=java.lang.Integer> -> java.util.Map<K=java.lang.Integer,V=java.lang.String>'
        signatures.label == 'java.util.Map<K=java.lang.String,V=java.lang.Integer> -> int'
        signatures.sum == 'java.util.List<E=java.lang.Integer> -> int'
        signatures.names == '-> java.util.List<E=java.lang.String>'
        signatures.strings == '-> java.lang.String[]'
    }

    void "an annotation on the use of an alias stays on that use"() {
        when:
        boolean annotated = buildClassElementMapped('test.AliasService', SOURCE) { ClassElement ce ->
            def method = ce.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().named('annotated'))[0]
            method.parameters[0].genericType.typeAnnotationMetadata.hasAnnotation(TypeUseRuntimeAnn)
        }

        then:
        annotated
    }

    void "constructor injection points typed by aliases are the types they expand to"() {
        when:
        List<String> injected = buildClassElementMapped('test.AliasService', SOURCE) { ClassElement ce ->
            ce.primaryConstructor.get().parameters.collect { it.name + ': ' + signature(it.genericType) }
        } as List<String>

        then:
        injected == [
                'motor: test.Engine',
                'engines: java.util.List<E=test.Engine>',
                'engineArray: test.Engine[]'
        ]
    }

    void "properties typed by aliases are the types they expand to"() {
        when:
        Map<String, String> properties = buildClassElementMapped('test.AliasHolder', SOURCE) { ClassElement ce ->
            ce.beanProperties.collectEntries { [(it.name): signature(it.genericType) + (it.isNullable() ? '?' : '')] }
        } as Map<String, String>

        then:
        properties == [
                names  : 'java.util.List<E=java.lang.String>',
                values : 'java.lang.String[]',
                maybe  : 'java.lang.String?',
                count  : 'java.lang.Integer?',
                swapped: 'java.util.Map<K=java.lang.String,V=java.lang.Integer>',
                labels : 'java.util.Map<K=java.lang.String,V=java.lang.Long>',
                numbers: 'java.util.List<E=java.lang.Integer>'
        ]
    }

    void "beans injected through aliases and executable methods taking aliases run"() {
        given:
        def context = buildContext(SOURCE)
        def definition = getBeanDefinition(context, 'test.AliasService')
        def bean = getBean(context, 'test.AliasService')
        def engine = getBean(context, 'test.Engine')

        expect:
        bean.motor.is(engine)
        bean.engines == [engine]
        bean.engineArray as List == [engine]

        and:
        definition.findMethod('join', List).get().invoke(bean, ['a', 'b']) == 'a,b'
        definition.findMethod('count', String[]).get().invoke(bean, [['a', 'b', 'c'] as String[]] as Object[]) == 3
        definition.findMethod('orDefault', String).get().invoke(bean, [null] as Object[]) == 'none'
        definition.findMethod('countOrZero', Integer).get().invoke(bean, [null] as Object[]) == 0
        definition.findMethod('annotated', List).get().invoke(bean, ['a']) == 1
        definition.findMethod('invert', Map).get().invoke(bean, [a: 1]) == [1: 'a']
        definition.findMethod('label', Map).get().invoke(bean, [a: 1, b: 2]) == 3
        definition.findMethod('sum', List).get().invoke(bean, [1, 2, 3]) == 6
        definition.findMethod('names').get().invoke(bean) == ['a', 'b']
        definition.findMethod('strings').get().invoke(bean) == ['x', 'y'] as String[]

        and: 'the runtime arguments carry the expanded type arguments'
        argumentSignature(definition.findMethod('join', List).get().arguments[0]) == 'java.util.List<E=java.lang.String>'
        argumentSignature(definition.findMethod('count', String[]).get().arguments[0]) == '[Ljava.lang.String;'
        argumentSignature(definition.findMethod('invert', Map).get().arguments[0]) == 'java.util.Map<K=java.lang.String,V=java.lang.Integer>'
        argumentSignature(definition.findMethod('label', Map).get().arguments[0]) == 'java.util.Map<K=java.lang.String,V=java.lang.Integer>'
        argumentSignature(definition.findMethod('sum', List).get().arguments[0]) == 'java.util.List<E=java.lang.Integer>'
        argumentSignature(definition.findMethod('names').get().returnType.asArgument()) == 'java.util.List<E=java.lang.String>'

        cleanup:
        context.close()
    }

    void "an introspection of properties typed by aliases reads and builds them"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.AliasHolder', SOURCE)

        when:
        def holder = introspection.instantiate(['a'], ['b'] as String[], null, null, [x: 1], [y: 2L], [3])

        then:
        introspection.getRequiredProperty('names', Object).get(holder) == ['a']
        introspection.getRequiredProperty('values', Object).get(holder) == ['b'] as String[]
        introspection.getRequiredProperty('maybe', Object).get(holder) == null
        introspection.getRequiredProperty('count', Object).get(holder) == null
        introspection.getRequiredProperty('swapped', Object).get(holder) == [x: 1]
        introspection.getRequiredProperty('labels', Object).get(holder) == [y: 2L]
        introspection.getRequiredProperty('numbers', Object).get(holder) == [3]

        and:
        introspection.constructorArguments.collect { argumentSignature(it) } == [
                'java.util.List<E=java.lang.String>',
                '[Ljava.lang.String;',
                'java.lang.String',
                'java.lang.Integer',
                'java.util.Map<K=java.lang.String,V=java.lang.Integer>',
                'java.util.Map<K=java.lang.String,V=java.lang.Long>',
                'java.util.List<E=java.lang.Integer>'
        ]
        introspection.getRequiredProperty('swapped', Object).asArgument().typeParameters*.type == [String, Integer]
    }

    private static String signature(ClassElement type) {
        if (type.isArray()) {
            return signature(type.fromArray()) + '[]'
        }
        def arguments = type.typeArguments
        if (arguments.isEmpty()) {
            return type.name
        }
        return type.name + '<' + arguments.collect { k, v -> k + '=' + signature(v) }.join(',') + '>'
    }

    private static String argumentSignature(Argument<?> argument) {
        def variables = argument.typeVariables
        if (variables.isEmpty()) {
            return argument.type.name
        }
        return argument.type.name + '<' + variables.collect { k, v -> k + '=' + argumentSignature(v) }.join(',') + '>'
    }
}
