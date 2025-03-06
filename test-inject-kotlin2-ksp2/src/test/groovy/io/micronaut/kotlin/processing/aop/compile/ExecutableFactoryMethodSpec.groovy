package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.inject.BeanDefinition
import reactor.core.publisher.Flux
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class ExecutableFactoryMethodSpec extends Specification {

    void "test executing a default interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyFactory$MyClass0', '''
package test

import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

interface SomeInterface {

    fun goDog(): String

    fun go(): String {
        return "go"
    }
}

@Factory
class MyFactory {

    @Singleton
    @Executable
    fun myClass(): MyClass {
        return MyClass()
    }
}

class MyClass: SomeInterface {

    override fun goDog(): String{
        return "go"
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null

        when:
        Object instance = beanDefinition.class.classLoader.loadClass('test.MyClass').newInstance()

        then:
        beanDefinition.findMethod("go").get().invoke(instance) == "go"
        beanDefinition.findMethod("goDog").get().invoke(instance) == "go"
    }

    void "test executable factory with multiple interface inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyFactory$MyClient0', """
package test

import reactor.core.publisher.Flux
import io.micronaut.context.annotation.*
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Factory
class MyFactory {

    @Singleton
    @Executable
    fun myClient(): MyClient? {
        return null
    }
}

interface HttpClient {
    fun retrieve(): Publisher<*>
}
interface StreamingHttpClient: HttpClient {
    fun stream(): Publisher<ByteArray>
}
interface ReactorHttpClient: HttpClient {
    override fun retrieve(): Flux<*>
}
interface ReactorStreamingHttpClient: StreamingHttpClient, ReactorHttpClient {
    override fun stream(): Flux<ByteArray>
}
interface MyClient: ReactorStreamingHttpClient {
    fun blocking(): ByteArray
}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        def retrieveMethod = beanDefinition.getRequiredMethod("retrieve")
        def blockingMethod = beanDefinition.getRequiredMethod("blocking")
        def streamMethod = beanDefinition.getRequiredMethod("stream")
        retrieveMethod.returnType.type == Flux.class
        streamMethod.returnType.type == Flux.class
        retrieveMethod.returnType.typeParameters.length == 1
        retrieveMethod.returnType.typeParameters[0].type == Object.class
        streamMethod.returnType.typeParameters[0].type == byte[].class
    }
}
