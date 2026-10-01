/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition

class SealedModifierSpec extends Specification {

    void "test introduction advice on a sealed interface doesn't compile"() {
        when:
        buildBeanDefinition('test.MyContract', '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

@Stub
@jakarta.inject.Singleton
sealed interface MyContract {

    fun someMethod(): String
}

class MyContractOnly : MyContract {
    override fun someMethod(): String {
        return "only"
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyContract'
    }

    void "test around advice on a sealed type produced by a factory doesn't compile"() {
        when:
        buildBeanDefinition('test.MyBeanFactory', '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

@Factory
class MyBeanFactory {

    @Mutating("someVal")
    @jakarta.inject.Singleton
    fun myBean(): MyBean {
        return MyBeanOnly()
    }
}

sealed class MyBean {
    open fun someMethod(): String {
        return "x"
    }
}

open class MyBeanOnly : MyBean()
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyBean'
    }

    void "test around advice on a subclass of a sealed class still compiles"() {
        when:
        def definition = buildBeanDefinition('test.$MyBean2' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating

sealed class MyBase {
    open fun someMethod(): String {
        return "x"
    }
}

@Mutating("someVal")
@jakarta.inject.Singleton
open class MyBean2 : MyBase()
''')
        then:
        definition != null
    }
}
