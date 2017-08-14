package org.particleframework.inject.configproperties

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.MapPropertySource
import spock.lang.Specification

class PrimitiveConfigurationPropertiesSpec extends Specification {

    // this was just to get the corner case for primitives working
    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(new MapPropertySource(
            'foo.bar.port':'8080',
        ))

        applicationContext.start()

        MyPrimitiveConfig config = applicationContext.getBean(MyPrimitiveConfig)

        expect:
        config.port == 8080
        config.primitiveDefaultValue == 9999
    }
}
