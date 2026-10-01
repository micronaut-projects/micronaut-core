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
package io.micronaut.inject.parallel

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.TimeUnit

class ParallelBeanSpec extends Specification {

    def setup() {
        // the fixtures coordinate through static latches, so restore them rather than rely on
        // this spec running at most once per JVM
        BlockingParallelCondition.reset()
        EachParallelBean.reset()
        FailingParallelBean.reset()
        HangingParallelBean.reset()
        LateFailingParallelBean.reset()
        SlowShutdownParallelBean.reset()
        ThrowingParallelCondition.reset()
        TolerantFailingParallelBean.reset()
    }

    void "test initialize bean in parallel"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.bean.enabled':true)
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        expect:
        conditions.eventually {
            ctx.getActiveBeanRegistrations(ParallelBean).size() == 1
        }

        cleanup:
        ctx.close()
    }

    void "test parallel bean still in construction when the context is closed is destroyed"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.shutdown.bean.enabled': true)

        expect: "the bean construction has started on a parallel thread"
        SlowShutdownParallelBean.CONSTRUCTING.await(10, TimeUnit.SECONDS)

        when: "the context is closed, releasing the construction only once shutdown has started"
        ctx.close()

        then: "the bean that registered during shutdown still had its @PreDestroy called"
        SlowShutdownParallelBean.DESTROYED.await(10, TimeUnit.SECONDS)
    }

    void "test the parallel bean discovery thread does not outlive the context"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.blocking.condition.enabled': true)

        expect: "condition evaluation is blocking the discovery thread"
        BlockingParallelCondition.EVALUATING.await(10, TimeUnit.SECONDS)

        when:
        Thread discoveryThread = findDiscoveryThread()

        then:
        discoveryThread != null

        when:
        ctx.close()

        then: "close interrupted and joined the discovery thread"
        !discoveryThread.isAlive()
        findDiscoveryThread() == null
    }

    void "test a failing parallel bean stops the context from the parallel worker"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.failing.bean.enabled': true)
        PollingConditions conditions = new PollingConditions(timeout: 10, delay: 0.1)

        expect: "the construction that is going to fail has started"
        FailingParallelBean.CONSTRUCTING.await(10, TimeUnit.SECONDS)

        and: "shutdownOnError defaults to true, so the failure stops the context"
        conditions.eventually {
            !ctx.isRunning()
        }

        cleanup:
        ctx.close()
    }

    void "test a failing parallel bean with shutdownOnError false leaves the context running"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.tolerant.bean.enabled': true)

        expect: "the construction that is going to fail has started"
        TolerantFailingParallelBean.CONSTRUCTING.await(10, TimeUnit.SECONDS)

        and: "the context is still running once the failure has been handled"
        new PollingConditions(timeout: 3, delay: 0.5).eventually {
            ctx.isRunning()
            ctx.getActiveBeanRegistrations(TolerantFailingParallelBean).isEmpty()
        }

        cleanup:
        ctx.close()
    }

    void "test an iterable parallel bean initializes every candidate in parallel"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'parallel.each.one.name': 'one',
                'parallel.each.two.name': 'two'
        )
        PollingConditions conditions = new PollingConditions(timeout: 10, delay: 0.1)

        expect:
        conditions.eventually {
            EachParallelBean.CONSTRUCTED.containsAll(['one', 'two'])
            ctx.getActiveBeanRegistrations(EachParallelBean).size() == 2
        }

        cleanup:
        ctx.close()
    }

    void "test a failure while discovering parallel beans does not vanish with the discovery thread"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.throwing.condition.enabled': true)
        PollingConditions conditions = new PollingConditions(timeout: 10, delay: 0.1)

        expect: "the condition that is going to fail has been evaluated on the discovery thread"
        ThrowingParallelCondition.EVALUATED.await(10, TimeUnit.SECONDS)

        and: "the failure is confined to the discovery thread, which does not outlive it"
        conditions.eventually {
            findDiscoveryThread() == null
        }

        and: "the context is unaffected"
        ctx.isRunning()

        cleanup:
        ctx.close()
    }

    void "test a parallel bean that fails after shutdown began does not re-enter the shutdown"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.late.failure.enabled': true)

        expect: "the bean construction has started on a parallel thread"
        LateFailingParallelBean.CONSTRUCTING.await(10, TimeUnit.SECONDS)

        when: "the context is closed, releasing the construction only once shutdown has started"
        ctx.close()

        then: "the construction failed after the shutdown was already under way, and close still returned"
        LateFailingParallelBean.FAILED.await(10, TimeUnit.SECONDS)
        !ctx.isRunning()
    }

    void "test a parallel bean that never finishes constructing does not block the shutdown forever"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.hanging.bean.enabled': true)

        expect: "the construction that never finishes has started on a parallel thread"
        HangingParallelBean.CONSTRUCTING.await(10, TimeUnit.SECONDS)

        when: "the context is closed while that initialization is still in flight"
        long start = System.nanoTime()
        ctx.close()
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        then: "close waited for the initialization, gave up on it and completed the shutdown"
        elapsedMillis >= 10_000
        !ctx.isRunning()

        cleanup:
        HangingParallelBean.RELEASE.countDown()
    }

    private static Thread findDiscoveryThread() {
        Thread.getAllStackTraces().keySet().find {
            it.isAlive() && it.name == BlockingParallelCondition.DISCOVERY_THREAD_NAME
        }
    }
}
