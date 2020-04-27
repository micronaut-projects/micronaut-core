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
package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.JaegerTracer
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.annotation.ContinueSpan
import io.opentracing.Tracer
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

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
        context.containsBean(JaegerTracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/hello/John', String)
        PollingConditions conditions = new PollingConditions()

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2
            def span = reporter.spans.find { it.operationName == 'GET /traced/hello/{name}' }
            span != null
            span.tags.get("foo") == 'bar'
            span.tags.get('http.path') == '/traced/hello/John'
        }

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
        PollingConditions conditions = new PollingConditions()

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2
            def span = reporter.spans.find { it.operationName == 'GET /traced/rxjava/{name}' }
            span != null
            span.tags.get("foo") == 'bar'
            span.tags.get('http.path') == '/traced/rxjava/John'
        }

        cleanup:
        context.close()
    }

    void "test basic http trace error"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/error/John', String)


        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response
        conditions.eventually {
            reporter.spans.size() == 2
            def span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags.get('http.path') == '/traced/error/John'
            span.tags.get('http.status_code') == 500
            span.tags.get('http.method') == 'GET'
            span.tags.get('error') == 'Internal Server Error: bad'
            span.operationName == 'GET /traced/error/John'

        }


        cleanup:
        context.close()
    }

    void "tested continue http tracing"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/continued/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/hello/{name}' &&
                        it.tags.get('foo') == 'bar' &&
                        it.tags.get('http.path') == '/traced/hello/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/hello/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/hello/John' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/continued/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/continued/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/continued/John' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/continued/John' &&
                        it.tags.get('http.client')
            } != null
        }

        cleanup:
        client.close()
        context.close(

        )
    }

    void "tested continue http tracing - rx"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/continueRx/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/hello/{name}' &&
                        it.tags.get('foo') == 'bar' &&
                        it.tags.get('http.path') == '/traced/hello/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/hello/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/hello/John' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/continueRx/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/continueRx/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/continueRx/John' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/continueRx/John' &&
                        it.tags.get('http.client')
            } != null
        }

        cleanup:
        client.close()
        context.close(

        )
    }

    void "tested nested http tracing"() {
        given:
        ApplicationContext context = buildContext()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/hello/{name}' &&
                it.tags.get('foo') == 'bar' &&
                it.tags.get('http.path') == '/traced/hello/John' &&
                it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/hello/{name}' &&
                !it.tags.get('foo') &&
                it.tags.get('http.path') == '/traced/hello/John' &&
                it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/nested/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/nested/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/nested/John' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/nested/John' &&
                        it.tags.get('http.client')
            } != null
        }

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
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/nestedError/John', String)

        then:
        def e = thrown(HttpClientResponseException)
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/error/{name}' &&
                        !it.tags.containsKey("error") &&
                        it.tags.get('http.path') == '/traced/error/John' &&
                        it.tags.get('http.server')

            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/error/{name}' &&
                        it.tags.get('http.path') == '/traced/error/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('error') == 'Internal Server Error: bad' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/nestedError/{name}' &&
                        !it.tags.containsKey("error") &&
                        it.tags.get('http.path') == '/traced/nestedError/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/nestedError/John' &&
                        it.tags.get('http.path') == '/traced/nestedError/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('error')  &&
                        it.tags.get('http.client')
            } != null
        }

        cleanup:
        context.close()
    }

    void "tested continue nested http tracing - rx"() {
        given:
        ApplicationContext context = buildContext()
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nestedRx/John', String)

        then:
        response.body() == "10"


        cleanup:
        client.close()
        context.close()
    }

    void "tested customising span name"() {
        given:
        ApplicationContext context = buildContext()
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/customised/name', String)

        then:
        conditions.eventually {
            reporter.spans.any { it.operationName == "custom name" }
        }

        cleanup:
        client.close()
        context.close()
    }


    ApplicationContext buildContext() {
        def reporter = new InMemoryReporter()
        ApplicationContext.builder(
            'tracing.jaeger.enabled':true,
            'tracing.jaeger.sampler.probability':1
        ).singletons(reporter)
         .start()
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

        @ContinueSpan
        @Get("/continued/{name}")
        String continued(String name) {
            tracedClient.continued(name)
        }

        @ContinueSpan
        @Get("/continueRx/{name}")
        Single<String> continuedRx(String name) {
            tracedClient.continuedRx(name)
        }

        @Get("/nestedError/{name}")
        String nestedError(String name) {
            tracedClient.error(name)
        }

        @Get("/customised/name")
        String cusomisedName() {
            spanCustomizer.activeSpan().setOperationName("custom name")
            "response"
        }

        @Get("/nestedRx/{name}")
        Single<String> nestedRx(String name) {
            spanCustomizer.activeSpan().setBaggageItem("foo", "bar")
            tracedClient.continuedRx(name)
                .flatMap({ String res ->
                    assert spanCustomizer.activeSpan().getBaggageItem("foo") == "bar"
                    return tracedClient.nestedRx2(res)
                })
        }

        @Get("/nestedRx2/{name}")
        Single<Integer> nestedRx2(String name) {
            assert spanCustomizer.activeSpan().getBaggageItem("foo") == "bar"
            return Single.just(10)
        }
    }

    @Client('/traced')
    static interface TracedClient {
        @Get("/hello/{name}")
        String hello(String name)

        @Get("/error/{name}")
        String error(String name)

        @Get("/hello/{name}")
        String continued(String name)

        @Get("/hello/{name}")
        Single<String> continuedRx(String name)

        @Get("/nestedRx2/{name}")
        Single<String> nestedRx2(String name)
    }
}
