package org.particleframework.inject.configproperties

import org.particleframework.context.annotation.ConfigurationProperties
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.context.env.PropertySource
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
