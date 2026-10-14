/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * FSEvents as {@link FsEventStreams} sees it, on any platform: streams that start, fail and call back while their run
 * loop runs. Every call is recorded with the thread it was made on, and whether the run loop was running then.
 */
final class FakeFsEvents implements FsEventsApi {

    final List<String> calls = new CopyOnWriteArrayList<>();
    final List<String> violations = new CopyOnWriteArrayList<>();
    final List<Stream> streams = new CopyOnWriteArrayList<>();
    /**
     * Run on the run loop thread while its loop runs, as FSEvents calls a stream's callback.
     */
    final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
    volatile RuntimeException runLoopFailure;
    volatile LinkageError runLoopLinkageFailure;
    volatile IOException createFailure;
    volatile RuntimeException scheduleFailure;
    volatile boolean startFails;
    volatile RuntimeException runFailure;
    volatile Thread runLoopThread;
    /**
     * Run when a stream starts, before it reports any change.
     */
    volatile Runnable onStart = () -> { };
    private volatile boolean running;

    @Override
    public RunLoop currentRunLoop() {
        runLoopThread = Thread.currentThread();
        if (runLoopLinkageFailure != null) {
            throw runLoopLinkageFailure;
        }
        if (runLoopFailure != null) {
            throw runLoopFailure;
        }
        return new Loop();
    }

    @Override
    public NativeStream create(String realPath, double latency, FsEventStreams.Events events) throws IOException {
        record("create " + realPath);
        if (createFailure != null) {
            throw createFailure;
        }
        Stream stream = new Stream(realPath, events);
        streams.add(stream);
        return stream;
    }

    /**
     * Calls back each started stream that is not stopped, from the run loop while it runs.
     *
     * @param call The call
     */
    void callBack(Consumer<FsEventStreams.Events> call) {
        callbacks.add(() -> {
            for (Stream stream : streams) {
                if (stream.started && !stream.stopped) {
                    call.accept(stream.events);
                }
            }
        });
    }

    private void record(String call) {
        calls.add(call);
        if (Thread.currentThread() != runLoopThread) {
            violations.add(call + " on " + Thread.currentThread().getName());
        }
        if (running) {
            violations.add(call + " while the run loop runs");
        }
    }

    private final class Loop implements RunLoop {
        private final LinkedBlockingQueue<Boolean> stops = new LinkedBlockingQueue<>();

        @Override
        public void run(double seconds) {
            if (Thread.currentThread() != runLoopThread) {
                violations.add("run on " + Thread.currentThread().getName());
            }
            RuntimeException failure = runFailure;
            if (failure != null) {
                throw failure;
            }
            running = true;
            try {
                long deadline = System.nanoTime() + (long) (seconds * 1_000_000_000L);
                while (System.nanoTime() < deadline && stops.poll() == null) {
                    Runnable callback = callbacks.poll(5, TimeUnit.MILLISECONDS);
                    if (callback != null) {
                        callback.run();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running = false;
            }
        }

        @Override
        public void stop() {
            Thread owner = runLoopThread;
            if (owner != null && !owner.isAlive()) {
                // CoreFoundation frees a thread's run loop with the thread: stopping it then traps
                violations.add("stop of the run loop after its thread ended");
            }
            stops.add(Boolean.TRUE);
        }
    }

    final class Stream implements NativeStream {
        final String realPath;
        final FsEventStreams.Events events;
        volatile boolean started;
        volatile boolean stopped;
        volatile boolean released;

        Stream(String realPath, FsEventStreams.Events events) {
            this.realPath = realPath;
            this.events = events;
        }

        @Override
        public void schedule(RunLoop runLoop) {
            record("schedule " + realPath);
            if (scheduleFailure != null) {
                throw scheduleFailure;
            }
        }

        @Override
        public boolean start() {
            record("start " + realPath);
            onStart.run();
            started = !startFails;
            return started;
        }

        @Override
        public void stop() {
            record("stop " + realPath);
            if (!started) {
                violations.add("stop of a stream that did not start");
            }
            stopped = true;
        }

        @Override
        public void invalidate() {
            record("invalidate " + realPath);
        }

        @Override
        public void release() {
            record("release " + realPath);
            released = true;
        }
    }
}
