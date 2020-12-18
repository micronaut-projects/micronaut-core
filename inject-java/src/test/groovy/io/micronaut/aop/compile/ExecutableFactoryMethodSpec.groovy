package io.micronaut.aop.compile

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.reactivex.Flowable;

class ExecutableFactoryMethodSpec extends AbstractTypeElementSpec {

    void "test executing a default interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyFactory$MyClass0', '''
package test;

import io.micronaut.context.annotation.*;
import javax.inject.*;

interface SomeInterface {

    String goDog();
    default String go() { return "go"; }
}

@Factory
class MyFactory {

    @Singleton
    @Executable
    MyClass myClass() {
        return new MyClass();
    }
}

class MyClass implements SomeInterface {
    
    @Override
    public String goDog() {
        return "go";
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
package test;

import io.reactivex.Flowable;
import io.micronaut.context.annotation.*;
import javax.inject.*;
import org.reactivestreams.Publisher;

@Factory
class MyFactory {

    @Singleton
    @Executable
    MyClient myClient() {
        return null;
    }
}

interface HttpClient {
    Publisher retrieve();
}
interface StreamingHttpClient extends HttpClient {
    Publisher<byte[]> stream();
}
interface RxHttpClient extends HttpClient {
    @Override
    Flowable retrieve();
}
interface RxStreamingHttpClient extends StreamingHttpClient, RxHttpClient {
    @Override
    Flowable<byte[]> stream();
}
interface MyClient extends RxStreamingHttpClient {
    byte[] blocking();
}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        def retrieveMethod = beanDefinition.getRequiredMethod("retrieve")
        def blockingMethod = beanDefinition.getRequiredMethod("blocking")
        def streamMethod = beanDefinition.getRequiredMethod("stream")
        retrieveMethod.returnType.type == Flowable.class
        streamMethod.returnType.type == Flowable.class
        retrieveMethod.returnType.typeParameters.length == 1
        retrieveMethod.returnType.typeParameters[0].type == Object.class
        streamMethod.returnType.typeParameters[0].type == byte[].class
    }
}
