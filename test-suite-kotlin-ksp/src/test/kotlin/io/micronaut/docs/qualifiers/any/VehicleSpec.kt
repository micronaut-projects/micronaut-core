package io.micronaut.docs.qualifiers.any

import io.micronaut.docs.qualifiers.annotationmember.Engine
// tag::imports[]
import io.micronaut.context.annotation.Any
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
// end::imports[]

@MicronautTest
class VehicleSpec {
    // tag::any[]
    @Inject
    @field:Any
    lateinit var engine: Engine
    // end::any[]

    @Test
    fun testEngine() {
        assertNotNull(engine)
    }
}
