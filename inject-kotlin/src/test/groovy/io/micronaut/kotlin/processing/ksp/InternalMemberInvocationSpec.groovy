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
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

/**
 * An {@code internal} function or property is compiled to a JVM member whose name carries the name of the module, and
 * a function taking a value class to one whose name carries a hash of its signature (KSP #2413, #2415, #2964). The
 * generated code calls them by those names.
 */
class InternalMemberInvocationSpec extends AbstractKotlinCompilerSpec {

    // The name kotlin-compile-testing gives the module it compiles
    private static final String MODULE = 'main'

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

@JvmInline
value class UserId(val value: String)

@Singleton
class InternalService {

    @Executable
    internal fun greet(name: String): String = "Hello $name"

    @Executable
    internal fun twice(count: Int): Int = count * 2

    @Executable
    fun describe(id: UserId): String = "user " + id.value

    @Executable
    internal fun describeInternal(id: UserId): String = "internal user " + id.value

    @Executable
    fun userId(): UserId = UserId("u2")
}

@Introspected
class InternalBean(internal val id: String) {
    internal var secret: String = "s"
    var name: String = "n"
}
'''

    void "internal executable methods are invoked by their JVM names"() {
        given:
        def context = buildContext(SOURCE)
        def definition = getBeanDefinition(context, 'test.InternalService')
        def bean = getBean(context, 'test.InternalService')
        def greet = jvmMethod(definition, 'greet$')
        def twice = jvmMethod(definition, 'twice$')

        expect: 'the name of an internal function carries the name of the module'
        greet.methodName == 'greet$' + MODULE
        twice.methodName == 'twice$' + MODULE
        definition.findMethod(greet.methodName, String).isPresent()

        and:
        greet.invoke(bean, 'Kotlin') == 'Hello Kotlin'
        twice.invoke(bean, 21) == 42

        cleanup:
        context.close()
    }

    void "executable methods taking or returning a value class are invoked by their JVM names"() {
        given:
        def context = buildContext(SOURCE)
        def definition = getBeanDefinition(context, 'test.InternalService')
        def bean = getBean(context, 'test.InternalService')
        def describe = jvmMethod(definition, 'describe-')
        def describeInternal = jvmMethod(definition, 'describeInternal-')
        def userId = jvmMethod(definition, 'userId-')

        expect: 'the name carries a hash of the signature, and of the module for an internal function'
        describe.methodName.matches('describe-\\w+')
        describeInternal.methodName.matches('describeInternal-\\w+\\$' + MODULE)
        userId.methodName.matches('userId-\\w+')

        and: 'a value class is passed and returned as the value it wraps'
        describe.argumentTypes == [String] as Class[]
        describe.invoke(bean, 'u1') == 'user u1'
        describeInternal.invoke(bean, 'u1') == 'internal user u1'
        userId.invoke(bean) == 'u2'

        cleanup:
        context.close()
    }

    void "internal properties are read and written by their JVM names"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.InternalBean', SOURCE)

        when:
        def bean = introspection.instantiate('i1')
        def secret = introspection.getRequiredProperty('secret', String)
        secret.set(bean, 'changed')

        then:
        introspection.propertyNames as Set == ['id', 'secret', 'name'] as Set
        introspection.getRequiredProperty('id', String).get(bean) == 'i1'
        secret.get(bean) == 'changed'
        introspection.getRequiredProperty('name', String).get(bean) == 'n'
    }

    private static ExecutableMethod jvmMethod(BeanDefinition definition, String prefix) {
        def methods = definition.executableMethods.findAll { it.methodName.startsWith(prefix) }
        assert methods.size() == 1
        return methods[0]
    }
}
