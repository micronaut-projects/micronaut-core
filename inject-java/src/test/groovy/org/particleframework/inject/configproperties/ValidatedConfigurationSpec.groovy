package org.particleframework.inject.configproperties

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.context.env.PropertySource
import org.particleframework.context.exceptions.BeanInstantiationException
import spock.lang.Specification

import javax.validation.Validation

class ValidatedConfigurationSpec extends Specification {

    void "test validated config with invalid config"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.registerSingleton(
                Validation.buildDefaultValidatorFactory()
        )
        applicationContext.start()

        when:
        ValidatedConfig config = applicationContext.getBean(ValidatedConfig)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('url - may not be null')
        e.message.contains('name - may not be empty')
    }

    void "test validated config with valid config"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'foo.bar.url':'http://localhost',
                'foo.bar.name':'test'
        ))

        applicationContext.registerSingleton(
                Validation.buildDefaultValidatorFactory()
        )

        applicationContext.start()

        when:
        ValidatedConfig config = applicationContext.getBean(ValidatedConfig)

        then:
        config != null
        config.url == new URL("http://localhost")
        config.name == 'test'

    }
}
