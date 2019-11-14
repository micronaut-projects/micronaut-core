package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanResolutionContext
import io.micronaut.context.annotation.Property
import io.micronaut.core.naming.Named
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.ValidatedBeanDefinition

class ImmutableConfigurationPropertiesSpec extends AbstractTypeElementSpec {


    void "test parse immutable configuration properties"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    private String host;
    private int serverPort;
    
    @ConfigurationInject
    MyConfig(@javax.validation.constraints.NotBlank String host, int serverPort) {
        this.host = host;
        this.serverPort = serverPort;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getServerPort() {
        return serverPort;
    }
}

''')
        def arguments = beanDefinition.constructor.arguments
        then:
        beanDefinition instanceof ValidatedBeanDefinition
        arguments.length == 2
        arguments[0].synthesize(Property)
            .name() == 'foo.bar.host'
        arguments[1].synthesize(Property)
                .name() == 'foo.bar.server-port'

        when:
        def context = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    void "test parse immutable configuration properties - child config"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    private String host;
    private int serverPort;
    
    @ConfigurationInject
    MyConfig(String host, int serverPort) {
        this.host = host;
        this.serverPort = serverPort;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    @ConfigurationProperties("baz")
    static class ChildConfig {
        final String stuff;
        @ConfigurationInject
        ChildConfig(String stuff) {
            this.stuff = stuff;
        }
    }
}

''')
        def arguments = beanDefinition.constructor.arguments
        then:
        arguments.length == 1
        arguments[0].synthesize(Property)
                .name() == 'foo.bar.baz.stuff'

        when:
        def context = ApplicationContext.run('foo.bar.baz.stuff': 'test')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.stuff == 'test'

        cleanup:
        context.close()

    }

    void "test parse immutable configuration properties - each property"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@EachProperty("foo.bar")
class MyConfig {
    private String host;
    private int serverPort;
    
    @ConfigurationInject
    MyConfig(String host, int serverPort) {
        this.host = host;
        this.serverPort = serverPort;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getServerPort() {
        return serverPort;
    }
}

''')
        def arguments = beanDefinition.constructor.arguments
        then:
        arguments.length == 2
        arguments[0].synthesize(Property)
                .name() == 'foo.bar.*.host'
        arguments[1].synthesize(Property)
                .name() == 'foo.bar.*.server-port'

        when:
        def context = ApplicationContext.run('foo.bar.one.host': 'test', 'foo.bar.one.server-port': '9999')
        def resolutionContext = new DefaultBeanResolutionContext(context, beanDefinition)
        resolutionContext.setAttribute(
                Named.class.getName(),
                "one"
        )
        def config = ((BeanFactory) beanDefinition).build(resolutionContext,context, beanDefinition)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    void "test parse immutable configuration properties - init method"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    private String host;
    private int serverPort;
    
    
    MyConfig() {
    }
    
    @ConfigurationInject
    void init(String host, int serverPort) {
       this.host = host;
       this.serverPort = serverPort;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getServerPort() {
        return serverPort;
    }
}

''')
        def context = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }
}
