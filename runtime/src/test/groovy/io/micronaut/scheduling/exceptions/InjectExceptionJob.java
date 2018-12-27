package io.micronaut.scheduling.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Requires(property = "injection-exception.task.enabled", value = "true")
public class InjectExceptionJob {

    @Inject NotInjectable notInjectable;

    @Scheduled(fixedRate = "10m")
    public void runSomething() {

    }

}
