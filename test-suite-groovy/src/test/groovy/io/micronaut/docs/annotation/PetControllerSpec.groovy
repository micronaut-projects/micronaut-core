package io.micronaut.docs.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Rule
import org.junit.rules.ExpectedException
import reactor.core.publisher.Mono
import spock.lang.Specification

import javax.validation.ConstraintViolationException

class PetControllerSpec extends Specification {

    // tag::errorRule[]
    @Rule
    public ExpectedException thrown = ExpectedException.none()
    // end::errorRule[]

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
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)

        // tag::error[]
        thrown.expect(ConstraintViolationException.class)
        thrown.expectMessage("save.age: must be greater than or equal to 1")
        Mono.from(client.save("Fred", -1)).block()
        // end::error[]

        embeddedServer.stop()
    }
}
