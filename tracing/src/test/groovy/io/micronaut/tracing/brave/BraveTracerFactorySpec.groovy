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

import brave.Tracing
import brave.http.HttpClientHandler
import brave.http.HttpServerHandler
import brave.http.HttpTracing
import io.micronaut.context.ApplicationContext
import io.micronaut.tracing.brave.sender.HttpClientSender
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracer
import spock.lang.Specification
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.Reporter

/**
 * @author graemerocher
 * @since 1.0
 */
class BraveTracerFactorySpec extends Specification {
    void "test brave tracer configuration no endpoint present"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"The tracer is obtained"
        Tracer tracer = context.getBean(Tracer)

        then:"It is present"
        tracer instanceof NoopTracer

        cleanup:
        context.close()
    }

    void "test brave tracer configuration"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.http.endpoint':HttpClientSender.Builder.DEFAULT_SERVER_URL
        )

        expect:"The tracer is obtained"
        context.getBean(AsyncReporter)
        context.getBean(Tracer)
        context.getBean(Tracing)
        context.getBean(HttpTracing)
        context.getBean(HttpClientHandler)
        context.getBean(HttpServerHandler)

        cleanup:
        context.close()
    }

    void "test brave tracer configuration no endpoint"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'tracing.zipkin.enabled':true
        )

        expect:"The tracer is obtained"
        !context.containsBean(Reporter)
        context.getBean(Tracer)
        context.getBean(Tracing)
        context.getBean(HttpTracing)
        context.getBean(HttpClientHandler)
        context.getBean(HttpServerHandler)

        cleanup:
        context.close()
    }

    void "test brace tracer report spans"() {

        given:
        def reporter = new TestReporter()
        ApplicationContext context = ApplicationContext.builder(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1
        ).singletons(reporter)
         .start()

        when:
        Tracer tracer = context.getBean(Tracer)
        def span = tracer.buildSpan("test").start()
        def scope = tracer.activateSpan(span)

        span.finish()
        scope.close()

        then:
        reporter.spans.size() == 1
        reporter.spans[0].name() == "test"

        cleanup:
        context.close()
    }



}
