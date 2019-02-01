package io.micronaut.scheduling.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.reflect.exception.InvocationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Singleton;

@Singleton
@Requires(property = "scheduled-exception1.task3.enabled", value = StringUtils.TRUE)
public class ThrowsExceptionJob3  {
    @Scheduled(fixedRate = "10ms")
    public void runSomething() {
        throw new InvocationException("bad things");
    }
}
