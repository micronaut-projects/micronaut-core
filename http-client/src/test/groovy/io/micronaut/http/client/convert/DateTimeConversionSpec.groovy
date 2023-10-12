/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.constraints.NotNull
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class DateTimeConversionSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "test offset date time conversion"() {
        given:
        TimeController controller = embeddedServer.getApplicationContext().getBean(TimeController)
        TimeClient client = embeddedServer.getApplicationContext().getBean(TimeClient)

        def now = controller.targetOffset
        def result = OffsetDateTime.parse(client.offsetDateTime(now))

        expect:
        result == now
    }

    void "test local date time conversion"() {
        given:
        TimeController controller = embeddedServer.getApplicationContext().getBean(TimeController)
        TimeClient client = embeddedServer.getApplicationContext().getBean(TimeClient)

        def now = controller.targetLocal
        def result = LocalDateTime.parse(client.localDateTime(now))

        expect:
        result == now
    }

    static interface TimeApi {
        @Get(uri = "/offset", processes = MediaType.TEXT_PLAIN)
        String offsetDateTime(@NotNull @QueryValue OffsetDateTime time)

        @Get(uri = "/local", processes = MediaType.TEXT_PLAIN)
        String localDateTime(@NotNull @QueryValue LocalDateTime time)
    }

    @Client("/convert/time")
    static interface TimeClient extends TimeApi {}

    @Controller("/convert/time")
    static class TimeController implements TimeApi {
        def targetOffset = OffsetDateTime.parse("2007-12-03T10:15:30+01:00")
        def targetLocal = targetOffset.toLocalDateTime()

        @Override
        String offsetDateTime(OffsetDateTime time) {
            assert targetOffset == time
            return time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }

        @Override
        String localDateTime(@NotNull @QueryValue LocalDateTime time) {
            assert targetLocal == time
            return time.format(DateTimeFormatter.ISO_DATE_TIME)
        }
    }


}
