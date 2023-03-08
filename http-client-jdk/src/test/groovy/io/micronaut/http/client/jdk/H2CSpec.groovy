package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

class H2CSpec extends Specification {

    def "h2c is not supported"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.http.client.plaintext-mode': 'h2c'
        )

        when:
        ctx.getBean(HttpClient)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.cause instanceof ConfigurationException
        ex.cause.message == AbstractJdkHttpClient.H2C_ERROR_MESSAGE

        cleanup:
        ctx.close()
    }
}
