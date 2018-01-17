/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.errors

import groovy.json.JsonSlurper
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Get

import javax.inject.Singleton

/**
 * Tests for different kinds of errors and the expected responses
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ErrorSpec extends AbstractParticleSpec {

    void "test 500 server error"() {
        given:
        def request = new Request.Builder()
                .url(new URL(server, '/errors/serverError'))
        def response = client.newCall(request.build()).execute()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_VND_ERROR
        new JsonSlurper().parseText(response.body().string()).message == 'Internal Server Error: bad'
    }

    void "test 404 error"() {
        given:
        def request = new Request.Builder()
                .url(new URL(server, '/errors/blah'))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.NOT_FOUND.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_VND_ERROR


        when:
        def json = new JsonSlurper().parseText(response.body().string())

        then:
        json.message == 'Page Not Found'
        json._links.self.href == '/errors/blah'
    }

    void "test 405 error"() {
        given:
        def request = new Request.Builder()
                .url(new URL(server, '/errors/serverError'))
                .post(RequestBody.create(okhttp3.MediaType.parse("text/plain"), "blah"))
        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.METHOD_NOT_ALLOWED.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_VND_ERROR


        when:
        def json = new JsonSlurper().parseText(response.body().string())

        then:
        json.message == 'Method [POST] not allowed. Allowed methods: [GET]'
        json._links.self.href == '/errors/serverError'
    }
    @Controller('/errors')
    @Singleton
    static class ErrorController {

        @Get
        String serverError() {
            throw new RuntimeException("bad")
        }
    }
}
