package io.micronaut.http.client.jdk

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.client.exceptions.HttpClientException
import reactor.core.publisher.Mono
import spock.lang.Specification

class RawRequestOptionsHostSpec extends Specification {

    void "retaining the host header fails fast unless the JDK allows the header"() {
        given:
        assert !System.getProperty("jdk.httpclient.allowRestrictedHeaders")?.toLowerCase()?.contains("host")
        RawHttpClient client = RawHttpClient.create(null)

        when:
        Mono.from(client.exchange(
            HttpRequest.GET("http://localhost:1/unused").header(HttpHeaders.HOST, "gateway.example"),
            null,
            null,
            RawRequestOptions.builder().retainHostHeader(true).build()
        )).block()

        then:
        def e = thrown(HttpClientException)
        e.message.contains("jdk.httpclient.allowRestrictedHeaders")

        cleanup:
        client.close()
    }
}
