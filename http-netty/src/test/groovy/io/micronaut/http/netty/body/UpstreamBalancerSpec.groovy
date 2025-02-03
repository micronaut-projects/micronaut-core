package io.micronaut.http.netty.body

import io.micronaut.http.body.ByteBody.SplitBackpressureMode
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.body.stream.UpstreamBalancer
import spock.lang.Specification

class UpstreamBalancerSpec extends Specification {
    def 'slowest'(List<Long> steps, int expect) {
        given:
        long combined = 0
        def pair = UpstreamBalancer.slowest(new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
                assert bytesConsumed > 0
                combined += bytesConsumed
                // clamp
                if (combined < 0) {
                    combined = Long.MAX_VALUE;
                }
            }
        })
        def left = pair.left()
        def right = pair.right()

        when:
        for (Long step : steps) {
            if (step < 0) {
                left.onBytesConsumed(-step)
            } else {
                right.onBytesConsumed(step)
            }
        }
        then:
        combined == expect

        where:
        steps    | expect
        [-1]     | 0
        [1]      | 0
        [1, 1]   | 0
        [-1, -1] | 0
        [-1, 1]  | 1
        [1, -1]  | 1
        [2, -1]  | 1
        [-1, 2]  | 1
        [Long.MAX_VALUE, Long.MAX_VALUE, -1]  | 1
        [-Long.MAX_VALUE, -Long.MAX_VALUE, 1] | 1
    }

    def 'fastest'(List<Long> steps, long expect) {
        given:
        long combined = 0
        def pair = UpstreamBalancer.fastest(new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
                assert bytesConsumed > 0
                combined += bytesConsumed
                // clamp
                if (combined < 0) {
                    combined = Long.MAX_VALUE;
                }
            }
        })
        def left = pair.left()
        def right = pair.right()

        when:
        for (Long step : steps) {
            if (step < 0) {
                left.onBytesConsumed(-step)
            } else {
                right.onBytesConsumed(step)
            }
        }
        then:
        combined == expect

        where:
        steps    | expect
        [-1]     | 1
        [1]      | 1
        [1, 1]   | 2
        [-1, -1] | 2
        [-1, 1]  | 1
        [1, -1]  | 1
        [2, -1]  | 2
        [-1, 2]  | 2
        [Long.MAX_VALUE, Long.MAX_VALUE]   | Long.MAX_VALUE
        [-Long.MAX_VALUE, -Long.MAX_VALUE] | Long.MAX_VALUE
    }

    def 'allowDiscard combined'(SplitBackpressureMode mode) {
        given:
        boolean calledUpstream = false
        def upstream = new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            void allowDiscard() {
                calledUpstream = true
            }
        }

        when:
        def pairA = UpstreamBalancer.balancer(upstream, mode)
        pairA.left().allowDiscard()
        then:
        !calledUpstream
        when:
        pairA.right().allowDiscard()
        then:
        calledUpstream

        // now try the other order
        when:
        calledUpstream = false
        def pairB = UpstreamBalancer.balancer(upstream, mode)
        pairB.right().allowDiscard()
        then:
        !calledUpstream
        when:
        pairB.left().allowDiscard()
        then:
        calledUpstream

        where:
        mode << SplitBackpressureMode.values()
    }

    def 'disregardBackpressure combined'(SplitBackpressureMode mode) {
        given:
        boolean calledUpstream = false
        def upstream = new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            void disregardBackpressure() {
                calledUpstream = true
            }
        }

        when:
        def pairA = UpstreamBalancer.balancer(upstream, mode)
        pairA.left().disregardBackpressure()
        then:
        !calledUpstream
        when:
        pairA.right().disregardBackpressure()
        then:
        calledUpstream

        // now try the other order
        when:
        calledUpstream = false
        def pairB = UpstreamBalancer.balancer(upstream, mode)
        pairB.right().disregardBackpressure()
        then:
        !calledUpstream
        when:
        pairB.left().disregardBackpressure()
        then:
        calledUpstream

        where:
        mode << SplitBackpressureMode.values()
    }

    def 'slowest disregard does not hold back other side'() {
        given:
        long demand = 0
        def pair = UpstreamBalancer.slowest(n -> demand += n)

        when:
        pair.left().onBytesConsumed(1)
        then:
        demand == 0

        when:
        pair.right().disregardBackpressure()
        then:
        demand == 1

        when:
        pair.left().onBytesConsumed(1)
        then:
        demand == 2
    }

    def 'fastest disregard does not exceed other side'() {
        given:
        long demand = 0
        def pair = UpstreamBalancer.fastest(n -> demand += n)

        when:
        pair.left().onBytesConsumed(1)
        then:
        demand == 1

        when:
        pair.left().disregardBackpressure()
        then:
        demand == 1

        when:
        pair.right().onBytesConsumed(1)
        then:
        demand == 1

        when:
        pair.right().onBytesConsumed(1)
        then:
        demand == 2
    }

    def 'first disregard uses other side 1'() {
        given:
        long demand = 0
        def pair = UpstreamBalancer.first(n -> demand += n)

        when:
        pair.left().onBytesConsumed(1)
        then:
        demand == 1

        when:
        pair.left().disregardBackpressure()
        then:
        demand == 1

        when:
        pair.right().onBytesConsumed(1)
        then:
        demand == 1

        when:
        pair.right().onBytesConsumed(1)
        then:
        demand == 2
    }

    def 'first disregard uses other side 2'() {
        given:
        long demand = 0
        def pair = UpstreamBalancer.first(n -> demand += n)

        when:
        pair.right().onBytesConsumed(1)
        then:
        demand == 0

        when:
        pair.left().disregardBackpressure()
        then:
        demand == 1
    }
}
