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
package io.micronaut.reactive.rxjava2

import io.micronaut.context.ApplicationContext
import io.micronaut.scheduling.instrument.InvocationInstrumenter
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression test: in case of certain reactive constructs the same InvocationInstrumenter instance can be triggered
 * recursively, thus potentially potentially corrupting the instrumenter's internal state.
 *
 * @author lgathy
 */
class RxChainedInstrumentationSpec extends Specification {

    @AutoCleanup
    ApplicationContext beanContext

    void "test regression of chained invocation of instrumenter methods"() {
        given:
        beanContext = ApplicationContext.run()
        def tracker = beanContext.getBean(InstrumentationDepthTracker)
        def words = ['one', 'two', 'three']

        when:
        def combined = Flowable
            .<String> merge(words.collect { Single.just(it).toFlowable() })
            .reduce { a, b -> "$a, $b" }
            .toSingle()
            .blockingGet()

        then:
        combined.split(", ").toList().sort() == words.sort()
        tracker.maxDepth.get() == 1
    }

    @Singleton
    static class InstrumentationDepthTracker implements ReactiveInvocationInstrumenterFactory {

        private final AtomicInteger maxDepth = new AtomicInteger()

        @Override
        InvocationInstrumenter newReactiveInvocationInstrumenter() {
            new InvocationInstrumenter() {

                int depth = 0

                @Override
                void beforeInvocation() {
                    int old = depth
                    ++depth
                    maxDepth.compareAndSet(old, depth)
                }

                @Override
                void afterInvocation(boolean cleanup) {
                    --depth
                }
            }
        }
    }
}
