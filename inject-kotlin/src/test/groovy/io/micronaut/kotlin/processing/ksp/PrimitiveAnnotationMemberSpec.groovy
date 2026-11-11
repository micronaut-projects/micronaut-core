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
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement

/**
 * The members of an annotation typed {@code Byte}, {@code Short}, {@code Char}, {@code Long}, {@code Float} and
 * {@code Double} keep the values they are given, a conversion such as {@code 1.toByte()} given as the first argument
 * included (KSP #2672).
 */
class PrimitiveAnnotationMemberSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import jakarta.inject.Singleton

@Retention(AnnotationRetention.RUNTIME)
annotation class Numbers(
    val b: Byte,
    val s: Short = 0,
    val c: Char = 'a',
    val l: Long = 0,
    val f: Float = 0f,
    val d: Double = 0.0
)

@Retention(AnnotationRetention.RUNTIME)
annotation class ByteFirst(val b: Byte, val name: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class NumberArrays(val bytes: ByteArray = [], val shorts: ShortArray = [], val chars: CharArray = [], val longs: LongArray = [])

@Singleton
@Numbers(1.toByte(), 2.toShort(), 'x', 4L, 5.5f, 6.5)
@ByteFirst((-7).toByte(), "seven")
@NumberArrays(bytes = [1.toByte(), 2.toByte()], shorts = [3.toShort()], chars = ['y'], longs = [Long.MAX_VALUE])
class Positional

@Singleton
@Numbers(b = 1.toByte(), s = 2.toShort(), c = 'x', l = 4L, f = 5.5f, d = 6.5)
class Named

@Singleton
@Numbers(Byte.MAX_VALUE)
class Defaults
'''

    void "the values of primitive members are read from #className"() {
        when:
        Map<String, Object> values = buildClassElementMapped(className, SOURCE) { ClassElement ce ->
            numbers(ce)
        }

        then:
        values == expected

        where:
        className         | expected
        'test.Positional' | [b: (byte) 1, s: (short) 2, c: (char) 'x', l: 4L, f: 5.5f, d: 6.5d]
        'test.Named'      | [b: (byte) 1, s: (short) 2, c: (char) 'x', l: 4L, f: 5.5f, d: 6.5d]
        'test.Defaults'   | [b: (byte) 127, s: (short) 0, c: (char) 'a', l: 0L, f: 0f, d: 0d]
    }

    void "a byte given as the first argument is read along with the members after it"() {
        when:
        Map<String, Object> values = buildClassElementMapped('test.Positional', SOURCE) { ClassElement ce ->
            [
                    b     : ce.getValue('test.ByteFirst', 'b', Byte).orElse(null),
                    name  : ce.stringValue('test.ByteFirst', 'name').orElse(null),
                    bytes : ce.getValue('test.NumberArrays', 'bytes', byte[]).orElse(null) as List,
                    shorts: ce.getValue('test.NumberArrays', 'shorts', short[]).orElse(null) as List,
                    chars : ce.getValue('test.NumberArrays', 'chars', char[]).orElse(null) as List,
                    longs : ce.getValue('test.NumberArrays', 'longs', long[]).orElse(null) as List
            ]
        }

        then:
        values == [
                b     : (byte) -7,
                name  : 'seven',
                bytes : [(byte) 1, (byte) 2],
                shorts: [(short) 3],
                chars : [(char) 'y'],
                longs : [Long.MAX_VALUE]
        ]
    }

    void "the values of primitive members are written to the bean definition of #className"() {
        given:
        def definition = buildBeanDefinition(className, SOURCE)

        expect:
        numbers(definition.annotationMetadata) == expected

        and: 'the annotation synthesized from the metadata holds them too'
        def numbers = definition.synthesize(definition.beanType.classLoader.loadClass('test.Numbers'))
        [b: numbers.b(), s: numbers.s(), c: numbers.c(), l: numbers.l(), f: numbers.f(), d: numbers.d()] == expected

        where:
        className         | expected
        'test.Positional' | [b: (byte) 1, s: (short) 2, c: (char) 'x', l: 4L, f: 5.5f, d: 6.5d]
        'test.Named'      | [b: (byte) 1, s: (short) 2, c: (char) 'x', l: 4L, f: 5.5f, d: 6.5d]
        'test.Defaults'   | [b: (byte) 127, s: (short) 0, c: (char) 'a', l: 0L, f: 0f, d: 0d]
    }

    void "a byte given as the first argument is written to the bean definition"() {
        given:
        def definition = buildBeanDefinition('test.Positional', SOURCE)

        expect:
        definition.getValue('test.ByteFirst', 'b', Byte).get() == (byte) -7
        definition.stringValue('test.ByteFirst', 'name').get() == 'seven'
        definition.getValue('test.NumberArrays', 'bytes', byte[]).get() == [1, 2] as byte[]
        definition.getValue('test.NumberArrays', 'shorts', short[]).get() == [3] as short[]
        definition.getValue('test.NumberArrays', 'chars', char[]).get() == ['y' as char] as char[]
        definition.getValue('test.NumberArrays', 'longs', long[]).get() == [Long.MAX_VALUE] as long[]
    }

    private static Map<String, Object> numbers(AnnotationMetadata metadata) {
        [
                b: metadata.getValue('test.Numbers', 'b', Byte).orElse(null),
                s: metadata.getValue('test.Numbers', 's', Short).orElse(null),
                c: metadata.getValue('test.Numbers', 'c', Character).orElse(null),
                l: metadata.getValue('test.Numbers', 'l', Long).orElse(null),
                f: metadata.getValue('test.Numbers', 'f', Float).orElse(null),
                d: metadata.getValue('test.Numbers', 'd', Double).orElse(null)
        ]
    }
}
