package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.annotation.Property
import io.micronaut.context.visitor.ConfigurationReaderVisitor
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.validation.visitor.ValidationVisitor

class ImmutableConfigurationPropertiesSpec extends AbstractTypeElementSpec {

    void 'test interface immutable properties'() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('interfaceprops.MyConfig$Intercepted', '''
package interfaceprops;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;

@EachProperty("foo.bar")
interface MyConfig {
    @jakarta.validation.constraints.NotBlank
    String getHost();

    int getPort();
}


''')
        then:
        beanDefinition instanceof ValidatedBeanDefinition
        beanDefinition.getRequiredMethod("getHost").synthesize(Property).name() == 'foo.bar.*.host'
        beanDefinition.getRequiredMethod("getPort").synthesize(Property).name() == 'foo.bar.*.port'
    }

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
    MyConfig(@jakarta.validation.constraints.NotBlank String host, int serverPort) {
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
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

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
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.stuff == 'test'

        cleanup:
        context.close()

    }

    void "test parse immutable configuration properties - each property"() {

        when:
        ApplicationContext context = buildContext( '''
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
        def beanDefinition = getBeanDefinition(context, 'test.MyConfig', Qualifiers.byName('one'))
        def arguments = beanDefinition.constructor.arguments
        then:
        arguments.length == 2
        arguments[0].synthesize(Property)
                .name() == 'foo.bar.*.host'
        arguments[1].synthesize(Property)
                .name() == 'foo.bar.*.server-port'

        when:
        def config = getBean(context, 'test.MyConfig', Qualifiers.byName('one'))

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties('foo.bar.one.host': 'test', 'foo.bar.one.server-port': '9999')
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
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        [new ValidationVisitor(), new ConfigurationReaderVisitor()]
    }
}
