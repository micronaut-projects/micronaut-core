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

import brave.SpanCustomizer
import brave.propagation.StrictCurrentTraceContext
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import spock.lang.Specification
import zipkin2.Span
import zipkin2.Span.Kind

import javax.inject.Inject

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpTracingSpec extends Specification {

    void "test basic http tracing"() {
        given:
        ApplicationContext context = buildContext()
        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/hello/John', String)

        then:
        response
        reporter.spans.size() == 2
        reporter.spans[0].tags().get("foo") == 'bar'
        reporter.spans[0].tags().get('http.path') == '/traced/hello/John'
        reporter.spans[0].name() == 'get /traced/hello/{name}'

        cleanup:
        context.close()
    }

    void "test rxjava http tracing"() {
        given:
        ApplicationContext context = buildContext()
        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/rxjava/John', String)

        then:
        response
        reporter.spans.size() == 2
        reporter.spans[0].tags().get('http.path') == '/traced/rxjava/John'
        reporter.spans[0].name() == 'get /traced/rxjava/{name}'
        reporter.spans[0].id() == reporter.spans[1].id()
        reporter.spans[0].kind() == Span.Kind.SERVER
        reporter.spans[0].tags().get("foo") == 'bar'

        reporter.spans[1].tags().get('http.path') == '/traced/rxjava/John'
        reporter.spans[1].id() == reporter.spans[1].id()
        reporter.spans[1].kind() == Span.Kind.CLIENT


        when:"An observeOn call is used"
        response = client.toBlocking().exchange('/traced/rxjava/observe', String)

        then:"The response is correct"
        response.body() == 'hello'

        cleanup:
        context.close()
    }

    void "test basic http trace error"() {
        given:
        ApplicationContext context = buildContext()
        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        client.toBlocking().exchange('/traced/error/John', String)

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response
        reporter.spans.size() == 3
        reporter.spans[0].tags().get('http.path') == '/traced/error/John'
        reporter.spans[0].tags().get('http.status_code') == '500'
        reporter.spans[0].tags().get('http.method') == 'GET'
        reporter.spans[0].tags().get('error') == 'bad'
        reporter.spans[0].name() == 'get /traced/error/{name}'
        reporter.spans[1].tags().get('http.path') == '/traced/error/John'
        reporter.spans[1].tags().get('http.status_code') == '500'
        reporter.spans[1].tags().get('http.method') == 'GET'
        reporter.spans[1].tags().get('error') == '500'
        reporter.spans[1].name() == 'get /traced/error/{name}'

        cleanup:
        context.close()
    }

    void "tested nested http tracing"() {
        given:
        ApplicationContext context = buildContext()
        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then:
        response
        reporter.spans.size() == 3
        reporter.spans[0].tags().get("foo") == 'bar'
        reporter.spans[0].tags().get('http.path') == '/traced/hello/John'
        reporter.spans[0].name() == 'get /traced/hello/{name}'
        reporter.spans[0].kind() == zipkin2.Span.Kind.SERVER
        reporter.spans[1].name() == 'get /traced/nested/{name}'
        reporter.spans[1].kind() == zipkin2.Span.Kind.SERVER
        reporter.spans[1].tags().get('http.method') == 'GET'
        reporter.spans[1].tags().get('http.path') == '/traced/nested/John'
        reporter.spans[2].tags().get("foo") == null
        reporter.spans[2].tags().get('http.path') == '/traced/nested/John'
        reporter.spans[2].name() == 'get'
        reporter.spans[2].kind() == zipkin2.Span.Kind.CLIENT

        cleanup:
        client.close()
        context.close()
    }

    void "tested nested http tracing with server without tracing"() {
        given:
        ApplicationContext appWithoutTracing = ApplicationContext.build().start()
        EmbeddedServer embeddedServerWithoutTracing = appWithoutTracing.getBean(EmbeddedServer).start()

        ApplicationContext context = ApplicationContext.build(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1,
                'micronaut.http.services.not-traced-client.urls[0]':"http://localhost:${embeddedServerWithoutTracing.port}",
        )
        .singletons(new StrictCurrentTraceContext(), new TestReporter())
        .start()

        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested-not-traced/John', String)

        then:
        response
        reporter.spans.size() == 3
        reporter.spans[0].tags().get('http.path') == '/not-traced/hello/John'
        reporter.spans[0].name() == 'get /not-traced/hello/{name}'
        reporter.spans[0].kind() == zipkin2.Span.Kind.CLIENT
        reporter.spans[0].remoteEndpoint().serviceName() == "not-traced-client"
        reporter.spans[1].name() == 'get /traced/nested-not-traced/{name}'
        reporter.spans[1].kind() == zipkin2.Span.Kind.SERVER
        reporter.spans[1].tags().get('http.method') == 'GET'
        reporter.spans[1].tags().get('http.path') == '/traced/nested-not-traced/John'
        reporter.spans[2].tags().get("foo") == null
        reporter.spans[2].tags().get('http.path') == '/traced/nested-not-traced/John'
        reporter.spans[2].name() == 'get'
        reporter.spans[2].kind() == zipkin2.Span.Kind.CLIENT

        cleanup:
        client.close()
        context.close()
        appWithoutTracing.close()
    }

    void "tested nested http error tracing"() {
        given:
        ApplicationContext context = buildContext()
        TestReporter reporter = context.getBean(TestReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        client.toBlocking().exchange('/traced/nestedError/John', String)

        then:
        def e = thrown(HttpClientResponseException)
        reporter.spans.size() == 5
        assertSpan(reporter.spans[0],
                "get /traced/error/{name}",
                "bad",
                "/traced/error/John",
                Span.Kind.SERVER)
        assertSpan(reporter.spans[1],
                "get /traced/error/{name}",
                "500",
                "/traced/error/John",
                Span.Kind.SERVER)
        assertSpan(reporter.spans[2],
                "get /traced/nestederror/{name}",
                "Internal Server Error: bad",
                "/traced/nestedError/John",
                Span.Kind.SERVER)
        assertSpan(reporter.spans[3],
                "get /traced/nestederror/{name}",
                "500",
                "/traced/nestedError/John",
                Span.Kind.SERVER)
        assertSpan(reporter.spans[4],
                "get",
                "Internal Server Error: Internal Server Error: bad",
                "/traced/nestedError/John",
                Span.Kind.CLIENT)


        cleanup:
        context.close()
    }

    private boolean assertSpan(Span span, String name, String error, String path, Kind kind) {
        return span.tags().get('http.path') == path &&
                span.tags().get('error') == error &&
                span.name() == name &&
                span.kind() == kind
    }

    ApplicationContext buildContext() {
        def reporter = new TestReporter()
        ApplicationContext.build(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1
        )
        .singletons(new StrictCurrentTraceContext(), reporter)
        .start()
    }

    @Controller('/traced')
    static class TracedController {
        @Inject SpanCustomizer spanCustomizer
        @Inject TracedClient tracedClient
        @Inject NotTracedEndpointClient notTracedEndpointClient

        @Get("/hello/{name}")
        String hello(String name) {
            spanCustomizer.tag("foo", "bar")
            return name
        }

        @Get(value = "/rxjava/observe", produces = MediaType.TEXT_PLAIN)
        Single<String> index() {
            return Single.just("hello").observeOn(Schedulers.computation()).map( { r ->
                if (ServerRequestContext.currentRequest().isPresent()) {
                    return r;
                } else {
                    throw new RuntimeException("fail");
                }
            });
        }

        @Get("/rxjava/{name}")
        Single<String> rxjava(String name) {
            Single.fromCallable({->
                spanCustomizer.tag("foo", "bar")
                return name
            }).subscribeOn(Schedulers.io())
        }

        @Get("/error/{name}")
        String error(String name) {
            throw new RuntimeException("bad")
        }

        @Get("/nested/{name}")
        String nested(String name) {
            tracedClient.hello(name)
        }

        @Get("/nested-not-traced/{name}")
        String nestedNotTraced(String name) {
            notTracedEndpointClient.hello(name)
        }

        @Get("/nestedError/{name}")
        String nestedError(String name) {
            tracedClient.error(name)
        }
    }

    @Client('/traced')
    static interface TracedClient {
        @Get("/hello/{name}")
        String hello(String name)

        @Get("/error/{name}")
        String error(String name)
    }

    @Controller('/not-traced')
    static class NotTracedController {

        @Get("/hello/{name}")
        String hello(String name) {
            return name
        }
    }

    @Client(id = "not-traced-client")
    static interface NotTracedEndpointClient {
        @Get("/not-traced/hello/{name}")
        String hello(String name)
    }
}
