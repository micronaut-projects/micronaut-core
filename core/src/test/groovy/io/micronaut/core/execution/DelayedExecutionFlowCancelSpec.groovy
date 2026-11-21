package io.micronaut.core.execution

import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DelayedExecutionFlowCancelSpec extends Specification {

    def 'a hook registered before cancel runs once on cancel'() {
        given:
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
        AtomicInteger runs = new AtomicInteger()
        flow.onCancel(runs::incrementAndGet)

        expect:
        runs.get() == 0
        !flow.cancelled

        when:
        flow.cancel()

        then:
        runs.get() == 1
        flow.cancelled

        when:
        flow.cancel()

        then:"a second cancel does not run the hook again"
        runs.get() == 1
    }

    def 'a hook registered after cancel runs at once'() {
        given:
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
        flow.cancel()
        AtomicInteger runs = new AtomicInteger()

        when:
        flow.onCancel(runs::incrementAndGet)

        then:
        runs.get() == 1
        flow.cancelled

        when:
        flow.cancel()

        then:
        runs.get() == 1
    }

    def 'a cancel that overlaps another one returns while the first discards the value'() {
        given: 'a completed flow with a hook'
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
        flow.complete('value')
        AtomicInteger runs = new AtomicInteger()
        flow.onCancel(runs::incrementAndGet)
        CountDownLatch discarding = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        List<String> discarded = Collections.synchronizedList([])
        ExecutorService executor = Executors.newSingleThreadExecutor()

        when: 'a second cancel runs while the first is inside its discard callback'
        Future<?> first = executor.submit {
            flow.cancel { String v ->
                discarded.add(v)
                discarding.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        assert discarding.await(10, TimeUnit.SECONDS)
        flow.cancel()
        release.countDown()
        first.get(10, TimeUnit.SECONDS)

        then: 'neither throws, the value is discarded once and the hook runs once'
        noExceptionThrown()
        discarded == ['value']
        runs.get() == 1
        flow.cancelled

        cleanup:
        release.countDown()
        executor.shutdownNow()
    }

    def 'every registered hook runs in registration order'() {
        given:
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
        List<String> runs = []
        flow.onCancel { runs.add('first') }
        flow.onCancel { runs.add('second') }
        flow.onCancel { runs.add('third') }

        when:
        flow.cancel()

        then:"a later registration adds a hook rather than replacing the earlier ones"
        runs == ['first', 'second', 'third']

        when:
        flow.onCancel { runs.add('late') }

        then:
        runs == ['first', 'second', 'third', 'late']
    }

    def 'a hook is not run when the flow completes without cancellation'() {
        given:
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
        AtomicInteger runs = new AtomicInteger()
        flow.onCancel(runs::incrementAndGet)
        String result = null
        flow.onComplete((v, t) -> result = v)

        when:
        flow.complete('foo')

        then:
        result == 'foo'
        runs.get() == 0
        !flow.cancelled
    }

    def 'a hook registered concurrently with cancel runs exactly once'() {
        given:
        ExecutorService executor = Executors.newFixedThreadPool(2)
        int iterations = 5000

        when:
        List<Integer> wrong = []
        for (int i = 0; i < iterations; i++) {
            DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
            AtomicInteger early = new AtomicInteger()
            AtomicInteger raced = new AtomicInteger()
            flow.onCancel(early::incrementAndGet)
            CyclicBarrier barrier = new CyclicBarrier(2)
            Future<?> register = executor.submit {
                barrier.await()
                flow.onCancel(raced::incrementAndGet)
            }
            Future<?> cancel = executor.submit {
                barrier.await()
                flow.cancel()
            }
            register.get(10, TimeUnit.SECONDS)
            cancel.get(10, TimeUnit.SECONDS)
            if (early.get() != 1 || raced.get() != 1 || !flow.cancelled) {
                wrong.add(i)
            }
        }

        then:
        wrong.isEmpty()

        cleanup:
        executor.shutdownNow()
    }

    def 'hooks registered concurrently from several threads all run exactly once'() {
        given:
        int threads = 4
        ExecutorService executor = Executors.newFixedThreadPool(threads + 1)
        int iterations = 1000

        when:
        List<Integer> wrong = []
        for (int i = 0; i < iterations; i++) {
            DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create()
            List<AtomicInteger> runs = (0..<threads).collect { new AtomicInteger() }
            CountDownLatch start = new CountDownLatch(1)
            List<Future<?>> futures = []
            runs.each { AtomicInteger counter ->
                futures.add(executor.submit {
                    start.await()
                    flow.onCancel(counter::incrementAndGet)
                })
            }
            futures.add(executor.submit {
                start.await()
                flow.cancel()
            })
            start.countDown()
            futures.each { it.get(10, TimeUnit.SECONDS) }
            if (runs.any { it.get() != 1 }) {
                wrong.add(i)
            }
        }

        then:
        wrong.isEmpty()

        cleanup:
        executor.shutdownNow()
    }
}
