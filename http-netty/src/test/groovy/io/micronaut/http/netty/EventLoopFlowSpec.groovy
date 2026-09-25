package io.micronaut.http.netty

import org.jspecify.annotations.NonNull
import io.netty.util.concurrent.AbstractEventExecutor
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.OrderedEventExecutor
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class EventLoopFlowSpec extends Specification {
    def 'outside simple'() {
        given:
        def mock = new MockEventExecutor()
        def serializer = new EventLoopFlow(mock)
        boolean run = false

        when:
        def now = serializer.executeNow { run = true }
        then:
        !now
        !run
        mock.submitted.size() == 1

        when:
        mock.submitted[0].run()
        then:
        run
    }

    def 'inside simple'() {
        given:
        def mock = new MockEventExecutor()
        def serializer = new EventLoopFlow(mock)
        boolean run = false

        when:
        mock.inEventLoop = true
        def now = serializer.executeNow { run = true }
        then:
        now
        !run
        mock.submitted.isEmpty()
    }

    def 'serialize on inside call after outside call'() {
        given:
        def mock = new MockEventExecutor()
        def serializer = new EventLoopFlow(mock)
        boolean run1 = false
        boolean run2 = false

        when:
        def now1 = serializer.executeNow { run1 = true }
        then:
        !now1
        !run1

        when:
        mock.inEventLoop = true
        def now2 = serializer.executeNow { run2 = true }
        then:
        !now2
        !run2
        mock.submitted.size() == 2

        when:
        mock.submitted[0].run()
        then:
        run1
        !run2

        when:
        mock.submitted[1].run()
        then:
        run2

        when:
        def now3 = serializer.executeNow {}
        then:
        now3
        mock.submitted.size() == 2
    }

    def 'tryRunNow fast path on the event loop does not submit anything'() {
        given:
        def mock = new MockEventExecutor()
        def flow = new EventLoopFlow(mock)
        mock.inEventLoop = true

        expect:
        flow.tryRunNow()
        flow.tryRunNow()
        flow.tryRunNow()
        mock.submitted.isEmpty()
    }

    def 'tryRunNow outside the event loop defers'() {
        given:
        def mock = new MockEventExecutor()
        def flow = new EventLoopFlow(mock)
        List<String> order = []

        when:
        def now = flow.tryRunNow()
        then:
        !now
        mock.submitted.isEmpty()

        when:
        flow.submit { order << 'a' }
        then:
        mock.submitted.size() == 1
        order.isEmpty()

        when:
        mock.submitted[0].run()
        then:
        order == ['a']
    }

    def 'inline step does not overtake pending deferred steps'() {
        given:
        def mock = new MockEventExecutor()
        def flow = new EventLoopFlow(mock)
        List<String> order = []

        def step = { String name ->
            if (flow.tryRunNow()) {
                order << name
            } else {
                flow.submit { order << name }
            }
        }

        when: 'a step arrives from outside the loop'
        step('a')
        then:
        order.isEmpty()
        mock.submitted.size() == 1

        when: 'two steps arrive on the loop while the first is still pending'
        mock.inEventLoop = true
        step('b')
        step('c')
        then: 'they must defer as well'
        order.isEmpty()
        mock.submitted.size() == 3

        when:
        mock.submitted[0].run()
        then:
        order == ['a']

        when: 'a step arrives on the loop while one deferred step is still pending'
        step('d')
        then:
        order == ['a']
        mock.submitted.size() == 4

        when:
        mock.submitted[1].run()
        mock.submitted[2].run()
        mock.submitted[3].run()
        then:
        order == ['a', 'b', 'c', 'd']

        when: 'all deferred work is done, the fast path is available again'
        step('e')
        then:
        order == ['a', 'b', 'c', 'd', 'e']
        mock.submitted.size() == 4
    }

    def 'deferred steps must run in submission order'() {
        given:
        def mock = new MockEventExecutor()
        def flow = new EventLoopFlow(mock)

        when:
        flow.submit {}
        flow.submit {}
        mock.submitted[1].run()
        then:
        thrown IllegalStateException
    }

    private static final class MockEventExecutor extends AbstractEventExecutor implements OrderedEventExecutor {
        boolean inEventLoop = false
        List<Runnable> submitted = []

        @Override
        boolean isShuttingDown() {
            return false
        }

        @Override
        Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return null
        }

        @Override
        Future<?> terminationFuture() {
            return null
        }

        @Override
        void shutdown() {

        }

        @Override
        boolean isShutdown() {
            return false
        }

        @Override
        boolean isTerminated() {
            return false
        }

        @Override
        boolean awaitTermination(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            return false
        }

        @Override
        boolean inEventLoop(Thread thread) {
            return inEventLoop
        }

        @Override
        void execute(@NonNull Runnable command) {
            submitted.add(command)
        }
    }
}
