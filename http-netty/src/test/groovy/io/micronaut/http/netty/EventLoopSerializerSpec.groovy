package io.micronaut.http.netty

import io.micronaut.core.annotation.NonNull
import io.netty.util.concurrent.AbstractEventExecutor
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.OrderedEventExecutor
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class EventLoopSerializerSpec extends Specification {
    def 'outside simple'() {
        given:
        def mock = new MockEventExecutor()
        def serializer = new EventLoopSerializer(mock)
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
        def serializer = new EventLoopSerializer(mock)
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
        def serializer = new EventLoopSerializer(mock)
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

    private static class MockEventExecutor extends AbstractEventExecutor implements OrderedEventExecutor {
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
