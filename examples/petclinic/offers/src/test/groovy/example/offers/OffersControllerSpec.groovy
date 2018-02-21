/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.offers

import example.api.v1.Offer
import example.api.v1.PetType
import example.offers.client.v1.Pet
import org.particleframework.context.ApplicationContext
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.validation.ConstraintViolationException
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
class OffersControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["consul.client.enabled": false]
    )

    @Unroll
    void "test save offer with invalid arguments"() {
        given:
        TestOffersClient offersClient = embeddedServer.applicationContext.getBean(TestOffersClient)

        when:
        offersClient.save(slug, price, duration, description).block()

        then:
        thrown(ConstraintViolationException)

        where:
        slug   | price  | duration                            | description
        null   | null   | null                                | null
        null   | null   | null                                | null
        "dino" | null   | null                                | null
        "dino" | 10.0   | null                                | null
        "dino" | 10.0   | Duration.of(10, ChronoUnit.SECONDS) | null
        "dino" | 10.400 | Duration.of(10, ChronoUnit.SECONDS) | "desc"
        "dino" | 10.0   | null                                | "desc"
    }

    void "test save offer for pet that doesn't exist"() {
        given:
        TestOffersClient offersClient = embeddedServer.applicationContext.getBean(TestOffersClient)

        when:
        Offer offer = offersClient.save(
                "not there",
                10.0,
                Duration.of(10, ChronoUnit.SECONDS),
                "description"

        ).block()

        then: "No offer was created"
        offer == null
    }


    void "test save offer for pet that does exist"() {
        given:
        TestOffersClient offersClient = embeddedServer.applicationContext.getBean(TestOffersClient)
        TestPetClientFallback petClientFallback = embeddedServer.applicationContext.getBean(TestPetClientFallback)
        def pet = new Pet("Fred", "Harry", "harry", "photo-1457914109735-ce8aba3b7a79.jpeg")
        petClientFallback.addPet(pet)

        when: "An offer is saved"
        Offer offer = offersClient.save(
                pet.slug,
                10.0,
                Duration.of(10, ChronoUnit.SECONDS),
                "Friendly Dog"

        ).block()

        then: "The offer was created correctly"
        offer != null
        offer.pet.name == "Harry"
        offer.pet.vendor == "Fred"
        offer.price == 10.0
        offer.description == "Friendly Dog"
    }

}
