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
package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.tracing.brave.sender.HttpClientSender
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Retry
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import zipkin2.Span
import zipkin2.reporter.Sender
import io.micronaut.core.async.annotation.SingleResult

/**
 * @author graemerocher
 * @since 1.0
 */
@Retry
class HttpClientSenderSpec extends Specification {

    void "test http client sender bean initialization with instrumented threads"() {
        given:
        ApplicationContext context = ApplicationContext.run(
          'tracing.zipkin.enabled':true,
          'tracing.instrument-threads':true,
          'tracing.zipkin.sampler.probability':1,
          'tracing.zipkin.http.url':HttpClientSender.Builder.DEFAULT_SERVER_URL
        )

        when:
        Sender httpClientSender = context.getBean(Sender)

        then:
        httpClientSender != null
        httpClientSender instanceof HttpClientSender

        cleanup:
        httpClientSender.close()
        context.close()
    }

    void "test http client sender receives spans"() {
        given:
        EmbeddedServer zipkinServer = ApplicationContext.run(
                EmbeddedServer,
                ['micronaut.server.port':-1]
        )
        SpanController spanController = zipkinServer.applicationContext.getBean(SpanController)

        ApplicationContext context = ApplicationContext.run(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1,
                'tracing.zipkin.http.url':zipkinServer.getURL().toString()
        )

        when:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions(timeout: 10)
        StartedListener listener = zipkinServer.applicationContext.getBean(StartedListener)

        then:
        conditions.eventually {
            listener.started
        }

        when:"Requests are executed"
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)


        then:"spans are received"
        conditions.eventually {
            response.status() == HttpStatus.OK
            spanController.receivedSpans.size() == 4
            spanController.receivedSpans[0].tags.get("foo") == 'bar'
            spanController.receivedSpans[0].tags.get('http.path') == '/traced/hello/John'
            spanController.receivedSpans[0].name == 'get /traced/hello/{name}'
            spanController.receivedSpans[0].kind == Span.Kind.SERVER.name()
            spanController.receivedSpans[1].tags.get('http.path') == '/traced/hello/John'
            spanController.receivedSpans[1].name == 'get /traced/hello/{name}'
            spanController.receivedSpans[1].kind == Span.Kind.CLIENT.name()
            spanController.receivedSpans[2].name == 'get /traced/nested/{name}'
            spanController.receivedSpans[2].kind == Span.Kind.SERVER.name()
            spanController.receivedSpans[2].tags.get('http.method') == 'GET'
            spanController.receivedSpans[2].tags.get('http.path') == '/traced/nested/John'
            spanController.receivedSpans[3].tags.get("foo") == null
            spanController.receivedSpans[3].tags.get('http.path') == '/traced/nested/John'
            spanController.receivedSpans[3].name == 'get'
            spanController.receivedSpans[3].kind == Span.Kind.CLIENT.name()

        }

        cleanup:
        client.close()
        context.close()
        zipkinServer.close()

    }

    void "test http client sender receives spans with custom path"() {
        given:
        EmbeddedServer zipkinServer = ApplicationContext.run(
                EmbeddedServer,
                ['micronaut.server.port':-1]
        )
        ApplicationContext context = ApplicationContext.run(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1,
                'tracing.zipkin.http.url':zipkinServer.getURL().toString(),
                'tracing.zipkin.http.path':'/custom/path/spans'
        )

        when:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        PollingConditions conditions = new PollingConditions(timeout: 10)
        CustomPathSpanController customPathSpanController = zipkinServer.applicationContext.getBean(CustomPathSpanController)
        StartedListener listener = zipkinServer.applicationContext.getBean(StartedListener)

        then:
        conditions.eventually {
            listener.started
        }

        when:"Requests are executed"
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)


        then:"spans are received"
        conditions.eventually {
            response.status() == HttpStatus.OK
            customPathSpanController.receivedSpans.size() == 4
            customPathSpanController.receivedSpans[0].tags.get("foo") == 'bar'
            customPathSpanController.receivedSpans[0].tags.get('http.path') == '/traced/hello/John'
            customPathSpanController.receivedSpans[0].name == 'get /traced/hello/{name}'
            customPathSpanController.receivedSpans[0].kind == Span.Kind.SERVER.name()
            customPathSpanController.receivedSpans[1].tags.get('http.path') == '/traced/hello/John'
            customPathSpanController.receivedSpans[1].name == 'get /traced/hello/{name}'
            customPathSpanController.receivedSpans[1].kind == Span.Kind.CLIENT.name()
            customPathSpanController.receivedSpans[2].name == 'get /traced/nested/{name}'
            customPathSpanController.receivedSpans[2].kind == Span.Kind.SERVER.name()
            customPathSpanController.receivedSpans[2].tags.get('http.method') == 'GET'
            customPathSpanController.receivedSpans[2].tags.get('http.path') == '/traced/nested/John'
            customPathSpanController.receivedSpans[3].tags.get("foo") == null
            customPathSpanController.receivedSpans[3].tags.get('http.path') == '/traced/nested/John'
            customPathSpanController.receivedSpans[3].name == 'get'
            customPathSpanController.receivedSpans[3].kind == Span.Kind.CLIENT.name()

        }

        cleanup:
        client.close()
        context.close()
        zipkinServer.close()

    }

    @Controller('/api/v2')
    static class SpanController {
        List<Map> receivedSpans = []
        @Post('/spans')
        @SingleResult
        Publisher<HttpResponse> spans(@Body Publisher<Map> spans) {
            Flux.from(spans)
                    .collectList()
                    .map({ List list ->
                println "SPANS $list"
                receivedSpans.addAll(list)
                HttpResponse.ok()
            })
        }

    }

    @Controller('/custom/path')
    static class CustomPathSpanController {
        List<Map> receivedSpans = []
        @Post('/spans')
        @SingleResult
        Publisher<HttpResponse> spans(@Body Publisher<Map> spans) {
            Flux.from(spans).collectList().map({ List list ->
                println "SPANS $list"
                receivedSpans.addAll(list)
                HttpResponse.ok()
            })
        }
    }

    @Singleton
    static class StartedListener implements ApplicationEventListener<ServerStartupEvent> {
        boolean started
        @Override
        void onApplicationEvent(ServerStartupEvent event) {
            started = true
        }
    }
}
