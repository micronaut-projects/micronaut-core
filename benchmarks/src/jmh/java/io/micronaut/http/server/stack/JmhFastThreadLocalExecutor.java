package io.micronaut.http.server.stack;

import io.micronaut.core.annotation.NonNull;
import io.netty.util.concurrent.FastThreadLocalThread;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class JmhFastThreadLocalExecutor extends ThreadPoolExecutor {
    public JmhFastThreadLocalExecutor(int maxThreads, String prefix) {
        super(maxThreads, maxThreads,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactory() {
                final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(@NonNull Runnable r) {
                    return new FastThreadLocalThread(r, prefix + "-jmh-worker-ftl-" + counter.incrementAndGet());
                }
            });
    }
}
