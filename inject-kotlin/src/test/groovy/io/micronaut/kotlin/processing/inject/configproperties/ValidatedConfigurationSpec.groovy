package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition
import spock.lang.Specification
import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class ValidatedConfigurationSpec extends Specification {

    void "test validated config with invalid config"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(["spec.name": getClass().simpleName], "test")

        when:
        ValidatedConfig config = applicationContext.getBean(ValidatedConfig)

        then:
        applicationContext.getBeanDefinition(ValidatedConfig) instanceof ValidatedBeanDefinition
        def e = thrown(BeanInstantiationException)
        e.message.contains('url - must not be null')
        e.message.contains('name - must not be blank')


        cleanup:
        applicationContext.close()
    }

    void "test validated config with valid config"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder()
            .properties(["spec.name": getClass().simpleName])
            .environments("test")
            .build()
        applicationContext.environment.addPropertySource(PropertySource.of(
                'foo.bar.url':'http://localhost',
                'foo.bar.name':'test'
        ))

        applicationContext.start()

        when:
        ValidatedConfig config = applicationContext.getBean(ValidatedConfig)

        then:
        config != null
        config.url == new URL("http://localhost")
        config.name == 'test'

        cleanup:
        applicationContext.close()
    }

    void "test config props with @Valid on field is a validating bean definition"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.kotlin.processing.inject.configproperties.Pojo

import jakarta.validation.Valid

@ConfigurationProperties("test.valid")
class MyConfig {

    @Valid
    var pojos: List<Pojo>? = null
}
''')

        then:
        beanDefinition instanceof ValidatedBeanDefinition
    }
}
