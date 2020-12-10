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
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Produces
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Get
import io.reactivex.Single

import javax.inject.Singleton

/**
 * Tests for different kinds of errors and the expected responses
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ErrorSpec extends AbstractMicronautSpec {

    void "test 500 server error"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/errors/server-error')

        ).onErrorReturn({ t -> t.response.getBody(JsonError); return t.response } ).blockingFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(JsonError).get().message == 'Internal Server Error: bad'
    }

    void "test 500 server error IOException"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/errors/io-error')

        ).onErrorReturn({ t -> t.response.getBody(JsonError); return t.response } ).blockingFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(JsonError).get().message == 'Internal Server Error: null'
    }

    void "test 404 error"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/errors/blah')

        ).onErrorReturn({ t -> t.response.getBody(String); return t.response } ).blockingFirst()

        then:
        response.code() == HttpStatus.NOT_FOUND.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json.message == 'Page Not Found'
        json._links.self.href == '/errors/blah'
    }

    void "test 405 error"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.POST('/errors/server-error', 'blah')

        ).onErrorReturn({ t -> t.response.getBody(String); return t.response } ).blockingFirst()

        then:
        response.code() == HttpStatus.METHOD_NOT_ALLOWED.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json.message.matches('Method \\[POST\\] not allowed for URI \\[/errors/server-error\\]. Allowed methods: \\[(GET|HEAD), (GET|HEAD)\\]')
        json._links.self.href == '/errors/server-error'
    }

    void "test content type for error handler"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/errors/handler-content-type-error')

        ).onErrorReturn({ t -> t.response; return t.response } ).blockingFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.TEXT_HTML
        response.getBody(String).get() == '<div>Error</div>'
    }

    void "test calling a controller that fails to inject with a local error handler"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/errors/injection')

        ).onErrorReturn({ t -> t.response; return t.response } ).blockingFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.getBody(JsonError).get().message.contains("Failed to inject value for parameter [prop]")
    }

    @Controller('/errors')
    static class ErrorController {

        @Get('/server-error')
        String serverError() {
            throw new RuntimeException("bad")
        }

        @Get("/io-error")
        Single<String> ioError() {
            return Single.create({ emitter ->
                emitter.onError(new IOException())
            })
        }

        @Get("/handler-content-type-error")
        String handlerContentTypeError() {
            throw new ContentTypeExceptionHandlerException()
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

}
