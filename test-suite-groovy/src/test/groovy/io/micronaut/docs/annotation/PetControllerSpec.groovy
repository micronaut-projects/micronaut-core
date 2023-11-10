package io.micronaut.docs.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.validation.ConstraintViolationException
import reactor.core.publisher.Mono
import spock.lang.Specification

class PetControllerSpec extends Specification {

    void "test post pet"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

        when:
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)
        Pet pet = Mono.from(client.save("Dino", 10)).block()

        then:
        "Dino" == pet.getName()
        10 == pet.getAge()

        cleanup:
        embeddedServer.stop()
    }

    void "test post pet validation"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

        when:
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)
        Mono.from(client.save("Fred", -1)).block()

        then:
        ConstraintViolationException ex = thrown()
        ex.message == "save.age: must be greater than or equal to 1"

        cleanup:
        embeddedServer.stop()
    }
}
