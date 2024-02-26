package io.micronaut.health

import io.micronaut.core.type.Argument
import io.micronaut.jackson.databind.JacksonDatabindMapper
import io.micronaut.json.JsonMapper
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

class HealthSpec extends Specification {

    void "test HealthResult"(JsonMapper objectMapper) {
        given:

            HealthResult hr = HealthResult.builder("db", HealthStatus.DOWN)
                    .details(Collections.singletonMap("foo", "bar"))
                    .build()

        when:
            def result = objectMapper.writeValueAsString(hr)

        then:
            result == '{"name":"db","status":"DOWN","details":{"foo":"bar"}}'

        when:
            hr = objectMapper.readValue(result, Argument.of(HealthResult))

        then:
            hr.name == 'db'
            hr.status == HealthStatus.DOWN

        where:
            objectMapper << [ObjectMapper.getDefault(), new JacksonDatabindMapper(new com.fasterxml.jackson.databind.ObjectMapper())]
    }

}
