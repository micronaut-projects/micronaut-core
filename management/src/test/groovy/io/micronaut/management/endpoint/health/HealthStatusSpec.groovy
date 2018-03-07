package io.micronaut.management.endpoint.health

import io.micronaut.health.HealthStatus
import spock.lang.Specification

class HealthStatusSpec extends Specification {

    void "test ordering"() {
        // operational (true -> null -> false)
        // severity (null > ascending)
        when:
        List<HealthStatus> statuses = []
        statuses.add(new HealthStatus("A", null, true, null))
        statuses.add(new HealthStatus("B", null, null, null))
        statuses.add(new HealthStatus("C", null, false, null))
        statuses.add(new HealthStatus("D", null, true, 1))
        statuses.add(new HealthStatus("E", null, null, 1))
        statuses.add(new HealthStatus("F", null, false, 1))
        statuses.add(new HealthStatus("G", null, true, 2))
        statuses.add(new HealthStatus("H", null, null, 2))
        statuses.add(new HealthStatus("I", null, false, 2))
        statuses.sort(true)

        then:
        statuses[0].name == "A"
        statuses[1].name == "D"
        statuses[2].name == "G"
        statuses[3].name == "B"
        statuses[4].name == "E"
        statuses[5].name == "H"
        statuses[6].name == "C"
        statuses[7].name == "F"
        statuses[8].name == "I"
    }
}
