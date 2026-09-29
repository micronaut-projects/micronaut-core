package io.micronaut.aop.adapter.classlevel;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import java.util.concurrent.CountDownLatch;

/**
 * A bean carrying class level around advice and a {@code @Scheduled} method. Unlike {@code @EventListener},
 * {@code @Scheduled} is {@code @Executable} + {@code @Parallel} advice and generates no adapter bean, so the
 * class level advice has only ever been applied once, to the declaring bean.
 */
@Singleton
@Logged
@Requires(property = "spec.name", value = "ScheduledClassLevelAdviceSpec")
class LoggedScheduledBean {

    final CountDownLatch latch = new CountDownLatch(1);

    @Scheduled(fixedDelay = "10ms")
    void everyNow() {
        latch.countDown();
    }
}
