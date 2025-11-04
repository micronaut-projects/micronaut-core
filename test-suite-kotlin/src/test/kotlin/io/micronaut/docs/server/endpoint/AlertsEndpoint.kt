package io.micronaut.docs.server.endpoint

//tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.management.endpoint.annotation.Delete
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.management.endpoint.annotation.Sensitive
import io.micronaut.management.endpoint.annotation.Write
import java.util.concurrent.CopyOnWriteArrayList

//end::imports[]

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
    fun addAlert(@Body alert: String) {
        alerts.add(alert)
    }
}
//end::clazz[]
