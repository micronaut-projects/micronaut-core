package io.micronaut.docs.annotation.headers

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.annotation.Pet
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class HeaderSpec extends Specification {

    void "test sender headers"() throws Exception {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ["pet.client.id": "11"], Environment.TEST)
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)
        Pet pet = client.get("Fred").blockingGet()

        then:
        null != pet
        11 == pet.getAge()
    }
}
