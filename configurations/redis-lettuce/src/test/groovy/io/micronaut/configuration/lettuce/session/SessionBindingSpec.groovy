/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.configuration.lettuce.session
import okhttp3.OkHttpClient
import okhttp3.Request
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.annotation.Controller
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.session.annotation.SessionValue
import io.micronaut.http.annotation.Get
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SessionBindingSpec extends Specification {

    void "test bind simple session argument using HTTP header processing"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'redis.type':'embedded',
                'micronaut.session.http.redis.enabled':'true'
        ])
        OkHttpClient httpClient = new OkHttpClient()


        when:
        def request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/simple"))
        def response = httpClient.newCall(request.build())
                .execute()

        then:
        response.body().string() == "not in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        def sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)


        request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/simple"))
                .header(HttpHeaders.AUTHORIZATION_INFO, sessionId)
        response = httpClient.newCall(request.build())
                .execute()
        then:
        response.body().string() == "value in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        cleanup:
        embeddedServer.stop()
    }


    void "test bind simple session argument using Cookie processing"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'redis.type':'embedded',
                'micronaut.session.http.redis.enabled':'true'
        ])
        OkHttpClient httpClient = new OkHttpClient()


        when:
        def request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/simple"))
        def response = httpClient.newCall(request.build())
                .execute()

        then:
        response.body().string() == "not in session"
        response.header(HttpHeaders.SET_COOKIE)

        when:
        def sessionId = response.header(HttpHeaders.SET_COOKIE)


        request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/simple"))
                .header(HttpHeaders.COOKIE, sessionId)
        response = httpClient.newCall(request.build())
                .execute()
        then:
        response.body().string() == "value in session"
        response.header(HttpHeaders.SET_COOKIE)

        cleanup:
        embeddedServer.stop()
    }

    void "test bind optional session"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'redis.type':'embedded',
                'micronaut.session.http.redis.enabled':'true'
        ])
        OkHttpClient httpClient = new OkHttpClient()


        when:
        def request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/optional"))
        def response = httpClient.newCall(request.build())
                .execute()

        then:
        response.body().string() == "no session"
        !response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/simple"))
        response = httpClient.newCall(request.build())
                .execute()

        then:
        response.body().string() == "not in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        def sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)


        request = new Request.Builder().url(new URL(embeddedServer.getURL(), "/sessiontest/optional"))
                .header(HttpHeaders.AUTHORIZATION_INFO, sessionId)
        response = httpClient.newCall(request.build())
                .execute()
        then:
        response.body().string() == "value in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        cleanup:
        embeddedServer.stop()
    }

    @Controller('/sessiontest')
    @Singleton
    static class SessionController {

        @Get("/simple")
        String simple(Session session) {
            return session.get("myValue", String).orElseGet({
                session.put("myValue", "value in session")
                "not in session"
            })
        }

        @Get("/value")
        String value(@SessionValue Optional<String> myValue) {
            return myValue.orElse(
                    "no value in session"
            )
        }


        @Get("/optional")
        String optional(Optional<Session> session) {
            if(session.isPresent()) {
                def s = session.get()
                return s.get("myValue", String).orElseGet({
                    s.put("myValue", "value in session")
                    "not in session"
                })
            }
            else {
                return "no session"
            }
        }
    }
}
