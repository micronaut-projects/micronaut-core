package io.micronaut.http.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.http.HttpHeaders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

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

    static class MemoryAppender extends AppenderBase<ILoggingEvent> {
        final BlockingQueue<String> events = new LinkedBlockingQueue<>()

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e.formattedMessage)
        }
    }
}
