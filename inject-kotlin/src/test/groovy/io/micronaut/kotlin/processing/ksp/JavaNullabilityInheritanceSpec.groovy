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
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

/**
 * The nullability annotations of the parameters and the return type of a method of a Java interface stay on that
 * method as a Kotlin type implementing the interface sees it, whether it overrides the method or inherits it
 * (KSP #2696).
 */
class JavaNullabilityInheritanceSpec extends AbstractKotlinCompilerSpec {

    private static final String JAVA = '''
package test;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface Greeter {

    @NonNull String greet(@Nullable String name, @NonNull String greeting);

    @Nullable
    default String describe(@Nullable String name, @NonNull Integer count) {
        return name == null ? null : name + count;
    }
}
'''

    private static final String KOTLIN = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

interface InheritingGreeter : Greeter

@Singleton
@Executable
class KotlinGreeter : Greeter {
    override fun greet(name: String?, greeting: String): String = greeting + " " + (name ?: "nobody")
}
'''

    void "the nullability of the methods of a Java interface is seen from #className"() {
        given:
        Map<String, List<String>> nullability = [:]
        KotlinCompiler.compile([
                KotlinCompiler.javaSource('Greeter.java', JAVA),
                KotlinCompiler.kotlinSource('Kotlin.kt', KOTLIN)
        ], { ClassElement ce ->
            if (ce.name == className) {
                nullability.putAll(methodNullability(ce))
            }
        }, [])

        expect:
        nullability == [
                greet   : ['name:nullable', 'greeting:non-null', 'return:non-null'],
                describe: ['name:nullable', 'count:non-null', 'return:nullable']
        ]

        where:
        className << ['test.InheritingGreeter', 'test.KotlinGreeter']
    }

    void "the arguments of the executable methods of a Kotlin bean implementing a Java interface keep their nullability"() {
        given:
        def classLoader = compileWithJava()
        def definition = (classLoader.loadClass('test.$KotlinGreeter$Definition').getDeclaredConstructor().newInstance() as BeanDefinitionReference).load()
        def bean = classLoader.loadClass('test.KotlinGreeter').getDeclaredConstructor().newInstance()
        def greet = definition.findMethod('greet', String, String).get()
        def describe = definition.findMethod('describe', String, Integer).get()

        expect:
        greet.arguments.collect { it.isNullable() } == [true, false]
        greet.invoke(bean, null, 'Hello') == 'Hello nobody'
        describe.arguments.collect { it.isNullable() } == [true, false]
        describe.returnType.asArgument().isNullable()
        describe.invoke(bean, null, 1) == null
        describe.invoke(bean, 'a', 1) == 'a1'
    }

    private static String state(element) {
        element.isNullable() ? 'nullable' : element.isNonNull() ? 'non-null' : 'unknown'
    }

    private static ClassLoader compileWithJava() {
        def pair = KotlinCompiler.compile([
                KotlinCompiler.javaSource('Greeter.java', JAVA),
                KotlinCompiler.kotlinSource('Kotlin.kt', KOTLIN)
        ], { }, [])
        def classpath = []
        classpath << pair.component1().component2().outputDirectory.toURI().toURL()
        classpath.addAll(pair.component1().component1().classpaths.collect { it.toURI().toURL() })
        return new URLClassLoader(classpath as URL[], KotlinCompiler.classLoader)
    }

    private static Map<String, List<String>> methodNullability(ClassElement ce) {
        return ce.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())
                .findAll { it.name in ['greet', 'describe'] }
                .collectEntries { MethodElement m -> [m.name, parameterNullability(m) + ['return:' + state(m)]] }
    }

    private static List<String> parameterNullability(MethodElement m) {
        return m.parameters.collect { p -> p.name + ':' + state(p) }
    }
}
