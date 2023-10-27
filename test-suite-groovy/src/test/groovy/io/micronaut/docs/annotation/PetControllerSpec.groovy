package io.micronaut.docs.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.validation.ConstraintViolationException
import reactor.core.publisher.Mono
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class PetControllerSpec extends Specification {

    void "test post pet"() {
        when:
        // tag::post[]
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)
        Pet pet = Mono.from(client.save("Dino", 10)).block()

        then:
        "Dino" == pet.getName()
        10 == pet.getAge()
        // end::post[]

        embeddedServer.stop()
    }

    void "test post pet validation"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        def ex = assertThrows(ConstraintViolationException.class, () -> {
            PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)
            Mono.from(client.save("Fred", -1)).block()
        })

        assertEquals(ex.getMessage(), "save.age: must be greater than or equal to 1")
        embeddedServer.stop()
    }
}
