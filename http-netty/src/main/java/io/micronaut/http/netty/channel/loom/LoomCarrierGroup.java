/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty.channel.loom;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.LoomSupport;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ManualIoEventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.AttributeMap;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import jakarta.inject.Singleton;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.StackTrace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Netty {@link EventLoopGroup} that can also carry virtual threads.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
@Experimental
public final class LoomCarrierGroup extends MultiThreadIoEventLoopGroup {
    private List<Runner> runners;

    private LoomCarrierGroup(Factory factory, int nThreads, Executor executor, IoHandlerFactory ioHandlerFactory) {
        super(nThreads, executor, ioHandlerFactory, factory);
    }

    @Override
    protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
        if (runners == null) {
            // newChild is called from the super constructor, so we must initialize the fields here
            runners = new ArrayList<>();
        }
        Runner runner = new Runner(runners.size(), (Factory) args[0], ioHandlerFactory);
        this.runners.add(runner);
        executor.execute(runner);
        return runner.delegate;
    }

    @Singleton
    @Requires(condition = LoomSupport.LoomCondition.class)
    @Requires(condition = PrivateLoomSupport.PrivateLoomCondition.class)
    public static final class Factory {
        final EventLoopLoomFactory holder;
        final LoomCarrierConfiguration configuration;

        Factory(EventLoopLoomFactory holder, LoomCarrierConfiguration configuration) {
            this.holder = holder;
            this.configuration = configuration;
        }

        public EventLoopGroup create(int nThreads, Executor executor, IoHandlerFactory ioHandlerFactory) {
            return new LoomCarrierGroup(this, nThreads, executor, ioHandlerFactory);
        }
    }

    final class Runner implements Runnable, EventLoopVirtualThreadScheduler, ThreadFactory, Executor, LoomBranchSupport.VirtualThreadSchedulerProxy {
        final int id;
        final Factory factory;
        final ManualIoEventLoop delegate;
        final AttributeMap attributeMap = new DefaultAttributeMap();
        IoHandler backingHandler;
        Thread carrier;

        /**
         * The continuation of the virtual thread responsible for running the event loop.
         */
        Runnable ioContinuation;
        Thread ioThread;
        /**
         * {@code true} when the {@link #ioContinuation} has been scheduled but has not run yet.
         */
        volatile boolean ioContinuationScheduled;
        /**
         * Queue for continuations submitted outside the event loop.
         */
        final Queue<Runnable> globalLoomQueue = new MpscUnboundedArrayQueue<>(4096);
        /**
         * Queue for continuations submitted on the event loop.
         */
        final Deque<ScheduledTask> localLoomQueue = new ArrayDeque<>();
        /**
         * Set to {@code true} during continuation execution to prevent recursion.
         */
        volatile boolean loomNested;
        /**
         * Set to {@code true} when a task is submitted to the event loop to signal that we should
         * stop execution of continuations to get straight to running the submitted task. This can
         * improve latency, because event loop tasks are often responsible for writes, but it may
         * harm throughput.
         */
        volatile boolean expediteWrite = false;
        /**
         * Whether we should block for the next {@link ManualIoEventLoop#run(long, long)} call.
         */
        volatile boolean block;
        /**
         * If {@code true}, continuations from {@link #localLoomQueue} are executed in FIFO mode,
         * otherwise FILO mode is used.
         */
        boolean continuationsFifo = false;
        /**
         * Summed execution time of continuations since {@link #continuationsFifo} was last
         * flipped.
         */
        long continuationTime = 0L;
        /**
         * This is set to {@code true} when there is a backlog of
         * {@link LoomCarrierConfiguration#throughputModeThreshold()} queued tasks. This improves
         * throughput at cost of latency.
         */
        boolean throughputMode = false;
        /**
         * Set to {@code true} during a blocking call to {@link ManualIoEventLoop#run(long, long)}.
         * This enables immediate execution of submitted continuations to further improve latency
         * when there are few concurrent requests.
         */
        boolean idle = false;

        /**
         * Number of active continuations (scheduled or running). This counter is only updated from
         * the event loop thread. Only the sum with {@link #activeThreadsExternal} is meaningful.
         */
        volatile int activeThreadsLocal = 0;
        /**
         * Number of active continuations (scheduled or running). This counter is only updated from
         * external threads. Only the sum with {@link #activeThreadsLocal} is meaningful.
         */
        final AtomicInteger activeThreadsExternal = new AtomicInteger();

        int warmupTasks;

        Runner(int id, Factory factory, IoHandlerFactory ioHandlerFactory) {
            this.id = id;
            this.factory = factory;
            this.warmupTasks = factory.configuration.normalWarmupTasks();
            IoHandlerFactory proxied = ioExecutor -> {
                backingHandler = ioHandlerFactory.newHandler(ioExecutor);
                return new DelegateIoHandler(backingHandler) {
                    @Override
                    public void wakeup() {
                        // this is called on EventLoop.execute

                        if (block) {
                            block = false;
                            super.wakeup();
                        }

                        // we don't need to wake up if we're running on a vthread carried by this event loop.
                        Thread thread = Thread.currentThread();
                        if (isOnRunner(thread)) {
                            if (!throughputMode) {
                                expediteWrite = true;
                                Thread.yield();
                            }
                        }
                    }
                };
            };
            this.delegate = new ManualIoEventLoop(null, proxied);
        }

        @Override
        public @NonNull AttributeMap attributeMap() {
            return attributeMap;
        }

        @Override
        public EventExecutor eventLoop() {
            return delegate;
        }

        private boolean isOnRunner(Thread thread) {
            if (!LoomSupport.isVirtual(thread)) {
                return false;
            }
            if (LoomBranchSupport.isSupported()) {
                assert thread == Thread.currentThread();
                return LoomBranchSupport.currentScheduler() == this;
            } else {
                return PrivateLoomSupport.getScheduler(thread) == Runner.this;
            }
        }

        /**
         * Number of active (scheduled or running) virtual threads.
         *
         * @return The number of active virtual threads
         */
        int activeThreads() {
            return activeThreadsLocal + activeThreadsExternal.get();
        }

        @Override
        public Thread newThread(Runnable r) {
            return LoomSupport.unstarted("loom-on-netty-" + id + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong()), b -> {
                if (warmupTasks > 0) {
                    warmupTasks--;
                    if (!LoomBranchSupport.isSupported()) {
                        PrivateLoomSupport.setScheduler(b, PrivateLoomSupport.getDefaultScheduler());
                    }
                    return;
                }

                Runner dst = Runner.this;
                int active = activeThreads();
                if (active >= factory.configuration.workSpillThreshold()) {
                    // spill to a less busy event loop
                    for (Runner runner : runners) {
                        int a = runner.activeThreads();
                        if (a < active) {
                            dst = runner;
                            active = a;
                        }
                    }
                }
                if (LoomBranchSupport.isSupported()) {
                    LoomBranchSupport.setScheduler(b, new StickyScheduler(dst));
                } else {
                    PrivateLoomSupport.setScheduler(b, new StickyScheduler(dst));
                }
            }, r);
        }

        @Override
        public void run() {
            carrier = Thread.currentThread();

            ioThread = LoomSupport.unstarted(
                "loom-on-netty-" + id + "-io",
                b -> {
                    if (LoomBranchSupport.isSupported()) {
                        LoomBranchSupport.setScheduler(b, new IoScheduler(this));
                    } else {
                        PrivateLoomSupport.setScheduler(b, new IoScheduler(this));
                    }
                },
                () -> FastThreadLocalThread.runWithFastThreadLocal(this::runIo)
            );
            ioThread.start();
            assert ioContinuationScheduled;

            while (!delegate.isTerminated()) {
                boolean ioContinuationScheduled = this.ioContinuationScheduled;
                if (!ioContinuationScheduled) {
                    LockSupport.park();
                    ioContinuationScheduled = this.ioContinuationScheduled;
                }
                if (ioContinuationScheduled) {
                    this.ioContinuationScheduled = false;
                    ioContinuation.run();
                }

                // Phase 3: Run continuations
                tick(3);
                globalToLocal();
                throughputMode = localLoomQueue.size() > factory.configuration.throughputModeThreshold();
                if (runContinuations(null, System.nanoTime() + timeSlice()) || expediteWrite) {
                    block = false;
                }
            }
        }

        private void runIo() {
            delegate.setOwningThread(Thread.currentThread());
            ThreadExecutorMap.setCurrentExecutor(delegate);
            factory.holder.targetScheduler.set(this);

            while (!delegate.isShuttingDown()) {
                // Phase 1/2: run IO (blocking/non-blocking) and event loop tasks
                long waitNanos;
                if (block) {
                    waitNanos = factory.configuration.blockTime().toNanos();
                    idle = true;
                    tick(1);
                } else {
                    waitNanos = -1;
                    tick(2);
                }
                block = delegate.run(waitNanos, timeSlice()) == 0;
                idle = false;
                expediteWrite = false;

                Thread.yield();

                // Phase 4: Run event loop tasks, e.g. write ops submitted by the virtual threads
                tick(4);
                if (delegate.runNonBlockingTasks(timeSlice()) != 0) {
                    block = false;
                }
            }

            while (!delegate.isTerminated()) {
                delegate.runNow(-1);
                Thread.yield();
            }
        }

        /**
         * Move tasks from the {@link #globalLoomQueue} to the {@link #localLoomQueue}.
         */
        private void globalToLocal() {
            while (true) {
                Runnable task = globalLoomQueue.poll();
                if (task == null) {
                    break;
                }
                // It would be nice to use the real scheduled time here, but then we'd have to
                // place the task somewhere in the middle of the queue rather than just at the
                // start.
                localLoomQueue.addFirst(new ScheduledTask(System.nanoTime(), task));
            }
        }

        private long timeSlice() {
            return (throughputMode ? factory.configuration.timeSliceThroughput() : factory.configuration.timeSliceLatency()).toNanos();
        }

        private boolean runContinuations(@Nullable Runnable immediateTask, long deadline) {
            assert !loomNested;

            boolean ranAny = false;
            loomNested = true;
            long now;
            do {
                now = System.nanoTime();
                // select a task
                Runnable task;
                if (immediateTask == null) {
                    if (localLoomQueue.isEmpty()) {
                        break;
                    }
                    if (continuationsFifo) {
                        task = localLoomQueue.pollLast().task();
                    } else {
                        task = localLoomQueue.pollFirst().task();
                    }
                } else {
                    task = immediateTask;
                    immediateTask = null;
                }
                ranAny = true;
                task.run();
                //noinspection NonAtomicOperationOnVolatileField
                activeThreadsLocal--;
                long end = System.nanoTime();
                continuationTime += end - now;
                now = end;

                // decide whether to switch between fifo and filo modes
                if (continuationTime > factory.configuration.fifoSwitchTime().toNanos()) {
                    if (continuationsFifo) {
                        continuationsFifo = false;
                    } else {
                        ScheduledTask last = localLoomQueue.peekLast();
                        continuationsFifo = last != null && last.scheduleTime > now + factory.configuration.taskFifoThreshold().toNanos();
                    }
                    continuationTime = 0;
                }
            } while (now < deadline && !expediteWrite);
            loomNested = false;
            return ranAny;
        }

        private void executeIo(Thread thread, Runnable command) {
            if (thread == ioThread) {
                Thread t = Thread.currentThread();
                ioContinuation = command;
                ioContinuationScheduled = true;
                if (t != carrier && !isOnRunner(t)) {
                    LockSupport.unpark(carrier);
                }
            } else {
                LoomBranchSupport.runOnDefaultScheduler(command);
            }
        }

        private void executeIo(Runnable command) {
            // special handling for the continuation of the IO thread.
            Runnable ioContinuation = this.ioContinuation;
            if (ioContinuation == null) {
                ioContinuation = command;
                this.ioContinuation = command;
            }
            if (ioContinuation == command) {
                Thread t = Thread.currentThread();
                ioContinuationScheduled = true;
                if (t != carrier && !isOnRunner(t)) {
                    LockSupport.unpark(carrier);
                }
                return;
            }

            PrivateLoomSupport.getDefaultScheduler().execute(command);
        }

        @Override
        public void execute(Runnable task) {
            execute(null, task);
        }

        @Override
        public void execute(Thread thread, Runnable command) {
            if (delegate.isShuttingDown()) {
                if (LoomBranchSupport.isSupported()) {
                    LoomBranchSupport.runOnDefaultScheduler(command);
                } else {
                    PrivateLoomSupport.getDefaultScheduler().execute(command);
                }
                return;
            }

            // JFR
            ContinuationScheduled scheduled;
            if (ContinuationScheduled.INSTANCE.isEnabled()) {
                scheduled = new ContinuationScheduled();
                long hash = System.identityHashCode(command);
                scheduled.hashCode = hash;
                scheduled.virtualThreadName = thread == null ? null : thread.getName();

                Runnable r = command;
                command = () -> {
                    ContinuationStarted started = new ContinuationStarted();
                    started.begin();

                    r.run();

                    started.end();
                    started.hashCode = hash;
                    started.virtualThreadName = thread == null ? null : thread.getName();
                    started.commit();
                };
            } else {
                scheduled = null;
            }

            if (Thread.currentThread() == carrier) {
                //noinspection NonAtomicOperationOnVolatileField
                activeThreadsLocal++;
                long time = System.nanoTime();
                if (idle && !loomNested && !expediteWrite) {
                    if (scheduled != null) {
                        scheduled.scheduleMode = 2;
                        scheduled.queueDepth = -1;
                        scheduled.commit();
                    }
                    runContinuations(command, time + factory.configuration.timeSliceLatency().toNanos());
                } else {
                    if (scheduled != null) {
                        scheduled.scheduleMode = 1;
                        scheduled.queueDepth = localLoomQueue.size();
                        scheduled.commit();
                    }
                    localLoomQueue.addFirst(new ScheduledTask(time, command));
                }
            } else {
                activeThreadsExternal.incrementAndGet();
                if (scheduled != null) {
                    scheduled.scheduleMode = 3;
                    scheduled.queueDepth = globalLoomQueue.size();
                    scheduled.commit();
                }
                globalLoomQueue.add(command);

                if (isOnRunner(Thread.currentThread())) {
                    if (!throughputMode && !expediteWrite) {
                        Thread.yield();
                    }
                } else {
                    backingHandler.wakeup();
                }
            }
        }

        private void tick(int type) {
            if (LoopTick.INSTANCE.isEnabled()) {
                LoopTick tick = new LoopTick();
                tick.loopIndex = id;
                tick.type = type;
                tick.activeThreads = activeThreads();
                tick.commit();
            }
        }
    }

    record IoScheduler(Runner runner) implements Executor, EventLoopVirtualThreadScheduler, LoomBranchSupport.VirtualThreadSchedulerProxy {
        @Override
        public @NonNull AttributeMap attributeMap() {
            return runner.attributeMap();
        }

        @Override
        public @NonNull EventExecutor eventLoop() {
            return runner.eventLoop();
        }

        @Override
        public void execute(Thread thread, Runnable task) {
            runner.executeIo(thread, task);
        }

        @Override
        public void execute(Runnable command) {
            runner.executeIo(command);
        }
    }

    record StickyScheduler(Runner io) implements Executor, EventLoopVirtualThreadScheduler, LoomBranchSupport.VirtualThreadSchedulerProxy {
        @Override
        public void execute(Runnable command) {
            Thread currentThread = Thread.currentThread();
            Executor dst;
            if (currentThread instanceof ForkJoinWorkerThread fjwt && fjwt.getPool() == PrivateLoomSupport.getDefaultScheduler()) {
                dst = PrivateLoomSupport.getDefaultScheduler();
            } else if (LoomSupport.isVirtual(currentThread) && PrivateLoomSupport.getScheduler(currentThread) == PrivateLoomSupport.getDefaultScheduler()) {
                dst = PrivateLoomSupport.getDefaultScheduler();
            } else {
                // move back to event loop whenever possible (e.g. after sleep)
                dst = io;
            }
            dst.execute(command);
        }

        @Override
        public void execute(Thread thread, Runnable task) {
            LoomBranchSupport.VirtualThreadSchedulerProxy dst;
            if (LoomSupport.isVirtual(Thread.currentThread())) {
                dst = LoomBranchSupport.currentScheduler();
                if (dst instanceof EventLoopVirtualThreadScheduler) {
                    if (dst instanceof IoScheduler s) {
                        dst = s.runner;
                    } else if (dst instanceof StickyScheduler s) {
                        dst = s.io;
                    }
                } else {
                    dst = (t, r) -> LoomBranchSupport.runOnDefaultScheduler(r);
                }
            } else {
                dst = io;
            }
            dst.execute(thread, task);
        }

        @Override
        public @NonNull AttributeMap attributeMap() {
            return io.attributeMap();
        }

        @Override
        public @NonNull EventExecutor eventLoop() {
            return io.eventLoop();
        }
    }

    private record ScheduledTask(
        long scheduleTime,
        Runnable task
    ) {
    }

    @StackTrace(false)
    @Enabled(false)
    static class ContinuationScheduled extends Event {
        static final ContinuationScheduled INSTANCE = new ContinuationScheduled();

        long hashCode;
        String virtualThreadName;
        int scheduleMode;
        int queueDepth;
    }

    @StackTrace(false)
    @Enabled(false)
    static class ContinuationStarted extends Event {
        long hashCode;
        String virtualThreadName;
    }

    @StackTrace(false)
    @Enabled(false)
    static class LoopTick extends Event {
        static final LoopTick INSTANCE = new LoopTick();

        int loopIndex;
        int type;
        int activeThreads;
    }
}
