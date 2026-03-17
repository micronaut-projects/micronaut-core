package io.micronaut.http.netty.channel;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Promise;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskQueueInterceptorTest {
    @Test
    public void test() throws ExecutionException, InterruptedException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "TaskQueueInterceptorTest"))) {
            AtomicInteger started = ctx.getBean(MyTaskQueueInterceptor.class).started;
            EventLoopGroup group = ctx.getBean(EventLoopGroup.class);
            EventLoop loop = group.next();
            Promise<String> promise = loop.newPromise();
            int before = started.get();
            loop.execute(() -> promise.setSuccess("foo"));
            assertEquals("foo", promise.get());
            assertNotEquals(before, started.get());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "TaskQueueInterceptorTest")
    static final class MyTaskQueueInterceptor implements TaskQueueInterceptor {
        final AtomicInteger started = new AtomicInteger(0);

        @Override
        public Queue<Runnable> wrapTaskQueue(String groupName, Queue<Runnable> original) {
            return new AbstractQueue<>() {
                @Override
                public Iterator<Runnable> iterator() {
                    return original.iterator();
                }

                @Override
                public int size() {
                    return original.size();
                }

                @Override
                public boolean offer(Runnable runnable) {
                    return original.offer(() -> {
                        started.incrementAndGet();
                        runnable.run();
                    });
                }

                @Override
                public Runnable poll() {
                    return original.poll();
                }

                @Override
                public Runnable peek() {
                    return original.peek();
                }
            };
        }
    }
}
