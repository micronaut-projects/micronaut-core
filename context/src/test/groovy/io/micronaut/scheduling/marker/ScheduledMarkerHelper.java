package io.micronaut.scheduling.marker;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationMarkerSpec")
public class ScheduledMarkerHelper {

    @RecordScheduled
    public void work() {
    }
}
