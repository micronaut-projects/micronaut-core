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
package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.reactivex.Single
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class TraceInterceptorSpec extends Specification {

    void "test trace interceptor"() {
        given:
        ApplicationContext applicationContext = buildContext()
        TracedService tracedService = applicationContext.getBean(TracedService)
        TestReporter reporter = applicationContext.getBean(TestReporter)

        when:
        String result = tracedService.methodOne("test")

        then:
        result == "test"
        reporter.spans.size() == 2
        reporter.spans[0].name() == 'trace-rx'
        reporter.spans[0].tags().get("more.stuff") == 'test'
        reporter.spans[0].tags().get("class") == 'TracedService'
        reporter.spans[0].tags().get("method") == 'methodThree'
        reporter.spans[1].name() == 'my-trace'
        reporter.spans[1].tags().get('foo.bar') == 'test'
    }

    ApplicationContext buildContext() {
        ApplicationContext context = ApplicationContext.build()
        context.environment.addPropertySource(PropertySource.of('tracing.zipkin.enabled':true, 'tracing.zipkin.samplerProbability':1))
        def reporter = new TestReporter()
        context.registerSingleton(reporter)
        context.start()
    }
    @Singleton
    static class TracedService {

        @NewSpan("my-trace")
        String methodOne(@SpanTag("foo.bar") String name) {
            methodTwo(name)
        }

        @ContinueSpan
        String methodTwo(@SpanTag("foo.baz") String another) {
            methodThree(another).blockingGet()
        }

        @NewSpan("trace-rx")
        Single<String> methodThree(@SpanTag("more.stuff") String name) {
            return Single.just(name)
        }

    }
}
