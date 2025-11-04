package io.micronaut.docs.inject.generics

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class VehicleSpec(private val vehicle: Vehicle) {
    @Test
    fun testStartVehicle() {
        assertEquals("Starting V8", vehicle.start())
        assertEquals(listOf(6), vehicle.v6Engines
                .map { it.cylinders })
    }
}
