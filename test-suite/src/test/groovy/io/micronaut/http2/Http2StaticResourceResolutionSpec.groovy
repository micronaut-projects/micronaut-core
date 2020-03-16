package io.micronaut.http2

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Specification

import javax.inject.Inject
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
    RxHttpClient rxClient

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
                'micronaut.ssl.enabled': true,
                "micronaut.server.http-version" : "2.0",
                "micronaut.http.client.http-version" : "2.0",
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE",
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': -1
        ]
    }

    void "test resources from the file system are returned"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/'+tempFile.getName()), String
        ).blockingFirst()

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
