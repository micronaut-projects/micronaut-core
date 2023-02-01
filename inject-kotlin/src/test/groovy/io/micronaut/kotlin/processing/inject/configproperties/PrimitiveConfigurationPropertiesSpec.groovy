package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class PrimitiveConfigurationPropertiesSpec extends Specification {

    // this was just to get the corner case for primitives working
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
        config.defaultValue == 9999
    }
}
