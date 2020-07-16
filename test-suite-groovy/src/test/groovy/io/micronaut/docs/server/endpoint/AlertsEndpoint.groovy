package io.micronaut.docs.server.endpoint;

//tag::clazz[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.management.endpoint.annotation.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

//end::clazz[]
@Requires(property = "spec.name", value = "AlertsEndpointSpec")
//tag::clazz[]
@Endpoint(id = "alerts", defaultSensitive = false) // <1>
class AlertsEndpoint {

    private final List<String> alerts = new CopyOnWriteArrayList<>();

    @Read
    List<String> getAlerts() {
        alerts
    }

    @Delete
    @Sensitive(true)  // <2>
    void clearAlerts() {
        alerts.clear()
    }

    @Write(consumes = MediaType.TEXT_PLAIN)
    @Sensitive(property = "add.sensitive", defaultValue = true)  // <3>
    void addAlert(String alert) {
        alerts.add(alert)
    }
}
//end::clazz[]