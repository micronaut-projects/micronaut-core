package io.micronaut.docs.factories

// tag::imports[]
import io.micronaut.context.annotation.*
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import javax.inject.Inject
// end::imports[]

// tag::class[]
@MicronautTest
class VehicleMockSpec extends Specification {
    @Requires(beans=VehicleMockSpec.class)
    @Bean @Replaces(Engine.class)
    Engine mockEngine = {-> "Mock Started" } as Engine  // <1>

    @Inject Vehicle vehicle // <2>

    void "test start engine"() {
        given:
        final String result = vehicle.start()

        expect:
        result == "Mock Started" // <3>
    }
}
// end::class[]