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

    private static Thread findDiscoveryThread() {
        Thread.getAllStackTraces().keySet().find {
            it.isAlive() && it.name == BlockingParallelCondition.DISCOVERY_THREAD_NAME
        }
    }
}
