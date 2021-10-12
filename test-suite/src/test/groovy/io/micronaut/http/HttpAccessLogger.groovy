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
package io.micronaut.http

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.annotation.Property
import io.micronaut.core.type.Argument
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.Specification

import jakarta.inject.Inject
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * @author Christophe Roudet
 * @since 2.0
 */
@MicronautTest
@Property(name = "micronaut.server.netty.log-level", value = 'trace')
@Property(name = "micronaut.http.client.log-level", value = 'trace')
@Property(name = 'micronaut.server.netty.access-logger.enabled', value = 'true')
class HttpAccessLoggerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    static MemoryAppender appender = new MemoryAppender()

    static {
        Logger l = (Logger) LoggerFactory.getLogger("HTTP_ACCESS_LOGGER")
        l.addAppender(appender)
        appender.start()
    }

    @Inject
    EmbeddedServer embeddedServer

    void "test simple get request with type - access logger"() {
        when:
        appender.events.clear()
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.GET("/get/simple"), String
        ))
        HttpResponse<String> response = flowable.blockFirst()
        def body = response.getBody()


        then:
        response.status == HttpStatus.OK
        body.isPresent()
        body.get() == 'success'
        appender.headLog(10).contains("" + HttpStatus.OK.getCode())
    }

    void "test simple 404 request - access logger"() {

        when:
        appender.events.clear()
        def flowable = Flux.from(client.exchange(
                HttpRequest.GET("/get/doesntexist")
        ))
        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.getBody(Map).get()._embedded.errors[0].message == "Page Not Found"
        e.status == HttpStatus.NOT_FOUND
       appender.headLog(10).contains("" + HttpStatus.NOT_FOUND.getCode())
    }

    void "test 500 request with body - access logger"() {

        when:
        appender.events.clear()
        def flowable = Flux.from(client.exchange(
                HttpRequest.GET("/get/error"), Argument.of(String), Argument.of(String)
        ))
        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Server error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.getBody(String).get() == "Server error"
        appender.headLog(10).contains("" + HttpStatus.INTERNAL_SERVER_ERROR.getCode())

    }

     void "test simple session - access logger"() {
        when:
        appender.events.clear()
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.GET("/sessiontest/simple"), String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.getBody().get() == "not in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)
        // host - - [25/May/2020:15:14:00 -0400] "GET /get/simple HTTP/1.1" 200 7
        // host - f9d1c6b2-2980-4e6a-826c-bdfc6a21417c [25/May/2020:15:14:00 -0400] "GET /sessiontest/simple HTTP/1.1" 200 14
        !appender.headLog(10).contains(" - - [")

        when:
        def sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)
        flowable = Flux.from(client.exchange(
                HttpRequest.GET("/sessiontest/simple")
                        .header(HttpHeaders.AUTHORIZATION_INFO, sessionId)
                , String
        ))
        response = flowable.blockFirst()

        then:
        response.getBody().get() == "value in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)
        !appender.headLog(10).contains(" - - [")
    }

    @Controller("/get")
    static class GetController {

        @Get(value = "/simple", produces = MediaType.TEXT_PLAIN)
        String simple() {
            return "success"
        }

        @Get(value = "/error", produces = MediaType.TEXT_PLAIN)
        HttpResponse error() {
            return HttpResponse.serverError().body("Server error")
        }

    }

    @Controller('/sessiontest')
    static class SessionController {

        @Get("/simple")
        String simple(Session session) {
            return session.get("myValue").orElseGet({
                session.put("myValue", "value in session")
                "not in session"
            })
        }

    }

    private static class MemoryAppender extends AppenderBase<ILoggingEvent> {
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>()

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e.formattedMessage)
        }

        public Queue<String> getEvents() {
            return events
        }

        public String headLog(long timeout) {
            return events.poll(timeout, TimeUnit.SECONDS)
        }
    }

}
