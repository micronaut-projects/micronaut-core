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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class splits a single stream into two, based on configured
 * {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode}.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Internal
final class StreamPair {
    // originally from micronaut-servlet

    private static final int FLAG_DISCARD_L = 1;
    private static final int FLAG_DISCARD_R = 1 << 1;
    private static final int MASK_DISCARD = FLAG_DISCARD_L | FLAG_DISCARD_R;
    private static final int FLAG_CANCEL_L = 1 << 2;
    private static final int FLAG_CANCEL_R = 1 << 3;
    private static final int MASK_CANCEL = FLAG_CANCEL_L | FLAG_CANCEL_R;

    private final Lock lock = new ReentrantLock();
    private final Condition wakeup = lock.newCondition();
    private final AtomicInteger flags = new AtomicInteger();
    private final ExtendedInputStream upstream;

    /**
     * For SLOWEST mode, if one side is currently waiting for the other, this contains the demand
     * information.
     */
    private Slowest.SlowestDemand slowestDemand = null;

    /**
     * For all modes except SLOWEST, the queue of unprocessed bytes for the slower side.
     */
    private ByteQueue queue;
    /**
     * For FASTEST mode, this is the {@link Side#left} flag of the side that is currently slower,
     * i.e. that will read from {@link #queue}.
     */
    private boolean fastModeSlowerSide;
    /**
     * For ORIGINAL and NEW modes, this flag is set to {@code true} when the upstream is finished.
     */
    private boolean singleSideComplete;
    /**
     * For ORIGINAL and NEW modes, any read exception.
     */
    private IOException singleSideException;

    private StreamPair(ExtendedInputStream upstream) {
        this.upstream = upstream;
    }

    private int getAndSetFlag(int flag) {
        return flags.getAndUpdate(f -> f | flag);
    }

    private boolean setFlagAndCheckMask(int flag, int mask) {
        int old = getAndSetFlag(flag);
        return (old & mask) != mask && ((old | flag) & mask) == mask;
    }

    static Pair createStreamPair(ExtendedInputStream upstream, ByteBody.SplitBackpressureMode backpressureMode) {
        StreamPair pair = new StreamPair(upstream);
        return switch (backpressureMode) {
            case SLOWEST -> new Pair(pair.new Slowest(true), pair.new Slowest(false));
            case FASTEST -> {
                pair.queue = new ByteQueue();
                yield new Pair(pair.new Fastest(true), pair.new Fastest(false));
            }
            case ORIGINAL -> {
                pair.queue = new ByteQueue();
                yield new Pair(pair.new Preferred(), pair.new Listening());
            }
            case NEW -> {
                pair.queue = new ByteQueue();
                yield new Pair(pair.new Listening(), pair.new Preferred());
            }
        };
    }

    record Pair(ExtendedInputStream left, ExtendedInputStream right) {
    }

    private abstract class Side extends ExtendedInputStream {
        final boolean left;

        private Side(boolean left) {
            this.left = left;
        }

        @Override
        public void allowDiscard() {
            if (setFlagAndCheckMask(left ? FLAG_DISCARD_L : FLAG_DISCARD_R, MASK_DISCARD)) {
                upstream.allowDiscard();
            }
        }

        @Override
        public void cancelInput() {
            if (setFlagAndCheckMask(left ? FLAG_CANCEL_L : FLAG_CANCEL_R, MASK_CANCEL)) {
                upstream.cancelInput();
            }
        }

        final boolean isOtherSideCancelled() {
            return (flags.get() & (left ? FLAG_CANCEL_R : FLAG_CANCEL_L)) != 0;
        }
    }

    /**
     * Both sides of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#SLOWEST}.
     */
    private final class Slowest extends Side {
        private Slowest(boolean left) {
            super(left);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            lockBody: try {
                SlowestDemand theirDemand = slowestDemand;
                if (theirDemand == null) {
                    // other side is not reading yet. wait for them.
                    SlowestDemand ourDemand = new SlowestDemand(b, off, len);
                    slowestDemand = ourDemand;
                    do {
                        if (isOtherSideCancelled()) {
                            slowestDemand = null;
                            // other side should be disregarded. we must exit the lock here to
                            // avoid long blocking of any further disregardBackpressure calls on
                            // the other side.
                            break lockBody;
                        }

                        try {
                            wakeup.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            if (!ourDemand.fulfilled) {
                                // not fulfilled
                                slowestDemand = null;
                                throw new InterruptedIOException();
                            }
                        }
                    } while (!ourDemand.fulfilled); // guard for spurious wakeup
                    if (ourDemand.exception != null) {
                        throw ourDemand.exception;
                    }
                    return ourDemand.actualLength;
                } else {
                    // other side is waiting for us. read some data and send it to them.
                    int n = Math.min(len, theirDemand.len);
                    try {
                        int actualLength = upstream.read(b, off, n);
                        if (actualLength >= 0) {
                            System.arraycopy(b, off, theirDemand.dest, theirDemand.off, actualLength);
                        }
                        theirDemand.actualLength = actualLength;
                        theirDemand.fulfilled = true;
                        slowestDemand = null;
                        wakeup.signalAll();
                        return actualLength;
                    } catch (IOException e) {
                        theirDemand.exception = e;
                        theirDemand.fulfilled = true;
                        slowestDemand = null;
                        wakeup.signalAll();
                        throw e;
                    }
                }
            } finally {
                lock.unlock();
            }
            // this is hit when the other side has cancelled their input, see above.
            return upstream.read(b, off, len);
        }

        @Override
        public void cancelInput() {
            super.cancelInput();
            // if the other side is waiting on us, wake it up.
            lock.lock();
            try {
                wakeup.signalAll();
            } finally {
                lock.unlock();
            }
        }

        static class SlowestDemand {
            final byte[] dest;
            final int off;
            final int len;
            boolean fulfilled;
            IOException exception;
            int actualLength;

            SlowestDemand(byte[] dest, int off, int len) {
                this.dest = dest;
                this.off = off;
                this.len = len;
            }
        }
    }

    /**
     * Both sides of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#FASTEST}.
     */
    private final class Fastest extends Side {
        private Fastest(boolean left) {
            super(left);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                if (!queue.isEmpty() && fastModeSlowerSide == left) {
                    return queue.take(b, off, len);
                } else {
                    int n = upstream.read(b, off, len);
                    if (n == -1) {
                        return -1;
                    }
                    if (!isOtherSideCancelled()) {
                        fastModeSlowerSide = !left;
                        queue.addCopy(b, off, n);
                    } else {
                        // discard queue here because we already hold the lock
                        queue.clear();
                    }
                    return n;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Original side of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#ORIGINAL}, or
     * new side of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#NEW}.
     */
    private final class Preferred extends Side {
        Preferred() {
            super(true);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                int n = upstream.read(b, off, len);
                if (n == -1) {
                    singleSideComplete = true;
                } else if (!isOtherSideCancelled()) {
                    queue.addCopy(b, off, n);
                } else {
                    // discard queue here because we already hold the lock
                    queue.clear();
                }
                // in case other side is waiting, wake them
                wakeup.signalAll();
                return n;
            } catch (IOException e) {
                singleSideException = e;
                wakeup.signalAll();
                throw e;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void cancelInput() {
            super.cancelInput();
            lock.lock();
            try {
                // signal other side that it's time to take over
                wakeup.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * New side of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#ORIGINAL}, or
     * original side of {@link io.micronaut.http.body.ByteBody.SplitBackpressureMode#NEW}.
     */
    private final class Listening extends Side {
        Listening() {
            super(false);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                while (true) {
                    if (!queue.isEmpty()) {
                        return queue.take(b, off, len);
                    }
                    if (singleSideException != null) {
                        throw singleSideException;
                    }
                    if (singleSideComplete) {
                        return -1;
                    }
                    if (isOtherSideCancelled()) {
                        // exit lock and take over reading
                        break;
                    }
                    // wait for other side to read some data and wake us
                    wakeup.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            } finally {
                lock.unlock();
            }
            // only hit if other side has cancelled. in that case, directly read
            return upstream.read(b, off, len);
        }
    }
}
