package io.micronaut.scheduling.marker;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationMarkerSpec")
public class ScheduledMarkerTwoSchedulesTask {

    @Scheduled(fixedDelay = "40ms")
    @Scheduled(fixedDelay = "60ms")
    @RecordScheduled
    public void twice() {
    }
}
