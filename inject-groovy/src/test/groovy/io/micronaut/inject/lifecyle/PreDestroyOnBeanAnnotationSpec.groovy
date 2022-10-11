package io.micronaut.inject.lifecyle

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers

import java.util.concurrent.ExecutorService

class PreDestroyOnBeanAnnotationSpec extends AbstractBeanDefinitionSpec {
    void "test pre destroy with bean method on parent class"() {
        given:
        ApplicationContext context = buildContext('''
package predestroy;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="close")
    @jakarta.inject.Singleton
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
        Class<?> beanType = context.classLoader.loadClass('predestroy.Test')
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
        ApplicationContext context = buildContext('''
package predestroy2;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="close")
    @jakarta.inject.Singleton
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
        Class<?> beanType = context.classLoader.loadClass('predestroy2.Test')
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
        ApplicationContext context = buildContext('''
package predestroy3;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="shutdown")
    @jakarta.inject.Singleton
    @jakarta.inject.Named("my")
    java.util.concurrent.ExecutorService myService() {
        return java.util.concurrent.Executors.newFixedThreadPool(1);
    }
}


''', false)

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
        ApplicationContext context = buildContext('''
package predestroy4;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean(preDestroy="shutdown")
    @jakarta.inject.Singleton
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
        Class<?> beanType = context.classLoader.loadClass('predestroy4.Test')
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
        ApplicationContext context = buildContext('''
package predestroy5;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory2 {

    @Bean(preDestroy="shutdown")
    @jakarta.inject.Singleton
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
        Class<?> beanType = context.classLoader.loadClass('predestroy5.Test2')
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
        buildContext('''
package predestroy6;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory2 {

    @Bean(preDestroy="notthere")
    @jakarta.inject.Singleton
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
