import java
from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Body
from micronaut.management.endpoint.annotation import Delete, Endpoint, Read, Sensitive, Write

CopyOnWriteArrayList = java.type("java.util.concurrent.CopyOnWriteArrayList")
# end::imports[]


@Requires(property="spec.name", value="AlertsEndpointSpec")
# tag::clazz[]
@Endpoint(id="alerts", defaultSensitive=False)  # <1>
class AlertsEndpoint:

    def __init__(self):
        self.alerts = CopyOnWriteArrayList()

    @Read
    def getAlerts(self):
        return self.alerts

    @Delete
    @Sensitive(True)  # <2>
    def clearAlerts(self) -> None:
        self.alerts.clear()

    @Write(consumes=MediaType.TEXT_PLAIN)
    @Sensitive(property="add.sensitive", defaultValue=True)  # <3>
    def addAlert(self, alert: Annotated[str, Body]) -> None:
        self.alerts.add(alert)
# end::clazz[]
