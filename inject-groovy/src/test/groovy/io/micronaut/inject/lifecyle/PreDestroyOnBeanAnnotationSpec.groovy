package io.micronaut.inject.lifecyle

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext

import java.util.concurrent.ExecutorService

class PreDestroyOnBeanAnnotationSpec extends AbstractBeanDefinitionSpec {
    void "test pre destroy with bean method on parent class"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestBean', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="close")
    @javax.inject.Singleton
    Test testBean() {
        return new Test();
    }
}

class Test extends AbstractTest {}
class AbstractTest implements AutoCloseable {

    public boolean closed = false;
    public void close() {
        closed = true;
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
        bean.closed
        bean != context.getBean(beanType)
    }

    void "test pre destroy with bean method on parent interface"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestBean', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="close")
    @javax.inject.Singleton
    Test testBean() {
        return new Test();
    }
}

class Test implements AutoCloseable {

    public boolean closed = false;
    public void close() {
        closed = true;
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
        bean.closed
        bean != context.getBean(beanType)
    }

    void "test pre destroy with bean method on interface"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestBean', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="shutdown")
    @javax.inject.Singleton
    java.util.concurrent.ExecutorService myService() {
        return java.util.concurrent.Executors.newFixedThreadPool(1);
    }
}


''')

        when:
        def bean = context.getBean(ExecutorService)

        then:
        bean != null

        when:
        context.destroyBean(ExecutorService)

        then:
        bean.isTerminated()
        bean != context.getBean(ExecutorService)
    }

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
