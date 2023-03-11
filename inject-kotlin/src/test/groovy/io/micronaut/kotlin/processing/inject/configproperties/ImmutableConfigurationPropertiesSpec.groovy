package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanResolutionContext
import io.micronaut.context.annotation.Property
import io.micronaut.core.naming.Named
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition
import spock.lang.Specification

import jakarta.validation.Constraint

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class ImmutableConfigurationPropertiesSpec extends Specification {

    void 'test interface immutable properties'() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('interfaceprops.MyConfig$Intercepted', '''
package interfaceprops

import io.micronaut.context.annotation.EachProperty

@EachProperty("foo.bar")
interface MyConfig {

    @jakarta.validation.constraints.NotBlank
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
class MyConfig @ConfigurationInject constructor(@jakarta.validation.constraints.NotBlank val host: String, val serverPort: Int)

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
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
class MyConfig @ConfigurationInject constructor(@jakarta.validation.constraints.NotBlank val host: String, val serverPort: Int) {

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
class MyConfig @ConfigurationInject constructor(@jakarta.validation.constraints.NotBlank val host: String, val serverPort: Int)
''', false, ['foo.bar.one.host': 'test', 'foo.bar.one.server-port': '9999'])
        def config = getBean(context, 'test.MyConfig')

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
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()
    }
}
