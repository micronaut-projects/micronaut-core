package io.micronaut.http.client.jdk

import spock.lang.Specification

import javax.net.ssl.SSLSession
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class BufferedJdkResponseSpec extends Specification {

    def "the buffered response delegates to the response it read"() {
        given:
        HttpRequest theRequest = HttpRequest.newBuilder(URI.create('http://localhost/x')).build()
        HttpHeaders theHeaders = HttpHeaders.of(['a': ['b']], (x, y) -> true)
        SSLSession session = Mock(SSLSession)
        HttpResponse<?> response = Mock(HttpResponse) {
            statusCode() >> 201
            request() >> theRequest
            headers() >> theHeaders
            sslSession() >> Optional.of(session)
            uri() >> URI.create('http://localhost/x')
            version() >> HttpClient.Version.HTTP_2
        }
        def buffered = new BufferedJdkResponse(response, [1, 2, 3] as byte[])

        expect:
        buffered.statusCode() == 201
        buffered.request().is(theRequest)
        !buffered.previousResponse().isPresent()
        buffered.headers().is(theHeaders)
        buffered.sslSession().get().is(session)
        buffered.uri() == URI.create('http://localhost/x')
        buffered.version() == HttpClient.Version.HTTP_2
        buffered.body() == [1, 2, 3] as byte[]
        buffered == new BufferedJdkResponse(response, [1, 2, 3] as byte[])
        buffered != new BufferedJdkResponse(response, [1, 2] as byte[])
        buffered != 'other'
        buffered.hashCode() == new BufferedJdkResponse(response, [1, 2, 3] as byte[]).hashCode()
        buffered.toString().endsWith('body=3 bytes]')
    }
}
