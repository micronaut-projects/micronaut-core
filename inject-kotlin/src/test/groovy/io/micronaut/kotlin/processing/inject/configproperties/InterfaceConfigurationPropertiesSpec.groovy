package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition
import io.micronaut.runtime.context.env.ConfigurationAdvice
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition

class InterfaceConfigurationPropertiesSpec extends Specification {

    void "test simple interface config props"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @jakarta.validation.constraints.NotBlank
    fun getHost(): String?

    @jakarta.validation.constraints.Min(10L)
    fun getServerPort(): Int
}
''')
        then:
        beanDefinition.getAnnotationMetadata().getAnnotationType(ConfigurationAdvice.class.getName()).isPresent()
        beanDefinition instanceof ValidatedBeanDefinition
        beanDefinition.getRequiredMethod("getHost")
                .stringValue(Property, "name").get() == 'foo.bar.host'
        beanDefinition.getRequiredMethod("getServerPort")
                .stringValue(Property, "name").get() == 'foo.bar.server-port'

        when:
        def context = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999')
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()
    }

    void "test optional interface config props"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test

import io.micronaut.context.annotation.*
import java.net.URL
import java.util.Optional

@ConfigurationProperties("foo.bar")
@Executable
interface MyConfig {

    fun getHost(): String?

    @jakarta.validation.constraints.Min(10L)
    fun getServerPort(): Optional<Int>

    @io.micronaut.core.bind.annotation.Bindable(defaultValue = "http://default")
    fun getURL(): Optional<URL>
}

''')
        then:
        beanDefinition.getAnnotationMetadata().getAnnotationType(ConfigurationAdvice.class.getName()).isPresent()
        beanDefinition instanceof ValidatedBeanDefinition
        beanDefinition.getRequiredMethod("getHost")
                .stringValue(Property, "name").get() == 'foo.bar.host'
        beanDefinition.getRequiredMethod("getServerPort")
                .stringValue(Property, "name").get() == 'foo.bar.server-port'
        beanDefinition.getRequiredMethod("getURL")
                .stringValue(Property, "name").get() == 'foo.bar.url'

        when:
        def context = ApplicationContext.run()
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.host == null
        config.serverPort == Optional.empty()
        config.URL == Optional.of(new URL("http://default"))

        when:
        def context2 = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999', 'foo.bar.url': 'http://test')
        def config2 = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context2)

        then:
        config2.host == 'test'
        config2.serverPort == Optional.of(9999)
        config2.URL == Optional.of(new URL("http://test"))

        cleanup:
        context.close()
        context2.close()
    }

    void "test inheritance interface config props"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test;

import io.micronaut.context.annotation.*

@ConfigurationProperties("bar")
interface MyConfig: ParentConfig {

    @Executable
    @jakarta.validation.constraints.Min(10L)
    fun getServerPort(): Int
}

@ConfigurationProperties("foo")
interface ParentConfig {

    @Executable
    @jakarta.validation.constraints.NotBlank
    fun getHost(): String?
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
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }

    void "test nested interface config props"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$Intercepted', '''
package test

import io.micronaut.context.annotation.*
import java.net.URL

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @Executable
    @jakarta.validation.constraints.NotBlank
    fun getHost(): String?

    @Executable
    @jakarta.validation.constraints.Min(10L)
    fun getServerPort(): Int

    @ConfigurationProperties("child")
    interface ChildConfig {
        @Executable
        fun getURL(): URL?
    }
}
''')
        then:
        beanDefinition instanceof BeanDefinition
        beanDefinition.getRequiredMethod("getURL")
                .stringValue(Property, "name").get() == 'foo.bar.child.url'

        when:
        def context = ApplicationContext.run('foo.bar.child.url': 'http://test')
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        config.URL == new URL("http://test")

        cleanup:
        context.close()
    }

    void "test nested interface config props - get child"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$Intercepted', '''
package test

import io.micronaut.context.annotation.*
import java.net.URL

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @jakarta.validation.constraints.NotBlank
    @Executable
    fun getHost(): String

    @jakarta.validation.constraints.Min(10L)
    @Executable
    fun getServerPort(): Int

    @Executable
    fun getChild(): ChildConfig

    @ConfigurationProperties("child")
    interface ChildConfig {
        @Executable
        fun getURL(): URL?
    }
}

''')
        then:
        beanDefinition instanceof BeanDefinition
        def method = beanDefinition.getRequiredMethod("getChild")
        method.isTrue(ConfigurationAdvice, "bean")

        when:
        def context = ApplicationContext.run('foo.bar.child.url': 'http://test')
        def config = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)
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
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @jakarta.validation.constraints.NotBlank
    fun junk(s: String): String

    @jakarta.validation.constraints.Min(10L)
    fun getServerPort(): Int
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Only getter methods are allowed on @ConfigurationProperties interfaces: junk(java.lang.String). You can change the accessors using @AccessorsStyle annotation');
    }

    void "test getter that returns void method"() {
        when:
        buildBeanDefinition('test.MyConfig$Intercepted', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("foo.bar")
interface MyConfig {
    fun getServerPort()
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Getter methods must return a value @ConfigurationProperties interfaces')
    }
}
