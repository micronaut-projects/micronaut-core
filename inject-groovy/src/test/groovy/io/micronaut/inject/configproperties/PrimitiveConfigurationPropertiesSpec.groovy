package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.env.PropertySource
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class PrimitiveConfigurationPropertiesSpec extends Specification {

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
            'test',
            ['foo.bar.port':'8080']
        ))

        applicationContext.start()

        MyPrimitiveConfig config = applicationContext.getBean(MyPrimitiveConfig)

        expect:
        config.port == 8080
        config.primitiveDefaultValue == 9999
    }

    @ConfigurationProperties('foo.bar')
    static class MyPrimitiveConfig {
        int port
        int primitiveDefaultValue = 9999
    }
}
