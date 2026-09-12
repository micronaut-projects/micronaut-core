package io.micronaut.http.server.netty.handler.accesslog.element

import io.netty.handler.codec.http.DefaultHttpHeaders
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DateTimeElementSpec extends Specification {
    private static final long T = 1_700_000_000_000L // Tue, 14 Nov 2023 22:13:20 UTC

    private static String uncached(String pattern, long epochMillis) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    def 'default pattern is cached per second and matches the uncached format'() {
        given:
        def element = new DateTimeElement(null)

        expect:
        element.@cache != null
        element.value(T) == uncached("'['dd/MMM/yyyy:HH:mm:ss Z']'", T)
        element.value(T).is(element.value(T + 999))
        element.value(T + 1000) != element.value(T + 999)
        element.value(T + 1000) == uncached("'['dd/MMM/yyyy:HH:mm:ss Z']'", T + 1000)
    }

    def 'custom pattern with explicit zone is exact'() {
        given:
        def element = new DateTimeElement("dd/MMM/yyyy:HH:mm:ss Z, UTC")

        expect:
        element.@cache != null
        element.value(T) == '14/Nov/2023:22:13:20 +0000'
        element.value(T + 999) == '14/Nov/2023:22:13:20 +0000'
        element.value(T + 1000) == '14/Nov/2023:22:13:21 +0000'
        element.toString() == '%{dd/MMM/yyyy:HH:mm:ss Z, UTC}t'
    }

    def 'custom pattern with a different zone respects that zone'() {
        given:
        def element = new DateTimeElement("end:yyyy-MM-dd HH:mm:ss, Asia/Tokyo")

        expect:
        element.events() == [LogElement.Event.ON_LAST_RESPONSE_WRITE] as Set
        element.value(T) == '2023-11-15 07:13:20'
        element.value(T + 1000) == '2023-11-15 07:13:21'
    }

    def 'pattern with sub-second precision is not cached and stays exact'() {
        given:
        def element = new DateTimeElement("HH:mm:ss.SSS, UTC")

        expect:
        element.@cache == null
        element.value(T) == '22:13:20.000'
        element.value(T + 1) == '22:13:20.001'
        element.value(T + 999) == '22:13:20.999'
        element.value(T + 1000) == '22:13:21.000'
    }

    def 'live values are current'() {
        given:
        def cached = new DateTimeElement("yyyy-MM-dd'T'HH:mm:ss, UTC")
        def exact = new DateTimeElement("yyyy-MM-dd'T'HH:mm:ss.SSSSSS, UTC")
        def headers = new DefaultHttpHeaders()

        when:
        long before = System.currentTimeMillis() / 1000
        String cachedValue = cached.onRequestHeaders(ConnectionMetadata.empty(), 'GET', headers, '/', 'HTTP/1.1')
        String exactValue = exact.onRequestHeaders(ConnectionMetadata.empty(), 'GET', headers, '/', 'HTTP/1.1')
        long after = System.currentTimeMillis() / 1000

        then:
        long cachedSecond = Instant.parse(cachedValue + 'Z').epochSecond
        long exactSecond = Instant.parse(exactValue + 'Z').epochSecond
        cachedSecond >= before && cachedSecond <= after
        exactSecond >= before && exactSecond <= after
    }

    def 'sub-second detection: #pattern'() {
        expect:
        DateTimeElement.hasSubSecondField(pattern) == subSecond

        where:
        pattern                            | subSecond
        "'['dd/MMM/yyyy:HH:mm:ss Z']'"     | false
        "dd/MMM/yyyy:HH:mm:ss Z"           | false
        "yyyy-MM-dd'T'HH:mm:ssXXX"         | false
        "HH:mm:ss.SSS"                     | true
        "HH:mm:ss.n"                       | true
        "N"                                | true
        "A"                                | true
        "'S'HH:mm:ss"                      | false // quoted literal
        "'Sent at 'HH:mm:ss"               | false // quoted literal
        "'it''s 'HH:mm:ss"                 | false // escaped quote inside literal
        "'it''s 'HH:mm:ss.SSS"             | true
        "HH:mm:ss 'ms='SSS"                | true
    }
}
