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

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.JaegerTracer
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.tracing.annotation.ContinueSpan
import io.opentracing.Tracer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import io.micronaut.core.async.annotation.SingleResult
import jakarta.inject.Inject
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpTracingSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = buildContext()

    void "test basic http tracing"() {

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
            JaegerSpan span = reporter.spans.find { it.operationName == 'GET /traced/hello/{name}' }
            span != null
            span.tags.get("foo") == 'bar'
            span.tags.get('http.path') == '/traced/hello/John'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test basic http tracing - blocking controller method"() {

        when:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        then:
        context.containsBean(JaegerTracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/blocking/hello/John', String)
        PollingConditions conditions = new PollingConditions()

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2
            JaegerSpan span = reporter.spans.find { it.operationName == 'GET /traced/blocking/hello/{name}' }
            span != null
            span.tags.get("foo") == 'bar'
            span.tags.get('http.path') == '/traced/blocking/hello/John'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test basic response reactive http tracing"() {

        when:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        then:
        context.containsBean(JaegerTracer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/response-reactive/John', String)
        PollingConditions conditions = new PollingConditions()

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2
            def span = reporter.spans.find { it.operationName == 'GET /traced/response-reactive/{name}' }
            span != null
            span.tags.get("foo") == 'bar'
            span.tags.get('http.path') == '/traced/response-reactive/John'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test reactive http tracing"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/reactive/John', String)
        PollingConditions conditions = new PollingConditions()

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 2
            JaegerSpan span = reporter.spans.find { it.operationName == 'GET /traced/reactive/{name}' }
            span != null
            span.tags.get("foo") == 'bar'
            span.tags.get('http.path') == '/traced/reactive/John'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test basic http trace error"() {
        given:
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
            span.tags.get('error') == 'Internal Server Error'
            span.operationName == 'GET /traced/error/John'
            def serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags.get('http.path') == '/traced/error/John'
            serverSpan.tags.get('http.status_code') == 500
            serverSpan.tags.get('http.method') == 'GET'
            serverSpan.tags.get('error') == 'Internal Server Error'
            serverSpan.operationName == 'GET /traced/error/{name}'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test basic http trace error - blocking controller method"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/blocking/error/John', String)

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response
        conditions.eventually {
            reporter.spans.size() == 2
            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags.get('http.path') == '/traced/blocking/error/John'
            span.tags.get('http.status_code') == 500
            span.tags.get('http.method') == 'GET'
            span.tags.get('error') == 'Internal Server Error'
            span.operationName == 'GET /traced/blocking/error/John'
            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags.get('http.path') == '/traced/blocking/error/John'
            serverSpan.tags.get('http.status_code') == 500
            serverSpan.tags.get('http.method') == 'GET'
            serverSpan.tags.get('error') == 'Internal Server Error'
            serverSpan.operationName == 'GET /traced/blocking/error/{name}'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test basic http trace error - reactive"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/reactiveError/John', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response
        conditions.eventually {
            reporter.spans.size() == 2
            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags.get('http.path') == '/traced/reactiveError/John'
            span.tags.get('http.status_code') == 500
            span.tags.get('http.method') == 'GET'
            span.tags.get('error') == 'Internal Server Error'
            span.operationName == 'GET /traced/reactiveError/John'
            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags.get('http.path') == '/traced/reactiveError/John'
            serverSpan.tags.get('http.status_code') == 500
            serverSpan.tags.get('http.method') == 'GET'
            serverSpan.tags.get('error') == 'Internal Server Error'
            serverSpan.operationName == 'GET /traced/reactiveError/{name}'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test basic http trace error - reactive with error handler"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/quota-error', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response
        conditions.eventually {
            reporter.spans.size() == 2
            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags.get('http.path') == '/traced/quota-error'
            span.tags.get('http.status_code') == 429
            span.tags.get('http.method') == 'GET'
            span.tags.get('error') == 'retry later'
            span.operationName == 'GET /traced/quota-error'
            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags.get('http.path') == '/traced/quota-error'
            serverSpan.tags.get('http.status_code') == 429
            serverSpan.tags.get('http.method') == 'GET'
            serverSpan.tags.get('error') == 'Too Many Requests'
            serverSpan.operationName == 'GET /traced/quota-error'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "test delayed http trace error"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/delayed-error/2s', String)

        then:
        HttpClientResponseException e = thrown()
        HttpResponse<?> response = e.response
        response
        conditions.eventually {
            reporter.spans.size() == 2
            JaegerSpan span = reporter.spans.find { it.tags.containsKey('http.client') }
            span.tags.get('http.path') == '/traced/delayed-error/2s'
            span.tags.get('http.status_code') == 500
            span.tags.get('http.method') == 'GET'
            span.tags.get('error') == 'Internal Server Error'
            span.operationName == 'GET /traced/delayed-error/2s'
            JaegerSpan serverSpan = reporter.spans.find { it.tags.containsKey('http.server') }
            serverSpan.tags.get('http.path') == '/traced/delayed-error/2s'
            serverSpan.tags.get('http.status_code') == 500
            serverSpan.tags.get('http.method') == 'GET'
            serverSpan.tags.get('error') == 'Internal Server Error'
            serverSpan.duration > TimeUnit.SECONDS.toMicros(2L)
            serverSpan.operationName == 'GET /traced/delayed-error/{duration}'
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "tested continue http tracing"() {
        given:
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
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested continue http tracing - blocking controller method"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/blocking/continued/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/hello/{name}' &&
                        it.tags.get('foo') == 'bar' &&
                        it.tags.get('http.path') == '/traced/blocking/hello/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/hello/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/blocking/hello/John' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/continued/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/blocking/continued/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/continued/John' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/blocking/continued/John' &&
                        it.tags.get('http.client')
            } != null
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested continue http tracing - reactive"() {
        given:
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
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested nested http tracing"() {
        given:
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
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested nested http tracing - blocking controller method"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/blocking/nested/John', String)

        then:
        response
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/hello/{name}' &&
                        it.tags.get('foo') == 'bar' &&
                        it.tags.get('http.path') == '/traced/blocking/hello/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/hello/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/blocking/hello/John' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nested/{name}' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/blocking/nested/John' &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nested/John' &&
                        !it.tags.get('foo') &&
                        it.tags.get('http.path') == '/traced/blocking/nested/John' &&
                        it.tags.get('http.client')
            } != null
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested nested http error tracing"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/nestedError/John', String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex != null
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/error/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags.get('http.path') == '/traced/error/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('http.server')

            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/error/{name}' &&
                        it.tags.get('http.path') == '/traced/error/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('error') == 'Internal Server Error' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/nestedError/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags.get('http.path') == '/traced/nestedError/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/nestedError/John' &&
                        it.tags.get('http.path') == '/traced/nestedError/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('error') &&
                        it.tags.get('http.client')
            } != null
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "tested nested http error tracing - blocking controller method"() {
        given:
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/blocking/nestedError/John', String)

        then:
        HttpClientResponseException ex = thrown()
        ex != null
        conditions.eventually {
            reporter.spans.size() == 4
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/error/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags.get('http.path') == '/traced/blocking/error/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('http.server')

            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/error/{name}' &&
                        it.tags.get('http.path') == '/traced/blocking/error/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('error') == 'Internal Server Error' &&
                        it.tags.get('http.client')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nestedError/{name}' &&
                        it.tags.containsKey('error') &&
                        it.tags.get('http.path') == '/traced/blocking/nestedError/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('http.server')
            } != null
            reporter.spans.find {
                it.operationName == 'GET /traced/blocking/nestedError/John' &&
                        it.tags.get('http.path') == '/traced/blocking/nestedError/John' &&
                        it.tags.get('http.status_code') == 500 &&
                        it.tags.get('error') &&
                        it.tags.get('http.client')
            } != null
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }
    }

    void "tested continue nested http tracing - reactive"() {
        given:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nestedReactive/John', String)

        then:
        response.body() == "10"

        and: 'all spans are finished'
        conditions.eventually {
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested customising span name"() {
        given:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/customised/name', String)

        then:
        conditions.eventually {
            reporter.spans.any { it.operationName == "custom name" }
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    void "tested customising span name - blocking controller method"() {
        given:
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        InMemoryReporter reporter = context.getBean(InMemoryReporter)
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        PollingConditions conditions = new PollingConditions()

        when:
        client.toBlocking().exchange('/traced/blocking/customised/name', String)

        then:
        conditions.eventually {
            reporter.spans.any { it.operationName == "custom name" }
            nrOfStartedSpans > 0
            nrOfFinishedSpans == nrOfStartedSpans
        }

        cleanup:
        client.close()
    }

    ApplicationContext buildContext() {
        def reporter = new InMemoryReporter()
        def metricsFactory = new InMemoryMetricsFactory()
        ApplicationContext.builder(
                'tracing.jaeger.enabled': true,
                'tracing.jaeger.sampler.probability': 1
        ).singletons(reporter, metricsFactory)
                .start()
    }

    long getJaegerMetric(String name, Map tags = [:]) {
        context.getBean(InMemoryMetricsFactory).getCounter("jaeger_tracer_${name}", tags)
    }

    long getNrOfFinishedSpans() {
        getJaegerMetric('finished_spans')
    }

    long getNrOfStartedSpans() {
        getJaegerMetric('started_spans', ['sampled': 'y'])
    }

    @Controller('/traced')
    static class TracedController {
        @Inject
        Tracer spanCustomizer
        @Inject
        TracedClient tracedClient

        @Get("/hello/{name}")
        String hello(String name) {
            spanCustomizer.activeSpan().setTag("foo", "bar")
            return name
        }

        @Get("/blocking/hello/{name}")
        @ExecuteOn(TaskExecutors.IO)
        String blockingHello(String name) {
            spanCustomizer.activeSpan().setTag("foo", "bar")
            return name
        }

        @Get("/response-reactive/{name}")
        HttpResponse<Publisher<String>> responseRx(String name) {
            return HttpResponse.ok(Publishers.map(Mono.fromCallable({ ->
                spanCustomizer.activeSpan().setTag("foo", "bar")
                return name
            }), { String n -> n }))
        }

        @Get("/reactive/{name}")
        @SingleResult
        Publisher<String> reactive(String name) {
            Mono.fromCallable({ ->
                spanCustomizer.activeSpan().setTag("foo", "bar")
                return name
            }).subscribeOn(Schedulers.boundedElastic())
        }

        @Get("/error/{name}")
        String error(String name) {
            throw new RuntimeException("bad")
        }

        @Get("/blocking/error/{name}")
        @ExecuteOn(TaskExecutors.IO)
        String blockingError(String name) {
            throw new RuntimeException("bad")
        }

        @Get("/reactiveError/{name}")
        @SingleResult
        Publisher<String> reactiveError(String name) {
            Mono.defer { Mono.just(error(name)) }
        }

        @Get("/nested/{name}")
        String nested(String name) {
            tracedClient.hello(name)
        }

        @Get("/blocking/nested/{name}")
        @ExecuteOn(TaskExecutors.IO)
        String blockingNested(String name) {
            tracedClient.blockingHello(name)
        }

        @ContinueSpan
        @Get("/continued/{name}")
        String continued(String name) {
            tracedClient.continued(name)
        }

        @ContinueSpan
        @Get("/blocking/continued/{name}")
        @ExecuteOn(TaskExecutors.IO)
        String blockingContinued(String name) {
            tracedClient.blockingContinued(name)
        }

        @ContinueSpan
        @Get("/continueRx/{name}")
        Publisher<String> continuedRx(String name) {
            tracedClient.continuedRx(name)
        }

        @Get("/nestedError/{name}")
        String nestedError(String name) {
            tracedClient.error(name)
        }

        @Get("/blocking/nestedError/{name}")
        @ExecuteOn(TaskExecutors.IO)
        String blockingNestedError(String name) {
            tracedClient.blockingError(name)
        }

        @Get("/customised/name")
        String customisedName() {
            spanCustomizer.activeSpan().setOperationName("custom name")
            "response"
        }

        @Get("/blocking/customised/name")
        @ExecuteOn(TaskExecutors.IO)
        String blockingCustomisedName() {
            spanCustomizer.activeSpan().setOperationName("custom name")
            "response"
        }

        @Get("/nestedReactive/{name}")
        @SingleResult
        Publisher<String> nestedReactive(String name) {
            spanCustomizer.activeSpan().setBaggageItem("foo", "bar")
            Flux.from(tracedClient.continuedRx(name))
                    .flatMap({ String res ->
                        assert spanCustomizer.activeSpan().getBaggageItem("foo") == "bar"
                        return tracedClient.nestedReactive2(res)
                    })
        }

        @Get("/nestedReactive2/{name}")
        @SingleResult
        Publisher<Integer> nestedReactive2(String name) {
            assert spanCustomizer.activeSpan().getBaggageItem("foo") == "bar"
            return Mono.just(10)
        }

        @Get("/quota-error")
        @SingleResult
        Publisher<String> quotaError() {
            Mono.error(new QuotaException("retry later"))
        }

        @Get("/delayed-error/{duration}")
        @SingleResult
        Publisher<Object> delayedError(Duration duration) {
            Mono.error(new RuntimeException("delayed error"))
                    .delaySubscription(Duration.of(duration.toMillis(), ChronoUnit.MILLIS))
        }

        @Error(QuotaException)
        HttpResponse<?> handleQuotaError(QuotaException ex) {
            HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS, ex.message)
        }
    }

    static class QuotaException extends RuntimeException {

        QuotaException(String message) {
            super(message)
        }
    }

    @Client('/traced')
    static interface TracedClient {
        @Get("/hello/{name}")
        String hello(String name)

        @Get("/blocking/hello/{name}")
        String blockingHello(String name)

        @Get("/error/{name}")
        String error(String name)

        @Get("/blocking/error/{name}")
        String blockingError(String name)

        @Get("/hello/{name}")
        String continued(String name)

        @Get("/blocking/hello/{name}")
        String blockingContinued(String name)

        @Get("/hello/{name}")
        @SingleResult
        Publisher<String> continuedRx(String name)

        @Get("/nestedReactive2/{name}")
        @SingleResult
        Publisher<String> nestedReactive2(String name)
    }
}
