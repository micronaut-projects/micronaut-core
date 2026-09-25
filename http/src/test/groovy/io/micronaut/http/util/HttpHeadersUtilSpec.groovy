package io.micronaut.http.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpHeaders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.See
import spock.lang.Specification

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class HttpHeadersUtilSpec extends Specification {
    def "check masking works for #value"() {
        expect:
        expected == HttpHeadersUtil.mask(value)

        where:
        value       | expected
        null        | null
        "foo"       | "*MASKED*"
        "Tim Yates" | "*MASKED*"
    }

    def "check mask detects common security headers"() {
        given:
        MemoryAppender appender = new MemoryAppender()
        Logger log = LoggerFactory.getLogger(HttpHeadersUtilSpec.class)

        expect:
        log instanceof ch.qos.logback.classic.Logger

        when:
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) log
        logger.addAppender(appender)
        logger.setLevel(Level.TRACE)
        appender.start()

        HttpHeaders headers = new MockHttpHeaders([
                "Authorization": ["Bearer foo"],
                "Proxy-Authorization": ["AWS4-HMAC-SHA256 bar"],
                "Cookie": ["baz"],
                "Set-Cookie": ["qux"],
                "X-Forwarded-For": ["quux", "fred"],
                "X-Forwarded-Host": ["quuz"],
                "X-Real-IP": ["waldo"],
                "Credential": ["foo"],
                "Signature": ["bar probably secret"]])

        HttpHeadersUtil.trace(log, headers)

        then:
        appender.events.size() == headers.values().collect { it -> it.size() }.sum()
        appender.events.contains("Authorization: *MASKED*")
        appender.events.contains("Cookie: baz")
        appender.events.contains("Credential: *MASKED*")
        appender.events.contains("Set-Cookie: qux")
        appender.events.contains("Proxy-Authorization: *MASKED*")
        appender.events.contains("Signature: *MASKED*")
        appender.events.contains("X-Forwarded-For: quux")
        appender.events.contains("X-Forwarded-For: fred")
        appender.events.contains("X-Forwarded-Host: quuz")
        appender.events.contains("X-Real-IP: waldo")

        cleanup:
        appender.stop()
    }

    def "splitAcceptHeader"(String header, String result) {
        expect:
        HttpHeadersUtil.splitAcceptHeader(header) == result

        where:
        header                                         | result
        "fr-CH, fr;q=0.9, en;q=0.8, de;q=0.7, *;q=0.5" | "fr-CH"
        "fr-CH;q=0.9, en;q=0.8, de;q=0.7, *;q=0.5"     | "fr-CH"
        "*"                                            | null
    }

    @See("https://udn.realityripple.com/docs/Web/HTTP/Headers/Accept-Charset")
    void "acceptCharset"(String headerValue, Charset expectedCharset) {
        when:
        Charset charset = HttpHeadersUtil.parseAcceptCharset(headerValue)

        then:
        charset == expectedCharset

        where:
        headerValue                        || expectedCharset
        'iso-8859-1'                       || StandardCharsets.ISO_8859_1
        'utf-8, iso-8859-1;q=0.5, *;q=0.1' || StandardCharsets.UTF_8
        'utf-8, iso-8859-1;q=0.5'          || StandardCharsets.UTF_8

    }

    void "parse character encoding based on the Content-Type and Accept-Charset Header values"(String contentTypeHeaderValue, String acceptCharsetHeaderValue , Charset expectedCharset) {
        when:
        Charset charset = HttpHeadersUtil.parseCharacterEncoding(contentTypeHeaderValue, acceptCharsetHeaderValue)

        then:
        charset == expectedCharset

        where:
        contentTypeHeaderValue            | acceptCharsetHeaderValue  || expectedCharset
        null                              | 'iso-8859-1'              || StandardCharsets.ISO_8859_1
        null                              | 'utf-8, iso-8859-1;q=0.5, *;q=0.1' || StandardCharsets.UTF_8
        null                              | 'utf-8, iso-8859-1;q=0.5' || StandardCharsets.UTF_8
        'application/json; charset=utf-8' | 'iso-8859-1'              || StandardCharsets.UTF_8
        'application/json'                | 'iso-8859-1'              || StandardCharsets.ISO_8859_1
        null                              | null                      || StandardCharsets.UTF_8
        'application/json; charset=bogus' | 'iso-8859-1'              || StandardCharsets.UTF_8
    }

    static class MemoryAppender extends AppenderBase<ILoggingEvent> {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>()

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e.formattedMessage)
        }
    }

    void "the hop-by-hop headers are stripped in place"() {
        given:
        MutableHttpHeaders headers = HttpRequest.GET("/").headers
        headers.add("Connection", "keep-alive, X-Hop")
        headers.add("Connection", "x-other-hop,")
        headers.add("X-Hop", "1")
        headers.add("X-Other-Hop", "2")
        headers.add("Keep-Alive", "timeout=5")
        headers.add("Proxy-Authorization", "Basic abc")
        headers.add("proxy-connection", "keep-alive")
        headers.add("TE", "trailers")
        headers.add("Trailer", "X-Checksum")
        headers.add("Transfer-Encoding", "chunked")
        headers.add("Upgrade", "h2c")
        headers.add("Accept", "text/plain")
        headers.add("Accept", "application/json")
        headers.add("X-Proxyish", "kept")

        when:
        HttpHeadersUtil.stripHopByHopHeaders(headers)

        then:
        headers.names().toList().sort() == ["Accept", "X-Proxyish"]
        headers.getAll("Accept") == ["text/plain", "application/json"]
    }
}
