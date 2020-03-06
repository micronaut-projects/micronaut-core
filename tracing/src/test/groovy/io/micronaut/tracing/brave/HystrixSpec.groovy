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
import io.micronaut.configuration.hystrix.annotation.HystrixCommand
import io.micronaut.context.ApplicationContext
import io.micronaut.tracing.annotation.NewSpan
import io.reactivex.Single
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author graemerocher
 * @since 1.0
 */
class HystrixSpec extends Specification {

    void "test instrument hystrix commands"() {
        given:
        ApplicationContext applicationContext = buildContext()
        TestReporter reporter = applicationContext.getBean(TestReporter)
        TracedHystrixService service = applicationContext.getBean(TracedHystrixService)

//        when:
//        String result = service.hello("Fred")
//
//        then:
//        result == "Hello Fred"
//        reporter.spans.size() == 1
//        reporter.spans[0].tags().get("foo") == "bar"
//        reporter.spans[0].name() == 'my-hystrix-command'

        when:
        reporter.spans.clear()
        def observable = service.helloRx("Fred")
        String result = observable.blockingGet()

        then:
        result == "Hello Fred"
        reporter.spans.size() == 1
        reporter.spans[0].tags().get("foo") == "bar"
        reporter.spans[0].name() == 'my-other-command'

        cleanup:
        applicationContext.close()
    }

    static class TracedHystrixService {
        @Inject SpanCustomizer spanCustomizer

        @HystrixCommand("my-hystrix-command")
        @NewSpan
        String hello(String name) {
            spanCustomizer.tag("foo", "bar")
            return "Hello $name"
        }

        @HystrixCommand("my-other-command")
        @NewSpan
        Single<String> helloRx(String name) {
            return Single.fromCallable({
                spanCustomizer.tag("foo", "bar")
                "Hello $name"
            })
        }
    }

    ApplicationContext buildContext() {
        def reporter = new TestReporter()
        ApplicationContext.builder(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1
        )
        .singletons(reporter)
        .start()
    }
}
