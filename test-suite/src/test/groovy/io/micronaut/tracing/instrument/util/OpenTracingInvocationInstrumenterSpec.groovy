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
package io.micronaut.tracing.instrument.util

import io.micronaut.context.ApplicationContext
import io.opentracing.Tracer
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.Specification

/**
 * Regression test: when executing certain reactive operations the ScopeManager's internal state can become permanently
 * corrupted resulting in incorrect tracing.
 *
 * @author lgathy
 */
class OpenTracingInvocationInstrumenterSpec extends Specification {

    void "test regression of corrupted ScopeManager state"() {
        given: 'Jaeger tracer is enabled'
        ApplicationContext context = ApplicationContext.run(
            'tracing.jaeger.enabled': true
        )
        Tracer tracer = context.getBean(Tracer)
        String[] words = ['one', 'two', 'three']

        expect: 'no active span'
        tracer.activeSpan() == null

        when: 'reactive operations are executed inside a span'
        def rootSpan = tracer.buildSpan('root').start()
        def scope = tracer.activateSpan(rootSpan)
        def combined = Flowable
            .merge(words.collect { Single.just(it).toFlowable() })
            .reduce { a, b -> "$a, $b" }
            .toSingle()
            .blockingGet()
        scope.close()
        rootSpan.finish()

        then: 'there should be no active span after it was finished'
        combined.split(", ").sort() == words.sort()
        tracer.activeSpan() == null
    }
}
