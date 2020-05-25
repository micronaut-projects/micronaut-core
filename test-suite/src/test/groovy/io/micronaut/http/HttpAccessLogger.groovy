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

import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.Format
import io.micronaut.core.type.Argument
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http2.Http2AccessLoggerSpec.MemoryAppender
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.session.annotation.SessionValue
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.Consumer
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.annotation.Nullable
import javax.inject.Inject

import org.slf4j.LoggerFactory

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import java.time.LocalDate
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
    RxHttpClient client

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
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/simple"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
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
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/doesntexist")
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Page Not Found"
        e.status == HttpStatus.NOT_FOUND
       appender.headLog(10).contains("" + HttpStatus.NOT_FOUND.getCode())
    }

    void "test 500 request with body - access logger"() {

        when:
        appender.events.clear()
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/error"), Argument.of(String), Argument.of(String)
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Server error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.getBody(String).get() == "Server error"
        appender.headLog(10).contains("" + HttpStatus.INTERNAL_SERVER_ERROR.getCode())

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
