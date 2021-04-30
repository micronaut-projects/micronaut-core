package io.micronaut.docs.factories

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.inject.Inject
// end::imports[]

// tag::class[]
@MicronautTest
class VehicleMockSpec {
    @get:Bean
    @get:Replaces(Engine::class)
    val mockEngine: Engine = object : Engine { // <1>
        override fun start(): String {
            return "Mock Started"
        }
    }

    @Inject
    lateinit var vehicle : Vehicle // <2>

    @Test
    fun testStartEngine() {
        val result = vehicle.start()
        Assertions.assertEquals("Mock Started", result) // <3>
    }
}
// tag::class[]