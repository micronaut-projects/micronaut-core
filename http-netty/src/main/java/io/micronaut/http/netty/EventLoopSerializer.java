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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * This class forwards reactive operations onto an {@link io.netty.channel.EventLoop} if they are
 * called from outside that loop. It avoids unnecessary {@link EventExecutor#execute} calls when
 * the reactive methods are called inside the loop. All the while it ensures the operations remain
 * serialized (in the same order).
 * <p>
 * Should be used like this:
 * <pre>
 *     public void onNext(Object item) {
 *         if (serializer.executeNow(() -> onNext0(item))) {
 *             onNext0(item);
 *         }
 *     }
 *
 *     private void onNext0(Object item) {
 *         ...
 *     }
 * </pre>
 * <p>
 * This class is <b>not</b> thread-safe: The invariants for calls to {@link #executeNow} are very
 * strict. In particular:
 * <ul>
 *     <li>There must be no concurrent calls to {@link #executeNow}.</li>
 *     <li>When {@link #executeNow} returns {@code true}, the subsequent execution of the child
 *     method ({@code onNext0} in the above example) must fully complete before the next
 *     {@link #executeNow} call. This ensures that there are no concurrent calls to the child
 *     method.</li>
 * </ul>
 * Both of these invariants are guaranteed by the reactive spec, but may not apply to other use
 * cases.
 *
 * @since 4.4.0
 * @author Jonas Konrad
 */
@Internal
public final class EventLoopSerializer {
    /**
     * This adds some extra checks to find bugs.
     */
    private static final boolean STRICT_CHECKING = false;

    private final OrderedEventExecutor loop;
    /**
     * Generation assigned to the next task.
     */
    private int submitGeneration = 0;
    /**
     * Generation of the next task that can be executed immediately. All tasks with a lower
     * generation count have been fully executed already, with one exception: If the last task
     * was submitted on the event loop, {@link #executeNow} returned true and the caller may not
     * have fully executed it yet.
     */
    private volatile int runGeneration = 0;

    public EventLoopSerializer(OrderedEventExecutor loop) {
        this.loop = loop;
    }

    /**
     * Determine whether the next step can be executed immediately. Iff this method returns
     * {@code true}, {@code delayTask} will be ignored and the caller should call the target method
     * manually. Iff this method returns {@code false}, the caller should take no further action as
     * {@code delayTask} will be run in the future.
     *
     * @param delayTask The task to run if it can't be run immediately
     * @return {@code true} if the caller should instead run the task immediately
     */
    public boolean executeNow(Runnable delayTask) {
        // pick a generation ID for this task.
        int generation = submitGeneration++;
        if (loop.inEventLoop()) {
            if (runGeneration == generation) {
                if (STRICT_CHECKING) {
                    runGeneration = generation + 1;
                    try {
                        delayTask.run();
                    } finally {
                        if (runGeneration != generation + 1 || submitGeneration != generation + 1) {
                            throw new AssertionError("Nested call?");
                        }
                    }
                    return false;
                }

                /*
                 * All previous tasks have run completely, the caller can run the task immediately.
                 * Technically, we should only increment the runGeneration after the caller has
                 * finished running the task. However, doing it before is safe because of two
                 * reasons:
                 * - Any other task already submitted is executed using EventLoop#execute, so it
                 * will certainly run after the child method completes
                 * - No new task can be submitted while this task is running, because we're still
                 * in the outer reactive method call, and the reactive spec forbids nested or
                 * concurrent calls
                 */
                runGeneration = generation + 1;
                return true;
            }
            // another task already submitted, need to delay to stay serialized
        }
        loop.execute(new Delayed(delayTask, generation));
        return false;
    }

    private final class Delayed implements Runnable {
        private final Runnable task;
        private final int generation;

        private Delayed(Runnable task, int generation) {
            this.task = task;
            this.generation = generation;
        }

        @Override
        public void run() {
            if (runGeneration != generation) {
                throw new IllegalStateException("Improper run order. Expected " + generation + ", was " + runGeneration);
            }
            try {
                task.run();
            } finally {
                if (STRICT_CHECKING) {
                    if (runGeneration != generation) {
                        throw new AssertionError("Weird");
                    }
                }
                runGeneration = generation + 1;
            }
        }
    }
}
