/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.beans.executable

import io.micronaut.context.ApplicationContext
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

/**
 * A Kotlin method with default arguments is called through a synthetic {@code $default} method,
 * which for an interface method is emitted on the interface, on the interface's
 * {@code DefaultImpls} class, or on both, depending on the {@code -jvm-default} mode.
 *
 * <p>Only the modes emitting {@code DefaultImpls} are covered here: the compiler used by these
 * tests never tells KSP which mode it runs, so KSP always reports the {@code disable} it defaults
 * to. The {@code no-compatibility} mode, the one dropping {@code DefaultImpls}, is covered by
 * {@link DefaultArgumentsController} instead, which this module compiles and processes with
 * {@code -Xjvm-default=all}.</p>
 */
class KotlinDefaultArgumentsSpec extends Specification {

    void cleanup() {
        setJvmDefaultMode("enable")
    }

    @Unroll
    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/12638")
    void "test default arguments declared by an interface with -jvm-default=#mode"() {
        given:
        setJvmDefaultMode(mode)
        ApplicationContext context = buildContext('''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

interface MyApi {
    fun send(one: String = "default", two: String? = null): String
}

@Singleton
@Executable
open class MyController : MyApi {
    override fun send(one: String, two: String?): String {
        return "$one-$two"
    }
}
''')
        def definition = getBeanDefinition(context, 'test.MyController')
        def bean = getBean(context, 'test.MyController')
        def method = definition.executableMethods.find { it.methodName == 'send' }

        expect: "the arguments given are passed through"
        method.invoke(bean, "a", "b") == "a-b"

        and: "an absent argument gets the default declared by the interface"
        method.invoke(bean, null, null) == "default-null"

        cleanup:
        context.close()

        where:
        mode << ["enable", "disable"]
    }

    @Unroll
    void "test default arguments declared by a class with -jvm-default=#mode"() {
        given:
        setJvmDefaultMode(mode)
        ApplicationContext context = buildContext('''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
@Executable
open class MyController {
    fun send(one: String = "default", two: String? = null): String {
        return "$one-$two"
    }
}
''')
        def definition = getBeanDefinition(context, 'test.MyController')
        def bean = getBean(context, 'test.MyController')
        def method = definition.executableMethods.find { it.methodName == 'send' }

        expect:
        method.invoke(bean, "a", "b") == "a-b"
        method.invoke(bean, null, null) == "default-null"

        cleanup:
        context.close()

        where:
        mode << ["enable", "disable"]
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/12638")
    void "test default arguments declared by an interface compiled with -Xjvm-default=all"() {
        given: "a bean of this module, processed by KSP with -Xjvm-default=all"
        ApplicationContext context = ApplicationContext.builder("test").build().start()
        def definition = context.getBeanDefinition(DefaultArgumentsController)
        def bean = context.getBean(DefaultArgumentsController)
        def method = definition.executableMethods.find { it.methodName == 'send' }

        expect:
        method.invoke(bean, "a", "b") == "a-b"
        method.invoke(bean, null, null) == "default-null"

        cleanup:
        context.close()
    }
}
