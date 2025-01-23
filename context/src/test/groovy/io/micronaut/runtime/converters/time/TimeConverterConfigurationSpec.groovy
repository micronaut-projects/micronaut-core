package io.micronaut.runtime.converters.time

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.exceptions.DependencyInjectionException
import jakarta.inject.Singleton
import java.time.Duration
import spock.lang.Specification

class TimeConverterConfigurationSpec extends Specification {

    void 'test exception message with incorrect duration value'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                    : 'TimeConverterRegistrarSpec',
                'health-check-cache-expiration': '60 seconds',
        ])

        when:
        var myTask = ctx.getBean(MyTask)

        then:
        var e = thrown(DependencyInjectionException.class)
        e.message.contains("Error resolving property value [\${health-check-cache-expiration}]. Unable to convert value [60 seconds] to target type [Duration] due to: Unparseable date format (60 seconds). Should either be a ISO-8601 duration or a round number followed by the unit type")
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'TimeConverterRegistrarSpec')
    static class MyTask {

        @Value("\${health-check-cache-expiration}")
        private Duration healthCheckCacheExpiration;

        Duration getHealthCheckCacheExpiration() {
            return healthCheckCacheExpiration
        }

        void setHealthCheckCacheExpiration(Duration healthCheckCacheExpiration) {
            this.healthCheckCacheExpiration = healthCheckCacheExpiration
        }
    }
}
