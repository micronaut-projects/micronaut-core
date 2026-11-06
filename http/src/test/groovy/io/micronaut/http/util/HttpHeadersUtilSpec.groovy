package io.micronaut.http.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.http.HttpHeaders
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

    void "trace by logger name preserves masking and respects #level"(Level level) {
        given:
        String loggerName = "example.headers.named-${level}"
        def logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName)
        def previousLevel = logger.level
        def appender = new MemoryAppender()
        appender.start()
        logger.addAppender(appender)
        logger.level = level
        def headers = new MockHttpHeaders([
            'Authorization': ['Bearer private'],
            'Proxy-Authorization': ['private-proxy'],
            'Credential': ['private-credential'],
            'Signature': ['private-signature'],
            'Password': ['private-password'],
            'Certificate': ['private-certificate'],
            'Api-Key': ['private-key'],
            'Secret': ['private-secret'],
            'Token': ['private-token'],
            'Cookie': ['cookie'],
            'Set-Cookie': ['set-cookie'],
            'X-Forwarded-For': ['first', 'second'],
            'X-Forwarded-Host': ['host'],
            'X-Real-IP': ['ip']
        ])

        when:
        HttpHeadersUtil.trace(logger, headers)
        def baseline = appender.events.toList()
        appender.events.clear()
        HttpHeadersUtil.trace(loggerName, headers)
        def named = appender.events.toList()

        then:
        named == baseline
        if (level == Level.TRACE) {
            assert named.size() == 15
            assert named.containsAll(['Authorization', 'Proxy-Authorization', 'Credential',
                'Signature', 'Password', 'Certificate', 'Api-Key', 'Secret', 'Token']
                .collect { "$it: *MASKED*".toString() })
            assert named.containsAll(['Cookie: cookie', 'Set-Cookie: set-cookie',
                'X-Forwarded-For: first', 'X-Forwarded-For: second',
                'X-Forwarded-Host: host', 'X-Real-IP: ip'])
            assert !named.any { it.contains('private') }
        } else {
            assert named.empty
        }

        cleanup:
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel

        where:
        level << [Level.TRACE, Level.INFO]
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
}
