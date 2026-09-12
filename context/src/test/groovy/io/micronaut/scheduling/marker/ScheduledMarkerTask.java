package io.micronaut.scheduling.marker;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationMarkerSpec")
public class ScheduledMarkerTask {

    private final ScheduledMarkerHelper helper;

    public ScheduledMarkerTask(ScheduledMarkerHelper helper) {
        this.helper = helper;
    }

    @Scheduled(fixedDelay = "50ms")
    @RecordScheduled
    public void run() {
        // calls through the proxy: neither nested interception is a scheduled invocation
        nested();
        helper.work();
    }

    @RecordScheduled
    public void nested() {
    }
}
