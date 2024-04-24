package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * This class merges the backpressure of two data streams. The bytes signaled to the
 * {@link #upstream} is always the <i>minimum</i> of the consumed bytes of the two downstreams.
 *
 * @implNote This is a bit tricky to implement without locking due to the concurrent nature of
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
    private static final AtomicIntegerFieldUpdater<UpstreamBalancer> DISCARD_FLAGS = AtomicIntegerFieldUpdater.newUpdater(UpstreamBalancer.class, "discardFlags");

    private final BufferConsumer.Upstream upstream;
    private volatile long delta;
    private volatile int discardFlags;

    private UpstreamBalancer(BufferConsumer.Upstream upstream) {
        this.upstream = upstream;
    }

    public static UpstreamPair slowest(BufferConsumer.Upstream upstream) {
        UpstreamBalancer balancer = new UpstreamBalancer(upstream);
        return new UpstreamPair(balancer.new SlowestUpstreamImpl(false), balancer.new SlowestUpstreamImpl(true));
    }

    static UpstreamPair fastest(BufferConsumer.Upstream upstream) {
        UpstreamBalancer balancer = new UpstreamBalancer(upstream);
        return new UpstreamPair(balancer.new FastestUpstreamImpl(false), balancer.new FastestUpstreamImpl(true));
    }

    static UpstreamPair first(BufferConsumer.Upstream upstream) {
        UpstreamBalancer balancer = new UpstreamBalancer(upstream);
        return new UpstreamPair(balancer.new PassthroughUpstreamImpl(), balancer.new IgnoringUpstreamImpl());
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
        assert n > 0;

        long newValue = DELTA.updateAndGet(this, prev -> inv ? subtractSaturating(prev, n) : addSaturating(prev, n));
        if (newValue > 0 != inv) {
            long actual = Math.min(n, Math.abs(newValue));
            if (actual > 0) {
                this.upstream.onBytesConsumed(actual);
            }
        }
    }

    private abstract class UpstreamImpl implements BufferConsumer.Upstream {
        final boolean inv;

        UpstreamImpl(boolean inv) {
            this.inv = inv;
        }

        @Override
        public void allowDiscard() {
            int mask = inv ? 2 : 1;
            while (true) {
                int current = discardFlags;
                if ((current & mask) != 0) {
                    // already discarded
                    return;
                }
                int next = current | mask;
                if (DISCARD_FLAGS.compareAndSet(UpstreamBalancer.this, current, next)) {
                    if (next == 3) {
                        // both streams discarded
                        upstream.allowDiscard();
                    } else {
                        // only we are discarded right now. Special case for slow mode, need to
                        // prevent stall
                        if (getClass() == SlowestUpstreamImpl.class ||
                            getClass() == PassthroughUpstreamImpl.class) {
                            onBytesConsumed(Long.MAX_VALUE);
                        }
                    }
                    return;
                }
                // retry
            }
        }
    }

    private final class SlowestUpstreamImpl extends UpstreamImpl {
        SlowestUpstreamImpl(boolean inv) {
            super(inv);
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            addSlowest(inv, bytesConsumed);
        }
    }

    private final class FastestUpstreamImpl extends UpstreamImpl {
        FastestUpstreamImpl(boolean inv) {
            super(inv);
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

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // ignored
        }
    }

    private final class PassthroughUpstreamImpl extends UpstreamImpl {
        PassthroughUpstreamImpl() {
            super(false);
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            upstream.onBytesConsumed(bytesConsumed);
        }
    }

    public record UpstreamPair(BufferConsumer.Upstream left, BufferConsumer.Upstream right) {
        UpstreamPair flip() {
            return new UpstreamPair(right, left);
        }
    }
}
