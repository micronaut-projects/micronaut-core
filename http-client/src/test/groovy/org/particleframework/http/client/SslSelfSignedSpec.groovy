package org.particleframework.http.client

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class SslSelfSignedSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'particle.ssl.enabled': true,
            'particle.ssl.buildSelfSigned': true
    ])

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    void "expect the url to be https"() {
        expect:
        embeddedServer.getURL().toString() == "https://localhost:8443"
    }

    void "test send https request"() {
        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/ssl"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.body() == "Hello"
    }

    @Controller('/')
    static class SslSelfSignedController {

        @Get('/ssl')
        String simple() {
            return "Hello"
        }

    }
}