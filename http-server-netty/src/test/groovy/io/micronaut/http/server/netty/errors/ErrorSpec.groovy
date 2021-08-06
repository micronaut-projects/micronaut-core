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
package io.micronaut.http.server.netty.errors

import groovy.json.JsonSlurper
import io.micronaut.context.annotation.Property
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.server.netty.AbstractMicronautSpec
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import io.micronaut.core.async.annotation.SingleResult

/**
 * Tests for different kinds of errors and the expected responses
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ErrorSpec extends AbstractMicronautSpec {

    void "test 500 server error"() {
        given:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/server-error')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: bad'
    }

    void "test 500 server error IOException"() {
        given:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/io-error')

        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: null'
    }

    void "test an error route throwing the same exception it handles"() {
        given:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/loop')

        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: null'
    }

    void "test an exception handler throwing the same exception it handles"() {
        given:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/loop/handler')

        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: null'
    }

    void "test 404 error"() {
        when:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/blah')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        then:
        response.code() == HttpStatus.NOT_FOUND.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json._embedded.errors[0].message == 'Page Not Found'
        json._links.self.href == '/errors/blah'
    }

    void "test 405 error"() {
        when:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/errors/server-error', 'blah')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        then:
        response.code() == HttpStatus.METHOD_NOT_ALLOWED.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json._embedded.errors[0].message.matches('Method \\[POST\\] not allowed for URI \\[/errors/server-error\\]. Allowed methods: \\[(GET|HEAD), (GET|HEAD)\\]')
        json._links.self.href == '/errors/server-error'
    }

    void "test content type for error handler"() {
        given:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/handler-content-type-error')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.TEXT_HTML
        response.getBody(String).get() == '<div>Error</div>'
    }

    void "test calling a controller that fails to inject with a local error handler"() {
        given:
        HttpResponse response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/errors/injection')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.getBody(Map).get()._embedded.errors[0].message.contains("Failed to inject value for parameter [prop]")
    }

    @Controller('/errors')
    static class ErrorController {

        @Get('/server-error')
        String serverError() {
            throw new RuntimeException("bad")
        }

        @Get("/io-error")
        @SingleResult
        Publisher<String> ioError() {
            return Mono.create({ emitter ->
                emitter.error(new IOException())
            })
        }

        @Get("/handler-content-type-error")
        String handlerContentTypeError() {
            throw new ContentTypeExceptionHandlerException()
        }
    }

    @Controller('/errors/loop')
    static class ErrorLoopController {

        @Get()
        String serverError() {
            throw new LoopingException()
        }

        @Error(LoopingException)
        String loop() {
            throw new LoopingException()
        }
    }

    @Controller('/errors/loop/handler')
    static class ErrorLoopHandlerController {

        @Get()
        String serverError() {
            throw new LoopingException()
        }
    }

    @Controller('/errors/injection')
    static class ErrorInjectionController {

        ErrorInjectionController(@Property(name = "does.not.exist") String prop) {}

        @Get
        String ok() {
            "OK"
        }

        @Error
        HttpResponse<String> error(HttpRequest<?> request, Throwable e) {
            HttpResponse.serverError("Server error")
        }
    }


    @Produces(value = MediaType.TEXT_HTML)
    @Singleton
    static class ContentTypeExceptionHandler implements ExceptionHandler<ContentTypeExceptionHandlerException, HttpResponse<String>> {

        @Override
        HttpResponse<String> handle(HttpRequest r, ContentTypeExceptionHandlerException exception) {
            HttpResponse.serverError("<div>Error</div>")
        }
    }

    static class ContentTypeExceptionHandlerException extends RuntimeException {}

    static class LoopingException extends RuntimeException {}

    @Singleton
    static class LoopingExceptionHandler implements ExceptionHandler<LoopingException, String> {
        @Override
        String handle(HttpRequest request, LoopingException exception) {
            throw new LoopingException()
        }
    }
}
