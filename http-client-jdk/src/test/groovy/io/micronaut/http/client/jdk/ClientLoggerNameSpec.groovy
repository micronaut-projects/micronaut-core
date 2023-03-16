package io.micronaut.http.client.jdk

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "ClientLoggerNameSpec")
class ClientLoggerNameSpec extends Specification {

    @Inject
    @Client(value = "/one", configuration = OneConfig)
    HttpClient oneClient

    @Inject
    @Client(value = "/two", configuration = TwoConfig)
    HttpClient twoClient

    def "test"() {
        when:
        def one = oneClient.toBlocking().retrieve("/")
        def two = twoClient.toBlocking().retrieve("/")

        then:
        one == "one"
        two == "two"
    }

    @Requires(property = "spec.name", value = "ClientLoggerNameSpec")
    @Controller
    static class SpecController {

        @Get("/one")
        String one() {
            "one"
        }

        @Get("/two")
        String two() {
            "two"
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ClientLoggerNameSpec")
    static final class OneConfig extends HttpClientConfiguration {

        OneConfig() {
            setLoggerName("named.client.one")
        }

        @Override
        ConnectionPoolConfiguration getConnectionPoolConfiguration() {
            return null
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ClientLoggerNameSpec")
    static final class TwoConfig extends HttpClientConfiguration {

        TwoConfig() {
            setLoggerName("named.client.two")
        }

        @Override
        ConnectionPoolConfiguration getConnectionPoolConfiguration() {
            return null
        }
    }
}
