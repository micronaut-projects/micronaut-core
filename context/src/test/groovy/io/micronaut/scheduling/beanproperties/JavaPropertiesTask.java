package io.micronaut.scheduling.beanproperties;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(property = "spec.name", value = "ScheduledBeanPropertiesSpec")
@Requires(property = "scheduled-test.task.enabled", value = StringUtils.TRUE)
public class JavaPropertiesTask {
    AtomicInteger fixedRateEvents = new AtomicInteger(0);

    @Scheduled(bean = TestConfig.class, fixedRateProperty = "stringFixedRate")
    @Scheduled(bean = TestConfig.class, fixedRateProperty = "durationFixedRate")
    void runFixed() {
        fixedRateEvents.incrementAndGet();
    }
}
