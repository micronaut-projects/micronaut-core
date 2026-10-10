package io.micronaut.scheduling.marker;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ScheduledInvocationMarkerSpec")
public class ScheduledMarkerProxyTargetTask {

    @Scheduled(fixedDelay = "50ms")
    @RecordScheduledProxyTarget
    public void tick() {
    }
}
