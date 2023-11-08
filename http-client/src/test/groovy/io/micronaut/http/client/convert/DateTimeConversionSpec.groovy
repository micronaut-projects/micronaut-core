package io.micronaut.http.client.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class DateTimeConversionSpec extends Specification {

    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            "spec.name": 'DateTimeConversionSpec',
    ])

    void "test offset date time conversion"() {
        given:
        TimeController controller = embeddedServer.applicationContext.getBean(TimeController)
        TimeClient client = embeddedServer.applicationContext.getBean(TimeClient)

        def now = controller.target
        def result = OffsetDateTime.parse(client.time(now))

        expect:
        result == now
    }

    static interface TimeApi {
        @Get(uri = "/offset", processes = MediaType.TEXT_PLAIN)
        String time(@NotNull @QueryValue OffsetDateTime time)
    }

    @Client("/convert/time")
    @Requires(property = 'spec.name', value = 'DateTimeConversionSpec')
    static interface TimeClient extends TimeApi {}

    @Controller("/convert/time")
    @Requires(property = 'spec.name', value = 'DateTimeConversionSpec')
    static class TimeController implements TimeApi {
        def target = OffsetDateTime.parse("2007-12-03T10:15:30+01:00")
        @Override
        String time(OffsetDateTime time) {
            assert target == time
            return time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }
    }
}
