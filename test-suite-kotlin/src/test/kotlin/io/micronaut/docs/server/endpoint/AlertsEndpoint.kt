package io.micronaut.docs.server.endpoint

//tag::clazz[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.management.endpoint.annotation.*
import java.util.concurrent.CopyOnWriteArrayList

//end::clazz[]
@Requires(property = "spec.name", value = "AlertsEndpointSpec")
//tag::clazz[]
@Endpoint(id = "alerts", defaultSensitive = false) // <1>
class AlertsEndpoint {

    private val alerts: MutableList<String> = CopyOnWriteArrayList()

    @Read
    fun getAlerts(): List<String> {
        return alerts
    }

    @Delete
    @Sensitive(true)  // <2>
    fun clearAlerts() {
        alerts.clear()
    }

    @Write(consumes = [MediaType.TEXT_PLAIN])
    @Sensitive(property = "add.sensitive", defaultValue = true)  // <3>
    fun addAlert(alert: String) {
        alerts.add(alert)
    }
}
//end::clazz[]
