/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.concurrency

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import static java.util.concurrent.TimeUnit.SECONDS

/**
 * Three threads ask for the same singleton: A is creating it, B is already blocked on the creation lock A holds,
 * and C arrives only once A is done. The singleton must be constructed by exactly one of B and C whatever
 * A's creation did.
 */
class JavaConcurrentFailedSingletonCreationSpec extends Specification {

    private static final long TIMEOUT_MS = 10_000

    void "a singleton is created once after a concurrent creation of it failed"() {
        given:
        SteppedSingleton.reset(true)
        ApplicationContext context = ApplicationContext.run()
        Map<String, Object> results = new ConcurrentHashMap<>()

        when: "A starts creating the singleton and stops in its constructor"
        Thread a = getBeanOn("A", context, results)

        then:
        SteppedSingleton.started[1].await(TIMEOUT_MS, SECONDS)

        when: "B is blocked on the creation lock that A holds"
        Thread b = getBeanOn("B", context, results)
        int lockWaitedOnByB = awaitBlockedOnMonitorOf(b, a)

        and: "A's construction fails"
        SteppedSingleton.released[1].countDown()
        a.join(TIMEOUT_MS)

        then:
        results.A instanceof BeanInstantiationException

        when: "B goes on to construct the singleton and C arrives after the failed creation"
        SteppedSingleton.started[2].await(TIMEOUT_MS, SECONDS)
        Thread c = getBeanOn("C", context, results)
        int lockWaitedOnByC = awaitBlockedOnMonitorOf(c, b, SteppedSingleton.started[3])

        then: "C waits on the same lock as B did rather than constructing a second instance"
        SteppedSingleton.CONSTRUCTIONS.get() == 2
        lockWaitedOnByC == lockWaitedOnByB

        when:
        SteppedSingleton.released[2].countDown()
        SteppedSingleton.released[3].countDown()
        b.join(TIMEOUT_MS)
        c.join(TIMEOUT_MS)

        then:
        results.B instanceof SteppedSingleton
        results.C.is(results.B)
        context.getBean(SteppedSingleton).is(results.B)
        SteppedSingleton.CONSTRUCTIONS.get() == 2

        cleanup:
        context.close()
    }

    void "a singleton is created once when the concurrent creation of it succeeded"() {
        given:
        SteppedSingleton.reset(false)
        ApplicationContext context = ApplicationContext.run()
        Map<String, Object> results = new ConcurrentHashMap<>()

        when: "A starts creating the singleton and stops in its constructor"
        Thread a = getBeanOn("A", context, results)

        then:
        SteppedSingleton.started[1].await(TIMEOUT_MS, SECONDS)

        when: "B is blocked on the creation lock that A holds, then A's construction completes"
        Thread b = getBeanOn("B", context, results)
        awaitBlockedOnMonitorOf(b, a)
        SteppedSingleton.released[1].countDown()
        a.join(TIMEOUT_MS)
        b.join(TIMEOUT_MS)

        and: "C arrives after the creation"
        Thread c = getBeanOn("C", context, results)
        c.join(TIMEOUT_MS)

        then:
        results.A instanceof SteppedSingleton
        results.B.is(results.A)
        results.C.is(results.A)
        context.getBean(SteppedSingleton).is(results.A)
        SteppedSingleton.CONSTRUCTIONS.get() == 1

        cleanup:
        context.close()
    }

    private static Thread getBeanOn(String name, ApplicationContext context, Map<String, Object> results) {
        Thread thread = new Thread({
            try {
                results[name] = context.getBean(SteppedSingleton)
            } catch (Throwable e) {
                results[name] = e
            }
        }, name)
        thread.start()
        return thread
    }

    /**
     * Waits until the thread is blocked on a plain {@link Object} monitor owned by the owner, or until the given
     * latch, if any, is counted down.
     *
     * @return The identity hash code of the monitor the thread is blocked on, or -1 if the latch was counted down first
     */
    private static int awaitBlockedOnMonitorOf(Thread thread, Thread owner, CountDownLatch orUntil = null) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (orUntil != null && orUntil.count == 0) {
                return -1
            }
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.id)
            if (info != null && info.lockOwnerId == owner.id && info.lockInfo?.className == Object.name) {
                return info.lockInfo.identityHashCode
            }
            Thread.sleep(5)
        }
        throw new IllegalStateException("$thread.name did not get blocked on a monitor of $owner.name")
    }
}
