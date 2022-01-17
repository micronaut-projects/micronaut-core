/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.async.annotation.SingleResult
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
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class HttpTracingExclusionSpec extends Specification {

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
        reporter.spans.empty
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
        reporter.spans.empty
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
            reporter.spans.size() == 2
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
            reporter.spans.size() == 2
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
            reporter.spans.size() == 2
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
            reporter.spans.size() == 2
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
            reporter.spans.size() == 2
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
                'tracing.jaeger.sampler.probability': 1,
                'tracing.exclusions[0]': '.*hello.*'
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

}
