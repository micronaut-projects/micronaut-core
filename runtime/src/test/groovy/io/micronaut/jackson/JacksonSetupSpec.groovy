package io.micronaut.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.jackson.JacksonConfiguration
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
        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo:'bar']).get('foo').textValue() == 'bar'

        cleanup:
        applicationContext?.close()
    }


    void "verify custom jackson setup"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.dateFormat':'yyMMdd',
                'jackson.serialization.indentOutput':true
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(JacksonConfiguration).dateFormat == 'yyMMdd'
        applicationContext.getBean(JacksonConfiguration).serializationSettings.get(SerializationFeature.INDENT_OUTPUT)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo:'bar']).get('foo').textValue() == 'bar'

        cleanup:
        applicationContext?.close()
    }
}
