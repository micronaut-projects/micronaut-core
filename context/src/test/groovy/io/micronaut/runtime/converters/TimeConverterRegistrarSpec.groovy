package io.micronaut.runtime.converters

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TimeConverterRegistrarSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run()

    static final String DATE_FORMAT = "yyyy-MM-dd"
    static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm"
    static final String TEST_TIME_ZONE = "America/New_York"

    void 'test converting LocalDate and LocalDateTime'() {
        given:
        def timeZone = TimeZone.default
        TimeZone.default = TimeZone.getTimeZone(TEST_TIME_ZONE)
        def conversionService = context.getBean(ConversionService)
        def localDate = LocalDate.of(2024, 1, 1)
        def localDateTime = LocalDateTime.of(2024, 1, 1, 2, 30)
        def localDateStr = createDateTimeFormatter(DATE_FORMAT).format(localDate)
        def localDateTimeStr = createDateTimeFormatter(DATE_TIME_FORMAT).format(localDateTime)
        when:
        def date = conversionService.convert(localDate, Date).orElse(null)
        def dateTime = conversionService.convert(localDateTime, Date).orElse(null)
        then:
        def dateStr = createDateFormat(DATE_FORMAT).format(date)
        dateStr == localDateStr
        def dateTimeStr = new SimpleDateFormat(DATE_TIME_FORMAT).format(dateTime)
        dateTimeStr == localDateTimeStr
        cleanup:
        TimeZone.default = timeZone
    }

    static DateTimeFormatter createDateTimeFormatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern)
    }

    static DateFormat createDateFormat(String pattern) {
        return new SimpleDateFormat(pattern)
    }
}
