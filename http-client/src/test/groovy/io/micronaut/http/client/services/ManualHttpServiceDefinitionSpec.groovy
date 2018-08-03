package io.micronaut.http.client.services

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ManualHttpServiceDefinitionSpec extends Specification {


    void "test that manually defining an HTTP client creates a client bean"() {
        given:
        EmbeddedServer firstApp = ApplicationContext.run(EmbeddedServer)


        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.url': firstApp.getURI(),
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms'
        )
        TestClient tc = clientApp.getBean(TestClient)

        when:
        RxHttpClient client = clientApp.getBean(RxHttpClient, Qualifiers.byName("foo"))
        String result = client.retrieve('/').blockingFirst()

        then:
        result == 'ok'
        tc.index() == 'ok'

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
