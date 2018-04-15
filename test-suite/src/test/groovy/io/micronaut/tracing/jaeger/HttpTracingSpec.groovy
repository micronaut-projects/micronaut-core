/*
 * Copyright 2018 original authors
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
package io.micronaut.tracing.jaeger

import io.jaegertracing.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.opentracing.Tracer
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpTracingSpec extends Specification {
    void "test basic http tracing"() {
        given:
        ApplicationContext context = buildContext()

        when:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        then:
        context.containsBean(io.jaegertracing.Tracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/hello/John', String)

        then:
        response
        reporter.spans.size() == 2
        reporter.spans[0].tags.get("foo") == 'bar'
        reporter.spans[0].tags.get('http.path') == '/traced/hello/John'
        reporter.spans[0].operationName == 'GET /traced/hello/{name}'

        cleanup:
        context.close()
    }

    void "test rxjava http tracing"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/rxjava/John', String)

        then:
        response
        reporter.spans.size() == 2
        reporter.spans[0].tags.get('http.path') == '/traced/rxjava/John'
        reporter.spans[0].operationName == 'GET /traced/rxjava/{name}'
        reporter.spans[0].tags.get("foo") == 'bar'

        cleanup:
        context.close()
    }

    void "test basic http trace error"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        client.toBlocking().exchange('/traced/error/John', String)

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response
        reporter.spans.size() == 2
        reporter.spans[0].tags.get('http.path') == '/traced/error/John'
        reporter.spans[1].tags.get('http.path') == '/traced/error/John'
        reporter.spans[1].tags.get('http.status_code') == 500
        reporter.spans[1].tags.get('http.method') == 'GET'
        reporter.spans[1].tags.get('error') == 'Internal Server Error: bad'
        reporter.spans[1].operationName == 'GET /traced/error/John'

        cleanup:
        context.close()
    }

    void "tested nested http tracing"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then:
        response
        reporter.spans.size() == 4
        reporter.spans[0].tags.get("foo") == 'bar'
        reporter.spans[0].tags.get('http.path') == '/traced/hello/John'
        reporter.spans[0].operationName == 'GET /traced/hello/{name}'
        reporter.spans[1].operationName == 'GET /traced/hello/{name}'
        reporter.spans[2].tags.get("foo") == null
        reporter.spans[2].tags.get('http.path') == '/traced/nested/John'
        reporter.spans[2].operationName == 'GET /traced/nested/{name}'

        cleanup:
        client.close()
        context.close()
    }

    void "tested nested http error tracing"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        client.toBlocking().exchange('/traced/nestedError/John', String)

        then:
        def e = thrown(HttpClientResponseException)
        reporter.spans.size() == 4
        reporter.spans[0].tags.get('http.path') == '/traced/error/John'
        reporter.spans[0].operationName == 'GET /traced/error/{name}'
        reporter.spans[1].operationName == 'GET /traced/error/{name}'
        reporter.spans[2].tags.get('http.path') == '/traced/nestedError/John'
        reporter.spans[2].operationName == 'GET /traced/nestedError/{name}'

        cleanup:
        context.close()
    }


    ApplicationContext buildContext() {
        ApplicationContext context = ApplicationContext.build()
        context.environment.addPropertySource(PropertySource.of(
                'tracing.jaeger.enabled':true,
                'tracing.jaeger.sampler.param':1))
        def reporter = new InMemoryReporter()
        context.registerSingleton(reporter)
        context.start()
    }

    @Controller('/traced')
    static class TracedController {
        @Inject Tracer spanCustomizer
        @Inject TracedClient tracedClient

        @Get("/hello/{name}")
        String hello(String name) {
            spanCustomizer.activeSpan().setTag("foo", "bar")
            return name
        }

        @Get("/rxjava/{name}")
        Single<String> rxjava(String name) {
            Single.fromCallable({->
                spanCustomizer.activeSpan().setTag("foo", "bar")
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
}
