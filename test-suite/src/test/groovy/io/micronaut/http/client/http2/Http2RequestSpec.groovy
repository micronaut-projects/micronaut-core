package io.micronaut.http.client.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore
import spock.lang.PendingFeature
import spock.lang.Specification

// Netty + HTTP/2 on JDKs less than 9 require tcnative setup
// which is not included in this test suite
//@IgnoreIf({ !Jvm.current.isJava9Compatible() })
class Http2RequestSpec extends Specification {

    void "test make HTTP/2 request - HTTPS"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.ssl.enabled': true,
                "micronaut.server.http-version" : "2.0",
                "micronaut.http.client.http-version" : "2.0",
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)

        when:
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        when:"operation repeated to use same connection"
        result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        cleanup:
        server.close()
    }

    void "test make HTTP/2 request - upgrade over HTTP"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                "micronaut.server.http-version" : "2.0",
                "micronaut.http.client.http-version" : "2.0",
                "micronaut.http.client.read-timeout": -1,
                "micronaut.http.client.log-level" : "TRACE"
//                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)

        when:
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        when:"operation repeated to use same connection"
        result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        cleanup:
        server.close()
    }

    @Ignore
    void "test make HTTP/2 request - upgrade over HTTP 2"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                "micronaut.http.client.http-version" : "2.0",
                "micronaut.http.client.read-timeout": -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
        def result = client.retrieve("http://localhost:8080").blockingFirst()

        expect:
        result == 'Version: HTTP_2_0'

        cleanup:
        server.close()
    }

    @Ignore
    void "test make HTTP/2 client with HTTP/1 server - HTTPS"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.ssl.enabled': true,
                "micronaut.http.client.http-version" : "2.0",
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
        def result = client.retrieve("http://localhost:${server.port}/http2").blockingFirst()

        expect:
        result == 'Version: HTTP_2_0'

        cleanup:
        server.close()
    }

    void "test HTTP/2 server with HTTP/1 client request works"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.ssl.enabled': true,
                "micronaut.server.http-version" : "2.0",
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        expect:
        result == 'Version: HTTP_1_1'

        cleanup:
        server.close()
    }


    @Controller("/http2")
    static class Http2Controller {

        @Get(produces = MediaType.TEXT_PLAIN)
        String index(HttpRequest<?> request) {
            return "Version: ${request.httpVersion}"
        }
    }
}
