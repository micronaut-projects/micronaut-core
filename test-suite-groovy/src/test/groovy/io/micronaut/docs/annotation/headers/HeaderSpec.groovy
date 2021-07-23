package io.micronaut.docs.annotation.headers

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.annotation.Pet
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HeaderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ["pet.client.id": "11"], Environment.TEST)

    void "test sender headers"() throws Exception {
        when:
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient)
        Pet pet = Mono.from(client.get("Fred")).block()

        then:
        null != pet
        11 == pet.getAge()
    }
}
