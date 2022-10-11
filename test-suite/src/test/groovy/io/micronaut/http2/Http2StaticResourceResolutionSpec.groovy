package io.micronaut.http2

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Specification

import jakarta.inject.Inject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import static io.micronaut.http.HttpHeaders.CACHE_CONTROL
import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH
import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.HttpHeaders.DATE
import static io.micronaut.http.HttpHeaders.EXPIRES
import static io.micronaut.http.HttpHeaders.LAST_MODIFIED

@MicronautTest
class Http2StaticResourceResolutionSpec extends Specification implements TestPropertyProvider {
    private static File tempFile
    @Inject
    @Client("/")
    HttpClient rxClient

    static {
        tempFile = File.createTempFile("staticResourceResolutionSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page from static file</body></html>")
        tempFile
    }

    void cleanupSpec() {
        tempFile.delete()
    }

    @Override
    Map<String, String> getProperties() {
        return [
                'micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent],
                'micronaut.server.ssl.enabled': true,
                "micronaut.server.http-version" : "2.0",
                "micronaut.http.client.http-version" : "2.0",
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE",
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true
        ]
    }

    void "test resources from the file system are returned"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/'+tempFile.getName()), String
        ).blockFirst()

        then:
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from static file</body></html>"
    }
}
