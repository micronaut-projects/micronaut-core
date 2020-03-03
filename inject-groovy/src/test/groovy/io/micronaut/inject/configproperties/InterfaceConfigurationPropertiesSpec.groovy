package io.micronaut.inject.configproperties

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.ValidatedBeanDefinition
import io.micronaut.runtime.context.env.ConfigurationAdvice

class InterfaceConfigurationPropertiesSpec extends AbstractBeanDefinitionSpec {


    void "test simple interface config props"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @javax.validation.constraints.NotBlank
    String getHost();
    
    @javax.validation.constraints.Min(10L)
    int getServerPort();
}

''')
        then:
        beanDefinition instanceof ValidatedBeanDefinition
        beanDefinition.getRequiredMethod("getHost")
                .stringValue(Property, "name").get() == 'foo.bar.host'
        beanDefinition.getRequiredMethod("getServerPort")
                .stringValue(Property, "name").get() == 'foo.bar.server-port'

        when:
        def context = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    void "test inheritance interface config props"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("bar")
interface MyConfig extends ParentConfig {
    
    @javax.validation.constraints.Min(10L)
    int getServerPort();
}

@ConfigurationProperties("foo")
interface ParentConfig {
    @javax.validation.constraints.NotBlank
    String getHost();
}

''')
        then:
        beanDefinition instanceof ValidatedBeanDefinition
        beanDefinition.getRequiredMethod("getServerPort")
                .stringValue(Property, "name").get() == 'foo.bar.server-port'
        beanDefinition.getRequiredMethod("getHost")
                .stringValue(Property, "name").get() == 'foo.bar.host'

        when:
        def context = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    void "test nested interface config props"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;
import java.net.URL;

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @javax.validation.constraints.NotBlank
    String getHost();
    
    @javax.validation.constraints.Min(10L)
    int getServerPort();

    @ConfigurationProperties("child")    
    static interface ChildConfig {
        URL getURL();
    }
}

''')
        then:
        beanDefinition instanceof BeanDefinition
        beanDefinition.getRequiredMethod("getURL")
                .stringValue(Property, "name").get() == 'foo.bar.child.url'

        when:
        def context = ApplicationContext.run('foo.bar.child.url': 'http://test')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.URL == new URL("http://test")

        cleanup:
        context.close()

    }

    void "test nested interface config props - get child"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;
import java.net.URL;

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @javax.validation.constraints.NotBlank
    String getHost();
    
    @javax.validation.constraints.Min(10L)
    int getServerPort();
    
    ChildConfig getChild();

    @ConfigurationProperties("child")    
    static interface ChildConfig {
        URL getURL();
    }
}

''')
        then:
        beanDefinition instanceof BeanDefinition
        beanDefinition.getRequiredMethod("getChild")
                .isTrue(ConfigurationAdvice, "bean")

        when:
        def context = ApplicationContext.run('foo.bar.child.url': 'http://test')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)
        config.child

        then:"we expect a bean resolution"
        def e = thrown(NoSuchBeanException)
        e.message.contains("No bean of type [test.MyConfig\$ChildConfig] exists")

        cleanup:
        context.close()

    }

    void "test invalid method"() {

        when:
        buildBeanDefinition('test.MyConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @javax.validation.constraints.NotBlank
    String junk(String s);
    
    @javax.validation.constraints.Min(10L)
    int getServerPort();
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Only getter methods are allowed on @ConfigurationProperties interfaces: junk')
    }

    void "test getter that returns void method"() {

        when:
        buildBeanDefinition('test.MyConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
interface MyConfig {
    void getServerPort();
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Getter methods must return a value @ConfigurationProperties interfaces')
    }
}
