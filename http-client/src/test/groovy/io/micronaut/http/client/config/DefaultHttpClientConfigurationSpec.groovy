package io.micronaut.http.client.config

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClientConfiguration
import spock.lang.Specification

import java.time.Duration

class DefaultHttpClientConfigurationSpec extends Specification {

    void "test config"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.http.client.$key".toString()): value
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)


        expect:
        config[property] == expected

        cleanup:
        ctx.close()

        where:
        key            | property      | value  | expected
        'read-timeout' | 'readTimeout' | '15s'  | Optional.of(Duration.ofSeconds(15))
        'proxy-type'   | 'proxyType'   | 'http' | Proxy.Type.HTTP
    }


    void "test pool config"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.http.client.pool.$key".toString()): value
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)
        HttpClientConfiguration.ConnectionPoolConfiguration poolConfig = config.getConnectionPoolConfiguration()

        expect:
        poolConfig[property] == expected

        cleanup:
        ctx.close()

        where:
        key               | property         | value   | expected
        'enabled'         | 'enabled'        | 'false' | false
        'max-connections' | 'maxConnections' | '10'    | 10
    }
}
