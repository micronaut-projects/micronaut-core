package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class ConfigPropertiesInnerClassSpec extends Specification {

    void "test configuration properties binding with inner class"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.innerVals': [
                        ['expire-unsigned-seconds': 123], ['expireUnsignedSeconds': 600]
                ]]
        ))

        applicationContext.start()

        MyConfigInner config = applicationContext.getBean(MyConfigInner)

        expect:
        config.innerVals.size() == 2
        config.innerVals[0].expireUnsignedSeconds == 123
        config.innerVals[1].expireUnsignedSeconds == 600
    }
}
