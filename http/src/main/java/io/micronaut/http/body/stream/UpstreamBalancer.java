/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.ByteBody;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * This class merges the backpressure of two data streams. The bytes signaled to the
 * {@link #upstream} is always the <i>minimum</i> of the consumed bytes of the two downstreams.
 * <p>
 * Implementation notes: This is a bit tricky to implement without locking due to the concurrent nature of
 * {@link BufferConsumer.Upstream}. Let {@code l} and {@code r} be the total bytes consumed by the left and
 * right downstreams respectively. We have signalled already the consumption of
 * {@code min(l, r)} bytes upstream. The {@link AtomicLong} stores the difference {@code l-r}.
 * Now, assume the left downstream (wlog) signals consumption of {@code n} further bytes. There
 * are three cases:
 *
 * <ul>
 *     <li>{@code l>r}, thus {@code l-r>0}: right downstream is already lagging behind, don't
 *     send any demand upstream</li>
 *     <li>{@code l<r}, and {@code l+n<r}: left downstream stays lagging behind, send the full
 *     {@code n} demand upstream</li>
 *     <li>{@code l<r}, but {@code l+n>r}: left downstream was lagging behind but now right
 *     downstream is. Just send {@code r-l=abs(l-r)} upstream</li>
 * </ul>
 * <p>
 * The last two cases can be combined into sending {@code min(n, abs(l-r))} upstream. So we
 * only need to test for the first case.
 */
@Internal
public final class UpstreamBalancer {
    private static final AtomicLongFieldUpdater<UpstreamBalancer> DELTA = AtomicLongFieldUpdater.newUpdater(UpstreamBalancer.class, "delta");
    private static final AtomicIntegerFieldUpdater<UpstreamBalancer> FLAGS = AtomicIntegerFieldUpdater.newUpdater(UpstreamBalancer.class, "flags");

    private static final int FLAG_DISCARD_A = 1;
    private static final int FLAG_DISCARD_B = 2;
    private static final int MASK_DISCARD = FLAG_DISCARD_A | FLAG_DISCARD_B;

    private static final int FLAG_START_A = 4;
    private static final int FLAG_START_B = 8;
    private static final int MASK_START = FLAG_START_A | FLAG_START_B;

    private static final int FLAG_DISREGARD_A = 16;
    private static final int FLAG_DISREGARD_B = 32;
    private static final int MASK_DISREGARD = FLAG_DISREGARD_A | FLAG_DISREGARD_B;

    private final BufferConsumer.Upstream upstream;
    private volatile long delta;
    private volatile int flags;

    private UpstreamBalancer(BufferConsumer.Upstream upstream) {
        this.upstream = upstream;
    }

    /**
     * Implementation of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#SLOWEST}.
     *
     * @param upstream The original upstream
     * @return The balanced upstreams
     */
    public static UpstreamPair slowest(BufferConsumer.Upstream upstream) {
        UpstreamBalancer balancer = new UpstreamBalancer(upstream);
        return new UpstreamPair(balancer.new SlowestUpstreamImpl(false), balancer.new SlowestUpstreamImpl(true));
    }

    /**
     * Implementation of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#FASTEST}.
     *
     * @param upstream The original upstream
     * @return The balanced upstreams
     */
    public static UpstreamPair fastest(BufferConsumer.Upstream upstream) {
        UpstreamBalancer balancer = new UpstreamBalancer(upstream);
        return new UpstreamPair(balancer.new FastestUpstreamImpl(false), balancer.new FastestUpstreamImpl(true));
    }

    /**
     * Implementation of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#ORIGINAL} and
     * {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#NEW}.
     *
     * @param upstream The original upstream
     * @return The balanced upstreams
     */
    public static UpstreamPair first(BufferConsumer.Upstream upstream) {
        UpstreamBalancer balancer = new UpstreamBalancer(upstream);
        return new UpstreamPair(balancer.new PassthroughUpstreamImpl(), balancer.new IgnoringUpstreamImpl());
    }

    /**
     * Create a pair of {@link BufferConsumer.Upstream}
     * instances that delegates to the given {@code upstream} according to the semantics of the
     * given {@code mode}.
     *
     * @param upstream The original upstream
     * @param mode The balancing mode
     * @return The balanced upstreams
     */
    public static UpstreamPair balancer(BufferConsumer.Upstream upstream, ByteBody.SplitBackpressureMode mode) {
        return switch (mode) {
            case SLOWEST -> slowest(upstream);
            case FASTEST -> fastest(upstream);
            case ORIGINAL -> first(upstream);
            case NEW -> first(upstream).flip();
        };
    }

    private int getAndSetFlag(int flag) {
        while (true) {
            int current = this.flags;
            if ((current & flag) != 0) {
                // already set
                return current;
            }
            int next = current | flag;
            if (FLAGS.compareAndSet(UpstreamBalancer.this, current, next)) {
                return current;
            }
        }
    }

    private boolean setFlagAndCheckMask(int flag, int mask) {
        int old = getAndSetFlag(flag);
        return (old & mask) != mask && ((old | flag) & mask) == mask;
    }

    private static long subtractSaturating(long dest, long n) {
        assert n >= 0;
        long sum = dest - n;
        // also prevent MIN_VALUE to avoid some edge cases
        if (sum > dest || sum == Long.MIN_VALUE) {
            sum = -Long.MAX_VALUE;
        }
        return sum;
    }

    private static long addSaturating(long dest, long n) {
        assert n >= 0;
        long sum = dest + n;
        if (sum < dest) {
            sum = Long.MAX_VALUE;
        }
        return sum;
    }

    private void addSlowest(boolean inv, long n) {
        if (n == 0) {
            return;
        }

        assert n > 0;

        long oldValue = DELTA.getAndUpdate(this, prev -> inv ? subtractSaturating(prev, n) : addSaturating(prev, n));
        if (oldValue < 0 != inv) {
            long actual = Math.min(n, Math.abs(oldValue));
            if (actual > 0) {
                this.upstream.onBytesConsumed(actual);
            }
        }
    }

    private void addFastest(boolean inv, long n) {
        if (n == 0) {
            return;
        }

        assert n > 0;

        long newValue = DELTA.updateAndGet(this, prev -> inv ? subtractSaturating(prev, n) : addSaturating(prev, n));
        if (newValue > 0 != inv) {
            long actual = Math.min(n, Math.abs(newValue));
            if (actual > 0) {
                this.upstream.onBytesConsumed(actual);
            }
        }
    }

    private void pushSomeFromIgnored() {
        // if delta > 0, push that demand upstream.
        long n = DELTA.getAndUpdate(this, l -> l > 0 ? 0 : l);
        if (n > 0) {
            upstream.onBytesConsumed(n);
        }
    }

    private abstract class UpstreamImpl implements BufferConsumer.Upstream {
        final boolean inv;

        UpstreamImpl(boolean inv) {
            this.inv = inv;
        }

        @Override
        public void allowDiscard() {
            int flag = inv ? FLAG_DISCARD_B : FLAG_DISCARD_A;
            if (setFlagAndCheckMask(flag, MASK_DISCARD)) {
                // both streams discarded
                upstream.allowDiscard();
            }
        }

        @Override
        public void disregardBackpressure() {
            int flag = inv ? FLAG_DISREGARD_B : FLAG_DISREGARD_A;
            int old = getAndSetFlag(flag);
            if ((old & MASK_DISREGARD) != MASK_DISREGARD && ((old | flag) & MASK_DISREGARD) == MASK_DISREGARD) {
                upstream.disregardBackpressure();
            } else if ((old | flag) != old) {
                disregardBackpressureThisSide();
            }
        }

        protected void disregardBackpressureThisSide() {
        }
    }

    private final class SlowestUpstreamImpl extends UpstreamImpl {
        SlowestUpstreamImpl(boolean inv) {
            super(inv);
        }

        @Override
        public void start() {
            int flag = inv ? FLAG_START_A : FLAG_START_B;
            if (setFlagAndCheckMask(flag, MASK_START)) {
                // both downstreams signalled start
                upstream.start();
            }
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            addSlowest(inv, bytesConsumed);
        }

        @Override
        protected void disregardBackpressureThisSide() {
            onBytesConsumed(Long.MAX_VALUE);
        }
    }

    private final class FastestUpstreamImpl extends UpstreamImpl {
        FastestUpstreamImpl(boolean inv) {
            super(inv);
        }

        @Override
        public void start() {
            upstream.start();
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            addFastest(inv, bytesConsumed);
        }
    }

    private final class IgnoringUpstreamImpl extends UpstreamImpl {
        IgnoringUpstreamImpl() {
            super(true);
        }

        // start() is ignored

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // don't send the demand upstream, but save it for later in case the other side calls disregardBackpressure
            DELTA.updateAndGet(UpstreamBalancer.this, old -> addSaturating(old, bytesConsumed));
            if ((flags & FLAG_DISREGARD_A) != 0) {
                pushSomeFromIgnored();
            }
        }
    }

    private final class PassthroughUpstreamImpl extends UpstreamImpl {
        PassthroughUpstreamImpl() {
            super(false);
        }

        @Override
        public void start() {
            upstream.start();
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // save already-demanded bytes to delta to calculate demand for other side in case of disregardBackpressure
            DELTA.updateAndGet(UpstreamBalancer.this, old -> subtractSaturating(old, bytesConsumed));
            upstream.onBytesConsumed(bytesConsumed);
        }

        @Override
        protected void disregardBackpressureThisSide() {
            // when disregardBackpressure is called on this side, the previously "ignoring" side takes over backpressure.
            pushSomeFromIgnored();
        }
    }

    public record UpstreamPair(BufferConsumer.Upstream left, BufferConsumer.Upstream right) {
        UpstreamPair flip() {
            return new UpstreamPair(right, left);
        }
    }
}
