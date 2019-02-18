package io.micronaut.inject.factory.lifecycle

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractTypeElementSpec

class PreDestroyOnBeanAnnotationSpec extends AbstractTypeElementSpec {
    void "test pre destroy with bean method that returns a value"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestBean', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="shutdown")
    @javax.inject.Singleton
    Test testBean() {
        return new Test();
    }
}

class Test {

    Test shutdown() {
        return this;
    }
}

''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test')
        def bean = context.getBean(beanType)

        then:
        bean != null

        when:
        context.destroyBean(beanType)

        then:
        bean != context.getBean(beanType)
    }

    void "test pre destroy with bean method that returns void"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory2$TestBean', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory2 {

    @Bean(preDestroy="shutdown")
    @javax.inject.Singleton
    Test2 testBean() {
        return new Test2();
    }
}

class Test2 {

    void shutdown() {
        // no-op
    }
}

''')

        when:
        Class<?> beanType = context.classLoader.loadClass('test.Test2')
        def bean = context.getBean(beanType)

        then:
        bean != null

        when:
        context.destroyBean(beanType)

        then:
        bean != context.getBean(beanType)
    }

    void "test pre destroy with bean method that doesn't exist"() {
        when:
        buildContext('test.TestFactory2$TestBean', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory2 {

    @Bean(preDestroy="notthere")
    @javax.inject.Singleton
    Test2 testBean() {
        return new Test2();
    }
}

class Test2 {

    void shutdown() {
        // no-op
    }
}

''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Bean method defines a preDestroy method that does not exist or is not public: notthere")
    }
}
