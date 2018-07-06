package io.micronaut.configuration.kafka.health

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import spock.lang.Specification

class KafkaHealthIndicatorSpec extends Specification {

    void "test kafka health indicator"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )
        )

        when:
        KafkaHealthIndicator healthIndicator = applicationContext.getBean(KafkaHealthIndicator)
        HealthResult result = healthIndicator.result.firstElement().blockingGet()

        then:
        // report down because the not enough nodes to meet replication factor
        result.status == HealthStatus.DOWN
        result.details.nodes == 1

        cleanup:
        applicationContext.close()
    }
}
