/*
 * Copyright 2017-2019 original authors
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

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.aop.Intercepted
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition
import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class FinalModifierSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2530')
    void 'test final modifier on external class produced by factory'() {
        when:
        def context = buildContext('''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*
import com.fasterxml.jackson.databind.ObjectMapper

@Factory
class MyBeanFactory {

    @Mutating("someVal")
    @jakarta.inject.Singleton
    @jakarta.inject.Named("myMapper")
    fun myMapper(): ObjectMapper {
        return ObjectMapper()
    }

}

''')
        then:
        context.getBean(ObjectMapper, Qualifiers.byName("myMapper")) instanceof Intercepted

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2479')
    void "test final modifier on inherited public method"() {
        when:
        def definition = buildBeanDefinition('test.CountryRepositoryImpl', '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

abstract class BaseRepositoryImpl {
    fun getContext(): Any {
        return Object()
    }
}

interface CountryRepository

@jakarta.inject.Singleton
@Mutating("someVal")
open class CountryRepositoryImpl: BaseRepositoryImpl(), CountryRepository {

    open fun someMethod(): String {
        return "test";
    }
}
''')
        then:"Compilation passes"
        definition != null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2479')
    void "test final modifier on inherited protected method"() {
        when:
        def definition = buildBeanDefinition('test.CountryRepositoryImpl', '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

abstract class BaseRepositoryImpl {

    protected fun getContext(): Any {
        return Object()
    }
}

interface CountryRepository

@jakarta.inject.Singleton
@Mutating("someVal")
open class CountryRepositoryImpl: BaseRepositoryImpl(), CountryRepository {

    open fun someMethod(): String {
        return "test"
    }
}
''')
        then:"Compilation passes"
        definition != null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2479')
    void "test final modifier on inherited protected method - 2"() {
        when:
        def definition = buildBeanDefinition('test.CountryRepositoryImpl', '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

abstract class BaseRepositoryImpl {
    protected fun getContext(): Any {
        return Object()
    }
}

interface CountryRepository {
    @Mutating("someVal")
    fun someMethod(): String
}

@jakarta.inject.Singleton
open class CountryRepositoryImpl: BaseRepositoryImpl(), CountryRepository {

    override fun someMethod(): String {
        return "test"
    }
}
''')
        then:"Compilation passes"
        definition != null
    }

    void "test final modifier on factory with AOP advice doesn't compile"() {
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
        return MyBean()
    }

}

class MyBean
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean'
    }

    @PendingFeature(reason = "active workaround for https://github.com/micronaut-projects/micronaut-core/issues/9426")
    void "test final modifier on class with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

@Mutating("someVal")
@jakarta.inject.Singleton
class MyBean(@Value("\\${foo.bar}") private val myValue: String) {

    open fun someMethod(): String {
        return myValue
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean'
    }

    @PendingFeature(reason = "active workaround for https://github.com/micronaut-projects/micronaut-core/issues/9426")
    void "test final modifier on method with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

@Mutating("someVal")
@jakarta.inject.Singleton
open class MyBean(@Value("\\${foo.bar}") private val myValue: String) {

    fun someMethod(): String {
        return myValue
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Public method inherits AOP advice but is declared final.'
    }

    void "test final modifier on method with AOP advice on method doesn't compile"() {
        when:
        buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
class MyBean(@Value("\\${foo.bar}") private val myValue: String) {

    @Mutating("someVal")
    fun someMethod(): String {
        return myValue
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.'
    }
}
