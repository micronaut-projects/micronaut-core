package io.micronaut.aop.scheduled;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationSpec")
public class OtherMarkedBean {

    @Marked
    public int run(int depth) {
        return depth;
    }
}
