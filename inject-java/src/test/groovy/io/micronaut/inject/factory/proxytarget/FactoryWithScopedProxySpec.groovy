package io.micronaut.inject.factory.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument

class FactoryWithScopedProxySpec extends AbstractTypeElementSpec {

    void "test that a factory that returns a class that has constructor argument and specifies AOP advise fails to compile"() {
        when:
        buildBeanDefinition('factproxy.TestFactory', '''
package factproxy;

import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean
    @io.micronaut.runtime.context.scope.ThreadLocal
    Test test() {
        return new Test("foo");
    }
}

class Test {
    Test(String name) {}
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor")
    }

    void "test that a scoped proxy returned from a factory is lazily initialized"() {
        given:
        def context = buildContext('''
package lazyfact;

import io.micronaut.context.annotation.*;
import io.micronaut.runtime.context.scope.ScopedProxy;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

@Factory
class TestFactory {

    @jakarta.inject.Singleton
    @TestAnn
    Test test() {
        return new Test();
    }
}

class Test {
    public static int initCount = 0;
    Test() {
        initCount++;
    }
    
    public void test() {}
}

@ScopedProxy
@Retention(RUNTIME)
@interface TestAnn {
}

''')
        Class<?> testClass = context.classLoader.loadClass('lazyfact.Test')

        when:"An object is initially retrieved"
        def bean = context.getBean(testClass)

        then:"The target has not yet been initialized because it is lazy"
        testClass.initCount == 1

        when:"A public method is invoked"
        bean.test()

        then:"The object is initialized"
        testClass.initCount == 2
    }

    void "test that a scoped proxy with annotation qualifier"() {
        given:
        def context = buildContext('''
package lazyfact;

import io.micronaut.context.annotation.*;
import io.micronaut.runtime.context.scope.ScopedProxy;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

@Factory
class TestFactory {

    @Bean
    @TestAnn
    @TestQ
    Test test() {
        return new Test();
    }
}

class Test {
    public static int initCount = 0;
    Test() {
        initCount++;
    }
    
    public void test() {}
}

@ScopedProxy
@Retention(RUNTIME)
@interface TestAnn {
}

@Retention(RUNTIME)
@jakarta.inject.Qualifier
@interface TestQ {
}

''')
        Class<?> testClass = context.classLoader.loadClass('lazyfact.Test')

        when:"An object is initially retrieved"
        def bean = context.getBean(testClass)

        then:"The target has not yet been initialized because it is lazy"
        testClass.initCount == 1

        when:"A public method is invoked"
        bean.test()

        then:"The object is initialized"
        testClass.initCount == 2
    }

    void "test that a scoped proxy with generics"() {
        given:
        def context = buildContext('''
package lazyfact;

import io.micronaut.context.annotation.*;
import io.micronaut.runtime.context.scope.ScopedProxy;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

@Factory
class TestFactory {

    @jakarta.inject.Singleton
    @TestAnn
    Test<String> testString() {
        return new StringTest();
    }
    
    @jakarta.inject.Singleton
    @TestAnn
    Test<Integer> testInteger() {
        return new IntegerTest();
    }
}

abstract class Test<T> {
    public static int initCount = 0;
    Test() {
        initCount++;
    }
    
    public abstract T test();
}

class StringTest extends Test<String> {
    public String test() {
        return "good";
    }   
}

class IntegerTest extends Test<Integer> {
    public Integer test() {
        return 1;
    }   
}

@ScopedProxy
@Retention(RUNTIME)
@interface TestAnn {
}

''')
        Class<?> testClass = context.classLoader.loadClass('lazyfact.Test')

        when:"An object is initially retrieved"
        def bean = context.getBean(Argument.of(testClass, String))

        then:"The target has not yet been initialized because it is lazy but the proxy has"
        testClass.initCount == 1

        when:"A public method is invoked"
        bean.test() == 'good'

        then:"The target object is initialized"
        testClass.initCount == 2
    }
}
