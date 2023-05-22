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

    def "h3 is not supported"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.http.client.alpn-modes': ['h3']
        )

        when:
        ctx.getBean(HttpClient)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.cause instanceof ConfigurationException
        ex.cause.message == AbstractJdkHttpClient.H3_ERROR_MESSAGE

        cleanup:
        ctx.close()
    }

    def "http2-only is not supported"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.http.client.alpn-modes': ['h2']
        )

        when:
        ctx.getBean(HttpClient)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.cause instanceof ConfigurationException
        ex.cause.message == AbstractJdkHttpClient.WEIRD_ALPN_ERROR_MESSAGE

        cleanup:
        ctx.close()
    }
}
