package io.micronaut.http.server.netty.body

import spock.lang.Specification

class UpstreamBalancerSpec extends Specification {
    def 'slowest'(List<Integer> steps, int expect) {
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

            @Override
            void discard() {
            }
        })
        def left = pair.left()
        def right = pair.right()

        when:
        for (Integer step : steps) {
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
        // todo: saturate tests
    }

    def 'fastest'(List<Integer> steps, int expect) {
        given:
        long combined = 0
        def pair = UpstreamBalancer.fastest(new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
                assert bytesConsumed > 0
                combined += bytesConsumed
            }

            @Override
            void discard() {
            }
        })
        def left = pair.left()
        def right = pair.right()

        when:
        for (Integer step : steps) {
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
    }
}
