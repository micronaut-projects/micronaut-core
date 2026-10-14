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
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

/**
 * The extension receiver of a member extension function is passed to its {@code $default}
 * method as the first parameter, but the Kotlin compiler gives only the value parameters a bit
 * in the defaults mask.
 */
class KotlinExtensionDefaultArgumentsSpec extends Specification {

    void "test default arguments of a member extension function"() {
        given:
        ApplicationContext context = buildContext('''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
open class Greeter {
    @Executable
    open fun String.greet(prefix: String = "Hi", suffix: String = "!"): String = "$prefix $this$suffix"
}
''')
        def definition = getBeanDefinition(context, 'test.Greeter')
        def bean = getBean(context, 'test.Greeter')
        def method = definition.executableMethods.find { it.methodName == 'greet' }

        expect: "the arguments given are passed through"
        method.invoke(bean, "Bob", "Hello", "?") == "Hello Bob?"

        and: "an absent argument gets its own default"
        method.invoke(bean, "Bob", null, "?") == "Hi Bob?"
        method.invoke(bean, "Bob", "Hello", null) == "Hello Bob!"
        method.invoke(bean, "Bob", null, null) == "Hi Bob!"

        cleanup:
        context.close()
    }

    void "test default arguments of a member extension function with 32 value parameters"() {
        given:
        def parameters = (1..32).collect { "p$it: String = \"d$it\"" }.join(", ")
        def body = (1..32).collect { "\$p$it" }.join(",")
        ApplicationContext context = buildContext("""
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
open class Joiner {
    @Executable
    open fun String.join($parameters): String = "\$this:$body"
}
""")
        def definition = getBeanDefinition(context, 'test.Joiner')
        def bean = getBean(context, 'test.Joiner')
        def method = definition.executableMethods.find { it.methodName == 'join' }
        Object[] arguments = new Object[33]
        arguments[0] = "r"
        (1..32).each { arguments[it] = it % 2 == 0 ? null : "v$it".toString() }

        expect: "each absent argument gets its own default"
        method.invoke(bean, arguments) == "r:" + (1..32).collect { it % 2 == 0 ? "d$it" : "v$it" }.join(",")

        cleanup:
        context.close()
    }
}
