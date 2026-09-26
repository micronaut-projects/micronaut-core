package io.micronaut.aop.scheduled;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationSpec")
public class MarkedBean {

    private final OtherMarkedBean other;

    public MarkedBean(OtherMarkedBean other) {
        this.other = other;
    }

    @Marked
    public int run(int depth) {
        if (depth > 0) {
            return run(depth - 1);
        }
        return other.run(depth);
    }
}
