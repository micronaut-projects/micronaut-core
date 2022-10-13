package io.micronaut.scheduling;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class VirtualThreadTest {
    @Test
    public void test() throws ExecutionException, InterruptedException {
        ApplicationContext ctx = ApplicationContext.run();
        ExecutorService executor = ctx.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO));
        Thread t = executor.submit(Thread::currentThread).get();
        Assertions.assertTrue(t.isVirtual());
    }
}
