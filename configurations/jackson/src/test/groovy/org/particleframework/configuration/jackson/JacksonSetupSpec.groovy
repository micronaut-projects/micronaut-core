package org.particleframework.configuration.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 31/08/2017.
 */
class JacksonSetupSpec extends Specification {

    void "verify default jackson setup"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

        expect:
        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo:'bar']).get('foo').textValue() == 'bar'

        cleanup:
        applicationContext.close()
    }
}
