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
import io.micronaut.scheduling.LoomSupport;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ManualIoEventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import jakarta.inject.Singleton;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Internal
@Experimental
public final class LoomCarrierGroup extends MultiThreadIoEventLoopGroup {
    private static final int MAX_IMMEDIATE_RUN_DEPTH = 2;

    private LoomCarrierGroup(Factory factory, int nThreads, Executor executor, IoHandlerFactory ioHandlerFactory) {
        super(nThreads, executor, ioHandlerFactory, factory);
    }

    @Override
    protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
        Runner runner = new Runner((Factory) args[0], ioHandlerFactory);
        executor.execute(runner);
        try {
            return runner.completer.get(); // TODO https://github.com/netty/netty/pull/14976
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Singleton
    @Requires(condition = LoomSupport.LoomCondition.class)
    @Requires(condition = PrivateLoomSupport.PrivateLoomCondition.class)
    public static final class Factory {
        final EventLoopLoomFactory holder;

        Factory(EventLoopLoomFactory holder) {
            this.holder = holder;
        }

        public EventLoopGroup create(int nThreads, Executor executor, IoHandlerFactory ioHandlerFactory) {
            return new LoomCarrierGroup(this, nThreads, executor, ioHandlerFactory);
        }
    }

    private static final class Runner implements Runnable, Executor {
        final Factory factory;
        final IoHandlerFactory ioHandlerFactory;
        final CompletableFuture<IoEventLoop> completer = new CompletableFuture<>();
        ManualIoEventLoop delegate;
        final Queue<Runnable> globalLoomQueue = new MpscUnboundedArrayQueue<>(4096);
        final Queue<Runnable> localLoomQueue = new ArrayDeque<>();
        int loomDepth = 0;

        Runner(Factory factory, IoHandlerFactory ioHandlerFactory) {
            this.factory = factory;
            this.ioHandlerFactory = ioExecutor -> new DelegateIoHandler(ioHandlerFactory.newHandler(ioExecutor)) {
                @Override
                public void wakeup() {
                    // we don't need to wake up if we're running on a vthread carried by this event loop.
                    Thread thread = Thread.currentThread();
                    if (LoomSupport.isVirtual(thread) && ioExecutor.isExecutorThread(PrivateLoomSupport.getCarrierThread(thread))) {
                        return;
                    }

                    super.wakeup();
                }
            };
        }

        @Override
        public void run() {
            Thread carrier = Thread.currentThread();
            delegate = new ManualIoEventLoop(carrier, ioHandlerFactory);
            completer.complete(delegate);
            factory.holder.targetScheduler.set(LoomSupport.newVirtualThreadFactory("loom-on-netty-", b -> PrivateLoomSupport.setScheduler(b, this)));

            while (!delegate.isShuttingDown()) {
                boolean workDone = delegate.runNow() != 0;
                long deadline = System.nanoTime() + 1_000_000_000L;
                workDone |= runSomeLoomTasks(localLoomQueue, deadline);
                workDone |= runSomeLoomTasks(globalLoomQueue, deadline);
                if (!workDone) {
                    delegate.run(1_000_000_000L);
                }
            }
            while (!delegate.isTerminated()) {
                delegate.runNow();
                drainLoomQueue(localLoomQueue);
                drainLoomQueue(globalLoomQueue);
            }
            // TODO: finish draining loom queue
        }

        private void drainLoomQueue(Queue<Runnable> queue) {
            while (true) {
                Runnable task = queue.poll();
                if (task == null) {
                    break;
                }
                PrivateLoomSupport.getDefaultScheduler().execute(task);
            }
        }

        private boolean runSomeLoomTasks(Queue<Runnable> queue, long deadline) {
            loomDepth = 1;
            boolean anyWorkDone = false;
            while (deadline < System.nanoTime()) {
                Runnable task = queue.poll();
                if (task == null) {
                    break;
                }
                anyWorkDone = true;
                task.run();
            }
            loomDepth = 0;
            return anyWorkDone;
        }

        @Override
        public void execute(Runnable command) {
            if (delegate.isShuttingDown()) {
                PrivateLoomSupport.getDefaultScheduler().execute(command);
                return;
            }

            if (delegate.inEventLoop()) {
                int loomDepth = this.loomDepth;
                if (loomDepth >= MAX_IMMEDIATE_RUN_DEPTH) {
                    localLoomQueue.add(command);
                } else {
                    this.loomDepth = loomDepth + 1;
                    command.run();
                    this.loomDepth = loomDepth;
                }
            } else {
                globalLoomQueue.add(command);
                delegate.wakeup();
            }
        }
    }
}
