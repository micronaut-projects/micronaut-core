/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.web.router.Router
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

class MessageBodyReaderSpecializationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'MessageBodyReaderSpecializationSpec'])

    void "routes retain separate specialized readers for the same body type"() {
        given:
        def router = context.getBean(Router)
        def factory = context.getBean(ReaderFactory)
        def left = router.POST('/specialized-reader/left').orElseThrow().routeInfo.messageBodyReader
        def right = router.POST('/specialized-reader/right').orElseThrow().routeInfo.messageBodyReader
        def headers = HttpRequest.GET('/').headers

        expect:
        !left.is(right)
        ['first', 'second'].each { payload ->
            assert left.read(Argument.STRING, null, headers, new ByteArrayInputStream(payload.bytes)) == "left:${payload}"
            assert right.read(Argument.STRING, null, headers, new ByteArrayInputStream(payload.bytes)) == "right:${payload}"
        }
        router.POST('/specialized-reader/left').orElseThrow().routeInfo.messageBodyReader.is(left)
        factory.specializations*.name.sort() == ['left', 'right']
    }

    @Controller('/specialized-reader')
    @Requires(property = 'spec.name', value = 'MessageBodyReaderSpecializationSpec')
    static class TestController {
        @Post(value = '/left', consumes = 'application/x-specialized', produces = MediaType.TEXT_PLAIN)
        String left(@Body String left) {
            left
        }

        @Post(value = '/right', consumes = 'application/x-specialized', produces = MediaType.TEXT_PLAIN)
        String right(@Body String right) {
            right
        }
    }

    @Singleton
    @Order(-100)
    @Consumes('application/x-specialized')
    @Requires(property = 'spec.name', value = 'MessageBodyReaderSpecializationSpec')
    static class ReaderFactory implements MessageBodyReader<String> {
        final List<Argument<String>> specializations = []

        @Override
        MessageBodyReader<String> createSpecific(Argument<String> type) {
            specializations.add(type)
            new SpecializedReader(type)
        }

        @Override
        String read(Argument<String> type, MediaType mediaType, Headers headers, InputStream input) {
            throw new IllegalStateException('The route must use the specialized reader')
        }
    }

    static class SpecializedReader implements MessageBodyReader<String> {
        private final Argument<String> argument

        SpecializedReader(Argument<String> argument) {
            this.argument = argument
        }

        @Override
        String read(Argument<String> type, MediaType mediaType, Headers headers, InputStream input) {
            "${argument.name}:${input.getText('UTF-8')}"
        }
    }
}
