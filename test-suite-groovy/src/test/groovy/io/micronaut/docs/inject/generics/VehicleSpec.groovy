package io.micronaut.docs.inject.generics

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class VehicleSpec extends Specification {
    @Inject Vehicle vehicle

    void 'test start engine'()  {
        expect:
        vehicle.start() == 'Starting V8'
    }
}
