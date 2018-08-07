package io.micronaut.http.client.services

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.RxHttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.time.Duration

class ManualHttpServiceDefinitionSpec extends Specification {


    void "test that manually defining an HTTP client creates a client bean"() {
        given:
        EmbeddedServer firstApp = ApplicationContext.run(EmbeddedServer)


        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.url': firstApp.getURI(),
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms',
                'micronaut.http.services.foo.read-timeout':'15s',
                'micronaut.http.services.foo.pool.enabled':false
        )
        TestClient tc = clientApp.getBean(TestClient)

        when:'the config is retrieved'
        def config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("foo"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(15)
        !config.getConnectionPoolConfiguration().isEnabled()

        when:
        RxHttpClient client = clientApp.getBean(RxHttpClient, Qualifiers.byName("foo"))
        String result = client.retrieve('/').blockingFirst()

        then:
        client.configuration == config
        result == 'ok'
        tc.index() == 'ok'

        cleanup:
        firstApp.close()
        clientApp.close()
    }


    void "test that manually defining an HTTP client without URL doesn't create bean"() {
        given:
        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms',
                'micronaut.http.services.foo.read-timeout':'15s',
                'micronaut.http.services.foo.pool.enabled':false
        )

        when:'the config is retrieved'
        def config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("foo"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(15)
        !config.getConnectionPoolConfiguration().isEnabled()

        when:
        def opt = clientApp.findBean(RxHttpClient, Qualifiers.byName("foo"))

        then:
        !opt.isPresent()

        cleanup:
        clientApp.close()
    }
    @Client(id = "foo")
    static interface TestClient {
        @Get
        String index()
    }


    @Controller('/manual/http/service')
    static class TestController {
        @Get
        String index() {
            return "ok"
        }
    }
}
