package io.micronaut.http.util

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMessage
import io.micronaut.http.MediaType
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class HttpUtilSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/4332")
    void "test an invalid charset returns the default"() {
        when:
        Optional<Charset> charset = HttpUtil.resolveCharset(Mock(HttpMessage) {
            getContentType() >> Optional.of(MediaType.of("text/xml;charset=\"invalid\""))
        })

        then:
        charset.get() == StandardCharsets.UTF_8
    }
}
