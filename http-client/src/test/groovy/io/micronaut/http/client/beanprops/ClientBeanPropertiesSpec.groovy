package io.micronaut.http.client.beanprops

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Specification

@MicronautTest
@Property(name = 'spec.name', value = 'ClientBeanPropertiesSpec')
class ClientBeanPropertiesSpec extends Specification {

    @Inject
    @Client(bean = TestBean.class, urlProperty = "url")
    @AutoCleanup
    HttpClient client

    @Inject
    TestClient testClient

    void "interface client URL property"() {
        when:
        Flux flowable = Flux.from(client.exchange(
                HttpRequest.GET("/test").accept(MediaType.TEXT_PLAIN)
        ))
        Optional<String> body = flowable.map({ res ->
            res.getBody(String)}
        ).blockFirst()

        then:
        body.isPresent()
        body.get() == 'success'
    }

    void "injected client URL property"() {
        when:
        HttpResponse<String> response = testClient.exchange()
        Optional<String> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        body.isPresent()
        body.get() == 'success'
    }

    @Requires(bean = TestBean.class, beanProperty = "url")
    @Client(bean = TestBean.class, urlProperty = "url")
    static interface TestClient {
        @Get(uri = "/test", consumes = MediaType.TEXT_PLAIN)
        HttpResponse exchange()
    }

    @Singleton
    static class TestBean {

        String url;

        TestBean(EmbeddedServer embeddedServer) {
            this.url = embeddedServer.getURL();
        }
    }

    @Requires(property = 'spec.name', value = 'ClientBeanPropertiesSpec')
    @Controller('/test')
    static class TestController {

        @Get
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> test() {
            return HttpResponse.ok("success")
        }
    }
}
