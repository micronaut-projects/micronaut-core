package io.micronaut.http

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.format.Format
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DateTimeConversionSpec extends Specification {

    private static final String FORMAT = "'TS':yyyyMMdd:HHmmss:X"

    @AutoCleanup
    EmbeddedServer server

    @AutoCleanup
    BlockingHttpClient client

    def setup() {
        server = ApplicationContext.run(EmbeddedServer, ['spec.name': getClass().simpleName, "micronaut.http.client.readTimeout": '300s'])
        client = server.applicationContext.getBean(HttpClient).toBlocking()
    }

    def "timestamped headers can be converted to a #desc"() {
        given:
        def now = ZonedDateTime.now(ZoneOffset.UTC)

        // Timestamps in headers are always in GMT in this format https://httpwg.org/specs/rfc7231.html#http.date
        def header = now.format(DateTimeFormatter.RFC_1123_DATE_TIME)
        def expected = now.truncatedTo(ChronoUnit.SECONDS).with {
            (converter ? converter(it) : it).format(formatter)
        }

        when:
        def response = client.exchange(
                HttpRequest.GET("$server.URL/dates/$endpoint")
                        .header('X-Test', header),
                String
        )

        then:
        response.body() == expected

        where:
        desc                       | endpoint               | formatter                       | converter
        'java.util.Date'           | 'header-date'          | DateTimeFormatter.ISO_DATE_TIME | null
        'java.time.OffsetDateTime' | 'header-offset'        | DateTimeFormatter.ISO_DATE_TIME | null
        'java.time.OffsetTime'     | 'header-offsettime'    | DateTimeFormatter.ISO_TIME      | null
        'java.time.ZonedDateTime'  | 'header-zoned'         | DateTimeFormatter.ISO_DATE_TIME | null
        'java.time.LocalDate'      | 'header-localdate'     | DateTimeFormatter.ISO_DATE      | { ZonedDateTime it -> it.toLocalDate() }
        'java.time.LocalTime'      | 'header-localtime'     | DateTimeFormatter.ISO_TIME      | { ZonedDateTime it -> it.toLocalTime() }
        'java.time.LocalDateTime'  | 'header-localdatetime' | DateTimeFormatter.ISO_DATE_TIME | { ZonedDateTime it -> it.toLocalDateTime() }
        'java.time.Instant'        | 'header-instant'       | DateTimeFormatter.ISO_DATE_TIME | null
    }

    def "timestamped can parse the default format for #desc"() {
        when:
        def response = client.exchange(
                HttpRequest.GET("$server.URL/dates/$endpoint?date=${URLEncoder.encode(value.toString(), 'UTF-8')}"),
                String
        )

        then:
        response.body() == value.toString()

        where:
        desc                       | endpoint        | value
        'java.util.Date'           | 'date'          | new Date()
        'java.time.OffsetDateTime' | 'offset'        | OffsetDateTime.now()
        'java.time.OffsetTime'     | 'offset-time'   | OffsetTime.now()
        'java.time.ZonedDateTime'  | 'zoned'         | ZonedDateTime.now()
        'java.time.LocalDate'      | 'localdate'     | LocalDate.now()
        'java.time.LocalTime'      | 'localtime'     | LocalTime.now()
        'java.time.LocalDateTime'  | 'localdatetime' | LocalDateTime.now()
        'java.time.Instant'        | 'instant'       | Instant.now()
    }

    def "timestamp custom format works for #desc"() {
        given:
        def now = ZonedDateTime.now(ZoneOffset.UTC)

        def sent = now.format(DateTimeFormatter.ofPattern(FORMAT))

        def expected = now.truncatedTo(ChronoUnit.SECONDS).with {
            (converter ? converter(it) : it).format(formatter)
        }

        when:
        def response = client.exchange(
                HttpRequest.GET("$server.URL/dates/$endpoint?date=$sent"),
                String
        )

        then:
        response.body() == expected

        where:
        desc                       | endpoint                  | formatter                       | converter
        'java.util.Date'           | 'formatted-date'          | DateTimeFormatter.ISO_DATE_TIME | null
        'java.time.OffsetDateTime' | 'formatted-offset'        | DateTimeFormatter.ISO_DATE_TIME | null
        'java.time.OffsetTime'     | 'formatted-offset-time'   | DateTimeFormatter.ISO_TIME      | null
        'java.time.ZonedDateTime'  | 'formatted-zoned'         | DateTimeFormatter.ISO_DATE_TIME | null
        'java.time.LocalDate'      | 'formatted-localdate'     | DateTimeFormatter.ISO_DATE      | { ZonedDateTime it -> it.toLocalDate() }
        'java.time.LocalTime'      | 'formatted-localtime'     | DateTimeFormatter.ISO_TIME      | { ZonedDateTime it -> it.toLocalTime() }
        'java.time.LocalDateTime'  | 'formatted-localdatetime' | DateTimeFormatter.ISO_DATE_TIME | { ZonedDateTime it -> it.toLocalDateTime() }
        'java.time.Instant'        | 'formatted-instant'       | DateTimeFormatter.ISO_DATE_TIME | null
    }

    @Controller("/dates")
    @Requires(property = 'spec.name', value = 'DateTimeConversionSpec')
    @SuppressWarnings('GrMethodMayBeStatic')
    static class DateFormattingController {
        @Get('/header-date')
        def headerDate(@Header('X-Test') Date date) {
            response(date)
        }

        @Get('/header-offset')
        def headerOffset(@Header('X-Test') OffsetDateTime date) {
            response(date)
        }

        @Get('/header-offsettime')
        def headerOffset(@Header('X-Test') OffsetTime date) {
            response(date)
        }

        @Get('/header-zoned')
        def headerZoned(@Header('X-Test') ZonedDateTime date) {
            response(date)
        }

        @Get('/header-localdate')
        def headerLocalDate(@Header('X-Test') LocalDate date) {
            response(date)
        }

        @Get('/header-localtime')
        def headerLocalTime(@Header('X-Test') LocalTime date) {
            response(date)
        }

        @Get('/header-localdatetime')
        def headerLocalDateTime(@Header('X-Test') LocalDateTime date) {
            response(date)
        }

        @Get('/header-instant')
        def headerInstant(@Header('X-Test') Instant date) {
            response(date);
        }

        @Get('/date')
        def date(Date date) {
            date.toString()
        }

        @Get('/offset')
        def offset(OffsetDateTime date) {
            date.toString()
        }

        @Get('/offset-time')
        def offsetTime(OffsetTime date) {
            date.toString()
        }

        @Get('/zoned')
        def zoned(ZonedDateTime date) {
            date.toString()
        }

        @Get('/localdate')
        def localDate(LocalDate date) {
            date.toString()
        }

        @Get('/localtime')
        def localTime(LocalTime date) {
            date.toString()
        }

        @Get('/localdatetime')
        def localDateTime(LocalDateTime date) {
            date.toString()
        }


        @Get('/instant')
        def instant(Instant date) {
            date.toString()
        }

        @Get('/formatted-date')
        def formatDate(@Format(FORMAT) Date date) {
            response(date)
        }

        @Get('/formatted-offset')
        def formatOffset(@Format(FORMAT) OffsetDateTime date) {
            response(date)
        }

        @Get('/formatted-offset-time')
        def formatOffsetTime(@Format(FORMAT) OffsetTime date) {
            response(date)
        }

        @Get('/formatted-zoned')
        def formatZoned(@Format(FORMAT) ZonedDateTime date) {
            response(date)
        }

        @Get('/formatted-localdate')
        def formatLocalDate(@Format(FORMAT) LocalDate date) {
            response(date)
        }

        @Get('/formatted-localtime')
        def formatLocalTime(@Format(FORMAT) LocalTime date) {
            response(date)
        }

        @Get('/formatted-localdatetime')
        def formatLocalDateTime(@Format(FORMAT) LocalDateTime date) {
            response(date)
        }

        @Get('/formatted-instant')
        def formatInstant(@Format(FORMAT) Instant date) {
            response(date)
        }

        private String response(Date date) {
            date.toInstant().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        private String response(OffsetDateTime date) {
            date.toZonedDateTime().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        private String response(OffsetTime date) {
            date.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_TIME)
        }

        private String response(ZonedDateTime date) {
            date.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        private String response(LocalDate date) {
            date.format(DateTimeFormatter.ISO_DATE)
        }

        private String response(LocalTime date) {
            date.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_TIME)
        }

        private String response(LocalDateTime date) {
            date.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        private String response(Instant date) {
            date.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
        }
    }
}
