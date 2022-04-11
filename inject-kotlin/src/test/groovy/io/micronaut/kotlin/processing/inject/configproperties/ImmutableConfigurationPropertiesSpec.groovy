package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanResolutionContext
import io.micronaut.context.annotation.Property
import io.micronaut.core.naming.Named
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.ValidatedBeanDefinition
import spock.lang.Specification
import static io.micronaut.kotlin.processing.KotlinCompiler.*

class ImmutableConfigurationPropertiesSpec extends Specification {

    void 'test interface immutable properties'() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('interfaceprops.MyConfig$Intercepted', '''
package interfaceprops

import io.micronaut.context.annotation.EachProperty

@EachProperty("foo.bar")
interface MyConfig {

    @javax.validation.constraints.NotBlank
    fun getHost(): String

    fun getPort(): Int
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
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig @ConfigurationInject constructor(@javax.validation.constraints.NotBlank val host: String, val serverPort: Int)

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
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig @ConfigurationInject constructor(@javax.validation.constraints.NotBlank val host: String, val serverPort: Int) {
 
    @ConfigurationProperties("baz")
    class ChildConfig @ConfigurationInject constructor(val stuff: String)
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
class MyConfig @ConfigurationInject constructor(@javax.validation.constraints.NotBlank val host: String, val serverPort: Int)
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
    var host: String? = null
        private set
    var serverPort: Int = 0
        private set
    
    @ConfigurationInject
    fun init(host: String, serverPort: Int) {
       this.host = host
       this.serverPort = serverPort
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
